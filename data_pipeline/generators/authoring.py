"""Tiện ích dùng chung khi viết tay nội dung đề.

Không gọi API nào. Nội dung do người/agent viết trực tiếp rồi validate bằng
schema Phase 2.
"""

from __future__ import annotations

import collections
import hashlib
import json
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import ExamGroup  # noqa: E402

__all__ = ["LABELS", "place_options", "report_bias", "write_batch", "find_span"]

LABELS = ["A", "B", "C", "D"]

# Ngưỡng của docs/exam-quality-bar.md §4
POSITION_MIN, POSITION_MAX = 0.20, 0.30
LONGEST_MAX = 0.35


def place_options(idx: int, seed_text: str, options: list[tuple[str, bool, str]]
                  ) -> list[tuple[str, bool, str]]:
    """Đặt đáp án đúng vào vị trí xoay vòng A→B→C→D, distractor xáo tất định.

    Nội dung viết tay luôn để đáp án đúng đầu tiên cho dễ đọc, nên giữ nguyên
    thứ tự thì 100% đáp án đúng rơi vào A — học viên chỉ cần chọn A. Đó là
    thiên lệch B-1 của exam-quality-bar.md.

    Xáo ngẫu nhiên có seed vẫn lệch: trên 30 câu đã từng cho ra D=40%. Xoay vòng
    theo chỉ số câu mới bảo đảm đúng 25% mỗi nhãn.

    Distractor xáo theo seed lấy từ nội dung (không dùng random() trần) để chạy
    lại pipeline ra cùng một đề.
    """
    correct = next(o for o in options if o[1])
    distractors = [o for o in options if not o[1]]
    seed = int(hashlib.sha256(seed_text.encode("utf-8")).hexdigest()[:8], 16)
    random.Random(seed).shuffle(distractors)
    out = distractors[:]
    out.insert(idx % len(options), correct)
    return out


def find_span(passage_text: str, quote: str, passage_order: int = 1) -> dict:
    """Tính offset bằng string-match trong CODE, không để LLM tự khai (§Phase 7).

    Không tìm thấy nghĩa là câu hỏi không định vị được bằng chứng → item không
    hợp lệ, phải sửa nội dung chứ không phải bịa offset.
    """
    start = passage_text.find(quote)
    if start < 0:
        raise ValueError(
            f"Không tìm thấy trong passage {passage_order}: {quote[:60]!r}")
    return {"passage_order": passage_order,
            "char_start": start, "char_end": start + len(quote)}


def report_bias(groups: list[ExamGroup]) -> list[str]:
    """Ba kiểm tra thiên lệch của exam-quality-bar.md §4, chạy trên CẢ bộ.

    Trả về danh sách cảnh báo. In ra chứ không raise: đây là thống kê phân bố,
    một bộ nhỏ lệch nhẹ là bình thường — nhưng phải nhìn thấy.
    """
    items = [q for g in groups for q in g.questions]
    n = len(items)
    warns: list[str] = []

    pos = collections.Counter(
        next(o.label for o in q.options if o.is_correct) for q in items)
    print(f"  B-1 vị trí đáp án đúng: " +
          "  ".join(f"{k}={pos[k]} ({pos[k]/n*100:.0f}%)" for k in LABELS))
    for k in LABELS:
        share = pos[k] / n
        if share and not (POSITION_MIN <= share <= POSITION_MAX):
            warns.append(f"B-1: nhãn {k} chiếm {share*100:.0f}%, ngoài 20–30%")

    longest = sum(1 for q in items
                  if max(q.options, key=lambda o: len(o.text)).is_correct)
    print(f"  B-2 đáp án đúng là lựa chọn dài nhất: {longest}/{n} ({longest/n*100:.0f}%)")
    if longest / n > LONGEST_MAX:
        warns.append(f"B-2: {longest/n*100:.0f}% vượt ngưỡng {LONGEST_MAX*100:.0f}%")

    return warns


def write_batch(batch, out: Path, root: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        json.dumps(batch.model_dump(mode="json"), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    print(f"Ghi {out.relative_to(root)}")
    print(f"  {out.stat().st_size // 1024} KB")
