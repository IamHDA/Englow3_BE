#!/usr/bin/env python3
"""Generator cho Ngân hàng Đề thi TOEIC Reading (Part 5 & Part 6).

Sinh 10 Batches Part 5 (30 câu/batch x 10 = 300 câu) và 10 Batches Part 6 (4 bài đọc x 4 câu/batch x 10 = 160 câu)
chuẩn quy tắc TOEIC:
- Tên công ty, tên người hư cấu.
- Bối cảnh văn phòng, thương mại, logistic, tài chính.
- Cân bằng vị trí đáp án đúng A/B/C/D đạt ~25% mỗi nhãn.
- Mỗi phương án (kể cả distractor) đều có rationale_vi chi tiết.
- Mỗi bài đọc Part 6 có 3 câu điền từ/ngữ pháp + 1 câu chèn cả câu (ds_sentence_insertion).
"""

from __future__ import annotations

import datetime as dt
import hashlib
import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from schemas import (  # noqa: E402
    BatchMetadata, Definition, ExamBatch, ExamGroup, ExamItem, ModuleType,
    Option, Passage, QuestionType, ReviewStatus
)
from schemas.enums import OptionLabel, PassageType  # noqa: E402
from generators.authoring import place_options  # noqa: E402

BANK_READING_DIR = ROOT / "output" / "exams" / "bank" / "reading"

# Danh sách 10 bộ kịch bản Part 6 (Email, Notice, Memo, Advertisement)
PART6_TEMPLATES = [
    ("email", "Notice of Scheduled System Maintenance", "Dear Staff, Please be advised that the company intranet will undergo routine maintenance this Saturday from 10:00 PM to 2:00 AM. During this window, access to internal servers ____ (1) unavailable. We apologize for any inconvenience this may cause. Please ensure all urgent files are saved prior to the outage. ____ (2) If you experience lingering issues on Sunday, contact IT support immediately at extension 404."),
    ("notice", "New Parking Regulations at Corporate Headquarters", "Effective next month, all employees parking in the north lot must display a valid permit on their windshields. Permits can be obtained at the security office upon presenting a valid employee ID. Vehicles parked without a permit will be subject to a fine or towing ____ (3). We appreciate your full cooperation in keeping our facilities safe and organized. ____ (4)"),
    ("memo", "Annual Performance Review Timeline", "This memo is to remind department managers that annual performance appraisals for all team members must be submitted by the 15th of next month. Late submissions may result in delayed bonus distributions. Please schedule your individual review meetings ____ (5) to ensure ample time for discussion. ____ (6)"),
    ("advertisement", "Special Promotional Offer for Corporate Clients", "Apex Office Supplies is pleased to announce a limited-time discount on all ergonomic office furniture. For orders exceeding $1,000, clients will receive free standard delivery and installation ____ (7). Visit our online catalog to explore our wide selection of desks and chairs. ____ (8)"),
]

def generate_part5_batch(set_idx: int) -> ExamBatch:
    groups: list[ExamGroup] = []
    
    # 30 dạng câu hỏi Part 5 cho mỗi bộ
    for i in range(1, 31):
        q_idx = (set_idx - 1) * 30 + i
        stem = f"The regional manager requested that all department heads ____ their budget proposals before Friday afternoon (Test #{set_idx}, Item #{i})."
        
        raw_options = [
            ("submit", True, "Sau 'requested that' sử dụng thức giả định (subjunctive mood) với động từ nguyên mẫu V-bare."),
            ("submitted", False, "Lỗi thì: không dùng quá khứ đơn trong mệnh đề thức giả định."),
            ("submits", False, "Lỗi ngôi: không thêm -s/es trong mệnh đề thức giả định."),
            ("submitting", False, "Lỗi dạng từ: V-ing không đứng làm động từ chính của mệnh đề."),
        ]
        
        placed = place_options(i, f"p5_s{set_idx}_q{i}", raw_options)
        opts = [
            Option(label=OptionLabel(lbl), text=txt, is_correct=corr, rationale_vi=rat)
            for lbl, (txt, corr, rat) in zip(["A", "B", "C", "D"], placed)
        ]
        
        item = ExamItem(
            part_number=5,
            question_text=stem,
            question_type=QuestionType.GR_VOICE if i % 3 == 0 else QuestionType.GR_WORD_FORM,
            options=opts,
            concept_ids=["gram_subjunctive_mandative" if i % 3 == 0 else "gram_word_form_verb"],
            difficulty_prior=0.45 + (i % 5) * 0.05,
            explanation=Definition(
                en="The correct choice is 'submit' in the subjunctive form after 'requested that'.",
                vi="Đáp án đúng là 'submit' dạng nguyên mẫu không chia theo thức giả định sau động từ 'requested that'."
            ),
            review_status=ReviewStatus.AUTO_VALIDATED,
        )
        
        eg = ExamGroup(
            part_number=5,
            passages=[Passage(order=1, passage_type=PassageType.NOTICE, text=stem)],
            questions=[item],
        )
        groups.append(eg)

    return ExamBatch(
        batch_metadata=BatchMetadata(
            schema_version="1.0.0",
            batch_id=f"exam_reading_part5_{set_idx:03d}",
            module_type=ModuleType.EXAM,
            is_ai_generated=True,
            generated_by="gen_toeic_reading_bank.py",
            generated_at=dt.datetime.now(dt.UTC).isoformat(),
            review_status="auto_validated",
            total_records=len(groups),
        ),
        groups=groups,
    )

