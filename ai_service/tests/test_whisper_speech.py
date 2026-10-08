import asyncio
import io
import wave

import httpx
import pytest
from pydantic import SecretStr

from app.config import Settings
from app.errors import ProviderError
from app.providers.whisper import WhisperSpeechProvider, fluency_score, pronunciation_estimate


def recording(seconds: int = 6) -> bytes:
    output = io.BytesIO()
    with wave.open(output, "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(16000)
        audio.writeframes(bytes(2 * 16000 * seconds))
    return output.getvalue()


def settings(**overrides) -> Settings:
    values = {
        "speech_enabled": True,
        "speech_provider": "whisper",
        "llm_api_key": SecretStr("groq-key"),
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


def words(rate_per_second: float, count: int, gap: float = 0.05):
    out, t = [], 0.0
    for i in range(count):
        length = 1 / rate_per_second - gap
        out.append({"word": f"w{i}", "start": t, "end": t + length})
        t += length + gap
    return out


def test_steady_speech_scores_fluent_and_halting_speech_does_not():
    steady, _ = fluency_score(words(2.4, 30))  # about 144 words a minute
    halting = words(2.4, 30)
    for i in range(5, 30, 5):  # a 2.5 second pause every five words
        for w in halting[i:]:
            w["start"] += 2.5
            w["end"] += 2.5
    slow, metrics = fluency_score(halting)
    assert steady > 90
    assert slow < steady - 25
    assert metrics["long_pauses"] == 5


def test_confident_recognition_reads_as_clear_pronunciation():
    clear, _ = pronunciation_estimate(
        [{"avg_logprob": -0.05, "no_speech_prob": 0.01, "start": 0, "end": 5}]
    )
    unclear, _ = pronunciation_estimate(
        [{"avg_logprob": -0.6, "no_speech_prob": 0.05, "start": 0, "end": 5}]
    )
    assert clear > 85
    assert unclear < 30


def test_transcribes_with_word_timings_and_returns_estimated_evidence():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["auth"] = request.headers["authorization"]
        seen["body"] = request.content
        return httpx.Response(
            200,
            json={
                "text": " I come from Hue, a peaceful city.",
                "duration": 6.0,
                "segments": [
                    {"start": 0.0, "end": 5.5, "avg_logprob": -0.12, "no_speech_prob": 0.02}
                ],
                "words": words(2.4, 12),
            },
        )

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return await WhisperSpeechProvider(client, settings()).assess(
                recording(), "audio/wav", "en-US", None
            )

    result = asyncio.run(run())
    assert seen["url"] == "https://api.groq.com/openai/v1/audio/transcriptions"
    assert seen["auth"] == "Bearer groq-key"
    assert b"whisper-large-v3-turbo" in seen["body"] and b"verbose_json" in seen["body"]
    assert result.provider == "whisper"
    assert result.recognized_text == "I come from Hue, a peaceful city."
    assert result.pronunciation is not None and result.fluency is not None
    assert result.raw["estimated"] is True
    assert result.words[0].offset_ms == 0


def test_disabled_without_a_key():
    async def run():
        async with httpx.AsyncClient() as client:
            await WhisperSpeechProvider(client, settings(llm_api_key=SecretStr(""))).assess(
                recording(), "audio/wav", "en-US", None
            )

    with pytest.raises(ProviderError) as failure:
        asyncio.run(run())
    assert failure.value.code == "SPEECH_DISABLED"


@pytest.mark.parametrize("status,retryable", [(429, True), (400, False)])
def test_provider_errors_keep_their_retry_meaning(status, retryable):
    async def run():
        transport = httpx.MockTransport(lambda request: httpx.Response(status, json={}))
        async with httpx.AsyncClient(transport=transport) as client:
            await WhisperSpeechProvider(client, settings()).assess(
                recording(), "audio/wav", "en-US", None
            )

    with pytest.raises(ProviderError) as failure:
        asyncio.run(run())
    assert failure.value.retryable is retryable


def test_silence_is_not_assessed():
    async def run():
        transport = httpx.MockTransport(
            lambda request: httpx.Response(200, json={"text": "  ", "words": []})
        )
        async with httpx.AsyncClient(transport=transport) as client:
            await WhisperSpeechProvider(client, settings()).assess(
                recording(), "audio/wav", "en-US", None
            )

    with pytest.raises(ProviderError) as failure:
        asyncio.run(run())
    assert failure.value.code == "SPEECH_NO_SPEECH"
