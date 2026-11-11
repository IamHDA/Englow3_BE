"""What every AI-written IELTS component must satisfy before it is built into a paper.

Each check returns plain-English problems; an empty list means the component is accepted. The
problems go back to the model verbatim, so they say what to change, not just what is wrong.

Compact question-set shapes, shared by Listening and Reading:

    {"kind": "gap", "instruction": str, "content": "text with {{1}} ... {{k}}",
     "word_limit": int, "questions": [{"prompt": str, "answers": [str, ...]}]}
    {"kind": "single", "instruction": str,
     "questions": [{"prompt": str, "options": [str], "answer": int}]}
    {"kind": "multi", "instruction": str,
     "questions": [{"prompt": str, "options": [5 x str], "answers": [i, j]}]}
    {"kind": "matching", "instruction": str, "list": [{"label": str, "text": str}],
     "questions": [{"prompt": str, "answer": label}]}
    {"kind": "tfng" | "ynng", "instruction": str, "questions": [{"statement": str, "answer": str}]}

Every question also carries "evidence": the exact words of the recording or passage that settle
it (a list of two for a choose-TWO question). A Listening gap or single-choice question may carry
"trap": the exact words in the recording that tempt a wrong answer. `check_difficulty` uses both:
a paper whose questions can be answered by matching words is a coursebook exercise, not a test.
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
MIN_PASSAGE_WORDS = 700
MAX_PASSAGE_WORDS = 1000
# A real part runs five to six minutes: shorter and there is no room for anything but the answers.
MIN_RECORDING_WORDS = {1: 550, 2: 550, 3: 650, 4: 700}
MAX_RECORDING_WORDS = 1100  # four parts still fit the 30 minutes
MAX_LINE_WORDS = 300  # one transcript line is one request to the voice service
# How many consecutive words of the text a question may repeat before it is "word for word".
COPIED_HEARD_OPTION = 4  # spoken options are short
COPIED_READ_OPTION = 5
COPIED_NOTES = 5
COPIED_STATEMENT = 6
TRAP_SHARE = 0.4  # of a Listening part's questions
MIN_CHART_FIGURES = 8

WORD = re.compile(r"[a-z0-9£$%]+(?:'[a-z]+)?")
ELLIPSIS = re.compile(r"\.{3,}|…")
STOP = frozenset(
    "a an the of to in on at for by with from into and or but as is are was were be been being it "
    "its this that these those there their they them he she we you i his her our your not no do "
    "does did has have had will would can could should may might than then so if which who what "
    "when where how all any some more most very also about up out over".split()
)


def normalise(text: str) -> str:
    """How answers are compared with the text: case, punctuation and spacing folded."""
    return " ".join(re.sub(r"[^\w£$%.\s]", " ", text.lower()).split())


def words(text: str) -> int:
    return len(text.split())


def tokens(text: str) -> list[str]:
    """Words only, so a quote matches the text whatever its case, punctuation or line breaks."""
    return WORD.findall(str(text).lower().replace("\u2019", "'"))


class Source:
    """A recording or passage, read once, to find quotes in and to spot wording copied from."""

    def __init__(self, text: str) -> None:
        self._joined = f" {' '.join(tokens(text))} "

    def find(self, quote: str, start: int = 0) -> int:
        """Where the quote begins, at or after `start`; -1 when its words are not there.

        A quote may skip words with an ellipsis: every piece must then follow the one before.
        """
        first, at = -1, start
        for piece in ELLIPSIS.split(str(quote)):
            needle = " ".join(tokens(piece))
            if not needle:
                continue
            at = self._joined.find(f" {needle} ", at)
            if at < 0:
                return -1
            first = at if first < 0 else first
            at += len(needle)
        return first

    def has(self, run: list[str]) -> bool:
        return bool(run) and f" {' '.join(run)} " in self._joined

    def copied(self, phrase: str, length: int) -> str | None:
        """A run of `length` consecutive words the phrase shares with the text, if there is one.

        Runs of small words alone ("at the end of the") are in every text and do not count.
        """
        found = tokens(phrase)
        for i in range(len(found) - length + 1):
            run = found[i : i + length]
            if sum(word not in STOP for word in run) >= 2 and self.has(run):
                return " ".join(run)
        return None


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
                f"{where}: content must contain each gap {{{{1}}}} to "
                f"{{{{{len(questions)}}}}} exactly once; "
                f"found {found or 'none'}."
            )
        elif found != expected:
            problems.append(
                f"{where}: gaps must appear in order {{{{1}}}}, {{{{2}}}}, ... in the content."
            )
        for i, q in enumerate(questions, 1):
            answers = [a for a in q.get("answers", []) if str(a).strip()]
            if not answers:
                problems.append(f"{where}, gap {i}: give at least one accepted answer.")
                continue
            if not any(normalise(a) and normalise(a) in text for a in answers):
                problems.append(
                    f"{where}, gap {i}: the answer {answers[0]!r} does not appear "
                    "in the recording or passage; "
                    "the exact words must be there."
                )
            for a in answers:
                if words(str(a)) > limit:
                    problems.append(
                        f"{where}, gap {i}: {a!r} is longer than the {limit}-word limit."
                    )
            if not str(q.get("prompt", "")).strip():
                problems.append(
                    f"{where}, gap {i}: give a short prompt naming what the gap asks for."
                )
    elif kind == "single":
        for i, q in enumerate(questions, 1):
            options = q.get("options") or []
            if len(options) != single_options:
                problems.append(f"{where}, question {i}: give exactly {single_options} options.")
            if not isinstance(q.get("answer"), int) or not 0 <= q["answer"] < len(options):
                problems.append(
                    f"{where}, question {i}: answer must be the index of one of its options."
                )
            if len({normalise(o) for o in options}) != len(options):
                problems.append(f"{where}, question {i}: two options say the same thing.")
    elif kind == "multi":
        for i, q in enumerate(questions, 1):
            options = q.get("options") or []
            answers = q.get("answers") or []
            if len(options) != 5:
                problems.append(
                    f"{where}, question {i}: a choose-TWO question has exactly 5 options (A-E)."
                )
            if (
                len(answers) != 2
                or len(set(answers)) != 2
                or not all(isinstance(a, int) and 0 <= a < len(options) for a in answers)
            ):
                problems.append(
                    f"{where}, question {i}: answers must be two different option indexes."
                )
    elif kind == "matching":
        entries = qset.get("list") or []
        labels = [str(e.get("label", "")).strip() for e in entries]
        if len(entries) < 3:
            problems.append(f"{where}: the list to match from needs at least 3 entries.")
        if any(not label or len(label) > 10 for label in labels) or len(set(labels)) != len(labels):
            problems.append(
                f"{where}: every list entry needs its own short label (A, B, C or i, ii, iii)."
            )
        if len(entries) <= len(questions) and len({q.get("answer") for q in questions}) == len(
            questions
        ):
            problems.append(
                f"{where}: give more list entries than questions, "
                "so the last answer is not a giveaway."
            )
        for i, q in enumerate(questions, 1):
            if q.get("answer") not in labels:
                problems.append(
                    f"{where}, question {i}: answer {q.get('answer')!r} is not a label in the list."
                )
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
        problems.append(
            f"{where}: unknown kind {kind!r}; use gap, single, multi, matching, tfng or ynng."
        )
    return problems


def _quotes(value) -> list[str]:
    values = value if isinstance(value, list) else [value]
    return [str(v) for v in values if isinstance(v, str) and v.strip()]


def _is_headings(qset: dict) -> bool:
    """Matching headings to paragraphs: the whole paragraph is the evidence."""
    return qset.get("kind") == "matching" and any(
        str(q.get("prompt", "")).startswith("Paragraph") for q in qset.get("questions") or []
    )


def _around_gaps(content: str) -> list[tuple[str, str]]:
    """For each gap in order, the words of its own line before and after it."""
    pieces = GAP.split(content)  # text, number, text, number, ... text
    return [
        (pieces[i - 1].rsplit("\n", 1)[-1], pieces[i + 1].split("\n", 1)[0])
        for i in range(1, len(pieces), 2)
    ]


def _copied_notes(source: Source, before: str, after: str, answers: list[str]) -> str | None:
    """Words of the notes beside a gap that the text also has, word for word, if any."""
    lead, tail = tokens(before)[-8:], tokens(after)[:8]
    copied = source.copied(" ".join(lead), COPIED_NOTES) or source.copied(
        " ".join(tail), COPIED_NOTES
    )
    if copied:
        return copied
    # Shorter, but worse: the note runs straight into the answer exactly as the text does.
    for answer in answers:
        said = tokens(answer)
        for beside, run in ((lead[-3:], lead[-3:] + said), (tail[:3], said + tail[:3])):
            if (
                len(beside) == 3
                and sum(word not in STOP for word in beside) >= 2
                and source.has(run)
            ):
                return " ".join(run)
    return None


def check_difficulty(sets: list[dict], where: str, text: str, listening: bool) -> list[str]:
    """What keeps a component from being answered by matching words, checked against its text.

    Every question points at the words that settle it; the question itself does not repeat them;
    in a recording the questions come in the order they are answered and enough of them have a
    tempting wrong answer built in.
    """
    medium = "recording" if listening else "passage"
    source = Source(text)
    problems: list[str] = []
    reached = 0  # where the question before was answered
    slots = traps = 0
    singles: list[dict] = []
    option_run = COPIED_HEARD_OPTION if listening else COPIED_READ_OPTION
    reword = "reword it (synonyms, another word class, a different order)"

    for i, qset in enumerate(sets, 1):
        kind = qset.get("kind")
        questions = qset.get("questions") or []
        if _is_headings(qset):
            continue
        in_order = listening or kind in ("tfng", "ynng")
        if not listening:
            reached = 0
        around = _around_gaps(str(qset.get("content", ""))) if kind == "gap" else []
        entries = {str(e.get("label", "")).strip(): e for e in qset.get("list") or []}
        for j, q in enumerate(questions, 1):
            here = f"{where}, set {i}, {'gap' if kind == 'gap' else 'question'} {j}"
            options = q.get("options") or []
            answers = [str(a) for a in q.get("answers") or [] if str(a).strip()]

            quotes = _quotes(q.get("evidence"))
            if not quotes:
                problems.append(
                    f'{here}: give "evidence" - the exact words of the {medium} '
                    "that settle the answer."
                )
            missing = [quote for quote in quotes if source.find(quote) < 0]
            for quote in missing:
                problems.append(
                    f"{here}: the evidence {quote!r} is not in the {medium} word for word; "
                    "copy the exact words."
                )
            if quotes and not missing:
                said = Source(" ".join(quotes))
                if kind == "gap" and not any(said.has(tokens(a)) for a in answers):
                    problems.append(
                        f"{here}: the evidence must be the words of the {medium} "
                        "that contain the answer."
                    )
                if in_order:
                    at = source.find(quotes[0], reached)
                    if at < 0:
                        problems.append(
                            f"{here}: it is answered earlier in the {medium} than the question "
                            f"before it; questions follow the order of the {medium}."
                        )
                    else:
                        reached = at

            copied = None
            if kind == "gap" and j <= len(around):
                copied = _copied_notes(source, *around[j - 1], answers)
                what = "the notes beside the gap repeat"
            elif kind == "single" and isinstance(q.get("answer"), int):
                if 0 <= q["answer"] < len(options):
                    copied = source.copied(str(options[q["answer"]]), option_run)
                what = "the correct option repeats"
            elif kind == "multi":
                correct = [
                    options[a]
                    for a in q.get("answers") or []
                    if isinstance(a, int) and 0 <= a < len(options)
                ]
                copied = next(
                    (c for c in (source.copied(str(o), option_run) for o in correct) if c), None
                )
                what = "a correct option repeats"
            elif kind == "matching" and entries.get(str(q.get("answer"))):
                copied = source.copied(str(entries[q["answer"]].get("text", "")), option_run)
                what = "the correct list entry repeats"
            elif kind in ("tfng", "ynng"):
                copied = source.copied(str(q.get("statement", "")), COPIED_STATEMENT)
                what = "the statement repeats"
            if copied:
                problems.append(
                    f"{here}: {what} the {medium} word for word ({copied!r}); {reword} so that "
                    "matching words is not enough"
                    + (
                        " - only a wrong option may echo the text."
                        if kind in ("single", "multi", "matching")
                        else "."
                    )
                )

            if listening:
                slots += 1
                trap = str(q.get("trap") or "").strip()
                if trap and source.find(trap) < 0:
                    problems.append(
                        f"{here}: the trap {trap!r} is not in the recording word for word; "
                        "copy the exact words, or build the trap into the recording."
                    )
                elif trap and kind == "gap" and any(Source(trap).has(tokens(a)) for a in answers):
                    problems.append(
                        f"{here}: the trap must be the tempting WRONG words, not the answer."
                    )
                elif trap:
                    traps += 1
            if kind == "single" and len(options) > 1 and isinstance(q.get("answer"), int):
                singles.append(q)

    needed = int(slots * TRAP_SHARE)
    if traps < needed:
        problems.append(
            f"{where}: only {traps} of its {slots} questions have a trap. For at least {needed}, "
            "put a tempting wrong answer in the recording - a detail that is corrected, rejected "
            'or belongs to something else - and quote it in "trap".'
        )
    if len(singles) >= 3:
        lengths = [[len(str(o)) for o in q["options"]] for q in singles]
        longest = sum(
            0 <= q["answer"] < len(sizes)
            and sizes[q["answer"]] > max(s for k, s in enumerate(sizes) if k != q["answer"])
            for q, sizes in zip(singles, lengths, strict=True)
        )
        if longest * 2 > len(singles):
            problems.append(
                f"{where}: the correct option is the longest in {longest} of {len(singles)} "
                "multiple-choice questions; make the wrong options as long and as detailed."
            )
        if len({q["answer"] for q in singles}) == 1:
            problems.append(
                f"{where}: every multiple-choice answer is the same letter; vary its position."
            )
    return problems


def check_listening_part(part: int, spec: dict) -> list[str]:
    where = f"Listening Part {part}"
    problems: list[str] = []
    speakers = spec.get("speakers") or []
    names = [s.get("name") for s in speakers]
    if part in (1, 3) and not 2 <= len(speakers) <= (2 if part == 1 else 4):
        problems.append(
            f"{where} is a conversation: give {'2' if part == 1 else '2 to 4'} speakers."
        )
    if part in (2, 4) and len(speakers) != 1:
        problems.append(f"{where} is a monologue: give exactly 1 speaker.")
    for s in speakers:
        if s.get("accent") not in ACCENTS or s.get("gender") not in GENDERS:
            problems.append(
                f"{where}: speaker {s.get('name')!r} needs accent in {ACCENTS} "
                f"and gender in {GENDERS}."
            )
    transcript = spec.get("transcript") or []
    if len({n for n in names if n}) != len(names):
        problems.append(f"{where}: speaker names must be different.")
    for line in transcript:
        if line.get("speaker") not in names:
            problems.append(
                f"{where}: transcript line by {line.get('speaker')!r}, who is not in speakers."
            )
            break
    spoken = " ".join(str(line.get("text", "")) for line in transcript)
    if not MIN_RECORDING_WORDS[part] <= words(spoken) <= MAX_RECORDING_WORDS:
        problems.append(
            f"{where}: the recording is {words(spoken)} words; it must be "
            f"{MIN_RECORDING_WORDS[part]}-{MAX_RECORDING_WORDS}. Add talk that answers no "
            "question rather than stretching the answers out."
        )
    if any(words(str(line.get("text", ""))) > MAX_LINE_WORDS for line in transcript):
        problems.append(
            f"{where}: a transcript line is longer than {MAX_LINE_WORDS} words; split a long "
            "turn into several lines by the same speaker, one per paragraph."
        )
    sets = spec.get("sets") or []
    for i, qset in enumerate(sets, 1):
        problems += check_set(qset, f"{where}, set {i}", spoken, single_options=3)
    problems += check_difficulty(sets, where, spoken, listening=True)
    marks = sum(set_marks(s) for s in sets)
    if marks != LISTENING_MARKS[part]:
        problems.append(
            f"{where}: the sets are worth {marks} marks; they must total {LISTENING_MARKS[part]}."
        )
    if any(s.get("kind") in ("tfng", "ynng") for s in sets):
        problems.append(f"{where}: True/False/Not Given belongs to Reading, not Listening.")
    return problems


def check_reading_passage(
    passage: int, spec: dict, min_words: int = MIN_PASSAGE_WORDS
) -> list[str]:
    where = f"Reading Passage {passage}"
    problems: list[str] = []
    paragraphs = [p for p in spec.get("paragraphs") or [] if str(p).strip()]
    text = " ".join(paragraphs)
    count = words(text)
    if not str(spec.get("title", "")).strip():
        problems.append(f"{where}: give the passage a title.")
    if not min_words <= count <= MAX_PASSAGE_WORDS:
        problems.append(
            f"{where}: the passage is {count} words; it must be {min_words}-{MAX_PASSAGE_WORDS}."
        )
    sets = spec.get("sets") or []
    labels = [chr(65 + i) for i in range(len(paragraphs))]
    for i, qset in enumerate(sets, 1):
        problems += check_set(qset, f"{where}, set {i}", text, single_options=4)
        if qset.get("kind") == "matching" and any(
            str(q.get("prompt", "")).startswith("Paragraph") for q in qset.get("questions", [])
        ):
            if not spec.get("label_paragraphs"):
                problems.append(
                    f"{where}, set {i}: matching headings to paragraphs "
                    "needs label_paragraphs: true."
                )
            for q in qset.get("questions", []):
                letter = str(q.get("prompt", "")).replace("Paragraph", "").strip()
                if letter not in labels:
                    problems.append(
                        f"{where}, set {i}: {q.get('prompt')!r} names no paragraph of the passage."
                    )
    problems += check_difficulty(sets, where, text, listening=False)
    marks = sum(set_marks(s) for s in sets)
    if marks != READING_MARKS[passage]:
        problems.append(
            f"{where}: the sets are worth {marks} marks; they must total {READING_MARKS[passage]}."
        )
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
            problems.append(
                f"Writing Task 1: series {s.get('name')!r} needs one number per category."
            )
    if len(categories) * len(series) < MIN_CHART_FIGURES:
        problems.append(
            f"Writing Task 1: the chart has {len(categories) * len(series)} figures; give at "
            f"least {MIN_CHART_FIGURES}, so there is something to select and compare."
        )
    if not str(chart.get("title", "")).strip() or not str(chart.get("unit", "")).strip():
        problems.append(
            "Writing Task 1: give chart.title and chart.unit (for example %, tonnes, millions)."
        )
    if not str(spec.get("description", "")).strip():
        problems.append("Writing Task 1: give a one-sentence description of what the chart shows.")
    if len(spec.get("key_features") or []) < 3:
        problems.append(
            "Writing Task 1: list at least 3 key_features a strong answer would report."
        )
    return problems


def check_task2(spec: dict) -> list[str]:
    prompt = str(spec.get("prompt", ""))
    return (
        []
        if words(prompt) >= 25
        else ["Writing Task 2: write the full essay question (at least 25 words)."]
    )


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
