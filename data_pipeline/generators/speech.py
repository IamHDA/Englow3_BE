"""Recordings built from lines of speech and real pauses, whatever engine speaks the lines.

A script is a list of Line (who says what, and how) and Pause. An engine turns one Line into raw
24 kHz 16-bit mono PCM; render() joins the lines with exact silences, then ffmpeg (on PATH)
normalises the loudness and encodes the MP3.

Engines (choose with engine(name)):
    kokoro  Kokoro-82M, run on this machine; free, no key (see kokoro_voice.py)
    openai  OpenAI's gpt-4o-mini-tts; needs OPENAI_API_KEY (see openai_voice.py)

Every engine caches each line by its exact request, so re-recording a part after a fix only
speaks the changed lines again.
"""
from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "output" / "audio" / "tts-cache"
ENV_FILE = ROOT.parent / ".env"
RATE = 24_000  # samples per second of the PCM every engine returns
BYTES_PER_SECOND = RATE * 2
ENGINES = ("kokoro", "openai")

ACCENTS = {
    "UK": "a natural, educated Southern British English accent",
    "US": "a natural General American accent",
    "AU": "a natural, mild Australian accent",
    "CA": "a natural Canadian English accent",
    "NZ": "a natural New Zealand English accent",
}


class VoiceError(RuntimeError):
    pass


@dataclass(frozen=True)
class Line:
    """One line of speech: the words, the voice, how to say them, and how fast."""

    text: str
    voice: str
    instructions: str
    speed: float = 1.0


@dataclass(frozen=True)
class Pause:
    seconds: float


class Engine(Protocol):
    narrator: str
    requests: int
    cached: int

    def pool(self, gender: str, accent: str) -> list[str]:
        """The voices that can speak with this gender and accent, best first."""

    def pcm(self, line: Line) -> bytes:
        """The line as 24 kHz 16-bit mono PCM."""


def engine(name: str = "kokoro") -> Engine:
    if name == "kokoro":
        from kokoro_voice import KokoroSpeaker

        return KokoroSpeaker()
    if name == "openai":
        from openai_voice import Speaker

        return Speaker()
    raise SystemExit(f"unknown speech engine {name!r}; choose one of {', '.join(ENGINES)}")


def instructions(accent: str, role: str, delivery: str) -> str:
    """How a speaker sounds: accent, who they are, and the delivery the situation calls for.

    Engines that take a description (OpenAI) use it; the others ignore it.
    """
    return (
        f"Speak with {ACCENTS.get(accent, ACCENTS['UK'])}. {role.strip()} {delivery.strip()} "
        "Sound like a real person speaking spontaneously, not someone reading a script: natural rhythm and "
        "intonation, small natural hesitations where the text has them, and a normal conversational pace. "
        "Read numbers, prices, dates and spellings exactly as written, letter by letter where a word is spelt out."
    )


def silence(seconds: float) -> bytes:
    return b"\x00\x00" * int(RATE * seconds)


def trim(pcm: bytes, threshold: int = 300, keep: float = 0.05) -> bytes:
    """The line without the silence the engine leaves at either end, so the pauses are ours to set."""
    samples = memoryview(pcm).cast("h")
    loud = [i for i in range(0, len(samples), 120) if abs(samples[i]) > threshold]
    if not loud:
        return pcm
    pad = int(RATE * keep)
    start, end = max(0, loud[0] - pad), min(len(samples), loud[-1] + pad)
    return pcm[start * 2 : end * 2]


def ffmpeg() -> str:
    found = shutil.which("ffmpeg")
    if not found:
        raise SystemExit("ffmpeg is needed on PATH to encode the MP3.")
    return found


def render(script: list[Line | Pause], speaker: Engine, out: Path, bitrate: str = "64k") -> float:
    """Speak every line, join them with the pauses, and write a loudness-normalised mono MP3.

    Returns the length in seconds.
    """
    audio = bytearray()
    for item in script:
        audio += silence(item.seconds) if isinstance(item, Pause) else trim(speaker.pcm(item))
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [ffmpeg(), "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", "pipe:0",
         "-af", "loudnorm=I=-18:TP=-1.5:LRA=11", "-ar", str(RATE), "-ac", "1", "-b:a", bitrate, str(out)],
        input=bytes(audio), check=True)
    return len(audio) / BYTES_PER_SECOND
