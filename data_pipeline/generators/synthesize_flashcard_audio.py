#!/usr/bin/env python3
"""Synthesize resumable US/UK pronunciation audio for every flashcard."""

from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parent.parent
FLASHCARDS = ROOT / "output" / "flashcards"
MEDIA = ROOT / "output" / "media" / "audio" / "flashcards"
PUBLIC_BASE = "http://localhost:9000/audio/flashcards"
VOICES = {"us": "en-US-JennyNeural", "uk": "en-GB-SoniaNeural"}


async def synthesize(text: str, voice: str, path: Path,
                     semaphore: asyncio.Semaphore) -> None:
    if path.exists() and path.stat().st_size > 300:
        return
    last_error = None
    for attempt in range(3):
        try:
            async with semaphore:
                await edge_tts.Communicate(text, voice, rate="-8%").save(str(path))
            if path.exists() and path.stat().st_size > 300:
                return
            raise RuntimeError("TTS returned an empty or undersized file")
        except Exception as error:  # network/TTS retry boundary
            last_error = error
            if path.exists():
                path.unlink()
            await asyncio.sleep(1 + attempt * 2)
    raise RuntimeError(f"Failed {path.name}: {last_error}")


async def run(limit: int | None, concurrency: int) -> None:
    documents = []
    cards = []
    for path in sorted(FLASHCARDS.glob("flashcard_batch_*.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        documents.append((path, document))
        cards.extend(document["flashcards"])
    if limit is not None:
        cards = cards[:limit]
    MEDIA.mkdir(parents=True, exist_ok=True)
    semaphore = asyncio.Semaphore(concurrency)
    jobs = []
    for card in cards:
        for accent, voice in VOICES.items():
            target = MEDIA / f"{card['id']}_{accent}.mp3"
            jobs.append(synthesize(card["lemma"], voice, target, semaphore))

    completed = 0
    for offset in range(0, len(jobs), 240):
        await asyncio.gather(*jobs[offset:offset + 240])
        completed += len(jobs[offset:offset + 240])
        print(f"audio {completed}/{len(jobs)}")

    selected = {card["id"] for card in cards}
    for path, document in documents:
        changed = False
        for card in document["flashcards"]:
            if card["id"] not in selected:
                continue
            us = MEDIA / f"{card['id']}_us.mp3"
            uk = MEDIA / f"{card['id']}_uk.mp3"
            if not (us.exists() and uk.exists()):
                raise RuntimeError(f"Missing pronunciation audio for {card['id']}")
            card["audio_url_us"] = f"{PUBLIC_BASE}/{us.name}"
            card["audio_url_uk"] = f"{PUBLIC_BASE}/{uk.name}"
            changed = True
        if changed:
            path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Synthesized {len(cards)} cards / {len(jobs)} pronunciation assets")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int)
    parser.add_argument("--concurrency", type=int, default=24)
    args = parser.parse_args()
    asyncio.run(run(args.limit, args.concurrency))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
