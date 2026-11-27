"""Recording IELTS and TOEIC Listening audio, and checking a hand-written set."""

from __future__ import annotations

import io
import json

import sys
from pathlib import Path

import pytest

# The recording scripts import their engines as top-level modules; the tests use the same ones.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "generators"))

import openai_voice  # noqa: E402
import speech  # noqa: E402
from generators.assemble_ielts_set import load  # noqa: E402
from kokoro_voice import KokoroSpeaker, spoken  # noqa: E402
from openai_voice import Speaker  # noqa: E402
from speech import Line, silence, trim  # noqa: E402
from generators.record_ielts_listening import Pause, numbering, script, sections, voices  # noqa: E402
from generators.record_ielts_listening import Line as ScriptLine  # noqa: E402


def part_one(answers: int = 10) -> dict:
    """A Part 1 conversation whose n-th line answers the n-th gap."""
    transcript = [{"speaker": "Ann", "text": "Hello, can I help?"}]
    for n in range(1, answers + 1):
        transcript.append({"speaker": "Ben", "text": f"Well, the answer is word{n}, I think."})
        transcript.append({"speaker": "Ann", "text": "Right, thanks."})
    return {
        "topic": "a booking",
        "context": "a woman taking a booking",
        "speakers": [{"name": "Ann", "accent": "UK", "gender": "female"},
                     {"name": "Ben", "accent": "AU", "gender": "male"}],
        "transcript": transcript,
        "sets": [{
            "kind": "gap", "instruction": "Complete the form.", "word_limit": 1,
            "content": " ".join(f"{{{{{n}}}}}" for n in range(1, answers + 1)),
            "questions": [{"prompt": f"q{n}", "answers": [f"word{n}"], "evidence": f"the answer is word{n}"}
                          for n in range(1, answers + 1)],
        }],
    }


def test_part_one_is_split_in_the_middle_before_the_line_that_answers_the_sixth_question():
    spec = part_one()
    ranges = numbering({1: spec})[1]

    assert ranges == [(1, 10)]
    assert sections(1, spec, ranges) == [(1, 5, 0), (6, 10, 10)]


def test_the_script_frames_the_speakers_with_the_narrators_rubric_and_timed_silences():
    spec = part_one()
    engine = KokoroSpeaker()
    items = script(1, spec, numbering({1: spec})[1], "set", engine)
    said = [item.text for item in items if isinstance(item, ScriptLine) and item.voice == engine.narrator]

    assert said[0] == "Part 1. You will hear a woman taking a booking."
    assert "First, you have some time to look at questions 1 to 5." in said
    assert any("look at questions 6 to 10" in s for s in said)
    assert said[-1].startswith("That is the end of Part 1.")
    assert Pause(30.0) in items
    lines = [item for item in items if isinstance(item, ScriptLine) and item.voice != engine.narrator]
    assert len(lines) == len(spec["transcript"])


def test_preview_shortens_every_long_silence():
    spec = part_one()
    items = script(1, spec, numbering({1: spec})[1], "set", KokoroSpeaker(), short=True)

    assert max(item.seconds for item in items if isinstance(item, Pause)) <= 2.0


@pytest.mark.parametrize("engine", [KokoroSpeaker(), Speaker(key="test")])
def test_speakers_in_a_part_never_share_a_voice_or_the_narrators(engine):
    spec = part_one()
    spec["speakers"] += [{"name": "Cat", "accent": "UK", "gender": "female"},
                         {"name": "Dee", "accent": "UK", "gender": "female"}]

    chosen = voices(spec, 1, "set", engine)
    assert len(set(chosen.values())) == 4 and engine.narrator not in chosen.values()


def test_kokoro_voices_follow_the_speakers_accent():
    chosen = voices(part_one(), 1, "set", KokoroSpeaker())
    assert chosen["Ann"].startswith("bf_") and chosen["Ben"].startswith("bm_")  # AU is read as British


