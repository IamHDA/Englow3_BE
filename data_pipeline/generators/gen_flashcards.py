#!/usr/bin/env python3
"""Sinh flashcard — v4, thay thế đợt dữ liệu điền khuôn 2026-08-06.

Nguyên tắc: **thà ít mà thật còn hơn nhiều mà rỗng.** Từ nào chưa có nội dung
tiếng Việt viết tay trong generators/vi_lexicon.py thì BỎ QUA, không sinh thẻ.
Đợt cũ hỏng đúng vì làm ngược lại — điền khuôn cho đủ 3 000.

Nguồn:
  definition_en   WordNet gloss (thật, không phải khuôn)
  examples EN     vi_lexicon (ưu tiên) hoặc WordNet examples
  definition_vi   vi_lexicon — viết tay, không có nguồn mở
  translation VI  vi_lexicon — viết tay
  ipa_us          eng-to-ipa/CMUdict, có suy luận hình thái cho hậu tố phái sinh
  concept_ids     topic_hint → WordNet lexname → phái sinh → fallback
  difficulty      base theo band + phân vị frequency_rank TRONG band

Trước khi ghi, batch phải qua check_skeleton_diversity ≥ 0.60.

    python generators/gen_flashcards.py
    python generators/gen_flashcards.py --report-only
"""

from __future__ import annotations

import argparse
import bisect
import collections
import csv
import sys
from datetime import UTC, datetime
from pathlib import Path

import eng_to_ipa as ipa
import yaml
from nltk.corpus import wordnet as wn

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from authoring import write_batch  # noqa: E402
from schemas import (  # noqa: E402
    Collocation,  # noqa: E402
    BatchMetadata, Definition, Example, Flashcard, FlashcardBatch, ModuleType,
)
from validators.diversity import check_skeleton_diversity  # noqa: E402
from vi_lexicon import COLLOCATIONS, LEXICON, TOPIC_OVERRIDE  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "seeds" / "vocab_seed.csv"
TAXONOMY = ROOT / "taxonomy" / "concepts.yaml"
OUT = ROOT / "output" / "flashcards" / "flashcard_batch_001.json"
GENERATED_BY = "claude-opus-5"

WN_POS = {"noun": wn.NOUN, "verb": wn.VERB, "adjective": wn.ADJ, "adverb": wn.ADV}

# --- Ánh xạ lexname → topic --------------------------------------------------
# v4 gốc chỉ map 16/45 lexname nên chỉ gán đúng ngữ nghĩa được 44.9%. Bảng dưới
# phủ nốt phần còn lại. Riêng adj.all / adv.all thì WordNet gom TẤT CẢ tính từ
# và trạng từ vào một nhóm, không phân loại chủ đề — phải đi qua liên kết phái
# sinh để chạm danh từ/động từ gốc (xem topic_via_derivation).
LEXNAME_TOPIC = {
    "noun.food": "dining_entertainment",
    "noun.artifact": "daily_life",
    "noun.location": "travel_transport",
    "noun.time": "daily_life",
    "noun.communication": "business_office",
    "noun.act": "business_office",
    "noun.possession": "shopping_finance",
    "noun.attribute": "daily_life",
    "noun.body": "health_wellbeing",
    "noun.cognition": "education_career",
    "noun.person": "education_career",
    "noun.group": "business_office",
    "noun.state": "health_wellbeing",
    "noun.event": "dining_entertainment",
    "noun.quantity": "shopping_finance",
    "noun.substance": "dining_entertainment",
    "noun.object": "travel_transport",
    "noun.animal": "daily_life",
    "noun.plant": "daily_life",
    "noun.phenomenon": "daily_life",
    "noun.feeling": "health_wellbeing",
    "noun.motive": "education_career",
    "noun.process": "technology_media",
    "noun.relation": "business_office",
    "noun.shape": "daily_life",
    "noun.Tops": "daily_life",
    "verb.social": "business_office",
    "verb.communication": "business_office",
    "verb.possession": "shopping_finance",
    "verb.motion": "travel_transport",
    "verb.consumption": "dining_entertainment",
    "verb.change": "technology_media",
    "verb.cognition": "education_career",
    "verb.contact": "daily_life",
    "verb.stative": "daily_life",
    "verb.creation": "technology_media",
    "verb.perception": "health_wellbeing",
    "verb.body": "health_wellbeing",
    "verb.emotion": "health_wellbeing",
    "verb.competition": "dining_entertainment",
    "verb.weather": "daily_life",
}

