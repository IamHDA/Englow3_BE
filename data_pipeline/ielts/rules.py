"""What every AI-written IELTS component must satisfy before it is built into a paper.

Each check returns plain-English problems; an empty list means the component is accepted. The
problems go back to the model verbatim, so they say what to change, not just what is wrong.

Compact question-set shapes, shared by Listening and Reading:

    {"kind": "gap", "instruction": str, "content": "text with {{1}} ... {{k}}",
     "word_limit": int, "questions": [{"prompt": str, "answers": [str, ...]}]}
    {"kind": "single", "instruction": str, "questions": [{"prompt": str, "options": [str], "answer": int}]}
    {"kind": "multi", "instruction": str, "questions": [{"prompt": str, "options": [5 x str], "answers": [i, j]}]}
    {"kind": "matching", "instruction": str, "list": [{"label": str, "text": str}],
     "questions": [{"prompt": str, "answer": label}]}
    {"kind": "tfng" | "ynng", "instruction": str, "questions": [{"statement": str, "answer": str}]}
"""
from __future__ import annotations

import re
from collections import Counter

GAP = re.compile(r"\{\{(\d+)\}\}")
TFNG = ("TRUE", "FALSE", "NOT GIVEN")
YNNG = ("YES", "NO", "NOT GIVEN")
ACCENTS = ("UK", "US", "AU", "CA")
GENDERS = ("female", "male")

LISTENING_MARKS = {1: 10, 2: 10, 3: 10, 4: 10}
READING_MARKS = {1: 13, 2: 13, 3: 14}
MIN_PASSAGE_WORDS = 600
MAX_PASSAGE_WORDS = 1000


def normalise(text: str) -> str:
    """How answers are compared with the text: case, punctuation and spacing folded."""
    return " ".join(re.sub(r"[^\w£$%.\s]", " ", text.lower()).split())


def words(text: str) -> int:
    return len(text.split())


def set_marks(qset: dict) -> int:
    """Marks a set is worth: one per question, except a choose-N question, one per letter."""
    if qset.get("kind") == "multi":
        return sum(len(q.get("answers", [])) for q in qset.get("questions", []))
    return len(qset.get("questions", []))


def check_set(qset: dict, where: str, source: str, single_options: int) -> list[str]:
    """One question set; `source` is the passage or transcript its answers must come from."""
    problems: list[str] = []
    kind = qset.get("kind")
    questions = qset.get("questions") or []
    if not str(qset.get("instruction", "")).strip():
        problems.append(f"{where}: give the set an instruction, as the paper prints it.")
    if not questions:
        return problems + [f"{where}: the set has no questions."]
    text = normalise(source)

    if kind == "gap":
        limit = qset.get("word_limit")
        if not isinstance(limit, int) or not 1 <= limit <= 3:
            problems.append(f"{where}: word_limit must be 1, 2 or 3 (ONE WORD ... THREE WORDS).")
            limit = 3
        found = [int(n) for n in GAP.findall(qset.get("content", ""))]
        expected = list(range(1, len(questions) + 1))
        if sorted(found) != expected:
            problems.append(
                f"{where}: content must contain each gap {{{{1}}}} to {{{{{len(questions)}}}}} exactly once; "
                f"found {found or 'none'}."
            )
        elif found != expected:
            problems.append(f"{where}: gaps must appear in order {{{{1}}}}, {{{{2}}}}, ... in the content.")
        for i, q in enumerate(questions, 1):
            answers = [a for a in q.get("answers", []) if str(a).strip()]
            if not answers:
                problems.append(f"{where}, gap {i}: give at least one accepted answer.")
                continue
            if not any(normalise(a) and normalise(a) in text for a in answers):
                problems.append(
                    f"{where}, gap {i}: the answer {answers[0]!r} does not appear in the recording or passage; "
                    "the exact words must be there."
                )
            for a in answers:
                if words(str(a)) > limit:
                    problems.append(f"{where}, gap {i}: {a!r} is longer than the {limit}-word limit.")
            if not str(q.get("prompt", "")).strip():
                problems.append(f"{where}, gap {i}: give a short prompt naming what the gap asks for.")
    elif kind == "single":
        for i, q in enumerate(questions, 1):
            options = q.get("options") or []
            if len(options) != single_options:
                problems.append(f"{where}, question {i}: give exactly {single_options} options.")
            if not isinstance(q.get("answer"), int) or not 0 <= q["answer"] < len(options):
                problems.append(f"{where}, question {i}: answer must be the index of one of its options.")
            if len({normalise(o) for o in options}) != len(options):
                problems.append(f"{where}, question {i}: two options say the same thing.")
    elif kind == "multi":
        for i, q in enumerate(questions, 1):
            options = q.get("options") or []
            answers = q.get("answers") or []
            if len(options) != 5:
                problems.append(f"{where}, question {i}: a choose-TWO question has exactly 5 options (A-E).")
            if len(answers) != 2 or len(set(answers)) != 2 or not all(
                isinstance(a, int) and 0 <= a < len(options) for a in answers
            ):
                problems.append(f"{where}, question {i}: answers must be two different option indexes.")
    elif kind == "matching":
        entries = qset.get("list") or []
        labels = [str(e.get("label", "")).strip() for e in entries]
        if len(entries) < 3:
            problems.append(f"{where}: the list to match from needs at least 3 entries.")
        if any(not label or len(label) > 10 for label in labels) or len(set(labels)) != len(labels):
            problems.append(f"{where}: every list entry needs its own short label (A, B, C or i, ii, iii).")
        if len(entries) <= len(questions) and len({q.get("answer") for q in questions}) == len(questions):
            problems.append(f"{where}: give more list entries than questions, so the last answer is not a giveaway.")
        for i, q in enumerate(questions, 1):
            if q.get("answer") not in labels:
                problems.append(f"{where}, question {i}: answer {q.get('answer')!r} is not a label in the list.")
    elif kind in ("tfng", "ynng"):
        allowed = TFNG if kind == "tfng" else YNNG
        answers = [q.get("answer") for q in questions]
        if any(a not in allowed for a in answers):
            problems.append(f"{where}: every answer must be one of {', '.join(allowed)}.")
        elif len(questions) >= 4 and len(set(answers)) < 3:
            problems.append(f"{where}: use each of {', '.join(allowed)} at least once.")
        if any(not str(q.get("statement", "")).strip() for q in questions):
            problems.append(f"{where}: every question needs its statement.")
    else:
        problems.append(f"{where}: unknown kind {kind!r}; use gap, single, multi, matching, tfng or ynng.")
    return problems


