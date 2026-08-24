"""Integration guard for the published 200-question set."""

from __future__ import annotations

from pathlib import Path

from schemas import ExamBatch
from validators.exam_set_rules import check_exam_collection, check_exam_set

ROOT = Path(__file__).resolve().parent.parent


def test_set_001_matches_full_blueprint():
    groups = []
    for path in sorted((ROOT / "output" / "exams" / "bank").rglob("*.json")):
        batch = ExamBatch.model_validate_json(path.read_text(encoding="utf-8"))
        groups.extend(batch.groups)
    set_batch = ExamBatch.model_validate_json(
        (ROOT / "output" / "exams" / "sets" / "exam_sets_001.json").read_text(encoding="utf-8"))
    assert check_exam_set(set_batch.sets[0], groups) == []


def test_collection_guard_rejects_reused_test_content():
    groups = []
    for path in sorted((ROOT / "output" / "exams" / "bank").rglob("*.json")):
        batch = ExamBatch.model_validate_json(path.read_text(encoding="utf-8"))
        groups.extend(batch.groups)
    set_batch = ExamBatch.model_validate_json(
        (ROOT / "output" / "exams" / "sets" / "exam_sets_001.json").read_text(encoding="utf-8"))
    original = set_batch.sets[0]
    copies = [original.model_copy(update={"set_id": f"set_{index:03d}"})
              for index in range(1, 11)]

    errors = check_exam_collection(copies, groups)

    assert any("item_id is shared" in error for error in errors)
    assert any("passage_content is shared" in error for error in errors)
    assert any("audio_script is shared" in error for error in errors)