# Hậu tố suy luận IPA được (§ suy luận hình thái, thay cho việc nhét chính tả)
SUFFIX_RULES = [("ly", "li"), ("ness", "nəs"), ("ment", "mənt"), ("able", "əbəl")]

BASE_PRIOR = {"A1": 0.22, "A2": 0.37, "B1": 0.52, "B2": 0.67, "C1": 0.82}


def load_valid_concepts() -> set[str]:
    """concepts.yaml là list phẳng ở top-level — không phải dict có khoá."""
    return {c["concept_id"] for c in yaml.safe_load(TAXONOMY.read_text(encoding="utf-8"))}


def first_synset(lemma: str, pos: str):
    p = WN_POS.get(pos)
    if not p:
        return None
    ss = wn.synsets(lemma.replace(" ", "_"), pos=p)
    return ss[0] if ss else None


def topic_via_derivation(lemma: str, pos: str) -> str | None:
    """adj.all / adv.all không có chủ đề — đi qua liên kết phái sinh của WordNet
    để chạm danh từ hoặc động từ gốc (vd 'financial' → 'finance')."""
    s = first_synset(lemma, pos)
    if not s:
        return None
    for l in s.lemmas():
        for rel in l.derivationally_related_forms():
            t = LEXNAME_TOPIC.get(rel.synset().lexname())
            if t:
                return t
    return None


def resolve_concept(lemma: str, pos: str, level: str, topic_hint: str,
                    valid: set[str]) -> tuple[str, str]:
    """Trả về (concept_id, nguồn quyết định). Luôn thuộc `valid`."""
    lv = level.lower()
    # Gán tay thắng mọi suy đoán: những từ này được CHỌN cho đúng chủ đề đó.
    cid = TOPIC_OVERRIDE.get(lemma)
    if cid and cid in valid:
        return cid, "gán tay"
    if topic_hint and f"vocab_{topic_hint}_{lv}" in valid:
        return f"vocab_{topic_hint}_{lv}", "topic_hint"
    s = first_synset(lemma, pos)
    if s:
        t = LEXNAME_TOPIC.get(s.lexname())
        if t and f"vocab_{t}_{lv}" in valid:
            return f"vocab_{t}_{lv}", "lexname"
    t = topic_via_derivation(lemma, pos)
    if t and f"vocab_{t}_{lv}" in valid:
        return f"vocab_{t}_{lv}", "phái sinh"
    for fb in (f"vocab_daily_life_{lv}", f"vocab_business_office_{lv}"):
        if fb in valid:
            return fb, "FALLBACK"
    return "vocab_daily_life_a1", "FALLBACK"


def resolve_ipa(lemma: str) -> tuple[str, bool]:
    """(ipa, verified). KHÔNG BAO GIỜ trả chính tả làm IPA (§0.4 cấm bịa IPA)."""
    out = ipa.convert(lemma, keep_punct=False)
    if "*" not in out:
        return f"/{out}/", True
    for suf, tail in SUFFIX_RULES:                 # suy luận hình thái
        if lemma.endswith(suf) and len(lemma) > len(suf) + 2:
            base = lemma[: -len(suf)]
            for cand in (base, base + "e"):
                b = ipa.convert(cand, keep_punct=False)
                if "*" not in b:
                    return f"/{b}{tail}/", True
    return "", False                               # không suy được → bỏ từ này


def build_rank_percentiles(rows: list[dict]) -> dict[str, list[int]]:
    """Phân vị TRONG từng band. v4 gốc chia hằng số 4000 nên spread trong band
    chỉ 0.02–0.06; chuẩn hoá theo band mới dùng trọn dải ±0.04."""
    by = collections.defaultdict(list)
    for r in rows:
        if r["frequency_rank"]:
            by[r["cefr_level"]].append(int(r["frequency_rank"]))
    return {k: sorted(v) for k, v in by.items()}