def check_listening_part(part: int, spec: dict) -> list[str]:
    where = f"Listening Part {part}"
    problems: list[str] = []
    speakers = spec.get("speakers") or []
    names = [s.get("name") for s in speakers]
    if part in (1, 3) and not 2 <= len(speakers) <= (2 if part == 1 else 4):
        problems.append(f"{where} is a conversation: give {'2' if part == 1 else '2 to 4'} speakers.")
    if part in (2, 4) and len(speakers) != 1:
        problems.append(f"{where} is a monologue: give exactly 1 speaker.")
    for s in speakers:
        if s.get("accent") not in ACCENTS or s.get("gender") not in GENDERS:
            problems.append(f"{where}: speaker {s.get('name')!r} needs accent in {ACCENTS} and gender in {GENDERS}.")
    transcript = spec.get("transcript") or []
    if len({n for n in names if n}) != len(names):
        problems.append(f"{where}: speaker names must be different.")
    for line in transcript:
        if line.get("speaker") not in names:
            problems.append(f"{where}: transcript line by {line.get('speaker')!r}, who is not in speakers.")
            break
    spoken = " ".join(str(line.get("text", "")) for line in transcript)
    if words(spoken) < 350:
        problems.append(f"{where}: the recording is only {words(spoken)} words; write at least 350.")
    sets = spec.get("sets") or []
    for i, qset in enumerate(sets, 1):
        problems += check_set(qset, f"{where}, set {i}", spoken, single_options=3)
    marks = sum(set_marks(s) for s in sets)
    if marks != LISTENING_MARKS[part]:
        problems.append(f"{where}: the sets are worth {marks} marks; they must total {LISTENING_MARKS[part]}.")
    if any(s.get("kind") in ("tfng", "ynng") for s in sets):
        problems.append(f"{where}: True/False/Not Given belongs to Reading, not Listening.")
    return problems


