#!/usr/bin/env python3
"""Build ten independent TOEIC-format practice-test manifests.

The command is deliberately fail-closed: it preserves set_001, reserves every
group used by that set, and writes nothing unless nine additional disjoint
100+100 sets can be assembled with the official per-part blueprint.
"""

from __future__ import annotations

import collections
import hashlib
import json
import sys
import zipfile
from datetime import UTC, datetime
from pathlib import Path
from urllib.parse import urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import BatchMetadata, ExamBatch, ExamSet, ModuleType, SetItemRef  # noqa: E402
from validators.exam_set_rules import check_exam_collection  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
BANK = ROOT / "output" / "exams" / "bank"
CURRENT = ROOT / "output" / "exams" / "sets" / "exam_sets_001.json"
OUT = CURRENT
OUT_DIR = ROOT / "output" / "exams" / "individual_sets"
ZIP_DIR = OUT_DIR / "zip"
MEDIA_ROOT = ROOT / "output" / "media"
QUOTAS = {1: 6, 2: 25, 3: 39, 4: 30, 5: 30, 6: 16, 7: 54}


def load_bank():
    groups = []
    for path in sorted(BANK.rglob("*.json")):
        batch = ExamBatch.model_validate_json(path.read_text(encoding="utf-8"))
        groups.extend(batch.groups)
    return groups


def capacity(groups, reserved: set[str]) -> dict[int, int]:
    return collections.Counter(
        group.part_number
        for group in groups if group.group_id not in reserved
        for _ in group.questions
    )


def take_whole_groups(pool, part: int, question_count: int):
    selected = []
    total = 0
    while pool and total < question_count:
        group = pool.pop(0)
        if total + len(group.questions) > question_count:
            raise RuntimeError(
                f"Part {part} group {group.group_id} would split across tests")
        selected.append(group)
        total += len(group.questions)
    if total != question_count:
        raise RuntimeError(f"Part {part} has {total}/{question_count} available questions")
    return selected


def take_part7(singles, multiples):
    if len(singles) < 10 or len(multiples) < 5:
        raise RuntimeError(
            f"Part 7 needs 10 single + 5 multiple groups; available "
            f"{len(singles)} single + {len(multiples)} multiple")
    chosen_singles = [singles.pop(0) for _ in range(10)]
    chosen_multiples = [multiples.pop(0) for _ in range(5)]
    if any(len(group.questions) < 2 for group in chosen_singles):
        raise RuntimeError("Every selected Part 7 single text needs at least 2 questions")
    if any(len(group.questions) < 5 for group in chosen_multiples):
        raise RuntimeError("Every selected Part 7 multiple set needs at least 5 questions")

    counts = [min(3, len(group.questions)) for group in chosen_singles]
    while sum(counts) > 29:
        index = next(i for i in range(9, -1, -1) if counts[i] > 2)
        counts[index] -= 1
    while sum(counts) < 29:
        index = next(i for i, group in enumerate(chosen_singles)
                     if counts[i] < len(group.questions))
        counts[index] += 1
    return [(group, count) for group, count in zip(chosen_singles, counts)] + [
        (group, 5) for group in chosen_multiples]


def refs(groups_with_counts, start=1):
    output = []
    position = start
    for group, count in groups_with_counts:
        for question in group.questions[:count]:
            output.append(SetItemRef(
                group_id=group.group_id, item_id=question.item_id, position=position))
            position += 1
    return output