def difficulty(level: str, freq_rank: str, pct: dict[str, list[int]]) -> float:
    base = BASE_PRIOR.get(level, 0.50)
    ranks = pct.get(level) or []
    if freq_rank and ranks:
        i = bisect.bisect_left(ranks, int(freq_rank))
        p = i / max(len(ranks) - 1, 1)
    else:
        p = 0.5                                    # thiếu rank → giữa band
    return round(max(0.05, min(0.95, base + p * 0.08 - 0.04)), 3)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    valid = load_valid_concepts()
    rows = list(csv.DictReader(SEED.open(encoding="utf-8")))
    pct = build_rank_percentiles(rows)

    cards: list[Flashcard] = []
    skip = collections.Counter()
    src = collections.Counter()

    for r in rows:
        lemma, pos = r["lemma"], r["pos"]
        entry = LEXICON.get(lemma)
        if entry is None:
            skip["chưa có nội dung tiếng Việt"] += 1
            continue
        ipa_us, verified = resolve_ipa(lemma)
        if not ipa_us:
            skip["không suy được IPA"] += 1
            continue

        def_vi, examples = entry
        syn = first_synset(lemma, pos)
        def_en = syn.definition() if syn else None
        if not def_en or len(def_en) < 5:
            skip["WordNet không có định nghĩa"] += 1
            continue

        # Nhãn nghĩa lấy từ WordNet. Có synset trả về lemma quá ngắn ("go") mà
        # schema đòi tối thiểu 3 ký tự — bỏ qua từ đó thay vì độn thêm chữ.
        sense_label = syn.lemmas()[0].name().replace("_", " ") if syn else lemma
        if len(sense_label) < 3:
            skip["nhãn nghĩa WordNet ngắn hơn 3 ký tự"] += 1
            continue

        # B2/C1 bắt buộc ≥3 collocation. Không có thì BỎ QUA từ đó, không sinh
        # cụm bịa để lách schema.
        cols = [Collocation(pattern=pt, text=tx, cefr=lv)
                for pt, tx, lv in COLLOCATIONS.get(lemma, [])]
        if r["cefr_level"] in ("B2", "C1") and len(cols) < 3:
            skip["B2/C1 chưa có collocation viết tay"] += 1
            continue

        cid, how = resolve_concept(lemma, pos, r["cefr_level"], r["topic_hint"], valid)
        src[how] += 1

        cards.append(Flashcard(
            lemma=lemma, pos=pos, sense_index=1,
            sense_label_en=sense_label, collocations=cols,
            ipa_us=ipa_us, ipa_verified=verified,
            definition=Definition(en=def_en[0].upper() + def_en[1:], vi=def_vi),
            examples=[Example(sentence=en, translation=vi) for en, vi in examples],
            cefr_level=r["cefr_level"], cefr_source=r["cefr_source"],
            frequency_rank=int(r["frequency_rank"]) if r["frequency_rank"] else None,
            topics=[cid.replace("vocab_", "").rsplit("_", 1)[0]],
            concept_ids=[cid],
            difficulty_prior=difficulty(r["cefr_level"], r["frequency_rank"], pct),
        ))

    print(f"Sinh {len(cards)} thẻ từ {len(rows)} từ seed")
    for k, v in skip.most_common():
        print(f"  bỏ qua — {k:32} {v}")
    print(f"\n  nguồn concept: {dict(src.most_common())}")

    # --- Lưới chắn: phải qua trước khi ghi ---
    gv = lambda c: (c.lemma, c.pos.value, c.sense_label_en)  # noqa: E731
    print()
    fail = False
    for name, get in [("definition.en", lambda c: c.definition.en),
                      ("definition.vi", lambda c: c.definition.vi),
                      ("examples[0]", lambda c: c.examples[0].sentence),
                      ("examples[0].vi", lambda c: c.examples[0].translation)]:
        ok, ratio, uniq = check_skeleton_diversity(cards, get, gv)
        print(f"  diversity {name:16} {uniq:4d}/{len(cards)} = {ratio:6.1%}  "
              f"{'OK' if ok else '✗ REJECT'}")
        fail |= not ok
    if fail:
        print("\nFAIL — batch bị lưới chắn từ chối, không ghi file")
        return 1

    ver = sum(1 for c in cards if c.ipa_verified)
    d = [c.difficulty_prior for c in cards]
    import statistics
    print(f"\n  ipa_verified: {ver}/{len(cards)} ({ver/len(cards):.0%})")
    print(f"  difficulty_prior: stdev={statistics.pstdev(d):.3f}")
    for lv in ["A1", "A2", "B1", "B2", "C1"]:
        s = [c.difficulty_prior for c in cards if c.cefr_level.value == lv]
        if s:
            print(f"    {lv}: {min(s):.3f}–{max(s):.3f}  spread={max(s)-min(s):.3f}  n={len(s)}")
    print(f"  concept lá dùng: {len({c.concept_ids[0] for c in cards})}")

    if args.report_only:
        return 0

    batch = FlashcardBatch(
        batch_metadata=BatchMetadata(
            batch_id="flashcard_batch_001", module_type=ModuleType.FLASHCARD,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(cards)),
        flashcards=cards)
    print()
    write_batch(batch, OUT, ROOT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
