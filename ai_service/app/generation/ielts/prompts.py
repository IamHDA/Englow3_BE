"""What the model is asked for. Every reply is one JSON object in the shape ielts.rules checks."""

from __future__ import annotations

import json

SYSTEM = """You are a senior IELTS Academic item writer. You write ORIGINAL material: never copy or
paraphrase a published IELTS or Cambridge test. Use British spelling. Invent people, places and organisations;
no real brands, celebrities or politics. Keep content neutral and suitable for an international audience.

LEVEL. A real paper separates Band 5 from Band 8, so it must contain questions that a solid upper-intermediate
(B2) candidate gets wrong. Each component below is given its level: write the text AT that level, for educated
native speakers, and no lower. Do not write a coursebook exercise - do not simplify the vocabulary, slow the
ideas down, or spell the answer out.

HOW A QUESTION IS MADE HARD - the way the real test does it, never by being unfair or obscure:
- Paraphrase. A question, the notes around a gap, a statement and the answer options never reuse the wording
  of the text: use synonyms, another word class, a summarised or reordered idea. Only a gap-fill ANSWER is taken
  from the text word for word. A candidate who only matches words must get the question wrong.
- Wrong options come from the text. Every wrong option is something the recording or passage really says: an
  idea that is considered and rejected, that was true earlier, that is true of someone or something else, or
  that is only part of the truth. Let a WRONG option echo the words of the text while the correct one rewords
  them. No option is absurd, off-topic or never mentioned.
- The correct option is not the longest or the most carefully worded, and the correct letter varies.
- Outside Listening Part 1, a question tests the understanding of a stretch of text - an opinion, a reason, a
  contrast, a change of mind, an implication - not the catching of one isolated word.
- Nothing can be answered from general knowledge, from the grammar of the question alone, or from another
  question.

Rules that a checker enforces - break one and your work is sent back:
- Reply with ONE JSON object only, exactly in the shape asked for. No commentary.
- Every gap-fill answer must appear word for word in the recording or passage, within the word limit.
- Every question carries "evidence": the exact words of the recording or passage that settle it - a sentence
  or less, copied character for character (for NOT GIVEN: the sentence that comes closest). A choose-TWO
  question gives a list of two. Matching headings to paragraphs needs none.
- Wording is compared by machine. Rejected: a correct option or list entry that repeats 4 consecutive words of
  the text, a statement that repeats 6, notes that repeat 5 beside a gap or run into the answer exactly as the
  text does.
- In a recording, questions are answered in the order they are asked.
- Listening questions carry "trap" wherever there is one: the exact words in the recording that tempt a wrong
  answer (the figure that is then corrected, the suggestion that is turned down, the detail that belongs to
  something else). At least 4 in every 10 questions of a part must have one, never containing the answer.
- Marks: one per question, except a "choose TWO" question, which is worth 2.
- True/False/Not Given (or Yes/No/Not Given): use every answer at least once. A FALSE statement contradicts the
  passage in meaning, not by adding or dropping "not". A NOT GIVEN statement is about something the passage
  does discuss, and adds a comparison, cause, figure or judgement the passage never makes.

Question-set shapes you may use:
{"kind":"gap","instruction":"Complete the notes below.","word_limit":1,
 "content":"text with numbered gaps {{1}}, {{2}} ... in order","questions":[{"prompt":"what gap 1 asks","answers":["word","variant"],"evidence":"exact words containing the answer","trap":"exact tempting words, or null"}]}
{"kind":"single","instruction":"Choose the correct letter, A, B or C.","questions":[{"prompt":"...","options":["...","...","..."],"answer":0,"evidence":"...","trap":"... or null"}]}
{"kind":"multi","instruction":"Choose TWO letters, A-E.","questions":[{"prompt":"Which TWO ...?","options":["5 options"],"answers":[1,3],"evidence":["words for one answer","words for the other"],"trap":"... or null"}]}
{"kind":"matching","instruction":"...Choose your answers from the list.","list":[{"label":"A","text":"..."}],"questions":[{"prompt":"...","answer":"C","evidence":"...","trap":"... or null"}]}
{"kind":"tfng","instruction":"Do the following statements agree with the information...?","questions":[{"statement":"...","answer":"NOT GIVEN","evidence":"..."}]}
{"kind":"ynng", ... same as tfng with YES / NO / NOT GIVEN, for the writer's views}
In "gap", {{n}} counts from 1 within that set. In "matching" give MORE list entries than questions.
"trap" belongs to Listening only. List the gap-fill answer variants that should also be accepted
(e.g. "14", "14th"); never list a different answer."""

