"""Speaking, Writing và Rubric — §2.6 của work order."""

from __future__ import annotations

from pydantic import Field, model_validator

from .common import StrictModel
from .enums import ReviewStatus, WritingTaskType
from .ids import rubric_id as make_rubric_id, task_id as make_task_id

__all__ = ["BandDescriptor", "RubricDimension", "Rubric", "SpeakingTask", "WritingTask"]

BANDS = (0, 1, 2, 3, 4, 5)


class BandDescriptor(StrictModel):
    band: int = Field(ge=0, le=5)
    descriptor_en: str = Field(min_length=10)
    descriptor_vi: str = Field(min_length=10)


class RubricDimension(StrictModel):
    name: str = Field(min_length=2)
    weight: float = Field(gt=0.0, le=1.0)
    concept_id: str = Field(min_length=2)   # map về concept sp_*/wr_* của taxonomy
    band_descriptors: list[BandDescriptor] = Field(min_length=len(BANDS))

    @model_validator(mode="after")
    def all_bands_present(self):
        """DoD Phase 9: không được để trống band nào."""
        got = sorted(b.band for b in self.band_descriptors)
        if got != list(BANDS):
            raise ValueError(f"phải có đủ band {list(BANDS)}, đang có {got}")
        return self


class Rubric(StrictModel):
    """Tách riêng; task chỉ giữ `rubric_ref` (§2.6)."""
    rubric_id: str = Field(default="")
    name: str = Field(min_length=3)
    version: str = Field(default="1.0.0", pattern=r"^\d+\.\d+\.\d+$")
    dimensions: list[RubricDimension] = Field(min_length=1)

    @model_validator(mode="after")
    def weights_sum_to_one(self):
        total = sum(d.weight for d in self.dimensions)
        if abs(total - 1.0) > 0.001:
            raise ValueError(f"tổng weight phải bằng 1.0, đang là {total:.3f}")
        return self

    @model_validator(mode="after")
    def compute_id(self):
        object.__setattr__(self, "rubric_id", make_rubric_id(self.name, self.version))
        return self


class _TaskBase(StrictModel):
    task_id: str = Field(default="")
    prompt: str = Field(min_length=10)
    sample_answer_c1: str = Field(min_length=30)
    rubric_ref: str = Field(min_length=1)
    concept_ids: list[str] = Field(min_length=1)
    difficulty_prior: float = Field(ge=0.0, le=1.0)
    review_status: ReviewStatus = ReviewStatus.DRAFT


class SpeakingTask(_TaskBase):
    """11 task theo định dạng TOEIC S&W (§Phase 9)."""
    part_number: int = Field(ge=1, le=11)
    prep_time_sec: int = Field(ge=0, le=120)
    response_time_sec: int = Field(ge=1, le=120)

    @model_validator(mode="after")
    def compute_id(self):
        object.__setattr__(self, "task_id", make_task_id(f"sp{self.part_number}", self.prompt))
        return self


class WritingTask(_TaskBase):
    """8 task theo định dạng TOEIC S&W (§Phase 9)."""
    task_type: WritingTaskType
    min_words: int | None = Field(default=None, ge=1)
    max_words: int | None = Field(default=None, ge=1)
    # Liên kết ngược về flashcard.id nếu từ đó có trong bank (§Phase 9)
    high_scoring_vocab: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def word_bounds_ordered(self):
        if self.min_words is not None and self.max_words is not None and self.max_words < self.min_words:
            raise ValueError(f"max_words ({self.max_words}) nhỏ hơn min_words ({self.min_words})")
        return self

    @model_validator(mode="after")
    def sample_meets_min_words(self):
        """Đếm bằng code, không tin lời khai (DoD Phase 9)."""
        if self.min_words is not None:
            n = len(self.sample_answer_c1.split())
            if n < self.min_words:
                raise ValueError(
                    f"sample_answer_c1 có {n} từ, dưới min_words={self.min_words}")
        return self

    @model_validator(mode="after")
    def compute_id(self):
        object.__setattr__(self, "task_id", make_task_id(self.task_type, self.prompt))
        return self
