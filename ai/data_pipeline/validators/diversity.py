"""Lưới chắn đa dạng nội dung — bắt dữ liệu điền khuôn.

Vì sao cần: đợt dữ liệu ngày 2026-08-06 có 3 000 flashcard dùng đúng MỘT khuôn
câu, nhưng vượt 100% kiểm tra hình thức (schema, part rules, thiên lệch). Đếm
chuỗi thô không phát hiện được, vì thay một từ vào khuôn cố định đã tạo ra chuỗi
"duy nhất": tỉ lệ đo được là 83.9%, trông rất đa dạng.

Khuôn mẫu chỉ lộ ra khi che token biến thiên rồi mới đếm. Với cùng dữ liệu đó,
đếm trên bộ xương cho 1/3000 = 0.03%.
"""

from __future__ import annotations

import re
from collections.abc import Callable, Sequence

__all__ = ["skeleton", "check_skeleton_diversity", "DIVERSITY_THRESHOLD"]

DIVERSITY_THRESHOLD = 0.60
MASK = "§"


def skeleton(text: str, *variables: str) -> str:
    """Che token biến thiên để lộ ra cấu trúc câu bên dưới.

    Dùng ranh giới từ và bỏ qua hoa/thường. Hai điều này không phải chi tiết
    làm đẹp — thiếu chúng thì hàm sai theo hai hướng ngược nhau:

      `str.replace` trần với lemma='at'  → 'Please meet § the st§ion.'
          phá luôn 'station', hai câu khác nhau ra cùng bộ xương → reject oan

      phân biệt hoa/thường với lemma='vacation'
          → 'Vacation days accrue monthly.' không bị che gì
          bộ xương vẫn duy nhất → BỎ LỌT, đúng lỗ hổng cần bịt
    """
    if not text:
        return ""
    for v in variables:
        if not v:
            continue
        v = str(v).strip()
        if not v:
            continue
        text = re.sub(rf"\b{re.escape(v)}\b", MASK, text, flags=re.IGNORECASE)
    return text


def check_skeleton_diversity(
    records: Sequence,
    get_text: Callable[[object], str | None],
    get_vars: Callable[[object], Sequence[str]],
    threshold: float = DIVERSITY_THRESHOLD,
) -> tuple[bool, float, int]:
    """Trả về (đạt, tỉ lệ, số bộ xương duy nhất).

    Tỉ lệ = số bộ xương duy nhất / tổng bản ghi. 1.0 nghĩa là mọi bản ghi có
    cấu trúc câu riêng; 0.03 nghĩa là cả tập chỉ dùng vài khuôn.
    """
    sks = [skeleton(get_text(r) or "", *get_vars(r)) for r in records]
    sks = [s for s in sks if s]
    if not sks:
        return False, 0.0, 0
    uniq = len(set(sks))
    ratio = uniq / len(sks)
    return ratio >= threshold, ratio, uniq
