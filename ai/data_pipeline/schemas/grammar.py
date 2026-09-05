"""GrammarPoint — §2.6 của work order."""

from __future__ import annotations

from pydantic import Field, model_validator

from .common import Example, StrictModel
from .enums import CEFRLevel, ReviewStatus
from .exam import ExamItem
from .ids import grammar_point_id

__all__ = ["CommonMistake", "GrammarPoint"]

MIN_COMMON_MISTAKES = 3


class CommonMistake(StrictModel):
    """Ưu tiên lỗi đặc trưng người Việt: thiếu article, present perfect vs past
    simple, sai giới từ, thiếu -s ngôi 3, trật tự tính từ."""
    wrong: str = Field(min_length=3)
    right: str = Field(min_length=3)
    why_vi: str = Field(min_length=10)


class GrammarPoint(StrictModel):
    id: str = Field(default="")               # tự tính
    title_en: str = Field(min_length=3)
    title_vi: str = Field(min_length=3)
    cefr_level: CEFRLevel
    concept_ids: list[str] = Field(min_length=1)

    theory_vi: str = Field(min_length=30)
    theory_en_summary: str = Field(min_length=20)   # dùng cho embedding_text §2.7
    form_patterns: list[str] = Field(min_length=1)
    examples: list[Example] = Field(min_length=2)
    common_mistakes: list[CommonMistake] = Field(min_length=MIN_COMMON_MISTAKES)

    # Tái dùng ExamItem thay vì tạo schema thứ hai (§Phase 6).
    # Work order baseline: at least five focused exercises per grammar point.
    quick_exercises: list[ExamItem] = Field(min_length=5)

    embedding_text: str = Field(default="")
    review_status: ReviewStatus = ReviewStatus.DRAFT

    @model_validator(mode="after")
    def exercises_are_part5(self):
        for ex in self.quick_exercises:
            if ex.part_number != 5:
                raise ValueError(
                    f"quick_exercise phải là part_number=5, có {ex.part_number}")
        return self

    @model_validator(mode="after")
    def compute_derived_fields(self):
        object.__setattr__(self, "id", grammar_point_id(self.title_en, self.cefr_level))
        patterns = "; ".join(self.form_patterns)
        object.__setattr__(
            self, "embedding_text",
            f"{self.title_en}. {self.theory_en_summary} Patterns: {patterns}")
        return self
