import json
from collections.abc import AsyncIterator
from typing import Any

import httpx

from app.config import Settings
from app.errors import ProviderError, is_retryable_http_status, retry_after_seconds
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
        if request.messages is not None:
            body["messages"] = [{"role": "system", "content": request.system_prompt}] + [
                turn.model_dump() for turn in request.messages
            ]
        if request.json_output:
            body["response_format"] = {"type": "json_object"}
        self._reasoning_options(request, body)

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
            if choices[0].get("finish_reason") == "length":
                raise ProviderError(
                    code="AI_OUTPUT_BUDGET_EXCEEDED",
                    message="The completion exceeded its output budget",
                    status_code=502,
                    retryable=False,
                )
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
                retry_after_seconds=retry_after_seconds(exc.response.headers.get("retry-after")),
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

    async def stream(self, request: LlmGenerateRequest) -> AsyncIterator[dict[str, Any]]:
        api_key = self._settings.llm_api_key.get_secret_value()
        if not self._settings.llm_enabled or not api_key:
            raise ProviderError(
                code="AI_DISABLED", message="LLM generation is disabled", status_code=503
            )
        messages = [{"role": "system", "content": request.system_prompt}]
        messages += (
            [turn.model_dump() for turn in request.messages]
            if request.messages is not None
            else [{"role": "user", "content": request.user_prompt}]
        )
        body = {
            "model": request.model,
            "messages": messages,
            "temperature": request.temperature,
            "max_tokens": request.max_output_tokens,
            "stream": True,
        }
        self._reasoning_options(request, body)
        text: list[str] = []
        model = request.model
        usage: dict[str, Any] = {}
        size = 0
        finished = False
        finish_reason = None
        try:
            async with self._client.stream(
                "POST",
                f"{str(self._settings.llm_base_url).rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json=body,
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if data == "[DONE]":
                        finished = True
                        break
                    payload = json.loads(data)
                    if not isinstance(payload, dict) or payload.get("error"):
                        raise ValueError("Invalid stream frame")
                    model = str(payload.get("model") or model)
                    provider_meta = payload.get("x_groq") or {}
                    if not isinstance(provider_meta, dict):
                        raise ValueError("Invalid provider metadata")
                    next_usage = payload.get("usage") or provider_meta.get("usage")
                    if next_usage is not None:
                        if not isinstance(next_usage, dict):
                            raise ValueError("Invalid usage")
                        usage = next_usage
                    choices = payload.get("choices") or []
                    if not isinstance(choices, list):
                        raise ValueError("Invalid choices")
                    if choices:
                        finish_reason = choices[0].get("finish_reason") or finish_reason
                        delta = choices[0].get("delta") or {}
                        content = delta.get("content")
                        if content is not None:
                            if not isinstance(content, str):
                                raise ValueError("Invalid delta")
                            size += len(content)
                            if size > 200_000:
                                raise ValueError("Response too large")
                            text.append(content)
                            yield {"delta": content}
            if finish_reason == "length":
                raise ProviderError(
                    code="AI_OUTPUT_BUDGET_EXCEEDED",
                    message="The completion exceeded its output budget",
                    status_code=502,
                    retryable=False,
                )
            if not finished or not "".join(text).strip():
                raise ProviderError(
                    code="AI_STREAM_INCOMPLETE",
                    message="The completion was interrupted",
                    status_code=502,
                    retryable=True,
                )
            result = LlmGenerateResponse(
                content="".join(text),
                model=model,
                input_tokens=self._token_count(usage.get("prompt_tokens")),
                output_tokens=self._token_count(usage.get("completion_tokens")),
            )
            yield {"result": result.model_dump()}
        except httpx.HTTPStatusError as exc:
            status = exc.response.status_code
            raise ProviderError(
                code=f"AI_PROVIDER_HTTP_{status}",
                message="The LLM provider rejected the request",
                status_code=502,
                retryable=is_retryable_http_status(status),
                retry_after_seconds=retry_after_seconds(exc.response.headers.get("retry-after")),
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
                message="The provider stream was invalid",
                status_code=502,
                retryable=True,
            ) from exc

    def _reasoning_options(self, request: LlmGenerateRequest, body: dict[str, Any]) -> None:
        # Verified Groq GPT-OSS capability only. Other providers keep their existing contract.
        if self._settings.llm_base_url.host == "api.groq.com" and request.model in (
            "openai/gpt-oss-20b",
            "openai/gpt-oss-120b",
        ):
            body["reasoning_effort"] = (
                request.reasoning_effort or self._settings.llm_reasoning_effort
            )
            body["max_completion_tokens"] = body.pop("max_tokens")

    @staticmethod
    def _token_count(value: Any) -> int | None:
        if value is None:
            return None
        if isinstance(value, bool):
            raise ValueError("Token count cannot be boolean")
        parsed = int(value)
        if parsed < 0:
            raise ValueError("Token count cannot be negative")
        return parsed
