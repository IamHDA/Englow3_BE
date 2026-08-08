#!/usr/bin/env python3
"""Sinh tầng staging output/_db/ — mỗi file JSONL là một bảng tương lai.

Đọc dữ liệu tầng 1 (taxonomy + output/<module>/) và làm phẳng thành row.
Xem docs/storage-layout.md để biết bảng nào phụ thuộc bảng nào.

Tầng này là DẪN XUẤT: xoá được, sinh lại được. Không sửa tay.

Chạy:
    python generators/export_db_staging.py
    python generators/export_db_staging.py --check   # chỉ kiểm tra, không ghi
"""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
TAXONOMY = ROOT / "taxonomy" / "concepts.yaml"
OUT = ROOT / "output" / "_db"

LOAD_ORDER = [
    "concepts",
    "rubrics",
    "concept_prerequisites",
    "rubric_dimensions",
    "flashcards",
    "grammar_points",
    "exam_groups",
    "exam_sets",
    "passages",
    "audio_assets",
    "exam_items",
    "speaking_tasks",
    "writing_tasks",
    "options",
    "flashcard_concepts",
    "flashcard_examples",
    "flashcard_collocations",
    "exam_item_concepts",
    "grammar_point_concepts",
    "task_concepts",
    "exam_set_items",
]


