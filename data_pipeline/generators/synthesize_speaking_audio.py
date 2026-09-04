#!/usr/bin/env python3
"""Generate the three recorded questions used by Speaking tasks 8–10."""

from __future__ import annotations

import asyncio
import json
import shutil
import tempfile
from pathlib import Path

from synthesize_listening_audio import Segment, duration_ms, synthesize_segment

ROOT = Path(__file__).resolve().parent.parent
BATCH = ROOT / "output" / "speaking_writing" / "speaking_batch_001.json"
MEDIA = ROOT / "output" / "media" / "audio" / "toeic" / "speaking"
PUBLIC_BASE = "http://localhost:9000/images/toeic/speaking-writing/speaking/audio"


async def run() -> None:
    document = json.loads(BATCH.read_text(encoding="utf-8"))
    tasks = [task for task in document["tasks"] if task.get("audio")]
    MEDIA.mkdir(parents=True, exist_ok=True)
    semaphore = asyncio.Semaphore(3)

    with tempfile.TemporaryDirectory(prefix="englow_speaking_tts_") as temp:
        work = Path(temp)
        jobs = []
        paths = []
        for task in tasks:
            path = work / f"{task['task_id']}.mp3"
            paths.append(path)
            jobs.append(synthesize_segment(
                Segment("W", task["audio"]["script"]), task["audio"]["accent"], path, semaphore))
        timings = await asyncio.gather(*jobs)

        for task, source, (start, end) in zip(tasks, paths, timings):
            target = MEDIA / source.name
            shutil.copyfile(source, target)
            measured = duration_ms(target)
            task["audio"].update({
                "audio_url": f"{PUBLIC_BASE}/{target.name}",
                "duration_ms": measured,
                "alignment_status": "aligned",
                "cues": [{
                    "speaker": "W",
                    "text": task["audio"]["script"],
                    "start_ms": start,
                    "end_ms": min(end, measured),
                }],
            })

    BATCH.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Synthesized {len(tasks)} Speaking prompt audio assets")


if __name__ == "__main__":
    asyncio.run(run())
