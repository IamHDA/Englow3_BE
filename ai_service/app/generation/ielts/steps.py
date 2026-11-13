"""The steps of one IELTS full test, in order, and what each asks the model and checks.

The backend's generation module runs them one at a time (one AI job each) and knows only their
names; everything IELTS-specific about a step lives here.
"""

from __future__ import annotations

import re
from difflib import SequenceMatcher

from app.generation.ielts import prompts, rules

PLAN = "plan"
LISTENING = [f"listening-{n}" for n in range(1, 5)]
READING = [f"reading-{n}" for n in range(1, 4)]
WRITING_1, WRITING_2, SPEAKING = "writing-1", "writing-2", "speaking"
WRITING_STEPS = [PLAN, *LISTENING, *READING, WRITING_1, WRITING_2, SPEAKING]

# The kinds each component must include, so every question type appears somewhere in the paper.
LISTENING_KINDS = {1: ["gap"], 2: ["single", "matching"], 3: ["multi", "matching"], 4: ["gap"]}
READING_KINDS = {1: ["tfng", "gap"], 2: ["matching", "single"], 3: ["ynng"]}
SIMILAR = 0.8


def _tokens(text: str) -> str:
    return " ".join(sorted(set(re.findall(r"[a-z0-9']+", text.lower()))))


def repeats(topic: str, used: list[str]) -> str | None:
    """The used topic this one closely repeats, if any (word order and repetition ignored)."""
    mine = _tokens(topic)
    for other in used:
        theirs = _tokens(other)
        if (
            mine
            and theirs
            and (mine == theirs or SequenceMatcher(None, mine, theirs).ratio() >= SIMILAR)
        ):
            return other
    return None


def _requires(spec: dict, kinds: list[str]) -> list[str]:
    present = {s.get("kind") for s in spec.get("sets", [])}
    return [f"include a {kind!r} question set here" for kind in kinds if kind not in present]


def check_plan(plan: dict, used: list[str]) -> list[str]:
    problems = []
    if len(plan.get("listening") or []) != 4 or len(plan.get("reading") or []) != 3:
        problems.append("give exactly 4 listening topics and 3 reading topics")
    for field in ("title", "writing_task1", "writing_task2", "speaking"):
        if not str(plan.get(field, "")).strip():
            problems.append(f"give {field}")
    chosen = [
        *(plan.get("listening") or []),
        *(plan.get("reading") or []),
        plan.get("writing_task1", ""),
        plan.get("writing_task2", ""),
        plan.get("speaking", ""),
    ]
    seen: list[str] = []
    for topic in [str(t) for t in chosen if str(t).strip()]:
        repeat = repeats(topic, used) or repeats(topic, seen)
        if repeat:
            problems.append(f"{topic!r} repeats a topic already used ({repeat!r}); choose another")
        seen.append(topic)
    return problems


def topics_of(plan: dict) -> list[str]:
    """What a finished set used up, for the next plan to avoid."""
    return [
        str(t)
        for t in [
            *plan.get("listening", []),
            *plan.get("reading", []),
            plan.get("writing_task1"),
            plan.get("writing_task2"),
            plan.get("speaking"),
        ]
        if t
    ]


def messages(
    step: str,
    plan: dict | None,
    brief: str | None,
    used: list[str],
    min_words: int = rules.MIN_PASSAGE_WORDS,
) -> list[dict]:
    if step == PLAN:
        return prompts.plan(used, brief)
    if not plan:
        raise ValueError(f"step {step} needs the plan")
    if step in LISTENING:
        n = int(step[-1])
        return prompts.listening(
            n, plan["listening"][n - 1], rules.LISTENING_MARKS[n], LISTENING_KINDS[n], brief
        )
    if step in READING:
        n = int(step[-1])
        return prompts.reading(
            n, plan["reading"][n - 1], rules.READING_MARKS[n], min_words, READING_KINDS[n], brief
        )
    if step == WRITING_1:
        return prompts.task1(plan["writing_task1"], brief)
    if step == WRITING_2:
        return prompts.task2(plan["writing_task2"], brief)
    if step == SPEAKING:
        return prompts.speaking(plan["speaking"], brief)
    raise ValueError(f"unknown step {step!r}")


def check(
    step: str, reply: dict, used: list[str], min_words: int = rules.MIN_PASSAGE_WORDS
) -> list[str]:
    if step == PLAN:
        return check_plan(reply, used)
    if step in LISTENING:
        n = int(step[-1])
        return rules.check_listening_part(n, reply) + _requires(reply, LISTENING_KINDS[n])
    if step in READING:
        n = int(step[-1])
        return rules.check_reading_passage(n, reply, min_words) + _requires(reply, READING_KINDS[n])
    if step == WRITING_1:
        return rules.check_task1(reply)
    if step == WRITING_2:
        return rules.check_task2(reply)
    if step == SPEAKING:
        return rules.check_speaking(reply)
    raise ValueError(f"unknown step {step!r}")
