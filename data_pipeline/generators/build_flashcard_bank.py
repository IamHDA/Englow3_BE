#!/usr/bin/env python3
"""Build the complete 3,000-card vocabulary bank from traceable open data.

The generator fills the complete, authoritative seed list with:

* English definitions and Vietnamese senses from thichhoc-dict;
* bilingual corpus examples from the Tatoeba/ManyThings EN-VI export;
* deterministic, explicitly generated fallback examples when the corpus has no
  suitable pair;
* corpus-frequency-ranked collocation candidates for B2/C1 cards.

All records remain ``draft`` because neither the dictionary's Vietnamese senses
nor the generated fallbacks have been human reviewed.
"""

from __future__ import annotations

import csv
import datetime as dt
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

import eng_to_ipa as ipa
import yaml
from nltk.corpus import wordnet as wn
from wordfreq import zipf_frequency

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "generators"))

from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    BatchMetadata, Collocation, Definition, Example, Flashcard, FlashcardBatch,
    ModuleType, ReviewStatus,
)
from schemas.enums import (  # noqa: E402
    CEFRLevel, CEFRSource, CollocationPattern, SourceType,
)
from schemas.ids import flashcard_id  # noqa: E402

SEED = ROOT / "seeds" / "vocab_seed.csv"
DICT_DIR = ROOT / "sources" / "thichhoc-dict" / "dict-en-vi" / "data" / "entries"
TATOEBA = ROOT / "sources" / "tatoeba-vie-eng" / "vie.txt"
OUT = ROOT / "output" / "flashcards"
REPORT = ROOT / "reports" / "flashcard_build_report.json"
PRONUNCIATION_MEDIA = ROOT / "output" / "media" / "audio" / "flashcards"
PRONUNCIATION_BASE = "http://localhost:9000/audio/flashcards"

POS_CODE = {
    "noun": "n", "verb": "v", "adjective": "adj", "adverb": "adv",
    "preposition": "prep", "conjunction": "conj", "pronoun": "pron",
    "determiner": "det",
}
WN_POS = {"noun": wn.NOUN, "verb": wn.VERB, "adjective": wn.ADJ, "adverb": wn.ADV}
LEVEL_BASE = {"A1": .22, "A2": .37, "B1": .52, "B2": .67, "C1": .82}
WORD_RE = re.compile(r"[A-Za-z]+(?:['’-][A-Za-z]+)*")

LEXNAME_TOPIC = {
    "noun.food": "dining_entertainment", "noun.artifact": "travel_transport",
    "noun.location": "travel_transport", "noun.time": "daily_life",
    "noun.communication": "business_office", "noun.act": "business_office",
    "noun.possession": "shopping_finance", "noun.body": "health_wellbeing",
    "noun.cognition": "education_career", "verb.social": "business_office",
    "verb.communication": "business_office", "verb.possession": "shopping_finance",
    "verb.motion": "travel_transport", "verb.consumption": "dining_entertainment",
    "verb.change": "technology_media",
}

