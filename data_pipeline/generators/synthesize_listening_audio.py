#!/usr/bin/env python3
"""Synthesize all Listening audio, measure duration, and persist cue timing."""

from __future__ import annotations

import argparse
import asyncio
import json
import re
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parent.parent
BANK = ROOT / "output" / "exams" / "bank"
MEDIA = ROOT / "output" / "media" / "audio" / "toeic" / "listening"
PUBLIC_BASE = "http://localhost:9000/images/toeic/listening/audio"
SPEAKER_RE = re.compile(r"^(W\d*|M\d*):\s*(.*)$")

VOICES = {
    "US": {"W": "en-US-JennyNeural", "M": "en-US-GuyNeural"},
    "UK": {"W": "en-GB-SoniaNeural", "M": "en-GB-RyanNeural"},
    "AU": {"W": "en-AU-NatashaNeural", "M": "en-AU-WilliamNeural"},
    "CA": {"W": "en-CA-ClaraNeural", "M": "en-CA-LiamNeural"},
}


@dataclass
class Segment:
    speaker: str
    text: str


def dialogue_segments(part: int, script: str, ordinal: int) -> list[Segment]:
    if part in (1, 4):
        return [Segment("W" if ordinal % 2 == 0 else "M", " ".join(script.split()))]
    if part == 2:
        lines = [line.strip() for line in script.splitlines() if line.strip()]
        return [
            Segment("W" if ordinal % 2 == 0 else "M", lines[0]),
            Segment("M" if ordinal % 2 == 0 else "W", " ".join(lines[1:])),
        ]

    segments: list[Segment] = []
    speaker: str | None = None
    words: list[str] = []
    for raw_line in script.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        match = SPEAKER_RE.match(line)
        if match:
            if speaker and words:
                segments.append(Segment(speaker, " ".join(words)))
            speaker, first = match.groups()
            words = [first] if first else []
        elif speaker:
            words.append(line)
        else:
            raise ValueError(f"Part {part} dialogue line has no speaker label: {line!r}")
    if speaker and words:
        segments.append(Segment(speaker, " ".join(words)))
    if not segments:
        raise ValueError(f"No dialogue segments parsed for Part {part}")
    return segments


def duration_ms(path: Path) -> int:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
        check=True, capture_output=True, text=True,
    )
    return round(float(result.stdout.strip()) * 1000)


async def synthesize_segment(segment: Segment, accent: str, path: Path,
                             semaphore: asyncio.Semaphore,
                             rate: str = "-5%") -> tuple[int, int]:
    voice = VOICES[accent][segment.speaker[0]]
    first_tick: int | None = None
    last_tick: int | None = None
    async with semaphore:
        communicator = edge_tts.Communicate(segment.text, voice, rate=rate)
        with path.open("wb") as stream:
            async for chunk in communicator.stream():
                if chunk["type"] == "audio":
                    stream.write(chunk["data"])
                elif chunk["type"] == "WordBoundary":
                    first_tick = chunk["offset"] if first_tick is None else first_tick
                    last_tick = chunk["offset"] + chunk["duration"]
    if not path.exists() or path.stat().st_size == 0:
        raise RuntimeError(f"TTS returned no audio for {path.name}")
    measured = duration_ms(path)
    start = round((first_tick or 0) / 10_000)
    end = round((last_tick or measured * 10_000) / 10_000)
    return min(start, measured - 1), min(max(end, 1), measured)


async def synthesize_group(group: dict, ordinal: int, semaphore: asyncio.Semaphore,
                           work: Path, force: bool) -> tuple[str, int, list[dict]]:
    group_id = group["group_id"]
    output = MEDIA / f"{group_id}.mp3"
    segments = dialogue_segments(group["part_number"], group["audio"]["script"], ordinal)
    if output.exists() and group["audio"].get("cues") and not force:
        return group_id, duration_ms(output), group["audio"]["cues"]

    segment_files = [work / f"{group_id}_{index:02d}.mp3" for index in range(len(segments))]
    timings = await asyncio.gather(*[
        synthesize_segment(segment, group["audio"]["accent"], path, semaphore)
        for segment, path in zip(segments, segment_files)
    ])

    if len(segment_files) == 1:
        shutil.copyfile(segment_files[0], output)
    else:
        concat_file = work / f"{group_id}.txt"
        concat_file.write_text(
            "".join(f"file '{path.as_posix()}'\n" for path in segment_files), encoding="utf-8")
        subprocess.run(
            ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "concat",
             "-safe", "0", "-i", str(concat_file), "-c", "copy", str(output)],
            check=True,
        )

    total = duration_ms(output)
    cues: list[dict] = []
    elapsed = 0
    for segment, path, (word_start, word_end) in zip(segments, segment_files, timings):
        segment_duration = duration_ms(path)
        cues.append({
            "speaker": segment.speaker,
            "text": segment.text,
            "start_ms": elapsed + word_start,
            "end_ms": min(total, elapsed + word_end),
        })
        elapsed += segment_duration
    return group_id, total, cues


async def run(force: bool) -> None:
    MEDIA.mkdir(parents=True, exist_ok=True)
    documents: list[tuple[Path, dict]] = []
    groups: list[dict] = []
    for path in sorted(BANK.rglob("*.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        audio_groups = [group for group in document.get("groups", []) if group.get("audio")]
        if audio_groups:
            documents.append((path, document))
            groups.extend(audio_groups)

    semaphore = asyncio.Semaphore(8)
    with tempfile.TemporaryDirectory(prefix="englow_tts_") as temp:
        work = Path(temp)
        results = await asyncio.gather(*[
            synthesize_group(group, index, semaphore, work, force)
            for index, group in enumerate(groups)
        ])
    metadata = {group_id: (duration, cues) for group_id, duration, cues in results}

    for path, document in documents:
        for group in document.get("groups", []):
            if not group.get("audio"):
                continue
            duration, cues = metadata[group["group_id"]]
            group["audio"].update({
                "audio_url": f"{PUBLIC_BASE}/{group['group_id']}.mp3",
                "duration_ms": duration,
                "alignment_status": "aligned",
                "cues": cues,
            })
        path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total_bytes = sum(path.stat().st_size for path in MEDIA.glob("*.mp3"))
    print(f"Synthesized {len(groups)} listening assets ({total_bytes / 1_048_576:.1f} MiB)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="regenerate existing audio")
    args = parser.parse_args()
    asyncio.run(run(args.force))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
