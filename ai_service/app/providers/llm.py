from typing import Any

import httpx

from app.config import Settings
from app.errors import ProviderError
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
                f"{self._settings.llm_base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json=body,
            )
            response.raise_for_status()
            payload = response.json()
            choice = payload.get("choices", [])[0]
            content = choice.get("message", {}).get("content")
            if not isinstance(content, str) or not content:
                raise ProviderError(
                    code="AI_EMPTY_RESPONSE",
                    message="The LLM provider returned no completion",
                    status_code=502,
                    retryable=True,
                )
            usage = payload.get("usage") or {}
            return LlmGenerateResponse(
                content=content,
                model=str(payload.get("model") or request.model),
                input_tokens=int(usage.get("prompt_tokens") or 0),
                output_tokens=int(usage.get("completion_tokens") or 0),
            )
        except httpx.HTTPStatusError as exc:
            status = exc.response.status_code
            raise ProviderError(
                code=f"AI_PROVIDER_HTTP_{status}",
                message="The LLM provider rejected the request",
                status_code=502,
                retryable=status == 429 or status >= 500,
            ) from exc
        except (httpx.RequestError, ValueError, TypeError, KeyError, IndexError) as exc:
            raise ProviderError(
                code="AI_PROVIDER_UNAVAILABLE",
                message="The LLM provider is unavailable or returned an invalid response",
                status_code=502,
                retryable=True,
            ) from exc
