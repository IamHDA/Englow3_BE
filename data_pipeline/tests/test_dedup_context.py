"""Luật so trùng câu hỏi — đã sai hai lần, nên khoá lại bằng test.

  1. Dedup theo stem trần đã xoá nhầm 12 câu Part 6 hợp lệ ("Chỗ trống (1)"
     dùng chung ở nhiều đoạn văn khác nhau).
  2. Sửa lần một chỉ tính passage, bỏ sót audio — Part 3 dùng chung
     "What are the speakers discussing?" ở nhiều hội thoại và bị báo trùng oan.
"""

from __future__ import annotations

import pytest

from schemas import AudioAsset, ExamGroup
from schemas.enums import Accent
from tests.conftest import make_item, make_passage
from validators.audit_data import dedup_context

STEM = "What are the speakers discussing?"


def _listening_group(script: str) -> ExamGroup:
    return ExamGroup(
        part_number=3,
        passages=[],
        audio=AudioAsset(script=script, accent=Accent.US, speaker_count=2),
        questions=[make_item(part_number=3, question_text=STEM)])


def test_cung_stem_khac_hoi_thoai_khong_phai_trung():
    a = _listening_group("M: Có chuyện gì với lô hàng sáng nay không?" * 3)
    b = _listening_group("W: Anh xem giúp bản hợp đồng mới gửi chưa?" * 3)
    assert dedup_context(a) != dedup_context(b)


def test_cung_stem_cung_hoi_thoai_moi_la_trung():
    script = "M: Nội dung hội thoại hoàn toàn giống nhau ở cả hai bản ghi." * 3
    assert dedup_context(_listening_group(script)) == dedup_context(_listening_group(script))


def test_phan_doc_van_dung_passage_lam_ngu_canh():
    a = ExamGroup(part_number=7, passages=[make_passage(1, "Thư mời họp quý ba. " * 8)],
                  questions=[make_item(part_number=7, question_text=STEM)])
    b = ExamGroup(part_number=7, passages=[make_passage(1, "Thông báo bảo trì thang máy. " * 8)],
                  questions=[make_item(part_number=7, question_text=STEM)])
    assert dedup_context(a) != dedup_context(b)


@pytest.mark.parametrize("part", [3, 4])
def test_group_nghe_khong_co_passage_van_ra_ngu_canh_khac_rong(part):
    g = _listening_group("W: Kịch bản có nội dung thật, không rỗng." * 3)
    assert dedup_context(g)
