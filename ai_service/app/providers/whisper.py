"""Speech assessment through an OpenAI-compatible Whisper endpoint (Groq by default).

Whisper transcribes with word timings; it does not score pronunciation. So the two
acoustic figures the grader needs are estimated, and say so in `raw`:

- fluency from speaking rate and pauses between words, which word timings measure;
- pronunciation from how confidently the recogniser understood the speech
  (duration-weighted segment log probability). Clear speech is recognised with high
  confidence; unclear sounds lower it. It is a proxy, not a phoneme-level score,
  which is why the grader is told the evidence is estimated.
"""

import io
import math
import wave
from typing import Any

import httpx

from app.config import Settings
from app.errors import ProviderError, is_retryable_http_status
from app.schemas import SpeechAssessmentResponse, WordAssessment

# Comfortable conversational English for an exam answer, in words per minute.
IDEAL_WPM = (110.0, 170.0)
LONG_PAUSE_SECONDS = 1.0
SHORT_PAUSE_SECONDS = 0.3
# exp(avg_logprob) at or below this reads as unintelligible; at or above the ceiling as fully clear.
CONFIDENCE_FLOOR = 0.45
CONFIDENCE_CEILING = 0.95


def _clamp(value: float) -> float:
    return max(0.0, min(100.0, value))


def fluency_score(words: list[dict[str, Any]]) -> tuple[float | None, dict[str, float]]:
    """Fluency 0-100 from speaking rate and pauses; None when there is too little speech."""
    timed = [
        w
        for w in words
        if isinstance(w.get("start"), (int, float)) and isinstance(w.get("end"), (int, float))
    ]
    if len(timed) < 3:
        return None, {}
    span = max(timed[-1]["end"] - timed[0]["start"], 0.1)
    wpm = len(timed) / (span / 60)
    gaps = [max(0.0, b["start"] - a["end"]) for a, b in zip(timed, timed[1:], strict=False)]
    pause_time = sum(g for g in gaps if g >= SHORT_PAUSE_SECONDS)
    long_pauses = sum(1 for g in gaps if g >= LONG_PAUSE_SECONDS)
    low, high = IDEAL_WPM
    rate_penalty = (
        min(50.0, (low - wpm) * 0.8)
        if wpm < low
        else min(30.0, (wpm - high) * 0.5)
        if wpm > high
        else 0.0
    )
    pause_ratio = pause_time / span
    pause_penalty = min(40.0, pause_ratio * 80.0) + min(20.0, long_pauses / (span / 60) * 4.0)
    metrics = {
        "words_per_minute": round(wpm, 1),
        "pause_ratio": round(pause_ratio, 3),
        "long_pauses": float(long_pauses),
        "speaking_seconds": round(span, 2),
    }
    return round(_clamp(100.0 - rate_penalty - pause_penalty), 1), metrics


def pronunciation_estimate(segments: list[dict[str, Any]]) -> tuple[float | None, float | None]:
    """Pronunciation 0-100 from duration-weighted recognition confidence, and that confidence."""
    weighted, total = 0.0, 0.0
    for segment in segments:
        logprob = segment.get("avg_logprob")
        start, end = segment.get("start"), segment.get("end")
        if (
            not isinstance(logprob, (int, float))
            or not isinstance(start, (int, float))
            or not isinstance(end, (int, float))
        ):
            continue
        duration = max(end - start, 0.01)
        # A segment Whisper thinks is not speech should not count as clear speech.
        speech_probability = 1.0 - float(segment.get("no_speech_prob") or 0.0)
        weighted += math.exp(logprob) * speech_probability * duration
        total += duration
    if total == 0:
        return None, None
    confidence = weighted / total
    score = (confidence - CONFIDENCE_FLOOR) / (CONFIDENCE_CEILING - CONFIDENCE_FLOOR) * 100
    return round(_clamp(score), 1), round(confidence, 3)


