#!/usr/bin/env python3
"""Record the IELTS trial paper's Listening audio from its transcript.

Reads src/test/resources/ielts/academic-trial-paper-1.listening-transcript.md and writes one MP3 per part
to data_pipeline/output/audio/ielts/ (git-ignored), named academic-trial-paper-1-part-<n>.mp3. Each
speaker gets their own neural voice - British, Australian and American accents, as IELTS mixes them.
scripts/load_ielts_trial.py then uploads the four files and attaches them to the paper's parts.

    pip install edge-tts
    python data_pipeline/generators/synthesize_ielts_trial_audio.py            # record
    python data_pipeline/generators/synthesize_ielts_trial_audio.py --dry-run  # only show who says what

Needs internet access to Microsoft's free voice service (speech.platform.bing.com). No ffmpeg: the
segments come out as MP3 frames of one format, so joining the bytes gives one playable file.
"""
from __future__ import annotations

import argparse
import asyncio
import re
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRANSCRIPT = ROOT.parent / "src" / "test" / "resources" / "ielts" / "academic-trial-paper-1.listening-transcript.md"
OUT = ROOT / "output" / "audio" / "ielts"
STEM = "academic-trial-paper-1-part-{}.mp3"

NARRATOR = "Narrator"
# Part 1 and Part 3 are conversations, Part 2 and Part 4 monologues.
VOICES = {
    1: {"Receptionist": "en-GB-SoniaNeural", "Maria": "en-AU-NatashaNeural"},
    2: {NARRATOR: "en-GB-RyanNeural"},
    3: {"Tutor": "en-GB-LibbyNeural", "Tom": "en-GB-ThomasNeural", "Aisha": "en-US-JennyNeural"},
    4: {NARRATOR: "en-GB-SoniaNeural"},
}

PART_HEADING = re.compile(r"^##\s+Part\s+(\d+)\b")
SPOKEN_LINE = re.compile(r"^\*\*([^*:]+):\*\*\s*(.+)$")
# The answer cues in the transcript, e.g. **(Q1 Kowalski)** or **(Q21–22 B)**, are for authors only.
ANSWER_CUE = re.compile(r"\s*\*\*\(Q[^)]*\)\*\*")


@dataclass(frozen=True)
class Segment:
    speaker: str
    text: str


def parse_transcript(markdown: str) -> dict[int, list[Segment]]:
    """Who says what in each part, answer cues removed. A line without a speaker is the narrator's."""
    parts: dict[int, list[Segment]] = {}
    current: int | None = None
    for raw in markdown.splitlines():
        line = raw.strip()
        heading = PART_HEADING.match(line)
        if heading:
            current = int(heading.group(1))
            parts[current] = []
            continue
        if current is None or not line or line.startswith("#"):
            continue
        spoken = SPOKEN_LINE.match(line)
        speaker, text = (spoken.group(1).strip(), spoken.group(2)) if spoken else (NARRATOR, line)
        text = ANSWER_CUE.sub("", text).replace("**", "").strip()
        if text:
            parts[current].append(Segment(speaker, text))
    return parts


def voice_for(part: int, speaker: str) -> str:
    voices = VOICES.get(part, {})
    if speaker not in voices:
        raise SystemExit(f"Part {part}: no voice for speaker {speaker!r}; add one to VOICES.")
    return voices[speaker]


async def record(part: int, segments: list[Segment], out: Path) -> None:
    import edge_tts  # only needed when actually recording

    audio = bytearray()
    for segment in segments:
        stream = edge_tts.Communicate(segment.text, voice_for(part, segment.speaker), rate="-5%").stream()
        async for chunk in stream:
            if chunk["type"] == "audio":
                audio.extend(chunk["data"])
    out.write_bytes(bytes(audio))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dry-run", action="store_true", help="print who says what, record nothing")
    args = parser.parse_args()

    parts = parse_transcript(TRANSCRIPT.read_text(encoding="utf-8"))
    if sorted(parts) != [1, 2, 3, 4]:
        raise SystemExit(f"Expected Parts 1-4 in the transcript, found {sorted(parts)}")
    for part, segments in parts.items():
        for segment in segments:
            voice_for(part, segment.speaker)  # fail before recording anything
    if args.dry_run:
        for part, segments in parts.items():
            print(f"Part {part}: {len(segments)} segments")
            for segment in segments:
                print(f"  {segment.speaker} ({voice_for(part, segment.speaker)}): {segment.text[:70]}")
        return

    OUT.mkdir(parents=True, exist_ok=True)
    for part, segments in parts.items():
        out = OUT / STEM.format(part)
        asyncio.run(record(part, segments, out))
        print(f"Part {part}: {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
