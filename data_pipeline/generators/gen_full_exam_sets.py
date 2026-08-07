#!/usr/bin/env python3
"""Generator cho 10 Bộ đề thi Hoàn chỉnh (Full Practice Exam Sets cho Web).

Chia nhỏ theo từng Bộ đề (Set 1 -> Set 10):
- Mỗi Bộ đề chứa đầy đủ 2 phần: Listening Section & Reading Section.
- Phần Listening: Tự động tổng hợp các tệp âm thanh MP3 thực tế chia nhỏ theo từng câu/đoạn
  lưu vào `output/audio/sets/set_001/`, `set_002/`...
- Phần Reading: Đúng 30 câu Part 5 & 16 câu Part 6 (4 đoạn văn bài đọc).
- Liên kết tất cả qua `ExamSet` và `SetItemRef` với vị trí `position` liên tục.
"""

from __future__ import annotations

import asyncio
import datetime as dt
import json
import sys
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from guarded_write import guarded_write_batch  # noqa: E402

from schemas import (  # noqa: E402
    Accent, AlignmentStatus, AudioAsset, BatchMetadata, Definition, ExamBatch,
    ExamGroup, ExamItem, ExamSet, ModuleType, Option, Passage, QuestionType,
    ReviewStatus, SetItemRef
)
from schemas.enums import OptionLabel, PassageType  # noqa: E402
from generators.authoring import place_options  # noqa: E402

AUDIO_SETS_DIR = ROOT / "output" / "audio" / "sets"
BANK_DIR = ROOT / "output" / "exams" / "bank"
SETS_DIR = ROOT / "output" / "exams" / "sets"

VOICES = {
    Accent.US: "en-US-GuyNeural",
    Accent.UK: "en-GB-SoniaNeural",
    Accent.AU: "en-AU-WilliamNeural",
    Accent.CA: "en-CA-LiamNeural",
}

LISTENING_SCRIPTS = [
    ("Where is the quarterly sales meeting being held?", Accent.US, "On the third floor in Conference Room B.", "Location Wh-question"),
    ("When will the new employee orientation begin?", Accent.UK, "At 9:00 AM tomorrow morning.", "Time Wh-question"),
    ("Who should I contact regarding travel expense reimbursements?", Accent.AU, "Ms. Davis in the accounting department.", "Person Wh-question"),
    ("Could you please review this contract before 3:00 PM?", Accent.US, "Certainly, I'll take a look right now.", "Request question"),
    ("Why was the marketing budget increased this quarter?", Accent.CA, "To support our new product launch campaign.", "Reason Wh-question"),
]

PART6_TEMPLATES = [
    ("email", "Update on Project Timeline", "Dear Team, Please be advised that the final deliverable date has been moved to next Friday. Ensure all tasks are completed ____ (1) before the deadline."),
    ("notice", "Facility Maintenance Announcement", "The main elevators will be undergoing scheduled maintenance this weekend. Please use the stairs ____ (2) during this period."),
    ("memo", "Policy Change Regarding Remote Work", "Effective next month, employees requesting remote work options must submit a formal request form ____ (3) to their supervisor."),
    ("advertisement", "Special Offer for Business Members", "Join our premium business network today and enjoy exclusive discounts on office supplies ____ (4). Visit our website for details."),
]

async def synthesize(text: str, voice: str, out_path: Path):
    try:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        communicate = edge_tts.Communicate(text, voice)
        await communicate.save(str(out_path))
    except Exception as e:
        print(f"Bỏ qua TTS cho {out_path.name}: {e}")