def write_json(path: Path, payload: ExamBatch) -> None:
    """Write a validated manifest without exposing a partially written file."""
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload.model_dump(mode="json"), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package_media(groups) -> list[dict]:
    """Resolve every image and audio URL used by a test to a local media file."""
    records = {}
    for group in groups:
        if group.image_url:
            url = str(group.image_url)
            relative = urlparse(url).path.removeprefix("/images/")
            local = MEDIA_ROOT / "images" / relative
            archive_path = f"media/images/{relative}"
            records[archive_path] = (url, local)
        if group.audio and group.audio.audio_url:
            url = str(group.audio.audio_url)
            filename = Path(urlparse(url).path).name
            local = MEDIA_ROOT / "audio" / "toeic" / "listening" / filename
            archive_path = f"media/audio/toeic/listening/{filename}"
            records[archive_path] = (url, local)

    missing = [str(local) for _, local in records.values()
               if not local.is_file() or local.stat().st_size == 0]
    if missing:
        raise RuntimeError("Cannot create delivery ZIP; missing media:\n  - "
                           + "\n  - ".join(missing))
    return [
        {"url": url, "local": local, "archive_path": archive_path}
        for archive_path, (url, local) in sorted(records.items())
    ]


def write_zip(path: Path, source: Path, media: list[dict], exam_set: ExamSet) -> None:
    """Create one self-contained archive with test data, images, and audio."""
    temporary = path.with_suffix(path.suffix + ".tmp")
    package_root = source.stem
    manifest_files = [{
        "archive_path": f"{package_root}/{source.name}",
        "bytes": source.stat().st_size,
        "sha256": sha256(source),
    }]
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=9) as archive:
        archive.write(source, arcname=f"{package_root}/{source.name}")
        for record in media:
            archive_path = f"{package_root}/{record['archive_path']}"
            archive.write(record["local"], arcname=archive_path)
            manifest_files.append({
                "archive_path": archive_path,
                "source_url": record["url"],
                "bytes": record["local"].stat().st_size,
                "sha256": sha256(record["local"]),
            })
        package_manifest = {
            "format": "englow3-toeic-delivery-package-v1",
            "set_id": exam_set.set_id,
            "listening_questions": len(exam_set.listening),
            "reading_questions": len(exam_set.reading),
            "total_questions": exam_set.total_questions,
            "entry_json": f"{package_root}/{source.name}",
            "media_files": len(media),
            "files": manifest_files,
        }
        archive.writestr(
            f"{package_root}/package_manifest.json",
            json.dumps(package_manifest, ensure_ascii=False, indent=2) + "\n",
        )
        archive.writestr(
            f"{package_root}/README.txt",
            "Self-contained TOEIC practice-test delivery package.\n"
            f"Set: {exam_set.set_id}\n"
            "The JSON contains all selected groups, passages, 200 questions, "
            "answers, explanations, and original media URLs.\n"
            "All referenced local media are included under media/. "
            "package_manifest.json maps each source URL to its packaged file "
            "and records SHA-256 checksums.\n",
        )
    temporary.replace(path)


def selected_groups(exam_set: ExamSet, groups_by_id: dict):
    """Copy only the groups and questions selected by one test manifest."""
    refs = list(exam_set.listening) + list(exam_set.reading)
    wanted = collections.defaultdict(set)
    group_order = []
    for ref in refs:
        if ref.group_id not in wanted:
            group_order.append(ref.group_id)
        wanted[ref.group_id].add(ref.item_id)

    output = []
    for group_id in group_order:
        source = groups_by_id.get(group_id)
        if source is None:
            raise RuntimeError(f"Missing group {group_id} while packaging {exam_set.set_id}")
        questions = [question for question in source.questions
                     if question.item_id in wanted[group_id]]
        if len(questions) != len(wanted[group_id]):
            raise RuntimeError(f"Missing selected questions in group {group_id}")
        output.append(source.model_copy(update={"questions": questions}))
    if sum(len(group.questions) for group in output) != 200:
        raise RuntimeError(f"{exam_set.set_id} package does not contain exactly 200 questions")
    return output


