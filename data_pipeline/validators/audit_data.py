#!/usr/bin/env python3
"""Audit toàn bộ dữ liệu đang có trên đĩa.

Không tin con số nào chưa tự đo. Kiểm:
  A. Mọi batch có parse và validate được bằng schema không
  B. Ràng buộc part 1–7 (§2.5)
  C. Trùng lặp — ID trùng, nội dung gần trùng (rapidfuzz ≥0.92)
  D. Thiên lệch thống kê B-1 / B-2 (docs/exam-quality-bar.md §4)
  E. Phủ concept — concept nào 0 item, concept nào <10 item
  F. Phân bố difficulty_prior (dồn quanh 0.5 → prior vô dụng cho Elo)
  G. concept_ids có tồn tại trong taxonomy không
  H. Flashcard: IPA, collocation B2/C1, trùng nghĩa
  I. Listening: phân bố accent, audio_url giả
  J. Bộ đề: thành phần 200 câu

    python validators/audit_data.py
"""

from __future__ import annotations

import collections
import json
import statistics
import sys
from pathlib import Path

import yaml
from rapidfuzz import fuzz

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import (  # noqa: E402
    ExamBatch, FlashcardBatch, GrammarBatch, SpeakingBatch, WritingBatch,
)
from validators.part_rules import check_groups  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "output"
NEAR_DUP = 92          # ngưỡng rapidfuzz của §Phase 3
MIN_ITEMS_PER_CONCEPT = 10   # ngưỡng BKT hội tụ
ACCENT_TARGET = {"US": 0.50, "UK": 0.17, "AU": 0.17, "CA": 0.17}

findings: list[tuple[str, str]] = []      # (mức, mô tả)


def flag(level: str, msg: str) -> None:
    findings.append((level, msg))


def hdr(t: str) -> None:
    print(f"\n{'═' * 74}\n{t}\n{'═' * 74}")


def load_batches(subdir: str, model):
    """Parse + validate mọi batch. Trả về (batches, lỗi)."""
    d = OUTPUT / subdir
    ok, errs = [], []
    if not d.exists():
        return ok, errs
    for p in sorted(d.rglob("*.json")):
        if p.name.startswith("."):
            continue
        try:
            ok.append((p.name, model.model_validate(json.loads(p.read_text(encoding="utf-8")))))
        except Exception as e:
            errs.append((p.name, str(e)[:160]))
    return ok, errs


