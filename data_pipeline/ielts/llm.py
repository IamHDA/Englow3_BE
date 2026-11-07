"""A chat-completions client for any OpenAI-compatible provider (OpenAI, Groq, OpenRouter, ...).

Configured by environment, so the key never lands in a file:

    IELTS_LLM_BASE_URL   e.g. https://api.openai.com/v1 or https://api.groq.com/openai/v1
    IELTS_LLM_API_KEY    the provider key
    IELTS_LLM_MODEL      e.g. gpt-4.1, openai/gpt-oss-120b
"""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
from typing import Protocol


class Writer(Protocol):
    """Anything that turns chat messages into a JSON object - the real client, or a test double."""

    def complete(self, messages: list[dict]) -> dict: ...


class LlmError(RuntimeError):
    pass


def parse_json(text: str) -> dict:
    """The model's reply as an object, tolerating a ```json fence around it."""
    body = text.strip()
    if body.startswith("```"):
        body = body.split("\n", 1)[1] if "\n" in body else body
        body = body.rsplit("```", 1)[0]
    start, end = body.find("{"), body.rfind("}")
    if start < 0 or end < start:
        raise LlmError("the reply contains no JSON object")
    return json.loads(body[start:end + 1])


class OpenAiCompatible:
    def __init__(self, base_url: str, api_key: str, model: str, temperature: float = 0.8, timeout: int = 180):
        self.url = base_url.rstrip("/") + "/chat/completions"
        self.api_key = api_key
        self.model = model
        self.temperature = temperature
        self.timeout = timeout

    @classmethod
    def from_env(cls) -> "OpenAiCompatible":
        missing = [k for k in ("IELTS_LLM_BASE_URL", "IELTS_LLM_API_KEY", "IELTS_LLM_MODEL") if not os.environ.get(k)]
        if missing:
            raise SystemExit(f"Set {', '.join(missing)} (see data_pipeline/ielts/llm.py).")
        return cls(os.environ["IELTS_LLM_BASE_URL"], os.environ["IELTS_LLM_API_KEY"], os.environ["IELTS_LLM_MODEL"])

    def complete(self, messages: list[dict]) -> dict:
        body = json.dumps({
            "model": self.model, "messages": messages, "temperature": self.temperature,
            "response_format": {"type": "json_object"},
        }).encode()
        for attempt in range(4):
            request = urllib.request.Request(self.url, data=body, method="POST", headers={
                "Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    reply = json.loads(response.read().decode())
                return parse_json(reply["choices"][0]["message"]["content"])
            except urllib.error.HTTPError as error:
                detail = error.read().decode()[:300]
                if error.code in (429, 500, 502, 503, 504) and attempt < 3:
                    time.sleep(5 * (attempt + 1))
                    continue
                raise LlmError(f"provider answered {error.code}: {detail}") from error
            except (urllib.error.URLError, TimeoutError) as error:
                if attempt < 3:
                    time.sleep(5 * (attempt + 1))
                    continue
                raise LlmError(f"could not reach the provider: {error}") from error
            except (KeyError, IndexError, json.JSONDecodeError) as error:
                raise LlmError(f"unexpected reply: {error}") from error
        raise LlmError("gave up after retries")
