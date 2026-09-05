#!/usr/bin/env python3
"""Pydantic → JSON Schema (draft 2020-12). §0.6, §2.8.

Sinh ra, không viết tay: hai bản viết tay sẽ lệch nhau ngay lần sửa đầu tiên.

    python schemas/export_json_schema.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import BATCH_MODELS  # noqa: E402
from schemas.assessment import AssessmentResult  # noqa: E402
from schemas.exam import ExamGroup, ExamItem, ExamSet  # noqa: E402
from schemas.flashcard import Flashcard  # noqa: E402
from schemas.grammar import GrammarPoint  # noqa: E402
from schemas.speaking_writing import Rubric, SpeakingTask, WritingTask  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "schemas" / "json"

# Ngoài 8 batch, xuất thêm schema của từng record để validator dùng lẻ được
EXTRA = {
    "flashcard": Flashcard,
    "exam_item": ExamItem,
    "exam_group": ExamGroup,
    "exam_set": ExamSet,
    "grammar_point": GrammarPoint,
    "speaking_task": SpeakingTask,
    "writing_task": WritingTask,
    "rubric": Rubric,
    "assessment_result": AssessmentResult,
}


def dump(name: str, model: type, written: list[tuple[str, int]]) -> None:
    path = OUT / f"{name}.schema.json"
    if path.exists():
        # Tên trùng nghĩa là file trước bị đè im lặng — batch "flashcard" và
        # record "flashcard" từng va nhau đúng kiểu này.
        raise SystemExit(f"FAIL — tên schema trùng: {path.name}")
    schema = model.model_json_schema(mode="validation")
    schema["$schema"] = "https://json-schema.org/draft/2020-12/schema"
    schema["$id"] = f"https://englow3.local/schemas/{name}.schema.json"
    text = json.dumps(schema, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    path.write_text(text, encoding="utf-8")
    written.append((path.name, len(text.splitlines())))


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("*.schema.json"):
        stale.unlink()

    written: list[tuple[str, int]] = []
    # Hậu tố _batch để không đụng tên với schema record cùng tên
    for module_type, model in BATCH_MODELS.items():
        dump(f"{module_type.value.lower()}_batch", model, written)
    for name, model in EXTRA.items():
        dump(name, model, written)

    print(f"Sinh {len(written)} JSON Schema vào schemas/json/\n")
    print(f"{'File':40}{'Dòng':>7}")
    for name, lines in sorted(written):
        print(f"{name:40}{lines:>7}")

    n_batch = len(BATCH_MODELS)
    print(f"\n{n_batch}/8 module_type có schema, cộng {len(EXTRA)} schema record lẻ")
    if n_batch != 8:
        print(f"FAIL — thiếu {8 - n_batch} module_type")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
