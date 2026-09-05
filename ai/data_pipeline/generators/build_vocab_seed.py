#!/usr/bin/env python3
"""Dựng seeds/vocab_seed.csv từ wordlist gốc trong seeds/raw/.

Phase 4 của work order. LLM KHÔNG được tự chọn từ vựng — file này quyết định
trước danh sách, Phase 5 chỉ làm giàu nội dung cho đúng những từ ở đây.

Nguồn (xem seeds/raw/README-LOCAL.md để biết license):
  - cefrj-vocabulary-profile-1.5.csv         A1-B2, có sẵn (headword, pos, CEFR)
  - octanove-vocabulary-profile-c1c2-1.0.csv C1 (bỏ C2, ngoài phạm vi §3.2)
  - TSL/NGSL/BSL stats                       độ liên quan TOEIC + frequency rank

Xếp ưu tiên trong mỗi CEFR level:
  1. có trong TOEIC Service List  (sát đích nhất)
  2. có trong NGSL                (từ phổ thông tần suất cao)
  3. có trong Business Service List
  4. còn lại
Trong mỗi tier sắp theo rank tăng dần.

Chạy:
    python generators/build_vocab_seed.py
    python generators/build_vocab_seed.py --quota A1=400,A2=500,B1=700,B2=800,C1=600
"""

from __future__ import annotations

import argparse
import collections
import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "seeds" / "raw"
OUT = ROOT / "seeds"

CEFR_ORDER = ["A1", "A2", "B1", "B2", "C1"]
DEFAULT_QUOTA = {"A1": 400, "A2": 500, "B1": 700, "B2": 800, "C1": 600}

# --- Ánh xạ pos của nguồn sang enum Flashcard §2.3 --------------------------
# Enum đích: noun|verb|adjective|adverb|preposition|conjunction|pronoun|
#            determiner|phrasal_verb|idiom|collocation
POS_MAP = {
    "noun": "noun",
    "verb": "verb",
    "adjective": "adjective",
    "adverb": "adverb",
    "preposition": "preposition",
    "conjunction": "conjunction",
    "pronoun": "pronoun",
    "determiner": "determiner",
    "vern": "verb",  # lỗi chính tả trong nguồn Octanove
}
# Loại hẳn: không phải mục từ vựng để làm flashcard, hoặc thuộc về ngữ pháp.
POS_DROP = {
    "number",           # one, two... không cần flashcard
    "modal auxiliary",  # thuộc concept gram_modal_*
    "be-verb",          # thuộc gram_be_present
    "do-verb",
    "have-verb",
    "infinitive-to",    # thuộc gram_infinitive_purpose
    "interjection",     # oh, wow — không xuất hiện trong đề TOEIC
    "",                 # thiếu dữ liệu
}


def read_csv(name: str) -> list[dict]:
    """Đọc CSV với fallback encoding — TSL_12_stats.csv là cp1252, không phải UTF-8."""
    path = RAW / name
    if not path.exists():
        sys.exit(f"Thiếu {path}. Chạy seeds/raw/fetch_wordlists.sh trước.")
    for enc in ("utf-8-sig", "cp1252", "latin-1"):
        try:
            with path.open(encoding=enc, newline="") as fh:
                return list(csv.DictReader(fh))
        except UnicodeDecodeError:
            continue
    sys.exit(f"Không đọc được {path} với bất kỳ encoding nào đã thử.")


def rank_index(rows: list[dict], word_col: str, rank_col: str) -> dict[str, int]:
    out: dict[str, int] = {}
    for r in rows:
        w = (r.get(word_col) or "").strip().lower()
        if not w:
            continue
        try:
            rank = int(float(r[rank_col]))
        except (KeyError, TypeError, ValueError):
            continue
        out.setdefault(w, rank)
    return out


def build() -> tuple[list[dict], dict]:
    cefrj = read_csv("cefrj-vocabulary-profile-1.5.csv")
    octa = read_csv("octanove-vocabulary-profile-c1c2-1.0.csv")
    tsl = rank_index(read_csv("TSL_12_stats.csv"), "Word", "TSL Rank")
    ngsl = rank_index(read_csv("NGSL_12_stats.csv"), "Lemma", "SFI Rank")
    bsl = rank_index(read_csv("BSL_120_stats.csv"), "Word", "BSL Rank")

    stats = collections.Counter()
    entries: dict[tuple[str, str], dict] = {}

    def add(headword: str, pos_raw: str, cefr: str, source: str) -> None:
        # CEFR-J gộp biến thể chính tả bằng '/': "a.m./A.M./am/AM"
        lemma = headword.split("/")[0].strip().lower()
        if not lemma:
            stats["bỏ: lemma rỗng"] += 1
            return
        pos_raw = (pos_raw or "").strip().lower()
        if pos_raw in POS_DROP:
            stats[f"bỏ: pos={pos_raw or '(rỗng)'}"] += 1
            return
        pos = POS_MAP.get(pos_raw)
        if pos is None:
            stats[f"bỏ: pos lạ={pos_raw}"] += 1
            return
        if cefr not in CEFR_ORDER:
            stats[f"bỏ: cefr={cefr}"] += 1
            return
        key = (lemma, pos)
        if key in entries:
            stats["bỏ: trùng (lemma, pos)"] += 1
            return
        entries[key] = {
            "lemma": lemma,
            "pos": pos,
            "cefr_level": cefr,
            "cefr_source": source,
            "tsl_rank": tsl.get(lemma),
            "ngsl_rank": ngsl.get(lemma),
            "bsl_rank": bsl.get(lemma),
        }

    for r in cefrj:
        add(r["headword"], r["pos"], r["CEFR"].strip(), "cefrj")
    for r in octa:
        add(r["headword"], r["pos"], r["CEFR"].strip(), "octanove")

    return list(entries.values()), stats


