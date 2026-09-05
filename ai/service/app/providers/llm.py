from typing import Any

import httpx

from app.config import Settings
from app.errors import ProviderError, is_retryable_http_status
from app.schemas import LlmGenerateRequest, LlmGenerateResponse


class OpenAiCompatibleProvider:
    def __init__(self, client: httpx.AsyncClient, settings: Settings) -> None:
        self._client = client
        self._settings = settings

    async def generate(self, request: LlmGenerateRequest) -> LlmGenerateResponse:
        api_key = self._settings.llm_api_key.get_secret_value()
        if not self._settings.llm_enabled or not api_key:
            raise ProviderError(
                code="AI_DISABLED",
                message="LLM generation is disabled",
                status_code=503,
            )

        body: dict[str, Any] = {
            "model": request.model,
            "messages": [
                {"role": "system", "content": request.system_prompt},
                {"role": "user", "content": request.user_prompt},
            ],
            "temperature": request.temperature,
            "max_tokens": request.max_output_tokens,
        }
        if request.json_output:
            body["response_format"] = {"type": "json_object"}

        try:
            response = await self._client.post(
                f"{str(self._settings.llm_base_url).rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json=body,
            )
            response.raise_for_status()
            payload = response.json()
            if not isinstance(payload, dict):
                raise ValueError("LLM response must be an object")
            choices = payload.get("choices")
            if not isinstance(choices, list) or not choices or not isinstance(choices[0], dict):
                raise ValueError("LLM response has no valid choice")
            message = choices[0].get("message")
            if not isinstance(message, dict):
                raise ValueError("LLM response has no valid message")
            content = message.get("content")
            if not isinstance(content, str) or not content:
                raise ProviderError(
                    code="AI_EMPTY_RESPONSE",
                    message="The LLM provider returned no completion",
                    status_code=502,
                    retryable=True,
                )
            usage = payload.get("usage") or {}
            if not isinstance(usage, dict):
                raise ValueError("LLM usage must be an object")
            return LlmGenerateResponse(
                content=content,
                model=str(payload.get("model") or request.model),
                input_tokens=self._token_count(usage.get("prompt_tokens")),
                output_tokens=self._token_count(usage.get("completion_tokens")),
            )
        except httpx.HTTPStatusError as exc:
            status = exc.response.status_code
            raise ProviderError(
                code=f"AI_PROVIDER_HTTP_{status}",
                message="The LLM provider rejected the request",
                status_code=502,
                retryable=is_retryable_http_status(status),
            ) from exc
        except httpx.RequestError as exc:
            raise ProviderError(
                code="AI_PROVIDER_UNAVAILABLE",
                message="The LLM provider is unavailable",
                status_code=502,
                retryable=True,
            ) from exc
        except (ValueError, TypeError, KeyError, IndexError, AttributeError) as exc:
            raise ProviderError(
                code="AI_PROVIDER_INVALID_RESPONSE",
                message="The LLM provider returned an invalid response",
                status_code=502,
                retryable=True,
            ) from exc

    @staticmethod
    def _token_count(value: Any) -> int:
        if value is None:
            return 0
        if isinstance(value, bool):
            raise ValueError("Token count cannot be boolean")
        parsed = int(value)
        if parsed < 0:
            raise ValueError("Token count cannot be negative")
        return parsed
