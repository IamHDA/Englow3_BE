# Englow AI service

Stateless FastAPI service responsible only for external AI inference. Spring Boot
remains the public API, authorization boundary, system of record, job queue and
owner of deterministic business rules.

## Internal API

- `POST /internal/v1/llm/generate`: OpenAI-compatible text generation.
- `POST /internal/v1/embeddings`: fixed-dimension embeddings for semantic retrieval.
- `POST /internal/v1/speech/assess`: Azure pronunciation assessment.
- `GET /health/live`: process liveness.
- `GET /health/ready`: configuration readiness.

Every `/internal/*` request requires `X-Internal-API-Key`. Do not expose port 8000
to the public internet in production; use a private network and a secret manager.

## Local development

```powershell
Copy-Item .env.example .env
python -m venv .venv
.\.venv\Scripts\pip.exe install -r requirements-dev.txt
.\.venv\Scripts\uvicorn.exe app.main:app --reload --port 8001
```

From this directory run `pytest`. From the repository root, Docker Compose starts
the service on `http://localhost:8001`.
