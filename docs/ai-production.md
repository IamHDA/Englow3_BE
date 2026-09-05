# AI service deployment

## Current boundary

The `dev` branch contains two independent AI projects under [`ai/`](../ai/):

- [`ai/service/`](../ai/service/) is a stateless FastAPI provider adapter.
- [`ai/data_pipeline/`](../ai/data_pipeline/) is an offline authoring and QA toolchain.

The Spring Boot backend does not contain an AI package, does not import either
Python project and does not require an AI service URL to start. The FastAPI
service currently exposes internal provider contracts only; adding a public AI
feature requires an explicit backend or gateway integration in a future change.

## FastAPI contract

- `POST /internal/v1/llm/generate`
- `POST /internal/v1/embeddings`
- `POST /internal/v1/speech/assess`
- `GET /health/live`
- `GET /health/ready`

Every `/internal/*` request requires `X-Internal-API-Key`. Keep port 8000 and
all internal routes off the public internet.

## Configuration

Copy [`ai/service/.env.example`](../ai/service/.env.example) to
`ai/service/.env` for local development. Provider credentials belong to the AI
service and must not be added to Spring's `application.yml`.

Required for a ready production instance:

- `AI_SERVICE_ENVIRONMENT=production`
- a long random `AI_SERVICE_INTERNAL_API_KEY`
- the enable flag, base URL and secret for each configured provider

## Verification

```bash
python -m pip install -r ai/service/requirements-dev.txt
(cd ai/service && python -m ruff check app tests)
(cd ai/service && python -m ruff format --check app tests)
(cd ai/service && python -m pytest)
```

The data pipeline is verified separately:

```bash
python -m pip install -r ai/data_pipeline/requirements.txt
(cd ai/data_pipeline && python -m pytest -q)
```

The repository CI keeps Java, FastAPI and data-pipeline checks in separate jobs.

## Deployment

The CD workflow builds two independent images:

- `ghcr.io/<owner>/<repository>:<branch>` from the root Spring Dockerfile.
- `ghcr.io/<owner>/<repository>-ai-service:<branch>` from
  `ai/service/Dockerfile`.

Configure `RENDER_BACKEND_DEPLOY_HOOK_URL` and `RENDER_AI_DEPLOY_HOOK_URL`
independently. On a VPS, only the backend publishes port 8080; the AI container
stays on the private `englow3-private` network.

For local use, run `docker compose up ai-service`. The Compose build context is
`ai/service`, while the root backend Docker context excludes the complete `ai/`
directory.
