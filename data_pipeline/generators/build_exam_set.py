#!/usr/bin/env python3
"""Dựng manifest bộ đề từ ngân hàng câu hỏi.

Bộ đề chỉ THAM CHIẾU item_id, không sao chép nội dung — xem
docs/exam-set-structure.md. Sửa một câu ở bank thì mọi bộ đề tự đúng theo.

Không bịa cho đủ 100+100: bộ đề khai đúng số câu thật sự có. Đợt trước có 10 bộ
đều khai L=10 R=46 nhưng gọi là "full" — con số đó là ảo.

    python generators/build_exam_set.py
"""

from __future__ import annotations

import collections
import json
import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import BatchMetadata, ExamBatch, ExamSet, ModuleType, SetItemRef  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
BANK = ROOT / "output" / "exams" / "bank"
OUT = ROOT / "output" / "exams" / "sets" / "exam_sets_001.json"
GENERATED_BY = "claude-opus-5"

TARGET_R, TARGET_L = 100, 100
PART_ORDER = [5, 6, 7]          # thứ tự trong đề Reading thật


def main() -> int:
    reading: list[tuple[int, str, str]] = []   # (part, group_id, item_id)
    listening: list[tuple[int, str, str]] = []

    for p in sorted(BANK.rglob("*.json")):
        if p.name.startswith("."):
            continue
        b = ExamBatch.model_validate(json.loads(p.read_text(encoding="utf-8")))
        for g in b.groups:
            bucket = reading if g.part_number >= 5 else listening
            for q in g.questions:
                bucket.append((g.part_number, g.group_id, q.item_id))

    reading.sort(key=lambda r: (PART_ORDER.index(r[0]) if r[0] in PART_ORDER else 9))
    by_part = collections.Counter(p for p, _, _ in reading)

    print(f"Ngân hàng: {len(reading)} câu Reading, {len(listening)} câu Listening")
    for part in PART_ORDER:
        want = {5: 30, 6: 16, 7: 54}[part]
        got = by_part.get(part, 0)
        mark = "✅" if got >= want else f"thiếu {want - got}"
        print(f"  Part {part}: {got:3d}/{want}  {mark}")

    refs_r = [SetItemRef(group_id=g, item_id=i, position=n + 1)
              for n, (_, g, i) in enumerate(reading)]
    refs_l = [SetItemRef(group_id=g, item_id=i, position=n + 1)
              for n, (_, g, i) in enumerate(listening)]

    complete = len(refs_r) >= TARGET_R and len(refs_l) >= TARGET_L
    title = ("Đề luyện theo định dạng TOEIC số 1" if complete else
             f"Đề luyện theo định dạng TOEIC — bản một phần "
             f"({len(refs_r)} câu Reading, {len(refs_l)} câu Listening)")

    exam_set = ExamSet(
        set_id="set_001", title=title,
        listening=refs_l, reading=refs_r,
        total_questions=len(refs_r) + len(refs_l))

    print(f"\n  set_001: L={len(refs_l)} R={len(refs_r)} tổng={exam_set.total_questions}")
    print(f"  {'ĐỦ BỘ' if complete else 'BẢN MỘT PHẦN — title ghi rõ, không khai là full'}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    batch = ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_sets_001", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=0),
        groups=[], sets=[exam_set])
    OUT.write_text(json.dumps(batch.model_dump(mode="json"),
                              ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nGhi {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
