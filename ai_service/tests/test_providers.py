import asyncio
import base64
import json

import httpx
import pytest
from pydantic import SecretStr

from app.config import Settings
from app.errors import ProviderError
from app.providers.llm import OpenAiCompatibleProvider
from app.providers.speech import AzureSpeechProvider
from app.schemas import LlmGenerateRequest


def configured(**overrides) -> Settings:
    values = {
        "llm_enabled": True,
        "llm_base_url": "https://llm.example/v1",
        "llm_api_key": SecretStr("llm-secret"),
        "speech_enabled": True,
        "azure_speech_base_url": "https://speech.example",
        "azure_speech_api_key": SecretStr("speech-secret"),
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


def llm_request() -> LlmGenerateRequest:
    return LlmGenerateRequest(
        model="test-model",
        system_prompt="system",
        user_prompt="user",
        temperature=0.2,
        max_output_tokens=100,
        json_output=True,
    )


def test_llm_provider_normalizes_openai_compatible_response():
    async def run():
        def handler(request: httpx.Request) -> httpx.Response:
            assert request.url == "https://llm.example/v1/chat/completions"
            assert request.headers["Authorization"] == "Bearer llm-secret"
            payload = json.loads(request.content)
            assert payload["response_format"] == {"type": "json_object"}
            assert payload["max_tokens"] == 100
            return httpx.Response(
                200,
                json={
                    "model": "resolved-model",
                    "choices": [{"message": {"content": "answer"}}],
                    "usage": {"prompt_tokens": 12, "completion_tokens": 4},
                },
            )

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return await OpenAiCompatibleProvider(client, configured()).generate(llm_request())

    result = asyncio.run(run())
    assert result.content == "answer"
    assert result.model == "resolved-model"
    assert result.input_tokens == 12
    assert result.output_tokens == 4


def test_llm_rate_limit_is_reported_as_retryable_without_leaking_body():
    async def run():
        transport = httpx.MockTransport(
            lambda _: httpx.Response(429, json={"secret_provider_detail": "quota-account"})
        )
        async with httpx.AsyncClient(transport=transport) as client:
            await OpenAiCompatibleProvider(client, configured()).generate(llm_request())

    with pytest.raises(ProviderError) as captured:
        asyncio.run(run())
    assert captured.value.code == "AI_PROVIDER_HTTP_429"
    assert captured.value.retryable is True
    assert "quota-account" not in captured.value.message


@pytest.mark.parametrize(
    ("status", "retryable"),
    [(400, False), (408, True), (425, True), (500, True)],
)
def test_llm_provider_classifies_http_failures(status, retryable):
    async def run():
        transport = httpx.MockTransport(lambda _: httpx.Response(status))
        async with httpx.AsyncClient(transport=transport) as client:
            await OpenAiCompatibleProvider(client, configured()).generate(llm_request())

    with pytest.raises(ProviderError) as captured:
        asyncio.run(run())
    assert captured.value.code == f"AI_PROVIDER_HTTP_{status}"
    assert captured.value.retryable is retryable


def test_llm_transport_failure_is_retryable():
    async def run():
        def handler(request: httpx.Request):
            raise httpx.ReadTimeout("provider timeout", request=request)

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            await OpenAiCompatibleProvider(client, configured()).generate(llm_request())

    with pytest.raises(ProviderError) as captured:
        asyncio.run(run())
    assert captured.value.code == "AI_PROVIDER_UNAVAILABLE"
    assert captured.value.retryable is True
    assert "provider timeout" not in captured.value.message


@pytest.mark.parametrize(
    "payload",
    [
        [],
        {"choices": []},
        {"choices": [{"message": "not-an-object"}]},
        {
            "choices": [{"message": {"content": "answer"}}],
            "usage": {"prompt_tokens": -1},
        },
    ],
)
def test_llm_malformed_responses_use_a_stable_error(payload):
    async def run():
        transport = httpx.MockTransport(lambda _: httpx.Response(200, json=payload))
        async with httpx.AsyncClient(transport=transport) as client:
            await OpenAiCompatibleProvider(client, configured()).generate(llm_request())

    with pytest.raises(ProviderError) as captured:
        asyncio.run(run())
    assert captured.value.code == "AI_PROVIDER_INVALID_RESPONSE"
    assert captured.value.retryable is True


def test_speech_provider_builds_assessment_and_normalizes_scores():
    async def run():
        def handler(request: httpx.Request) -> httpx.Response:
            assert request.url.params["language"] == "en-US"
            assert request.headers["Ocp-Apim-Subscription-Key"] == "speech-secret"
            assessment = json.loads(base64.b64decode(request.headers["Pronunciation-Assessment"]))
            assert assessment["ReferenceText"] == "Hello"
            assert request.content == b"RIFF-audio"
            return httpx.Response(
                200,
                headers={"X-RequestId": "speech-request"},
                json={
                    "RecognitionStatus": "Success",
                    "NBest": [
                        {
                            "Display": "Hello",
                            "PronunciationAssessment": {
                                "AccuracyScore": 90,
                                "FluencyScore": 80,
                                "CompletenessScore": 100,
                                "PronScore": 88,
                            },
                            "Words": [
                                {
                                    "Word": "Hello",
                                    "Offset": 10_000,
                                    "Duration": 20_000,
                                    "PronunciationAssessment": {
                                        "AccuracyScore": 90,
                                        "ErrorType": "None",
                                    },
                                }
                            ],
                        }
                    ],
                },
            )

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return await AzureSpeechProvider(client, configured()).assess(
                b"RIFF-audio", "audio/wav", "en-US", "Hello"
            )

    result = asyncio.run(run())
    assert result.recognized_text == "Hello"
    assert result.pronunciation == 88
    assert result.request_id == "speech-request"
    assert result.words[0].offset_ms == 1


def test_speech_recognition_failure_is_not_retryable():
    async def run():
        transport = httpx.MockTransport(
            lambda _: httpx.Response(200, json={"RecognitionStatus": "NoMatch"})
        )
        async with httpx.AsyncClient(transport=transport) as client:
            await AzureSpeechProvider(client, configured()).assess(
                b"RIFF-audio", "audio/wav", "en-US", None
            )

    with pytest.raises(ProviderError) as captured:
        asyncio.run(run())
    assert captured.value.code == "SPEECH_RECOGNITION_FAILED"
    assert captured.value.retryable is False


@pytest.mark.parametrize(
    "payload",
    [
        [],
        {"RecognitionStatus": "Success", "NBest": []},
        {"RecognitionStatus": "Success", "NBest": ["not-an-object"]},
        {
            "RecognitionStatus": "Success",
            "NBest": [{"Display": "Hello", "Words": ["not-an-object"]}],
        },
        {
            "RecognitionStatus": "Success",
            "NBest": [
                {
                    "Display": "Hello",
                    "PronunciationAssessment": {"AccuracyScore": 101},
                }
            ],
        },
    ],
)
def test_speech_malformed_responses_use_a_stable_error(payload):
    async def run():
        transport = httpx.MockTransport(lambda _: httpx.Response(200, json=payload))
        async with httpx.AsyncClient(transport=transport) as client:
            await AzureSpeechProvider(client, configured()).assess(
                b"RIFF-audio", "audio/wav", "en-US", None
            )

    with pytest.raises(ProviderError) as captured:
        asyncio.run(run())
    assert captured.value.code == "SPEECH_PROVIDER_INVALID_RESPONSE"
    assert captured.value.retryable is True


def test_invalid_runtime_limits_are_rejected_at_startup():
    with pytest.raises(ValueError):
        configured(connect_timeout_seconds=0)
    with pytest.raises(ValueError):
        configured(max_audio_bytes=0)
