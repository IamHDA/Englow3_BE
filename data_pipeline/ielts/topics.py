"""The topics already used, so a new set does not repeat an old one.

seeds/ielts/topics.json is committed: every set that passes the rules adds its topics, and the
planner is told to avoid all of them. A topic counts as taken when it closely matches one listed.
"""
from __future__ import annotations

import json
from pathlib import Path

from rapidfuzz import fuzz

REGISTRY = Path(__file__).resolve().parent.parent / "seeds" / "ielts" / "topics.json"
SIMILAR = 80


def load(path: Path = REGISTRY) -> list[dict]:
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else []


def taken(topic: str, used: list[dict]) -> str | None:
    """The listed topic this one repeats, if any."""
    for entry in used:
        if fuzz.token_set_ratio(topic.lower(), entry["topic"].lower()) >= SIMILAR:
            return entry["topic"]
    return None


def record(set_id: str, topics: dict[str, str], path: Path = REGISTRY) -> None:
    used = load(path)
    used += [{"set": set_id, "slot": slot, "topic": topic} for slot, topic in topics.items()]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(used, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
