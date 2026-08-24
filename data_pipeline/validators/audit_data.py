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
import re
import statistics
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlparse

import yaml
from rapidfuzz import fuzz

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import (  # noqa: E402
    AssessmentPromptBatch, AssessmentResult, ExamBatch, FlashcardBatch,
    GrammarBatch, ShadowingBatch, SpeakingBatch, WritingBatch,
)
from validators.exam_set_rules import check_exam_collection, check_exam_set  # noqa: E402
from validators.part_rules import check_groups  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "output"
NEAR_DUP = 92          # ngưỡng rapidfuzz của §Phase 3
MIN_ITEMS_PER_CONCEPT = 10   # ngưỡng BKT hội tụ
ACCENT_TARGET = {"US": 0.50, "UK": 0.17, "AU": 0.17, "CA": 0.17}
NUMBERED_BLANK = re.compile(
    r"(?:chỗ trống|blank)\s*\(\d+\)", re.IGNORECASE)

findings: list[tuple[str, str]] = []      # (mức, mô tả)


def dedup_context(g) -> str:
    """Ngữ cảnh dùng để so trùng câu hỏi: passage với phần đọc, kịch bản audio
    với phần nghe. Ở mức module để test chặn được hồi quy — luật này đã sai
    hai lần (Part 6 mất 12 câu, Part 3 bị báo trùng oan)."""
    if g.passages:
        return "|".join(p.text[:200] for p in g.passages)
    return g.audio.script[:200] if g.audio else ""


def near_duplicate_pairs(records: list[tuple[str, str]], threshold: int = NEAR_DUP
                         ) -> list[tuple[tuple[str, str], tuple[str, str]]]:
    """Return near-duplicate questions only when both stem and context match.

    Standard exam stems are intentionally reused across unrelated passages and
    recordings. Numbered Part 6 blanks also share a context by design, so their
    stems are exempt; other near-identical stems in the same context remain a
    useful duplicate signal.
    """
    unique = list(dict.fromkeys(records))
    pairs = []
    for i, left in enumerate(unique):
        left_stem, left_context = left
        for right in unique[i + 1:]:
            right_stem, right_context = right
            if (left_context == right_context
                    and NUMBERED_BLANK.search(left_stem)
                    and NUMBERED_BLANK.search(right_stem)):
                continue
            if (fuzz.ratio(left_stem, right_stem) >= threshold
                    and fuzz.ratio(left_context, right_context) >= threshold):
                pairs.append((left, right))
    return pairs



def flag(level: str, msg: str) -> None:
    findings.append((level, msg))


def hdr(t: str) -> None:
    print(f"\n{'═' * 74}\n{t}\n{'═' * 74}")


def probe_duration_ms(path: Path) -> int:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
        check=True, capture_output=True, text=True,
    )
    return round(float(result.stdout.strip()) * 1000)


