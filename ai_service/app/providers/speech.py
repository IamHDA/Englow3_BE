import base64
import json
from typing import Any

import httpx

from app.config import Settings
from app.errors import ProviderError, is_retryable_http_status
from app.schemas import SpeechAssessmentResponse, WordAssessment


class AzureSpeechProvider:
    def __init__(self, client: httpx.AsyncClient, settings: Settings) -> None:
        self._client = client
        self._settings = settings

    async def assess(
        self,
        audio: bytes,
        content_type: str,
        locale: str,
        reference_text: str | None,
    ) -> SpeechAssessmentResponse:
        api_key = self._settings.azure_speech_api_key.get_secret_value()
        if not self._settings.speech_enabled or not api_key:
            raise ProviderError(
                code="SPEECH_DISABLED",
                message="Speech assessment is disabled",
                status_code=503,
            )

        config: dict[str, Any] = {
            "GradingSystem": "HundredMark",
            "Granularity": "Phoneme",
            "Dimension": "Comprehensive",
            "EnableMiscue": True,
            "EnableProsodyAssessment": True,
        }
        if reference_text:
            config["ReferenceText"] = reference_text
        assessment = base64.b64encode(
            json.dumps(config, separators=(",", ":")).encode("utf-8")
        ).decode("ascii")

        try:
            response = await self._client.post(
                self._speech_url(),
                params={"language": locale, "format": "detailed"},
                headers={
                    "Ocp-Apim-Subscription-Key": api_key,
                    "Pronunciation-Assessment": assessment,
                    "Content-Type": content_type,
                    "Accept": "application/json",
                },
                content=audio,
            )
            response.raise_for_status()
            payload = response.json()
            if not isinstance(payload, dict):
                raise ValueError("Speech response must be an object")
            if payload.get("RecognitionStatus") != "Success":
                raise ProviderError(
                    code="SPEECH_RECOGNITION_FAILED",
                    message="Speech could not be recognized",
                    status_code=422,
                )
            candidates = payload.get("NBest")
            if (
                not isinstance(candidates, list)
                or not candidates
                or not isinstance(candidates[0], dict)
            ):
                raise ValueError("Speech response has no best candidate")
            best = candidates[0]
            scores = best.get("PronunciationAssessment") or {}
            if not isinstance(scores, dict):
                raise ValueError("Speech scores must be an object")
            word_items = best.get("Words") or []
            if not isinstance(word_items, list):
                raise ValueError("Speech words must be a list")
            words = [self._word(item) for item in word_items]
            return SpeechAssessmentResponse(
                recognized_text=str(best.get("Display") or payload.get("DisplayText") or ""),
                accuracy=self._number(scores.get("AccuracyScore")),
                fluency=self._number(scores.get("FluencyScore")),
                completeness=self._number(scores.get("CompletenessScore")),
                prosody=self._number(scores.get("ProsodyScore")),
                pronunciation=self._number(scores.get("PronScore")),
                request_id=response.headers.get("X-RequestId"),
                words=words,
                raw=payload,
            )
        except httpx.HTTPStatusError as exc:
            status = exc.response.status_code
            raise ProviderError(
                code=f"SPEECH_PROVIDER_HTTP_{status}",
                message="The speech provider rejected the request",
                status_code=502,
                retryable=is_retryable_http_status(status),
            ) from exc
        except httpx.RequestError as exc:
            raise ProviderError(
                code="SPEECH_PROVIDER_UNAVAILABLE",
                message="The speech provider is unavailable",
                status_code=502,
                retryable=True,
            ) from exc
        except (ValueError, TypeError, KeyError, IndexError, AttributeError) as exc:
            raise ProviderError(
                code="SPEECH_PROVIDER_INVALID_RESPONSE",
                message="The speech provider returned an invalid response",
                status_code=502,
                retryable=True,
            ) from exc

    def _speech_url(self) -> str:
        return (
            f"{str(self._settings.azure_speech_base_url).rstrip('/')}"
            "/stt/speech/recognition/conversation/cognitiveservices/v1"
        )

    @staticmethod
    def _number(value: Any) -> float | None:
        if isinstance(value, int | float) and not isinstance(value, bool):
            return float(value)
        return None

    @classmethod
    def _word(cls, item: dict[str, Any]) -> WordAssessment:
        if not isinstance(item, dict):
            raise TypeError("Speech word must be an object")
        scores = item.get("PronunciationAssessment") or {}
        return WordAssessment(
            word=str(item.get("Word") or ""),
            accuracy=cls._number(scores.get("AccuracyScore")),
            error_type=scores.get("ErrorType"),
            offset_ms=cls._ticks_to_millis(item.get("Offset")),
            duration_ms=cls._ticks_to_millis(item.get("Duration")),
        )

    @staticmethod
    def _ticks_to_millis(value: Any) -> int | None:
        return int(value) // 10_000 if isinstance(value, int) else None
