# Englow AI workspace

This directory is the boundary for AI-specific code and tooling. The Spring Boot
backend remains at the repository root and must not import Python modules from
this directory.

## Layout

- `service/`: stateless FastAPI runtime for LLM, embedding and speech provider
  calls. It is built and deployed as a separate container.
- `data_pipeline/`: offline content generation, validation, media and QA tools.
  It is never started as part of the backend runtime.

## Verification

```bash
python -m pip install -r ai/service/requirements-dev.txt
(cd ai/service && python -m ruff check app tests && python -m pytest)

python -m pip install -r ai/data_pipeline/requirements.txt
(cd ai/data_pipeline && python -m pytest -q)
```

From the repository root, `docker compose up ai-service` builds the runtime
service from `ai/service/Dockerfile`. GitHub Actions validates both AI projects
independently from the Java job.
