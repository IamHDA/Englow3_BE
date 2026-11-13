#!/usr/bin/env python3
"""Turn the scraper's JSON into set folders that scripts/load_ielts_trial.py --set-dir can load.

    python to_englow_sets.py --input ~/Downloads/ielts-scrape --output sets [--answers answers --band 8.5]

One folder per test:
    paper.json                the Listening and Reading paper, in the admin authoring format
    tasks.json                Writing Task 1-2 and Speaking Part 1-3
    task1-chart.png           the Task 1 visual, when the task has one
    listening-transcript.md   the site's transcript, for the reviewer
    source.json               where it came from, the audio URLs, and everything that needs a human look

Nothing is sent anywhere: load each folder with scripts/load_ielts_trial.py, which submits it for
review. With --answers, the AI sample answers from generate_answers.py become each task's sampleAnswer.
"""

from __future__ import annotations

import argparse
import io
import json
import re
import sys
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

BLANK = re.compile(r"\{\{(\d+)\}\}")
INSTRUCTION = re.compile(
    r"^(complete|write|choose|answer|label|nb\b|look at|do the following|which|match|classify|reading passage|"
    r"in boxes|you may|questions?\s+\d|the (text|passage|reading)|there are|from the list)|"
    r"no more than|one word|answer sheet|\bboxes\b|\bTRUE\b.*\bif\b|\bYES\b.*\bif\b|\bNOT GIVEN\b.*\bif\b",
    re.IGNORECASE,
)


