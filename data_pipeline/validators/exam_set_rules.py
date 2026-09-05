"""Whole-test blueprint checks that cannot be enforced on one group alone."""

from __future__ import annotations

import collections
import hashlib
import re

from schemas import ExamGroup, ExamSet
from schemas.enums import QuestionType

PART_QUOTAS = {1: 6, 2: 25, 3: 39, 4: 30, 5: 30, 6: 16, 7: 54}
REQUIRED_PART7_TYPES = {
    QuestionType.RC_VOCAB_IN_CONTEXT,
    QuestionType.RC_PARAPHRASE,
    QuestionType.RC_SENTENCE_INSERTION,
}

EXPECTED_SET_COUNT = 10


def _normalise(text: str | None) -> str:
    """Normalise authored content before cross-test duplicate checks."""
    return re.sub(r"[^a-z0-9]+", " ", (text or "").casefold()).strip()


def _digest(*parts: str | None) -> str:
    payload = "\n".join(_normalise(part) for part in parts)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def check_exam_collection(exam_sets: list[ExamSet], groups: list[ExamGroup],
                          expected_count: int = EXPECTED_SET_COUNT) -> list[str]:
    """Validate a release of independent full tests, not shuffled copies.

    Content hashes supplement IDs so changing an ID cannot disguise copied
    passages, scripts, questions, or media references.
    """
    errors: list[str] = []
    if len(exam_sets) != expected_count:
        errors.append(f"Collection has {len(exam_sets)} sets; expected {expected_count}")

    group_index = {group.group_id: group for group in groups}
    item_index = {
        question.item_id: (group, question)
        for group in groups
        for question in group.questions
    }
    owners: dict[tuple[str, str], str] = {}

    def claim(kind: str, value: str | None, set_id: str) -> None:
        if not value:
            return
        key = (kind, value)
        previous = owners.get(key)
        if previous is not None and previous != set_id:
            errors.append(f"{kind} is shared by {previous} and {set_id}: {value[:24]}")
        else:
            owners[key] = set_id

    set_ids = [exam_set.set_id for exam_set in exam_sets]
    if len(set(set_ids)) != len(set_ids):
        errors.append("Collection contains duplicate set_id values")

    for exam_set in exam_sets:
        errors.extend(f"{exam_set.set_id}: {error}"
                      for error in check_exam_set(exam_set, groups))
        selected_group_ids: set[str] = set()
        for ref in list(exam_set.listening) + list(exam_set.reading):
            claim("item_id", ref.item_id, exam_set.set_id)
            indexed = item_index.get(ref.item_id)
            if indexed is None:
                continue
            group, question = indexed
            if ref.group_id != group.group_id:
                errors.append(
                    f"{exam_set.set_id}: item {ref.item_id} belongs to {group.group_id}, "
                    f"not referenced group {ref.group_id}")
            selected_group_ids.add(group.group_id)
            claim("item_content", _digest(
                question.question_text,
                next(option.text for option in question.options if option.is_correct),
                *(passage.text for passage in group.passages),
                group.audio.script if group.audio else None,
            ), exam_set.set_id)

        for group_id in selected_group_ids:
            group = group_index.get(group_id)
            if group is None:
                errors.append(f"{exam_set.set_id}: referenced group does not exist: {group_id}")
                continue
            claim("group_id", group.group_id, exam_set.set_id)
            for passage in group.passages:
                claim("passage_content", _digest(passage.text), exam_set.set_id)
                if passage.graphic_url:
                    claim("passage_graphic_url", str(passage.graphic_url), exam_set.set_id)
            if group.audio:
                claim("audio_script", _digest(group.audio.script), exam_set.set_id)
                if group.audio.audio_url:
                    claim("audio_url", str(group.audio.audio_url), exam_set.set_id)
            if group.image_url:
                claim("image_url", str(group.image_url), exam_set.set_id)

    return errors


def check_exam_set(exam_set: ExamSet, groups: list[ExamGroup]) -> list[str]:
    errors: list[str] = []
    item_index = {
        question.item_id: (group, question)
        for group in groups
        for question in group.questions
    }
    refs = list(exam_set.listening) + list(exam_set.reading)
    missing = [ref.item_id for ref in refs if ref.item_id not in item_index]
    if missing:
        return [f"{len(missing)} item references do not exist in the bank"]

    part_counts = collections.Counter(item_index[ref.item_id][0].part_number for ref in refs)
    for part, expected in PART_QUOTAS.items():
        if part_counts[part] != expected:
            errors.append(f"Part {part} has {part_counts[part]} questions; expected {expected}")

    selected_p7 = [ref for ref in exam_set.reading if item_index[ref.item_id][0].part_number == 7]
    questions_by_group = collections.Counter(ref.group_id for ref in selected_p7)
    p7_groups = {ref.group_id: item_index[ref.item_id][0] for ref in selected_p7}
    singles = [group for group in p7_groups.values() if len(group.passages) == 1]
    multiples = [group for group in p7_groups.values() if len(group.passages) >= 2]
    single_questions = sum(questions_by_group[group.group_id] for group in singles)
    multiple_questions = sum(questions_by_group[group.group_id] for group in multiples)
    if (len(singles), single_questions) != (10, 29):
        errors.append(
            f"Part 7 singles are {len(singles)} texts/{single_questions} questions; expected 10/29")
    if (len(multiples), multiple_questions) != (5, 25):
        errors.append(
            f"Part 7 multiple sets are {len(multiples)} groups/{multiple_questions} questions; expected 5/25")
    for group in multiples:
        if questions_by_group[group.group_id] != 5:
            errors.append(f"Part 7 multiple group {group.group_id} does not contain 5 selected questions")

    p7_types = {item_index[ref.item_id][1].question_type for ref in selected_p7}
    missing_types = sorted(question_type.value for question_type in REQUIRED_PART7_TYPES - p7_types)
    if missing_types:
        errors.append(f"Part 7 is missing question types: {missing_types}")

    graphic_items = [
        (group, question)
        for ref in exam_set.listening
        for group, question in [item_index[ref.item_id]]
        if question.question_type is QuestionType.LC_GRAPHIC_REFERENCE
    ]
    if len(graphic_items) < 2:
        errors.append(f"Listening has {len(graphic_items)} graphic-reference items; expected at least 2")
    for group, question in graphic_items:
        if group.image_url is None:
            errors.append(f"Graphic-reference item {question.item_id} has no group image_url")
    return errors
