"""Speech from OpenAI's text-to-speech model, one request per line (engine "openai" in speech.py).

The model is told who is speaking and how (accent, role, mood) through the line's instructions.

    OPENAI_API_KEY   the key (read from the environment, or from the backend's .env when unset)
    OPENAI_TTS_MODEL optional, default gpt-4o-mini-tts
"""
from __future__ import annotations

import hashlib
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

from speech import BYTES_PER_SECOND, CACHE, ENV_FILE, Line, VoiceError

URL = "https://api.openai.com/v1/audio/speech"
DEFAULT_MODEL = "gpt-4o-mini-tts"
MAX_INPUT = 4000  # characters per request

VOICES = {
    "female": ["coral", "sage", "shimmer", "nova", "marin"],
    "male": ["ash", "verse", "onyx", "echo", "cedar", "ballad"],
}


def api_key() -> str:
    key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not key and ENV_FILE.is_file():
        for raw in ENV_FILE.read_text(encoding="utf-8").splitlines():
            if raw.startswith("OPENAI_API_KEY="):
                key = raw.split("=", 1)[1].strip().strip('"').strip("'")
    if not key:
        raise SystemExit(f"Set OPENAI_API_KEY (environment or {ENV_FILE}).")
    return key


class Speaker:
    """The OpenAI speech endpoint, with a cache of every line already recorded."""

    narrator = "fable"

    def __init__(self, key: str | None = None, model: str | None = None, cache: Path = CACHE,
                 timeout: int = 120) -> None:
        self.key = key
        self.model = model or os.environ.get("OPENAI_TTS_MODEL", DEFAULT_MODEL)
        self.cache = cache
        self.timeout = timeout
        self.requests = 0
        self.cached = 0

    def pool(self, gender: str, accent: str) -> list[str]:
        """Any voice takes any accent: the instructions set it."""
        return VOICES.get(gender, VOICES["female"])

    def _path(self, line: Line) -> Path:
        digest = hashlib.sha256(
            json.dumps([self.model, line.voice, line.instructions, line.text]).encode()).hexdigest()
        return self.cache / digest[:2] / f"{digest}.pcm"

    def pcm(self, line: Line) -> bytes:
        if len(line.text) > MAX_INPUT:
            raise VoiceError(f"line longer than {MAX_INPUT} characters: {line.text[:60]}...")
        path = self._path(line)
        if path.is_file() and path.stat().st_size > 0:
            self.cached += 1
            return path.read_bytes()
        self.key = self.key or api_key()
        body = json.dumps({"model": self.model, "voice": line.voice, "input": line.text,
                           "instructions": line.instructions, "response_format": "pcm"}).encode()
        for attempt in range(5):
            request = urllib.request.Request(URL, data=body, method="POST", headers={
                "Authorization": f"Bearer {self.key}", "Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    audio = response.read()
                break
            except urllib.error.HTTPError as error:
                detail = error.read().decode(errors="replace")[:300]
                if error.code in (429, 500, 502, 503, 504) and attempt < 4:
                    time.sleep(5 * (attempt + 1))
                    continue
                raise VoiceError(f"speech API answered {error.code}: {detail}") from error
            except (urllib.error.URLError, TimeoutError) as error:
                if attempt < 4:
                    time.sleep(5 * (attempt + 1))
                    continue
                raise VoiceError(f"could not reach the speech API: {error}") from error
        # Roughly 10 characters a second at the slowest; much less audio than that is a failed reply.
        if len(audio) < BYTES_PER_SECOND * min(1.0, len(line.text) / 40):
            raise VoiceError(f"too little audio came back for: {line.text[:60]}")
        audio = audio[: len(audio) - len(audio) % 2]
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(audio)
        self.requests += 1
        return audio