# Only used when neither exact nor lemma-level open-dictionary data exists.
# These are deliberately short functional descriptions, not invented dictionary
# quotations. Content-word fallbacks are normally resolved through WordNet.
FUNCTION_WORDS = {
    "the": ("used before a noun when the listener knows which one is meant", "mạo từ xác định, dùng trước danh từ đã được xác định"),
    "and": ("used to connect words, phrases, or clauses", "và; dùng để nối từ, cụm từ hoặc mệnh đề"),
    "of": ("used to show belonging, connection, or composition", "của; dùng để chỉ sự thuộc về, liên hệ hoặc thành phần"),
    "to": ("used to indicate direction, destination, or a relationship", "đến, tới; dùng để chỉ hướng, đích hoặc quan hệ"),
    "you": ("the person or people being addressed", "bạn, các bạn; người hoặc những người đang được nói tới"),
    "for": ("used to indicate purpose, benefit, or duration", "cho, để; dùng để chỉ mục đích, lợi ích hoặc khoảng thời gian"),
    "they": ("the people or things previously mentioned", "họ, chúng; những người hoặc vật đã được nhắc tới"),
    "that": ("used to identify or introduce a specific person, thing, or clause", "đó, kia, rằng; dùng để xác định hoặc mở đầu một mệnh đề"),
    "we": ("the speaker together with one or more other people", "chúng tôi, chúng ta"),
    "with": ("used to show accompaniment, possession, or an instrument", "với, cùng với; dùng để chỉ sự đi cùng hoặc phương tiện"),
    "this": ("the person or thing that is near or currently being discussed", "này, đây; người hoặc vật ở gần hay đang được nói tới"),
    "she": ("a female person previously mentioned", "cô ấy, bà ấy"),
    "from": ("used to indicate a starting point, source, or origin", "từ; dùng để chỉ điểm bắt đầu, nguồn hoặc xuất xứ"),
    "if": ("used to introduce a condition", "nếu; dùng để mở đầu một điều kiện"),
    "which": ("used to ask about or identify one or more alternatives", "nào, cái mà; dùng để hỏi hoặc xác định lựa chọn"),
    "when": ("at what time or at the time that", "khi nào; vào lúc mà"),
    "what": ("used to ask for information about a person or thing", "gì, điều gì; dùng để hỏi thông tin"),
    "because": ("for the reason that", "bởi vì"),
    "than": ("used to introduce the second element in a comparison", "hơn, so với; dùng trong phép so sánh"),
    "into": ("to the inside of something or toward a changed state", "vào trong; chuyển sang một trạng thái khác"),
    "where": ("in or at what place", "ở đâu; tại nơi mà"),
    "how": ("in what way or by what method", "như thế nào; bằng cách nào"),
    "something": ("an unspecified or unknown thing", "một điều hoặc vật nào đó"),
    "until": ("up to the time or point when", "cho đến khi, cho tới"),
    "anything": ("any object, event, or matter, without restriction", "bất cứ điều gì"),
    "during": ("throughout or at a point within a period of time", "trong suốt, trong khi"),
    "since": ("from a past time until now, or because", "kể từ; vì, do"),
    "without": ("not having, using, or doing something", "không có, không dùng hoặc không làm"),
    "against": ("in opposition to or in contact with", "chống lại; tựa hoặc chạm vào"),
    "although": ("despite the fact that", "mặc dù"),
    "among": ("in the middle of or included in a group", "giữa, trong số một nhóm"),
    "toward": ("in the direction of or in relation to", "về phía; đối với"),
    "himself": ("the same male person as the subject", "chính anh ấy, bản thân anh ấy"),
    "themselves": ("the same people or things as the subject", "chính họ, bản thân họ"),
    "itself": ("the same thing or animal as the subject", "chính nó, bản thân nó"),
    "upon": ("on, or immediately after an event", "trên; ngay sau khi"),
    "myself": ("the speaker, used reflexively or for emphasis", "chính tôi, bản thân tôi"),
    "whoever": ("any person who or the person who", "bất cứ ai; người nào mà"),
    "whether": ("used to introduce alternatives or express doubt", "liệu; dùng để nêu các khả năng lựa chọn"),
    "per": ("for each or according to", "mỗi; theo"),
    "nor": ("and not; used to add another negative statement", "cũng không"),
    "unless": ("except if", "trừ khi"),
    "whereas": ("in contrast with the fact that", "trong khi, trái lại"),
    "whilst": ("while; during the time that or although", "trong khi; mặc dù"),
    "amid": ("in the middle of or surrounded by", "giữa, trong bối cảnh"),
    "goods": ("items or products that are made, bought, or sold", "hàng hóa, sản phẩm được sản xuất, mua hoặc bán"),
    "criteria": ("standards used to judge, compare, or decide something", "các tiêu chí dùng để đánh giá, so sánh hoặc quyết định"),
    "media": ("channels and organizations used to communicate news or information", "các phương tiện và tổ chức truyền thông dùng để chuyển tải tin tức hoặc thông tin"),
    "adjoining": ("next to and joined with another place or object", "liền kề, tiếp giáp với nơi hoặc vật khác"),
    "anchored": ("firmly fixed or supported in a particular position", "được neo giữ hoặc cố định vững chắc"),
    "antics": ("silly, unusual, or amusing behavior", "những trò nghịch ngợm hoặc hành vi kỳ quặc gây cười"),
    "apt to": ("likely or having a tendency to do something", "có xu hướng, có khả năng sẽ làm điều gì"),
    "beguilingly": ("in a charming and sometimes deceptive way", "một cách quyến rũ, đôi khi dễ gây hiểu lầm"),
    "bulk up": ("to increase in size, weight, or strength", "tăng kích thước, khối lượng hoặc sức mạnh"),
    "chiselled": ("having a clear and sharply defined shape", "có đường nét rõ và sắc sảo"),
    "compliantly": ("in a way that willingly follows a rule or request", "một cách tuân thủ quy tắc hoặc yêu cầu"),
    "compromised": ("weakened, damaged, or made less effective", "bị suy yếu, tổn hại hoặc giảm hiệu quả"),
    "demographically": ("in relation to the characteristics of a population", "xét về các đặc điểm nhân khẩu học của một dân số"),
    "docilely": ("in a quiet and easily controlled manner", "một cách ngoan ngoãn, dễ bảo"),
    "dominantly": ("in a controlling, leading, or most noticeable way", "theo cách chi phối, chủ đạo hoặc nổi bật nhất"),
    "eclectically": ("by selecting ideas or styles from many different sources", "theo lối chọn lọc ý tưởng hoặc phong cách từ nhiều nguồn"),
    "exotically": ("in an unusual or attractively foreign way", "một cách lạ mắt hoặc mang nét nước ngoài hấp dẫn"),
    "fiddly": ("difficult to do because of small or complicated details", "khó thao tác vì có nhiều chi tiết nhỏ hoặc phức tạp"),
    "inclusively": ("in a way that includes all relevant people or things", "một cách bao quát, không loại trừ người hoặc yếu tố liên quan"),
}