def load_batches(subdir: str, model, excluded_dirs: set[str] | None = None):
    """Parse + validate mọi batch. Trả về (batches, lỗi)."""
    d = OUTPUT / subdir
    ok, errs = [], []
    excluded_dirs = excluded_dirs or set()
    if not d.exists():
        return ok, errs
    for p in sorted(d.rglob("*.json")):
        if p.name.startswith("."):
            continue
        if excluded_dirs.intersection(p.relative_to(d).parts[:-1]):
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
    # Delivery packages intentionally duplicate selected source records and
    # therefore are excluded from source-of-truth quality statistics.
    exams, e_err = load_batches("exams", ExamBatch, {"individual_sets"})
    cards, c_err = load_batches("flashcards", FlashcardBatch)
    gram, g_err = load_batches("grammar", GrammarBatch)
    spk, s_err = load_batches("speaking_writing", SpeakingBatch)
    wrt, w_err = load_batches("speaking_writing", WritingBatch)
    shadow, sh_err = load_batches("shadowing", ShadowingBatch)
    prompt_path = OUTPUT / "prompts" / "assessment_prompts_batch_001.json"
    prompts, p_err = [], []
    if prompt_path.exists():
        try:
            prompts.append((prompt_path.name, AssessmentPromptBatch.model_validate(
                json.loads(prompt_path.read_text(encoding="utf-8")))))
        except Exception as exc:
            p_err.append((prompt_path.name, str(exc)[:160]))
    for name, batches, errs in [("exam", exams, e_err), ("flashcard", cards, c_err),
                                ("grammar", gram, g_err), ("shadowing", shadow, sh_err),
                                ("assessment", prompts, p_err)]:
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
    shadow_clips = [c for _, b in shadow for c in b.clips]
    assessment_prompts = [p for _, b in prompts for p in b.prompts]
    print(f"\n  Tổng: {len(groups)} group, {len(items)} câu hỏi, "
          f"{len(flashcards)} flashcard, {len(gpoints)} grammar point, {len(sets_)} bộ đề, "
          f"{len(sp_tasks)} speaking + {len(wr_tasks)} writing task, "
          f"{len(shadow_clips)} shadowing/dictation clip, {len(assessment_prompts)} prompt")

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

    # So theo (stem + NGỮ CẢNH), không theo stem trần. Ngữ cảnh là passage với
    # phần đọc, và là kịch bản audio với phần nghe:
    #   - Part 6 dùng chung "Chỗ trống (1)" ở nhiều đoạn khác nhau
    #   - Part 3/4 dùng chung "What are the speakers discussing?" ở nhiều
    #     hội thoại khác nhau — đúng như đề thật
    # Cả hai đều là câu khác nhau, không phải bản sao. Lần trước đã sửa cho
    # passage nhưng bỏ sót audio, nên Part 3 vừa bị báo trùng oan.
    question_records = [(q.question_text, dedup_context(g))
                        for g in groups for q in g.questions if q.question_text]
    texts = [f"{stem}##{context}" for stem, context in question_records]
    exact = [k for k, v in collections.Counter(texts).items() if v > 1]
    print(f"  question_text trùng nguyên văn: {len(exact)} chuỗi "
          f"(chiếm {sum(collections.Counter(texts)[k] for k in exact)} câu)")
    if exact:
        flag("LỖI", f"{len(exact)} câu hỏi trùng nguyên văn")
        for t in exact[:3]:
            print(f"      ×{collections.Counter(texts)[t]}  {t.split('##')[0][:70]}")

    sample = list(dict.fromkeys(question_records))[:400]
    near = near_duplicate_pairs(sample)
    print(f"  gần trùng (rapidfuzz ≥{NEAR_DUP}) trên {len(sample)} câu đầu: {len(near)} cặp")
    if near:
        flag("CẢNH BÁO", f"{len(near)} cặp câu hỏi gần trùng nhau trong {len(sample)} câu mẫu")

    # ---------- D. Thiên lệch ----------
    hdr("D. THIÊN LỆCH THỐNG KÊ")
    n = len(items)
    # Tách theo SỐ LỰA CHỌN. Part 2 chỉ có 3 phương án nên không bao giờ có D;
    # gộp chung với câu 4 lựa chọn thì D luôn bị kéo xuống dưới 20% một cách
    # giả tạo và audit sẽ báo động nhầm. Ngưỡng cũng khác nhau: đều tay là 25%
    # với 4 lựa chọn nhưng 33% với 3 lựa chọn.
    for k_opt, labels, lo, hi in ((3, "ABC", 0.27, 0.40), (4, "ABCD", 0.20, 0.30)):
        subset = [q for q in items if len(q.options) == k_opt]
        if not subset:
            continue
        m = len(subset)
        pos = collections.Counter(
            next(o.label for o in q.options if o.is_correct) for q in subset)
        print(f"  B-1 vị trí đáp án đúng ({k_opt} lựa chọn, {m} câu): " +
              "  ".join(f"{k}={pos[k]} ({pos[k]/m*100:.0f}%)" for k in labels))
        for k in labels:
            share = pos[k] / m
            if not (lo <= share <= hi):
                flag("CẢNH BÁO", f"B-1 ({k_opt} lựa chọn): nhãn {k} chiếm "
                                 f"{share*100:.0f}%, ngoài {lo*100:.0f}–{hi*100:.0f}%")

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
    for clip in shadow_clips:
        used.update(clip.concept_ids)
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
        print(f"  định nghĩa gần nhau (≥{NEAR_DUP}) trên {len(defs)} mẫu: {nd} cặp "
              "(thống kê; synonym/khác từ loại được phép)")
        required_collocations = [f for f in flashcards if f.cefr_level.value in {"B2", "C1"}]
        complete_collocations = sum(len(f.collocations) >= 3 for f in required_collocations)
        print(f"  B2/C1 có >=3 collocation: {complete_collocations}/{len(required_collocations)}")
        if complete_collocations != len(required_collocations):
            flag("LỖI", "Flashcard B2/C1 chưa đủ tối thiểu 3 collocation")

        pronunciation_dir = OUTPUT / "media" / "audio" / "flashcards"
        missing_pronunciation = []
        for card in flashcards:
            for accent, value in (("us", card.audio_url_us), ("uk", card.audio_url_uk)):
                filename = Path(urlparse(str(value)).path).name if value else ""
                path = pronunciation_dir / filename
                if not filename or not path.is_file() or path.stat().st_size <= 300:
                    missing_pronunciation.append(f"{card.id}:{accent}")
        print(f"  audio phát âm US/UK: {len(flashcards) * 2 - len(missing_pronunciation)}/"
              f"{len(flashcards) * 2}")
        if missing_pronunciation:
            flag("LỖI", f"{len(missing_pronunciation)} audio phát âm flashcard còn thiếu")

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
        if with_url != len(audios):
            flag("LỖI", f"{len(audios) - with_url} Listening audio chưa có audio_url thật")
        if aligned != len(audios):
            flag("LỖI", f"{len(audios) - aligned} Listening audio chưa có cue alignment đã đo")
        media_dir = OUTPUT / "media" / "audio" / "toeic" / "listening"
        missing_files = []
        incomplete_metadata = 0
        duration_mismatches = 0
        for audio in audios:
            if not audio.audio_url:
                continue
            filename = Path(urlparse(str(audio.audio_url)).path).name
            local_file = media_dir / filename
            if not local_file.is_file() or local_file.stat().st_size == 0:
                missing_files.append(filename)
            elif audio.duration_ms is not None:
                measured = probe_duration_ms(local_file)
                if abs(measured - audio.duration_ms) > 250:
                    duration_mismatches += 1
            if not audio.duration_ms or not audio.cues:
                incomplete_metadata += 1
        print(f"  MP3 hiện hữu: {with_url - len(missing_files)}/{with_url}   "
              f"đủ duration+cues: {with_url - incomplete_metadata}/{with_url}")
        if missing_files:
            flag("LỖI", f"{len(missing_files)} audio_url không có tệp MP3 cục bộ tương ứng")
        if incomplete_metadata:
            flag("LỖI", f"{incomplete_metadata} audio thiếu duration_ms hoặc cue timing thật")
        if duration_mismatches:
            flag("LỖI", f"{duration_mismatches} audio có duration_ms lệch ffprobe quá 250 ms")

    # ---------- I.2 Media used by exam and Speaking/Writing ----------
    hdr("I.2 MEDIA FILES")
    image_urls = [str(g.image_url) for g in groups if g.image_url]
    image_urls += [str(t.image_url) for t in sp_tasks + wr_tasks if t.image_url]
    missing_images = []
    for url in image_urls:
        relative = urlparse(url).path.removeprefix("/images/")
        local_file = OUTPUT / "media" / "images" / relative
        if not local_file.is_file() or local_file.stat().st_size == 0:
            missing_images.append(relative)
        elif local_file.suffix.lower() in (".jpg", ".jpeg"):
            with local_file.open("rb") as stream:
                if stream.read(3) != b"\xff\xd8\xff":
                    missing_images.append(f"{relative} (not JPEG content)")
    speaking_audio = [t.audio for t in sp_tasks if t.audio]
    missing_speaking_audio = []
    for audio in speaking_audio:
        filename = Path(urlparse(str(audio.audio_url)).path).name if audio.audio_url else ""
        local_file = OUTPUT / "media" / "audio" / "toeic" / "speaking" / filename
        if not filename or not local_file.is_file() or local_file.stat().st_size == 0:
            missing_speaking_audio.append(filename or "<no URL>")
    print(f"  image_url có tệp cục bộ: {len(image_urls) - len(missing_images)}/{len(image_urls)}")
    print(f"  Speaking prompt audio: {len(speaking_audio) - len(missing_speaking_audio)}/"
          f"{len(speaking_audio)}")
    if missing_images:
        flag("LỖI", f"{len(missing_images)} image_url không có tệp media cục bộ")
    if missing_speaking_audio:
        flag("LỖI", f"{len(missing_speaking_audio)} Speaking audio thiếu tệp cục bộ")

    # ---------- I.3 Shadowing / dictation + assessment prompts ----------
    hdr("I.3 SHADOWING / DICTATION / ASSESSMENT")
    shadow_media = OUTPUT / "media" / "audio" / "shadowing"
    bad_shadow = []
    for clip in shadow_clips:
        filename = Path(urlparse(str(clip.audio_url)).path).name if clip.audio_url else ""
        local = shadow_media / filename
        timing_ok = all(s.start_ms is not None and s.end_ms is not None
                        and s.end_ms > s.start_ms for s in clip.segments)
        if (not filename or not local.is_file() or local.stat().st_size == 0
                or clip.duration_ms is None or not 30_000 <= clip.duration_ms <= 60_000
                or not timing_ok or set(clip.practice_modes) != {"shadowing", "dictation"}):
            bad_shadow.append(clip.clip_id)
    print(f"  clip đủ MP3 + timestamp + 30–60 giây + 2 mode: "
          f"{len(shadow_clips) - len(bad_shadow)}/{len(shadow_clips)}")
    if len(shadow_clips) != 30 or bad_shadow:
        flag("LỖI", f"Shadowing/dictation không đạt blueprint: total={len(shadow_clips)}, bad={len(bad_shadow)}")

    fixture_path = OUTPUT / "prompts" / "assessment_calibration_cases.json"
    bad_cases, case_count = [], 0
    prompt_ids = {p.prompt_id for p in assessment_prompts}
    if fixture_path.exists():
        fixture_data = json.loads(fixture_path.read_text(encoding="utf-8"))
        for case in fixture_data.get("cases", []):
            case_count += 1
            try:
                AssessmentResult.model_validate(case["expected_result"])
                if case["prompt_id"] not in prompt_ids:
                    raise ValueError("prompt_id not found")
            except Exception as exc:
                bad_cases.append(f"{case.get('case_id')}: {exc}")
    print(f"  assessment prompt: {len(assessment_prompts)}/2; "
          f"fixture hợp schema: {case_count - len(bad_cases)}/{case_count}")
    if len(assessment_prompts) != 2 or case_count != 10 or bad_cases:
        flag("LỖI", "Assessment prompt/calibration fixture chưa đủ hoặc sai schema")

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
            for error in check_exam_set(s, groups):
                flag("LỖI", f"{s.set_id}: {error}")
        for error in check_exam_collection(sets_, groups):
            flag("LỖI", f"10-set collection: {error}")

    # ---------- Tổng kết ----------
    hdr("TỔNG KẾT")
    lois = [f for f in findings if f[0] == "LỖI"]
    canh = [f for f in findings if f[0] == "CẢNH BÁO"]
    print(f"  {len(lois)} LỖI, {len(canh)} CẢNH BÁO\n")
    for lvl, msg in lois + canh:
        print(f"  [{lvl:9}] {msg}")
    return 1 if lois else 0


if __name__ == "__main__":
    reconfigure = getattr(sys.stdout, "reconfigure", None)
    if callable(reconfigure):
        reconfigure(encoding="utf-8")
    sys.exit(main())
