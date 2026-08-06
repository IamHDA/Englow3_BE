"""Ghi batch có lưới chắn — dùng chung cho mọi generator.

Vì sao tồn tại: đợt dữ liệu 2026-08-06 hỏng vì generator ghi thẳng ra đĩa rồi
mới kiểm sau. Kiểm sau thì đã muộn — file đã nằm đó, đã vào staging, đã được báo
cáo là "26 300 row, 0 lỗi".

Lưới chắn chỉ có tác dụng khi nằm TRƯỚC lệnh ghi. Hàm này là chỗ duy nhất
generator được phép ghi batch ra đĩa.

    from guarded_write import guarded_write_batch
    guarded_write_batch(batch, out_path)      # raise nếu không đạt
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from validators.diversity import DIVERSITY_THRESHOLD, check_skeleton_diversity  # noqa: E402

__all__ = ["guarded_write_batch", "DiversityRejected"]

ROOT = Path(__file__).resolve().parent.parent

# Trường nào của loại batch nào cần kiểm, và token nào phải che trước khi đếm.
# Che token là mấu chốt: thay một từ vào khuôn cố định vẫn tạo ra chuỗi "duy
# nhất", nên đếm chuỗi thô cho 83.9% trên chính dữ liệu hỏng.
CHECKS = {
    "flashcards": {
        "vars": lambda r: (r.get("lemma"), r.get("pos"), r.get("sense_label_en")),
        "fields": {
            "definition.en": lambda r: (r.get("definition") or {}).get("en"),
            "definition.vi": lambda r: (r.get("definition") or {}).get("vi"),
            "examples[0].en": lambda r: (r.get("examples") or [{}])[0].get("sentence"),
            "examples[0].vi": lambda r: (r.get("examples") or [{}])[0].get("translation"),
        },
    },
    "grammar_points": {
        "vars": lambda r: (r.get("title_en"), r.get("title_vi")),
        "fields": {
            "theory_vi": lambda r: r.get("theory_vi"),
            "common_mistakes[0]": lambda r: (r.get("common_mistakes") or [{}])[0].get("wrong"),
        },
    },
    "groups": {
        "vars": lambda r: (),
        "fields": {
            "question_text": lambda r: ((r.get("questions") or [{}])[0]).get("question_text"),
        },
    },
}


class DiversityRejected(RuntimeError):
    """Batch không đạt ngưỡng đa dạng — KHÔNG được ghi ra đĩa."""


def guarded_write_batch(batch, out_path: Path, threshold: float = DIVERSITY_THRESHOLD,
                        quiet: bool = False) -> None:
    """Kiểm đa dạng rồi mới ghi. Không đạt thì raise, file không được tạo."""
    payload = batch.model_dump(mode="json") if hasattr(batch, "model_dump") else batch

    failures: list[str] = []
    for key, spec in CHECKS.items():
        records = payload.get(key)
        if not records:
            continue
        for name, get in spec["fields"].items():
            ok, ratio, uniq = check_skeleton_diversity(
                records, get, spec["vars"], threshold)
            if not quiet:
                mark = "OK" if ok else "✗"
                print(f"    diversity {key}.{name:22} {uniq:5d}/{len(records)} "
                      f"= {ratio:6.1%}  {mark}")
            if not ok:
                failures.append(f"{key}.{name} = {ratio:.1%} (cần ≥{threshold:.0%})")

    if failures:
        raise DiversityRejected(
            "Batch bị lưới chắn từ chối, KHÔNG ghi file:\n  - " + "\n  - ".join(failures)
            + "\n\nĐây gần như luôn là dấu hiệu nội dung được điền theo khuôn."
              " Sửa prompt rồi sinh lại, đừng hạ ngưỡng."
        )

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not quiet:
        try:
            shown = out_path.relative_to(ROOT)
        except ValueError:
            shown = out_path
        print(f"    ghi {shown}  ({out_path.stat().st_size // 1024} KB)")