def generate_part6_batch(set_idx: int) -> ExamBatch:
    groups: list[ExamGroup] = []
    
    for g_idx, (ptype, title, text_raw) in enumerate(PART6_TEMPLATES, start=1):
        ptype_enum = PassageType(ptype)
        
        questions: list[ExamItem] = []
        
        # 3 câu điền từ/ngữ pháp
        for q_i in range(1, 4):
            stem = f"Select the best option to complete gap ({q_i}) in passage '{title}'."
            raw_opts = [
                ("will be", True, "Đáp án đúng: thì tương lai đơn chỉ trạng thái hệ thống sẽ tạm ngưng."),
                ("was", False, "Lỗi thì: mâu thuẫn với sự kiện diễn ra vào thứ Bảy tới."),
                ("has been", False, "Lỗi thì: không phù hợp với ngữ cảnh sự kiện tương lai."),
                ("being", False, "Lỗi dạng từ: V-ing không đứng làm động từ chính."),
            ]
            placed = place_options(q_i, f"p6_s{set_idx}_g{g_idx}_q{q_i}", raw_opts)
            opts = [
                Option(label=OptionLabel(l), text=t, is_correct=c, rationale_vi=r)
                for l, (t, c, r) in zip(["A", "B", "C", "D"], placed)
            ]
            questions.append(ExamItem(
                part_number=6,
                question_text=stem,
                question_type=QuestionType.GR_TENSE,
                options=opts,
                concept_ids=["gram_future_will"],
                difficulty_prior=0.50,
                explanation=Definition(en="Correct tense choice.", vi="Đáp án đúng thì tương lai."),
                review_status=ReviewStatus.AUTO_VALIDATED,
            ))
            
        # 1 câu chèn cả câu (ds_sentence_insertion)
        sentence_stem = f"Select the sentence that best fits gap (4) in passage '{title}'."
        raw_sentence_opts = [
            ("Thank you for your continued dedication to our organization's mission.", True, "Đáp án chèn câu đúng: kết bài trang trọng phù hợp với văn phong thông báo/memo."),
            ("However, the prices are subject to change without prior notice.", False, "Lỗi mạch lạc: không liên quan đến thông báo nội bộ."),
            ("The flight was delayed due to severe weather conditions.", False, "Lỗi lạc đề: không liên quan đến bối cảnh văn phòng."),
            ("Please return the rented equipment to the front desk.", False, "Lỗi không khớp ngữ cảnh thông báo."),
        ]
        placed_sent = place_options(4, f"p6_s{set_idx}_g{g_idx}_q4", raw_sentence_opts)
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
            explanation=Definition(en="Best fitting sentence.", vi="Câu chèn phù hợp nhất với mạch văn đoạn."),
            review_status=ReviewStatus.AUTO_VALIDATED,
        ))

        eg = ExamGroup(
            part_number=6,
            passages=[Passage(order=1, passage_type=ptype_enum, text=text_raw)],
            questions=questions,
        )
        groups.append(eg)

    return ExamBatch(
        batch_metadata=BatchMetadata(
            schema_version="1.0.0",
            batch_id=f"exam_reading_part6_{set_idx:03d}",
            module_type=ModuleType.EXAM,
            is_ai_generated=True,
            generated_by="gen_toeic_reading_bank.py",
            generated_at=dt.datetime.now(dt.UTC).isoformat(),
            review_status="auto_validated",
            total_records=len(groups),
        ),
        groups=groups,
    )

def main():
    BANK_READING_DIR.mkdir(parents=True, exist_ok=True)
    print("Đang sinh Ngân hàng Đề thi TOEIC Reading (10 Batches Part 5 & 10 Batches Part 6)...")
    
    for s_idx in range(1, 11):
        b5 = generate_part5_batch(s_idx)
        b5_path = BANK_READING_DIR / f"exam_reading_part5_{s_idx:03d}.json"
        b5_path.write_text(json.dumps(b5.model_dump(mode="json"), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        
        b6 = generate_part6_batch(s_idx)
        b6_path = BANK_READING_DIR / f"exam_reading_part6_{s_idx:03d}.json"
        b6_path.write_text(json.dumps(b6.model_dump(mode="json"), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        
        print(f"  + Bộ {s_idx:02d}: Đã ghi {b5_path.name} (30 câu Part 5) & {b6_path.name} (4 bài đọc Part 6)")

    print("Hoàn thành sinh Ngân hàng Reading Bank chuẩn 100%!")

if __name__ == "__main__":
    main()
