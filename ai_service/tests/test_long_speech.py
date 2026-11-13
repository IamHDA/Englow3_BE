import asyncio
import io
import wave

import httpx
import pytest
from pydantic import SecretStr

from app.config import Settings
from app.errors import ProviderError
from app.providers.speech import AzureSpeechProvider
from app.schemas import SpeechAssessmentResponse, WordAssessment


def recording(seconds: int, rate: int = 16000) -> bytes:
    output = io.BytesIO()
    with wave.open(output, "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(rate)
        audio.writeframes(bytes(2 * rate * seconds))
    return output.getvalue()


def provider(client: httpx.AsyncClient) -> AzureSpeechProvider:
    return AzureSpeechProvider(
        client,
        Settings(_env_file=None, speech_enabled=True, azure_speech_api_key=SecretStr("test")),
    )


def test_full_long_recording_is_assessed_in_order_with_global_offsets():
    async def run():
        durations = []
        async with httpx.AsyncClient() as client:
            adapter = provider(client)

            async def assess_segment(audio, content_type, locale, reference_text):
                with wave.open(io.BytesIO(audio), "rb") as part:
                    duration = part.getnframes() / part.getframerate()
                durations.append(duration)
                index = len(durations)
                assert duration <= 29
                assert reference_text is None
                return SpeechAssessmentResponse(
                    provider="test",
                    recognized_text=f"part {index}",
                    accuracy=index * 10,
                    fluency=index * 10,
                    pronunciation=index * 10,
                    words=[WordAssessment(word="hello", accuracy=80, offset_ms=1000)],
                )

            adapter._assess_segment = assess_segment
            result = await adapter.assess(recording(75), "audio/wav", "en-US", None)
        assert sum(durations) == 75
        assert result.recognized_text == "part 1 part 2 part 3"
        assert [word.offset_ms for word in result.words] == [1000, 25000, 49000]
        assert result.pronunciation == pytest.approx(20.4)
        assert result.completeness is None
        assert result.raw["segmented"] is True

    asyncio.run(run())


@pytest.mark.parametrize(
    "audio",
    [b"bad wav", recording(302), recording(1, rate=8000)],
    ids=["malformed-wav", "recording-too-long", "wrong-sample-rate"],
)
def test_invalid_free_response_audio_is_rejected_before_calling_provider(audio):
    async def run():
        async with httpx.AsyncClient() as client:
            with pytest.raises(ProviderError) as error:
                await provider(client).assess(audio, "audio/wav", "en-US", None)
            assert error.value.code == "SPEECH_AUDIO_INVALID"

    asyncio.run(run())


def test_a_failed_segment_does_not_publish_a_partial_assessment():
    async def run():
        async with httpx.AsyncClient() as client:
            adapter = provider(client)

            async def fail(*args):
                raise ProviderError(code="OUTAGE", message="Unavailable", status_code=502)

            adapter._assess_segment = fail
            with pytest.raises(ProviderError):
                await adapter.assess(recording(75), "audio/wav", "en-US", None)

    asyncio.run(run())
