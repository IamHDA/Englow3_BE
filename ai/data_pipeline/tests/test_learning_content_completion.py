"""Regression gates for the completed non-exam learning-data bank."""

from __future__ import annotations

import collections
import json
from pathlib import Path
from urllib.parse import urlparse

import pytest

from schemas import (
    AssessmentPromptBatch, AssessmentResult, FlashcardBatch, GrammarBatch,
    ShadowingBatch,
)

ROOT = Path(__file__).resolve().parent.parent
REQUIRED_ARTIFACTS = (
    ROOT / "output" / "grammar" / "grammar_batch_001.json",
    ROOT / "output" / "shadowing" / "shadowing_batch_001.json",
    ROOT / "output" / "prompts" / "assessment_prompts_batch_001.json",
    ROOT / "output" / "prompts" / "assessment_calibration_cases.json",
    ROOT / "output" / "_db" / "_manifest.json",
)

pytestmark = pytest.mark.skipif(
    not all(path.is_file() for path in REQUIRED_ARTIFACTS)
    or not any((ROOT / "output" / "flashcards").glob("flashcard_batch_*.json")),
    reason="requires generated learning-data artifacts; run make gen-learning-data",
)


def test_flashcard_bank_and_pronunciation_are_complete():
    cards = []
    for path in sorted((ROOT / "output" / "flashcards").glob("flashcard_batch_*.json")):
        cards.extend(FlashcardBatch.model_validate_json(
            path.read_text(encoding="utf-8")).flashcards)
    assert len(cards) == len({card.id for card in cards}) == 3000
    assert collections.Counter(card.cefr_level.value for card in cards) == {
        "A1": 400, "A2": 500, "B1": 700, "B2": 800, "C1": 600,
    }
    media = ROOT / "output" / "media" / "audio" / "flashcards"
    for card in cards:
        assert card.ipa_verified
        for value in (card.audio_url_us, card.audio_url_uk):
            assert value
            local = media / Path(urlparse(str(value)).path).name
            assert local.is_file() and local.stat().st_size > 300


def test_grammar_bank_has_five_exercises_per_point():
    path = ROOT / "output" / "grammar" / "grammar_batch_001.json"
    batch = GrammarBatch.model_validate_json(path.read_text(encoding="utf-8"))
    assert len(batch.grammar_points) == 90
    assert all(len(point.quick_exercises) == 5 for point in batch.grammar_points)
    ids = [exercise.item_id for point in batch.grammar_points
           for exercise in point.quick_exercises]
    assert len(ids) == len(set(ids)) == 450


def test_shadowing_and_dictation_bank_is_complete():
    path = ROOT / "output" / "shadowing" / "shadowing_batch_001.json"
    batch = ShadowingBatch.model_validate_json(path.read_text(encoding="utf-8"))
    assert len(batch.clips) == 30
    media = ROOT / "output" / "media" / "audio" / "shadowing"
    for clip in batch.clips:
        assert set(clip.practice_modes) == {"shadowing", "dictation"}
        assert 30_000 <= clip.duration_ms <= 60_000
        assert all(segment.start_ms is not None and segment.end_ms > segment.start_ms
                   for segment in clip.segments)
        local = media / Path(urlparse(str(clip.audio_url)).path).name
        assert local.is_file() and local.stat().st_size > 0


def test_assessment_prompts_and_fixtures_validate():
    prompt_path = ROOT / "output" / "prompts" / "assessment_prompts_batch_001.json"
    prompts = AssessmentPromptBatch.model_validate_json(
        prompt_path.read_text(encoding="utf-8"))
    assert {prompt.target for prompt in prompts.prompts} == {"speaking", "writing"}
    fixture_path = ROOT / "output" / "prompts" / "assessment_calibration_cases.json"
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    assert fixture["total_cases"] == len(fixture["cases"]) == 10
    for case in fixture["cases"]:
        AssessmentResult.model_validate(case["expected_result"])


def test_staging_contains_every_declared_table():
    path = ROOT / "output" / "_db" / "_manifest.json"
    manifest = json.loads(path.read_text(encoding="utf-8"))
    assert len(manifest["load_order"]) == 26
    assert all(manifest["row_counts"][table] > 0 for table in manifest["load_order"])