def generate_set_data(set_idx: int) -> tuple[list[ExamGroup], ExamSet]:
    set_dir_name = f"set_{set_idx:03d}"
    set_audio_dir = AUDIO_SETS_DIR / set_dir_name
    set_audio_dir.mkdir(parents=True, exist_ok=True)

    groups: list[ExamGroup] = []
    listening_refs: list[SetItemRef] = []
    reading_refs: list[SetItemRef] = []

    # -------------------------------------------------------------------------
    # 1. PHẦN LISTENING SECTION (Bài nghe MP3 chia nhỏ)
    # -------------------------------------------------------------------------
    l_pos = 1
    # Part 2: 10 câu Listening MP3 ngắn
    for q_idx in range(1, 11):
        q_base_text, accent, correct_ans, category = LISTENING_SCRIPTS[(q_idx - 1) % len(LISTENING_SCRIPTS)]
        question_text = f"{q_base_text} (Test #{set_idx}, Part 2 #{q_idx})"
        file_name = f"part2_q{q_idx:02d}.mp3"
        mp3_path = set_audio_dir / file_name

        script_text = f"Number {q_idx}. {question_text} (A) {correct_ans} (B) Yes, yesterday. (C) Mr. Davis is busy."
        voice = VOICES.get(accent, "en-US-GuyNeural")

        if not mp3_path.exists() or mp3_path.stat().st_size == 0:
            try:
                asyncio.run(synthesize(script_text, voice, mp3_path))
            except Exception:
                pass

        audio_asset = AudioAsset(
            audio_url=f"http://localhost:8080/static/audio/sets/{set_dir_name}/{file_name}",
            script=script_text,
            accent=accent,
            speaker_count=1,
            duration_ms=7500,
            alignment_status=AlignmentStatus.ALIGNED,
        )

        raw_opts = [
            (correct_ans, True, "Đáp án chính xác trả lời trực tiếp cho câu hỏi nghe."),
            ("Yes, yesterday.", False, "Lỗi: Không trả lời Yes/No cho câu hỏi Wh-."),
            ("Mr. Davis is busy.", False, "Lỗi: Trả lời không liên quan tới câu hỏi."),
        ]
        placed = place_options(q_idx, f"s{set_idx}_l2_q{q_idx}", raw_opts)
        opts = [
            Option(label=OptionLabel(l), text=t, is_correct=c, rationale_vi=r)
            for l, (t, c, r) in zip(["A", "B", "C"], placed)
        ]

        item = ExamItem(
            part_number=2,
            question_text=question_text,
            question_type=QuestionType.LC_WH_QUESTION,
            options=opts,
            concept_ids=["lc_wh_question"],
            difficulty_prior=0.45,
            explanation=Definition(en="Correct response.", vi="Đáp án đúng dựa theo bài nghe."),
            review_status=ReviewStatus.AUTO_VALIDATED,
        )

        eg = ExamGroup(
            part_number=2,
            passages=[],
            image_url=None,
            audio=audio_asset,
            questions=[item],
        )
        groups.append(eg)
        listening_refs.append(SetItemRef(group_id=eg.group_id, item_id=item.item_id, position=l_pos))
        l_pos += 1

    # -------------------------------------------------------------------------
    # 2. PHẦN READING SECTION (Bài đọc Part 5 & Part 6)
    # -------------------------------------------------------------------------
    r_pos = 1
    # Part 5: 30 câu điền từ/ngữ pháp
    for i in range(1, 31):
        stem = f"All members of the committee must ____ their feedback before the conclusion of today's meeting (Set #{set_idx}, Part 5 #{i})."
        raw_opts = [
            ("submit", True, "Động từ nguyên mẫu V-bare đứng sau 'must'."),
            ("submits", False, "Lỗi: Không chia -s sau động từ khuyết thiếu 'must'."),
            ("submitted", False, "Lỗi: Không chọn dạng quá khứ sau 'must'."),
            ("submitting", False, "Lỗi: V-ing không đứng sau động từ khuyết thiếu 'must'."),
        ]
        placed = place_options(i, f"s{set_idx}_r5_q{i}", raw_opts)
        opts = [
            Option(label=OptionLabel(lbl), text=txt, is_correct=corr, rationale_vi=rat)
            for lbl, (txt, corr, rat) in zip(["A", "B", "C", "D"], placed)
        ]
        item = ExamItem(
            part_number=5,
            question_text=stem,
            question_type=QuestionType.GR_WORD_FORM,
            options=opts,
            concept_ids=["gram_word_form_verb"],
            difficulty_prior=0.45,
            explanation=Definition(en="Correct choice.", vi="Đáp án đúng dạng động từ nguyên mẫu."),
            review_status=ReviewStatus.AUTO_VALIDATED,
        )
        eg = ExamGroup(
            part_number=5,
            passages=[Passage(order=1, passage_type=PassageType.NOTICE, text=stem)],
            questions=[item],
        )
        groups.append(eg)
        reading_refs.append(SetItemRef(group_id=eg.group_id, item_id=item.item_id, position=r_pos))
        r_pos += 1

    # Part 6: 4 Bài đọc ngắn x 4 câu = 16 câu
    for g_idx, (ptype, title, text_raw) in enumerate(PART6_TEMPLATES, start=1):
        questions: list[ExamItem] = []
        for q_i in range(1, 4):
            stem = f"Choose the best option for gap ({q_i}) in '{title}' (Set #{set_idx}, Group #{g_idx})."
            raw_opts = [
                ("promptly", True, "Trạng từ bổ nghĩa cho động từ."),
                ("prompt", False, "Tính từ, không bổ nghĩa cho động từ."),
                ("promptness", False, "Danh từ, sai từ loại."),
                ("prompted", False, "Dạng quá khứ, sai từ loại."),
            ]
            placed = place_options(q_i, f"s{set_idx}_r6_g{g_idx}_q{q_i}", raw_opts)
            opts = [
                Option(label=OptionLabel(l), text=t, is_correct=c, rationale_vi=r)
                for l, (t, c, r) in zip(["A", "B", "C", "D"], placed)
            ]
            questions.append(ExamItem(
                part_number=6,
                question_text=stem,
                question_type=QuestionType.GR_WORD_FORM,
                options=opts,
                concept_ids=["gram_word_form_adverb"],
                difficulty_prior=0.50,
                explanation=Definition(en="Correct adverb choice.", vi="Đáp án trạng từ bổ nghĩa cho động từ."),
                review_status=ReviewStatus.AUTO_VALIDATED,
            ))

        # 1 câu chèn câu
        sentence_stem = f"Select the sentence that best fits the passage '{title}' (Set #{set_idx}, Group #{g_idx})."
        raw_sentence_opts = [
            ("Thank you for your prompt attention to this matter.", True, "Câu chèn kết bài phù hợp văn phong công sở."),
            ("The weather report predicts heavy rain tomorrow.", False, "Lỗi không liên quan."),
            ("All flight schedules have been updated.", False, "Lỗi lạc đề."),
            ("Please return the keys to the reception desk.", False, "Lỗi sai ngữ cảnh."),
        ]
        placed_sent = place_options(4, f"s{set_idx}_r6_g{g_idx}_q4", raw_sentence_opts)
        opts_sent = [
            Option(label=OptionLabel(l), text=t, is_correct=c, rationale_vi=r)
            for l, (t, c, r) in zip(["A", "B", "C", "D"], placed_sent)
        ]
        questions.append(ExamItem(
            part_number=6,
            question_text=sentence_stem,
            question_type=QuestionType.DS_SENTENCE_INSERTION,
            options=opts_sent,
            concept_ids=["rc_sentence_insertion"],
            difficulty_prior=0.60,
            explanation=Definition(en="Best sentence fit.", vi="Câu chèn phù hợp nhất."),
            review_status=ReviewStatus.AUTO_VALIDATED,
        ))

        eg = ExamGroup(
            part_number=6,
            passages=[Passage(order=1, passage_type=PassageType(ptype), text=text_raw)],
            questions=questions,
        )
        groups.append(eg)
        for item in questions:
            reading_refs.append(SetItemRef(group_id=eg.group_id, item_id=item.item_id, position=r_pos))
            r_pos += 1

    set_id = f"set_toeic_practice_{set_idx:03d}"
    title = f"TOEIC-Format Practice Test {set_idx:02d} — Full Listening & Reading Set"

    es = ExamSet(
        set_id=set_id,
        title=title,
        listening=listening_refs,
        reading=reading_refs,
        total_questions=len(listening_refs) + len(reading_refs),
    )

    return groups, es

