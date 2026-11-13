#!/usr/bin/env python3
"""Generate AI sample answers and Writing rubric notes for the prompts the extension exported.

The site publishes no model answers for Writing and Speaking, so this fills the gap with an OpenAI
model. Every answer is stored with the model name and target band and marked ai_generated; none
of them has been marked by an examiner.

It also writes the rubric notes the Englow3 Writing grader reads. That grader gets text only: for
Task 1 it judges an essay against the figures in the notes, never against the picture, so the model
reads each chart into a data table here. A misread figure would mark correct essays down, so every
set of notes has to be checked against the chart by a person before the task is approved.

    export OPENAI_API_KEY=...
    python generate_answers.py --input ~/Downloads/ielts-scrape --output answers --model <model>

Reads each <collection>.json and writes <collection>.answers.json. Re-running skips what is
already in the output file, so an interrupted or partly failed run can simply be started again.
"""

from __future__ import annotations

import argparse
import base64
import json
import mimetypes
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import openai

SYSTEM = (
    "You are an experienced IELTS examiner writing sample answers for learners. "
    "Write the answer a candidate at the requested band would realistically produce, judged "
    "against the public IELTS band descriptors: a band 7 answer is good but not flawless, a "
    "band 8.5 answer is near-expert. Return only what the schema asks for."
)

WRITING_SCHEMA = {
    "name": "writing_sample",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["cannot_answer", "reason", "essay"],
        "properties": {
            "cannot_answer": {"type": "boolean"},
            "reason": {"type": "string"},
            "essay": {"type": "string"},
        },
    },
}

SPEAKING_SCHEMA = {
    "name": "speaking_samples",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["answers"],
        "properties": {
            "answers": {
                "type": "array",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["question_number", "answer"],
                    "properties": {
                        "question_number": {"type": "integer"},
                        "answer": {"type": "string"},
                    },
                },
            }
        },
    },
}

RUBRIC_SCHEMA = {
    "name": "rubric_notes",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["cannot_read", "reason", "visual_type", "unit", "data_table", "description", "key_features"],
        "properties": {
            "cannot_read": {"type": "boolean"},
            "reason": {"type": "string"},
            "visual_type": {"type": "string"},
            "unit": {"type": "string"},
            "data_table": {"type": "string"},
            "description": {"type": "string"},
            "key_features": {"type": "array", "items": {"type": "string"}},
        },
    },
}

SPEAKING_LENGTH = {
    1: "Part 1 (interview): 2-4 spoken sentences per question.",
    2: "Part 2 (long turn): one monologue of about 220-260 words covering every bullet on the cue card.",
    3: "Part 3 (discussion): 4-6 spoken sentences per question, with a reason or example.",
}


class Unusable(Exception):
    """The model answered, but not with something that can be stored as a sample answer."""


