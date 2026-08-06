"""Test cho lưới chắn đa dạng — Bước 1 của kế hoạch remediation.

Phải xanh 100% TRƯỚC khi sinh lại bất kỳ dữ liệu nào. Lưới chắn này đã có lỗ
hổng hai lần (đếm chuỗi thô, rồi replace không ranh giới từ); lần này phải có
test chứng minh nó bắt được đúng thứ nó sinh ra để bắt.

Fixture tự dựng, KHÔNG đọc output/ — dữ liệu đó gitignored và sắp bị xoá.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from validators.diversity import check_skeleton_diversity, skeleton  # noqa: E402


# --- Test-case 1: ranh giới từ ------------------------------------------------

def test_short_lemma_does_not_corrupt_other_words():
    """'at' không được phá 'station' — bug của bản v2."""
    got = skeleton("Please meet at the station.", "at")
    assert got == "Please meet § the station."
    assert "st§ion" not in got


def test_substring_inside_longer_word_untouched():
    assert skeleton("The airport terminal is closed.", "air") == \
        "The airport terminal is closed."


# --- Test-case 2: không phân biệt hoa thường ----------------------------------

def test_capitalised_occurrence_is_masked():
    """'Vacation' đầu câu phải bị che — nếu không thì bỏ lọt khuôn mẫu."""
    assert skeleton("Vacation days accrue monthly.", "vacation") == \
        "§ days accrue monthly."


def test_mixed_case_all_masked():
    assert skeleton("VACATION, Vacation and vacation.", "vacation") == "§, § and §."


# --- Test-case 3: bắt được điền khuôn -----------------------------------------

TEMPLATE_EN = "The {pos} '{lemma}', used in general and professional English contexts."
TEMPLATE_EX = "Please review the usage of '{lemma}' before the meeting."
WORDS = [("vacation", "noun"), ("airport", "noun"), ("cloth", "noun"),
         ("negotiate", "verb"), ("efficient", "adjective"), ("quickly", "adverb")]


def _templated(n: int = 600) -> list[dict]:
    """Tái dựng đúng khuôn của đợt dữ liệu hỏng 2026-08-06."""
    out = []
    for i in range(n):
        lemma, pos = WORDS[i % len(WORDS)]
        lemma = f"{lemma}{i}"          # mỗi bản ghi một từ khác nhau
        out.append({"lemma": lemma, "pos": pos,
                    "definition": TEMPLATE_EN.format(pos=pos, lemma=lemma),
                    "sentence": TEMPLATE_EX.format(lemma=lemma)})
    return out


def _get_vars(r):
    return (r["lemma"], r["pos"])


def test_rejects_template_filled_definitions():
    ok, ratio, uniq = check_skeleton_diversity(
        _templated(), lambda r: r["definition"], _get_vars)
    assert not ok, "phải REJECT dữ liệu điền khuôn"
    assert uniq <= 4, f"600 bản ghi chỉ nên ra vài bộ xương, ra {uniq}"
    assert ratio < 0.02


def test_rejects_template_filled_examples():
    ok, ratio, uniq = check_skeleton_diversity(
        _templated(), lambda r: r["sentence"], _get_vars)
    assert not ok
    assert uniq == 1, f"một khuôn ví dụ duy nhất, ra {uniq}"


def test_naive_string_count_would_have_passed():
    """Chứng minh vì sao phải che token: đếm chuỗi thô cho 100% 'đa dạng'.

    Đây chính là lý do bản v1 của lưới chắn bỏ lọt 3 000 flashcard hỏng.
    """
    recs = _templated()
    raw = {r["sentence"] for r in recs}
    assert len(raw) / len(recs) == 1.0     # trông hoàn hảo
    ok, _, _ = check_skeleton_diversity(recs, lambda r: r["sentence"], _get_vars)
    assert not ok                          # nhưng bộ xương thì không


# --- Không được reject oan nội dung thật --------------------------------------

def test_accepts_genuinely_varied_content():
    real = [
        {"lemma": "vacation", "pos": "noun",
         "sentence": "We get two weeks of vacation every summer."},
        {"lemma": "airport", "pos": "noun",
         "sentence": "Heavy fog closed the airport for six hours."},
        {"lemma": "invoice", "pos": "noun",
         "sentence": "Attach the invoice before you submit the claim."},
        {"lemma": "negotiate", "pos": "verb",
         "sentence": "They negotiate contracts on behalf of small suppliers."},
        {"lemma": "efficient", "pos": "adjective",
         "sentence": "The new layout is far more efficient than the old one."},
    ]
    ok, ratio, uniq = check_skeleton_diversity(real, lambda r: r["sentence"], _get_vars)
    assert ok
    assert ratio == 1.0 and uniq == len(real)


def test_threshold_boundary():
    """Ngay tại ngưỡng 0.60 phải đạt; dưới ngưỡng thì trượt."""
    recs = ([{"lemma": "x", "pos": "noun", "s": f"Unique sentence number {i}."}
             for i in range(6)] +
            [{"lemma": "x", "pos": "noun", "s": "Same shape here."} for _ in range(4)])
    ok, ratio, _ = check_skeleton_diversity(recs, lambda r: r["s"], _get_vars)
    assert abs(ratio - 0.7) < 1e-9 and ok

    recs = ([{"lemma": "x", "pos": "noun", "s": f"Unique sentence number {i}."}
             for i in range(5)] +
            [{"lemma": "x", "pos": "noun", "s": "Same shape here."} for _ in range(5)])
    ok, ratio, _ = check_skeleton_diversity(recs, lambda r: r["s"], _get_vars)
    assert abs(ratio - 0.6) < 1e-9 and ok       # đúng ngưỡng → đạt


# --- Biên ---------------------------------------------------------------------

def test_empty_and_none_are_safe():
    assert skeleton("", "x") == ""
    assert skeleton("text", "", None) == "text"
    ok, ratio, uniq = check_skeleton_diversity([], lambda r: "", lambda r: ())
    assert not ok and ratio == 0.0 and uniq == 0


def test_regex_metacharacters_in_lemma():
    """Lemma có ký tự đặc biệt regex không được làm hàm nổ."""
    assert skeleton("The o'clock chime rang.", "o'clock") == "The § chime rang."
    assert skeleton("Use cost-effective methods.", "cost-effective") == \
        "Use § methods."
