"""stable_id phải tất định — DoD Phase 2."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas.ids import (  # noqa: E402
    exam_group_id, exam_item_id, flashcard_id, passage_hash, stable_id,
)


def test_same_input_same_id():
    """Tính idempotent: chạy lại pipeline trên cùng input → cùng ID → upsert."""
    a = stable_id("vocab", "address", "verb", 2)
    b = stable_id("vocab", "address", "verb", 2)
    assert a == b


def test_different_input_different_id():
    assert stable_id("vocab", "address", "verb", 1) != stable_id("vocab", "address", "verb", 2)
    assert stable_id("vocab", "address", "noun", 1) != stable_id("vocab", "address", "verb", 1)


def test_normalises_case_and_whitespace():
    """'The  report' và 'the report' là cùng nội dung, phải ra cùng ID."""
    assert stable_id("itm", "The  report") == stable_id("itm", "the report")
    assert stable_id("itm", "  padded  ") == stable_id("itm", "padded")


def test_format():
    sid = stable_id("vocab", "test")
    prefix, _, digest = sid.partition("_")
    assert prefix == "vocab"
    assert len(digest) == 16
    assert all(c in "0123456789abcdef" for c in digest)


def test_prefix_separates_namespaces():
    """Cùng nội dung nhưng khác loại thì không được đụng ID nhau."""
    assert stable_id("vocab", "x") != stable_id("itm", "x")


def test_empty_prefix_rejected():
    with pytest.raises(ValueError):
        stable_id("", "x")


def test_order_matters():
    assert stable_id("itm", "a", "b") != stable_id("itm", "b", "a")


def test_typed_helpers_are_stable():
    assert flashcard_id("Address", "verb", 2) == flashcard_id("address", "verb", 2)
    assert exam_item_id(5, "Q?", "answer") == exam_item_id(5, "Q?", "answer")
    assert exam_group_id(7, "p1", "p2") == exam_group_id(7, "p1", "p2")


def test_part1_null_question_text_is_stable():
    """Part 1 không có question_text — vẫn phải sinh được ID ổn định."""
    assert exam_item_id(1, None, "A man is typing.") == exam_item_id(1, None, "A man is typing.")


def test_passage_hash_order_sensitive():
    """Part 7 double passage: đảo thứ tự là đề khác, phải ra group khác."""
    assert passage_hash("first", "second") != passage_hash("second", "first")
