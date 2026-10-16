import asyncio

from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.config import Settings
from app.errors import retry_after_seconds
from app.main import create_app


def test_retry_after_is_bounded_and_rejects_invalid_values():
    assert retry_after_seconds("60") == 60
    assert retry_after_seconds("9000") == 600
    assert retry_after_seconds("nan") is None
    assert retry_after_seconds("invalid") is None


class StalledProvider:
    async def generate(self, _body):
        await asyncio.sleep(10)


def test_total_generation_deadline_cancels_a_stalled_provider():
    config = Settings(
        _env_file=None, internal_api_key=SecretStr("test-key"), read_timeout_seconds=0.02
    )
    app = create_app(config)
    with TestClient(app) as client:
        app.state.llm_provider = StalledProvider()
        response = client.post(
            "/internal/v1/llm/generate",
            headers={"X-Internal-Api-Key": "test-key"},
            json={
                "model": "test",
                "system_prompt": "Rules",
                "user_prompt": "Hello",
                "max_output_tokens": 128,
                "temperature": 0.2,
            },
        )
        assert response.status_code == 504
        assert response.json()["retryable"] is True
        assert "ai;dur=" in response.headers["server-timing"]
