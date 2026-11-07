"""Accepted components -> the JSON the admin editor saves, plus the transcript and voices to record.

The paper has the same shape as src/test/resources/ielts/academic-trial-paper-1.json, which
IeltsTrialPaperIntegrationTest takes through the real authoring, review, sitting and full test flow.
"""

from __future__ import annotations

from app.generation.ielts.rules import TFNG, YNNG, set_marks

LISTENING_SECONDS = 1920  # 30 minutes plus 2 to check answers (D-IELTS-7)
READING_SECONDS = 3600
WORD_LIMITS = {1: "ONE WORD", 2: "NO MORE THAN TWO WORDS", 3: "NO MORE THAN THREE WORDS"}

VOICE_POOL = {
    ("UK", "female"): ["en-GB-SoniaNeural", "en-GB-LibbyNeural", "en-GB-MaisieNeural"],
    ("UK", "male"): ["en-GB-RyanNeural", "en-GB-ThomasNeural"],
    ("US", "female"): ["en-US-JennyNeural", "en-US-AriaNeural"],
    ("US", "male"): ["en-US-GuyNeural", "en-US-DavisNeural"],
    ("AU", "female"): ["en-AU-NatashaNeural"],
    ("AU", "male"): ["en-AU-WilliamNeural"],
    ("CA", "female"): ["en-CA-ClaraNeural"],
    ("CA", "male"): ["en-CA-LiamNeural"],
}


def _question(
    qtype: str,
    content: str,
    skill: str,
    marks: int,
    difficulty: str = "MEDIUM",
    options=None,
    accepted=None,
    labels=None,
) -> dict:
    return {
        "questionType": qtype,
        "content": content,
        "difficultyLevel": difficulty,
        "skillType": skill,
        "maxRawScore": marks,
        "explanation": None,
        "options": options or [],
        "acceptedAnswers": [
            {"text": a.strip(), "caseSensitive": False, "orderNo": i + 1}
            for i, a in enumerate(dict.fromkeys(a.strip() for a in (accepted or []) if a.strip()))
        ],
        "matchingLabels": labels or [],
    }


def _options(texts: list[str], correct: set[int]) -> list[dict]:
    return [
        {"content": t, "orderNo": i + 1, "correct": i in correct, "explanation": None}
        for i, t in enumerate(texts)
    ]


def build_set(qset: dict, skill: str, title: str) -> dict:
    kind = qset["kind"]
    questions: list[dict] = []
    content = None
    set_options: list[dict] = []
    instruction = qset["instruction"].strip()
    if kind == "gap":
        content = qset["content"]
        limit = WORD_LIMITS[qset["word_limit"]]
        if limit not in instruction.upper():
            instruction = f"{instruction} Write {limit} for each answer."
        questions = [
            _question("TEXT_ENTRY", q["prompt"], skill, 1, accepted=q["answers"])
            for q in qset["questions"]
        ]
    elif kind == "single":
        questions = [
            _question(
                "SINGLE_CHOICE",
                q["prompt"],
                skill,
                1,
                options=_options(q["options"], {q["answer"]}),
            )
            for q in qset["questions"]
        ]
    elif kind == "multi":
        questions = [
            _question(
                "MULTIPLE_CHOICE",
                q["prompt"],
                skill,
                len(q["answers"]),
                options=_options(q["options"], set(q["answers"])),
            )
            for q in qset["questions"]
        ]
    elif kind == "matching":
        set_options = [
            {"label": e["label"].strip(), "content": e["text"], "orderNo": i + 1}
            for i, e in enumerate(qset["list"])
        ]
        questions = [
            _question("MATCHING", q["prompt"], skill, 1, labels=[q["answer"]])
            for q in qset["questions"]
        ]
    elif kind in ("tfng", "ynng"):
        labels = list(TFNG if kind == "tfng" else YNNG)
        questions = [
            _question(
                "SINGLE_CHOICE",
                q["statement"],
                skill,
                1,
                options=_options(labels, {labels.index(q["answer"])}),
            )
            for q in qset["questions"]
        ]
    return {
        "title": title,
        "instruction": instruction,
        "content": content,
        "questions": questions,
        "setOptions": set_options,
    }


def _sets(sets: list[dict], skill: str, first_number: int) -> list[dict]:
    built, number = [], first_number
    for qset in sets:
        marks = set_marks(qset)
        last = number + marks - 1
        built.append(
            build_set(
                qset, skill, f"Questions {number}-{last}" if last > number else f"Question {number}"
            )
        )
        number = last + 1
    return built


def passage_text(spec: dict) -> str:
    paragraphs = [p.strip() for p in spec["paragraphs"] if p.strip()]
    if spec.get("label_paragraphs"):
        paragraphs = [f"{chr(65 + i)}. {p}" for i, p in enumerate(paragraphs)]
    return spec["title"].strip().upper() + "\n\n" + "\n\n".join(paragraphs)


