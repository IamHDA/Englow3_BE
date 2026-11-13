#!/usr/bin/env python3
"""Run the whole flow: crawl -> AI answers and rubric notes -> set folders -> audio -> load into Englow3.

    npm install && pip install -r requirements.txt        (once; ffmpeg must be on PATH)

    export OPENAI_API_KEY=...        # optional: without it, or without --model, the AI step is skipped
    export ENGLOW_TOKEN=...          # an ADMIN access token when --publish is used
    python run_all.py --base-url https://api.example.com --model <openai model> --publish [--match 2026] [--limit 2]

Everything lands under --work (default ./data): scrape/, answers/, sets/. Each step skips what it has
already done, so the same command can be run again after a failure or to pick up new collections.
A step that fails for some items does not stop the run; the summary at the end says which steps had
failures, and each set's source.json says what happened to it.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--work", type=Path, default=Path("data"), help="folder for everything produced")
    parser.add_argument("--match", help="only collections whose title or URL contains one of these, comma-separated")
    parser.add_argument("--limit", type=int, help="at most this many collections")
    parser.add_argument("--model", help="OpenAI model id for sample answers and rubric notes (must read images)")
    parser.add_argument("--base-url", help="Englow3 backend root; without it the run stops after building the sets")
    parser.add_argument("--publish", action="store_true", help="approve everything and build the full tests (ADMIN token)")
    args = parser.parse_args()

    missing = [tool for tool in ("node", "ffmpeg", "ffprobe") if not shutil.which(tool)]
    if missing:
        print(f"Not on PATH: {', '.join(missing)}", file=sys.stderr)
        return 2
    if not (HERE / "node_modules" / "jsdom").is_dir():
        print(f"Run `npm install` in {HERE} first.", file=sys.stderr)
        return 2
    if args.base_url and not os.environ.get("ENGLOW_TOKEN"):
        print("Set ENGLOW_TOKEN, or leave out --base-url to stop after building the sets.", file=sys.stderr)
        return 2

    scrape, answers, sets = args.work / "scrape", args.work / "answers", args.work / "sets"
    py = [sys.executable]
    crawl = ["node", str(HERE / "crawl.mjs"), "--out", str(scrape)]
    if args.match:
        crawl += ["--match", args.match]
    if args.limit:
        crawl += ["--limit", str(args.limit)]
    convert = py + [str(HERE / "to_englow_sets.py"), "--input", str(scrape), "--output", str(sets)]
    steps = [("Crawl", crawl)]
    if args.model and os.environ.get("OPENAI_API_KEY"):
        steps.append(("AI answers and rubric notes",
                      py + [str(HERE / "generate_answers.py"), "--input", str(scrape), "--output", str(answers), "--model", args.model]))
        convert += ["--answers", str(answers)]
    else:
        print("No --model or no OPENAI_API_KEY: skipping AI answers and rubric notes.\n")
    steps.append(("Build set folders", convert))
    steps.append(("Cut Listening audio", py + [str(HERE / "split_audio.py"), "--input", str(scrape), "--sets", str(sets)]))
    if args.base_url:
        load = py + [str(HERE / "load_sets.py"), "--base-url", args.base_url, "--sets", str(sets), "--allow-silent"]
        steps.append(("Load into Englow3", load + (["--publish"] if args.publish else [])))

    outcome = []
    for name, command in steps:
        print(f"\n=== {name} ===", flush=True)
        code = subprocess.run(command).returncode
        outcome.append((name, code))
        if code not in (0, 1):  # 1 means some items failed; anything else means the step could not run at all
            break
    print("\n=== Summary ===")
    for name, code in outcome:
        print(f"{'ok' if code == 0 else 'some failed' if code == 1 else 'DID NOT RUN'} : {name}")
    for name, _ in steps[len(outcome):]:
        print(f"not attempted : {name}")
    if not args.base_url:
        print(f"Sets are in {sets}; pass --base-url to load them.")
    return max(code for _, code in outcome)


if __name__ == "__main__":
    sys.exit(main())
