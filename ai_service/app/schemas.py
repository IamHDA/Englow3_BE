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
    content: str
    model: str
    input_tokens: int = 0
    output_tokens: int = 0


class WordAssessment(BaseModel):
    word: str
    accuracy: float | None = None
    error_type: str | None = None
    offset_ms: int | None = None
    duration_ms: int | None = None


class SpeechAssessmentResponse(BaseModel):
    recognized_text: str
    accuracy: float | None = None
    fluency: float | None = None
    completeness: float | None = None
    prosody: float | None = None
    pronunciation: float | None = None
    request_id: str | None = None
    words: list[WordAssessment] = Field(default_factory=list)
    raw: dict[str, Any] = Field(default_factory=dict)


class ErrorResponse(BaseModel):
    code: str
    message: str
    retryable: bool
