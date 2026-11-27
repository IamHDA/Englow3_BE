#!/usr/bin/env python3
"""Record an IELTS Listening paper the way the real recording sounds.

Reads a built set (data_pipeline/output/ielts/<set>/components.json) and writes audio/part-N.mp3,
which scripts/load_ielts_trial.py uploads. Around the speakers it adds what a candidate hears in the
real test: the narrator's introduction, time to look at the questions before each section of a part
(with the part split where its questions are, as the real recording does), and time to check the
answers at the end of each part.

    python data_pipeline/generators/record_ielts_listening.py --set-dir data_pipeline/output/ielts/<set>
    python data_pipeline/generators/record_ielts_listening.py --set-dir ... --parts 1 --preview
    python data_pipeline/generators/record_ielts_listening.py --set-dir ... --dry-run
    python data_pipeline/generators/record_ielts_listening.py --set-dir ... --engine openai

The voices come from Kokoro on this machine by default (free), or from OpenAI (see speech.py).

--preview shortens every silence to two seconds, to listen to the voices without the waiting.
A component may give its speakers a "role" (who they are) and itself a "context" (what the narrator
says the candidate will hear); without them both are worked out from the part and the topic.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "generators"))

from ielts import rules  # noqa: E402
from speech import ENGINES, Engine, Line, Pause, engine, instructions, render  # noqa: E402

NARRATOR = instructions(
    "UK", "You are the narrator of an official English language listening examination.",
    "Speak clearly, calmly and neutrally, at a measured pace, as on a professionally produced exam recording.")
LOOK = {1: 30.0, 2: 30.0, 3: 30.0, 4: 45.0}  # seconds to read the questions before the first section
LOOK_AGAIN = 30.0  # before a later section of the same part
CHECK = 30.0  # to check the answers at the end of a part
MIN_SECTION = 4  # fewest questions the candidate looks at in one go
TITLED = ("dr ", "professor", "tutor", "lecturer", "prof ")

ROLE = {
    1: "You are {name}, one of two people in an everyday conversation about practical arrangements.",
    2: "You are {name}, giving an informative talk to a group of people.",
    3: "You are {name}, a university student discussing a piece of coursework with others.",
    4: "You are {name}, a university lecturer giving a lecture to a class of students.",
}
TUTOR = "You are {name}, a university tutor discussing a piece of coursework with students."
DELIVERY = {
    1: "Friendly and relaxed, as in a real conversation, reacting naturally to the other person.",
    2: "Warm, clear and engaging, like a guide or presenter addressing an audience in person.",
    3: "An informal academic discussion: thinking aloud, agreeing and disagreeing naturally.",
    4: "An engaged, fluent lecturing style with natural emphasis and the occasional aside.",
}
KIND = {1: "conversation", 2: "talk", 3: "discussion", 4: "lecture"}
# Gaps between turns, varied so the exchange does not tick like a metronome.
TURN_GAPS = (0.45, 0.35, 0.6, 0.4, 0.5, 0.3, 0.55)
SAME_SPEAKER_GAP = 0.75
AFTER_NARRATOR = 1.5


def numbering(listening: dict[int, dict]) -> dict[int, list[tuple[int, int]]]:
    """For each part, the first and last question number of each of its sets."""
    ranges, number = {}, 1
    for n in sorted(listening):
        ranges[n] = []
        for qset in listening[n]["sets"]:
            marks = rules.set_marks(qset)
            ranges[n].append((number, number + marks - 1))
            number += marks
    return ranges


def first_evidence(question: dict) -> str:
    evidence = question.get("evidence")
    quotes = evidence if isinstance(evidence, list) else [evidence]
    return next((q for q in quotes if isinstance(q, str) and q.strip()), "")


def line_of(transcript: list[dict], quote: str, start: int) -> int | None:
    for i in range(start, len(transcript)):
        if quote and rules.Source(transcript[i]["text"]).find(quote) >= 0:
            return i
    return None


def sections(part: int, spec: dict, ranges: list[tuple[int, int]]) -> list[tuple[int, int, int]]:
    """Where the recording stops for the candidate to look at the next questions.

    Each section is (first question, last question, first transcript line). A part made of several
    sets breaks before each later set; Part 1 and 2 with a single set break in the middle, as in the
    real test. Part 4 plays straight through.
    """
    transcript = spec["transcript"]
    starts: list[tuple[int, int]] = []  # (question number, line)
    questions = [(q, qset) for qset in spec["sets"] for q in qset["questions"]]
    first = ranges[0][0]
    if len(spec["sets"]) > 1:
        # Small sets are looked at together: every section covers at least MIN_SECTION questions.
        cut_numbers, since = [], first
        for start, _ in ranges[1:]:
            if start - since >= MIN_SECTION and ranges[-1][1] + 1 - start >= MIN_SECTION:
                cut_numbers.append(start)
                since = start
    elif part in (1, 2) and len(questions) >= 8:
        cut_numbers = [first + len(questions) // 2 + (len(questions) % 2)]
    else:
        cut_numbers = []
    # A choose-TWO question is worth two numbers but is one question.
    numbers, number = [], first
    for _, qset in questions:
        numbers.append(number)
        number += len(_.get("answers", [])) if qset["kind"] == "multi" else 1
    for cut in cut_numbers:
        if cut not in numbers:
            continue
        q = questions[numbers.index(cut)][0]
        previous = starts[-1][1] if starts else 1
        at = line_of(transcript, first_evidence(q), previous)
        # Break at the turn before the one that answers it, so the candidate is ready in time.
        if at is not None and at - 1 > previous:
            starts.append((cut, at - 1))
    last = ranges[-1][1]
    bounds = [(first, 0)] + starts
    return [(q, (bounds[i + 1][0] - 1 if i + 1 < len(bounds) else last), line)
            for i, (q, line) in enumerate(bounds)]


def voices(spec: dict, part: int, seed: str, speaker: Engine) -> dict[str, str]:
    """A different voice for each speaker in the part, varied from set to set and part to part.

    Each speaker gets one of the best voices for their gender and accent, rotating from part to part;
    the rest of the pool, then the other accent's voices, are there for when those are taken.
    """
    offset = int(hashlib.sha256(f"{seed}-{part}".encode()).hexdigest(), 16)
    used: set[str] = {speaker.narrator}
    chosen = {}
    for i, person in enumerate(spec["speakers"]):
        gender, accent = person.get("gender", "female"), person.get("accent", "UK")
        pool = speaker.pool(gender, accent)
        top = max(1, min(getattr(speaker, "varied", len(pool)), len(pool)))
        start = (offset + i) % top
        ordered = pool[start:top] + pool[:start] + pool[top:]
        ordered += [v for other in ("UK", "US") for v in speaker.pool(gender, other) if v not in ordered]
        voice = next((v for v in ordered if v not in used), ordered[0])
        used.add(voice)
        chosen[person["name"]] = voice
    return chosen


def speaker_instructions(spec: dict, part: int) -> dict[str, str]:
    out = {}
    for speaker in spec["speakers"]:
        name = speaker["name"]
        role = speaker.get("role") or (
            TUTOR if part == 3 and name.lower().startswith(TITLED) else ROLE[part]).format(name=name)
        out[name] = instructions(speaker.get("accent", "UK"), role, DELIVERY[part])
    return out


def context(spec: dict, part: int) -> str:
    given = str(spec.get("context") or "").strip().rstrip(".")
    if given:
        return given
    topic = str(spec.get("topic") or "a subject").strip().rstrip(".")
    if len(topic) > 1 and topic[1].islower():  # "Booking a place" -> "booking a place", but keep "UK ..."
        topic = topic[0].lower() + topic[1:]
    return f"a {KIND[part]} about {topic}"


def script(part: int, spec: dict, ranges: list[tuple[int, int]], seed: str, speaker: Engine,
           short: bool = False) -> list:
    wait = (lambda s: 2.0) if short else (lambda s: s)
    voice = voices(spec, part, seed, speaker)

    def narrator(text: str) -> Line:
        return Line(text, speaker.narrator, NARRATOR, speed=0.95)

    how = speaker_instructions(spec, part)
    parts = sections(part, spec, ranges)
    transcript = spec["transcript"]
    out: list = []
    for i, (first, last, start) in enumerate(parts):
        end = parts[i + 1][2] if i + 1 < len(parts) else len(transcript)
        if i == 0:
            out += [narrator(f"Part {part}. You will hear {context(spec, part)}."), Pause(1.0),
                    narrator(f"First, you have some time to look at questions {first} to {last}."),
                    Pause(wait(LOOK[part])),
                    narrator(f"Now listen carefully and answer questions {first} to {last}."),
                    Pause(AFTER_NARRATOR)]
        else:
            out += [Pause(1.5),
                    narrator(f"Before you hear the rest of the {KIND[part]}, you have some time to look at "
                             f"questions {first} to {last}."),
                    Pause(wait(LOOK_AGAIN)),
                    narrator(f"Now listen and answer questions {first} to {last}."),
                    Pause(AFTER_NARRATOR)]
        for j in range(start, end):
            line = transcript[j]
            if j > start:
                same = transcript[j - 1]["speaker"] == line["speaker"]
                out.append(Pause(SAME_SPEAKER_GAP if same else TURN_GAPS[j % len(TURN_GAPS)]))
            out.append(Line(line["text"].strip(), voice[line["speaker"]], how[line["speaker"]]))
    out += [Pause(2.0), narrator(f"That is the end of Part {part}. You now have half a minute to check your "
                                 f"answers to Part {part}."), Pause(wait(CHECK))]
    if part == 4:
        out.append(narrator("That is the end of the Listening test."))
    return out


def load_listening(set_dir: Path) -> dict[int, dict]:
    components = json.loads((set_dir / "components.json").read_text(encoding="utf-8"))
    return {int(n): spec for n, spec in components["listening"].items()}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--set-dir", type=Path, required=True, help="a built set folder (components.json)")
    parser.add_argument("--parts", type=int, nargs="*", default=[1, 2, 3, 4])
    parser.add_argument("--preview", action="store_true", help="two-second silences, to audition the voices")
    parser.add_argument("--dry-run", action="store_true", help="print the script, record nothing")
    parser.add_argument("--engine", choices=ENGINES, default="kokoro", help="who speaks the lines")
    args = parser.parse_args()

    listening = load_listening(args.set_dir)
    ranges = numbering(listening)
    speaker = engine(args.engine)
    for part in args.parts:
        items = script(part, listening[part], ranges[part], args.set_dir.name, speaker, short=args.preview)
        if args.dry_run:
            print(f"== Part {part}")
            for item in items:
                print(f"   [pause {item.seconds:g}s]" if isinstance(item, Pause)
                      else f"   {item.voice:8} {item.text[:90]}")
            continue
        name = f"part-{part}{'-preview' if args.preview else ''}.mp3"
        seconds = render(items, speaker, args.set_dir / "audio" / name)
        print(f"Part {part}: audio/{name} ({seconds / 60:.1f} min)")
    if not args.dry_run:
        print(f"{speaker.requests} line(s) recorded, {speaker.cached} from the cache")


if __name__ == "__main__":
    main()