def test_spelt_names_codes_and_times_are_written_out_the_way_they_are_read():
    assert spoken("It's C-R-A-N-B-R-O-O-K.") == "It's see, ar, eh, en, bee, ar, double oh, kay."
    assert spoken("Reference LP4872, please.") == "Reference el, pee, four, eight, seven, two, please."
    assert spoken("Doors open at 9.30 and close at 7.00.") == "Doors open at nine thirty and close at seven o'clock."
    assert spoken("Meet at 9.15 a.m. sharp.") == "Meet at nine fifteen a.m. sharp."
    assert spoken("Call 07700 900451.") == "Call oh seven seven oh oh, nine oh oh four five one."
    assert spoken("In 1835 it cost £12,000.") == "In 1835 it cost £12,000."
    assert spoken("It's 3 km away, or 3km.") == "It's 3 kilometres away, or 3 kilometres."
    assert spoken("It costs £85 and lasts 2.5 hours.") == "It costs £85 and lasts 2.5 hours."


def test_a_line_is_recorded_once_and_then_read_from_the_cache(tmp_path, monkeypatch):
    calls = []

    def fake_urlopen(request, timeout):
        calls.append(json.loads(request.data))
        return io.BytesIO(b"\x10\x27" * speech.RATE * 2)

    monkeypatch.setattr(openai_voice.urllib.request, "urlopen", fake_urlopen)
    speaker = Speaker(key="test", cache=tmp_path)
    line = Line("Hello there.", "coral", "Speak naturally.")

    first, second = speaker.pcm(line), speaker.pcm(line)

    assert first == second
    assert len(calls) == 1 and calls[0]["response_format"] == "pcm" and calls[0]["voice"] == "coral"
    assert (speaker.requests, speaker.cached) == (1, 1)


def test_too_little_audio_is_an_error_not_a_silent_gap(tmp_path, monkeypatch):
    monkeypatch.setattr(openai_voice.urllib.request, "urlopen", lambda request, timeout: io.BytesIO(b"\x00\x00"))
    with pytest.raises(speech.VoiceError):
        Speaker(key="test", cache=tmp_path).pcm(Line("A sentence that should take a few seconds to say.", "ash", ""))


def test_trim_drops_the_silence_around_a_line():
    loud = b"\x10\x27" * speech.RATE
    padded = silence(1.0) + loud + silence(1.0)

    trimmed = trim(padded)
    assert len(loud) <= len(trimmed) < len(loud) + 2 * int(speech.RATE * 0.1) * 2


def test_a_hand_written_set_must_have_every_component(tmp_path):
    (tmp_path / "plan.json").write_text("{}", encoding="utf-8")
    with pytest.raises(SystemExit, match="listening-1.json"):
        load(tmp_path)


def test_toeic_options_and_conversations_are_split_into_turns():
    from generators.record_toeic_listening import conversation, options, script

    lead, opts = options("Where is it?\n(A) In the drawer.\n(B) About forty.\n(C) Yes, I did.")
    assert lead == "Where is it?" and [letter for letter, _ in opts] == ["A", "B", "C"]

    turns = conversation("W: Morning - I rang\n   about the flat.\nM: Ah yes.\nW1: Fine.")
    assert turns == [("W", "Morning - I rang about the flat."), ("M", "Ah yes."), ("W1", "Fine.")]

    part2 = script({"key": "k.mp3", "part": 2, "transcript": "Where is it?\n(A) Here.\n(B) There.\n(C) Nowhere."},
                   KokoroSpeaker())
    said = [item for item in part2 if hasattr(item, "voice")]
    assert [item.text for item in said] == ["Where is it?", "(A) Here.", "(B) There.", "(C) Nowhere."]
    assert said[0].voice != said[1].voice and len({item.voice for item in said[1:]}) == 1


def test_an_answer_written_one_way_and_heard_another_still_counts_as_heard():
    pytest.importorskip("misaki")
    from verify_listening_audio import heard

    assert heard("£950", "so the rent is nine hundred fifty pounds a month")
    assert heard("12th", "we fly out on the twelfth of june")
    assert heard("tenor", "my teacher reckons i'm really a tenner these days")
    assert not heard("tenor", "my teacher reckons i'm really a baritone these days")
