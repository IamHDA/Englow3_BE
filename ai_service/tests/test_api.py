from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.config import Settings
from app.main import create_app
from app.schemas import EmbeddingResponse, LlmGenerateResponse


class FakeLlmProvider:
    async def generate(self, request):
        return LlmGenerateResponse(
            content='{"answer":"ok"}',
            model=request.model,
            input_tokens=12,
            output_tokens=4,
        )


class ExplodingLlmProvider:
    async def generate(self, _request):
        raise RuntimeError("provider-secret-must-not-leak")


class FakeEmbeddingProvider:
    async def embed(self, _request):
        return EmbeddingResponse(
            embedding=[0.25] * 1024,
            model="test-embedding-model",
            input_tokens=3,
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
    with TestClient(create_app(settings(internal_api_key=SecretStr("")))) as client:
        response = client.get("/health/ready")
        assert response.status_code == 503
        assert response.json()["checks"]["internal_auth"] is False


def test_readiness_fails_when_enabled_provider_has_no_key():
    with TestClient(create_app(settings(llm_api_key=SecretStr("")))) as client:
        response = client.get("/health/ready")
        assert response.status_code == 503
        assert response.json()["checks"]["llm"] is False


def test_internal_endpoint_rejects_missing_key():
    app = create_app(settings())
    with TestClient(app) as client:
        response = client.post("/internal/v1/llm/generate", json=request_body())
        assert response.status_code == 401
        assert response.json()["code"] == "UNAUTHORIZED_SERVICE"


def test_internal_endpoint_rejects_an_invalid_key():
    with TestClient(create_app(settings())) as client:
        response = client.post(
            "/internal/v1/llm/generate",
            headers={"X-Internal-API-Key": "wrong-key"},
            json=request_body(),
        )
        assert response.status_code == 401
        assert response.json() == {
            "code": "UNAUTHORIZED_SERVICE",
            "message": "Invalid internal service credential",
            "retryable": False,
        }


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
        assert response.json() == {
            "code": "REQUEST_VALIDATION_FAILED",
            "message": "Request validation failed",
            "retryable": False,
        }


def test_embedding_endpoint_returns_a_fixed_dimension_vector():
    app = create_app(settings())
    with TestClient(app) as client:
        app.state.embedding_provider = FakeEmbeddingProvider()
        response = client.post(
            "/internal/v1/embeddings",
            headers={"X-Internal-API-Key": "test-internal-key"},
            json={"text": "present perfect"},
        )
        assert response.status_code == 200
        assert response.json()["model"] == "test-embedding-model"
        assert len(response.json()["embedding"]) == 1024


def test_embedding_endpoint_rejects_unknown_fields():
    with TestClient(create_app(settings())) as client:
        response = client.post(
            "/internal/v1/embeddings",
            headers={"X-Internal-API-Key": "test-internal-key"},
            json={"text": "present perfect", "api_key": "must-not-pass"},
        )
        assert response.status_code == 422
        assert response.json()["code"] == "REQUEST_VALIDATION_FAILED"


def test_invalid_speech_locale_has_a_stable_validation_error():
    with TestClient(create_app(settings())) as client:
        response = client.post(
            "/internal/v1/speech/assess",
            headers={"X-Internal-API-Key": "test-internal-key"},
            files={"audio": ("audio.wav", b"RIFF", "audio/wav")},
            data={"locale": "../../etc/passwd"},
        )
        assert response.status_code == 422
        assert response.json()["code"] == "REQUEST_VALIDATION_FAILED"


def test_speech_endpoint_rejects_unsupported_media_before_provider_call():
    with TestClient(create_app(settings())) as client:
        response = client.post(
            "/internal/v1/speech/assess",
            headers={"X-Internal-API-Key": "test-internal-key"},
            files={"audio": ("audio.mp3", b"fake", "audio/mpeg")},
        )
        assert response.status_code == 415
        assert response.json()["code"] == "UNSUPPORTED_AUDIO_TYPE"


def test_speech_endpoint_rejects_empty_and_oversized_audio():
    with TestClient(create_app(settings(max_audio_bytes=3))) as client:
        empty = client.post(
            "/internal/v1/speech/assess",
            headers={"X-Internal-API-Key": "test-internal-key"},
            files={"audio": ("audio.wav", b"", "audio/wav")},
        )
        oversized = client.post(
            "/internal/v1/speech/assess",
            headers={"X-Internal-API-Key": "test-internal-key"},
            files={"audio": ("audio.wav", b"RIFF", "audio/wav")},
        )

        assert empty.status_code == 422
        assert empty.json()["code"] == "EMPTY_AUDIO"
        assert oversized.status_code == 413
        assert oversized.json()["code"] == "AUDIO_TOO_LARGE"


def test_unexpected_errors_are_sanitized():
    app = create_app(settings())
    with TestClient(app, raise_server_exceptions=False) as client:
        app.state.llm_provider = ExplodingLlmProvider()
        response = client.post(
            "/internal/v1/llm/generate",
            headers={"X-Internal-API-Key": "test-internal-key"},
            json=request_body(),
        )

        assert response.status_code == 500
        assert response.json()["code"] == "AI_SERVICE_INTERNAL_ERROR"
        assert response.json()["retryable"] is True
        assert "provider-secret" not in response.text
