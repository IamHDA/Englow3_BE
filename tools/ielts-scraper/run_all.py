#!/usr/bin/env python3
"""Run the whole flow: crawl -> AI answers and rubric notes -> set folders -> audio -> load into Englow3.

    npm install && pip install -r requirements.txt        (once; ffmpeg must be on PATH)

    export OPENAI_API_KEY=...        # optional: without it, or without --model, the AI step is skipped
    export OPENAI_BASE_URL=...       # optional: an OpenAI-compatible provider such as Gemini or Groq
    export SUPABASE_URL=... SUPABASE_PUBLISHABLE_KEY=... ENGLOW_EMAIL=... ENGLOW_PASSWORD=...
                                     # an ADMIN account when --publish is used (see supabase_auth.py)
    python run_all.py --base-url https://api.example.com --model <openai model> --publish [--match 2026] [--limit 2]

Everything lands under --work (default ./data): scrape/, answers/, sets/. Each step skips what it has
already done, so the same command can be run again after a failure or to pick up new collections.
A step that fails for some items does not stop the run; the summary at the end says which steps had
failures, and each set's source.json says what happened to it.
"""

from __future__ import annotations

import argparse
import importlib.util
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
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
    # The steps run with this same interpreter, so the packages must be installed for it.
    absent = [name for module, name in (("PIL", "pillow"), ("openai", "openai"))
              if importlib.util.find_spec(module) is None and (module != "openai" or args.model)]
    if absent:
        print(f"Missing Python packages for {sys.executable}: {', '.join(absent)}\n"
              f"Install them with:  \"{sys.executable}\" -m pip install -r \"{HERE / 'requirements.txt'}\"",
              file=sys.stderr)
        return 2
    if args.base_url:
        health = args.base_url.rstrip("/") + "/actuator/health"
        try:
            print(f"Checking the backend at {args.base_url} (a sleeping Render service can take a minute to wake)...")
            urllib.request.urlopen(health, timeout=120).close()
        except urllib.error.HTTPError:
            pass  # the server answered; whether health is public does not matter here
        except (urllib.error.URLError, OSError) as e:
            reason = getattr(e, "reason", e)
            print(f"Cannot reach the backend at {args.base_url} ({reason}).\n"
                  "Check --base-url: a local backend is usually http://localhost:8080, and it must be running.",
                  file=sys.stderr)
            return 2
        sys.path.insert(0, str(HERE))
        from supabase_auth import Auth, AuthError

        try:
            Auth()  # checks the variables are there; load_sets.py signs in itself
        except AuthError as e:
            print(f"{e}\nOr leave out --base-url to stop after building the sets.", file=sys.stderr)
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
    answers_cut_short = False
    for name, command in steps:
        if name == "Load into Englow3" and answers_cut_short:
            # Tasks are created with whatever answers exist, and a later run never updates a submitted task,
            # so loading now would leave these sets without sample answers and rubric notes for good.
            print(f"\n=== {name} ===\nNot loading: the AI step stopped before the end, so some tasks have no sample "
                  "answer or rubric notes yet. Run the same command again once the provider's quota resets.")
            break
        print(f"\n=== {name} ===", flush=True)
        code = subprocess.run(command).returncode
        if name == "AI answers and rubric notes" and code == 3:  # generate_answers.CUT_SHORT
            answers_cut_short, code = True, 1
        outcome.append((name, code))
        if code not in (0, 1):  # 1 means some items failed; anything else means the step could not run at all
            break
    print("\n=== Summary ===")
    for name, code in outcome:
        label = {0: "ok", 1: "some failed or need a look"}.get(code, "DID NOT RUN")
        print(f"{label} : {name}")
    for name, _ in steps[len(outcome):]:
        print(f"not attempted : {name}")
    if not args.base_url:
        print(f"Sets are in {sets}; pass --base-url to load them.")
    return max(code for _, code in outcome)


if __name__ == "__main__":
    sys.exit(main())
