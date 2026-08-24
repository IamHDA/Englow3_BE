from typing import Any

import httpx

from app.config import Settings
from app.errors import ProviderError, is_retryable_http_status
from app.schemas import EmbeddingRequest, EmbeddingResponse


class OpenAiCompatibleEmbeddingProvider:
    def __init__(self, client: httpx.AsyncClient, settings: Settings) -> None:
        self._client = client
        self._settings = settings

    async def embed(self, request: EmbeddingRequest) -> EmbeddingResponse:
        api_key = self._settings.embedding_api_key.get_secret_value()
        if not self._settings.embedding_enabled or not api_key:
            raise ProviderError("EMBEDDING_DISABLED", "Embedding generation is disabled", 503)
        try:
            response = await self._client.post(
                f"{str(self._settings.embedding_base_url).rstrip('/')}/embeddings",
                headers={"Authorization": f"Bearer {api_key}"},
                json={
                    "model": self._settings.embedding_model,
                    "input": request.text,
                    "dimensions": self._settings.embedding_dimensions,
                    "encoding_format": "float",
                },
            )
            response.raise_for_status()
            payload = response.json()
            data = payload.get("data") if isinstance(payload, dict) else None
            item = data[0] if isinstance(data, list) and data else None
            vector = item.get("embedding") if isinstance(item, dict) else None
            if not isinstance(vector, list) or len(vector) != self._settings.embedding_dimensions:
                raise ValueError("Embedding dimension mismatch")
            normalized = [self._finite_float(value) for value in vector]
            usage = payload.get("usage") or {}
            tokens = self._token_count(usage.get("prompt_tokens"))
            return EmbeddingResponse(
                embedding=normalized,
                model=str(payload.get("model") or self._settings.embedding_model),
                input_tokens=tokens,
            )
        except httpx.HTTPStatusError as exc:
            status = exc.response.status_code
            raise ProviderError(
                f"EMBEDDING_PROVIDER_HTTP_{status}",
                "The embedding provider rejected the request",
                502,
                is_retryable_http_status(status),
            ) from exc
        except httpx.RequestError as exc:
            raise ProviderError(
                "EMBEDDING_PROVIDER_UNAVAILABLE",
                "The embedding provider is unavailable",
                502,
                True,
            ) from exc
        except (ValueError, TypeError, KeyError, IndexError, AttributeError) as exc:
            raise ProviderError(
                "EMBEDDING_PROVIDER_INVALID_RESPONSE",
                "The embedding provider returned an invalid response",
                502,
                True,
            ) from exc

    @staticmethod
    def _finite_float(value: Any) -> float:
        parsed = float(value)
        if parsed != parsed or parsed in (float("inf"), float("-inf")):
            raise ValueError("Embedding contains a non-finite value")
        return parsed

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
