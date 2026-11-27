"""Speech from Kokoro-82M on this machine (engine "kokoro" in speech.py): free, no key, no network
after the first run, Apache-2.0 licensed so the audio can be used in the product.

    pip install kokoro soundfile   (and torch; the CUDA build makes it many times faster)

Kokoro speaks American (voices a*) and British (voices b*) English. Australian and New Zealand
speakers are given British voices and Canadian ones American voices. It takes no description of how
to speak, so the line's instructions are ignored; what it is given instead is the text written the way
an examiner reads it out (letters of a spelt name one by one, "double o", times and reference codes),
because the model would otherwise read "B-R-O-O-K" or "9.30" as if they were words or decimals.
Prices, ordinals, percentages, titles and "a.m." it already reads correctly. Each letter is written
as a word it says as the letter's name ("eh" for A, which it would otherwise read as "uh").
The transcript shown to the candidate keeps its own spelling.

    KOKORO_DEVICE   optional, "cuda" or "cpu" (default: cuda when available)
"""
from __future__ import annotations

import hashlib
import json
import os
import re
from pathlib import Path

from speech import BYTES_PER_SECOND, CACHE, RATE, Line, VoiceError

REPO = "hexgrad/Kokoro-82M"
MODEL_VERSION = "kokoro-82m-v1.0"

# Best first, by the quality grades published with the model. The narrator's voice is kept out of
# the speakers' pools so the rubric never sounds like one of the people in the recording.
NARRATOR = "bm_george"
POOLS = {
    ("female", "US"): ["af_heart", "af_bella", "af_nicole", "af_aoede", "af_kore", "af_sarah"],
    ("male", "US"): ["am_michael", "am_fenrir", "am_puck", "am_eric"],
    ("female", "UK"): ["bf_emma", "bf_isabella", "bf_alice", "bf_lily"],
    ("male", "UK"): ["bm_fable", "bm_lewis", "bm_daniel"],
}
ACCENT = {"UK": "UK", "AU": "UK", "NZ": "UK", "US": "US", "CA": "US"}

LETTERS = {
    "A": "eh", "B": "bee", "C": "see", "D": "dee", "E": "ee", "F": "eff", "G": "gee", "H": "aitch",
    "I": "eye", "J": "jay", "K": "kay", "L": "el", "M": "em", "N": "en", "O": "oh", "P": "pee",
    "Q": "cue", "R": "ar", "S": "ess", "T": "tee", "U": "you", "V": "vee", "W": "double you",
    "X": "ex", "Y": "why", "Z": "zed",
}
DIGITS = ["oh", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]
UNITS = ["", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
         "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"]
TENS = ["", "", "twenty", "thirty", "forty", "fifty"]

SPELT = re.compile(r"\b[A-Za-z](?:-[A-Za-z]){2,}\b")  # B-R-O-O-K
CODE = re.compile(r"\b(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z]{1,3}\d[A-Z0-9]{1,8}\b")  # LP4872, CB2 1TN
PHONE = re.compile(r"(?<![\w£$.,])(?:0\d+|\d{5,})(?:[ -]\d+)*\b")  # 07700 900451: digit by digit
TIME = re.compile(r"\b([01]?\d|2[0-3])[.:]([0-5]\d)\b")  # 9.30; misaki reads "a.m." itself
UNIT = {"km": "kilometres", "kg": "kilos", "cm": "centimetres", "mm": "millimetres"}
UNIT_AFTER_NUMBER = re.compile(r"(?<=\d) ?(km|kg|cm|mm)\b")


def two_digits(n: int) -> str:
    if n < 20:
        return UNITS[n]
    return TENS[n // 10] + (f"-{UNITS[n % 10]}" if n % 10 else "")


def letters(chars: str) -> str:
    """Characters read one by one, a repeated one as "double", as names and codes are read out."""
    words, i = [], 0
    while i < len(chars):
        char = chars[i].upper()
        said = LETTERS.get(char) or (DIGITS[int(char)] if char.isdigit() else char)
        if i + 1 < len(chars) and chars[i + 1].upper() == char:
            words.append(f"double {said}")
            i += 2
        else:
            words.append(said)
            i += 1
    return ", ".join(words)


def clock(hours: str, minutes: str) -> str:
    h, m = int(hours), int(minutes)
    if m == 0:
        return f"{UNITS[h] if h < 20 else two_digits(h)} o'clock"
    return f"{two_digits(h)} {'oh ' + UNITS[m] if m < 10 else two_digits(m)}"


def spoken(text: str) -> str:
    """The line as it should be pronounced."""
    text = SPELT.sub(lambda m: letters(m.group(0).replace("-", "")) + ",", text)
    text = CODE.sub(lambda m: letters(m.group(0)), text)
    text = PHONE.sub(lambda m: ", ".join(" ".join(DIGITS[int(d)] for d in group)
                                         for group in re.split(r"[ -]", m.group(0))), text)
    text = TIME.sub(lambda m: clock(m.group(1), m.group(2)), text)
    text = UNIT_AFTER_NUMBER.sub(lambda m: " " + UNIT[m.group(1)], text)
    return re.sub(r",\s*([.,;:!?])", r"\1", text)


class KokoroSpeaker:
    """Kokoro, loaded on the first line it has to speak, with a cache of every line already spoken."""

    narrator = NARRATOR
    varied = 2  # speakers rotate between the two best voices of their pool; the rest are fallbacks

    def __init__(self, cache: Path = CACHE, device: str | None = None) -> None:
        self.cache = cache
        self.device = device or os.environ.get("KOKORO_DEVICE")
        self.requests = 0
        self.cached = 0
        self._pipelines: dict[str, object] = {}

    def pool(self, gender: str, accent: str) -> list[str]:
        return POOLS[(gender if gender in ("female", "male") else "female", ACCENT.get(accent, "UK"))]

    def _path(self, line: Line, text: str) -> Path:
        digest = hashlib.sha256(json.dumps([MODEL_VERSION, line.voice, line.speed, text]).encode()).hexdigest()
        return self.cache / "kokoro" / digest[:2] / f"{digest}.pcm"

    def _pipeline(self, lang: str):
        if lang not in self._pipelines:
            import torch
            from kokoro import KModel, KPipeline

            device = self.device or ("cuda" if torch.cuda.is_available() else "cpu")
            shared = next(iter(self._pipelines.values()), None)
            model = shared.model if shared is not None else KModel(repo_id=REPO).to(device).eval()
            self._pipelines[lang] = KPipeline(lang_code=lang, repo_id=REPO, model=model)
        return self._pipelines[lang]

    def pcm(self, line: Line) -> bytes:
        text = spoken(line.text)
        path = self._path(line, text)
        if path.is_file() and path.stat().st_size > 0:
            self.cached += 1
            return path.read_bytes()
        import torch

        pipeline = self._pipeline(line.voice[0])
        pieces = []
        for result in pipeline(text, voice=line.voice, speed=line.speed, split_pattern=r"\n+"):
            if result.audio is not None:
                pieces.append(result.audio)
                pieces.append(torch.zeros(int(RATE * 0.15)))  # a breath between the chunks of a long line
        if not pieces:
            raise VoiceError(f"no audio for: {line.text[:60]}")
        audio = (torch.cat(pieces[:-1]).clamp(-1, 1) * 32767).to(torch.int16).cpu().numpy().tobytes()
        if len(audio) < BYTES_PER_SECOND * min(1.0, len(text) / 40):
            raise VoiceError(f"too little audio for: {line.text[:60]}")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(audio)
        self.requests += 1
        return audio
