"""Ràng buộc của các model ngoài Exam, và các bất biến toàn cục."""

from __future__ import annotations

import sys
from datetime import UTC, datetime
from pathlib import Path

import pytest
from pydantic import ValidationError

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from conftest import make_item  # noqa: E402
from schemas import (  # noqa: E402
    BATCH_MODELS, AssessmentResult, BatchMetadata, CEFRLevel, Collocation,
    Definition, Example, Flashcard, FlashcardBatch, IRTParams, ModuleType,
    Rubric, WritingTask,
)


def _defn(en="A meaning long enough.", vi="Nghĩa tiếng Việt."):
    return Definition(en=en, vi=vi)


def _examples(n=2):
    return [Example(sentence=f"Example sentence number {i}.", translation=f"Câu ví dụ {i}.")
            for i in range(n)]


def _flashcard(level=CEFRLevel.A1, collocations=None, **kw):
    payload = dict(
        lemma="address", pos="verb", sense_index=2,
        sense_label_en="to deal with a problem",
        ipa_us="/əˈdrɛs/", definition=_defn(), examples=_examples(),
        collocations=collocations or [], cefr_level=level, cefr_source="cefrj",
        topics=["business_office"], concept_ids=["vocab_business_office_b1"],
        difficulty_prior=0.4,
    )
    payload.update(kw)
    return Flashcard(**payload)


# --- Flashcard ---------------------------------------------------------------

def test_flashcard_id_and_embedding_text_are_computed():
    """§2.7: công thức chốt cứng — code tính, không nhận từ LLM."""
    fc = _flashcard()
    assert fc.id.startswith("vocab_")
    assert fc.embedding_text.startswith("address (verb, to deal with a problem).")
    assert "Examples:" in fc.embedding_text


def test_flashcard_llm_supplied_id_is_overwritten():
    """LLM khai ID bừa cũng không ăn thua — tính lại từ nội dung."""
    fc = _flashcard(id="vocab_deadbeefdeadbeef", embedding_text="rác")
    assert fc.id != "vocab_deadbeefdeadbeef"
    assert fc.embedding_text != "rác"


def test_b2_requires_three_collocations():
    with pytest.raises(ValidationError, match="≥3 collocation"):
        _flashcard(level=CEFRLevel.B2)


def test_b2_with_three_collocations_passes():
    cols = [Collocation(pattern="V+N", text=f"address the issue {i}", cefr="B2")
            for i in range(3)]
    assert len(_flashcard(level=CEFRLevel.B2, collocations=cols).collocations) == 3


def test_a1_does_not_require_collocations():
    assert _flashcard(level=CEFRLevel.A1).collocations == []


def test_octanove_is_valid_cefr_source():
    """Quyết định D3 — 600 từ C1 đến từ Octanove, không phải CEFR-J."""
    assert _flashcard(cefr_source="octanove").cefr_source == "octanove"


def test_unknown_field_rejected():
    """extra='forbid': LLM bịa thêm khoá là dấu hiệu prompt lệch, phải nổ."""
    with pytest.raises(ValidationError):
        _flashcard(khoa_la="giá trị")


def test_markdown_link_in_definition_rejected():
    """Lỗi P0-1."""
    with pytest.raises(ValidationError, match="Markdown link"):
        _flashcard(definition=_defn(en="See [https://a](https://a) for details."))


# --- IRT ---------------------------------------------------------------------

def test_cannot_claim_calibrated_without_responses():
    """exam-quality-bar.md §7: độ khó thật chỉ có khi có người làm bài."""
    with pytest.raises(ValidationError, match="cần ≥200 lượt"):
        IRTParams(calibration_status="calibrated", n_responses=5)


def test_uncalibrated_must_not_carry_params():
    with pytest.raises(ValidationError, match="không được có tham số"):
        IRTParams(calibration_status="uncalibrated", b=0.4)


def test_default_irt_is_uncalibrated():
    assert IRTParams().calibration_status == "uncalibrated"


# --- Batch metadata ----------------------------------------------------------

def _meta(module_type=ModuleType.FLASHCARD, total=1):
    return BatchMetadata(
        batch_id="b1", module_type=module_type, generated_by="claude-opus-5",
        generated_at=datetime.now(UTC), total_records=total)


