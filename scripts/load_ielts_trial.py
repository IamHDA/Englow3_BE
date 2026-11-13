"""Load the IELTS trial paper and its five Writing and Speaking tasks into a running backend.

Everything goes in through the admin API, exactly as the editor would save it, and is sent for review:
an administrator then approves each item on the admin screens and builds the full test at
/admin/full-tests. Nothing is published by this script.

Usage (standard library only):

    ENGLOW_TOKEN=<a staff or admin access token> python scripts/load_ielts_trial.py --base-url https://api.example.com

Listening audio: record it first with data_pipeline/generators/synthesize_ielts_trial_audio.py. When
the four part files are there, the paper is created as a draft, the files are uploaded and attached to
Parts 1-4, and only then is it sent for review. Without them the paper goes in silent.

--set-dir loads an AI-written set from data_pipeline/generators/generate_ielts_set.py instead of the
trial paper: its folder holds paper.json, tasks.json, task1-chart.png and audio/part-N.mp3.

--skip-tasks loads the paper alone, for when the five tasks are already in (a published paper cannot be
edited, so adding audio to one already loaded means loading a new copy and building a new full test).

The token is the Supabase access token of a STAFF or ADMIN account (for example copied from the
browser's network tab while signed in to the admin site). Run it once: a second run creates a second
copy.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

RESOURCES = Path(__file__).resolve().parent.parent / "src" / "test" / "resources" / "ielts"
PAPER = RESOURCES / "academic-trial-paper-1.json"
TASKS = RESOURCES / "academic-trial-tasks.json"
AUDIO = Path(__file__).resolve().parent.parent / "data_pipeline" / "output" / "audio" / "ielts"
AUDIO_STEM = "academic-trial-paper-1-part-{}.mp3"


def call(base, token, method, path, body=None, content_type="application/json"):
    data = None
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
    request = urllib.request.Request(base + path, data=data, method=method)
    request.add_header("Authorization", "Bearer " + token)
    if data is not None:
        request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            text = response.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        sys.exit(f"{method} {path} failed with {error.code}: {error.read().decode()[:500]}")


def multipart(field, filename, content_type, payload):
    boundary = uuid.uuid4().hex
    head = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
            f"Content-Type: {content_type}\r\n\r\n").encode()
    return head + payload + f"\r\n--{boundary}--\r\n".encode(), f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base-url", required=True, help="backend root, e.g. https://api.example.com")
    parser.add_argument("--skip-tasks", action="store_true", help="load the paper only")
    parser.add_argument("--audio-dir", type=Path, default=AUDIO, help="where the part MP3s are")
    parser.add_argument("--set-dir", type=Path, help="an AI-written set folder to load instead of the trial")
    args = parser.parse_args()
    paper_file, tasks_file, assets, audio_dir, audio_stem = PAPER, TASKS, RESOURCES, args.audio_dir, AUDIO_STEM
    if args.set_dir:
        paper_file, tasks_file, assets = args.set_dir / "paper.json", args.set_dir / "tasks.json", args.set_dir
        audio_dir, audio_stem = args.set_dir / "audio", "part-{}.mp3"
    token = os.environ.get("ENGLOW_TOKEN")
    if not token:
        sys.exit("Set ENGLOW_TOKEN to a STAFF or ADMIN access token.")
    base = args.base_url.rstrip("/")

    paper = json.loads(paper_file.read_text(encoding="utf-8"))
    listening = next(s for s in paper["content"]["sections"] if s["sectionType"] == "LISTENING")
    audio = [audio_dir / audio_stem.format(n) for n in range(1, len(listening["parts"]) + 1)]
    missing = [path.name for path in audio if not path.is_file()]
    if missing:
        print(f"No audio for {', '.join(missing)}: the Listening section goes in silent.")

    exam = call(base, token, "POST", "/api/admin/exams/authoring", paper)
    if not missing:
        for part, path in zip(listening["parts"], audio):
            body, content_type = multipart("file", path.name, "audio/mpeg", path.read_bytes())
            uploaded = call(base, token, "POST", f"/api/admin/exams/{exam['id']}/media", body, content_type)
            part["audioObjectKey"] = uploaded["objectKey"]
        exam = call(base, token, "PUT", f"/api/admin/exams/{exam['id']}/authoring",
                    {**paper, "version": exam["version"]})
        print(f"Audio attached to {len(audio)} Listening parts.")
    call(base, token, "POST", f"/api/admin/exams/{exam['id']}/submit-for-review")
    print(f"Paper sent for review: {paper['metadata']['title']} ({exam['id']})")
    if args.skip_tasks:
        print("\nNext: approve the paper, then build a full test with it at /admin/full-tests.")
        return

    for task in json.loads(tasks_file.read_text(encoding="utf-8")):
        chart = task.pop("chart", None)
        created = call(base, token, "POST", "/api/admin/assessments/tasks", task)
        if chart:
            body, content_type = multipart("file", chart, "image/png", (assets / chart).read_bytes())
            call(base, token, "POST", f"/api/admin/assessments/tasks/{created['id']}/image", body, content_type)
        call(base, token, "POST", f"/api/admin/assessments/tasks/{created['id']}/submit")
        print(f"Task sent for review: {task['title']} ({created['id']})")

    print("\nNext: approve the paper and the five tasks, then build the full test at /admin/full-tests.")
    if missing:
        print("Listening has no audio: record it with data_pipeline/generators/synthesize_ielts_trial_audio.py.")


if __name__ == "__main__":
    main()
