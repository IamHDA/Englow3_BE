"""The AI full-test pipeline end to end with a stand-in model: plan, write, check, retry, build, reject."""

from __future__ import annotations

import copy
import json
import re

import pytest

from generators.generate_ielts_set import generate
from generators.synthesize_ielts_trial_audio import parse_transcript, voice_for, voices_from
from ielts import rules

FILLER = ("The speakers talk about the arrangements in some detail and add a few remarks that do not "
          "answer any question at all. ")


# Words of FILLER, so they are in every text: what a question quotes as its evidence and trap.
EVIDENCE = "add a few remarks that do not answer any question"
TRAP = "The speakers talk about the arrangements"


def chunks(text: str, count: int) -> list[str]:
    """The text in `count` pieces cut between words, so a quote survives being joined again."""
    found = text.split()
    size = -(-len(found) // count)
    return [" ".join(found[i:i + size]) for i in range(0, len(found), size)]


def text_with(answers: list[str], min_words: int) -> str:
    """Prose that contains every answer and is long enough for the rules."""
    body = " ".join(f"{FILLER} The word to note is {a}." for a in answers)
    while rules.words(body) < min_words:
        body += " " + FILLER
    return body


def gap_set(answers: list[str]) -> dict:
    content = " ".join(f"Item {i}: {{{{{i}}}}}." for i in range(1, len(answers) + 1))
    return {"kind": "gap", "instruction": "Complete the notes below.", "word_limit": 1, "content": content,
            "questions": [{"prompt": f"Item {i}", "answers": [a], "evidence": f"The word to note is {a}",
                           "trap": TRAP} for i, a in enumerate(answers, 1)]}


def single_set(count: int, options: int) -> dict:
    return {"kind": "single", "instruction": "Choose the correct letter.",
            "questions": [{"prompt": f"Question {i}", "options": [f"choice {i}{c}" for c in "abcd"[:options]],
                           "answer": i % options, "evidence": EVIDENCE, "trap": TRAP} for i in range(count)]}


def multi_set(count: int) -> dict:
    return {"kind": "multi", "instruction": "Choose TWO letters, A-E.",
            "questions": [{"prompt": f"Which TWO {i}?", "options": [f"option {i}{c}" for c in "abcde"],
                           "answers": [0, 2], "evidence": [EVIDENCE, EVIDENCE], "trap": TRAP}
                          for i in range(count)]}


def matching_set(count: int, entries: int, prompts: list[str] | None = None, labels: str = "ABCDEFGH") -> dict:
    return {"kind": "matching", "instruction": "Choose your answers from the list.",
            "list": [{"label": labels[i] if len(labels) > 3 else f"{i}", "text": f"entry {i}"} for i in range(entries)],
            "questions": [{"prompt": (prompts or [f"Thing {i}" for i in range(count)])[i],
                           "answer": labels[i % entries], "evidence": EVIDENCE, "trap": TRAP}
                          for i in range(count)]}


def judgement_set(kind: str, count: int) -> dict:
    allowed = rules.TFNG if kind == "tfng" else rules.YNNG
    return {"kind": kind, "instruction": "Do the following statements agree?",
            "questions": [{"statement": f"Statement {i}", "answer": allowed[i % 3], "evidence": EVIDENCE}
                          for i in range(count)]}


def listening(part: int) -> dict:
    words = [f"word{part}{i}" for i in range(10)]
    if part in (1, 3):
        speakers = [{"name": "Anna", "accent": "UK", "gender": "female"},
                    {"name": "Ben", "accent": "AU", "gender": "male"}]
    else:
        speakers = [{"name": "Guide", "accent": "US", "gender": "male"}]
    sets = {1: [gap_set(words)], 2: [single_set(5, 3), matching_set(5, 7)],
            3: [multi_set(2), matching_set(6, 5)], 4: [gap_set(words)]}[part]
    lines = [{"speaker": speakers[i % len(speakers)]["name"], "text": text}
             for i, text in enumerate(chunks(text_with(words, rules.MIN_RECORDING_WORDS[part]), 6))]
    return {"topic": f"listening topic {part}", "speakers": speakers, "transcript": lines, "sets": sets}


def reading(passage: int) -> dict:
    words = [f"term{passage}{i}" for i in range(7)]
    paragraphs = chunks(text_with(words, rules.MIN_PASSAGE_WORDS + 50), 6)
    sets = {
        1: [judgement_set("tfng", 6), gap_set(words)],
        2: [matching_set(6, 8, [f"Paragraph {c}" for c in "ABCDEF"], labels=["i", "ii", "iii", "iv", "v", "vi",
                                                                         "vii", "viii"]),
            single_set(5, 4), multi_set(1)],
        3: [judgement_set("ynng", 5), matching_set(5, 3), gap_set(words[:4])],
    }[passage]
    return {"topic": f"reading topic {passage}", "title": f"Passage {passage}", "label_paragraphs": passage == 2,
            "paragraphs": paragraphs, "sets": sets}


TASK1 = {"title": "Cycling to work", "description": "The chart below shows the share of commuters who cycle.",
         "chart": {"type": "bar", "title": "Cycling to work", "unit": "%", "categories": ["A", "B", "C", "D"],
                   "series": [{"name": "2005", "values": [3, 5, 8, 6]}, {"name": "2025", "values": [9, 12, 10, 4]}]},
         "key_features": ["all rose", "B highest in 2025", "C rose least"]}
TASK2 = {"title": "Public transport", "notes": "both views and an opinion",
         "prompt": "Some people think public transport should be free for everyone, while others believe passengers "
                   "should pay the full cost of every journey they make. Discuss both views and give your own opinion."}
SPEAKING = {"part1": ["q1", "q2", "q3", "q4"], "part2": {"cue": "Describe a journey.", "points": ["where", "when",
            "who"], "explain": "and explain why you remember it."}, "part3": ["d1", "d2", "d3", "d4"]}
PLAN = {"title": "Cities and movement", "listening": ["renting a kayak", "a museum audio tour",
        "a group project on bees", "how glass is made"], "reading": ["desert locust swarms",
        "the invention of the zip", "why we forget dreams"], "writing_task1": "commuters who cycle",
        "writing_task2": "free public transport", "speaking": "journeys"}


class StandIn:
    """Answers each prompt with a valid component; `broken` replies come first for the named component."""

    def __init__(self, broken: dict[str, list[dict]] | None = None):
        self.broken = {k: list(v) for k, v in (broken or {}).items()}
        self.calls: list[list[dict]] = []

    def complete(self, messages: list[dict]) -> dict:
        self.calls.append(messages)
        ask = messages[1]["content"]
        listening_part = re.search(r"Listening Part (\d)", ask)
        reading_passage = re.search(r"Reading Passage (\d)", ask)
        component = ("plan" if ask.startswith("Plan one") else
                     "listening-" + listening_part.group(1) if listening_part else
                     "reading-" + reading_passage.group(1) if reading_passage else
                     "writing-1" if "Writing Task 1" in ask else "writing-2" if "Writing Task 2" in ask else
                     "speaking")
        if self.broken.get(component):
            return self.broken[component].pop(0)
        if component == "plan":
            return copy.deepcopy(PLAN)
        if component.startswith("listening"):
            return listening(int(component[-1]))
        if component.startswith("reading"):
            return reading(int(component[-1]))
        return copy.deepcopy({"writing-1": TASK1, "writing-2": TASK2, "speaking": SPEAKING}[component])


@pytest.fixture
def places(tmp_path):
    registry = tmp_path / "topics.json"
    registry.write_text(json.dumps([{"set": "old", "slot": "reading-1", "topic": "household waste recycling rates"}]))
    return {"out_root": tmp_path / "out", "rejects_root": tmp_path / "rejects", "registry": registry}


def test_a_whole_set_is_written_built_and_ready_to_record(places):
    folder = generate(StandIn(), **places)

    paper = json.loads((folder / "paper.json").read_text())
    sections = paper["content"]["sections"]
    assert [s["sectionType"] for s in sections] == ["LISTENING", "READING"]
    assert [s["maxRawScore"] for s in sections] == [40, 40]
    assert paper["metadata"]["maxRawScore"] == 80
    order = [q["orderNo"] for s in sections for p in s["parts"] for qs in p["questionSets"] for q in qs["questions"]]
    assert order == list(range(1, len(order) + 1))
    kinds = {q["questionType"] for s in sections for p in s["parts"] for qs in p["questionSets"] for q in qs["questions"]}
    assert kinds == {"TEXT_ENTRY", "SINGLE_CHOICE", "MULTIPLE_CHOICE", "MATCHING"}
    assert sections[1]["parts"][1]["content"].count("\n\nB. ") == 1  # headings need labelled paragraphs

    tasks = json.loads((folder / "tasks.json").read_text())
    assert [t["taskType"] for t in tasks] == ["TASK_1", "TASK_2", "PART_1", "PART_2", "PART_3"]
    assert "2005 | 2025" in tasks[0]["rubricNotes"]
    assert (folder / "task1-chart.png").stat().st_size > 1000

    # The audio script can record it: every speaker of every part has a voice.
    parts = parse_transcript((folder / "transcript.md").read_text())
    table = voices_from(folder / "voices.json")
    assert sorted(parts) == [1, 2, 3, 4]
    assert all(voice_for(n, s.speaker, table) for n, segments in parts.items() for s in segments)
    assert table[1]["Anna"] != table[1]["Ben"]

    used = json.loads(places["registry"].read_text())
    assert len(used) == 1 + 10 and {u["set"] for u in used[1:]} == {folder.name}


def test_problems_go_back_to_the_model_until_it_gets_it_right(places):
    wrong = listening(1)
    wrong["sets"][0]["questions"][0]["answers"] = ["elephant"]  # not in the recording
    model = StandIn({"listening-1": [wrong]})

    generate(model, **places)

    retry = next(m for m in model.calls if m[-1]["role"] == "user" and "rejected" in m[-1]["content"])
    assert "'elephant' does not appear" in retry[-1]["content"]


def test_a_component_still_wrong_after_every_attempt_is_set_aside_and_nothing_is_kept(places):
    short = reading(1)
    short["paragraphs"] = ["Far too short."]
    model = StandIn({"reading-1": [short, short, short]})

    with pytest.raises(SystemExit, match="reading-1 still failed"):
        generate(model, **places)

    rejected = next(places["rejects_root"].rglob("reading-1.json"))
    assert any("words" in p for p in json.loads(rejected.read_text())["problems"])
    assert not places["out_root"].exists()
    assert len(json.loads(places["registry"].read_text())) == 1


def test_a_topic_already_used_is_refused_at_planning(places):
    repeat = copy.deepcopy(PLAN)
    repeat["reading"][0] = "recycling rates of household waste"
    model = StandIn({"plan": [repeat]})

    generate(model, **places)

    assert "repeats a topic already used" in model.calls[1][-1]["content"]


@pytest.mark.parametrize("change, problem", [
    (lambda s: s["sets"][0].update(content="Item 1: {{1}}. Item 3: {{3}}."), "exactly once"),
    (lambda s: s["sets"][0]["questions"][0].update(answers=["two words"]), "longer than the 1-word limit"),
    (lambda s: s["sets"][0]["questions"].pop(), "must total 10"),
    (lambda s: s.update(speakers=s["speakers"][:1]), "give 2 speakers"),
])
def test_listening_rules_say_what_to_change(change, problem):
    spec = listening(1)
    change(spec)
    assert any(problem in p for p in rules.check_listening_part(1, spec))


def test_true_false_not_given_must_use_every_answer():
    qset = judgement_set("tfng", 6)
    for q in qset["questions"]:
        q["answer"] = "TRUE"
    assert any("at least once" in p for p in rules.check_set(qset, "x", "", 4))


def test_a_matching_list_no_longer_than_its_questions_gives_the_last_answer_away():
    assert any("more list entries" in p for p in rules.check_set(matching_set(5, 5), "x", "", 3))