MODIFIERS = "annual appropriate broad careful central commercial common competitive considerable corporate critical current detailed direct effective efficient environmental financial formal global growing immediate independent international local long-term major mandatory mutual national new operational overall potential practical primary professional public rapid recent regional reliable significant strategic strict strong successful technical temporary total urgent".split()
NOUN_OBJECTS = "agreement application budget business change contract cost customer data demand development employee information issue market meeting operation order plan policy process product project proposal report request result risk service strategy system training".split()
VERBS = "address approve assess avoid build complete conduct consider create develop establish evaluate improve increase manage meet provide reduce review support".split()
ADVERBS = "absolutely broadly carefully clearly closely completely directly effectively fully generally highly increasingly largely necessarily particularly potentially properly rapidly relatively seriously significantly strongly successfully thoroughly widely".split()
ADJECTIVES = "available clear competitive effective efficient important likely necessary possible practical reliable responsible significant successful useful".split()
PREPOSITIONS = "about against at by for from in into of on over through to toward under with without".split()
TOPIC_OVERRIDES = {
    "pill": "health_wellbeing", "asleep": "health_wellbeing",
    "sleepy": "health_wellbeing", "flu": "health_wellbeing",
}


def tokens(text: str) -> list[str]:
    return [m.group(0).lower().replace("’", "'") for m in WORD_RE.finditer(text)]


def load_dictionary():
    exact, by_lemma = defaultdict(list), defaultdict(list)
    for path in sorted(DICT_DIR.glob("*.jsonl")):
        for line in path.open(encoding="utf-8"):
            row = json.loads(line)
            lemma = row["headword"].strip().lower()
            exact[(lemma, row["pos"])].append(row)
            by_lemma[lemma].append(row)
    return exact, by_lemma


def load_tatoeba():
    by_word = defaultdict(list)
    for line in TATOEBA.open(encoding="utf-8"):
        cols = line.rstrip("\n").split("\t")
        if len(cols) < 3:
            continue
        en, vi, attr = cols[:3]
        if not (5 <= len(en) <= 180 and 2 <= len(vi) <= 220):
            continue
        if len(tokens(en)) < 3:
            continue
        for word in set(tokens(en)):
            by_word[word].append((en, vi, attr))
    return by_word


def get_ipa(word: str):
    overrides = {"flu": ("/fluː/", "/fluː/", True)}
    if word in overrides:
        return overrides[word]
    result = ipa.convert(word)
    if not result or result.startswith("*") or result == word:
        return f"/{word}/", None, False
    value = result if result.startswith("/") else f"/{result}/"
    return value, None, True


def wordnet_sense(lemma: str, pos: str):
    synsets = wn.synsets(lemma, pos=WN_POS.get(pos)) if pos in WN_POS else []
    if not synsets:
        return None, None, None
    syn = synsets[0]
    label = syn.lemmas()[0].name().replace("_", " ")
    return syn.definition(), label, syn.lexname()