def test_is_ai_generated_must_be_true():
    """§0.7 — bắt buộc, không ngoại lệ."""
    with pytest.raises(ValidationError, match="is_ai_generated"):
        BatchMetadata(batch_id="b", module_type=ModuleType.EXAM,
                      is_ai_generated=False, generated_by="x",
                      generated_at=datetime.now(UTC), total_records=0)


def test_generated_at_needs_timezone():
    with pytest.raises(ValidationError, match="timezone"):
        BatchMetadata(batch_id="b", module_type=ModuleType.EXAM, generated_by="x",
                      generated_at=datetime(2026, 1, 1), total_records=0)


def test_total_records_must_match_actual_count():
    """Lỗi P0-5 — pipeline đếm lại, không tin LLM khai."""
    with pytest.raises(ValidationError, match="đếm được"):
        FlashcardBatch(batch_metadata=_meta(total=99), flashcards=[_flashcard()])


def test_matching_count_passes():
    assert FlashcardBatch(batch_metadata=_meta(total=1),
                          flashcards=[_flashcard()]).batch_metadata.total_records == 1


def test_module_type_must_match_batch_class():
    with pytest.raises(ValidationError, match="module_type phải là FLASHCARD"):
        FlashcardBatch(batch_metadata=_meta(module_type=ModuleType.EXAM, total=0))


def test_all_eight_module_types_have_a_batch_model():
    assert set(BATCH_MODELS) == set(ModuleType)
    assert len(BATCH_MODELS) == 8


# --- Rubric / Writing --------------------------------------------------------

def _dimension(name="fluency", weight=1.0):
    return {
        "name": name, "weight": weight, "concept_id": "sp_fluency",
        "band_descriptors": [
            {"band": b, "descriptor_en": f"Band {b} descriptor text.",
             "descriptor_vi": f"Mô tả band {b} bằng tiếng Việt."}
            for b in range(6)
        ],
    }


def test_rubric_weights_must_sum_to_one():
    with pytest.raises(ValidationError, match="tổng weight"):
        Rubric(name="Speaking", dimensions=[_dimension(weight=0.5)])


def test_rubric_dimension_needs_all_six_bands():
    d = _dimension()
    d["band_descriptors"] = d["band_descriptors"][:5]
    with pytest.raises(ValidationError):
        Rubric(name="Speaking", dimensions=[d])


def test_writing_sample_must_meet_min_words():
    """DoD Phase 9: đếm bằng code, không tin lời khai."""
    with pytest.raises(ValidationError, match="dưới min_words"):
        WritingTask(task_type="opinion_essay", prompt="Discuss remote work policy.",
                    sample_answer_c1="Too short an answer for this task.",
                    rubric_ref="rub_x", concept_ids=["wr_task_response"],
                    difficulty_prior=0.6, min_words=300)


# --- Assessment --------------------------------------------------------------

def test_overall_band_cannot_exceed_dimension_scores():
    """Chống nịnh điểm (§Phase 10)."""
    with pytest.raises(ValidationError, match="vượt quá điểm chiều cao nhất"):
        AssessmentResult(
            overall_band=5.0,
            dimension_scores=[{"dimension": "fluency", "score": 2.0,
                               "evidence_quote": "um... uh...",
                               "feedback_vi": "Còn ngập ngừng nhiều."}])


# --- Exam set ----------------------------------------------------------------

def test_exam_set_title_must_respect_trademark():
    """§0.7 — TOEIC® là nhãn hiệu ETS."""
    from schemas import ExamSet
    with pytest.raises(ValidationError, match="§0.7"):
        ExamSet(set_id="set_001", title="TOEIC Practice Test 1", total_questions=0)


def test_exam_set_title_with_format_wording_passes():
    from schemas import ExamSet
    s = ExamSet(set_id="set_001", title="Đề luyện theo định dạng TOEIC số 1",
                total_questions=0)
    assert s.total_questions == 0


def test_exam_set_total_must_match_refs():
    from schemas import ExamSet
    with pytest.raises(ValidationError, match="P0-5"):
        ExamSet(set_id="s", title="Đề luyện theo định dạng TOEIC", total_questions=5,
                reading=[{"group_id": "g", "item_id": "i", "position": 1}])


def test_item_id_stable_across_construction():
    """Cùng nội dung dựng hai lần → cùng item_id (idempotent)."""
    assert make_item().item_id == make_item().item_id