def image_part(url: str) -> dict:
    # Sent inline rather than by URL so the result does not depend on the model host reaching the CDN.
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
        mime = response.headers.get_content_type()
    if not mime.startswith("image/"):
        mime = mimetypes.guess_type(url)[0] or "image/png"
    return {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{base64.b64encode(data).decode()}"}}


def ask(client: openai.OpenAI, model: str, content: list[dict], schema: dict) -> dict:
    completion = client.chat.completions.create(
        model=model,
        messages=[{"role": "system", "content": SYSTEM}, {"role": "user", "content": content}],
        response_format={"type": "json_schema", "json_schema": schema},
    )
    choice = completion.choices[0]
    if choice.message.refusal:
        raise Unusable(f"model refused: {choice.message.refusal}")
    if choice.finish_reason != "stop":
        raise Unusable(f"response ended early (finish_reason={choice.finish_reason})")
    return json.loads(choice.message.content)


def writing_answer(client, model, task: dict, band: str) -> dict:
    text = (
        f"IELTS Writing Task {task['number']}. Target band: {band}.\n\n{task['prompt']}\n\n"
        f"Write at least {task['min_words'] or (150 if task['number'] == 1 else 250)} words.\n"
        "If the task shows a chart, table, map or diagram, describe only figures you can read in "
        "the attached image and do not invent data. If the task depends on a visual that is not "
        "attached or cannot be read, set cannot_answer to true and say why in reason; otherwise "
        "set cannot_answer to false and leave reason empty."
    )
    content = [{"type": "text", "text": text}] + [image_part(u) for u in task["image_urls"]]
    result = ask(client, model, content, WRITING_SCHEMA)
    if result["cannot_answer"]:
        raise Unusable(f"model could not answer: {result['reason']}")
    words = len(result["essay"].split())
    minimum = task["min_words"] or 0
    if words < minimum:
        raise Unusable(f"essay is {words} words, below the task minimum of {minimum}")
    return {"essay": result["essay"], "word_count": words}


def rubric_notes(client, model, task: dict, band: None) -> dict:
    """Notes in the layout the Englow3 generator writes, so the grader reads both kinds the same way."""
    if task["image_urls"]:
        text = (
            f"IELTS Writing Task 1:\n\n{task['prompt']}\n\n"
            "Read the attached visual for a marker who will never see it and must check essays against your notes.\n"
            "- visual_type: e.g. bar chart, line graph, pie chart, table, map, process diagram.\n"
            "- For a chart, graph or table: unit is the unit of the figures; data_table has one header line and "
            "one line per category or series, cells separated by ' | ', with every figure shown. Give a figure "
            "read off an axis as an approximation (about 45). Leave description empty.\n"
            "- For a map, plan or process: leave unit and data_table empty and describe in description what is "
            "shown, in order, completely enough to check an essay against.\n"
            "- key_features: the 3-6 main trends, comparisons or changes a good overview would report.\n"
            "Write only what the visual shows. If any part cannot be read reliably, set cannot_read to true and "
            "say what in reason; otherwise set it to false and leave reason empty."
        )
        content = [{"type": "text", "text": text}] + [image_part(u) for u in task["image_urls"]]
    else:
        text = (
            f"IELTS Writing Task {task['number']}:\n\n{task['prompt']}\n\n"
            "Write marker's notes for this task. There is no visual: set visual_type to 'none', leave unit and "
            "data_table empty, and set cannot_read to false with an empty reason. In description say in one or two "
            "sentences what a response must do to answer the task fully (for example discuss both views and give "
            "an opinion, or cover every bullet of the letter). In key_features list 3-6 points a strong answer "
            "could raise."
        )
        content = [{"type": "text", "text": text}]
    result = ask(client, model, content, RUBRIC_SCHEMA)
    if result["cannot_read"]:
        raise Unusable(f"model could not read the visual: {result['reason']}")
    features = [f.strip() for f in result["key_features"] if f.strip()]
    table, description = result["data_table"].strip(), result["description"].strip()
    if len(features) < 2 or not (table or description):
        raise Unusable("the notes came back without data or key features")
    if table and len(table.splitlines()) < 2:
        raise Unusable("the data table has a header but no rows")
    if table:
        unit = f", {result['unit'].strip()}" if result["unit"].strip() else ""
        head = f"Chart data ({result['visual_type'].strip()}{unit}):\n{table}"
    elif task["image_urls"]:
        head = f"What the {result['visual_type'].strip()} shows: {description}"
    else:
        head = description
    label = "Key features" if task["image_urls"] else "Points a strong answer could raise"
    notes = f"{head}\n\n{label}: " + "; ".join(features)
    if len(notes) > 8000:
        raise Unusable(f"the notes are {len(notes)} characters, over the backend's 8000 limit")
    return {"rubric_notes": notes}


def speaking_answers(client, model, part: dict, band: str) -> dict:
    lines = []
    for i, q in enumerate(part["questions"], 1):
        lines.append(f"{i}. {q['text']}" + (f"\n   Cue card: {q['cue_card']}" if q.get("cue_card") else ""))
    text = (
        f"IELTS Speaking Part {part['number']}. Target band: {band}.\n"
        f"{SPEAKING_LENGTH.get(part['number'], '')}\n"
        "Write what the candidate would say, in natural spoken English. Answer every question, "
        "using the question numbers below.\n\n" + "\n".join(lines)
    )
    result = ask(client, model, [{"type": "text", "text": text}], SPEAKING_SCHEMA)
    by_number = {a["question_number"]: a["answer"].strip() for a in result["answers"]}
    expected = set(range(1, len(part["questions"]) + 1))
    if set(by_number) != expected or not all(by_number.values()):
        raise Unusable(f"expected answers for questions {sorted(expected)}, got {sorted(by_number)}")
    return {"answers": [{"question": q["text"], "answer": by_number[i]} for i, q in enumerate(part["questions"], 1)]}


def jobs(collection: dict, skills: set[str], bands: list[str], rubrics: bool):
    """Yield (key, callable) for every answer this collection should have."""
    for test in collection["tests"]:
        sections = test["sections"]
        if rubrics and "writing" in sections:
            for task in sections["writing"]["tasks"]:
                yield f"{test['name']}|writing|task{task['number']}|rubric", (rubric_notes, task, None)
        if "writing" in skills and "writing" in sections:
            for task in sections["writing"]["tasks"]:
                for band in bands:
                    yield f"{test['name']}|writing|task{task['number']}|band{band}", (writing_answer, task, band)
        if "speaking" in skills and "speaking" in sections:
            for part in sections["speaking"]["parts"]:
                if not part["questions"]:
                    continue
                for band in bands:
                    yield f"{test['name']}|speaking|part{part['number']}|band{band}", (speaking_answers, part, band)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, required=True, help="folder of JSON files written by the extension")
    parser.add_argument("--output", type=Path, required=True, help="folder for the *.answers.json files")
    parser.add_argument("--model", required=True, help="OpenAI model id; it must accept image input for Writing Task 1")
    parser.add_argument("--bands", nargs="+", default=["7.0", "8.5"], help="target bands (default: 7.0 8.5)")
    parser.add_argument("--skills", nargs="+", choices=["writing", "speaking"], default=["writing", "speaking"])
    parser.add_argument("--skip-rubric", action="store_true", help="do not write Writing rubric notes")
    parser.add_argument("--limit", type=int, help="stop after this many new answers (for a trial run)")
    args = parser.parse_args()

    files = sorted(p for p in args.input.glob("*.json") if not p.name.endswith(".answers.json"))
    if not files:
        print(f"No collection JSON files in {args.input}", file=sys.stderr)
        return 2
    args.output.mkdir(parents=True, exist_ok=True)
    client = openai.OpenAI(max_retries=5)  # the SDK backs off and retries 429s, 5xx and dropped connections

    generated = skipped = 0
    failures: list[str] = []
    for path in files:
        collection = json.loads(path.read_text(encoding="utf-8"))
        out_path = args.output / f"{path.stem}.answers.json"
        out = json.loads(out_path.read_text(encoding="utf-8")) if out_path.exists() else {
            "collection": collection["collection"], "answers": {},
        }
        for key, (make, item, band) in jobs(collection, set(args.skills), args.bands, not args.skip_rubric):
            if key in out["answers"]:
                skipped += 1
                continue
            if args.limit is not None and generated >= args.limit:
                print(f"Reached --limit {args.limit}; stopping.")
                return report(generated, skipped, failures, stopped_early=True)
            try:
                answer = make(client, args.model, item, band)
            except (openai.AuthenticationError, openai.PermissionDeniedError, openai.NotFoundError) as e:
                # Wrong key or model id: every later request would fail the same way.
                print(f"Stopping: {e}", file=sys.stderr)
                failures.append(f"{path.stem}|{key}: {e}")
                return report(generated, skipped, failures, stopped_early=True)
            except (openai.APIError, Unusable, OSError) as e:
                failures.append(f"{path.stem}|{key}: {e}")
                print(f"FAILED  {path.stem}|{key}: {e}", file=sys.stderr)
                continue
            out["answers"][key] = {
                "ai_generated": True,
                "model": args.model,
                **({"target_band": band} if band else {}),
                "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                **answer,
            }
            out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
            generated += 1
            print(f"ok      {path.stem}|{key}")
    return report(generated, skipped, failures, stopped_early=False)


def report(generated: int, skipped: int, failures: list[str], stopped_early: bool) -> int:
    print(f"\n{generated} generated, {skipped} already present, {len(failures)} failed"
          + (" (stopped before the end)" if stopped_early else ""))
    for f in failures:
        print(f"  - {f}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
