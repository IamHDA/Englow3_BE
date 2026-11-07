"""The model that writes generated content, and the voice that records it.

Separate from providers/llm.py on purpose: generation has its own key, model and timeout
(AI_SERVICE_GENERATION_*), and always asks for one JSON object back.
"""

from __future__ import annotations

import json
from typing import Any

import httpx

from app.config import Settings
from app.errors import ProviderError, is_retryable_http_status, retry_after_seconds


def parse_object(text: str) -> dict[str, Any]:
    """The reply as an object, tolerating a ```json fence around it."""
    body = text.strip()
    if body.startswith("```"):
        body = body.split("\n", 1)[1] if "\n" in body else body
        body = body.rsplit("```", 1)[0]
    start, end = body.find("{"), body.rfind("}")
    if start < 0 or end < start:
        raise ValueError("the reply contains no JSON object")
    value = json.loads(body[start : end + 1])
    if not isinstance(value, dict):
        raise ValueError("the reply is not a JSON object")
    return value


class GenerationProvider:
    def __init__(self, client: httpx.AsyncClient, settings: Settings) -> None:
        self._client = client
        self._settings = settings

    @property
    def model(self) -> str:
        return self._settings.generation_model

    async def complete(self, messages: list[dict[str, str]]) -> tuple[str, dict[str, int | None]]:
        """The raw reply text and token usage.

        Parsing is the caller's, so a bad reply can be fed back to the model.
        """
        api_key = self._settings.generation_api_key.get_secret_value()
        if not self._settings.generation_enabled or not api_key or not self.model:
            raise ProviderError(
                code="AI_GENERATION_DISABLED",
                message="Content generation is not configured",
                status_code=503,
            )
        body: dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "response_format": {"type": "json_object"},
        }
        effort = self._settings.generation_reasoning_effort
        if effort:
            # Reasoning models fix their own sampling; a temperature alongside is refused by some.
            body["reasoning_effort"] = effort
        else:
            body["temperature"] = 0.8
        try:
            response = await self._client.post(
                f"{str(self._settings.generation_base_url).rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json=body,
            )
            response.raise_for_status()
            payload = response.json()
            choice = payload["choices"][0]
            if choice.get("finish_reason") == "length":
                raise ProviderError(
                    code="AI_OUTPUT_BUDGET_EXCEEDED",
                    message="The completion was cut off",
                    status_code=502,
                    retryable=True,
                )
            content = choice["message"]["content"]
            if not isinstance(content, str) or not content:
                raise ProviderError(
                    code="AI_EMPTY_RESPONSE",
                    message="The provider returned no completion",
                    status_code=502,
                    retryable=True,
                )
            usage = payload.get("usage") or {}
            return content, {
                "input_tokens": usage.get("prompt_tokens"),
                "output_tokens": usage.get("completion_tokens"),
            }
        except httpx.HTTPStatusError as exc:
            status = exc.response.status_code
            raise ProviderError(
                code=f"AI_PROVIDER_HTTP_{status}",
                message="The generation provider rejected the request",
                status_code=502,
                retryable=is_retryable_http_status(status),
                retry_after_seconds=retry_after_seconds(exc.response.headers.get("retry-after")),
            ) from exc
        except httpx.RequestError as exc:
            raise ProviderError(
                code="AI_PROVIDER_UNAVAILABLE",
                message="The generation provider is unavailable",
                status_code=502,
                retryable=True,
            ) from exc
        except (ValueError, TypeError, KeyError, IndexError) as exc:
            raise ProviderError(
                code="AI_PROVIDER_INVALID_RESPONSE",
                message="The generation provider returned an invalid response",
                status_code=502,
                retryable=True,
            ) from exc


class EdgeSpeechSynthesizer:
    """Neural voices from Microsoft's free service (edge-tts).

    MP3 frames of one format join by bytes, so no ffmpeg is needed.
    """

    async def synthesize(self, segments: list[tuple[str, str]], rate: str = "-5%") -> bytes:
        import edge_tts

        audio = bytearray()
        for text, voice in segments:
            async for chunk in edge_tts.Communicate(text, voice, rate=rate).stream():
                if chunk["type"] == "audio":
                    audio.extend(chunk["data"])
        if not audio:
            raise ProviderError(
                code="AI_SPEECH_SYNTHESIS_EMPTY",
                message="No audio came back from the voice service",
                status_code=502,
                retryable=True,
            )
        return bytes(audio)
