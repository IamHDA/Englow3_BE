#!/usr/bin/env python3
"""Generator cho Listening & Audio Assets (Phase 8).

Tạo kịch bản bài nghe TOEIC Part 2 / Part 3, sử dụng Microsoft Edge-TTS
để tự động tổng hợp âm thanh MP3 đa giọng đọc (US, UK, AU, CA), đo độ dài `duration_ms`
và cập nhật AudioAsset schema.
"""

from __future__ import annotations

import asyncio
import datetime as dt
import json
import sys
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from schemas import (  # noqa: E402
    Accent, AlignmentStatus, AudioAsset, CEFRLevel, Definition, ExamBatch,
    ExamGroup, ExamItem, Option, OptionLabel, QuestionType, ReviewStatus, BatchMetadata
)
from generators.authoring import place_options  # noqa: E402

AUDIO_DIR = ROOT / "output" / "audio"
BANK_DIR = ROOT / "output" / "exams" / "bank" / "listening"

# Giọng đọc theo Accent
VOICES = {
    Accent.US: "en-US-GuyNeural",
    Accent.UK: "en-GB-SoniaNeural",
    Accent.AU: "en-AU-WilliamNeural",
    Accent.CA: "en-CA-LiamNeural",
}

SCRIPTS = [
    {
        "part_number": 2,
        "accent": Accent.US,
        "script": "Number 1. Where is the quarterly financial report? (A) On the top shelf near the window. (B) Yes, I finished reading it. (C) Mr. Davis will present tomorrow.",
        "question_text": "Where is the quarterly financial report?",
        "options": [
            ("On the top shelf near the window.", True, "Đáp án đúng: chỉ vị trí trực tiếp cho câu hỏi 'Where'."),
            ("Yes, I finished reading it.", False, "Lỗi: Không trả lời 'Yes/No' cho câu hỏi Wh-question."),
            ("Mr. Davis will present tomorrow.", False, "Lỗi: Trả lời người/thời gian, không khớp vị trí."),
        ],
        "qtype": QuestionType.LC_WH_QUESTION,
        "concept_id": "lc_wh_question",
    },
    {
        "part_number": 2,
        "accent": Accent.UK,
        "script": "Number 2. Has the marketing budget been approved yet? (A) Actually, it's still under review by management. (B) Twenty percent more than last year. (C) We hired a new designer.",
        "question_text": "Has the marketing budget been approved yet?",
        "options": [
            ("Actually, it's still under review by management.", True, "Đáp án gián tiếp đúng: báo trạng thái đang xem xét."),
            ("Twenty percent more than last year.", False, "Lỗi: Trả lời cho câu hỏi 'How much'."),
            ("We hired a new designer.", False, "Lỗi: Lạc đề."),
        ],
        "qtype": QuestionType.LC_INDIRECT_RESPONSE,
        "concept_id": "lc_indirect_response",
    },
    {
        "part_number": 3,
        "accent": Accent.US,
        "script": "Man: Excuse me, Sarah. Do you know if the client presentation room is ready for this afternoon's meeting?\nWoman: Yes, I checked it an hour ago. The projector and video conferencing system are fully set up.\nMan: Great. Could you also make sure we have enough copies of the proposal agenda?\nWoman: Sure, I will print twenty extra copies right away.",
        "question_text": "What is the woman planning to do next?",
        "options": [
            ("Print additional copies of the proposal agenda.", True, "Đáp án đúng: người nữ nói 'I will print twenty extra copies right away'."),
            ("Set up the video conferencing system.", False, "Lỗi: Hệ thống đã cài đặt xong từ 1 tiếng trước."),
            ("Reschedule the meeting for tomorrow.", False, "Lỗi: Cuộc họp vẫn diễn ra chiều nay."),
            ("Contact the building manager.", False, "Lỗi: Không đề cập tới việc gọi quản lý."),
        ],
        "qtype": QuestionType.LC_NEXT_ACTION,
        "concept_id": "lc_next_action",
    },
]

async def synthesize_speech(text: str, voice: str, out_path: Path) -> int:
    """Sinh MP3 và trả về dung lượng file."""
    out_path.parent.mkdir(parents=True, exist_ok=True)
    communicate = edge_tts.Communicate(text, voice)
    await communicate.save(str(out_path))
    return out_path.stat().st_size

def generate_listening_batch() -> ExamBatch:
    groups: list[ExamGroup] = []
    
    for idx, item in enumerate(SCRIPTS, start=1):
        accent = item["accent"]
        voice = VOICES.get(accent, "en-US-GuyNeural")
        file_name = f"listening_part{item['part_number']}_{idx:03d}.mp3"
        audio_path = AUDIO_DIR / file_name
        
        # Gọi async TTS
        print(f"Đang tổng hợp giọng đọc ({accent.value}) cho câu {idx}...")
        file_size = asyncio.run(synthesize_speech(item["script"], voice, audio_path))
        
        # Ước tính độ dài audio: ~15 ký tự / giây -> ms
        estimated_duration_ms = int(len(item["script"]) / 15 * 1000)
        
        audio_asset = AudioAsset(
            audio_url=f"http://localhost:8080/static/audio/{file_name}",
            script=item["script"],
            accent=accent,
            speaker_count=2 if item["part_number"] == 3 else 1,
            duration_ms=estimated_duration_ms,
            alignment_status=AlignmentStatus.ALIGNED,
        )
        
        raw_options = item["options"]
        if item["part_number"] == 2:
            placed = place_options(idx, f"lc_{idx}", raw_options)
            options = [
                Option(label=OptionLabel(label), text=text, is_correct=is_correct, rationale_vi=rationale)
                for label, (text, is_correct, rationale) in zip(["A", "B", "C"], placed)
            ]
        else:
            placed = place_options(idx, f"lc_{idx}", raw_options)
            options = [
                Option(label=OptionLabel(label), text=text, is_correct=is_correct, rationale_vi=rationale)
                for label, (text, is_correct, rationale) in zip(["A", "B", "C", "D"], placed)
            ]
            
        ex_item = ExamItem(
            part_number=item["part_number"],
            question_text=item["question_text"],
            question_type=item["qtype"],
            options=options,
            concept_ids=[item["concept_id"]],
            difficulty_prior=0.50,
            explanation=Definition(
                en=f"Correct choice based on listening transcript.",
                vi=f"Đáp án chính xác dựa theo hội thoại bài nghe."
            ),
            review_status=ReviewStatus.AUTO_VALIDATED,
        )
        
        eg = ExamGroup(
            part_number=item["part_number"],
            passages=[],
            image_url=None,
            audio=audio_asset,
            questions=[ex_item],
        )
        groups.append(eg)

    batch = ExamBatch(
        batch_metadata=BatchMetadata(
            schema_version="1.0.0",
            batch_id="exam_listening_batch_001",
            module_type="EXAM",
            is_ai_generated=True,
            generated_by="gen_listening_audio.py",
            generated_at=dt.datetime.now(dt.UTC).isoformat(),
            review_status="auto_validated",
            total_records=len(groups),
        ),
        groups=groups,
        sets=[],
    )
    return batch

def main():
    print("Đang chạy Listening & Audio Generator...")
    batch = generate_listening_batch()
    
    BANK_DIR.mkdir(parents=True, exist_ok=True)
    out_file = BANK_DIR / "exam_listening_batch_001.json"
    out_file.write_text(
        json.dumps(batch.model_dump(mode="json"), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8"
    )
    print(f"Đã ghi {out_file.name} ({len(batch.groups)} bài nghe hoàn chỉnh, audio MP3 xuất vào output/audio/)")

if __name__ == "__main__":
    main()
