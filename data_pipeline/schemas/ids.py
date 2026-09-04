"""ID tất định — §2.1 của work order.

Cùng input → cùng ID, ở mọi máy, mọi lần chạy. Nhờ vậy pipeline idempotent:
chạy lại trên cùng dữ liệu thì `INSERT ... ON CONFLICT (id) DO UPDATE` cập nhật
đúng row cũ thay vì nhân bản.

Không dùng SERIAL/IDENTITY của Postgres — ID phải biết trước khi chạm DB.
"""

from __future__ import annotations

import hashlib
import re

__all__ = [
    "stable_id",
    "flashcard_id",
    "exam_item_id",
    "exam_group_id",
    "grammar_point_id",
    "task_id",
    "rubric_id",
    "passage_hash",
]

_WS = re.compile(r"\s+")


def stable_id(prefix: str, *parts: object) -> str:
    """sha256 của các phần nối bằng '|', lấy 16 hex đầu.

    Chuẩn hoá trước khi băm: bỏ khoảng trắng thừa, hạ chữ thường. Nếu không,
    "the  report" và "The report" ra hai ID khác nhau cho cùng một nội dung.

    >>> stable_id("vocab", "Address", "verb", 2)
    'vocab_...'
    """
    if not prefix:
        raise ValueError("prefix không được rỗng")
    raw = "|".join(_WS.sub(" ", str(p)).strip().lower() for p in parts)
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
    return f"{prefix}_{digest}"


def passage_hash(*passage_texts: str) -> str:
    """Băm nội dung passage để định danh group. Part 7 có thể có 1–3 passage."""
    joined = "\n\n".join(_WS.sub(" ", t).strip().lower() for t in passage_texts)
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()[:16]


# --- Hàm tiện dụng theo từng loại (§2.1) ------------------------------------
# Gói lại thay vì để chỗ gọi tự truyền prefix, vì gõ nhầm prefix sẽ tạo ra ID
# hợp lệ nhưng trỏ sai bảng — lỗi im lặng, rất khó tìm.

def flashcard_id(lemma: str, pos: str, sense_index: int) -> str:
    """Khoá là (lemma, pos, sense_index) — lỗi P1-8: phân biệt nghĩa."""
    return stable_id("vocab", lemma, pos, sense_index)


def exam_item_id(part_number: int, question_text: str | None, correct_option_text: str) -> str:
    """Part 1 có thể không có question_text (câu hỏi nằm trong audio)."""
    return stable_id("itm", part_number, question_text or "", correct_option_text)


def exam_group_id(part_number: int, *passage_texts: str) -> str:
    return stable_id("grp", part_number, passage_hash(*passage_texts))


def grammar_point_id(title_en: str, cefr_level: str) -> str:
    return stable_id("gram", title_en, cefr_level)


def task_id(task_type: str, prompt: str) -> str:
    """Dùng chung cho SpeakingTask và WritingTask."""
    return stable_id("task", task_type, prompt)


def rubric_id(name: str, version: str = "1.0.0") -> str:
    return stable_id("rub", name, version)
