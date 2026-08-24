#!/usr/bin/env python3
"""Build deterministic final QA, human-review packet, and media manifest."""

from __future__ import annotations

import collections
import hashlib
import json
import random
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "output"
REPORTS = ROOT / "reports"


def load_json_files(path: Path) -> list[dict]:
    return [json.loads(file.read_text(encoding="utf-8")) for file in sorted(path.rglob("*.json"))]


def build_media_manifest() -> dict:
    media_root = OUTPUT / "media"
    files = []
    for path in sorted(file for file in media_root.rglob("*")
                       if file.is_file() and file.name != "_manifest.json"):
        relative = path.relative_to(media_root).as_posix()
        files.append({
            "path": relative,
            "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        })
    manifest = {
        "generated_by": "generators/build_qa_reports.py",
        "base_url": "http://localhost:9000/images/toeic/",
        "file_count": len(files),
        "total_bytes": sum(file["bytes"] for file in files),
        "files": files,
    }
    (media_root / "_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def main() -> int:
    exam_docs = load_json_files(OUTPUT / "exams" / "bank")
    groups = [group for doc in exam_docs for group in doc.get("groups", [])]
    items = [question for group in groups for question in group["questions"]]
    listening = [question for group in groups if group["part_number"] <= 4 for question in group["questions"]]
    reading = [question for group in groups if group["part_number"] >= 5 for question in group["questions"]]
    sets = [exam_set for doc in load_json_files(OUTPUT / "exams" / "sets")
            for exam_set in doc.get("sets", [])]
    flashcards = [card for doc in load_json_files(OUTPUT / "flashcards")
                  for card in doc.get("flashcards", [])]
    grammar = [point for doc in load_json_files(OUTPUT / "grammar")
               for point in doc.get("grammar_points", [])]
    speaking_docs = load_json_files(OUTPUT / "speaking_writing")
    speaking = [task for doc in speaking_docs for task in doc.get("tasks", []) if "part_number" in task]
    writing = [task for doc in speaking_docs for task in doc.get("tasks", []) if "task_type" in task]
    shadowing = [clip for doc in load_json_files(OUTPUT / "shadowing")
                 for clip in doc.get("clips", [])]
    prompt_docs = load_json_files(OUTPUT / "prompts")
    prompts = [prompt for doc in prompt_docs for prompt in doc.get("prompts", [])]
    calibration_cases = [case for doc in prompt_docs for case in doc.get("cases", [])]
    taxonomy = yaml.safe_load((ROOT / "taxonomy" / "concepts.yaml").read_text(encoding="utf-8"))

    concept_counts = collections.Counter()
    for record in items + flashcards + speaking + writing + shadowing:
        concept_counts.update(record.get("concept_ids", []))
    for point in grammar:
        concept_counts.update(point.get("concept_ids", []))
        for exercise in point.get("quick_exercises", []):
            concept_counts.update(exercise.get("concept_ids", []))
    children = collections.Counter(c.get("parent_id") for c in taxonomy if c.get("parent_id"))
    leaves = [c["concept_id"] for c in taxonomy if not children[c["concept_id"]]]
    under_five = sorted((concept, concept_counts[concept]) for concept in leaves
                        if concept_counts[concept] < 5)

    media = build_media_manifest()
    audio_assets = [group["audio"] for group in groups if group.get("audio")]
    audio_complete = sum(
        1 for audio in audio_assets
        if audio.get("audio_url")
        and audio.get("duration_ms")
        and audio.get("cues")
        and audio.get("alignment_status") == "aligned"
    )
    image_urls = [group["image_url"] for group in groups if group.get("image_url")]
    image_urls.extend(task["image_url"] for task in speaking + writing if task.get("image_url"))
    image_root = OUTPUT / "media" / "images" / "toeic"
    image_files_present = sum(
        1 for url in image_urls
        if (image_root / url.split("/images/toeic/", 1)[-1]).is_file()
    )
    review_counts = collections.Counter(record.get("review_status", "draft")
                                        for record in items + flashcards + grammar
                                        + speaking + writing + shadowing + prompts)
    calibrated = sum(1 for item in items
                     if item.get("irt_params", {}).get("calibration_status") == "calibrated")
    part_counts = collections.Counter(question["part_number"] for question in items)
    release_ready = len(sets) == 10 and all(
        len(exam_set.get("listening", [])) == 100
        and len(exam_set.get("reading", [])) == 100
        for exam_set in sets
    )
    grammar_exercises = sum(len(point.get("quick_exercises", [])) for point in grammar)
    pronunciation_root = OUTPUT / "media" / "audio" / "flashcards"
    pronunciation_complete = sum(
        1 for card in flashcards
        if card.get("audio_url_us") and card.get("audio_url_uk")
        and (pronunciation_root / card["audio_url_us"].rsplit("/", 1)[-1]).is_file()
        and (pronunciation_root / card["audio_url_uk"].rsplit("/", 1)[-1]).is_file()
    )
    shadowing_complete = sum(
        1 for clip in shadowing
        if clip.get("audio_url") and 30_000 <= (clip.get("duration_ms") or 0) <= 60_000
        and all(segment.get("start_ms") is not None and segment.get("end_ms") is not None
                for segment in clip.get("segments", []))
    )
    speaking_audio = [task["audio"] for task in speaking if task.get("audio")]
    speaking_audio_root = OUTPUT / "media" / "audio" / "toeic" / "speaking"
    speaking_audio_complete = sum(
        1 for audio in speaking_audio
        if audio.get("audio_url") and audio.get("duration_ms") and audio.get("cues")
        and audio.get("alignment_status") == "aligned"
        and (speaking_audio_root / audio["audio_url"].rsplit("/", 1)[-1]).is_file()
    )

    report = f"""# FINAL QA — English/TOEIC data

Generated from the current source-of-truth output by `generators/build_qa_reports.py`.

## Outcome

- Exam sets currently present: **{len(sets)}/10**. Structural readiness: **{'READY' if release_ready else 'NOT READY'}**.
- Bank: **{len(items)} exam questions** ({len(listening)} Listening, {len(reading)} Reading).
- Supporting content: **{len(flashcards)} flashcards**, **{len(grammar)} grammar points / {grammar_exercises} exercises**, **{len(speaking)} Speaking tasks**, **{len(writing)} Writing tasks**.
- Practice/evaluation: **{len(shadowing)} shadowing+dictation clips**, **{len(prompts)} versioned assessment prompts**, **{len(calibration_cases)} offline calibration fixtures**.
- Media: **{media['file_count']} files**, {media['total_bytes'] / 1_048_576:.1f} MiB, each recorded with SHA-256.
- Collection QA requires all 10 sets to pass blueprint and cross-set no-reuse checks.
- Exact test totals must be taken from the current CI run; this report does not hard-code them.

## Exam blueprint

| Part | Bank questions | Required in set |
|---:|---:|---:|
""" + "\n".join(
        f"| {part} | {part_counts[part]} | {target} |"
        for part, target in {1: 6, 2: 25, 3: 39, 4: 30, 5: 30, 6: 16, 7: 54}.items()
    ) + f"""

Part 7 in every set is locked to 10 single texts / 29 questions plus 5
double-or-triple sets / 25 questions. The collection includes vocabulary-in-context,
paraphrase, sentence-insertion, intent, inference, detail, main-idea,
not-true, and cross-reference items.

## Media integrity

- Listening: {audio_complete}/{len(audio_assets)} groups have real MP3 URLs,
  measured durations, and measured utterance cues.
- Exam/Speaking/Writing images: {image_files_present}/{len(image_urls)} referenced files exist locally.
- Speaking recorded prompts: {speaking_audio_complete}/{len(speaking_audio)} files exist with duration and cue data.
- Flashcard pronunciation: {pronunciation_complete}/{len(flashcards)} cards have both local US and UK MP3 files.
- Shadowing/dictation: {shadowing_complete}/{len(shadowing)} clips have local audio metadata and measured sentence timestamps in the 30–60 second target.
- Public object-store upload is reproducible with
  `powershell -File generators/upload_media_to_minio.ps1`.

## Human and empirical gates (not fabricated)

- Review status: {dict(review_counts)}. No item was marked approved by automation.
- IRT calibrated exam items: {calibrated}/{len(items)}. Calibration requires real learner responses.
- Assessment provider runs: 0. The 10 cases are schema/regression fixtures, not empirical gold labels; three-run variance still requires a configured model and human adjudication.
- Leaf concepts below five linked records: {len(under_five)}/{len(leaves)}.
  This does not block a fixed TOEIC-format test, but it is insufficient for stable
  per-concept BKT estimates on every leaf.
- This offline report does not assert MinIO upload state. Local files and upload
  automation are complete; deployment verification must check the public URLs.

## Remaining release actions

1. A human reviewer completes `reports/human_review_packet.md` and changes only
   accepted records from `draft` to `approved`.
2. Run the MinIO upload script; verify image/listening URLs under
   `http://localhost:9000/images/` and flashcard/shadowing URLs under
   `http://localhost:9000/audio/`.
3. Collect real responses before estimating IRT parameters or claiming calibrated difficulty.
"""

    rng = random.Random(20260820)
    item_by_id = {item["item_id"]: item for item in items}
    sample = []
    for exam_set in sets:
        refs = exam_set.get("listening", []) + exam_set.get("reading", [])
        sampled_refs = rng.sample(refs, min(20, len(refs)))
        sample.extend((exam_set["set_id"], item_by_id[ref["item_id"]])
                      for ref in sampled_refs)
    packet_lines = [
        f"# Human review packet — {len(sample)} deterministic exam samples",
        "",
        "Reviewer: ____________________    Date: ____________________",
        "",
        "For each item, check factual correctness, exactly one defensible answer,",
        "distractor plausibility, natural English, and Vietnamese rationale quality.",
        "Mark `APPROVE` or `REJECT` with a reason. Automation must not fill this field.",
        "",
    ]
    for index, (set_id, item) in enumerate(sample, 1):
        correct = next(option for option in item["options"] if option["is_correct"])
        packet_lines.extend([
            f"## {index}. {set_id} / Part {item['part_number']} — `{item['item_id']}`",
            "",
            f"**Question:** {item.get('question_text') or '[photograph-description item]'}",
            "",
            *[f"- {option['label']}. {option['text']}" for option in item["options"]],
            "",
            f"**Key:** {correct['label']} — {correct['text']}",
            "",
            f"**Rationale VI:** {correct['rationale_vi']}",
            "",
            "**Decision:** __________  **Reason:** ________________________________________",
            "",
        ])

    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / "FINAL_QA.md").write_text(report, encoding="utf-8")
    (REPORTS / "human_review_packet.md").write_text(
        "\n".join(packet_lines) + "\n", encoding="utf-8")

    support_lines = [
        "# Human review packet — supporting learning data", "",
        "Reviewer: ____________________    Date: ____________________", "",
        "Automation deliberately leaves every record in draft. Review meaning, naturalness,",
        "translation accuracy, CEFR fit, concept mapping, and media intelligibility.", "",
    ]
    for level in ["A1", "A2", "B1", "B2", "C1"]:
        pool = [x for x in flashcards if x["cefr_level"] == level]
        for card in rng.sample(pool, min(12, len(pool))):
            support_lines.extend([
                f"## Flashcard {level} — `{card['id']}`", "",
                f"**{card['lemma']} ({card['pos']})** — {card['definition']['en']} / {card['definition']['vi']}", "",
                f"Examples: {card['examples'][0]['sentence']} / {card['examples'][0]['translation']}", "",
                "**Decision:** __________  **Reason:** ________________________________________", "",
            ])
    for point in rng.sample(grammar, min(30, len(grammar))):
        exercise = point["quick_exercises"][0]
        support_lines.extend([
            f"## Grammar — `{point['id']}`", "",
            f"**{point['title_en']} / {point['title_vi']}**", "",
            f"Exercise: {exercise['question_text']}", "",
            "**Decision:** __________  **Reason:** ________________________________________", "",
        ])
    for clip in shadowing:
        support_lines.extend([
            f"## Shadowing/dictation — `{clip['clip_id']}`", "",
            f"{clip['cefr_level']} / {clip['accent']} / {clip.get('duration_ms')} ms", "",
            clip["script"], "",
            "**Decision:** __________  **Reason:** ________________________________________", "",
        ])
    for prompt in prompts:
        support_lines.extend([
            f"## Assessment prompt — `{prompt['prompt_id']}`", "",
            f"Target: {prompt['target']} / rubric: `{prompt['rubric_ref']}`", "",
            "Check rubric fidelity, evidence constraints, JSON-only output, and refusal to infer missing evidence.", "",
            "**Decision:** __________  **Reason:** ________________________________________", "",
        ])
    (REPORTS / "human_review_packet_supporting.md").write_text(
        "\n".join(support_lines) + "\n", encoding="utf-8")
    print(f"Wrote QA reports, two human-review packets, and {media['file_count']}-file media manifest")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
