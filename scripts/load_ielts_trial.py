"""Load the IELTS trial paper and its five Writing and Speaking tasks into a running backend.

Everything goes in through the admin API, exactly as the editor would save it, and is sent for review:
an administrator then approves each item on the admin screens and builds the full test at
/admin/full-tests. Nothing is published by this script.

Usage (standard library only):

    ENGLOW_TOKEN=<a staff or admin access token> python scripts/load_ielts_trial.py --base-url https://api.example.com

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
    args = parser.parse_args()
    token = os.environ.get("ENGLOW_TOKEN")
    if not token:
        sys.exit("Set ENGLOW_TOKEN to a STAFF or ADMIN access token.")
    base = args.base_url.rstrip("/")

    paper = json.loads(PAPER.read_text(encoding="utf-8"))
    exam = call(base, token, "POST", "/api/admin/exams/authoring", paper)
    call(base, token, "POST", f"/api/admin/exams/{exam['id']}/submit-for-review")
    print(f"Paper sent for review: {paper['metadata']['title']} ({exam['id']})")

    for task in json.loads(TASKS.read_text(encoding="utf-8")):
        chart = task.pop("chart", None)
        created = call(base, token, "POST", "/api/admin/assessments/tasks", task)
        if chart:
            body, content_type = multipart("file", chart, "image/png", (RESOURCES / chart).read_bytes())
            call(base, token, "POST", f"/api/admin/assessments/tasks/{created['id']}/image", body, content_type)
        call(base, token, "POST", f"/api/admin/assessments/tasks/{created['id']}/submit")
        print(f"Task sent for review: {task['title']} ({created['id']})")

    print("\nNext: approve the paper and the five tasks, then build the full test at /admin/full-tests.")
    print("Listening has no audio yet: record it from academic-trial-paper-1.listening-transcript.md first.")


if __name__ == "__main__":
    main()
