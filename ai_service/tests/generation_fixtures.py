"""Valid IELTS components built in code, so generation tests need no model."""

from __future__ import annotations

import copy

from app.generation.ielts import rules

FILLER = (
    "The speakers talk about the arrangements in some detail and add a few remarks that do not "
    "answer any question at all. "
)


# Words of FILLER, so they are in every text: what a question quotes as its evidence and trap.
EVIDENCE = "add a few remarks that do not answer any question"
TRAP = "The speakers talk about the arrangements"


def text_with(answers: list[str], min_words: int) -> str:
    body = " ".join(f"{FILLER} The word to note is {a}." for a in answers)
    while rules.words(body) < min_words:
        body += " " + FILLER
    return body


def chunks(text: str, count: int) -> list[str]:
    """The text in `count` pieces cut between words, so a quote survives being joined again."""
    found = text.split()
    size = -(-len(found) // count)
    return [" ".join(found[i : i + size]) for i in range(0, len(found), size)]


def gap_set(answers: list[str]) -> dict:
    content = " ".join(f"Item {i}: {{{{{i}}}}}." for i in range(1, len(answers) + 1))
    return {
        "kind": "gap",
        "instruction": "Complete the notes below.",
        "word_limit": 1,
        "content": content,
        "questions": [
            {
                "prompt": f"Item {i}",
                "answers": [a],
                "evidence": f"The word to note is {a}",
                "trap": TRAP,
            }
            for i, a in enumerate(answers, 1)
        ],
    }


def single_set(count: int, options: int) -> dict:
    return {
        "kind": "single",
        "instruction": "Choose the correct letter.",
        "questions": [
            {
                "prompt": f"Question {i}",
                "options": [f"choice {i}{c}" for c in "abcd"[:options]],
                "answer": i % options,
                "evidence": EVIDENCE,
                "trap": TRAP,
            }
            for i in range(count)
        ],
    }


def multi_set(count: int) -> dict:
    return {
        "kind": "multi",
        "instruction": "Choose TWO letters, A-E.",
        "questions": [
            {
                "prompt": f"Which TWO {i}?",
                "options": [f"option {i}{c}" for c in "abcde"],
                "answers": [0, 2],
                "evidence": [EVIDENCE, EVIDENCE],
                "trap": TRAP,
            }
            for i in range(count)
        ],
    }


def matching_set(count: int, entries: int, prompts: list[str] | None = None, labels=None) -> dict:
    labels = labels or list("ABCDEFGH")
    return {
        "kind": "matching",
        "instruction": "Choose your answers from the list.",
        "list": [{"label": labels[i], "text": f"entry {i}"} for i in range(entries)],
        "questions": [
            {
                "prompt": (prompts or [f"Thing {i}" for i in range(count)])[i],
                "answer": labels[i % entries],
                "evidence": EVIDENCE,
                "trap": TRAP,
            }
            for i in range(count)
        ],
    }


def judgement_set(kind: str, count: int) -> dict:
    allowed = rules.TFNG if kind == "tfng" else rules.YNNG
    return {
        "kind": kind,
        "instruction": "Do the following statements agree?",
        "questions": [
            {"statement": f"Statement {i}", "answer": allowed[i % 3], "evidence": EVIDENCE}
            for i in range(count)
        ],
    }


def listening(part: int) -> dict:
    answers = [f"word{part}{i}" for i in range(10)]
    if part in (1, 3):
        speakers = [
            {"name": "Anna", "accent": "UK", "gender": "female"},
            {"name": "Ben", "accent": "AU", "gender": "male"},
        ]
    else:
        speakers = [{"name": "Guide", "accent": "US", "gender": "male"}]
    sets = {
        1: [gap_set(answers)],
        2: [single_set(5, 3), matching_set(5, 7)],
        3: [multi_set(2), matching_set(6, 5)],
        4: [gap_set(answers)],
    }[part]
    spoken = text_with(answers, rules.MIN_RECORDING_WORDS[part])
    lines = [
        {"speaker": speakers[i % len(speakers)]["name"], "text": text}
        for i, text in enumerate(chunks(spoken, 6))
    ]
    return {
        "topic": f"listening topic {part}",
        "speakers": speakers,
        "transcript": lines,
        "sets": sets,
    }


def reading(passage: int) -> dict:
    answers = [f"term{passage}{i}" for i in range(7)]
    paragraphs = chunks(text_with(answers, rules.MIN_PASSAGE_WORDS + 50), 6)
    headings = ["i", "ii", "iii", "iv", "v", "vi", "vii", "viii"]
    sets = {
        1: [judgement_set("tfng", 6), gap_set(answers)],
        2: [
            matching_set(6, 8, [f"Paragraph {c}" for c in "ABCDEF"], headings),
            single_set(5, 4),
            multi_set(1),
        ],
        3: [judgement_set("ynng", 5), matching_set(5, 3), gap_set(answers[:4])],
    }[passage]
    return {
        "topic": f"reading topic {passage}",
        "title": f"Passage {passage}",
        "label_paragraphs": passage == 2,
        "paragraphs": paragraphs,
        "sets": sets,
    }


TASK1 = {
    "title": "Cycling to work",
    "description": "The chart below shows the share of commuters who cycle.",
    "chart": {
        "type": "bar",
        "title": "Cycling to work",
        "unit": "%",
        "categories": ["A", "B", "C", "D"],
        "series": [
            {"name": "2005", "values": [3, 5, 8, 6]},
            {"name": "2025", "values": [9, 12, 10, 4]},
        ],
    },
    "key_features": ["all rose", "B highest in 2025", "C rose least"],
}
TASK2 = {
    "title": "Public transport",
    "notes": "both views and an opinion",
    "prompt": (
        "Some people think public transport should be free for everyone, while others believe "
        "passengers should pay the full cost of every journey they make. Discuss both views and "
        "give your own opinion."
    ),
}
SPEAKING = {
    "part1": ["q1", "q2", "q3", "q4"],
    "part2": {
        "cue": "Describe a journey.",
        "points": ["where", "when", "who"],
        "explain": "and explain why you remember it.",
    },
    "part3": ["d1", "d2", "d3", "d4"],
}
PLAN = {
    "title": "Cities and movement",
    "listening": [
        "renting a kayak",
        "a museum audio tour",
        "a group project on bees",
        "how glass is made",
    ],
    "reading": ["desert locust swarms", "the invention of the zip", "why we forget dreams"],
    "writing_task1": "commuters who cycle",
    "writing_task2": "free public transport",
    "speaking": "journeys",
}


def components() -> dict[str, dict]:
    parts = {
        "plan": copy.deepcopy(PLAN),
        "writing-1": copy.deepcopy(TASK1),
        "writing-2": copy.deepcopy(TASK2),
        "speaking": copy.deepcopy(SPEAKING),
    }
    parts |= {f"listening-{n}": listening(n) for n in range(1, 5)}
    parts |= {f"reading-{n}": reading(n) for n in range(1, 4)}
    return parts