def write_manifests(payload: ExamBatch, bank_groups) -> tuple[list[Path], list[Path]]:
    """Write combined, standalone, and individually zipped test manifests."""
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ZIP_DIR.mkdir(parents=True, exist_ok=True)
    json_paths = [OUT]
    zip_paths = []
    groups_by_id = {group.group_id: group for group in bank_groups}
    write_json(OUT, payload)
    for index, exam_set in enumerate(payload.sets, 1):
        groups = selected_groups(exam_set, groups_by_id)
        individual = ExamBatch(
            batch_metadata=BatchMetadata(
                batch_id=f"exam_{exam_set.set_id}",
                module_type=ModuleType.EXAM,
                generated_by="codex-gpt-5",
                generated_at=payload.batch_metadata.generated_at,
                total_records=len(groups),
            ),
            groups=groups,
            sets=[exam_set],
        )
        stem = f"toeic_practice_test_{index:02d}"
        json_path = OUT_DIR / f"{stem}.json"
        zip_path = ZIP_DIR / f"{stem}.zip"
        write_json(json_path, individual)
        write_zip(zip_path, json_path, package_media(groups), exam_set)
        json_paths.append(json_path)
        zip_paths.append(zip_path)

        legacy_path = OUT_DIR / f"exam_{exam_set.set_id}.json"
        if legacy_path.is_file():
            legacy_path.unlink()
    return json_paths, zip_paths


def main() -> int:
    groups = load_bank()
    current = ExamBatch.model_validate_json(CURRENT.read_text(encoding="utf-8"))
    baseline = next((exam_set for exam_set in current.sets
                     if exam_set.set_id == "set_001"), None)
    if baseline is None:
        raise RuntimeError("The current manifest does not contain the preserved set_001")
    reserved = {ref.group_id for ref in baseline.listening + baseline.reading}

    available = capacity(groups, reserved)
    deficits = {
        part: max(0, quota * 9 - available[part])
        for part, quota in QUOTAS.items()
    }
    if any(deficits.values()):
        detail = ", ".join(
            f"Part {part}: thiếu {count}" for part, count in deficits.items() if count)
        raise RuntimeError(
            "Không ghi manifest: ngân hàng chưa đủ cho 9 đề độc lập. " + detail)

    pools = {
        part: [group for group in groups
               if group.part_number == part and group.group_id not in reserved]
        for part in range(1, 8)
    }
    p7_singles = [group for group in pools[7] if len(group.passages) == 1]
    p7_multiples = [group for group in pools[7] if len(group.passages) >= 2]
    exam_sets = [baseline]
    for set_number in range(2, 11):
        listening_groups = []
        for part in (1, 2, 3, 4):
            listening_groups.extend((group, len(group.questions)) for group in
                                    take_whole_groups(pools[part], part, QUOTAS[part]))
        reading_groups = []
        for part in (5, 6):
            reading_groups.extend((group, len(group.questions)) for group in
                                  take_whole_groups(pools[part], part, QUOTAS[part]))
        reading_groups.extend(take_part7(p7_singles, p7_multiples))
        listening_refs = refs(listening_groups)
        reading_refs = refs(reading_groups)
        exam_sets.append(ExamSet(
            set_id=f"set_{set_number:03d}",
            title=f"Đề luyện theo định dạng TOEIC số {set_number}",
            listening=listening_refs,
            reading=reading_refs,
            total_questions=200,
        ))

    errors = check_exam_collection(exam_sets, groups)
    if errors:
        raise RuntimeError("Không ghi manifest:\n  - " + "\n  - ".join(errors[:50]))
    payload = ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_sets_001_010", module_type=ModuleType.EXAM,
            generated_by="codex-gpt-5", generated_at=datetime.now(UTC),
            total_records=0),
        groups=[], sets=exam_sets,
    )
    json_paths, zip_paths = write_manifests(payload, groups)
    print(f"Wrote {len(exam_sets)} disjoint full tests to {OUT.relative_to(ROOT)}")
    print(f"Wrote {len(json_paths) - 1} individual manifests to {OUT_DIR.relative_to(ROOT)}")
    print(f"Wrote {len(zip_paths)} individual ZIP files to {ZIP_DIR.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
