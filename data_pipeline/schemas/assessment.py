"""Chấm bài AI và shadowing — §Phase 10 và §Phase 8C."""

from __future__ import annotations

from pydantic import Field, model_validator

from .common import StrictModel
from .enums import Accent, CEFRLevel

__all__ = [
    "DimensionScore", "ErrorFinding", "AssessmentResult",
    "AssessmentPrompt", "ShadowingSegment", "ShadowingClip",
]


class DimensionScore(StrictModel):
    dimension: str = Field(min_length=2)
    score: float = Field(ge=0.0, le=5.0)
    # Bắt trích dẫn từ bài làm — chống chấm cảm tính và chấm theo độ dài
    evidence_quote: str = Field(min_length=1)
    feedback_vi: str = Field(min_length=10)


class ErrorFinding(StrictModel):
    type: str = Field(min_length=2)
    span: str = Field(min_length=1)
    correction: str = Field(min_length=1)
    # §Phase 10: BẮT BUỘC. Đây là đường đưa kết quả chấm ngược về BKT.
    # Không có nó thì chấm xong mastery không cập nhật được.
    concept_id: str = Field(min_length=2)


class AssessmentResult(StrictModel):
    """Output bắt buộc của prompt chấm bài — §Phase 10."""
    overall_band: float = Field(ge=0.0, le=5.0)
    dimension_scores: list[DimensionScore] = Field(min_length=1)
    errors: list[ErrorFinding] = Field(default_factory=list)
    next_concepts: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def overall_consistent_with_dimensions(self):
        """Chống nịnh điểm: điểm tổng không được vượt điểm cao nhất của từng chiều."""
        if self.dimension_scores:
            top = max(d.score for d in self.dimension_scores)
            if self.overall_band > top + 0.5:
                raise ValueError(
                    f"overall_band {self.overall_band} vượt quá điểm chiều cao nhất {top}")
        return self


class AssessmentPrompt(StrictModel):
    """System prompt chấm bài, lưu như dữ liệu để version được."""
    prompt_id: str = Field(min_length=1)
    target: str = Field(pattern=r"^(speaking|writing)$")
    rubric_ref: str = Field(min_length=1)
    system_prompt: str = Field(min_length=100)
    output_schema_ref: str = Field(default="AssessmentResult")
    version: str = Field(default="1.0.0", pattern=r"^\d+\.\d+\.\d+$")


class ShadowingSegment(StrictModel):
    order: int = Field(ge=1)
    text: str = Field(min_length=3)
    start_ms: int | None = Field(default=None, ge=0)
    end_ms: int | None = Field(default=None, ge=0)


class ShadowingClip(StrictModel):
    """§Phase 8C — 20–30 đoạn 30–60 giây, có timestamp từng câu."""
    clip_id: str = Field(min_length=1)
    cefr_level: CEFRLevel
    accent: Accent
    script: str = Field(min_length=20)
    audio_url: str | None = None            # null cho tới khi TTS xong
    duration_ms: int | None = Field(default=None, ge=0)
    segments: list[ShadowingSegment] = Field(min_length=1)
    concept_ids: list[str] = Field(min_length=1)

    @model_validator(mode="after")
    def segment_order_contiguous(self):
        orders = [s.order for s in self.segments]
        if orders != list(range(1, len(orders) + 1)):
            raise ValueError(f"segment.order phải liên tục từ 1, đang là {orders}")
        return self

    @model_validator(mode="after")
    def duration_in_range(self):
        """30–60 giây theo §Phase 8C. Chỉ kiểm khi đã có audio thật."""
        if self.duration_ms is not None and not (20_000 <= self.duration_ms <= 90_000):
            raise ValueError(
                f"duration_ms={self.duration_ms} ngoài khoảng hợp lý 20–90 giây")
        return self
