#!/usr/bin/env python3
"""Synthesize shadowing clips sentence-by-sentence and persist measured cues."""

from __future__ import annotations

import asyncio
import json
import subprocess
import tempfile
from pathlib import Path

from synthesize_listening_audio import Segment, duration_ms, synthesize_segment

ROOT = Path(__file__).resolve().parent.parent
BATCH = ROOT / "output" / "shadowing" / "shadowing_batch_001.json"
MEDIA = ROOT / "output" / "media" / "audio" / "shadowing"
PUBLIC_BASE = "http://localhost:9000/audio/shadowing"


async def run() -> None:
    document = json.loads(BATCH.read_text(encoding="utf-8"))
    MEDIA.mkdir(parents=True, exist_ok=True)
    semaphore = asyncio.Semaphore(8)
    with tempfile.TemporaryDirectory(prefix="englow_shadowing_tts_") as temp:
        work = Path(temp)
        for clip_index, clip in enumerate(document["clips"]):
            target = MEDIA / f"{clip['clip_id']}.mp3"
            segment_files = [work / f"{clip['clip_id']}_{i:02d}.mp3"
                             for i in range(len(clip["segments"]))]
            speaker = "W" if clip_index % 2 == 0 else "M"
            timings = await asyncio.gather(*[
                synthesize_segment(
                    Segment(speaker, seg["text"]), clip["accent"], path,
                    semaphore, rate="-45%")
                for seg, path in zip(clip["segments"], segment_files)
            ])
            concat = work / f"{clip['clip_id']}.txt"
            concat.write_text("".join(f"file '{p.as_posix()}'\n" for p in segment_files), encoding="utf-8")
            subprocess.run([
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "concat",
                "-safe", "0", "-i", str(concat), "-c", "copy", str(target),
            ], check=True)
            total, elapsed = duration_ms(target), 0
            for seg, path, (word_start, word_end) in zip(clip["segments"], segment_files, timings):
                seg["start_ms"] = elapsed + word_start
                seg["end_ms"] = min(total, elapsed + word_end)
                elapsed += duration_ms(path)
            clip["audio_url"] = f"{PUBLIC_BASE}/{target.name}"
            clip["duration_ms"] = total
    BATCH.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Synthesized and aligned {len(document['clips'])} shadowing/dictation clips")


if __name__ == "__main__":
    asyncio.run(run())
