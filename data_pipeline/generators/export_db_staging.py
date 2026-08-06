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

# Thứ tự nạp — khoá ngoại chỉ trỏ về đợt trước. docs/storage-layout.md giải thích.
LOAD_ORDER = [
    # đợt 1 — không phụ thuộc gì
    "concepts",
    "rubrics",
    # đợt 2
    "concept_prerequisites",
    "rubric_dimensions",
    "flashcards",
    "grammar_points",
    "exam_groups",
    "exam_sets",
    # đợt 3
    "passages",
    "audio_assets",
    "exam_items",
    "speaking_tasks",
    "writing_tasks",
    # đợt 4 — bảng nối và bảng con
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
    """UTF-8, ensure_ascii=false theo §0.6. Ghi đè, không nối thêm."""
    if not dry:
        OUT.mkdir(parents=True, exist_ok=True)
        path = OUT / f"{name}.jsonl"
        with path.open("w", encoding="utf-8") as fh:
            for r in rows:
                fh.write(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n")
    return len(rows)


def export_concepts() -> tuple[list[dict], list[dict]]:
    """taxonomy/concepts.yaml → concepts + concept_prerequisites.

    bkt_priors bị làm phẳng thành 4 cột: một bảng quan hệ không nên chứa
    object lồng, và BKT đọc từng giá trị riêng lẻ chứ không đọc cả cụm.
    """
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
            "cefr_bands": c["cefr_band"],      # giữ nguyên mảng cho truy vấn phủ
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


def export_module(module_dir: Path) -> list[dict]:
    """Đọc mọi batch JSON trong một thư mục module. Rỗng cho tới Phase 5+."""
    if not module_dir.exists():
        return []
    return [json.loads(p.read_text(encoding="utf-8"))
            for p in sorted(module_dir.glob("*.json")) if not p.name.startswith(".")]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="chỉ đếm, không ghi file")
    args = ap.parse_args()

    counts: dict[str, int] = {name: 0 for name in LOAD_ORDER}

    concepts, prereqs = export_concepts()
    counts["concepts"] = write_jsonl("concepts", concepts, args.check)
    counts["concept_prerequisites"] = write_jsonl(
        "concept_prerequisites", prereqs, args.check)

    # Các module còn lại chưa có dữ liệu (Phase 5–10 mới sinh). Vẫn ghi file rỗng
    # để loader Phase 11 không phải phân biệt "chưa có" với "thiếu".
    pending = {
        "flashcards": "output/flashcards",
        "grammar_points": "output/grammar",
        "exam_groups": "output/exams/bank",
        "exam_sets": "output/exams/sets",
        "speaking_tasks": "output/speaking_writing",
        "writing_tasks": "output/speaking_writing",
    }
    for name in LOAD_ORDER:
        if name in ("concepts", "concept_prerequisites"):
            continue
        counts[name] = write_jsonl(name, [], args.check)

    manifest = {
        "generated_at": dt.datetime.now(dt.UTC).isoformat(),
        "generated_by": "generators/export_db_staging.py",
        "note": "Tầng dẫn xuất. Xoá và sinh lại bằng `make export-db`. Không sửa tay.",
        "load_order": LOAD_ORDER,
        "row_counts": counts,
        "empty_until": {k: v for k, v in pending.items() if counts[k] == 0},
    }
    if not args.check:
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / "_manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    filled = {k: v for k, v in counts.items() if v}
    print(f"Ghi {len(LOAD_ORDER)} bảng vào output/_db/  ({'DRY RUN' if args.check else 'đã ghi'})\n")
    print(f"{'Bảng':28}{'Rows':>8}")
    for name in LOAD_ORDER:
        mark = "" if counts[name] else "   (chờ Phase 5–10)"
        print(f"{name:28}{counts[name]:>8}{mark}")
    print(f"\n{sum(counts.values())} row, {len(filled)}/{len(LOAD_ORDER)} bảng có dữ liệu")

    # Bất biến: mọi prerequisite phải trỏ tới concept có thật
    ids = {c["concept_id"] for c in concepts}
    orphans = sorted({p["prerequisite_id"] for p in prereqs} - ids)
    if orphans:
        print(f"\nFAIL — {len(orphans)} prerequisite trỏ tới concept không tồn tại: {orphans[:5]}")
        return 1
    print(f"Kiểm tra FK: {len(prereqs)} cạnh prerequisite đều trỏ tới concept có thật")
    return 0


if __name__ == "__main__":
    sys.exit(main())