def priority(e: dict) -> tuple[int, int, str]:
    """Tier nhỏ hơn = ưu tiên cao hơn."""
    if e["tsl_rank"] is not None:
        return (0, e["tsl_rank"], e["lemma"])
    if e["ngsl_rank"] is not None:
        return (1, e["ngsl_rank"], e["lemma"])
    if e["bsl_rank"] is not None:
        return (2, e["bsl_rank"], e["lemma"])
    return (3, 10**6, e["lemma"])


def topic_hint(e: dict) -> str:
    """Chỉ gán topic khi SUY RA ĐƯỢC từ nguồn. Không đoán.

    Chỉ Business Service List mới suy ra được topic, vì đó đúng là danh sách từ
    vựng thương mại. TSL thì KHÔNG — nó là danh sách phủ toàn bộ đề TOEIC gồm cả
    du lịch, ăn uống, sức khoẻ; suy ra business từ TSL sẽ gán nhầm cho vacation,
    subway, o'clock. Độ liên quan TOEIC đã nằm ở cột in_tsl riêng.

    Ô trống nghĩa là 'chưa xác định' — Phase 5 để LLM chọn từ danh sách topic cố
    định của taxonomy. Không được tự bịa topic ở đây.
    """
    if e["bsl_rank"] is not None:
        return "business_office"
    return ""


FIELDS = [
    "lemma", "pos", "cefr_level", "cefr_source",
    "frequency_rank", "topic_hint", "in_tsl", "tsl_rank", "ngsl_rank", "bsl_rank",
]


def to_row(e: dict) -> dict:
    freq = e["ngsl_rank"] if e["ngsl_rank"] is not None else e["tsl_rank"]
    if freq is None:
        freq = e["bsl_rank"]
    return {
        "lemma": e["lemma"],
        "pos": e["pos"],
        "cefr_level": e["cefr_level"],
        "cefr_source": e["cefr_source"],
        "frequency_rank": freq if freq is not None else "",
        "topic_hint": topic_hint(e),
        "in_tsl": "yes" if e["tsl_rank"] is not None else "no",
        "tsl_rank": e["tsl_rank"] if e["tsl_rank"] is not None else "",
        "ngsl_rank": e["ngsl_rank"] if e["ngsl_rank"] is not None else "",
        "bsl_rank": e["bsl_rank"] if e["bsl_rank"] is not None else "",
    }


def write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--quota", default=None,
                    help="vd A1=400,A2=500,B1=700,B2=800,C1=600")
    args = ap.parse_args()

    quota = dict(DEFAULT_QUOTA)
    if args.quota:
        for part in args.quota.split(","):
            k, _, v = part.partition("=")
            quota[k.strip()] = int(v)

    entries, stats = build()
    by_level: dict[str, list[dict]] = collections.defaultdict(list)
    for e in entries:
        by_level[e["cefr_level"]].append(e)

    print(f"Đã gộp {len(entries)} mục (lemma, pos) duy nhất từ CEFR-J + Octanove\n")
    for reason, n in stats.most_common():
        print(f"  {reason:32} {n}")
    print()

    selected: list[dict] = []
    print(f"{'Level':6}{'Có sẵn':>9}{'Chỉ tiêu':>10}{'Chọn':>7}{'Thiếu':>8}{'∈TSL':>7}")
    shortfall = {}
    for lv in CEFR_ORDER:
        pool = sorted(by_level[lv], key=priority)
        take = pool[: quota[lv]]
        selected.extend(take)
        miss = max(0, quota[lv] - len(pool))
        shortfall[lv] = miss
        n_tsl = sum(1 for e in take if e["tsl_rank"] is not None)
        print(f"{lv:6}{len(pool):9}{quota[lv]:10}{len(take):7}{miss:8}{n_tsl:7}")
    total_q = sum(quota[l] for l in CEFR_ORDER)
    print(f"{'TỔNG':6}{len(entries):9}{total_q:10}{len(selected):7}"
          f"{sum(shortfall.values()):8}"
          f"{sum(1 for e in selected if e['tsl_rank'] is not None):7}")

    rows = [to_row(e) for e in selected]
    write_csv(OUT / "vocab_seed.csv", rows)
    print(f"\nĐã ghi seeds/vocab_seed.csv  ({len(rows)} dòng)")

    for lv in CEFR_ORDER:
        lv_rows = [r for r in rows if r["cefr_level"] == lv]
        write_csv(OUT / "by_level" / f"{lv}.csv", lv_rows)
        print(f"  seeds/by_level/{lv}.csv  {len(lv_rows)} dòng")

    # Kiểm tra bất biến: không được trùng (lemma, pos)
    keys = [(r["lemma"], r["pos"]) for r in rows]
    dups = [k for k, n in collections.Counter(keys).items() if n > 1]
    if dups:
        print(f"\nFAIL — có {len(dups)} cặp (lemma, pos) trùng: {dups[:5]}")
        return 1
    print(f"\nKhông có (lemma, pos) trùng — kiểm tra {len(keys)} dòng OK")

    if any(shortfall.values()):
        print(f"CẢNH BÁO — thiếu hàng ở: "
              f"{ {k: v for k, v in shortfall.items() if v} }")
    return 0


if __name__ == "__main__":
    sys.exit(main())
