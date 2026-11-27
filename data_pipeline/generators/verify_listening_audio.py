#!/usr/bin/env python3
"""Listen back to a recorded IELTS Listening paper and check that every answer can be heard.

A speech engine can stumble over a name, a number or an unusual word; if it does so on an answer,
the question cannot be answered. This transcribes each audio/part-N.mp3 with Whisper (on this
machine) and checks, for every question, that its evidence was said and that one of its accepted
answers can be heard, and for every line of the transcript that it was said recognisably.

    python data_pipeline/generators/verify_listening_audio.py --set-dir data_pipeline/output/ielts/<set>

Writes audio/verify.json and prints the problems; exits 1 if any answer could not be heard.
Needs `pip install openai-whisper rapidfuzz` (torch with CUDA makes it fast).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "generators"))

from record_ielts_listening import first_evidence, load_listening, numbering  # noqa: E402

EVIDENCE_SCORE = 85  # how closely (0-100) the heard words must match the evidence quote
LINE_SCORE = 80  # and each transcript line
CLOSE_SCORE = 75  # how close (0-100) a differently written answer must be to count as heard
WINDOW = 250  # characters either side of the evidence in which the answer must be heard
NUMBER_WORDS = {
    "zero": "0", "oh": "0", "one": "1", "two": "2", "three": "3", "four": "4", "five": "5", "six": "6",
    "seven": "7", "eight": "8", "nine": "9",
}


def words(text: str) -> str:
    """Lower-case words only, so punctuation and spelling style do not count as differences."""
    return " ".join(re.findall(r"[a-z0-9']+", text.lower().replace("-", " ")))


def squashed(text: str) -> str:
    """Letters and digits only, so "B-R-O-O-K", "B R O O K" and "Brook" all read "brook"."""
    text = text.lower()
    text = re.sub(r"\bdouble ([a-z0-9])\b", r"\1\1", text)
    return re.sub(r"[^a-z0-9]", "", text)


def heard(answer: str, transcript: str) -> bool:
    target = squashed(answer)
    if not target or target in squashed(transcript):
        return True
    # A spelt-out number ("four eight seven two") against the digits ("4872").
    digits = "".join(NUMBER_WORDS.get(w, w) for w in words(transcript).split())
    if target in squashed(digits):
        return True
    # Written one way, heard the other ("£950" / "nine hundred fifty pounds"): the same sounds.
    return sound(words(answer)) in sound(words(transcript))


@lru_cache(maxsize=1)
def pronouncer():
    try:
        from misaki import en
    except ImportError:
        return None
    return en.G2P(trf=False, british=True, fallback=None)


@lru_cache(maxsize=None)
def sound(text: str) -> str:
    """How the words are pronounced (British), so homophones and "950" / "nine hundred and fifty"
    compare equal; the spelling itself when the pronouncer is not installed."""
    g2p = pronouncer()
    if g2p is None or not text.strip():
        return text
    return re.sub(r"[ˈˌ\s,.]|and", "", g2p(text)[0] or text)


def similarity(said: str, heard: str) -> float:
    """How well (0-100) the words said appear in what was heard, by spelling or by sound."""
    from rapidfuzz import fuzz

    return max(fuzz.partial_ratio(words(said), heard), fuzz.partial_ratio(sound(words(said)), sound(heard)))


def sounds_close(answer: str, transcript: str) -> bool:
    """Whisper wrote it differently ("tenner" for "tenor") but it sounds the same or nearly so."""
    from rapidfuzz import fuzz

    target = words(answer)
    heard_words = words(transcript).split()
    size = max(1, len(target.split()))
    # Numbers can take more words to say than to write ("950": "nine hundred fifty pounds").
    grams = {" ".join(heard_words[i:i + n]) for n in range(size, size + 5) for i in range(len(heard_words))}
    return any(fuzz.ratio(target, gram) >= CLOSE_SCORE or fuzz.ratio(sound(target), sound(gram)) >= CLOSE_SCORE
               for gram in grams)


def pieces(audio, rate: int = 16_000, quiet: float = 0.01, gap: float = 0.8, longest: float = 25.0) -> list:
    """The audio cut at its silences. Whisper often stops at a long pause and never hears what comes
    after it (the options after a Part 2 question, the last line before the time to check), so each
    stretch of speech is heard on its own."""
    import numpy as np

    window = rate // 20
    loud = [float(np.abs(audio[i:i + window]).max()) > quiet for i in range(0, len(audio), window)]
    out, start, silent = [], None, 0
    for k, is_loud in enumerate(loud + [False] * int(gap * 20 + 1)):
        if is_loud:
            start = k if start is None else start
            silent = 0
            if (k - start) / 20 >= longest:  # a long stretch with no pause: cut it anyway
                out.append(audio[start * window:(k + 1) * window])
                start = None
        elif start is not None:
            silent += 1
            if silent / 20 >= gap:
                out.append(audio[start * window:(k - silent + 2) * window])
                start, silent = None, 0
    return [piece for piece in out if len(piece) > rate // 4]


def transcribe(model, path: Path) -> str:
    import whisper

    texts = []
    for piece in pieces(whisper.load_audio(str(path))):
        result = model.transcribe(piece, language="en", fp16=model.device.type == "cuda",
                                  condition_on_previous_text=False)
        texts.append(result["text"].strip())
    return " ".join(texts)


def check_part(part: int, spec: dict, ranges: list[tuple[int, int]], text: str) -> dict:
    from rapidfuzz import fuzz

    plain = words(text)
    problems, number = [], ranges[0][0]
    for qset in spec["sets"]:
        for question in qset["questions"]:
            evidence = first_evidence(question)
            score, near = 100.0, plain
            if evidence:
                found = fuzz.partial_ratio_alignment(words(evidence), plain)
                score = found.score if found.score >= EVIDENCE_SCORE else similarity(evidence, plain)
                # The answer has to be heard where it is given, not just anywhere in the part.
                near = plain[max(0, found.dest_start - WINDOW): found.dest_end + WINDOW]
            answers = question.get("answers") if qset["kind"] == "gap" else None
            status = "heard"
            if answers is not None and not any(heard(a, near) for a in answers):
                status = "close" if any(sounds_close(a, near) for a in answers) else "missing"
            if score < EVIDENCE_SCORE or status != "heard":
                problems.append({"question": number, "evidence": evidence, "score": round(score),
                                 "answers": answers, "answer": status})
            number += len(question.get("answers", [])) if qset["kind"] == "multi" else 1
    lines = []
    for line in spec["transcript"]:
        score = fuzz.partial_ratio(words(line["text"]), plain)
        if score < LINE_SCORE:
            score = similarity(line["text"], plain)
        if score < LINE_SCORE:
            lines.append({"speaker": line["speaker"], "text": line["text"], "score": round(score)})
    return {"part": part, "questions": problems, "lines": lines, "heard": text}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--set-dir", type=Path, required=True)
    parser.add_argument("--parts", type=int, nargs="*", default=[1, 2, 3, 4])
    parser.add_argument("--model", default="small.en", help="Whisper model (small.en fits a 4 GB GPU)")
    parser.add_argument("--reuse", action="store_true", help="check again what was heard last time")
    args = parser.parse_args()

    listening = load_listening(args.set_dir)
    ranges = numbering(listening)
    out = args.set_dir / "audio" / "verify.json"
    before = {r["part"]: r["heard"] for r in json.loads(out.read_text(encoding="utf-8"))} if args.reuse else {}
    model = None
    report, failed = [], False
    for part in args.parts:
        audio = args.set_dir / "audio" / f"part-{part}.mp3"
        if part in before:
            text = before[part]
        elif not audio.is_file():
            raise SystemExit(f"{audio} is missing; record it first")
        else:
            if model is None:
                import whisper

                model = whisper.load_model(args.model)
            text = transcribe(model, audio)
        result = check_part(part, listening[part], ranges[part], text)
        report.append(result)
        for q in result["questions"]:
            failed |= q["answer"] == "missing"
            what = {"missing": "answer NOT heard", "close": "answer heard differently, listen"}.get(
                q["answer"], f"evidence heard at {q['score']}%")
            print(f"Part {part} Q{q['question']}: {what} - {q['answers'] or ''} \"{q['evidence'][:70]}\"")
        for line in result["lines"]:
            print(f"Part {part} line ({line['score']}%): {line['speaker']}: {line['text'][:80]}")
        print(f"Part {part}: {len(result['questions'])} question(s), {len(result['lines'])} line(s) to listen to")
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False),
                                                        encoding="utf-8")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