def main() -> int:
    taxonomy = yaml.safe_load((ROOT / "taxonomy" / "concepts.yaml").read_text(encoding="utf-8"))
    all_cids = {c["concept_id"] for c in taxonomy}
    kids = collections.defaultdict(int)
    for c in taxonomy:
        if c.get("parent_id"):
            kids[c["parent_id"]] += 1
    leaves = {c["concept_id"] for c in taxonomy if not kids[c["concept_id"]]}

    # ---------- A. Parse + validate ----------
    hdr("A. PARSE + VALIDATE SCHEMA")
    exams, e_err = load_batches("exams", ExamBatch)
    cards, c_err = load_batches("flashcards", FlashcardBatch)
    gram, g_err = load_batches("grammar", GrammarBatch)
    spk, s_err = load_batches("speaking_writing", SpeakingBatch)
    wrt, w_err = load_batches("speaking_writing", WritingBatch)
    for name, batches, errs in [("exam", exams, e_err), ("flashcard", cards, c_err),
                                ("grammar", gram, g_err)]:
        print(f"  {name:10} {len(batches):3d} batch OK, {len(errs)} lỗi")
        for f, msg in errs[:3]:
            print(f"      ✗ {f}: {msg}")
            flag("LỖI", f"{f} không validate được: {msg[:80]}")

    groups = [g for _, b in exams for g in b.groups]
    items = [q for g in groups for q in g.questions]
    flashcards = [f for _, b in cards for f in b.flashcards]
    gpoints = [p for _, b in gram for p in b.grammar_points]
    sp_tasks = [t for _, b in spk for t in b.tasks]
    wr_tasks = [t for _, b in wrt for t in b.tasks]
    sets_ = [s for _, b in exams for s in b.sets]
    print(f"\n  Tổng: {len(groups)} group, {len(items)} câu hỏi, "
          f"{len(flashcards)} flashcard, {len(gpoints)} grammar point, {len(sets_)} bộ đề, "
          f"{len(sp_tasks)} speaking + {len(wr_tasks)} writing task")

    # ---------- B. Part rules ----------
    hdr("B. RÀNG BUỘC PART 1–7")
    errs = check_groups(groups)
    print(f"  {len(errs)}/{len(groups)} group vi phạm")
    if errs:
        flag("LỖI", f"{len(errs)} group vi phạm ràng buộc part")
        reasons = collections.Counter(
            e.split("(")[0].strip()[:60] for v in errs.values() for e in v)
        for r, n in reasons.most_common(6):
            print(f"      {n:4d}×  {r}")

    # ---------- C. Trùng lặp ----------
    hdr("C. TRÙNG LẶP")
    ids = [q.item_id for q in items]
    dup_id = [k for k, v in collections.Counter(ids).items() if v > 1]
    print(f"  item_id trùng: {len(dup_id)}/{len(ids)}")
    if dup_id:
        flag("LỖI", f"{len(dup_id)} item_id trùng — cùng nội dung bị sinh nhiều lần")

    texts = [q.question_text or "" for q in items if q.question_text]
    exact = [k for k, v in collections.Counter(texts).items() if v > 1]
    print(f"  question_text trùng nguyên văn: {len(exact)} chuỗi "
          f"(chiếm {sum(collections.Counter(texts)[k] for k in exact)} câu)")
    if exact:
        flag("LỖI", f"{len(exact)} câu hỏi trùng nguyên văn")
        for t in exact[:3]:
            print(f"      ×{collections.Counter(texts)[t]}  {t[:70]}")

    uniq = list(dict.fromkeys(texts))
    sample = uniq[:400]
    near = 0
    for i in range(len(sample)):
        for j in range(i + 1, len(sample)):
            if fuzz.ratio(sample[i], sample[j]) >= NEAR_DUP:
                near += 1
    print(f"  gần trùng (rapidfuzz ≥{NEAR_DUP}) trên {len(sample)} câu đầu: {near} cặp")
    if near:
        flag("CẢNH BÁO", f"{near} cặp câu hỏi gần trùng nhau trong {len(sample)} câu mẫu")

    # ---------- D. Thiên lệch ----------
    hdr("D. THIÊN LỆCH THỐNG KÊ")
    n = len(items)
    pos = collections.Counter(next(o.label for o in q.options if o.is_correct) for q in items)
    print("  B-1 vị trí đáp án đúng: " +
          "  ".join(f"{k}={pos[k]} ({pos[k]/n*100:.0f}%)" for k in "ABCD"))
    for k in "ABCD":
        share = pos[k] / n
        if share and not (0.20 <= share <= 0.30):
            flag("CẢNH BÁO", f"B-1: nhãn {k} chiếm {share*100:.0f}%, ngoài 20–30%")

    longest = sum(1 for q in items if max(q.options, key=lambda o: len(o.text)).is_correct)
    print(f"  B-2 đáp án đúng dài nhất: {longest}/{n} ({longest/n*100:.0f}%)")
    if longest / n > 0.35:
        flag("CẢNH BÁO", f"B-2: {longest/n*100:.0f}% vượt ngưỡng 35%")

    # ---------- E. Phủ concept ----------
    hdr("E. PHỦ CONCEPT")
    used = collections.Counter()
    for q in items:
        used.update(q.concept_ids)
    for f in flashcards:
        used.update(f.concept_ids)
    for p in gpoints:
        used.update(p.concept_ids)
    for t in sp_tasks + wr_tasks:
        used.update(t.concept_ids)
    zero = sorted(leaves - set(used))
    thin = sorted([c for c in leaves if 0 < used[c] < MIN_ITEMS_PER_CONCEPT],
                  key=lambda c: used[c])
    print(f"  concept lá: {len(leaves)}")
    print(f"    có ≥{MIN_ITEMS_PER_CONCEPT} item : {len(leaves) - len(zero) - len(thin)}")
    print(f"    có 1–{MIN_ITEMS_PER_CONCEPT-1} item: {len(thin)}")
    print(f"    có 0 item        : {len(zero)}")
    if zero:
        flag("CẢNH BÁO", f"{len(zero)} concept lá không có item nào — BKT không cập nhật được")
        print(f"      ví dụ 0 item: {zero[:8]}")
    if thin:
        print(f"      ví dụ thiếu : {[(c, used[c]) for c in thin[:6]]}")

    # ---------- F. difficulty_prior ----------
    hdr("F. PHÂN BỐ difficulty_prior")
    d = [q.difficulty_prior for q in items]
    if d:
        print(f"  n={len(d)}  min={min(d):.2f}  median={statistics.median(d):.2f}  "
              f"max={max(d):.2f}  stdev={statistics.pstdev(d):.3f}")
        buckets = collections.Counter(min(int(x * 10), 9) for x in d)
        for b in range(10):
            bar = "█" * int(buckets[b] / max(buckets.values()) * 40) if buckets else ""
            print(f"    {b/10:.1f}–{(b+1)/10:.1f}  {buckets[b]:5d}  {bar}")
        if statistics.pstdev(d) < 0.10:
            flag("CẢNH BÁO",
                 f"difficulty_prior dồn cục (stdev={statistics.pstdev(d):.3f}) — prior vô dụng cho Elo")

    # ---------- G. concept_ids mồ côi ----------
    hdr("G. concept_ids MỒ CÔI")
    orphan = sorted(set(used) - all_cids)
    print(f"  concept_ids không có trong taxonomy: {len(orphan)}")
    if orphan:
        flag("LỖI", f"{len(orphan)} concept_id mồ côi: {orphan[:5]}")

    # ---------- H. Flashcard ----------
    if flashcards:
        hdr("H. FLASHCARD")
        ver = sum(1 for f in flashcards if f.ipa_verified)
        print(f"  ipa_verified: {ver}/{len(flashcards)} ({ver/len(flashcards)*100:.0f}%)")
        if ver / len(flashcards) < 0.5:
            flag("CẢNH BÁO",
                 f"chỉ {ver/len(flashcards)*100:.0f}% flashcard có ipa_verified — "
                 "§Phase 5 bắt đối chiếu CMUdict, không tin IPA do LLM sinh")
        lv = collections.Counter(f.cefr_level.value for f in flashcards)
        print(f"  theo band: {dict(sorted(lv.items()))}")
        srcs = collections.Counter(f.cefr_source.value for f in flashcards)
        print(f"  cefr_source: {dict(srcs)}")
        if srcs.get("llm_estimate", 0):
            flag("CẢNH BÁO",
                 f"{srcs['llm_estimate']} flashcard có cefr_source=llm_estimate — không truy vết được")
        keys = [(f.lemma, f.pos.value, f.sense_index) for f in flashcards]
        dup = [k for k, v in collections.Counter(keys).items() if v > 1]
        print(f"  (lemma,pos,sense) trùng: {len(dup)}")
        if dup:
            flag("LỖI", f"{len(dup)} flashcard trùng khoá (lemma,pos,sense_index)")
        defs = [f.definition.en for f in flashcards][:400]
        nd = sum(1 for i in range(len(defs)) for j in range(i + 1, len(defs))
                 if fuzz.ratio(defs[i], defs[j]) >= NEAR_DUP)
        print(f"  định nghĩa gần trùng (≥{NEAR_DUP}) trên {len(defs)} mẫu: {nd} cặp")
        if nd:
            flag("CẢNH BÁO", f"{nd} cặp định nghĩa flashcard gần trùng nhau")

    # ---------- I. Listening ----------
    audios = [g.audio for g in groups if g.audio]
    if audios:
        hdr("I. LISTENING / AUDIO")
        ac = collections.Counter(a.accent.value for a in audios)
        print(f"  {len(audios)} audio asset")
        for k, target in ACCENT_TARGET.items():
            share = ac[k] / len(audios)
            mark = "" if abs(share - target) <= 0.08 else "  ⚠ lệch chỉ tiêu"
            print(f"    {k}  {ac[k]:4d}  {share*100:5.1f}%   (chỉ tiêu {target*100:.0f}%){mark}")
            if abs(share - target) > 0.08:
                flag("CẢNH BÁO", f"accent {k} chiếm {share*100:.0f}%, chỉ tiêu {target*100:.0f}%")
        with_url = sum(1 for a in audios if a.audio_url)
        aligned = sum(1 for a in audios if a.alignment_status.value == "aligned")
        print(f"  có audio_url: {with_url}/{len(audios)}   alignment=aligned: {aligned}")
        if with_url:
            flag("LỖI", f"{with_url} audio có URL nhưng chưa có TTS engine nào chạy — "
                        "§Phase 8 cấm nhét URL giả")

    # ---------- J. Bộ đề ----------
    if sets_:
        hdr("J. BỘ ĐỀ")
        item_ids = set(ids)
        for s in sets_[:12]:
            nl, nr = len(s.listening), len(s.reading)
            miss = sum(1 for r in list(s.listening) + list(s.reading)
                       if r.item_id not in item_ids)
            mark = "" if (nl, nr) == (100, 100) else "  ⚠ không đủ 100+100"
            m2 = "" if not miss else f"  ⚠ {miss} ref trỏ tới item không tồn tại"
            print(f"  {s.set_id:14} L={nl:3d} R={nr:3d} tổng={s.total_questions:3d}{mark}{m2}")
            if (nl, nr) != (100, 100):
                flag("CẢNH BÁO", f"{s.set_id}: L={nl} R={nr}, chuẩn là 100+100")
            if miss:
                flag("LỖI", f"{s.set_id}: {miss} tham chiếu trỏ tới item_id không tồn tại")

    # ---------- Tổng kết ----------
    hdr("TỔNG KẾT")
    lois = [f for f in findings if f[0] == "LỖI"]
    canh = [f for f in findings if f[0] == "CẢNH BÁO"]
    print(f"  {len(lois)} LỖI, {len(canh)} CẢNH BÁO\n")
    for lvl, msg in lois + canh:
        print(f"  [{lvl:9}] {msg}")
    return 1 if lois else 0


if __name__ == "__main__":
    sys.exit(main())
