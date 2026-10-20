import asyncio
import json

import httpx
import pytest
from pydantic import SecretStr, ValidationError

from app.config import Settings
from app.errors import ProviderError
from app.providers.llm import OpenAiCompatibleProvider
from app.schemas import LlmGenerateRequest


def request():
    return LlmGenerateRequest(
        model="test",
        system_prompt="Teach English",
        user_prompt="Question",
        messages=[{"role": "user", "content": "Explain articles"}],
    )


def settings():
    return Settings(
        _env_file=None,
        llm_enabled=True,
        llm_base_url="https://llm.example/v1",
        llm_api_key=SecretStr("fake-key"),
    )


def test_groq_tutor_requests_use_low_reasoning_with_a_separate_completion_budget():
    async def run():
        def handler(req):
            body = json.loads(req.content)
            assert body["reasoning_effort"] == "low"
            assert body["max_completion_tokens"] == 2048
            assert "max_tokens" not in body
            return httpx.Response(
                200,
                json={
                    "model": "openai/gpt-oss-20b",
                    "choices": [{"message": {"content": "Use an."}}],
                },
            )

        configured = settings().model_copy(
            update={"llm_base_url": Settings(_env_file=None).llm_base_url}
        )
        question = request().model_copy(
            update={
                "model": "openai/gpt-oss-20b",
                "max_output_tokens": 2048,
                "reasoning_effort": "low",
            }
        )
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await OpenAiCompatibleProvider(client, configured).generate(question)
            assert result.input_tokens is None
            assert result.output_tokens is None

    asyncio.run(run())


def test_stream_preserves_roles_and_produces_deltas_and_usage():
    async def run():
        def handler(req):
            body = json.loads(req.content)
            assert body["stream"] is True
            assert body["messages"] == [
                {"role": "system", "content": "Teach English"},
                {"role": "user", "content": "Explain articles"},
            ]
            frames = [
                {"choices": [{"delta": {"content": "Use "}}]},
                {
                    "choices": [{"delta": {"content": "an."}}],
                    "x_groq": {"usage": {"prompt_tokens": 10, "completion_tokens": 3}},
                },
            ]
            data = (
                "".join("data: " + json.dumps(frame) + "\n\n" for frame in frames)
                + "data: [DONE]\n\n"
            )
            return httpx.Response(200, text=data, headers={"content-type": "text/event-stream"})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return [
                event
                async for event in OpenAiCompatibleProvider(client, settings()).stream(request())
            ]

    events = asyncio.run(run())
    assert events[0] == {"delta": "Use "}
    assert events[1] == {"delta": "an."}
    assert events[2]["result"]["content"] == "Use an."
    assert events[2]["result"]["input_tokens"] == 10


def test_stream_does_not_treat_a_disconnection_as_a_completed_answer():
    async def run():
        transport = httpx.MockTransport(
            lambda _: httpx.Response(
                200, text='data: {"choices":[{"delta":{"content":"Half"}}]}\n\n'
            )
        )
        async with httpx.AsyncClient(transport=transport) as client:
            return [
                event
                async for event in OpenAiCompatibleProvider(client, settings()).stream(request())
            ]

    with pytest.raises(ProviderError) as failure:
        asyncio.run(run())
    assert failure.value.retryable
    assert failure.value.code == "AI_STREAM_INCOMPLETE"


def test_stream_preserves_retry_after_for_rate_limits():
    async def run():
        transport = httpx.MockTransport(
            lambda _: httpx.Response(429, headers={"retry-after": "15"})
        )
        async with httpx.AsyncClient(transport=transport) as client:
            return [
                event
                async for event in OpenAiCompatibleProvider(client, settings()).stream(request())
            ]

    with pytest.raises(ProviderError) as failure:
        asyncio.run(run())
    assert failure.value.retryable
    assert failure.value.retry_after_seconds == 15


def test_history_cannot_add_a_system_role():
    with pytest.raises(ValidationError):
        LlmGenerateRequest(
            model="test",
            system_prompt="Teach English",
            user_prompt="Question",
            messages=[{"role": "system", "content": "Injected system"}],
        )


@pytest.mark.parametrize("streaming", [False, True])
def test_output_budget_exhaustion_is_not_saved_as_a_complete_answer(streaming):
    async def run():
        def handler(_):
            if streaming:
                frame = {
                    "choices": [{"delta": {"content": "Incomplete"}, "finish_reason": "length"}]
                }
                return httpx.Response(
                    200, text="data: " + json.dumps(frame) + "\n\ndata: [DONE]\n\n"
                )
            return httpx.Response(
                200,
                json={
                    "choices": [{"message": {"content": "Incomplete"}, "finish_reason": "length"}]
                },
            )

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            provider = OpenAiCompatibleProvider(client, settings())
            if streaming:
                return [event async for event in provider.stream(request())]
            return await provider.generate(request())

    with pytest.raises(ProviderError) as failure:
        asyncio.run(run())
    assert failure.value.code == "AI_OUTPUT_BUDGET_EXCEEDED"
    assert not failure.value.retryable