class WhisperSpeechProvider:
    def __init__(self, client: httpx.AsyncClient, settings: Settings) -> None:
        self._client = client
        self._settings = settings

    def _api_key(self) -> str:
        return (
            self._settings.whisper_api_key.get_secret_value()
            or self._settings.llm_api_key.get_secret_value()
        )

    def _url(self) -> str:
        base = self._settings.whisper_base_url or self._settings.llm_base_url
        return f"{str(base).rstrip('/')}/audio/transcriptions"

    async def assess(
        self,
        audio: bytes,
        content_type: str,
        locale: str,
        reference_text: str | None,
    ) -> SpeechAssessmentResponse:
        if not self._settings.speech_enabled or not self._api_key():
            raise ProviderError(
                code="SPEECH_DISABLED", message="Speech assessment is disabled", status_code=503
            )
        duration = None
        if content_type in {"audio/wav", "audio/x-wav"}:
            try:
                with wave.open(io.BytesIO(audio), "rb") as recording:
                    duration = recording.getnframes() / recording.getframerate()
            except (wave.Error, EOFError) as exc:
                raise ProviderError(
                    code="SPEECH_AUDIO_INVALID", message="Invalid WAV recording", status_code=422
                ) from exc
            if duration > 301:
                raise ProviderError(
                    code="SPEECH_AUDIO_INVALID",
                    message="Recording is longer than five minutes",
                    status_code=422,
                )

        try:
            response = await self._client.post(
                self._url(),
                headers={"Authorization": f"Bearer {self._api_key()}"},
                data={
                    "model": self._settings.whisper_model,
                    "response_format": "verbose_json",
                    "language": locale.split("-")[0].lower(),
                    "temperature": "0",
                    "timestamp_granularities[]": ["word", "segment"],
                },
                files={
                    "file": (
                        "recording.wav" if "wav" in content_type else "recording.ogg",
                        audio,
                        content_type,
                    )
                },
            )
        except httpx.TimeoutException as exc:
            raise ProviderError(
                "SPEECH_TIMEOUT", "Speech provider timed out", 504, retryable=True
            ) from exc
        except httpx.HTTPError as exc:
            raise ProviderError(
                "SPEECH_UNAVAILABLE", "Speech provider is unreachable", 502, retryable=True
            ) from exc
        if response.status_code >= 400:
            raise ProviderError(
                code="SPEECH_PROVIDER_ERROR",
                message=f"Speech provider answered {response.status_code}",
                status_code=502,
                retryable=is_retryable_http_status(response.status_code),
            )
        try:
            body = response.json()
        except ValueError as exc:
            raise ProviderError(
                "SPEECH_PROVIDER_ERROR", "Speech provider returned invalid JSON", 502
            ) from exc

        text = (body.get("text") or "").strip()
        if not text:
            raise ProviderError(
                "SPEECH_NO_SPEECH", "No speech was recognised in the recording", 422
            )
        words = body.get("words") or []
        segments = body.get("segments") or []
        fluency, rate = fluency_score(words)
        pronunciation, confidence = pronunciation_estimate(segments)
        return SpeechAssessmentResponse(
            provider="whisper",
            recognized_text=text,
            accuracy=pronunciation,
            fluency=fluency,
            completeness=None,
            prosody=None,
            pronunciation=pronunciation,
            words=[
                WordAssessment(
                    word=str(w["word"]).strip() or "?",
                    offset_ms=round(w["start"] * 1000),
                    duration_ms=round(max(w["end"] - w["start"], 0) * 1000),
                )
                for w in words
                if str(w.get("word", "")).strip() and isinstance(w.get("start"), (int, float))
            ],
            raw={
                "estimated": True,
                "method": "word timings (fluency), recognition confidence (pronunciation)",
                "duration_seconds": duration if duration is not None else body.get("duration"),
                "recognition_confidence": confidence,
                **rate,
            },
        )