class Lines(HTMLParser):
    """Sanitised scraper HTML -> text lines; list items get '- ', table cells ' | '."""

    BLOCK = {"p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "table", "ul", "ol"}

    def __init__(self):
        super().__init__()
        self.lines, self.current, self.bullet = [], [], False

    def flush(self):
        text = re.sub(r"\s+", " ", "".join(self.current)).strip(" |")
        if text:
            # The bullet waits for the first text of its item, which may sit in a nested <p>.
            self.lines.append(("- " if self.bullet else "") + text)
            self.bullet = False
        self.current = []

    def handle_starttag(self, tag, attrs):
        if tag in self.BLOCK or tag == "br":
            self.flush()
        if tag == "li":
            self.bullet = True
        if tag in ("td", "th") and self.current:
            self.current.append(" | ")

    def handle_endtag(self, tag):
        if tag in self.BLOCK:
            self.flush()
        if tag == "li":
            self.bullet = False

    def handle_data(self, data):
        self.current.append(data)


def lines_of(html: str) -> list[str]:
    parser = Lines()
    parser.feed(html or "")
    parser.flush()
    return parser.lines


def clip(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


def accepted(answer: str) -> list[str]:
    """'(the) Pacific Hotel / Pacific' -> every spelling the key allows."""
    out = []
    for variant in re.split(r"\s*/\s*|\s+OR\s+", answer):
        variant = variant.strip()
        if not variant:
            continue
        spellings = [variant]
        if "(" in variant:
            spellings = [re.sub(r"[()]", "", variant), re.sub(r"\([^)]*\)", "", variant)]
        for s in spellings:
            s = re.sub(r"\s+", " ", s).strip()
            if s and s not in out and len(s) <= 200:
                out.append(s)
    return out


def split_instruction(lines: list[str]) -> tuple[str, list[str]]:
    head = 0
    while head < len(lines) and not BLANK.search(lines[head]) and INSTRUCTION.search(lines[head]):
        head += 1
    return " ".join(lines[:head]), lines[head:]


class Paper:
    def __init__(self):
        self.order = 0
        self.problems: list[str] = []

    def question(self, kind, content, skill, score, explanation, where):
        self.order += 1
        return {
            "questionType": kind, "content": content or f"Question {where}", "difficultyLevel": "MEDIUM",
            "skillType": skill, "maxRawScore": score, "explanation": explanation, "options": [],
            "acceptedAnswers": [], "matchingLabels": [], "orderNo": self.order,
        }

    def group(self, group: dict, skill: str, order_no: int) -> dict:
        instruction, body = split_instruction(lines_of(group["html"]))
        out = {"title": group["label"], "instruction": instruction or None, "content": None,
               "questions": [], "setOptions": [], "orderNo": order_no}
        numbers = ", ".join(q["number"] for q in group["questions"])
        if group["image_urls"]:
            self.problems.append(f"Questions {numbers}: the set has an image (map, plan or diagram) that is not carried over")
        kind = group["type"]

        if kind == "completion":
            local = {q["number"]: i for i, q in enumerate(group["questions"], 1)}
            out["content"] = "\n".join(BLANK.sub(lambda m: "{{%d}}" % local.get(m.group(1), 0), line) for line in body)
            for q in group["questions"]:
                item = self.question("TEXT_ENTRY", BLANK.sub("____", q["prompt"]), skill, 1, q["explanation"], q["number"])
                item["acceptedAnswers"] = [{"text": t, "caseSensitive": False, "orderNo": i}
                                           for i, t in enumerate(accepted(q["answer"] or ""), 1)]
                if not item["acceptedAnswers"]:
                    self.problems.append(f"Question {q['number']}: no accepted answer")
                out["questions"].append(item)

        elif kind == "matching":
            keys = [o["key"] for o in group["questions"][0]["options"]]
            texts = {}
            for line in body:
                m = re.match(r"^-?\s*([A-Za-z]{1,5})\s*[|.:)]?\s+(.+)$", line)
                if m and m.group(1) in keys and not BLANK.search(line):
                    texts.setdefault(m.group(1), m.group(2).strip())
            out["setOptions"] = [{"label": k, "content": texts.get(k), "orderNo": i} for i, k in enumerate(keys, 1)]
            for q in group["questions"]:
                item = self.question("MATCHING", BLANK.sub("", q["prompt"]).strip() or None, skill, 1, q["explanation"], q["number"])
                item["matchingLabels"] = [q["answer"]] if q["answer"] else []
                if q["answer"] not in keys:
                    self.problems.append(f"Question {q['number']}: answer {q['answer']!r} is not in the list {keys}")
                out["questions"].append(item)

        elif kind in ("multiple_choice", "true_false_not_given", "yes_no_not_given", "multiple_select"):
            many = kind == "multiple_select"
            for q in group["questions"]:
                right = {a.strip().upper() for a in (q["answer"] or "").split(",")} if many else {(q["answer"] or "").upper()}
                choices = q["options"]
                if not choices and not many:
                    # Some TRUE/FALSE/NOT GIVEN questions are a typed box on the site, with no option list.
                    words = next((w for w in (("TRUE", "FALSE", "NOT GIVEN"), ("YES", "NO", "NOT GIVEN")) if right <= set(w)), ())
                    choices = [{"key": w, "text": w} for w in words]
                options = [{"content": o["text"] or o["key"], "orderNo": i, "correct": o["key"].upper() in right, "explanation": None}
                           for i, o in enumerate(choices, 1)]
                correct = sum(o["correct"] for o in options)
                prompt = BLANK.sub("", q["prompt"]).strip()
                item = self.question("MULTIPLE_CHOICE" if many else "SINGLE_CHOICE", prompt, skill,
                                     max(correct, 1) if many else 1, q["explanation"], q["number"])
                item["options"] = options
                if len(options) < 2 or (correct != 1 if not many else not 0 < correct < len(options)):
                    self.problems.append(f"Question {q['number']}: {correct} of {len(options)} options marked correct for answer {q['answer']!r}")
                out["questions"].append(item)
        else:
            self.problems.append(f"Questions {numbers}: question type {kind!r} is not supported; the set is left out")
            return None
        return out

    def section(self, scraped: dict, section_type: str, order_no: int, seconds: int) -> dict:
        parts = []
        for part in scraped["parts"]:
            sets = [s for i, g in enumerate(part["question_groups"], 1) if (s := self.group(g, section_type, i))]
            questions = part["title"].split(":", 1)[-1].strip()
            if section_type == "LISTENING":
                head = {"title": f"Part {part['number']}", "instruction": questions, "content": None}
            else:
                passage = part["passage"] or {}
                if passage.get("image_urls"):
                    self.problems.append(f"Reading Passage {part['number']}: the passage has an image that is not carried over")
                text = "\n\n".join(lines_of(passage.get("html", "")))
                title = (passage.get("title") or "").strip()
                head = {"title": f"Reading Passage {part['number']}", "instruction": passage.get("description") or questions,
                        "content": f"{title.upper()}\n\n{text}" if title else text}
                if not text:
                    self.problems.append(f"Reading Passage {part['number']}: no passage text")
            parts.append({"orderNo": part["number"], **head, "questionSets": sets})
        score = sum(q["maxRawScore"] for p in parts for s in p["questionSets"] for q in s["questions"])
        if score != 40:
            self.problems.append(f"{section_type.title()} adds up to {score} marks, not 40")
        return {"sectionType": section_type, "scoredByCriteria": False, "timeLimitSeconds": seconds,
                "parts": parts, "orderNo": order_no, "maxRawScore": score}


def build_paper(name: str, source: str, sections: dict) -> tuple[dict, list[str]]:
    paper = Paper()
    listening = paper.section(sections["listening"], "LISTENING", 1, 1920)
    reading = paper.section(sections["reading"], "READING", 2, 3600)
    letter = "letter" in " ".join(t["prompt"] for t in sections.get("writing", {}).get("tasks", []) if t["number"] == 1).lower()
    return {
        "version": None,
        "metadata": {
            "title": clip(f"{name} - Listening and Reading", 100),
            "description": f"Imported from ieltsonlinetests.com ({source}); not written for Englow3. "
                           "Check every answer against the source before approving.",
            "examType": "MOCK", "certificateType": "IELTS",
            "certificateVariant": "GENERAL" if letter else "ACADEMIC",
            "targetLevel": "B2", "durationSeconds": 5520,
            "maxRawScore": listening["maxRawScore"] + reading["maxRawScore"], "passScore": None,
        },
        "content": {"sections": [listening, reading]},
    }, paper.problems


def fetch_png(url: str) -> bytes:
    from PIL import Image  # only needed when a Task 1 has a visual

    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        image = Image.open(io.BytesIO(response.read()))
    out = io.BytesIO()
    image.convert("RGB").save(out, "PNG")  # the loader uploads the chart as image/png
    return out.getvalue()


def sample(answers: dict, key: str, band: str) -> str | None:
    entry = answers.get(f"{key}|band{band}")
    if not entry:
        return None
    body = entry["essay"] if "essay" in entry else "\n\n".join(f"Q: {a['question']}\nA: {a['answer']}" for a in entry["answers"])
    return clip(f"[AI-generated sample ({entry['model']}), target band {band}; not marked by an examiner]\n\n{body}", 12000)


def build_tasks(name: str, sections: dict, answers: dict, band: str, folder: Path, problems: list[str]) -> list[dict]:
    base = {"targetLevel": "B2", "purpose": "PRACTICE", "certificateType": "IELTS"}
    tasks = []
    for task in sections.get("writing", {}).get("tasks", []):
        n = task["number"]
        minutes, words = task["minutes"] or (20 if n == 1 else 40), task["min_words"] or (150 if n == 1 else 250)
        item = {
            **base, "skill": "WRITING", "taskType": f"TASK_{n}", "title": clip(f"{name}: Writing Task {n}", 200),
            "instructions": f"You should spend about {minutes} minutes on this task.\n\n"
                            + task["prompt"].replace("\n", "\n\n") + f"\n\nWrite at least {words} words.",
            "rubricNotes": None, "sampleAnswer": sample(answers, f"{name.split(' | ')[-1]}|writing|task{n}", band),
            "minimumWords": words, "timeLimitSeconds": minutes * 60,
        }
        if task["image_urls"]:
            try:
                (folder / f"task{n}-chart.png").write_bytes(fetch_png(task["image_urls"][0]))
            except Exception as e:  # network, HTTP or an unreadable image: the task is useless without its visual
                problems.append(f"Writing Task {n}: could not fetch its image ({e}); the task is left out")
                continue
            item["chart"] = f"task{n}-chart.png"
            problems.append(f"Writing Task {n}: rubricNotes is empty, so the grader has no chart data to check essays against")
            if len(task["image_urls"]) > 1:
                problems.append(f"Writing Task {n}: has {len(task['image_urls'])} images, only the first is kept")
        tasks.append(item)

    for part in sections.get("speaking", {}).get("parts", []):
        questions, n = part["questions"], part["number"]
        if not questions or n not in (1, 2, 3):
            continue
        if n == 2:
            cue = "\n".join(lines_of(questions[0].get("cue_card_html") or ""))
            text = f"{questions[0]['text']}\n\n{cue}".strip() + "\n\nYou have one minute to prepare. Then speak for one to two minutes."
        else:
            intro = "The examiner asks you about yourself." if n == 1 else "Discussion questions:"
            text = intro + "\n\n" + "\n".join(f"- {q['text']}" for q in questions)
        tasks.append({
            **base, "skill": "SPEAKING", "taskType": f"PART_{n}", "title": clip(f"{name}: Speaking Part {n}", 200),
            "instructions": text, "rubricNotes": None,
            "sampleAnswer": sample(answers, f"{name.split(' | ')[-1]}|speaking|part{n}", band),
            "minimumWords": 0, "timeLimitSeconds": 120 if n == 2 else 300,
        })
    return tasks


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, required=True, help="folder of JSON files written by the extension")
    parser.add_argument("--output", type=Path, required=True, help="where the set folders go")
    parser.add_argument("--answers", type=Path, help="folder of *.answers.json from generate_answers.py")
    parser.add_argument("--band", default="8.5", help="which generated band becomes sampleAnswer (default 8.5)")
    args = parser.parse_args()

    files = sorted(p for p in args.input.glob("*.json") if not p.name.endswith(".answers.json"))
    if not files:
        print(f"No collection JSON files in {args.input}", file=sys.stderr)
        return 2
    written = flagged = skipped = 0
    for path in files:
        collection = json.loads(path.read_text(encoding="utf-8"))
        answers_file = args.answers / f"{path.stem}.answers.json" if args.answers else None
        answers = json.loads(answers_file.read_text(encoding="utf-8"))["answers"] if answers_file and answers_file.is_file() else {}
        for test in collection["tests"]:
            sections = test["sections"]
            name = f"{collection['collection']['title']} | {test['name']}"
            if "listening" not in sections or "reading" not in sections:
                print(f"SKIPPED  {name}: needs both Listening and Reading, has {sorted(sections)}", file=sys.stderr)
                skipped += 1
                continue
            folder = args.output / f"{path.stem}--{slug(test['name'])}"
            folder.mkdir(parents=True, exist_ok=True)
            paper, problems = build_paper(name, collection["collection"]["url"], sections)
            tasks = build_tasks(name, sections, answers, args.band, folder, problems)
            audio = sections["listening"].get("audio_urls", [])
            problems.append("Listening has no per-part audio: the source has one recording for the whole test "
                            "(see audio_urls), and the loader needs audio/part-1.mp3 to part-4.mp3")
            (folder / "paper.json").write_text(json.dumps(paper, ensure_ascii=False, indent=2), encoding="utf-8")
            (folder / "tasks.json").write_text(json.dumps(tasks, ensure_ascii=False, indent=2), encoding="utf-8")
            (folder / "listening-transcript.md").write_text("\n\n".join(
                f"## Part {p['number']}\n\n" + "\n\n".join(lines_of((p["transcript"] or {}).get("html", "")))
                for p in sections["listening"]["parts"]), encoding="utf-8")
            (folder / "source.json").write_text(json.dumps({
                "origin": "ieltsonlinetests.com", "collection": collection["collection"], "test": test["name"],
                "scraped_at": collection.get("scraped_at"),
                "source_urls": {k: v["source_url"] for k, v in sections.items()},
                "audio_urls": audio, "needs_review": problems,
            }, ensure_ascii=False, indent=2), encoding="utf-8")
            written += 1
            # These two apply to nearly every set, so they are recorded in source.json but not repeated per line.
            blocking = [p for p in problems if not re.match(r"Listening has no per-part|Writing Task \d: rubricNotes", p)]
            flagged += bool(blocking)
            print(f"{'CHECK  ' if blocking else 'ok     '}  {folder.name}: {len(tasks)} tasks" + "".join(f"\n           - {p}" for p in blocking))
    print(f"\n{written} sets written ({flagged} need a look before loading), {skipped} skipped. "
          "Every set also lists its open points in source.json.")
    return 1 if flagged or skipped else 0


if __name__ == "__main__":
    sys.exit(main())
