#!/usr/bin/env python3
"""Write a complete IELTS Academic full test set with an AI model, check it, and get it ready to load.

One run: plan seven fresh topics (none from seeds/ielts/topics.json) -> write 4 Listening parts,
3 Reading passages, Writing Task 1 and 2 and a Speaking test -> check each against ielts.rules, sending
the problems back to the model until it is right (up to --attempts) -> build the paper exactly as the
admin editor saves it -> draw the Task 1 chart -> optionally record the Listening audio.

Output, in data_pipeline/output/ielts/<set>/ (git-ignored):
    paper.json  tasks.json  transcript.md  voices.json  task1-chart.png  report.md  audio/part-N.mp3

    export IELTS_LLM_BASE_URL=https://api.openai.com/v1 IELTS_LLM_API_KEY=... IELTS_LLM_MODEL=gpt-4.1
    python data_pipeline/generators/generate_ielts_set.py --record

Then load it as drafts (the command is printed at the end) and review it in the admin before approving.
A component the model cannot get right is written to data_pipeline/rejects/ielts/<set>/ and nothing
is kept; the topics are only recorded once the whole set passes.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import UTC, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "generators"))

from ielts import build, chart, prompts, rules, topics  # noqa: E402
from ielts.llm import LlmError, OpenAiCompatible, Writer  # noqa: E402

OUTPUT = ROOT / "output" / "ielts"
REJECTS = ROOT / "rejects" / "ielts"

# The kinds each component must include, so that every question type appears somewhere in the paper.
LISTENING_KINDS = {1: ["gap"], 2: ["single", "matching"], 3: ["multi", "matching"], 4: ["gap"]}
READING_KINDS = {1: ["tfng", "gap"], 2: ["matching", "single"], 3: ["ynng"]}


class Rejected(Exception):
    def __init__(self, component: str, reply: dict | None, problems: list[str]):
        super().__init__(f"{component}: {len(problems)} problem(s) left")
        self.component, self.reply, self.problems = component, reply, problems


def write(writer: Writer, component: str, messages: list[dict], check, attempts: int, log: list[str]) -> dict:
    """Ask, check, and send the problems back until the reply passes or the attempts run out."""
    reply: dict | None = None
    problems: list[str] = []
    for attempt in range(1, attempts + 1):
        try:
            reply = writer.complete(messages)
        except (LlmError, ValueError) as error:
            problems = [f"The reply could not be read: {error}"]
            messages = messages + [prompts.fix(problems)]
            continue
        problems = check(reply)
        log.append(f"{component}: attempt {attempt}, {len(problems)} problem(s)")
        if not problems:
            return reply
        messages = messages + [prompts.as_message(reply), prompts.fix(problems)]
    raise Rejected(component, reply, problems)


def requires(kinds: list[str]):
    def check(spec: dict) -> list[str]:
        present = {s.get("kind") for s in spec.get("sets", [])}
        return [f"include a {kind!r} question set here" for kind in kinds if kind not in present]
    return check


def check_plan(used: list[dict]):
    def check(plan: dict) -> list[str]:
        problems = []
        if len(plan.get("listening") or []) != 4 or len(plan.get("reading") or []) != 3:
            problems.append("give exactly 4 listening topics and 3 reading topics")
        for field in ("title", "writing_task1", "writing_task2", "speaking"):
            if not str(plan.get(field, "")).strip():
                problems.append(f"give {field}")
        chosen = list(plan.get("listening") or []) + list(plan.get("reading") or []) + [
            plan.get("writing_task1", ""), plan.get("writing_task2", ""), plan.get("speaking", "")]
        seen: list[dict] = []
        for topic in [str(t) for t in chosen if str(t).strip()]:
            repeat = topics.taken(topic, used) or topics.taken(topic, seen)
            if repeat:
                problems.append(f"{topic!r} repeats a topic already used ({repeat!r}); choose another")
            seen.append({"topic": topic})
        return problems
    return check


def slugify(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:40] or "set"


def generate(writer: Writer, out_root: Path = OUTPUT, rejects_root: Path = REJECTS, registry: Path = topics.REGISTRY,
             attempts: int = 3, min_words: int = rules.MIN_PASSAGE_WORDS, draw_chart: bool = True) -> Path:
    log: list[str] = []
    used = topics.load(registry)
    plan = write(writer, "plan", prompts.plan([u["topic"] for u in used]), check_plan(used), attempts, log)
    set_id = f"ai-{datetime.now(UTC):%Y%m%d-%H%M}-{slugify(plan['title'])}"
    try:
        listening = {}
        for n in range(1, 5):
            listening[n] = write(
                writer, f"listening-{n}", prompts.listening(n, plan["listening"][n - 1], rules.LISTENING_MARKS[n], LISTENING_KINDS[n]),
                lambda spec, n=n: rules.check_listening_part(n, spec) + requires(LISTENING_KINDS[n])(spec),
                attempts, log)
        reading = {}
        for n in range(1, 4):
            reading[n] = write(
                writer, f"reading-{n}",
                prompts.reading(n, plan["reading"][n - 1], rules.READING_MARKS[n], min_words, READING_KINDS[n]),
                lambda spec, n=n: rules.check_reading_passage(n, spec, min_words) + requires(READING_KINDS[n])(spec),
                attempts, log)
        task1 = write(writer, "writing-1", prompts.task1(plan["writing_task1"]), rules.check_task1, attempts, log)
        task2 = write(writer, "writing-2", prompts.task2(plan["writing_task2"]), rules.check_task2, attempts, log)
        speaking = write(writer, "speaking", prompts.speaking(plan["speaking"]), rules.check_speaking, attempts, log)
    except Rejected as rejected:
        folder = rejects_root / set_id
        folder.mkdir(parents=True, exist_ok=True)
        (folder / f"{rejected.component}.json").write_text(json.dumps(
            {"problems": rejected.problems, "last_reply": rejected.reply}, indent=2, ensure_ascii=False),
            encoding="utf-8")
        raise SystemExit(f"{rejected.component} still failed after {attempts} attempts; see {folder}") from rejected

    mix = rules.check_paper_mix(listening, reading)
    if mix:
        raise SystemExit("The paper misses question types: " + "; ".join(mix))

    title = f"IELTS Academic AI Set: {plan['title']}"
    folder = out_root / set_id
    folder.mkdir(parents=True, exist_ok=True)
    paper = build.build_paper(f"{title} - Listening and Reading", listening, reading)
    tasks = build.build_tasks(plan["title"], task1, task2, speaking, "task1-chart.png")
    (folder / "paper.json").write_text(json.dumps(paper, indent=2, ensure_ascii=False), encoding="utf-8")
    (folder / "tasks.json").write_text(json.dumps(tasks, indent=2, ensure_ascii=False), encoding="utf-8")
    (folder / "transcript.md").write_text(build.transcript_markdown(title, listening), encoding="utf-8")
    (folder / "voices.json").write_text(json.dumps(build.voices(listening), indent=2), encoding="utf-8")
    (folder / "components.json").write_text(json.dumps(
        {"plan": plan, "listening": listening, "reading": reading, "task1": task1, "task2": task2,
         "speaking": speaking}, indent=2, ensure_ascii=False), encoding="utf-8")
    if draw_chart:
        chart.draw(task1["chart"], folder / "task1-chart.png")
    (folder / "report.md").write_text(
        f"# {title}\n\nSet `{set_id}`, written {datetime.now(UTC):%Y-%m-%d %H:%M} UTC.\n\n"
        "## Topics\n\n" + "\n".join(f"- {k}: {v}" for k, v in plan.items() if k != "title") +
        "\n\n## Checks\n\n" + "\n".join(f"- {line}" for line in log) + "\n", encoding="utf-8")

    slots = {f"listening-{i + 1}": t for i, t in enumerate(plan["listening"])}
    slots |= {f"reading-{i + 1}": t for i, t in enumerate(plan["reading"])}
    slots |= {"writing-1": plan["writing_task1"], "writing-2": plan["writing_task2"], "speaking": plan["speaking"]}
    topics.record(set_id, slots, registry)
    return folder


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--attempts", type=int, default=3, help="tries per component before giving up")
    parser.add_argument("--min-words", type=int, default=rules.MIN_PASSAGE_WORDS, help="shortest reading passage")
    parser.add_argument("--record", action="store_true", help="also record the Listening audio (edge-tts)")
    args = parser.parse_args()

    folder = generate(OpenAiCompatible.from_env(), attempts=args.attempts, min_words=args.min_words)
    print(f"Set written: {folder}")
    if args.record:
        from synthesize_ielts_trial_audio import record_all, voices_from

        record_all(folder / "transcript.md", folder / "audio", "part-{}.mp3", voices_from(folder / "voices.json"))
    rel = folder.relative_to(ROOT.parent)
    print("\nLoad it as drafts (with an admin token in ENGLOW_TOKEN):")
    print(f"  python scripts/load_ielts_trial.py --base-url <backend> --set-dir {rel}")
    print("Then review every question in the admin before approving - the model can still be wrong.")


if __name__ == "__main__":
    main()
