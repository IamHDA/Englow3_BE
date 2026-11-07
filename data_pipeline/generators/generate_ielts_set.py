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

from ielts import build, chart, prompts, rules, steps, topics  # noqa: E402
from ielts.llm import LlmError, OpenAiCompatible, Writer  # noqa: E402

OUTPUT = ROOT / "output" / "ielts"
REJECTS = ROOT / "rejects" / "ielts"



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


def slugify(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:40] or "set"


def generate(writer: Writer, out_root: Path = OUTPUT, rejects_root: Path = REJECTS, registry: Path = topics.REGISTRY,
             attempts: int = 3, min_words: int = rules.MIN_PASSAGE_WORDS, draw_chart: bool = True,
             brief: str | None = None) -> Path:
    log: list[str] = []
    used = [u["topic"] for u in topics.load(registry)]
    components: dict[str, dict] = {}
    set_id = None
    try:
        for step in steps.WRITING_STEPS:
            components[step] = write(
                writer, step,
                steps.messages(step, components.get("plan"), brief, used if step == steps.PLAN else [], min_words),
                lambda reply, s=step: steps.check(s, reply, used, min_words),
                attempts, log)
            if step == steps.PLAN:
                set_id = f"ai-{datetime.now(UTC):%Y%m%d-%H%M}-{slugify(components[step]['title'])}"
    except Rejected as rejected:
        folder = rejects_root / (set_id or f"ai-{datetime.now(UTC):%Y%m%d-%H%M}-unplanned")
        folder.mkdir(parents=True, exist_ok=True)
        (folder / f"{rejected.component}.json").write_text(json.dumps(
            {"problems": rejected.problems, "last_reply": rejected.reply}, indent=2, ensure_ascii=False),
            encoding="utf-8")
        raise SystemExit(f"{rejected.component} still failed after {attempts} attempts; see {folder}") from rejected

    plan = components[steps.PLAN]
    listening = {int(s[-1]): components[s] for s in steps.LISTENING}
    reading = {int(s[-1]): components[s] for s in steps.READING}
    task1, task2, speaking = components[steps.WRITING_1], components[steps.WRITING_2], components[steps.SPEAKING]
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
    parser.add_argument("--brief", help="what you want: topics, difficulty, question types (the rules still apply)")
    args = parser.parse_args()

    folder = generate(OpenAiCompatible.from_env(), attempts=args.attempts, min_words=args.min_words,
                      brief=args.brief)
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