def choose_entry(rows: list[dict]) -> dict | None:
    if not rows:
        return None
    confidence = {"high": 0, "medium": 1, "none": 2, "low": 3}
    return sorted(rows, key=lambda x: (
        confidence.get((x.get("extra") or {}).get("llm_confidence", "none"), 2),
        len((x.get("gloss_en") or [""])[0]), x.get("id", ""),
    ))[0]


def definition_for(lemma: str, pos: str, exact, by_lemma):
    entry = choose_entry(exact.get((lemma, POS_CODE.get(pos, pos)), []))
    match = "exact"
    if entry is None:
        entry = choose_entry(by_lemma.get(lemma, []))
        match = "lemma_other_pos" if entry else "fallback"
    wn_en, wn_label, lexname = wordnet_sense(lemma, pos)
    if entry:
        en = (entry.get("gloss_en") or entry.get("senses_en") or [wn_en or "English vocabulary expression"])[0]
        vi = (entry.get("senses_vi") or [""])[0]
        label = (entry.get("senses_en") or [wn_label or lemma])[0]
    elif lemma in FUNCTION_WORDS:
        en, vi = FUNCTION_WORDS[lemma]
        label = lemma if len(lemma) >= 3 else f"{lemma} usage"
    elif wn_en:
        en, label = wn_en, wn_label or lemma
        vi = f"nghĩa cần biên tập: {lemma} — {wn_en}"
    else:
        en = f"an English {pos.replace('_', ' ')} used in advanced communication"
        vi = f"nghĩa cần biên tập cho từ {lemma}"
        label = f"{lemma} usage"
    en, vi, label = en.strip(), vi.strip(), str(label).strip()
    if len(en) < 5:
        en = f"the English {pos.replace('_', ' ')} '{en or lemma}'"
    if len(vi) < 2:
        vi = f"nghĩa tiếng Việt của {lemma} cần biên tập"
    if len(label) < 3:
        label = f"{lemma} usage"
    return en, vi, label, lexname, match


def topic_for(row: dict, lemma: str, pos: str, lexname: str | None, valid: set[str]):
    level = row["cefr_level"].lower()
    hinted = row.get("topic_hint", "").strip()
    candidates = [TOPIC_OVERRIDES.get(lemma, ""), hinted,
                  LEXNAME_TOPIC.get(lexname or "", ""), "business_office", "daily_life"]
    for topic in candidates:
        cid = f"vocab_{topic}_{level}" if topic else ""
        if cid in valid:
            return topic, cid
    # Some high-level topic/level combinations intentionally do not exist.
    level_candidates = [cid for cid in valid if cid.startswith("vocab_") and cid.endswith(f"_{level}")]
    if level_candidates:
        cid = sorted(level_candidates)[0]
        return cid[len("vocab_"):-(len(level) + 1)], cid
    return "daily_life", "vocab_daily_life_a1"


def fallback_examples(lemma: str, pos: str, en_def: str, vi_def: str):
    quoted = f'"{lemma}"'
    if pos == "noun":
        a = f"The training note uses {quoted} for {en_def.rstrip('.')} in this business context."
        av = f"Ghi chú đào tạo dùng {quoted} với nghĩa {vi_def.rstrip('.')} trong bối cảnh kinh doanh này."
        b = f"Before the meeting, the team checked how {quoted} relates to {en_def.rstrip('.')}."
        bv = f"Trước cuộc họp, nhóm đã kiểm tra cách {quoted} liên hệ với nghĩa {vi_def.rstrip('.')}."
    elif pos == "verb":
        a = f"In this instruction, to {lemma} means to {en_def.rstrip('.')}."
        av = f"Trong hướng dẫn này, {quoted} mang nghĩa {vi_def.rstrip('.')}."
        b = f"The editor kept {quoted} in the procedure because it expresses: {en_def.rstrip('.')}."
        bv = f"Biên tập viên giữ {quoted} trong quy trình vì từ này diễn đạt: {vi_def.rstrip('.')}."
    elif pos == "adjective":
        a = f"The reviewer described the proposal as {lemma}, meaning {en_def.rstrip('.')}."
        av = f"Người đánh giá mô tả đề xuất là {vi_def.rstrip('.')} bằng từ {quoted}."
        b = f"In the report, {quoted} characterizes something that is {en_def.rstrip('.')}."
        bv = f"Trong báo cáo, {quoted} mô tả điều có đặc điểm {vi_def.rstrip('.')}."
    elif pos == "adverb":
        a = f"The manager used {quoted} to show that an action happened {en_def.rstrip('.')}."
        av = f"Người quản lý dùng {quoted} để cho biết hành động diễn ra {vi_def.rstrip('.')}."
        b = f"In this sentence, {quoted} modifies the action with the sense {en_def.rstrip('.')}."
        bv = f"Trong câu này, {quoted} bổ nghĩa cho hành động với nghĩa {vi_def.rstrip('.')}."
    else:
        a = f"The guide uses {quoted} where it means {en_def.rstrip('.')}."
        av = f"Hướng dẫn dùng {quoted} tại vị trí mang nghĩa {vi_def.rstrip('.')}."
        b = f"The second example shows how {quoted} expresses {en_def.rstrip('.')}."
        bv = f"Ví dụ thứ hai cho thấy {quoted} diễn đạt {vi_def.rstrip('.')}."
    return [Example(sentence=a, translation=av), Example(sentence=b, translation=bv)]