def check_reading_passage(passage: int, spec: dict, min_words: int = MIN_PASSAGE_WORDS) -> list[str]:
    where = f"Reading Passage {passage}"
    problems: list[str] = []
    paragraphs = [p for p in spec.get("paragraphs") or [] if str(p).strip()]
    text = " ".join(paragraphs)
    count = words(text)
    if not str(spec.get("title", "")).strip():
        problems.append(f"{where}: give the passage a title.")
    if not min_words <= count <= MAX_PASSAGE_WORDS:
        problems.append(f"{where}: the passage is {count} words; it must be {min_words}-{MAX_PASSAGE_WORDS}.")
    sets = spec.get("sets") or []
    labels = [chr(65 + i) for i in range(len(paragraphs))]
    for i, qset in enumerate(sets, 1):
        problems += check_set(qset, f"{where}, set {i}", text, single_options=4)
        if qset.get("kind") == "matching" and any(
            str(q.get("prompt", "")).startswith("Paragraph") for q in qset.get("questions", [])
        ):
            if not spec.get("label_paragraphs"):
                problems.append(f"{where}, set {i}: matching headings to paragraphs needs label_paragraphs: true.")
            for q in qset.get("questions", []):
                letter = str(q.get("prompt", "")).replace("Paragraph", "").strip()
                if letter not in labels:
                    problems.append(f"{where}, set {i}: {q.get('prompt')!r} names no paragraph of the passage.")
    marks = sum(set_marks(s) for s in sets)
    if marks != READING_MARKS[passage]:
        problems.append(f"{where}: the sets are worth {marks} marks; they must total {READING_MARKS[passage]}.")
    return problems


def check_paper_mix(listening: dict[int, dict], reading: dict[int, dict]) -> list[str]:
    """Across the whole paper: every question type a candidate should meet appears somewhere."""
    problems: list[str] = []
    l_kinds = Counter(s.get("kind") for spec in listening.values() for s in spec.get("sets", []))
    r_kinds = Counter(s.get("kind") for spec in reading.values() for s in spec.get("sets", []))
    for kind in ("gap", "single", "multi", "matching"):
        if not l_kinds[kind]:
            problems.append(f"Listening: no {kind} set anywhere; include at least one.")
    for kind in ("gap", "matching", "single"):
        if not r_kinds[kind]:
            problems.append(f"Reading: no {kind} set anywhere; include at least one.")
    if not (r_kinds["tfng"] or r_kinds["ynng"]):
        problems.append("Reading: include a True/False/Not Given or Yes/No/Not Given set.")
    return problems


def check_task1(spec: dict) -> list[str]:
    problems: list[str] = []
    chart = spec.get("chart") or {}
    categories = chart.get("categories") or []
    series = chart.get("series") or []
    if chart.get("type") not in ("bar", "line"):
        problems.append("Writing Task 1: chart.type must be bar or line.")
    if not 3 <= len(categories) <= 8:
        problems.append("Writing Task 1: give 3 to 8 categories.")
    if not 1 <= len(series) <= 4:
        problems.append("Writing Task 1: give 1 to 4 series.")
    for s in series:
        values = s.get("values") or []
        if len(values) != len(categories) or not all(isinstance(v, (int, float)) for v in values):
            problems.append(f"Writing Task 1: series {s.get('name')!r} needs one number per category.")
    if not str(chart.get("title", "")).strip() or not str(chart.get("unit", "")).strip():
        problems.append("Writing Task 1: give chart.title and chart.unit (for example %, tonnes, millions).")
    if not str(spec.get("description", "")).strip():
        problems.append("Writing Task 1: give a one-sentence description of what the chart shows.")
    if len(spec.get("key_features") or []) < 3:
        problems.append("Writing Task 1: list at least 3 key_features a strong answer would report.")
    return problems


def check_task2(spec: dict) -> list[str]:
    prompt = str(spec.get("prompt", ""))
    return [] if words(prompt) >= 25 else ["Writing Task 2: write the full essay question (at least 25 words)."]


def check_speaking(spec: dict) -> list[str]:
    problems: list[str] = []
    if not 4 <= len(spec.get("part1") or []) <= 6:
        problems.append("Speaking Part 1: give 4 to 6 questions.")
    card = spec.get("part2") or {}
    if not str(card.get("cue", "")).strip() or not 3 <= len(card.get("points") or []) <= 4:
        problems.append("Speaking Part 2: give a cue (Describe ...) and 3 to 4 points to cover.")
    if not str(card.get("explain", "")).strip():
        problems.append("Speaking Part 2: give the final 'and explain ...' line.")
    if not 4 <= len(spec.get("part3") or []) <= 5:
        problems.append("Speaking Part 3: give 4 to 5 discussion questions.")
    return problems
