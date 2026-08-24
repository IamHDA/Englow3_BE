from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated

import httpx
from fastapi import Depends, FastAPI, File, Form, Request, UploadFile
from fastapi.responses import JSONResponse

from app.config import Settings, get_settings
from app.errors import ServiceError
from app.providers.llm import OpenAiCompatibleProvider
from app.providers.speech import AzureSpeechProvider
from app.schemas import (
    ErrorResponse,
    LlmGenerateRequest,
    LlmGenerateResponse,
    SpeechAssessmentResponse,
)
from app.security import require_internal_api_key


def create_app(settings: Settings | None = None) -> FastAPI:
    configured = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        timeout = httpx.Timeout(
            configured.read_timeout_seconds,
            connect=configured.connect_timeout_seconds,
        )
        async with httpx.AsyncClient(timeout=timeout) as client:
            app.state.llm_provider = OpenAiCompatibleProvider(client, configured)
            app.state.speech_provider = AzureSpeechProvider(client, configured)
            yield

    app = FastAPI(
        title="Englow AI Service",
        version="1.0.0",
        docs_url=None if configured.environment == "production" else "/docs",
        redoc_url=None,
        lifespan=lifespan,
    )
    app.dependency_overrides[get_settings] = lambda: configured

    @app.exception_handler(ServiceError)
    async def service_error_handler(_: Request, exc: ServiceError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content={
                "code": exc.code,
                "message": exc.message,
                "retryable": exc.retryable,
            },
        )

    @app.get("/health/live", include_in_schema=False)
    async def live() -> dict[str, str]:
        return {"status": "up"}

    @app.get("/health/ready", include_in_schema=False)
    async def ready() -> JSONResponse:
        checks = _readiness(configured)
        is_ready = all(checks.values())
        return JSONResponse(
            status_code=200 if is_ready else 503,
            content={"status": "up" if is_ready else "down", "checks": checks},
        )

    internal = [Depends(require_internal_api_key)]

    @app.post(
        "/internal/v1/llm/generate",
        response_model=LlmGenerateResponse,
        responses={401: {"model": ErrorResponse}, 502: {"model": ErrorResponse}},
        dependencies=internal,
    )
    async def generate(request: Request, body: LlmGenerateRequest) -> LlmGenerateResponse:
        return await request.app.state.llm_provider.generate(body)

    @app.post(
        "/internal/v1/speech/assess",
        response_model=SpeechAssessmentResponse,
        responses={401: {"model": ErrorResponse}, 502: {"model": ErrorResponse}},
        dependencies=internal,
    )
    async def assess_speech(
        request: Request,
        audio: Annotated[UploadFile, File()],
        locale: Annotated[str, Form(min_length=2, max_length=20)] = "en-US",
        reference_text: Annotated[str | None, Form(max_length=20_000)] = None,
    ) -> SpeechAssessmentResponse:
        content_type = audio.content_type or "application/octet-stream"
        if content_type not in {"audio/wav", "audio/x-wav", "audio/ogg"}:
            raise ServiceError(
                code="UNSUPPORTED_AUDIO_TYPE",
                message="Only WAV PCM and OGG Opus audio are accepted",
                status_code=415,
            )
        payload = await audio.read(configured.max_audio_bytes + 1)
        if not payload:
            raise ServiceError("EMPTY_AUDIO", "Audio is empty", 422)
        if len(payload) > configured.max_audio_bytes:
            raise ServiceError("AUDIO_TOO_LARGE", "Audio exceeds the configured size limit", 413)
        return await request.app.state.speech_provider.assess(
            payload, content_type, locale, reference_text
        )

    return app


def _readiness(settings: Settings) -> dict[str, bool]:
    internal_auth = bool(settings.internal_api_key.get_secret_value())
    llm = not settings.llm_enabled or bool(settings.llm_api_key.get_secret_value())
    speech = not settings.speech_enabled or bool(
        settings.azure_speech_api_key.get_secret_value()
    )
    return {"internal_auth": internal_auth, "llm": llm, "speech": speech}


app = create_app()
