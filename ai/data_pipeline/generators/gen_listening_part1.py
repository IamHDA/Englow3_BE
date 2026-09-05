#!/usr/bin/env python3
"""Generate the six original photograph-description items for Listening Part 1."""

from __future__ import annotations

import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from authoring import LABELS, place_options, report_bias  # noqa: E402
from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    AudioAsset, BatchMetadata, Definition, ExamBatch, ExamGroup, ExamItem,
    ModuleType, Option, QuestionType,
)
from schemas.enums import Accent  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "listening" / "exam_listening_part1_001.json"
PUBLIC_BASE = "http://localhost:9000/images/toeic/listening/part1"
Q = QuestionType

# filename, type, accent, difficulty, concept, four descriptions
ITEMS = [
    ("warehouse_boxes.jpg", Q.LC_PHOTO_ACTION, Accent.US, 0.26, "lc_photo_action", [
        ("A man is stacking boxes on a wooden pallet.", True,
         "Người đàn ông đang xếp các thùng lên pa-lét gỗ."),
        ("A man is painting a line on the floor.", False,
         "Không ai sơn sàn; các vạch sàn đã có sẵn."),
        ("Some workers are carrying a ladder outdoors.", False,
         "Không có thang và cảnh ở trong kho."),
        ("Some packages are being opened at a desk.", False,
         "Các thùng còn đóng và không nằm trên bàn."),
    ]),
    ("bakery_pastries.jpg", Q.LC_PHOTO_ACTION, Accent.UK, 0.30, "lc_photo_action", [
        ("A woman is arranging pastries in a display case.", True,
         "Người phụ nữ đang sắp bánh vào tủ trưng bày."),
        ("A customer is paying at the counter.", False,
         "Không có khách hàng đang thanh toán."),
        ("Some shelves are being removed from a wall.", False,
         "Không ai tháo kệ khỏi tường."),
        ("A window is being washed from outside.", False,
         "Không có hoạt động lau cửa sổ ở bên ngoài."),
    ]),
    ("bicycles_awning.jpg", Q.LC_PHOTO_STATE, Accent.AU, 0.34, "lc_photo_state", [
        ("Several bicycles are parked beneath an awning.", True,
         "Nhiều xe đạp được dựng dưới mái che."),
        ("Some cyclists are crossing an intersection.", False,
         "Không có người đi xe đạp hay giao lộ trong ảnh."),
        ("A bicycle is being loaded onto a truck.", False,
         "Không có xe tải hay hoạt động chất xe."),
        ("The bicycle racks are being repaired.", False,
         "Không ai sửa giá dựng xe."),
    ]),
    ("construction_blueprint.jpg", Q.LC_PHOTO_ACTION, Accent.CA, 0.38, "lc_photo_action", [
        ("Two workers are looking over a set of plans.", True,
         "Hai công nhân đang cùng xem bản vẽ."),
        ("The workers are climbing onto the roof.", False,
         "Họ đứng trên mặt đất và không leo mái."),
        ("Some equipment is being unloaded from a van.", False,
         "Không có xe tải nhỏ hoặc hoạt động dỡ hàng."),
        ("A wall has been covered with finished tiles.", False,
         "Công trình chưa có bức tường lát gạch hoàn thiện."),
    ]),
    ("cafe_tables.jpg", Q.LC_PHOTO_ACTION, Accent.US, 0.31, "lc_photo_action", [
        ("A server is wiping an outdoor table.", True,
         "Nhân viên phục vụ đang lau một bàn ngoài trời."),
        ("Diners are reading menus under umbrellas.", False,
         "Không có thực khách đang ngồi đọc thực đơn."),
        ("All of the chairs have been stacked indoors.", False,
         "Ghế vẫn đặt ngoài sân và không xếp chồng."),
        ("A meal is being delivered by bicycle.", False,
         "Không có xe đạp giao đồ ăn."),
    ]),
    ("airport_luggage.jpg", Q.LC_PHOTO_STATE, Accent.US, 0.36, "lc_photo_state", [
        ("Several suitcases have been lined up beside the check-in counters.", True,
         "Nhiều va-li được xếp thành hàng cạnh quầy làm thủ tục."),
        ("Passengers are collecting bags from a carousel.", False,
         "Đây là quầy làm thủ tục, không phải băng chuyền nhận hành lý."),
        ("An attendant is weighing a piece of luggage.", False,
         "Không có nhân viên đang cân hành lý."),
        ("The counters have been closed with metal shutters.", False,
         "Các quầy vẫn mở và không có cửa cuốn kim loại."),
    ]),
]


def main() -> int:
    groups = []
    for index, (filename, qtype, accent, difficulty, concept, raw_options) in enumerate(ITEMS):
        options = place_options(index, filename, raw_options)
        script = " ".join(f"({LABELS[pos]}) {text}" for pos, (text, _, _) in enumerate(options))
        correct_text = next(text for text, correct, _ in raw_options if correct)
        groups.append(ExamGroup(
            part_number=1,
            image_url=f"{PUBLIC_BASE}/{filename}",
            audio=AudioAsset(script=script, accent=accent, speaker_count=1),
            questions=[ExamItem(
                part_number=1,
                question_text=None,
                question_type=qtype,
                options=[Option(
                    label=LABELS[pos], text=text, is_correct=correct, rationale_vi=rationale,
                ) for pos, (text, correct, rationale) in enumerate(options)],
                concept_ids=[concept],
                difficulty_prior=difficulty,
                explanation=Definition(
                    en=f'The photograph is best described by: "{correct_text}"',
                    vi=next(rationale for text, correct, rationale in raw_options if correct),
                ),
            )],
        ))

    for warning in report_bias(groups):
        print(f"  WARNING {warning}")
    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_listening_part1_001",
            module_type=ModuleType.EXAM,
            generated_by="codex-gpt-5",
            generated_at=datetime.now(UTC),
            total_records=len(groups),
        ),
        groups=groups,
    ), OUT)
    print(f"Part 1: {len(groups)} groups, {len(groups)} questions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
