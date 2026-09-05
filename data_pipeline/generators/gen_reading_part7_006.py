#!/usr/bin/env python3
"""Part 7 batch 006 — two original double-passage sets for the ETS blueprint."""

from __future__ import annotations

import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from authoring import report_bias  # noqa: E402
from gen_reading_part7 import build_group  # noqa: E402
from guarded_write import guarded_write_batch  # noqa: E402
from schemas import BatchMetadata, ExamBatch, ModuleType, QuestionType  # noqa: E402
from schemas.enums import PassageType  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "reading" / "exam_reading_part7_006.json"
Q = QuestionType


MULTI = [
    (
        [
            (PassageType.EMAIL, """From: Elaine Brooks <elaine.brooks@northmere.example>
To: bookings@willowroom.example
Date: 12 April
Subject: Product launch reception — revised numbers

Hello,

I am writing about our reception on 3 May. Registration has been stronger than
expected, so we now anticipate 72 guests rather than the 55 stated on our
booking form. Could you provide food for the larger number? The vegetarian share
is unchanged at one quarter of the total. We would also like to move the serving
time from 6:00 P.M. to 6:30 P.M. because the presentation will run longer than
planned.

Our finance team paid the required deposit yesterday. Please send an updated
invoice showing the new guest count and deducting that payment. The room layout
and the request for two mobile microphone stands remain unchanged.

Regards,
Elaine Brooks
Events Manager, Northmere Instruments"""),
            (PassageType.NOTICE, """WILLOW ROOM — UPDATED BOOKING CONFIRMATION

Client: Northmere Instruments
Event date: 3 May
Room access: 4:30 P.M.
Reception service: 6:30 P.M.
Guests: 72

Menu package: Orchard buffet, £28 per guest
Dietary meals: 18 vegetarian portions included
Equipment: Projector, lectern, and two fixed microphones

Charges
Room hire                                      £640
Food service (72 × £28)                      £2,016
Equipment                                      £110
Subtotal                                     £2,766
Deposit received                              −£500
Balance due                                  £2,266

The balance is payable no later than five business days before the event.
Changes to equipment must be requested by 24 April. Please quote booking WR-381
in all correspondence."""),
        ],
        [
            ("Why did Ms Brooks contact the Willow Room?", Q.RC_MAIN_IDEA,
             "rc_main_idea", 0.36, "we now anticipate 72 guests rather than the 55",
             [("To revise arrangements for an upcoming reception", True,
               "Cô Brooks thay đổi số khách, giờ phục vụ và yêu cầu hóa đơn cập nhật."),
              ("To cancel a product presentation", False,
               "Email không hủy sự kiện; sự kiện vẫn diễn ra ngày 3 May."),
              ("To complain about a late food delivery", False,
               "Chưa có giao đồ ăn nào xảy ra và email không phải khiếu nại."),
              ("To reserve a different meeting room", False,
               "Cô không yêu cầu đổi phòng.")]),
            ("What has NOT changed according to Ms Brooks?", Q.RC_NOT_TRUE,
             "rc_not_true", 0.42, "The room layout and the request for two mobile microphone stands remain unchanged",
             [("The requested room layout", True,
               "Email nói rõ bố trí phòng vẫn giữ nguyên."),
              ("The number of expected guests", False,
               "Số khách tăng từ 55 lên 72."),
              ("The food serving time", False,
               "Giờ phục vụ chuyển từ 6:00 sang 6:30."),
              ("The amount shown on the invoice", False,
               "Cô yêu cầu hóa đơn mới theo số khách mới và trừ tiền đặt cọc.")]),
            ("How much does the buffet cost for each guest?", Q.RC_DETAIL,
             "rc_detail", 0.30, 2, "Orchard buffet, £28 per guest",
             [("£28", True, "Xác nhận ghi trực tiếp giá £28 cho mỗi khách."),
              ("£18", False, "18 là số suất chay, không phải đơn giá."),
              ("£38", False, "Không có mức giá £38 trong xác nhận."),
              ("£72", False, "72 là tổng số khách.")]),
            ("Which request in Ms Brooks's email was recorded incorrectly in the confirmation?",
             Q.RC_CROSS_REFERENCE, "rc_cross_reference", 0.62, 2,
             "Equipment: Projector, lectern, and two fixed microphones",
             [("The type of microphone stands", True,
               "Email giữ yêu cầu hai chân micro di động, nhưng xác nhận ghi hai micro cố định."),
              ("The reception serving time", False,
               "Cả hai tài liệu đều ghi 6:30 P.M."),
              ("The total number of guests", False,
               "Cả hai tài liệu đều ghi 72 khách."),
              ("The number of vegetarian portions", False,
               "Một phần tư của 72 là 18, đúng với xác nhận.")]),
            ("What can be inferred about the 18 vegetarian portions?",
             Q.RC_CROSS_REFERENCE, "rc_cross_reference", 0.58, 2,
             "Dietary meals: 18 vegetarian portions included",
             [("They represent the same proportion Ms Brooks originally requested", True,
               "Email giữ tỷ lệ một phần tư; 18 chính là một phần tư của 72."),
              ("They will be charged separately from the buffet", False,
               "Xác nhận nói các suất này đã được bao gồm."),
              ("They were added after the updated confirmation was issued", False,
               "Không có thông tin nào cho thấy chúng được thêm sau."),
              ("They are intended for the presentation staff only", False,
               "Hai tài liệu không giới hạn các suất chay cho nhân viên.")]),
        ],
    ),
    (
        [
            (PassageType.NOTICE, """RIVERDALE TRANSIT — WEEKEND SERVICE NOTICE

On Saturday, 17 August, engineering work will close the Green Line between
Central Square and East Market from the start of service until 4:00 P.M. Trains
will continue to operate normally west of Central Square and east of East
Market.

A free replacement bus will serve Central Square, Museum Street, Park Gate and
East Market every 15 minutes. The bus will not stop at City Library because the
road beside the library is also being repaired. Passengers for that stop should
leave the bus at Museum Street and walk approximately six minutes.

After 4:00 P.M., Green Line trains will operate along the full route but only
every 20 minutes. Normal ten-minute service is expected to resume on Sunday.
Passengers using monthly passes do not need a separate ticket for the
replacement bus."""),
            (PassageType.EMAIL, """From: Marcus Lee
To: Saturday volunteer team
Date: 14 August
Subject: Getting to the library workshop

Thank you again for helping with our children's technology workshop this
Saturday. The session begins at 10:30 A.M., and we need everyone at City Library
by 9:45 to arrange the laptops and registration table.

Because of the Green Line closure, do not wait for a train at Park Gate. I
suggest meeting outside Central Square Station at 9:05 and taking the first
available replacement bus together. We will get off at Museum Street and walk
the final section. I will carry the box of name badges, while Priya will bring
the printed activity sheets.

If you are coming from east of East Market, the trains there are still running;
send me a message and we will meet you at the library entrance instead. Please
allow extra time because the replacement buses may be crowded."""),
        ],
        [
            ("Why will the Green Line be partly closed?", Q.RC_DETAIL,
             "rc_detail", 0.28, "engineering work will close the Green Line",
             [("Engineering work is being carried out", True,
               "Thông báo nêu trực tiếp công tác kỹ thuật là nguyên nhân đóng tuyến."),
              ("A public event is taking place", False,
               "Không có sự kiện công cộng nào là nguyên nhân."),
              ("Too few drivers are available", False,
               "Thông báo không đề cập thiếu lái tàu."),
              ("New trains are being tested", False,
               "Không có việc thử tàu mới.")]),
            ("What should passengers for City Library do?", Q.RC_DETAIL,
             "rc_detail", 0.35, "leave the bus at Museum Street and walk approximately six minutes",
             [("Get off at Museum Street and continue on foot", True,
               "Xe thay thế không dừng tại thư viện nên hành khách phải đi bộ từ Museum Street."),
              ("Change buses at Park Gate", False,
               "Không có yêu cầu đổi xe tại Park Gate."),
              ("Wait until service resumes at 4:00 P.M.", False,
               "Thông báo cung cấp tuyến thay thế vào buổi sáng."),
              ("Purchase a separate bus ticket", False,
               "Người có vé tháng không cần vé riêng.")]),
            ("What is Mr Lee mainly asking volunteers to do?", Q.RC_MAIN_IDEA,
             "rc_main_idea", 0.42, 2, "meeting outside Central Square Station at 9:05",
             [("Follow a revised travel plan to the workshop", True,
               "Email hướng dẫn điểm hẹn, thời gian và lộ trình thay thế."),
              ("Move the workshop to a different building", False,
               "Workshop vẫn tổ chức tại City Library."),
              ("Bring their own laptop computers", False,
               "Nhóm sẽ sắp xếp laptop có sẵn; không yêu cầu mang máy cá nhân."),
              ("Stay after the workshop to repair equipment", False,
               "Email không nói về việc ở lại sửa thiết bị.")]),
            ("Why does Mr Lee tell the group to meet at Central Square?",
             Q.RC_CROSS_REFERENCE, "rc_cross_reference", 0.55, 1,
             "A free replacement bus will serve Central Square",
             [("They can board the replacement bus there", True,
               "Thông báo cho biết xe thay thế dừng ở Central Square; email chọn nơi đó làm điểm hẹn."),
              ("The library has temporarily moved there", False,
               "Thư viện không chuyển địa điểm."),
              ("Green Line trains terminate there all weekend", False,
               "Việc gián đoạn chỉ đến 4:00 P.M. thứ Bảy."),
              ("The activity sheets must be collected there", False,
               "Priya sẽ mang tài liệu, không có điểm nhận tại ga.")]),
            ("Who may not need to join the group at Central Square?",
             Q.RC_CROSS_REFERENCE, "rc_cross_reference", 0.60, 1,
             "Trains will continue to operate normally west of Central Square and east of East Market",
             [("Volunteers travelling from east of East Market", True,
               "Thông báo nói tàu phía đông vẫn chạy; email bảo nhóm này gặp thẳng ở thư viện."),
              ("Volunteers who hold monthly passes", False,
               "Vé tháng chỉ ảnh hưởng việc mua vé xe thay thế, không đổi điểm hẹn."),
              ("Volunteers carrying registration materials", False,
               "Marcus và Priya vẫn thuộc kế hoạch đi chung nếu không ở phía đông."),
              ("Volunteers arriving before 9:05", False,
               "Đến sớm không phải lý do được bỏ điểm hẹn.")]),
        ],
    ),
]


def main() -> int:
    groups, idx = [], 500
    for passages, rows in MULTI:
        group, idx = build_group(passages, rows, idx)
        groups.append(group)

    total = sum(len(group.questions) for group in groups)
    print(f"Part 7 batch 006: {len(groups)} double-passage groups, {total} questions")
    for warning in report_bias(groups):
        print(f"  WARNING {warning}")

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_reading_part7_006",
            module_type=ModuleType.EXAM,
            generated_by="codex-gpt-5",
            generated_at=datetime.now(UTC),
            total_records=len(groups),
        ),
        groups=groups,
    ), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