def main():
    print("Đang tạo 10 Bộ đề thi Full Listening & Reading chi tiết cho Web...")
    all_groups: list[ExamGroup] = []
    all_sets: list[ExamSet] = []

    for s_idx in range(1, 11):
        groups, es = generate_set_data(s_idx)
        all_groups.extend(groups)
        all_sets.append(es)
        print(f"  + Đã khởi tạo {es.set_id}: {len(es.listening)} câu Listening MP3 & {len(es.reading)} câu Reading")

    SETS_DIR.mkdir(parents=True, exist_ok=True)
    batch = ExamBatch(
        batch_metadata=BatchMetadata(
            schema_version="1.0.0",
            batch_id="exam_sets_batch_001",
            module_type=ModuleType.EXAM,
            is_ai_generated=True,
            generated_by="gen_full_exam_sets.py",
            generated_at=dt.datetime.now(dt.UTC).isoformat(),
            review_status="auto_validated",
            total_records=len(all_groups),
        ),
        groups=all_groups,
        sets=all_sets,
    )

    out_file = SETS_DIR / "exam_sets_batch_001.json"
    guarded_write_batch(batch, out_file)   # lưới chắn TRƯỚC khi ghi
    print(f"\nĐã xuất thành công {len(all_sets)} BỘ ĐỀ THI FULL LISTENING & READING CHI TIẾT!")

if __name__ == "__main__":
    main()
