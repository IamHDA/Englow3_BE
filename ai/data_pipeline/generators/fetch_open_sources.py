#!/usr/bin/env python3
"""Fetch the pinned open-data inputs used by ``build_flashcard_bank.py``."""

from __future__ import annotations

import hashlib
import shutil
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "sources"
TATOEBA_URL = "https://www.manythings.org/anki/vie-eng.zip"
TATOEBA_SHA256 = "2fc68ac8dfe3210d78608eee41c0db2db4d930a7fdfbc76f9745906f2683974e"
THICHHOC_COMMIT = "4d6e92e8bcf8e3e762410c2b0a9f98fea8e62e5b"
THICHHOC_URL = (
    "https://github.com/thichhoc-org/thichhoc-dict/archive/"
    f"{THICHHOC_COMMIT}.zip"
)
MAX_DOWNLOAD_BYTES = 250 * 1024 * 1024
MAX_EXTRACTED_BYTES = 500 * 1024 * 1024
MAX_ARCHIVE_FILES = 20_000


def _download(url: str, destination: Path, expected_sha256: str | None = None) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "Englow3-data-pipeline/1.0"})
    digest = hashlib.sha256()
    total = 0
    with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as stream:
        declared = response.headers.get("Content-Length")
        if declared and int(declared) > MAX_DOWNLOAD_BYTES:
            raise RuntimeError(f"Source archive is too large: {declared} bytes")
        while chunk := response.read(1024 * 1024):
            total += len(chunk)
            if total > MAX_DOWNLOAD_BYTES:
                raise RuntimeError("Source archive exceeded the download limit")
            stream.write(chunk)
            digest.update(chunk)
    if expected_sha256 and digest.hexdigest() != expected_sha256:
        destination.unlink(missing_ok=True)
        raise RuntimeError("Source archive checksum did not match the pinned value")


def _safe_extract(archive: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    root = destination.resolve()
    with zipfile.ZipFile(archive) as bundle:
        entries = bundle.infolist()
        if len(entries) > MAX_ARCHIVE_FILES:
            raise RuntimeError("Source archive contains too many files")
        if sum(entry.file_size for entry in entries) > MAX_EXTRACTED_BYTES:
            raise RuntimeError("Source archive expands beyond the configured limit")
        for entry in entries:
            target = (destination / entry.filename).resolve()
            if target != root and root not in target.parents:
                raise RuntimeError(f"Unsafe archive path: {entry.filename}")
        bundle.extractall(destination)


def _install_tatoeba(work: Path) -> None:
    target = SOURCES / "tatoeba-vie-eng"
    required = target / "vie.txt"
    if required.is_file():
        print(f"Tatoeba source already present: {required.relative_to(ROOT)}")
        return
    if target.exists():
        raise RuntimeError(f"Incomplete source directory exists: {target}")
    archive = work / "vie-eng.zip"
    staged = work / "tatoeba-vie-eng"
    _download(TATOEBA_URL, archive, TATOEBA_SHA256)
    _safe_extract(archive, staged)
    if not (staged / "vie.txt").is_file() or not (staged / "_about.txt").is_file():
        raise RuntimeError("Tatoeba archive is missing its expected files")
    staged.replace(target)
    print(f"Installed Tatoeba source: {target.relative_to(ROOT)}")


def _install_thichhoc(work: Path) -> None:
    target = SOURCES / "thichhoc-dict"
    required = target / "dict-en-vi" / "data" / "entries"
    if required.is_dir() and any(required.glob("*.jsonl")):
        print(f"thichhoc-dict source already present: {target.relative_to(ROOT)}")
        return
    if target.exists():
        raise RuntimeError(f"Incomplete source directory exists: {target}")
    archive = work / "thichhoc-dict.zip"
    extracted = work / "thichhoc-extracted"
    _download(THICHHOC_URL, archive)
    _safe_extract(archive, extracted)
    roots = [path for path in extracted.iterdir() if path.is_dir()]
    if len(roots) != 1:
        raise RuntimeError("thichhoc-dict archive has an unexpected layout")
    staged = roots[0]
    if not (staged / "dict-en-vi" / "data" / "entries").is_dir():
        raise RuntimeError("thichhoc-dict archive is missing dictionary entries")
    shutil.move(str(staged), str(target))
    print(f"Installed thichhoc-dict commit {THICHHOC_COMMIT}")


def main() -> int:
    SOURCES.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="englow_sources_") as temporary:
        work = Path(temporary)
        _install_tatoeba(work)
        _install_thichhoc(work)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
