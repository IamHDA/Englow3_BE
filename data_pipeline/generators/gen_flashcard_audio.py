#!/usr/bin/env python3
"""Generator cho Flashcard Audio MP3 Files (Toàn bộ 3,000 từ vựng).

Sử dụng Microsoft Edge-TTS tự động phát sinh file âm thanh .mp3 thực tế
cho từng từ vựng Flashcard (US & UK voices) và lưu vào `output/audio/words/`.
"""

from __future__ import annotations

import asyncio
import csv
import sys
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parent.parent
SEED_CSV = ROOT / "seeds" / "vocab_seed.csv"
AUDIO_WORDS_DIR = ROOT / "output" / "audio" / "words"

VOICE_US = "en-US-GuyNeural"
VOICE_UK = "en-GB-SoniaNeural"

async def synthesize_word(word: str, voice: str, out_path: Path):
    try:
        communicate = edge_tts.Communicate(word, voice)
        await communicate.save(str(out_path))
    except Exception as e:
        print(f"Bỏ qua lỗi từ '{word}': {e}")

async def main_async(limit: int | None = None):
    if not SEED_CSV.exists():
        sys.exit(f"Thiếu {SEED_CSV}")

    AUDIO_WORDS_DIR.mkdir(parents=True, exist_ok=True)

    words: list[str] = []
    with SEED_CSV.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            w = row["lemma"].strip().lower()
            if w == "chauffer":
                w = "chauffeur"
            if w and w not in words:
                words.append(w)

    sample_words = words if limit is None else words[:limit]
    print(f"Đang tự động xuất {len(sample_words)} x 2 = {len(sample_words)*2} tệp MP3 phát âm thật vào output/audio/words/...")

    count = 0
    for w in sample_words:
        # Loại bỏ ký tự lạ trong filename nếu có
        safe_w = "".join(c for c in w if c.isalnum() or c in ("-", "_")).strip()
        if not safe_w:
            continue

        us_file = AUDIO_WORDS_DIR / f"{safe_w}_us.mp3"
        uk_file = AUDIO_WORDS_DIR / f"{safe_w}_uk.mp3"
        
        if not us_file.exists() or us_file.stat().st_size == 0:
            await synthesize_word(w, VOICE_US, us_file)
        if not uk_file.exists() or uk_file.stat().st_size == 0:
            await synthesize_word(w, VOICE_UK, uk_file)
            
        count += 2
        if count % 100 == 0:
            print(f"  + Đã tạo {count}/{len(sample_words)*2} tệp MP3 phát âm từ vựng...")

    print(f"Hoàn thành xuất tệp MP3 cho từ vựng tại {AUDIO_WORDS_DIR}!")

def main():
    asyncio.run(main_async(limit=None))

if __name__ == "__main__":
    main()
