#!/usr/bin/env python3
"""Sửa metadata audio cho khớp file MP3 thật.

Đợt dữ liệu 2026-08-06 khai `duration_ms: 8000` cho MỌI asset trong khi thời
lượng thật là 13–19 giây, và khai `alignment_status: "aligned"` trong khi 0/160
evidence_span có mốc thời gian — forced alignment chưa hề chạy.

File MP3 là thật. Chỉ có metadata mô tả chúng là bịa. Script này đo lại từ file.

    python generators/fix_audio_metadata.py            # sửa
    python generators/fix_audio_metadata.py --dry-run
"""

from __future__ import annotations

import argparse
import collections
import json
import struct
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import ExamBatch  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
EXAMS = ROOT / "output" / "exams"
AUDIO = ROOT / "output" / "audio"


def mp3_duration_ms(path: Path) -> int | None:
    """Thời lượng thật. Ưu tiên afinfo (macOS), lùi về ước lượng từ bitrate."""
    try:
        out = subprocess.run(["afinfo", str(path)], capture_output=True,
                             text=True, timeout=20).stdout
        for line in out.splitlines():
            if "estimated duration" in line.lower():
                return int(float(line.split(":")[1].strip().split()[0]) * 1000)
    except Exception:
        pass
    # Lùi: đọc bitrate từ frame header đầu tiên rồi suy ra từ kích thước file.
    try:
        data = path.read_bytes()
        i = data.find(b"\xff")
        while i >= 0 and i + 4 < len(data):
            h = struct.unpack(">I", data[i:i + 4])[0]
            if (h & 0xFFE00000) == 0xFFE00000:
                bitrate_idx = (h >> 12) & 0xF
                rates = [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160]
                if 0 < bitrate_idx < len(rates):
                    kbps = rates[bitrate_idx]
                    return int(len(data) * 8 / (kbps * 1000) * 1000)
            i = data.find(b"\xff", i + 1)
    except Exception:
        pass
    return None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    stats = collections.Counter()
    diffs: list[tuple[str, int, int]] = []

    for p in sorted(EXAMS.rglob("*.json")):
        if p.name.startswith("."):
            continue
        try:
            batch = ExamBatch.model_validate(json.loads(p.read_text(encoding="utf-8")))
        except Exception:
            continue
        payload = batch.model_dump(mode="json")
        changed = False

        for g in payload.get("groups", []):
            a = g.get("audio")
            if not a:
                continue
            stats["asset audio"] += 1

            url = a.get("audio_url") or ""
            f = AUDIO / url.rsplit("/", 1)[-1] if url else None

            if f and f.exists():
                real = mp3_duration_ms(f)
                if real and a.get("duration_ms") != real:
                    diffs.append((f.name, a.get("duration_ms") or 0, real))
                    a["duration_ms"] = real
                    stats["duration_ms sửa theo file thật"] += 1
                    changed = True
            else:
                # URL trỏ vào hư không → về trạng thái chưa có audio (§Phase 8)
                if a.get("audio_url") is not None:
                    a["audio_url"] = None
                    stats["audio_url trỏ vào hư không → null"] += 1
                    changed = True
                if a.get("duration_ms") is not None:
                    a["duration_ms"] = None
                    changed = True

            # forced alignment chưa chạy → không được khai aligned
            has_ts = any(
                (q.get("evidence_span") or {}).get("audio_start_ms") is not None
                for q in g.get("questions", []))
            if a.get("alignment_status") == "aligned" and not has_ts:
                a["alignment_status"] = "pending"
                stats["alignment aligned → pending (không có mốc thời gian)"] += 1
                changed = True

        if changed and not args.dry_run:
            p.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                         encoding="utf-8")

    print("Sẽ sửa (dry-run):" if args.dry_run else "Đã sửa:")
    for k, v in stats.most_common():
        print(f"  {v:5d}  {k}")
    if not stats:
        print("  (không có asset audio nào)")
    if diffs:
        print(f"\n  Ví dụ lệch thời lượng (khai → thật):")
        for name, old, new in diffs[:5]:
            print(f"    {name:38} {old:6d} → {new:6d} ms")
    return 0


if __name__ == "__main__":
    sys.exit(main())
