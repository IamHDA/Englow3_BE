"""Ba case sai cố ý của DoD Phase 2, cộng các ràng buộc part khác.

Mấu chốt: validator phải THẬT SỰ từ chối. Một validator luôn trả pass thì vô dụng,
nên mỗi test dưới đây đều khẳng định có lỗi, không phải khẳng định không có lỗi.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest
from pydantic import ValidationError

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from conftest import make_group, make_item, make_options, make_passage  # noqa: E402
from schemas import QuestionType  # noqa: E402
from validators.part_rules import PART_RULES, check_group  # noqa: E402


# --- Ba case sai cố ý theo DoD ----------------------------------------------

def test_reject_part2_with_four_options():
    """Case 1: Part 2 có 4 đáp án. Part 2 chỉ được 3 (lỗi P0-2)."""
    with pytest.raises(ValidationError, match="Part 2 bắt buộc 3 lựa chọn"):
        make_item(part_number=2, n_options=4,
                  question_type=QuestionType.LC_YES_NO)


def test_reject_two_correct_answers():
    """Case 2: hai đáp án đúng."""
    with pytest.raises(ValidationError, match="đúng 1 đáp án đúng, có 2"):
        make_item(n_options=4, n_correct=2)


def test_reject_part7_with_four_passages():
    """Case 3: Part 7 có 4 passage. Tối đa 3 (lỗi P0-3)."""
    group = make_group(
        part_number=7,
        n_passages=4,
        n_questions=2,
        questions=[
            make_item(part_number=7, question_type=QuestionType.RC_CROSS_REFERENCE,
                      evidence_span={"passage_order": 1, "char_start": 0, "char_end": 10}),
            make_item(part_number=7, question_type=QuestionType.RC_CROSS_REFERENCE,
                      question_text="Câu hỏi thứ hai?",
                      evidence_span={"passage_order": 2, "char_start": 0, "char_end": 10}),
        ],
    )
    errs = check_group(group)
    assert any("4 passage" in e for e in errs), errs


# --- Trường hợp hợp lệ: validator không được từ chối bừa ---------------------

def test_valid_part5_group_passes():
    assert check_group(make_group(part_number=5, n_passages=1, n_questions=1)) == []


def test_valid_part2_group_passes():
    group = make_group(
        part_number=2, n_passages=0, n_questions=1,
        questions=[make_item(part_number=2, n_options=3,
                             question_type=QuestionType.LC_WH_QUESTION)],
        audio={"script": "Where is the meeting room?", "accent": "US"},
    )
    assert check_group(group) == []


# --- Các ràng buộc part khác -------------------------------------------------

def test_part1_requires_image_and_audio():
    group = make_group(
        part_number=1, n_passages=0, n_questions=1,
        questions=[make_item(part_number=1, question_text=None,
                             question_type=QuestionType.LC_PHOTO_ACTION)],
    )
    errs = check_group(group)
    assert any("image_url" in e for e in errs), errs
    assert any("audio" in e for e in errs), errs


def test_part5_must_not_have_audio():
    group = make_group(part_number=5,
                       audio={"script": "không nên có ở đây", "accent": "US"})
    assert any("audio" in e for e in check_group(group))


def test_part6_needs_exactly_four_questions():
    group = make_group(part_number=6, n_passages=1, n_questions=2,
                       questions=[make_item(part_number=6, question_text=f"Q{i}?")
                                  for i in range(2)])
    assert any("2 câu hỏi" in e for e in check_group(group))


def test_question_type_must_match_part():
    """Dạng câu đọc hiểu không được nằm ở Part 5."""
    group = make_group(part_number=5,
                       questions=[make_item(part_number=5,
                                            question_type=QuestionType.RC_MAIN_IDEA)])
    assert any("không hợp với part 5" in e for e in check_group(group))


def test_part7_item_requires_evidence_span():
    group = make_group(part_number=7, n_passages=1, n_questions=2,
                       questions=[make_item(part_number=7, question_text=f"Q{i}?",
                                            question_type=QuestionType.RC_DETAIL)
                                  for i in range(2)])
    assert any("evidence_span" in e for e in check_group(group))


def test_multi_passage_requires_cross_reference_items():
    """Double passage mà không có câu đọc chéo thì tách ra làm hai đề đơn là xong."""
    group = make_group(
        part_number=7, n_passages=2, n_questions=2,
        questions=[make_item(part_number=7, question_text=f"Q{i}?",
                             question_type=QuestionType.RC_DETAIL,
                             evidence_span={"passage_order": 1, "char_start": 0, "char_end": 5})
                   for i in range(2)],
    )
    assert any("rc_cross_reference" in e for e in check_group(group))


# --- Bảng ràng buộc khớp §2.5 ------------------------------------------------

@pytest.mark.parametrize("part,n_options,q_min,q_max", [
    (1, 4, 1, 1), (2, 3, 1, 1), (3, 4, 3, 3), (4, 4, 3, 3),
    (5, 4, 1, 1), (6, 4, 4, 4), (7, 4, 2, 5),
])
def test_part_rule_table_matches_spec(part, n_options, q_min, q_max):
    r = PART_RULES[part]
    assert (r.n_options, r.questions_min, r.questions_max) == (n_options, q_min, q_max)


def test_option_labels_must_be_contiguous():
    """A,C,D — nhảy cóc nhãn B là lỗi đánh nhãn của LLM."""
    opts = make_options(4)
    opts[1].label = "D"
    with pytest.raises(ValidationError):
        make_item(options=opts)
