"""What keeps an AI-written component from being a word-matching exercise."""

import copy

import pytest

from app.generation.ielts import build, rules
from tests.generation_fixtures import TASK1, listening, reading

RECORDING = (
    "Could I book the workshop for the fourteenth? Ah, sorry, the fourteenth has just gone. "
    "We've got space on the sixteenth, though. Fine. And it's forty pounds, isn't it? "
    "It was, but from this month it's forty-five. The tutor used to bring the clay herself, "
    "but these days people are asked to buy their own from the shop downstairs."
)


def gap(content: str, **question) -> dict:
    question = {"prompt": "Date", "answers": ["sixteenth"], **question}
    return {
        "kind": "gap",
        "instruction": "Complete the notes below.",
        "word_limit": 1,
        "content": content,
        "questions": [question],
    }


def single(options: list[str], answer: int, **question) -> dict:
    return {
        "kind": "single",
        "instruction": "Choose the correct letter, A, B or C.",
        "questions": [{"prompt": "Who?", "options": options, "answer": answer, **question}],
    }


def problems(qset: dict, text: str = RECORDING, heard: bool = True) -> list[str]:
    return rules.check_difficulty([qset], "Part 1", text, listening=heard)


def test_a_question_that_points_at_its_answer_in_other_words_is_accepted():
    qset = gap("Workshop booked for the {{1}}", evidence="We've got space on the sixteenth")

    assert problems(qset) == []


def test_a_question_must_say_where_its_answer_is():
    assert any('give "evidence"' in p for p in problems(gap("Workshop on the {{1}}")))


def test_evidence_must_be_the_texts_own_words():
    qset = gap("Workshop on the {{1}}", evidence="There is room on the sixteenth")

    assert any("not in the recording word for word" in p for p in problems(qset))


def test_evidence_may_skip_words_and_ignores_punctuation():
    qset = gap("Workshop on the {{1}}", evidence="we've got space ... the SIXTEENTH, though")

    assert problems(qset) == []


def test_the_evidence_for_a_gap_holds_the_answer():
    qset = gap("Workshop on the {{1}}", evidence="the fourteenth has just gone")

    assert any("contain the answer" in p for p in problems(qset))


def test_notes_that_run_into_the_answer_as_the_recording_does_are_sent_back():
    qset = gap("We've got space on the {{1}}", evidence="We've got space on the sixteenth")

    assert any("notes beside the gap repeat" in p for p in problems(qset))


def test_notes_that_copy_the_words_after_the_gap_are_sent_back():
    qset = gap(
        "Clay: {{1}} people are asked to buy their own",
        prompt="When",
        answers=["days"],
        evidence="these days people are asked",
    )

    assert any("'people are asked to buy'" in p for p in problems(qset))


def test_a_correct_option_lifted_from_the_recording_is_sent_back():
    options = ["the tutor", "people are asked to buy their own", "the shop"]
    lifted = single(options, 1, evidence="people are asked to buy their own")
    echoed_by_a_wrong_option = single(options, 2, evidence="from the shop downstairs")

    assert any("correct option repeats" in p for p in problems(lifted))
    assert problems(echoed_by_a_wrong_option) == []


def test_a_statement_copied_from_the_passage_is_sent_back():
    qset = {
        "kind": "tfng",
        "instruction": "Do the statements agree?",
        "questions": [
            {
                "statement": "The tutor used to bring the clay herself.",
                "answer": "TRUE",
                "evidence": "The tutor used to bring the clay herself",
            }
        ],
    }

    assert any("statement repeats the passage" in p for p in problems(qset, heard=False))


def test_questions_follow_the_order_of_the_recording():
    price = gap("Fee: £{{1}}", prompt="Fee", answers=["forty-five"], evidence="it's forty-five")
    date = gap("Day: the {{1}}", evidence="space on the sixteenth")

    late = rules.check_difficulty([price, date], "Part 1", RECORDING, listening=True)

    assert any("set 2, gap 1: it is answered earlier" in p for p in late)
    assert rules.check_difficulty([date, price], "Part 1", RECORDING, listening=True) == []


