from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.config import Settings
from app.main import create_app
from app.schemas import LlmGenerateResponse


class FakeLlmProvider:
    async def generate(self, request):
        return LlmGenerateResponse(
            content='{"answer":"ok"}',
            model=request.model,
            input_tokens=12,
            output_tokens=4,
        )


def settings(**overrides) -> Settings:
    values = {
        "internal_api_key": SecretStr("test-internal-key"),
        "llm_enabled": True,
        "llm_api_key": SecretStr("provider-key"),
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


def request_body() -> dict:
    return {
        "model": "test-model",
        "system_prompt": "System rules",
        "user_prompt": "Hello",
        "temperature": 0.2,
        "max_output_tokens": 128,
        "json_output": True,
    }


def test_liveness_does_not_require_authentication():
    with TestClient(create_app(settings())) as client:
        assert client.get("/health/live").json() == {"status": "up"}


def test_readiness_fails_without_internal_key():
    with TestClient(
        create_app(settings(internal_api_key=SecretStr("")))
    ) as client:
        response = client.get("/health/ready")
        assert response.status_code == 503
        assert response.json()["checks"]["internal_auth"] is False


def test_internal_endpoint_rejects_missing_key():
    app = create_app(settings())
    with TestClient(app) as client:
        response = client.post("/internal/v1/llm/generate", json=request_body())
        assert response.status_code == 401
        assert response.json()["code"] == "UNAUTHORIZED_SERVICE"


def test_llm_endpoint_returns_stable_contract():
    app = create_app(settings())
    with TestClient(app) as client:
        app.state.llm_provider = FakeLlmProvider()
        response = client.post(
            "/internal/v1/llm/generate",
            headers={"X-Internal-API-Key": "test-internal-key"},
            json=request_body(),
        )
        assert response.status_code == 200
        assert response.json() == {
            "content": '{"answer":"ok"}',
            "model": "test-model",
            "input_tokens": 12,
            "output_tokens": 4,
        }


def test_llm_endpoint_forbids_unknown_fields():
    body = request_body()
    body["provider_api_key"] = "must-not-be-accepted"
    with TestClient(create_app(settings())) as client:
        response = client.post(
            "/internal/v1/llm/generate",
            headers={"X-Internal-API-Key": "test-internal-key"},
            json=body,
        )
        assert response.status_code == 422


def test_speech_endpoint_rejects_unsupported_media_before_provider_call():
    with TestClient(create_app(settings())) as client:
        response = client.post(
            "/internal/v1/speech/assess",
            headers={"X-Internal-API-Key": "test-internal-key"},
            files={"audio": ("audio.mp3", b"fake", "audio/mpeg")},
        )
        assert response.status_code == 415
        assert response.json()["code"] == "UNSUPPORTED_AUDIO_TYPE"
