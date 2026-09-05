"""Kiểu dùng chung cho mọi module — §2.2 của work order."""

from __future__ import annotations

import re
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from .enums import ModuleType, ReviewStatus, SourceType

__all__ = ["StrictModel", "Definition", "Example", "BatchMetadata", "SCHEMA_VERSION"]

SCHEMA_VERSION = "1.0.0"

# Lỗi P0-1: LLM hay trả URL bọc cú pháp Markdown "[https://a](https://a)".
_MARKDOWN_LINK = re.compile(r"\[[^\]]*\]\([^)]*\)")


class StrictModel(BaseModel):
    """Nền chung: từ chối field lạ thay vì bỏ qua im lặng.

    Nếu LLM trả thừa một khoá không có trong schema, đó là dấu hiệu prompt lệch
    hoặc model bịa cấu trúc — phải phát hiện chứ không nuốt.
    """
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class Definition(StrictModel):
    """Cặp Anh–Việt. Dùng cho định nghĩa flashcard và giải thích đáp án."""
    en: str = Field(min_length=5)
    vi: str = Field(min_length=2)

    @field_validator("en", "vi")
    @classmethod
    def no_markdown_link(cls, v: str) -> str:
        if _MARKDOWN_LINK.search(v):
            raise ValueError("còn cú pháp Markdown link (lỗi P0-1)")
        return v


class Example(StrictModel):
    sentence: str = Field(min_length=5)
    translation: str = Field(min_length=2)
    source: SourceType = SourceType.GENERATED


class BatchMetadata(StrictModel):
    """Header của mọi file batch — §2.2."""
    schema_version: str = Field(default=SCHEMA_VERSION, pattern=r"^\d+\.\d+\.\d+$")
    batch_id: str = Field(min_length=1)
    module_type: ModuleType

    # §0.7 — bắt buộc true trên mọi batch, không có ngoại lệ
    is_ai_generated: bool = True

    generated_by: str = Field(min_length=1)   # model + version cụ thể, vd "claude-opus-5"
    generated_at: datetime                    # UTC ISO-8601
    review_status: ReviewStatus = ReviewStatus.DRAFT

    # Lỗi P0-5: PIPELINE đếm lại, không phải LLM khai. Validator so với len(data).
    total_records: int = Field(ge=0)

    @field_validator("is_ai_generated")
    @classmethod
    def must_be_true(cls, v: bool) -> bool:
        if v is not True:
            raise ValueError("is_ai_generated bắt buộc là true (§0.7)")
        return v

    @field_validator("generated_at")
    @classmethod
    def must_be_utc(cls, v: datetime) -> datetime:
        if v.tzinfo is None:
            raise ValueError("generated_at phải có timezone (UTC)")
        return v