def test_a_trap_is_the_recordings_own_words_and_never_the_answer():
    evidence = "We've got space on the sixteenth"
    invented = gap("Day: the {{1}}", evidence=evidence, trap="the fifteenth is full")
    the_answer = gap("Day: the {{1}}", evidence=evidence, trap="space on the sixteenth")
    real = gap("Day: the {{1}}", evidence=evidence, trap="the fourteenth has just gone")

    assert any(
        "trap 'the fifteenth is full' is not in the recording" in p for p in problems(invented)
    )
    assert any("tempting WRONG words" in p for p in problems(the_answer))
    assert problems(real) == []


def test_a_listening_part_needs_traps_in_four_questions_of_ten():
    spec = listening(1)
    for question in spec["sets"][0]["questions"][3:]:
        question["trap"] = None

    found = rules.check_listening_part(1, spec)

    assert any("only 3 of its 10 questions have a trap" in p and "at least 4" in p for p in found)


def test_a_reading_passage_needs_no_traps():
    spec = reading(1)
    for qset in spec["sets"]:
        for question in qset["questions"]:
            question.pop("trap", None)

    assert rules.check_reading_passage(1, spec) == []


def test_the_correct_option_is_not_the_longest_or_always_the_same_letter():
    spec = listening(2)
    for question in spec["sets"][0]["questions"]:
        question["answer"] = 0
        question["options"][0] += " with a good deal of extra detail"

    found = rules.check_listening_part(2, spec)

    assert any("the longest in 5 of 5" in p for p in found)
    assert any("the same letter" in p for p in found)


@pytest.mark.parametrize("part, shortest", [(1, 550), (2, 550), (3, 650), (4, 700)])
def test_a_recording_is_as_long_as_the_real_one(part, shortest):
    spec = listening(part)
    spec["transcript"] = spec["transcript"][:3]

    assert any(f"it must be {shortest}-1100" in p for p in rules.check_listening_part(part, spec))


def test_a_long_turn_is_split_into_lines():
    spec = listening(4)
    spec["transcript"] = [
        {
            "speaker": spec["speakers"][0]["name"],
            "text": " ".join(line["text"] for line in spec["transcript"]),
        }
    ]

    assert any("longer than 300 words" in p for p in rules.check_listening_part(4, spec))


def test_a_task_1_chart_has_enough_figures_to_compare():
    thin = copy.deepcopy(TASK1)
    thin["chart"]["categories"] = ["A", "B", "C"]
    for series in thin["chart"]["series"]:
        series["values"] = series["values"][:3]

    assert any("6 figures" in p for p in rules.check_task1(thin))
    assert rules.check_task1(TASK1) == []


def test_the_built_paper_explains_each_answer_and_grades_the_parts():
    spec = listening(1)
    spec["sets"][0]["questions"][0]["trap"] = "The speakers talk"
    paper = build.build_paper("Set", {n: listening(n) for n in (2, 3, 4)} | {1: spec}, {})
    parts = paper["content"]["sections"][0]["parts"]
    first = parts[0]["questionSets"][0]["questions"][0]

    assert first["explanation"] == (
        "You hear: “The word to note is word10” The trap: “The speakers talk”."
    )
    assert [p["questionSets"][0]["questions"][0]["difficultyLevel"] for p in parts] == [
        "EASY",
        "MEDIUM",
        "HARD",
        "HARD",
    ]


def test_not_given_is_explained_as_not_settled():
    question = {"statement": "s", "answer": "NOT GIVEN", "evidence": "the nearest sentence"}

    assert build.explanation(question, "READING") == (
        "The passage comes closest with: “the nearest sentence” - it does not say this either way."
    )
