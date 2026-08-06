"""Factory dựng object hợp lệ để test còn tự do làm hỏng từng chỗ một."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import (  # noqa: E402
    Definition, ExamGroup, ExamItem, Option, Passage, QuestionType,
)
from schemas.enums import PassageType  # noqa: E402


def make_option(label: str, correct: bool = False, text: str | None = None) -> Option:
    return Option(
        label=label,
        text=text or f"lựa chọn {label}",
        is_correct=correct,
        rationale_vi=f"Giải thích vì sao {label} " + ("đúng" if correct else "sai"),
    )


def make_options(n: int = 4, n_correct: int = 1) -> list[Option]:
    labels = ["A", "B", "C", "D"][:n]
    return [make_option(l, correct=(i < n_correct)) for i, l in enumerate(labels)]


def make_item(
    part_number: int = 5,
    n_options: int = 4,
    n_correct: int = 1,
    question_type: QuestionType = QuestionType.GR_TENSE,
    **kw,
) -> ExamItem:
    payload = {
        "part_number": part_number,
        "question_text": "The report ____ by Friday.",
        "question_type": question_type,
        "options": make_options(n_options, n_correct),
        "concept_ids": ["gram_passive_present"],
        "difficulty_prior": 0.5,
        "explanation": Definition(en="Passive voice is required here.", vi="Cần thể bị động."),
    }
    payload.update(kw)
    return ExamItem(**payload)


def make_passage(order: int = 1, text: str | None = None) -> Passage:
    return Passage(
        order=order,
        passage_type=PassageType.EMAIL,
        text=text or f"Đoạn văn số {order}. " + "Nội dung email công việc trung tính. " * 3,
    )


def make_group(part_number: int = 5, n_passages: int = 1, n_questions: int = 1,
               **kw) -> ExamGroup:
    payload: dict = {"part_number": part_number}
    payload.update(kw)
    # Chỉ dựng mặc định khi test không tự truyền vào — dựng sẵn rồi ghi đè sẽ
    # nổ ngay lúc dựng với các part có ràng buộc khác (vd Part 2 chỉ 3 lựa chọn).
    # Không dùng setdefault: nó đánh giá tham số mặc định kể cả khi khoá đã có.
    if "passages" not in payload:
        payload["passages"] = [make_passage(i + 1) for i in range(n_passages)]
    if "questions" not in payload:
        payload["questions"] = [
            make_item(part_number=part_number, question_text=f"Câu hỏi số {i + 1}?")
            for i in range(n_questions)]
    return ExamGroup(**payload)


@pytest.fixture
def item_factory():
    return make_item


@pytest.fixture
def group_factory():
    return make_group
