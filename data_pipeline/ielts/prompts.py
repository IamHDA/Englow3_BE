"""What the model is asked for. Every reply is one JSON object in the shape ielts.rules checks."""
from __future__ import annotations

import json

SYSTEM = """You are an experienced IELTS Academic test writer. You write ORIGINAL material: never copy or
paraphrase a published IELTS or Cambridge test. Use British spelling. Invent people, places and organisations;
no real brands, celebrities or politics. Keep content neutral and suitable for an international audience.

Rules that a checker enforces - break one and your work is sent back:
- Reply with ONE JSON object only, exactly in the shape asked for. No commentary.
- Every gap-fill answer must appear word for word in the recording or passage, within the word limit.
- Questions follow the order in which the information appears.
- Distractors are plausible: in a recording, mention a wrong option before the speaker corrects it.
- Marks: one per question, except a "choose TWO" question, which is worth 2.
- True/False/Not Given (or Yes/No/Not Given): use every answer at least once; NOT GIVEN means the passage
  truly says nothing either way.

Question-set shapes you may use:
{"kind":"gap","instruction":"Complete the notes below.","word_limit":1,
 "content":"text with numbered gaps {{1}}, {{2}} ... in order","questions":[{"prompt":"what gap 1 asks","answers":["word","variant"]}]}
{"kind":"single","instruction":"Choose the correct letter, A, B or C.","questions":[{"prompt":"...","options":["...","...","..."],"answer":0}]}
{"kind":"multi","instruction":"Choose TWO letters, A-E.","questions":[{"prompt":"Which TWO ...?","options":["5 options"],"answers":[1,3]}]}
{"kind":"matching","instruction":"...Choose your answers from the list.","list":[{"label":"A","text":"..."}],"questions":[{"prompt":"...","answer":"C"}]}
{"kind":"tfng","instruction":"Do the following statements agree with the information...?","questions":[{"statement":"...","answer":"NOT GIVEN"}]}
{"kind":"ynng", ... same as tfng with YES / NO / NOT GIVEN, for the writer's views}
In "gap", {{n}} counts from 1 within that set. In "matching" give MORE list entries than questions.
List the gap-fill answer variants that should also be accepted (e.g. "14", "14th"); never list a different answer."""

LISTENING_BRIEF = {
    1: "Part 1: a conversation between 2 people in an everyday social context (booking, enquiry, registration). "
       "Usually a form or notes completion.",
    2: "Part 2: a monologue in an everyday social context (a tour, a radio talk, an announcement).",
    3: "Part 3: a conversation between 2-4 people in an educational context (students and a tutor).",
    4: "Part 4: a monologue on an academic subject (a lecture). Usually notes completion.",
}

READING_BRIEF = {
    1: "Passage 1: an accessible, factual article. 13 marks.",
    2: "Passage 2: a discursive article of medium difficulty. 13 marks. Use matching headings to paragraphs here "
       "(prompts 'Paragraph A', 'Paragraph B' ... and label_paragraphs: true).",
    3: "Passage 3: the hardest, an argued or academic text with the writer's views. 14 marks. "
       "A Yes/No/Not Given set fits here.",
}


def plan(avoid: list[str]) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": (
            "Plan one IELTS Academic full test. Choose a DIFFERENT topic for each slot, varied in field "
            "(science, society, history, environment, technology, work, education, health, arts).\n"
            "Do NOT use any of these topics or anything close to them:\n- " + "\n- ".join(avoid or ["(none)"]) +
            '\n\nReply as {"title": "a short name for the set", "listening": ["topic 1", "topic 2", "topic 3", '
            '"topic 4"], "reading": ["topic 1", "topic 2", "topic 3"], "writing_task1": "what the chart shows", '
            '"writing_task2": "the essay issue", "speaking": "the speaking theme"}'
        )},
    ]


def listening(part: int, topic: str, marks: int, kinds: list[str]) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": (
            f"Write IELTS Listening {LISTENING_BRIEF[part]}\nTopic: {topic}\n"
            f"The question sets must total exactly {marks} marks and include these kinds: {', '.join(kinds)}. "
            "The recording is 450-650 words. "
            "Speakers have invented names; give each an accent (UK, US, AU or CA) and gender (female or male).\n"
            'Reply as {"topic": "...", "speakers": [{"name": "...", "accent": "UK", "gender": "female"}], '
            '"transcript": [{"speaker": "name", "text": "what they say"}], "sets": [question sets]}'
        )},
    ]


def reading(passage: int, topic: str, marks: int, min_words: int, kinds: list[str]) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": (
            f"Write IELTS Academic Reading {READING_BRIEF[passage]}\nTopic: {topic}\n"
            f"The passage is {min_words}-950 words in 5-8 paragraphs. The question sets must total exactly "
            f"{marks} marks, use at least two different kinds, and include these kinds: {', '.join(kinds)}.\n"
            'Reply as {"topic": "...", "title": "...", "label_paragraphs": false, '
            '"paragraphs": ["paragraph text", "..."], "sets": [question sets]}'
        )},
    ]


def task1(topic: str) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": (
            f"Write an IELTS Academic Writing Task 1 based on a chart. Topic: {topic}\n"
            "Invent realistic figures with clear trends and at least one notable contrast.\n"
            'Reply as {"title": "short title", "description": "The chart below shows ... (one sentence)", '
            '"chart": {"type": "bar or line", "title": "...", "unit": "e.g. %", "categories": ["..."], '
            '"series": [{"name": "...", "values": [numbers, one per category]}]}, '
            '"key_features": ["what a strong overview and body would report", "..."]}'
        )},
    ]


def task2(topic: str) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": (
            f"Write an IELTS Writing Task 2 essay question. Issue: {topic}\n"
            "Use one standard type: discuss both views and give your opinion; agree or disagree; "
            "problems and solutions; advantages and disadvantages.\n"
            'Reply as {"title": "short title", "prompt": "the full question as printed", '
            '"notes": "what a full answer must cover, for the grader"}'
        )},
    ]


def speaking(theme: str) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": (
            f"Write an IELTS Speaking test. Theme: {theme}\n"
            "Part 1: everyday questions about the candidate. Part 2: a cue card. "
            "Part 3: abstract discussion linked to Part 2.\n"
            'Reply as {"part1": ["question", "..."], "part2": {"cue": "Describe ...", '
            '"points": ["what ...", "when ...", "how ..."], "explain": "and explain why ..."}, '
            '"part3": ["question", "..."]}'
        )},
    ]


def fix(problems: list[str]) -> dict:
    return {"role": "user", "content": (
        "The checker rejected your reply. Fix every problem below and send the whole JSON object again:\n- "
        + "\n- ".join(problems)
    )}


def as_message(reply: dict) -> dict:
    return {"role": "assistant", "content": json.dumps(reply, ensure_ascii=False)}
