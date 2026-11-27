#!/usr/bin/env python3
"""Re-record TOEIC Listening audio with natural voices, and replace the files in storage.

The published TOEIC papers keep each group's transcript in question_sets.metadata.transcript, and
its recording at question_sets.audio_object_key. This script works from an export of those two
columns, so it never talks to the database itself:

    [{"key": "toeic/listening/audio/grp_x.mp3", "part": 3, "transcript": "W: ...\\nM: ..."}, ...]

    python data_pipeline/generators/record_toeic_listening.py record groups.json [--only 5] [--preview]
    python data_pipeline/generators/record_toeic_listening.py verify groups.json   (Whisper listens back)
    python data_pipeline/generators/record_toeic_listening.py upload groups.json --bucket exams

`record` writes data_pipeline/output/audio/toeic-voiced/<file>.mp3 (git-ignored), with Kokoro on this
machine by default or `--engine openai`. `upload` first
downloads every file it is about to replace into output/audio/toeic-backup/, then overwrites the
object at the same key, so the papers pick up the new audio without any database change and the
old recording can be put back with `upload --restore`.

How each part is read, as on the real test: Part 1 one voice reading statements (A)-(D) with a
pause after each; Part 2 one voice asking, another answering (A)-(C); Part 3 the speakers of the
conversation (W/M lines); Part 4 one speaker. Accents rotate between American, British, Canadian
and Australian, as the test does.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "generators"))

from speech import ENGINES, Engine, Line, Pause, engine, instructions, render  # noqa: E402

OUT = ROOT / "output" / "audio" / "toeic-voiced"
BACKUP = ROOT / "output" / "audio" / "toeic-backup"
VERIFY = ROOT / "output" / "audio" / "toeic-verify.json"
ACCENTS = ("US", "UK", "CA", "AU")
OPTION = re.compile(r"\(([A-D])\)\s*")
SPEAKER = re.compile(r"^(W\d?|M\d?)\s*:\s*(.*)$")
DELIVERY = {
    1: "Read clearly and neutrally at a measured pace, like a professional English test recording.",
    2: "Speak naturally, as in a short everyday exchange at work.",
    3: "A natural, relaxed workplace conversation, reacting to the other speaker.",
    4: "A clear, natural spoken announcement or talk to listeners, not a reading.",
}


def seed(key: str) -> int:
    return int(hashlib.sha256(key.encode()).hexdigest(), 16)


def voice_for(speaker: Engine, gender: str, accent: str, n: int, taken: set[str]) -> str:
    """A voice for this gender and accent that no one else in the group has.

    Groups rotate between the best voices of the pool (n picks which); the others are only used
    when those are taken.
    """
    pool = [v for v in speaker.pool(gender, accent) if v != speaker.narrator] or speaker.pool(gender, accent)
    top = max(1, min(getattr(speaker, "varied", len(pool)), len(pool)))
    start = n % top
    ordered = pool[start:top] + pool[:start] + pool[top:]
    return next((v for v in ordered if v not in taken), ordered[0])


def lettered(letter: str, words: str, say) -> list:
    """An option as the test reads it, "(A) ...". The letter stays in the same line as the option:
    said on its own, a letter often comes out as another word ("Hey", "seen")."""
    return [say(f"({letter}) {words}")]


def options(text: str) -> tuple[str, list[tuple[str, str]]]:
    """Text before (A), and each lettered option."""
    pieces = OPTION.split(" ".join(text.split()))
    lead, rest = pieces[0].strip(), pieces[1:]
    return lead, [(rest[i], rest[i + 1].strip()) for i in range(0, len(rest) - 1, 2)]


def conversation(text: str) -> list[tuple[str, str]]:
    """(speaker tag, words) per turn; indented continuation lines belong to the turn above."""
    turns: list[tuple[str, str]] = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        match = SPEAKER.match(line)
        if match:
            turns.append((match.group(1), match.group(2)))
        elif turns:
            turns[-1] = (turns[-1][0], f"{turns[-1][1]} {line}")
        else:
            turns.append(("M", line))
    return [(tag, " ".join(words.split())) for tag, words in turns if words.strip()]


def script(group: dict, speaker: Engine, short: bool = False) -> list:
    """The lines and pauses of one group's recording."""
    part, text, key = int(group["part"]), group["transcript"], group["key"]
    n = seed(key)
    accents = [ACCENTS[(n + i) % len(ACCENTS)] for i in range(4)]
    gap = (lambda s: min(s, 1.0)) if short else (lambda s: s)
    first_gender = "female" if n % 2 else "male"
    other_gender = "male" if first_gender == "female" else "female"
    role = "You are a speaker on a recording for an international English test for the workplace."
    out: list = []
    cast: dict[int, str] = {}

    def line(words: str, gender: str, i: int, accent: str) -> Line:
        if i not in cast:
            cast[i] = voice_for(speaker, gender, accent, n // 7 + i, set(cast.values()))
        return Line(words, cast[i], instructions(accent, role, DELIVERY[part]))

    if part == 1:
        _, opts = options(text)
        for i, (letter, words) in enumerate(opts):
            if i:
                out.append(Pause(gap(1.6)))
            out += lettered(letter, words, lambda w: line(w, first_gender, 0, accents[0]))
    elif part == 2:
        question, opts = options(text)
        out.append(line(question, first_gender, 0, accents[0]))
        out.append(Pause(gap(1.4)))
        for i, (letter, words) in enumerate(opts):
            if i:
                out.append(Pause(gap(1.4)))
            out += lettered(letter, words, lambda w: line(w, other_gender, 1, accents[1]))
    elif part == 3:
        turns = conversation(text)
        tags = list(dict.fromkeys(tag for tag, _ in turns))
        people = {tag: (("female" if tag.startswith("W") else "male"), i, accents[i % 4])
                  for i, tag in enumerate(tags)}
        for i, (tag, words) in enumerate(turns):
            if i:
                out.append(Pause(0.35 + 0.1 * ((n + i) % 3)))
            gender, slot, accent = people[tag]
            out.append(line(words, gender, slot, accent))
    else:
        paragraphs = [" ".join(p.split()) for p in re.split(r"\n\s*\n", text) if p.strip()]
        for i, words in enumerate(paragraphs):
            if i:
                out.append(Pause(0.7))
            out.append(line(words, first_gender, 0, accents[0]))
    return out


def local_path(key: str, root: Path = OUT) -> Path:
    return root / Path(key).name


def record(groups: list[dict], only: int | None, preview: bool, engine_name: str) -> None:
    speaker = engine(engine_name)
    for i, group in enumerate(groups[:only] if only else groups, 1):
        out = local_path(group["key"])
        if out.is_file() and not preview:
            continue
        seconds = render(script(group, speaker, short=preview), speaker, out, bitrate="64k")
        print(f"[{i}/{len(groups)}] part {group['part']} {out.name} {seconds:.0f}s")
    print(f"{speaker.requests} line(s) recorded, {speaker.cached} from the cache")


def verify(groups: list[dict], only: int | None, model_name: str = "small.en") -> list[dict]:
    """Transcribe each recording with Whisper and list the lines that were not said recognisably."""
    import whisper
    from rapidfuzz import fuzz
    from verify_listening_audio import similarity, transcribe

    def words(text: str) -> str:
        return " ".join(re.findall(r"[a-z0-9']+", text.lower().replace("-", " ")))

    model = whisper.load_model(model_name)
    voices = engine("kokoro")  # the words are the same whichever engine spoke them
    problems = []
    for i, group in enumerate(groups[:only] if only else groups, 1):
        path = local_path(group["key"])
        if not path.is_file():
            raise SystemExit(f"{path} is missing; record it first")
        heard = words(transcribe(model, path))
        said = [item.text for item in script(group, voices) if isinstance(item, Line)]
        weak = [(round(score), text) for text in said if len(words(text).split()) > 1
                for score in [max(fuzz.partial_ratio(words(text), heard), similarity(text, heard))] if score < 80]
        if weak:
            problems.append({"key": group["key"], "part": group["part"], "lines": weak, "heard": heard})
            print(f"[{i}] part {group['part']} {path.name}: " + "; ".join(f"{s}% {t[:60]}" for s, t in weak))
    print(f"{len(problems)} of {len(groups[:only] if only else groups)} recording(s) to listen to")
    VERIFY.write_text(json.dumps(problems, indent=2, ensure_ascii=False), encoding="utf-8")
    return problems


def s3_client():
    import boto3

    env = ROOT.parent / ".env"
    values = {}
    for raw in env.read_text(encoding="utf-8").splitlines():
        if "=" in raw and not raw.startswith("#"):
            k, v = raw.split("=", 1)
            values[k.strip()] = v.strip().strip('"')

    def get(name: str) -> str:
        return os.environ.get(name) or values.get(name, "")

    return boto3.client("s3", endpoint_url=get("S3_ENDPOINT"), region_name=get("S3_REGION"),
                        aws_access_key_id=get("S3_ACCESS_KEY"), aws_secret_access_key=get("S3_SECRET_KEY"))


def upload(groups: list[dict], bucket: str, restore: bool) -> None:
    s3 = s3_client()
    source = BACKUP if restore else OUT
    missing = [g["key"] for g in groups if not local_path(g["key"], source).is_file()]
    if missing:
        raise SystemExit(f"{len(missing)} file(s) not in {source}, e.g. {missing[0]}; nothing uploaded")
    BACKUP.mkdir(parents=True, exist_ok=True)
    for i, group in enumerate(groups, 1):
        key = group["key"]
        backup = local_path(key, BACKUP)
        if not restore and not backup.is_file():
            s3.download_file(bucket, key, str(backup))
        s3.upload_file(str(local_path(key, source)), bucket, key,
                       ExtraArgs={"ContentType": "audio/mpeg", "CacheControl": "max-age=300"})
        if i % 50 == 0 or i == len(groups):
            print(f"{i}/{len(groups)} uploaded")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("action", choices=["record", "verify", "upload", "dry-run"])
    parser.add_argument("groups", type=Path, help="export of key, part and transcript per group")
    parser.add_argument("--only", type=int, help="record just the first N groups")
    parser.add_argument("--preview", action="store_true", help="short pauses, files overwritten")
    parser.add_argument("--bucket", default="exams")
    parser.add_argument("--restore", action="store_true", help="upload the backed-up originals instead")
    parser.add_argument("--engine", choices=ENGINES, default="kokoro", help="who speaks the lines")
    args = parser.parse_args()
    groups = json.loads(args.groups.read_text(encoding="utf-8"))
    if args.action == "dry-run":
        for group in groups[: args.only or 3]:
            print(f"== part {group['part']} {group['key']}")
            for item in script(group, engine(args.engine)):
                print(f"   [pause {item.seconds:g}s]" if isinstance(item, Pause) else f"   {item.voice:8} {item.text[:90]}")
    elif args.action == "record":
        record(groups, args.only, args.preview, args.engine)
    elif args.action == "verify":
        verify(groups, args.only)
    else:
        upload(groups, args.bucket, args.restore)


if __name__ == "__main__":
    main()
