#!/usr/bin/env python3
"""Check a hand-written IELTS Academic full test set and build it the way the AI generator does.

A source folder holds one JSON file per step, in exactly the shapes the AI writer replies with
(ai_service/app/generation/ielts/prompts.py):

    plan.json  listening-1.json ... listening-4.json  reading-1.json ... reading-3.json
    writing-1.json  writing-2.json  speaking.json

Every component goes through the same checks as an AI-written one (ielts.steps.check, then the
paper-wide mix of question types). Nothing is built until all of them pass; the problems are printed
in the words the checker sends back to the model, so they say what to change.

Sources are authored material, not output: keep them in data_pipeline/sources/ielts/ (git-ignored).

    python data_pipeline/generators/assemble_ielts_set.py data_pipeline/sources/ielts/test-01 --check-only
    python data_pipeline/generators/assemble_ielts_set.py data_pipeline/sources/ielts/test-01 \
        --title "IELTS Academic Mock Test 01"

The built set lands in data_pipeline/output/ielts/<set>/ like a generated one, so recording the audio
and loading it as drafts work the same way (the commands are printed at the end).
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "generators"))

from generate_ielts_set import OUTPUT, save_set, split  # noqa: E402
from ielts import rules, steps, topics  # noqa: E402

DESCRIPTION = (
    "IELTS Academic Listening and Reading practice paper in the format of the real test: "
    "four Listening parts (40 questions) and three Reading passages (40 questions)."
)


def load(source: Path) -> dict[str, dict]:
    missing = [step for step in steps.WRITING_STEPS if not (source / f"{step}.json").is_file()]
    if missing:
        raise SystemExit(f"{source}: missing {', '.join(f'{m}.json' for m in missing)}")
    return {step: json.loads((source / f"{step}.json").read_text(encoding="utf-8")) for step in steps.WRITING_STEPS}


def problems(components: dict[str, dict], set_id: str, registry: Path = topics.REGISTRY) -> list[str]:
    """Every problem in the set; topics this same set recorded on an earlier run do not count as used."""
    used = [u["topic"] for u in topics.load(registry) if u.get("set") != set_id]
    found = [f"{step}: {p}" for step in steps.WRITING_STEPS for p in steps.check(step, components[step], used)]
    if not found:
        found = rules.check_paper_mix(*split(components)[1:3])
    return found


def record_topics(set_id: str, plan: dict, registry: Path = topics.REGISTRY) -> None:
    """Add the set's topics to the registry, replacing what an earlier run of the same set recorded."""
    kept = [u for u in topics.load(registry) if u.get("set") != set_id]
    registry.write_text(json.dumps(kept, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    slots = {f"listening-{i + 1}": t for i, t in enumerate(plan["listening"])}
    slots |= {f"reading-{i + 1}": t for i, t in enumerate(plan["reading"])}
    slots |= {"writing-1": plan["writing_task1"], "writing-2": plan["writing_task2"], "speaking": plan["speaking"]}
    topics.record(set_id, slots, registry)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", type=Path, help="folder with the eleven component files")
    parser.add_argument("--title", help="paper title (default: the plan's title)")
    parser.add_argument("--check-only", action="store_true", help="report problems, build nothing")
    args = parser.parse_args()

    set_id = f"authored-{args.source.resolve().name}"
    components = load(args.source)
    found = problems(components, set_id)
    if found:
        print(f"{len(found)} problem(s):")
        print("\n".join(f"- {p}" for p in found))
        raise SystemExit(1)
    print(f"{args.source}: all checks pass")
    if args.check_only:
        return

    plan = components[steps.PLAN]
    title = args.title or plan["title"]
    folder = save_set(components, OUTPUT / set_id, set_id, title, ["hand-written; every check passed"],
                      description=DESCRIPTION)
    record_topics(set_id, plan)
    rel = folder.relative_to(ROOT.parent)
    print(f"Set built: {rel}")
    print(f"Record:  python data_pipeline/generators/synthesize_ielts_trial_audio.py --set-dir {rel}")
    print(f"Load:    python scripts/load_ielts_trial.py --base-url <backend> --set-dir {rel}")


if __name__ == "__main__":
    main()
