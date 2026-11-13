#!/usr/bin/env python3
"""Load the set folders into a running backend through the admin API and submit them for review.

    ENGLOW_TOKEN=<staff or admin access token> python load_sets.py --base-url https://api.example.com --sets sets

Does what scripts/load_ielts_trial.py --set-dir does for one folder, for every folder, and also
uploads the question-set images listed in images.json. Nothing is published: each paper and task
waits for an administrator's approval.

What was created is written to each folder's source.json, so a second run skips finished sets and
carries on with unfinished ones. A set whose Listening audio is missing is skipped unless
--allow-silent is given, because a paper cannot get its audio after it has been approved.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path


class ApiError(Exception):
    pass


def call(base: str, token: str, method: str, path: str, body=None, content_type: str = "application/json"):
    data = None
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
    request = urllib.request.Request(base + path, data=data, method=method)
    request.add_header("Authorization", "Bearer " + token)
    if data is not None:
        request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            text = response.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        raise ApiError(f"{method} {path} failed with {error.code}: {error.read().decode()[:400]}") from error
    except urllib.error.URLError as error:
        raise ApiError(f"{method} {path} failed: {error.reason}") from error


def upload(base: str, token: str, path: str, file: Path, content_type: str):
    boundary = uuid.uuid4().hex
    head = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{file.name}\"\r\n"
            f"Content-Type: {content_type}\r\n\r\n").encode()
    body = head + file.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    return call(base, token, "POST", path, body, f"multipart/form-data; boundary={boundary}")


def load_paper(base: str, token: str, folder: Path, state: dict, save) -> None:
    paper = json.loads((folder / "paper.json").read_text(encoding="utf-8"))
    sections = {s["sectionType"]: s for s in paper["content"]["sections"]}
    audio = [folder / "audio" / f"part-{n}.mp3" for n in range(1, len(sections["LISTENING"]["parts"]) + 1)]
    images = json.loads((folder / "images.json").read_text(encoding="utf-8")) if (folder / "images.json").is_file() else []

    exam = call(base, token, "POST", "/api/admin/exams/authoring", paper)
    state["exam_id"] = exam["id"]  # recorded at once: a failure below leaves a draft that must not be duplicated
    save()
    media = f"/api/admin/exams/{exam['id']}/media"
    if all(path.is_file() for path in audio):
        for part, path in zip(sections["LISTENING"]["parts"], audio):
            part["audioObjectKey"] = upload(base, token, media, path, "audio/mpeg")["objectKey"]
    for image in images:
        part = next(p for p in sections[image["section"]]["parts"] if p["orderNo"] == image["part"])
        target = part if image["set"] is None else next(s for s in part["questionSets"] if s["orderNo"] == image["set"])
        target["imageObjectKey"] = upload(base, token, media, folder / image["file"], "image/png")["objectKey"]
    call(base, token, "PUT", f"/api/admin/exams/{exam['id']}/authoring", {**paper, "version": exam["version"]})
    call(base, token, "POST", f"/api/admin/exams/{exam['id']}/submit-for-review")
    state["exam_submitted"] = True
    save()


def load_tasks(base: str, token: str, folder: Path, state: dict, save) -> None:
    done = state.setdefault("tasks", {})
    for task in json.loads((folder / "tasks.json").read_text(encoding="utf-8")):
        key = task["taskType"]
        entry = done.get(key)
        if entry and entry["submitted"]:
            continue
        chart = task.pop("chart", None)
        if not entry:
            entry = done[key] = {"id": call(base, token, "POST", "/api/admin/assessments/tasks", task)["id"], "submitted": False}
            save()
        # Uploading the image again on a retry replaces it, so these two steps can simply be repeated.
        if chart:
            upload(base, token, f"/api/admin/assessments/tasks/{entry['id']}/image", folder / chart, "image/png")
        call(base, token, "POST", f"/api/admin/assessments/tasks/{entry['id']}/submit")
        entry["submitted"] = True
        save()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True, help="backend root, e.g. https://api.example.com")
    parser.add_argument("--sets", type=Path, required=True, help="folder of set folders")
    parser.add_argument("--only", help="load this one set folder")
    parser.add_argument("--allow-silent", action="store_true", help="also load sets that have no Listening audio")
    args = parser.parse_args()
    token = os.environ.get("ENGLOW_TOKEN")
    if not token:
        print("Set ENGLOW_TOKEN to a STAFF or ADMIN access token.", file=sys.stderr)
        return 2
    base = args.base_url.rstrip("/")

    folders = sorted(f for f in args.sets.iterdir() if (f / "paper.json").is_file() and (not args.only or f.name == args.only))
    if not folders:
        print(f"No set folders in {args.sets}", file=sys.stderr)
        return 2
    counts = {"loaded": 0, "already loaded": 0, "skipped": 0, "failed": 0}
    for folder in folders:
        record_path = folder / "source.json"
        record = json.loads(record_path.read_text(encoding="utf-8"))
        state = record.setdefault("load", {})

        def save():
            record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")

        tasks = json.loads((folder / "tasks.json").read_text(encoding="utf-8"))
        tasks_done = all(state.get("tasks", {}).get(t["taskType"], {}).get("submitted") for t in tasks)
        if state.get("exam_submitted") and tasks_done:
            counts["already loaded"] += 1
            continue
        if state.get("exam_id") and not state.get("exam_submitted"):
            counts["skipped"] += 1
            print(f"skipped  {folder.name}: an earlier run left draft paper {state['exam_id']} unfinished. Delete it in "
                  'the admin site, remove "load" from source.json, and run again.')
            continue
        if not (folder / "audio" / "part-1.mp3").is_file() and not args.allow_silent and not state.get("exam_submitted"):
            counts["skipped"] += 1
            print(f"skipped  {folder.name}: no Listening audio (run split_audio.py, or pass --allow-silent)")
            continue
        try:
            if not state.get("exam_submitted"):
                load_paper(base, token, folder, state, save)
            load_tasks(base, token, folder, state, save)
        except (ApiError, OSError) as e:
            counts["failed"] += 1
            print(f"FAILED   {folder.name}: {e}", file=sys.stderr)
            if "401" in str(e) or "403" in str(e):
                print("The token was refused; stopping.", file=sys.stderr)
                break
            continue
        counts["loaded"] += 1
        print(f"loaded   {folder.name}: paper {state['exam_id']}, {len(state['tasks'])} tasks")

    print("\n" + ", ".join(f"{n} {name}" for name, n in counts.items() if n))
    print("Next: approve the papers and tasks in the admin site, then build each full test at /admin/full-tests.")
    return 1 if counts["failed"] or counts["skipped"] else 0


if __name__ == "__main__":
    sys.exit(main())