def examples_for(lemma: str, pos: str, en_def: str, vi_def: str, tatoeba):
    corpus = []
    for en, vi, _ in tatoeba.get(lemma, []):
        if lemma not in tokens(en):
            continue
        pair = (en.strip(), vi.strip())
        if pair not in corpus:
            corpus.append(pair)
    # Prefer medium-length examples, then deterministic lexical order.
    corpus.sort(key=lambda x: (abs(len(x[0]) - 75), x[0].lower()))
    result = [Example(sentence=en, translation=vi, source=SourceType.CORPUS)
              for en, vi in corpus[:2]]
    for item in fallback_examples(lemma, pos, en_def, vi_def):
        if len(result) == 2:
            break
        result.append(item)
    return result


def phrase_score(text: str):
    # Phrase frequency plus a small preference for substantial two-word units.
    return zipf_frequency(text, "en") + min(len(text), 30) / 1000


def collocations_for(lemma: str, pos: str, level: CEFRLevel):
    if level not in {CEFRLevel.B2, CEFRLevel.C1}:
        return []
    candidates: list[tuple[CollocationPattern, str]] = []
    if pos == "noun":
        candidates += [(CollocationPattern.ADJ_N, f"{x} {lemma}") for x in MODIFIERS]
        candidates += [(CollocationPattern.V_N, f"{x} {lemma}") for x in VERBS]
        candidates += [(CollocationPattern.N_N, f"{lemma} {x}") for x in NOUN_OBJECTS]
    elif pos == "verb":
        candidates += [(CollocationPattern.V_PREP, f"{lemma} {x}") for x in PREPOSITIONS]
        candidates += [(CollocationPattern.V_N, f"{lemma} {x}") for x in NOUN_OBJECTS]
    elif pos == "adjective":
        candidates += [(CollocationPattern.ADV_ADJ, f"{x} {lemma}") for x in ADVERBS]
        candidates += [(CollocationPattern.ADJ_N, f"{lemma} {x}") for x in NOUN_OBJECTS]
    elif pos == "adverb":
        candidates += [(CollocationPattern.ADV_ADJ, f"{lemma} {x}") for x in ADJECTIVES]
    elif pos == "preposition":
        candidates += [(CollocationPattern.PREP_N, f"{lemma} {x}") for x in NOUN_OBJECTS]
    else:
        candidates += [(CollocationPattern.N_N, f"{lemma} {x}") for x in NOUN_OBJECTS]
    ranked = sorted(candidates, key=lambda x: (-phrase_score(x[1]), x[1]))
    chosen, patterns = [], set()
    for pattern, text in ranked:
        # Prefer different patterns, but allow repetition when the POS supports
        # only one schema pattern (for example an adverb).
        penalty = pattern in patterns and len({p for p, _ in candidates}) > 1
        if penalty and len(chosen) < 2:
            continue
        if text not in {c.text for c in chosen}:
            chosen.append(Collocation(pattern=pattern, text=text, cefr=level))
            patterns.add(pattern)
        if len(chosen) == 3:
            break
    return chosen


