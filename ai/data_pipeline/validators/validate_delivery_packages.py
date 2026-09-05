#!/usr/bin/env python3
"""Validate the ten self-contained TOEIC delivery ZIP files."""

from __future__ import annotations

import hashlib
import json
import sys
import zipfile
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from schemas import ExamBatch  # noqa: E402

ZIP_DIR = ROOT / "output" / "exams" / "individual_sets" / "zip"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def expected_media(groups, package_root: str) -> dict[str, str]:
    records = {}
    for group in groups:
        if group.image_url:
            url = str(group.image_url)
            relative = urlparse(url).path.removeprefix("/images/")
            records[url] = f"{package_root}/media/images/{relative}"
        if group.audio and group.audio.audio_url:
            url = str(group.audio.audio_url)
            filename = Path(urlparse(url).path).name
            records[url] = f"{package_root}/media/audio/toeic/listening/{filename}"
    return records


def validate_zip(path: Path, seen_items: set[str]) -> tuple[int, int]:
    package_root = path.stem
    json_name = f"{package_root}/{package_root}.json"
    manifest_name = f"{package_root}/package_manifest.json"
    readme_name = f"{package_root}/README.txt"

    with zipfile.ZipFile(path) as archive:
        bad_member = archive.testzip()
        if bad_member:
            raise RuntimeError(f"{path.name}: CRC failure in {bad_member}")
        names = set(archive.namelist())
        for required in (json_name, manifest_name, readme_name):
            if required not in names:
                raise RuntimeError(f"{path.name}: missing {required}")

        batch = ExamBatch.model_validate_json(archive.read(json_name))
        if len(batch.sets) != 1:
            raise RuntimeError(f"{path.name}: expected exactly one set")
        exam_set = batch.sets[0]
        if (len(exam_set.listening), len(exam_set.reading), exam_set.total_questions) \
                != (100, 100, 200):
            raise RuntimeError(f"{path.name}: invalid 100+100 blueprint")

        questions = [question for group in batch.groups for question in group.questions]
        item_ids = {question.item_id for question in questions}
        ref_ids = {ref.item_id for ref in exam_set.listening + exam_set.reading}
        if len(questions) != 200 or len(item_ids) != 200 or item_ids != ref_ids:
            raise RuntimeError(f"{path.name}: embedded questions do not match its 200 refs")
        overlap = seen_items & item_ids
        if overlap:
            raise RuntimeError(f"{path.name}: reuses {len(overlap)} items from another ZIP")
        seen_items.update(item_ids)

        manifest = json.loads(archive.read(manifest_name))
        media = expected_media(batch.groups, package_root)
        manifest_media = {
            record["source_url"]: record["archive_path"]
            for record in manifest["files"] if "source_url" in record
        }
        if manifest_media != media or manifest["media_files"] != len(media):
            raise RuntimeError(f"{path.name}: package media manifest is incomplete")

        recorded_names = {record["archive_path"] for record in manifest["files"]}
        if names != recorded_names | {manifest_name, readme_name}:
            raise RuntimeError(f"{path.name}: archive contains unrecorded or missing files")
        for record in manifest["files"]:
            data = archive.read(record["archive_path"])
            if len(data) != record["bytes"] or digest(data) != record["sha256"]:
                raise RuntimeError(
                    f"{path.name}: checksum mismatch for {record['archive_path']}")
        return len(media), len(questions)


def main() -> int:
    paths = sorted(ZIP_DIR.glob("toeic_practice_test_*.zip"))
    if len(paths) != 10:
        raise RuntimeError(f"Expected 10 delivery ZIP files, found {len(paths)}")
    seen_items: set[str] = set()
    total_media = 0
    total_questions = 0
    for path in paths:
        media, questions = validate_zip(path, seen_items)
        total_media += media
        total_questions += questions
        print(f"{path.name}: {questions} questions, {media} media files, checksums OK")
    print(
        f"Validated 10/10 ZIPs: {total_questions} questions, "
        f"{len(seen_items)} unique items, {total_media} packaged media files"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
