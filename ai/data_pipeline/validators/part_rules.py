"""Ràng buộc theo part — §2.5 của work order.

| Part | Kỹ năng | Đáp án | passages | image | audio | questions/group |
|---|---|---|---|---|---|---|
| 1 | L | 4 | 0    | bắt buộc | bắt buộc | 1   |
| 2 | L | 3 | 0    | 0        | bắt buộc | 1   |
| 3 | L | 4 | 0    | optional | bắt buộc | 3   |
| 4 | L | 4 | 0    | optional | bắt buộc | 3   |
| 5 | R | 4 | 1    | 0        | 0        | 1   |
| 6 | R | 4 | 1    | 0        | 0        | 4   |
| 7 | R | 4 | 1–3  | optional | 0        | 2–5 |

Sai bất kỳ ô nào → reject. Reject-only: KHÔNG sửa dữ liệu.
"""

from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas.enums import (  # noqa: E402
    DISCOURSE_TYPES, GRAMMAR_TYPES, LISTENING_TYPES, READING_TYPES, VOCAB_TYPES,
    QuestionType,
)
from schemas.exam import ExamGroup  # noqa: E402

__all__ = ["PartRule", "PART_RULES", "check_group", "check_groups"]


@dataclass(frozen=True)
class PartRule:
    part_number: int
    skill: str                    # "L" | "R"
    n_options: int
    passages_min: int
    passages_max: int
    image: str                    # "required" | "optional" | "forbidden"
    audio: str
    questions_min: int
    questions_max: int
    allowed_types: frozenset[QuestionType]


# Part 5/6 kiểm tra ngữ pháp, từ vựng và liên kết câu — không phải đọc hiểu đoạn dài.
_P5_TYPES = GRAMMAR_TYPES | VOCAB_TYPES
_P6_TYPES = GRAMMAR_TYPES | VOCAB_TYPES | DISCOURSE_TYPES

PART_RULES: dict[int, PartRule] = {
    1: PartRule(1, "L", 4, 0, 0, "required", "required", 1, 1,
                frozenset({QuestionType.LC_PHOTO_ACTION, QuestionType.LC_PHOTO_STATE})),
    2: PartRule(2, "L", 3, 0, 0, "forbidden", "required", 1, 1,
                frozenset({QuestionType.LC_WH_QUESTION, QuestionType.LC_YES_NO,
                           QuestionType.LC_INDIRECT_RESPONSE})),
    3: PartRule(3, "L", 4, 0, 0, "optional", "required", 3, 3, LISTENING_TYPES),
    4: PartRule(4, "L", 4, 0, 0, "optional", "required", 3, 3, LISTENING_TYPES),
    5: PartRule(5, "R", 4, 1, 1, "forbidden", "forbidden", 1, 1, _P5_TYPES),
    6: PartRule(6, "R", 4, 1, 1, "forbidden", "forbidden", 4, 4, _P6_TYPES),
    7: PartRule(7, "R", 4, 1, 3, "optional", "forbidden", 2, 5, READING_TYPES),
}

MIN_CROSS_REFERENCE_ITEMS = 2   # §Phase 7: double/triple bắt buộc ≥2 câu đọc chéo


def _check_presence(kind: str, rule_value: str, present: bool, errs: list[str]) -> None:
    if rule_value == "required" and not present:
        errs.append(f"thiếu {kind} (part này bắt buộc có)")
    elif rule_value == "forbidden" and present:
        errs.append(f"có {kind} nhưng part này không được có")


def check_group(group: ExamGroup) -> list[str]:
    """Trả về danh sách lỗi. Rỗng nghĩa là hợp lệ."""
    errs: list[str] = []
    rule = PART_RULES.get(group.part_number)
    if rule is None:
        return [f"part_number={group.part_number} không hợp lệ (chỉ 1–7)"]

    n_p = len(group.passages)
    if not (rule.passages_min <= n_p <= rule.passages_max):
        rng = (str(rule.passages_min) if rule.passages_min == rule.passages_max
               else f"{rule.passages_min}–{rule.passages_max}")
        errs.append(f"có {n_p} passage, part {rule.part_number} cần {rng} (lỗi P0-3)")

    _check_presence("image_url", rule.image, group.image_url is not None, errs)
    _check_presence("audio", rule.audio, group.audio is not None, errs)

    n_q = len(group.questions)
    if not (rule.questions_min <= n_q <= rule.questions_max):
        rng = (str(rule.questions_min) if rule.questions_min == rule.questions_max
               else f"{rule.questions_min}–{rule.questions_max}")
        errs.append(f"có {n_q} câu hỏi, part {rule.part_number} cần {rng}")

    for q in group.questions:
        if len(q.options) != rule.n_options:
            errs.append(f"[{q.item_id}] có {len(q.options)} lựa chọn, "
                        f"part {rule.part_number} cần {rule.n_options} (lỗi P0-2)")
        if q.question_type not in rule.allowed_types:
            errs.append(f"[{q.item_id}] question_type={q.question_type} "
                        f"không hợp với part {rule.part_number}")
        # Part 7: mọi câu phải định vị được bằng chứng (§Phase 7)
        if rule.part_number == 7 and q.evidence_span is None:
            errs.append(f"[{q.item_id}] Part 7 bắt buộc có evidence_span")

    # Double/triple passage phải thật sự bắt đọc chéo, nếu không thì chia nhỏ ra là xong
    if n_p >= 2:
        n_cross = sum(1 for q in group.questions
                      if q.question_type is QuestionType.RC_CROSS_REFERENCE)
        if n_cross < MIN_CROSS_REFERENCE_ITEMS:
            errs.append(f"group {n_p} passage chỉ có {n_cross} câu rc_cross_reference, "
                        f"cần ≥{MIN_CROSS_REFERENCE_ITEMS}")

    return errs


def check_groups(groups: list[ExamGroup]) -> dict[str, list[str]]:
    """Kiểm nhiều group. Trả về {group_id: [lỗi]}, chỉ chứa group có lỗi."""
    out: dict[str, list[str]] = {}
    for g in groups:
        errs = check_group(g)
        if errs:
            out[g.group_id] = errs
    return out
