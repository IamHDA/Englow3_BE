from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class LlmGenerateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    model: str = Field(min_length=1, max_length=200)
    system_prompt: str = Field(min_length=1, max_length=100_000)
    user_prompt: str = Field(min_length=1, max_length=200_000)
    temperature: float = Field(default=0.2, ge=0.0, le=2.0)
    max_output_tokens: int = Field(default=2_048, ge=1, le=32_768)
    json_output: bool = False


class LlmGenerateResponse(BaseModel):
    content: str = Field(min_length=1)
    model: str = Field(min_length=1, max_length=200)
    input_tokens: int = Field(default=0, ge=0)
    output_tokens: int = Field(default=0, ge=0)


class EmbeddingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    text: str = Field(min_length=1, max_length=20_000)


class EmbeddingResponse(BaseModel):
    embedding: list[float] = Field(min_length=64, max_length=4_096)
    model: str = Field(min_length=1, max_length=200)
    input_tokens: int = Field(default=0, ge=0)


class WordAssessment(BaseModel):
    word: str = Field(min_length=1)
    accuracy: float | None = Field(default=None, ge=0, le=100)
    error_type: str | None = None
    offset_ms: int | None = Field(default=None, ge=0)
    duration_ms: int | None = Field(default=None, ge=0)


class SpeechAssessmentResponse(BaseModel):
    recognized_text: str = Field(min_length=1)
    accuracy: float | None = Field(default=None, ge=0, le=100)
    fluency: float | None = Field(default=None, ge=0, le=100)
    completeness: float | None = Field(default=None, ge=0, le=100)
    prosody: float | None = Field(default=None, ge=0, le=100)
    pronunciation: float | None = Field(default=None, ge=0, le=100)
    request_id: str | None = None
    words: list[WordAssessment] = Field(default_factory=list)
    raw: dict[str, Any] = Field(default_factory=dict)


class ErrorResponse(BaseModel):
    code: str
    message: str
    retryable: bool
