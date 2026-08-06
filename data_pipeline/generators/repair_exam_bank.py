#!/usr/bin/env python3
"""Sửa các lỗi máy sửa được trong ngân hàng đề đã sinh.

Chạy TRÊN output, không sửa generator — vì có 19 generator từ nhiều nguồn khác
nhau (viết tay, Groq, ...) và sửa từng cái là không bền. Sửa ở đầu ra thì
generator nào cũng phải đi qua.

Sửa được bằng máy:
  1. Câu hỏi trùng nguyên văn  → giữ bản đầu, loại phần còn lại
  2. Part 5 thiếu passage      → dùng chính câu hỏi làm passage (§2.5)
  3. Vị trí đáp án dồn cục     → xoay vòng lại A→B→C→D
  4. Accent lệch chỉ tiêu      → phân bổ lại 50/17/17/17

KHÔNG sửa được bằng máy (cần viết thêm nội dung):
  - Bộ đề thiếu câu (L=10 R=46, chuẩn 100+100)
  - Concept lá chưa có item

    python generators/repair_exam_bank.py            # sửa và ghi đè
    python generators/repair_exam_bank.py --dry-run  # chỉ báo cáo
"""

from __future__ import annotations

import argparse
import collections
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import ExamBatch, ExamGroup, Passage  # noqa: E402
from schemas.enums import Accent, PassageType  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
BANK = ROOT / "output" / "exams"
LABELS = ["A", "B", "C", "D"]
ACCENT_CYCLE = [Accent.US, Accent.US, Accent.US, Accent.UK, Accent.AU, Accent.CA]


def load_all() -> list[tuple[Path, ExamBatch]]:
    out = []
    for p in sorted(BANK.rglob("*.json")):
        if p.name.startswith("."):
            continue
        try:
            out.append((p, ExamBatch.model_validate(json.loads(p.read_text(encoding="utf-8")))))
        except Exception as e:
            print(f"  ✗ bỏ qua {p.name}: {str(e)[:70]}")
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    batches = load_all()
    print(f"Đọc {len(batches)} batch\n")

    fixed = collections.Counter()
    seen_text: set[str] = set()
    q_index = 0          # chỉ số toàn cục để xoay vòng vị trí đáp án
    audio_index = 0

    for path, batch in batches:
        keep_groups: list[ExamGroup] = []
        for g in batch.groups:
            data = g.model_dump(mode="python")

            # --- 1. Loại câu hỏi trùng nguyên văn ---
            kept_q = []
            for q in data["questions"]:
                t = (q.get("question_text") or "").strip()
                if t and t in seen_text:
                    fixed["câu trùng nguyên văn bị loại"] += 1
                    continue
                if t:
                    seen_text.add(t)
                kept_q.append(q)
            if not kept_q:
                fixed["group rỗng sau khi loại trùng"] += 1
                continue
            data["questions"] = kept_q

            # --- 3. Xoay vòng vị trí đáp án ---
            for q in data["questions"]:
                opts = q["options"]
                correct = next(o for o in opts if o["is_correct"])
                others = [o for o in opts if not o["is_correct"]]
                slot = q_index % len(opts)
                q_index += 1
                new = others[:]
                new.insert(slot, correct)
                for i, o in enumerate(new):
                    o["label"] = LABELS[i]
                if [o["label"] for o in opts] != [o["label"] for o in new]:
                    fixed["đáp án được xoay vị trí"] += 1
                q["options"] = new
                q["item_id"] = ""          # để schema tính lại
                q["embedding_text"] = ""

            # --- 2. Part 5 thiếu passage ---
            if data["part_number"] == 5 and not data["passages"]:
                stem = data["questions"][0].get("question_text") or ""
                if len(stem) >= 20:
                    data["passages"] = [Passage(
                        order=1, passage_type=PassageType.NOTICE, text=stem
                    ).model_dump(mode="python")]
                    fixed["Part 5 được bổ sung passage"] += 1

            # --- 4. Phân bổ lại accent ---
            if data.get("audio"):
                want = ACCENT_CYCLE[audio_index % len(ACCENT_CYCLE)]
                audio_index += 1
                if data["audio"]["accent"] != want.value:
                    data["audio"]["accent"] = want.value
                    fixed["accent được phân bổ lại"] += 1

            data["group_id"] = ""
            try:
                keep_groups.append(ExamGroup.model_validate(data))
            except Exception as e:
                fixed[f"group bị loại (không hợp lệ): {str(e)[:40]}"] += 1

        if keep_groups != batch.groups:
            meta = batch.batch_metadata.model_dump(mode="python")
            meta["total_records"] = len(keep_groups)
            new_batch = ExamBatch(batch_metadata=meta, groups=keep_groups,
                                  sets=batch.sets)
            if not args.dry_run:
                path.write_text(
                    json.dumps(new_batch.model_dump(mode="json"),
                               ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")

    print("Đã sửa:" if not args.dry_run else "Sẽ sửa (dry-run):")
    for k, v in fixed.most_common():
        print(f"  {v:5d}  {k}")
    if not fixed:
        print("  (không có gì cần sửa)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
