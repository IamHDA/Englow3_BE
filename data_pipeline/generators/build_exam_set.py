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
    passage_counts: dict[str, int] = {}
    question_types: dict[str, str] = {}

    for p in sorted(BANK.rglob("*.json")):
        if p.name.startswith("."):
            continue
        b = ExamBatch.model_validate(json.loads(p.read_text(encoding="utf-8")))
        for g in b.groups:
            passage_counts[g.group_id] = len(g.passages)
            bucket = reading if g.part_number >= 5 else listening
            for q in g.questions:
                question_types[q.item_id] = q.question_type.value
                bucket.append((g.part_number, g.group_id, q.item_id))

    reading.sort(key=lambda r: (PART_ORDER.index(r[0]) if r[0] in PART_ORDER else 9))
    listening.sort(key=lambda r: r[0])

    def take(pool, quota: dict[int, int]) -> list[tuple[int, str, str]]:
        """Lấy đúng chỉ tiêu mỗi part. Bank là KHO, bộ đề chỉ là một lát cắt —
        gom hết vào set_001 thì thêm câu nào là đề phình ra câu đó, và đề
        101 câu thì không còn là đề nữa."""
        left = dict(quota)
        out = []
        for row in pool:
            if left.get(row[0], 0) > 0:
                out.append(row)
                left[row[0]] -= 1
        return out

    R_QUOTA = {5: 30, 6: 16, 7: 54}
    L_QUOTA = {1: 6, 2: 25, 3: 39, 4: 30}

    for label, pool, quota in (("Reading", reading, R_QUOTA),
                               ("Listening", listening, L_QUOTA)):
        by_part = collections.Counter(p for p, _, _ in pool)
        print(f"Ngân hàng {label}: {len(pool)} câu")
        for part, want in quota.items():
            got = by_part.get(part, 0)
            mark = "✅" if got >= want else f"thiếu {want - got}"
            spare = f"  (dôi {got - want})" if got > want else ""
            print(f"  Part {part}: {got:3d}/{want}  {mark}{spare}")

    # ETS linear blueprint: Part 7 = 29 questions from 10 single texts and
    # 25 questions from exactly 5 multiple-passage sets (5 questions each).
    base_r = take([row for row in reading if row[0] in (5, 6)], {5: 30, 6: 16})
    grouped_p7: dict[str, list[tuple[int, str, str]]] = {}
    for row in (r for r in reading if r[0] == 7):
        grouped_p7.setdefault(row[1], []).append(row)
    singles = [rows for gid, rows in grouped_p7.items() if passage_counts[gid] == 1]
    multiples = [rows for gid, rows in grouped_p7.items() if passage_counts[gid] >= 2]

    selected_p7: list[tuple[int, str, str]] = []
    # Put the sentence-insertion single first; that same passage also supplies
    # vocabulary-in-context. Keep nine more texts, then trim only to a minimum
    # of two questions per text until the official total of 29 is reached.
    singles.sort(key=lambda rows: (
        not any(question_types[row[2]] == "rc_sentence_insertion" for row in rows),
    ))
    chosen_singles = singles[:10]
    take_counts = [len(rows) for rows in chosen_singles]
    excess = sum(take_counts) - 29
    for index in range(len(take_counts) - 1, -1, -1):
        removable = max(0, take_counts[index] - 2)
        removed = min(removable, excess)
        take_counts[index] -= removed
        excess -= removed
    for rows, count in zip(chosen_singles, take_counts):
        selected_p7.extend(rows[:count])
    for rows in multiples[:5]:
        selected_p7.extend(rows[:5])
    if excess or len(chosen_singles) != 10 or len(multiples[:5]) != 5 \
            or len(selected_p7) != 54:
        raise RuntimeError(
            "Part 7 bank không dựng được blueprint ETS: cần 10 single/29 câu "
            "và 5 multiple/25 câu")

    selected_r = base_r + selected_p7
    refs_r = [SetItemRef(group_id=g, item_id=i, position=n + 1)
              for n, (_, g, i) in enumerate(selected_r)]
    refs_l = [SetItemRef(group_id=g, item_id=i, position=n + 1)
              for n, (_, g, i) in enumerate(take(listening, L_QUOTA))]

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