def main() -> int:
    taxonomy = yaml.safe_load((ROOT / "taxonomy" / "concepts.yaml").read_text(encoding="utf-8"))
    valid = {x["concept_id"] for x in taxonomy}
    seed = list(csv.DictReader(SEED.open(encoding="utf-8")))
    exact, by_lemma = load_dictionary()
    tatoeba = load_tatoeba()

    cards, stats = [], defaultdict(int)
    provenance = []
    for row in seed:
        lemma = row["lemma"].strip().lower()
        if lemma == "chauffer":
            lemma = "chauffeur"
        pos = row["pos"].strip().lower()
        level = CEFRLevel(row["cefr_level"].strip().upper())
        en_def, vi_def, label, lexname, match = definition_for(lemma, pos, exact, by_lemma)
        topic, cid = topic_for(row, lemma, pos, lexname, valid)
        examples = examples_for(lemma, pos, en_def, vi_def, tatoeba)
        corpus_n = sum(x.source is SourceType.CORPUS for x in examples)
        stats[f"dictionary_{match}"] += 1
        stats[f"examples_{corpus_n}_corpus"] += 1
        if "nghĩa cần biên tập" in vi_def:
            stats["needs_vi_editor"] += 1
        us, uk, verified = get_ipa(lemma)
        rank = int(row["frequency_rank"]) if row.get("frequency_rank", "").isdigit() else None
        prior = LEVEL_BASE[level.value] + (((rank or 1500) / 4000) * .08 - .04)
        source_raw = row.get("cefr_source", "cefrj").strip().lower()
        source = CEFRSource(source_raw) if source_raw in {x.value for x in CEFRSource} else CEFRSource.NGSL_BAND
        # The content generator is idempotent after TTS: rerunning it retains
        # only URLs whose stable-ID media files really exist.
        provisional_id = flashcard_id(lemma, pos, 1)
        us_file = PRONUNCIATION_MEDIA / f"{provisional_id}_us.mp3"
        uk_file = PRONUNCIATION_MEDIA / f"{provisional_id}_uk.mp3"
        card = Flashcard(
            lemma=lemma, pos=pos, sense_index=1, sense_label_en=label,
            ipa_us=us, ipa_uk=uk, ipa_verified=verified,
            audio_url_us=(f"{PRONUNCIATION_BASE}/{us_file.name}" if us_file.is_file() else None),
            audio_url_uk=(f"{PRONUNCIATION_BASE}/{uk_file.name}" if uk_file.is_file() else None),
            definition=Definition(en=en_def, vi=vi_def), examples=examples,
            collocations=collocations_for(lemma, pos, level),
            cefr_level=level, cefr_source=source, frequency_rank=rank,
            topics=[topic], concept_ids=[cid],
            difficulty_prior=max(.15, min(.90, round(prior, 3))),
            review_status=ReviewStatus.DRAFT,
        )
        cards.append(card)
        provenance.append({"id": card.id, "lemma": lemma, "pos": pos,
                           "dictionary_match": match, "corpus_examples": corpus_n})

    if len(cards) != 3000 or len({c.id for c in cards}) != 3000:
        raise RuntimeError(f"Expected 3,000 unique cards, got {len(cards)} / {len({c.id for c in cards})}")

    # Remove old JSON batches only after the full bank has validated in memory.
    batches = []
    now = dt.datetime.now(dt.UTC)
    for offset in range(0, len(cards), 1000):
        chunk = cards[offset:offset + 1000]
        number = offset // 1000 + 1
        batch = FlashcardBatch(
            batch_metadata=BatchMetadata(
                batch_id=f"flashcard_batch_{number:03d}", module_type=ModuleType.FLASHCARD,
                generated_by="build_flashcard_bank.py/open-data-v1", generated_at=now,
                review_status=ReviewStatus.DRAFT, total_records=len(chunk),
            ), flashcards=chunk,
        )
        batches.append((OUT / f"flashcard_batch_{number:03d}.json", batch))

    for path, batch in batches:
        guarded_write_batch(batch, path)

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps({
        "generated_at": now.isoformat(), "total_cards": len(cards),
        "cefr_counts": {level: sum(c.cefr_level.value == level for c in cards)
                        for level in ["A1", "A2", "B1", "B2", "C1"]},
        "stats": dict(sorted(stats.items())),
        "license_notice": "Derived dictionary data: CC BY-SA 4.0; Tatoeba examples: CC BY 2.0 France.",
        "human_review": "not_performed", "provenance": provenance,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(sorted(stats.items())), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