def write_jsonl(name: str, rows: list[dict], dry: bool) -> int:
    if not dry:
        OUT.mkdir(parents=True, exist_ok=True)
        path = OUT / f"{name}.jsonl"
        with path.open("w", encoding="utf-8") as fh:
            for r in rows:
                fh.write(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n")
    return len(rows)


def export_concepts() -> tuple[list[dict], list[dict]]:
    if not TAXONOMY.exists():
        sys.exit(f"Thiếu {TAXONOMY}")
    concepts = yaml.safe_load(TAXONOMY.read_text(encoding="utf-8"))

    rows, prereqs = [], []
    for c in concepts:
        p = c["bkt_priors"]
        rows.append({
            "concept_id": c["concept_id"],
            "name_en": c["name_en"],
            "name_vi": c["name_vi"],
            "domain": c["domain"],
            "cefr_band_min": c["cefr_band"][0],
            "cefr_band_max": c["cefr_band"][-1],
            "cefr_bands": c["cefr_band"],
            "parent_id": c["parent_id"],
            "p_init": p["p_init"],
            "p_learn": p["p_learn"],
            "p_slip": p["p_slip"],
            "p_guess": p["p_guess"],
            "description_vi": c["description_vi"],
        })
        for pid in c.get("prerequisites") or []:
            prereqs.append({"concept_id": c["concept_id"], "prerequisite_id": pid})
    return rows, prereqs


def export_flashcards() -> tuple[list[dict], list[dict], list[dict], list[dict]]:
    fc_dir = ROOT / "output" / "flashcards"
    if not fc_dir.exists():
        return [], [], [], []

    cards, fc_concepts, fc_examples, fc_collocations = [], [], [], []

    for p in sorted(fc_dir.glob("*.json")):
        if p.name.startswith("."):
            continue
        data = json.loads(p.read_text(encoding="utf-8"))
        for fc in data.get("flashcards", []):
            fc_id = fc["id"]
            cards.append({
                "id": fc_id,
                "lemma": fc["lemma"],
                "pos": fc["pos"],
                "sense_index": fc["sense_index"],
                "sense_label_en": fc["sense_label_en"],
                "ipa_us": fc["ipa_us"],
                "ipa_uk": fc.get("ipa_uk"),
                "ipa_verified": fc.get("ipa_verified", False),
                "audio_url_us": fc.get("audio_url_us"),
                "audio_url_uk": fc.get("audio_url_uk"),
                "definition_en": fc["definition"]["en"],
                "definition_vi": fc["definition"]["vi"],
                "mnemonic_tip_vi": fc.get("mnemonic_tip_vi"),
                "cefr_level": fc["cefr_level"],
                "cefr_source": fc["cefr_source"],
                "frequency_rank": fc.get("frequency_rank"),
                "difficulty_prior": fc["difficulty_prior"],
                "embedding_text": fc.get("embedding_text", ""),
                "review_status": fc.get("review_status", "draft"),
            })
            for cid in fc.get("concept_ids", []):
                fc_concepts.append({"flashcard_id": fc_id, "concept_id": cid})
            for idx, ex in enumerate(fc.get("examples", []), start=1):
                fc_examples.append({
                    "flashcard_id": fc_id,
                    "idx": idx,
                    "sentence": ex["sentence"],
                    "translation": ex["translation"],
                    "source": ex.get("source", "generated"),
                })
            for idx, col in enumerate(fc.get("collocations", []), start=1):
                fc_collocations.append({
                    "flashcard_id": fc_id,
                    "idx": idx,
                    "pattern": col["pattern"],
                    "text": col["text"],
                    "cefr": col["cefr"],
                })

    return cards, fc_concepts, fc_examples, fc_collocations


def export_grammar() -> tuple[list[dict], list[dict], list[dict], list[dict], list[dict]]:
    g_dir = ROOT / "output" / "grammar"
    if not g_dir.exists():
        return [], [], [], [], []

    gps, gp_concepts, items, options, item_concepts = [], [], [], [], []

    for p in sorted(g_dir.glob("*.json")):
        if p.name.startswith("."):
            continue
        data = json.loads(p.read_text(encoding="utf-8"))
        for gp in data.get("grammar_points", []):
            gp_id = gp["id"]
            gps.append({
                "id": gp_id,
                "title_en": gp["title_en"],
                "title_vi": gp["title_vi"],
                "cefr_level": gp["cefr_level"],
                "theory_vi": gp["theory_vi"],
                "theory_en_summary": gp["theory_en_summary"],
                "form_patterns": gp.get("form_patterns", []),
                "common_mistakes": gp.get("common_mistakes", []),
                "embedding_text": gp.get("embedding_text", ""),
                "review_status": gp.get("review_status", "draft"),
            })
            for cid in gp.get("concept_ids", []):
                gp_concepts.append({"grammar_point_id": gp_id, "concept_id": cid})

            for q in gp.get("quick_exercises", []):
                iid = q["item_id"]
                items.append({
                    "item_id": iid,
                    "group_id": None,
                    "part_number": q["part_number"],
                    "question_text": q.get("question_text"),
                    "question_type": q["question_type"],
                    "difficulty_prior": q["difficulty_prior"],
                    "explanation_en": q["explanation"]["en"],
                    "explanation_vi": q["explanation"]["vi"],
                    "embedding_text": q.get("embedding_text", ""),
                    "review_status": q.get("review_status", "draft"),
                })
                for cid in q.get("concept_ids", []):
                    item_concepts.append({"item_id": iid, "concept_id": cid})
                for opt in q.get("options", []):
                    options.append({
                        "item_id": iid,
                        "label": opt["label"],
                        "text": opt["text"],
                        "is_correct": opt["is_correct"],
                        "rationale_vi": opt["rationale_vi"],
                    })

    return gps, gp_concepts, items, options, item_concepts


def export_speaking_writing() -> tuple[list[dict], list[dict], list[dict],
                                       list[dict], list[dict]]:
    """Rubric, speaking task, writing task và bảng nối concept.

    Rubric nằm TRONG cả hai batch nên phải khử trùng theo rubric_id — nạp hai
    lần vào Postgres là vi phạm khoá chính ngay lệnh COPY thứ hai.
    """
    d = ROOT / "output" / "speaking_writing"
    rubrics, dims, sp_tasks, wr_tasks, task_concepts = [], [], [], [], []
    seen_rubrics: set[str] = set()

    if not d.exists():
        return rubrics, dims, sp_tasks, wr_tasks, task_concepts

    for p in sorted(d.glob("*.json")):
        if p.name.startswith("."):
            continue
        data = json.loads(p.read_text(encoding="utf-8"))

        for r in data.get("rubrics", []):
            rid = r["rubric_id"]
            if rid in seen_rubrics:
                continue
            seen_rubrics.add(rid)
            rubrics.append({"rubric_id": rid, "name": r["name"],
                            "version": r["version"]})
            for dim in r["dimensions"]:
                dims.append({
                    "rubric_id": rid,
                    "name": dim["name"],
                    "weight": dim["weight"],
                    "concept_id": dim["concept_id"],
                    "band_descriptors": dim["band_descriptors"],
                })

        kind = "speaking" if "part_number" in (data.get("tasks") or [{}])[0] else "writing"
        for t in data.get("tasks", []):
            base = {
                "task_id": t["task_id"], "prompt": t["prompt"],
                "sample_answer_c1": t["sample_answer_c1"],
                "rubric_ref": t["rubric_ref"],
                "difficulty_prior": t["difficulty_prior"],
                "review_status": t["review_status"],
            }
            if kind == "speaking":
                sp_tasks.append({**base, "part_number": t["part_number"],
                                 "prep_time_sec": t["prep_time_sec"],
                                 "response_time_sec": t["response_time_sec"]})
            else:
                wr_tasks.append({**base, "task_type": t["task_type"],
                                 "min_words": t.get("min_words"),
                                 "max_words": t.get("max_words"),
                                 "high_scoring_vocab": t.get("high_scoring_vocab", [])})
            for cid in t["concept_ids"]:
                task_concepts.append({"task_id": t["task_id"],
                                      "task_kind": kind, "concept_id": cid})

    return rubrics, dims, sp_tasks, wr_tasks, task_concepts


def export_exams() -> tuple[list[dict], list[dict], list[dict], list[dict], list[dict], list[dict], list[dict], list[dict]]:
    bank_dir = ROOT / "output" / "exams" / "bank"
    sets_dir = ROOT / "output" / "exams" / "sets"

    groups, passages, audio_assets, items, options, item_concepts = [], [], [], [], [], []
    sets, set_items = [], []
    seen_groups, seen_items = set(), set()

    if bank_dir.exists():
        for p in sorted(bank_dir.glob("**/*.json")):
            if p.name.startswith("."):
                continue
            data = json.loads(p.read_text(encoding="utf-8"))
            for g in data.get("groups", []):
                gid = g["group_id"]
                if gid not in seen_groups:
                    seen_groups.add(gid)
                    groups.append({
                        "group_id": gid,
                        "part_number": g["part_number"],
                        "image_url": g.get("image_url"),
                    })
                    for pas in g.get("passages", []):
                        passages.append({
                            "group_id": gid,
                            "order": pas["order"],
                            "passage_type": pas["passage_type"],
                            "text": pas["text"],
                            "graphic_url": pas.get("graphic_url"),
                            "speaker": pas.get("speaker"),
                            "timestamp": pas.get("timestamp"),
                        })
                    if g.get("audio"):
                        aud = g["audio"]
                        audio_assets.append({
                            "group_id": gid,
                            "audio_url": aud.get("audio_url"),
                            "script": aud["script"],
                            "accent": aud["accent"],
                            "speaker_count": aud.get("speaker_count", 1),
                            "duration_ms": aud.get("duration_ms"),
                            "alignment_status": aud.get("alignment_status", "pending"),
                        })

                for q in g.get("questions", []):
                    iid = q["item_id"]
                    if iid not in seen_items:
                        seen_items.add(iid)
                        items.append({
                            "item_id": iid,
                            "group_id": gid,
                            "part_number": q["part_number"],
                            "question_text": q.get("question_text"),
                            "question_type": q["question_type"],
                            "difficulty_prior": q["difficulty_prior"],
                            "explanation_en": q["explanation"]["en"],
                            "explanation_vi": q["explanation"]["vi"],
                            "embedding_text": q.get("embedding_text", ""),
                            "review_status": q.get("review_status", "draft"),
                        })
                        for cid in q.get("concept_ids", []):
                            item_concepts.append({"item_id": iid, "concept_id": cid})
                        for opt in q.get("options", []):
                            options.append({
                                "item_id": iid,
                                "label": opt["label"],
                                "text": opt["text"],
                                "is_correct": opt["is_correct"],
                                "rationale_vi": opt["rationale_vi"],
                            })

    if sets_dir.exists():
        for p in sorted(sets_dir.glob("*.json")):
            if p.name.startswith("."):
                continue
            data = json.loads(p.read_text(encoding="utf-8"))
            for s in data.get("sets", []):
                sid = s["set_id"]
                sets.append({
                    "set_id": sid,
                    "title": s["title"],
                    "total_questions": s["total_questions"],
                })
                for ref in s.get("listening", []):
                    set_items.append({
                        "set_id": sid,
                        "section": "listening",
                        "position": ref["position"],
                        "group_id": ref["group_id"],
                        "item_id": ref["item_id"],
                    })
                for ref in s.get("reading", []):
                    set_items.append({
                        "set_id": sid,
                        "section": "reading",
                        "position": ref["position"],
                        "group_id": ref["group_id"],
                        "item_id": ref["item_id"],
                    })

    return groups, passages, audio_assets, items, options, item_concepts, sets, set_items


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="chỉ đếm, không ghi file")
    args = ap.parse_args()

    tables: dict[str, list[dict]] = {name: [] for name in LOAD_ORDER}

    concepts, prereqs = export_concepts()
    tables["concepts"] = concepts
    tables["concept_prerequisites"] = prereqs

    cards, fc_concepts, fc_examples, fc_collocations = export_flashcards()
    tables["flashcards"] = cards
    tables["flashcard_concepts"] = fc_concepts
    tables["flashcard_examples"] = fc_examples
    tables["flashcard_collocations"] = fc_collocations

    gps, gp_concepts, g_items, g_options, g_item_concepts = export_grammar()
    tables["grammar_points"] = gps
    tables["grammar_point_concepts"] = gp_concepts

    groups, passages, audio_assets, e_items, e_options, e_item_concepts, sets, set_items = export_exams()
    tables["exam_groups"] = groups
    tables["passages"] = passages
    tables["audio_assets"] = audio_assets
    tables["exam_sets"] = sets
    tables["exam_set_items"] = set_items

    rubrics, rdims, sp_tasks, wr_tasks, task_concepts = export_speaking_writing()
    tables["rubrics"] = rubrics
    tables["rubric_dimensions"] = rdims
    tables["speaking_tasks"] = sp_tasks
    tables["writing_tasks"] = wr_tasks
    tables["task_concepts"] = task_concepts

    tables["exam_items"] = g_items + e_items
    tables["options"] = g_options + e_options
    tables["exam_item_concepts"] = g_item_concepts + e_item_concepts

    counts: dict[str, int] = {}
    for name in LOAD_ORDER:
        counts[name] = write_jsonl(name, tables[name], args.check)

    manifest = {
        "generated_at": dt.datetime.now(dt.UTC).isoformat(),
        "generated_by": "generators/export_db_staging.py",
        "note": "Tầng dẫn xuất. Xoá và sinh lại bằng `make export-db`. Không sửa tay.",
        "load_order": LOAD_ORDER,
        "row_counts": counts,
    }
    if not args.check:
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / "_manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    filled = {k: v for k, v in counts.items() if v}
    print(f"Ghi {len(LOAD_ORDER)} bảng vào output/_db/  ({'DRY RUN' if args.check else 'đã ghi'})\n")
    print(f"{'Bảng':28}{'Rows':>8}")
    for name in LOAD_ORDER:
        mark = "" if counts[name] else "   (chờ Phase 8–10)"
        print(f"{name:28}{counts[name]:>8}{mark}")
    print(f"\n{sum(counts.values())} row, {len(filled)}/{len(LOAD_ORDER)} bảng có dữ liệu")

    ids = {c["concept_id"] for c in concepts}
    orphans = sorted({p["prerequisite_id"] for p in prereqs} - ids)
    if orphans:
        print(f"\nFAIL — {len(orphans)} prerequisite trỏ tới concept không tồn tại: {orphans[:5]}")
        return 1
    print(f"Kiểm tra FK: {len(prereqs)} cạnh prerequisite đều trỏ tới concept có thật")
    return 0


if __name__ == "__main__":
    sys.exit(main())
