"""The IELTS trial paper's transcript, split into who says what before it is recorded."""

from __future__ import annotations

from generators.synthesize_ielts_trial_audio import (
    NARRATOR,
    TRANSCRIPT,
    Segment,
    parse_transcript,
    voice_for,
)

SAMPLE = """# Title

Notes before the first part are not spoken.

## Part 1 — A call

**Receptionist:** Can I take your name?
**Maria:** It's Maria Kowalski. **(Q1 Kowalski)**

## Part 2 — A talk

Welcome to the wood. **(Q11 B)** It opened in 1985.
"""


def test_speakers_lines_and_narration_are_split_without_answer_cues():
    parts = parse_transcript(SAMPLE)

    assert parts == {
        1: [
            Segment("Receptionist", "Can I take your name?"),
            Segment("Maria", "It's Maria Kowalski."),
        ],
        2: [Segment(NARRATOR, "Welcome to the wood. It opened in 1985.")],
    }


def test_every_speaker_of_the_real_transcript_has_a_voice():
    parts = parse_transcript(TRANSCRIPT.read_text(encoding="utf-8"))

    assert sorted(parts) == [1, 2, 3, 4]
    for part, segments in parts.items():
        assert segments, f"Part {part} has nothing to say"
        for segment in segments:
            assert voice_for(part, segment.speaker)
            assert "(Q" not in segment.text