def build_paper(title: str, listening: dict[int, dict], reading: dict[int, dict]) -> dict:
    order = 1
    sections = []
    for section_type, skill, seconds, specs in (
        ("LISTENING", "LISTENING", LISTENING_SECONDS, listening),
        ("READING", "READING", READING_SECONDS, reading),
    ):
        parts, number = [], 1
        for n in sorted(specs):
            spec = specs[n]
            marks = sum(set_marks(s) for s in spec["sets"])
            label = f"Part {n}" if section_type == "LISTENING" else f"Reading Passage {n}"
            parts.append(
                {
                    "orderNo": n,
                    "title": label,
                    "instruction": f"Questions {number}-{number + marks - 1}",
                    "content": None if section_type == "LISTENING" else passage_text(spec),
                    "questionSets": _sets(spec["sets"], skill, number),
                }
            )
            number += marks
        sections.append(
            {
                "sectionType": section_type,
                "scoredByCriteria": False,
                "timeLimitSeconds": seconds,
                "parts": parts,
            }
        )
    for si, section in enumerate(sections, 1):
        section["orderNo"] = si
        section["maxRawScore"] = sum(
            q["maxRawScore"]
            for p in section["parts"]
            for qs in p["questionSets"]
            for q in qs["questions"]
        )
        for part in section["parts"]:
            for qi, qs in enumerate(part["questionSets"], 1):
                qs["orderNo"] = qi
                for q in qs["questions"]:
                    q["orderNo"] = order  # numbered across the paper, the way the editor saves it
                    order += 1
    return {
        "version": None,
        "metadata": {
            "title": title[:100],
            "examType": "MOCK",
            "certificateType": "IELTS",
            "certificateVariant": "ACADEMIC",
            "targetLevel": "B2",
            "description": (
                "AI-written IELTS Academic Listening and Reading paper. "
                "Review every answer before approving."
            ),
            "durationSeconds": LISTENING_SECONDS + READING_SECONDS,
            "maxRawScore": sum(s["maxRawScore"] for s in sections),
            "passScore": None,
        },
        "content": {"sections": sections},
    }


def transcript_markdown(title: str, listening: dict[int, dict]) -> str:
    lines = [
        f"# {title} — Listening transcript",
        "",
        "AI-written; check every answer against it before approving.",
        "",
    ]
    for n in sorted(listening):
        spec = listening[n]
        lines += [f"## Part {n} — {spec.get('topic', '').strip()}", ""]
        lines += [f"**{line['speaker']}:** {line['text'].strip()}" for line in spec["transcript"]]
        lines.append("")
    return "\n".join(lines)


def voices(listening: dict[int, dict]) -> dict[str, dict[str, str]]:
    """A neural voice per speaker, never the same voice twice within a part."""
    chosen: dict[str, dict[str, str]] = {}
    for n, spec in listening.items():
        used: set[str] = set()
        part: dict[str, str] = {}
        for speaker in spec["speakers"]:
            pool = VOICE_POOL[(speaker["accent"], speaker["gender"])]
            fallback = [
                v for (_, g), vs in VOICE_POOL.items() if g == speaker["gender"] for v in vs
            ]
            voice = next((v for v in pool + fallback if v not in used), pool[0])
            used.add(voice)
            part[speaker["name"]] = voice
        chosen[str(n)] = part
    return chosen


def build_tasks(
    set_title: str, task1: dict, task2: dict, speaking: dict, chart_file: str
) -> list[dict]:
    chart = task1["chart"]
    table = "\n".join(
        f"{category} | " + " | ".join(f"{s['values'][i]}" for s in chart["series"])
        for i, category in enumerate(chart["categories"])
    )
    header = "Category | " + " | ".join(s["name"] for s in chart["series"])
    base = {
        "sampleAnswer": None,
        "targetLevel": "B2",
        "purpose": "PRACTICE",
        "certificateType": "IELTS",
    }
    card = speaking["part2"]
    return [
        {
            **base,
            "skill": "WRITING",
            "taskType": "TASK_1",
            "title": f"{set_title}: Task 1 - {task1['title']}"[:200],
            "instructions": (
                "You should spend about 20 minutes on this task.\n\n"
                f"{task1['description'].strip()}\n\n"
                "Summarise the information by selecting and reporting the main features, and make "
                "comparisons where relevant.\n\nWrite at least 150 words."
            ),
            "rubricNotes": (
                f"Chart data ({chart['type']} chart, {chart['unit']}):\n{header}\n{table}\n\n"
                "Key features: " + "; ".join(task1["key_features"])
            ),
            "minimumWords": 150,
            "timeLimitSeconds": 1200,
            "chart": chart_file,
        },
        {
            **base,
            "skill": "WRITING",
            "taskType": "TASK_2",
            "title": f"{set_title}: Task 2 - {task2['title']}"[:200],
            "instructions": (
                "You should spend about 40 minutes on this task.\n\n"
                f"{task2['prompt'].strip()}\n\n"
                "Give reasons for your answer and include any relevant examples from your own "
                "knowledge "
                "or experience.\n\nWrite at least 250 words."
            ),
            "rubricNotes": task2.get("notes") or None,
            "minimumWords": 250,
            "timeLimitSeconds": 2400,
        },
        {
            **base,
            "skill": "SPEAKING",
            "taskType": "PART_1",
            "title": f"{set_title}: Speaking Part 1"[:200],
            "instructions": "The examiner asks you about yourself.\n\n"
            + "\n".join(f"- {q}" for q in speaking["part1"]),
            "rubricNotes": None,
            "minimumWords": 0,
            "timeLimitSeconds": 300,
        },
        {
            **base,
            "skill": "SPEAKING",
            "taskType": "PART_2",
            "title": f"{set_title}: Speaking Part 2"[:200],
            "instructions": (
                f"{card['cue'].strip()}\n\nYou should say:\n"
                + "\n".join(f"- {p}" for p in card["points"])
                + f"\n{card['explain'].strip()}\n\nYou have one minute to prepare. "
                "Then speak for one to two minutes."
            ),
            "rubricNotes": None,
            "minimumWords": 0,
            "timeLimitSeconds": 120,
        },
        {
            **base,
            "skill": "SPEAKING",
            "taskType": "PART_3",
            "title": f"{set_title}: Speaking Part 3"[:200],
            "instructions": "Discussion questions:\n\n"
            + "\n".join(f"- {q}" for q in speaking["part3"]),
            "rubricNotes": None,
            "minimumWords": 0,
            "timeLimitSeconds": 300,
        },
    ]