SPOKEN = (
    "Write speech, not prose read aloud: contractions, fillers and false starts ('well, actually - no, sorry'), "
    "interruptions, vague language, people checking what they heard. It is recorded at natural speed. "
    "An answer is said once and in passing - never announced ('the key point is'), repeated or dwelt on - and "
    "between two answers there are sentences that answer nothing. The speakers never say a question's own words."
)

LISTENING_BRIEF = {
    1: "Part 1 - the easiest part (Band 4-6, CEFR B1-B2): a conversation between 2 people in an everyday social "
    "context (booking, enquiry, registration). Usually a form or notes completion. The answers are specific "
    "details: names and addresses that are spelt out, numbers, dates, times, prices, ordinary nouns. At least "
    "four are hard to catch: a figure that is corrected, a date that is moved, an option offered and turned "
    "down, two similar details of which only one fits the note.",
    2: "Part 2 (Band 5-7, CEFR B2): a monologue in an everyday social context (a tour, a radio talk, an "
    "announcement). The speaker moves between several places, options or stages and compares them, so that every "
    "option and every list entry is talked about. What used to be true, what is planned but not yet true, and "
    "what is true of a different place make the wrong options.",
    3: "Part 3 (Band 6.5-8, CEFR C1): a conversation between 2-4 people in an educational context (students and "
    "a tutor). The questions test opinion, attitude, agreement, reasons and decisions, not facts. The speakers "
    "disagree, concede, hedge and change their minds, and say what they think indirectly and idiomatically "
    "('I'm not convinced it holds up', 'that's a bit of a stretch'), so the answer is what they end up agreeing "
    "or deciding and every wrong option is something one of them proposed or believed first. Short, overlapping "
    "turns; the vocabulary of the subject they study.",
    4: "Part 4 - the hardest part (Band 7-9, CEFR C1-C2): a monologue on an academic subject (a university "
    "lecture). Usually notes completion. Academic register: nominalisation, subject terms explained only in "
    "passing, signposting, asides and examples, long sentences with subordinate clauses. The notes condense the "
    "lecture under headings in words that are not the lecturer's. Each answer is a content word heard once "
    "inside a dense sentence, with another word of the same kind close by that fits the gap grammatically but "
    "is wrong.",
}
LISTENING_WORDS = {1: "600-750", 2: "600-750", 3: "750-900", 4: "750-950"}

READING_BRIEF = {
    1: "Passage 1 - the most accessible (Band 5-6.5, CEFR upper B2): a factual, descriptive article as a serious "
    "magazine would print it for educated adults - precise detail, dates, processes, and vocabulary that is "
    "not simplified. 13 marks.",
    2: "Passage 2 (Band 6-7.5, CEFR B2-C1): a discursive article that sets several viewpoints or strands of "
    "research against each other. 13 marks. Use matching headings to paragraphs here (prompts 'Paragraph A', "
    "'Paragraph B' ... and label_paragraphs: true): a heading states a paragraph's main idea in other words, "
    "and each spare heading fits a detail of some paragraph but not what it is about.",
    3: "Passage 3 - the hardest (Band 7-9, CEFR C1-C2): an argued academic text as a scholarly book or journal "
    "essay would print it. Abstract reasoning; the writer's stance carried by hedging, concession and evaluation "
    "rather than stated outright; long complex sentences; academic and low-frequency vocabulary. 14 marks. "
    "A Yes/No/Not Given set on the writer's views fits here, and questions on what the writer implies, claims "
    "and is trying to do.",
}


def with_brief(text: str, brief: str | None) -> str:
    """The author's own request, added to a prompt. The rules above still win where the two disagree."""
    if not brief or not brief.strip():
        return text
    return (
        text
        + "\n\nThe test author also asks (follow it wherever it does not break the rules above):\n"
        + brief.strip()
    )


def plan(avoid: list[str], brief: str | None = None) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {
            "role": "user",
            "content": with_brief(
                "Plan one IELTS Academic full test. Choose a DIFFERENT topic for each slot, varied in field "
                "(science, society, history, environment, technology, work, education, health, arts).\n"
                "Do NOT use any of these topics or anything close to them:\n- "
                + "\n- ".join(avoid or ["(none)"])
                + '\n\nReply as {"title": "a short name for the set", "listening": ["topic 1", "topic 2", "topic 3", '
                '"topic 4"], "reading": ["topic 1", "topic 2", "topic 3"], "writing_task1": "what the chart shows", '
                '"writing_task2": "the essay issue", "speaking": "the speaking theme"}',
                brief,
            ),
        },
    ]


