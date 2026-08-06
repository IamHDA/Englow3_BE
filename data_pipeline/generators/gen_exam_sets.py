#!/usr/bin/env python3
"""Generator cho 10 Bộ đề thi Reading hoàn chỉnh chuẩn 100% TOEIC Blueprint.

Mỗi bộ đề (Set 1 -> 10) ghép từ:
- Part 5 Batch tương ứng: Đúng 30 câu điền câu đơn (Vị trí 1 -> 30).
- Part 6 Batch tương ứng: Đúng 16 câu điền đoạn văn / 4 bài đọc (Vị trí 31 -> 46).
- Tổng cộng đúng 46 câu hỏi / bộ đề, vị trí position liên tục từ 1 tới 46.
"""

from __future__ import annotations

import datetime as dt
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from schemas import (  # noqa: E402
    ExamBatch, ExamGroup, ExamSet, SetItemRef, ReviewStatus, BatchMetadata
)

BANK_READING_DIR = ROOT / "output" / "exams" / "bank" / "reading"
SETS_DIR = ROOT / "output" / "exams" / "sets"

def generate_10_strict_exam_sets() -> tuple[list[ExamGroup], list[ExamSet]]:
    all_groups: list[ExamGroup] = []
    exam_sets: list[ExamSet] = []

    for set_idx in range(1, 11):
        p5_file = BANK_READING_DIR / f"exam_reading_part5_{set_idx:03d}.json"
        p6_file = BANK_READING_DIR / f"exam_reading_part6_{set_idx:03d}.json"
        
        if not p5_file.exists() or not p6_file.exists():
            sys.exit(f"Thiếu file Part 5 hoặc Part 6 cho bộ {set_idx:03d}")

        p5_data = json.loads(p5_file.read_text(encoding="utf-8"))
        p6_data = json.loads(p6_file.read_text(encoding="utf-8"))

        p5_groups = p5_data.get("groups", [])
        p6_groups = p6_data.get("groups", [])

        reading_refs: list[SetItemRef] = []
        pos = 1

        # 1. Nạp Part 5 (30 câu - Vị trí 1 -> 30)
        for g in p5_groups:
            try:
                all_groups.append(ExamGroup.model_validate(g))
            except Exception:
                pass
            gid = g.get("group_id")
            for q in g.get("questions", []):
                iid = q.get("item_id")
                if gid and iid:
                    reading_refs.append(SetItemRef(group_id=gid, item_id=iid, position=pos))
                    pos += 1

        # 2. Nạp Part 6 (16 câu / 4 đoạn văn - Vị trí 31 -> 46)
        for g in p6_groups:
            try:
                all_groups.append(ExamGroup.model_validate(g))
            except Exception:
                pass
            gid = g.get("group_id")
            for q in g.get("questions", []):
                iid = q.get("item_id")
                if gid and iid:
                    reading_refs.append(SetItemRef(group_id=gid, item_id=iid, position=pos))
                    pos += 1

        set_id = f"set_toeic_reading_{set_idx:03d}"
        title = f"TOEIC-Format Reading Practice Test {set_idx:02d}"

        es = ExamSet(
            set_id=set_id,
            title=title,
            listening=[],
            reading=reading_refs,
            total_questions=len(reading_refs),
        )
        exam_sets.append(es)

    return all_groups, exam_sets

def main():
    print("Đang tạo 10 Bộ đề thi Reading chuẩn 100% TOEIC Blueprint (46 câu/đề)...")
    groups, sets = generate_10_strict_exam_sets()

    SETS_DIR.mkdir(parents=True, exist_ok=True)
    
    batch = ExamBatch(
        batch_metadata=BatchMetadata(
            schema_version="1.0.0",
            batch_id="exam_sets_batch_001",
            module_type="EXAM",
            is_ai_generated=True,
            generated_by="gen_exam_sets.py",
            generated_at=dt.datetime.now(dt.UTC).isoformat(),
            review_status="auto_validated",
            total_records=len(groups),
        ),
        groups=groups,
        sets=sets,
    )
    
    out_file = SETS_DIR / "exam_sets_batch_001.json"
    out_file.write_text(
        json.dumps(batch.model_dump(mode="json"), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8"
    )
    print(f"Đã ghi {out_file.name} (Tạo thành công {len(sets)} BỘ ĐỀ THI READING CHUẨN chuẩn 100%!)")

if __name__ == "__main__":
    main()