def listening(
    part: int, topic: str, marks: int, kinds: list[str], brief: str | None = None
) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {
            "role": "user",
            "content": with_brief(
                f"Write IELTS Listening {LISTENING_BRIEF[part]}\nTopic: {topic}\n"
                f"The question sets must total exactly {marks} marks and include these kinds: {', '.join(kinds)}. "
                f"The recording is {LISTENING_WORDS[part]} words; no transcript line is longer than 150 words "
                "(a long turn is several lines by the same speaker). "
                f"{SPOKEN}\n"
                "Speakers have invented names; give each an accent (UK, US, AU or CA) and gender (female or male).\n"
                'Reply as {"topic": "...", "speakers": [{"name": "...", "accent": "UK", "gender": "female"}], '
                '"transcript": [{"speaker": "name", "text": "what they say"}], "sets": [question sets]}',
                brief,
            ),
        },
    ]


def reading(
    passage: int, topic: str, marks: int, min_words: int, kinds: list[str], brief: str | None = None
) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {
            "role": "user",
            "content": with_brief(
                f"Write IELTS Academic Reading {READING_BRIEF[passage]}\nTopic: {topic}\n"
                f"The passage is {min_words}-950 words in 5-8 paragraphs, written before the questions and able "
                "to stand on its own: nothing is put in only so that it can be asked about. "
                f"The question sets must total exactly {marks} marks, use at least two different kinds, and "
                f"include these kinds: {', '.join(kinds)}.\n"
                'Reply as {"topic": "...", "title": "...", "label_paragraphs": false, '
                '"paragraphs": ["paragraph text", "..."], "sets": [question sets]}',
                brief,
            ),
        },
    ]


def task1(topic: str, brief: str | None = None) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {
            "role": "user",
            "content": with_brief(
                f"Write an IELTS Academic Writing Task 1 based on a chart. Topic: {topic}\n"
                "Invent realistic figures - at least 8 of them, in 2 or more series where the topic allows - with "
                "clear trends, at least one notable contrast and one exception to the general pattern, so that a "
                "Band 7 answer has to select and group rather than list.\n"
                'Reply as {"title": "short title", "description": "The chart below shows ... (one sentence)", '
                '"chart": {"type": "bar or line", "title": "...", "unit": "e.g. %", "categories": ["..."], '
                '"series": [{"name": "...", "values": [numbers, one per category]}]}, '
                '"key_features": ["what a strong overview and body would report", "..."]}',
                brief,
            ),
        },
    ]


def task2(topic: str, brief: str | None = None) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {
            "role": "user",
            "content": with_brief(
                f"Write an IELTS Writing Task 2 essay question. Issue: {topic}\n"
                "Use one standard type: discuss both views and give your opinion; agree or disagree; "
                "problems and solutions; advantages and disadvantages. State a precise proposition that an "
                "educated adult could argue either way, not a truism, in the two or three sentences the real paper uses.\n"
                'Reply as {"title": "short title", "prompt": "the full question as printed", '
                '"notes": "what a full answer must cover, for the grader"}',
                brief,
            ),
        },
    ]


def speaking(theme: str, brief: str | None = None) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM},
        {
            "role": "user",
            "content": with_brief(
                f"Write an IELTS Speaking test. Theme: {theme}\n"
                "Part 1: everyday questions about the candidate. Part 2: a cue card. "
                "Part 3: abstract discussion linked to Part 2 - about society, not the candidate: questions that "
                "ask them to evaluate, compare past and present, speculate about the future and explain causes, "
                "the last ones demanding enough to stretch a Band 8 speaker.\n"
                'Reply as {"part1": ["question", "..."], "part2": {"cue": "Describe ...", '
                '"points": ["what ...", "when ...", "how ..."], "explain": "and explain why ..."}, '
                '"part3": ["question", "..."]}',
                brief,
            ),
        },
    ]


def fix(problems: list[str]) -> dict:
    return {
        "role": "user",
        "content": (
            "The checker rejected your reply. Fix every problem below and send the whole JSON object again:\n- "
            + "\n- ".join(problems)
        ),
    }


def as_message(reply: dict) -> dict:
    return {"role": "assistant", "content": json.dumps(reply, ensure_ascii=False)}
