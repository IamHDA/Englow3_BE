#!/usr/bin/env python3
"""Part 7 batch 003 — single, double, triple.

    python generators/gen_reading_part7_003.py
"""

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
OUT = ROOT / "output" / "exams" / "bank" / "reading" / "exam_reading_part7_003.json"
GENERATED_BY = "claude-opus-5"
Q = QuestionType

SINGLE = [

(PassageType.LETTER, """Pellingham Insurance Services
14 Bracken Row, Ardleigh

8 April

Dear Mr Ferreira,

Thank you for reporting the damage to the delivery van registered VN61 KTP. Our
assessor visited your depot on 3 April and has now submitted her report.

The report confirms that the damage to the nearside panel is consistent with the
account you gave. We are therefore able to settle the claim in full, less the
£250 excess set out in section 4 of your policy.

However, the assessor also noted corrosion along the lower door frame that
predates the incident. This is not covered, and we would encourage you to have
it treated before it spreads, as future claims involving that panel may be
affected.

Payment of £1,840 will reach your account within ten working days. If you have
not received it by 25 April, please telephone the claims line rather than
writing, as post can add several days.

Yours sincerely,
Hilde Ravensworth
Claims Department""",
 [("Why is the letter being sent?", Q.RC_MAIN_IDEA, "rc_main_idea", 0.35,
   "We are therefore able to settle the claim in full",
   [("To confirm that a claim has been approved", True,
     "Nội dung chính là thông báo chấp thuận bồi thường và số tiền."),
    ("To request further evidence about the incident", False,
     "Giám định viên đã đi khảo sát và nộp báo cáo rồi."),
    ("To inform Mr Ferreira that his policy has ended", False,
     "Không có chi tiết nào về việc chấm dứt hợp đồng."),
    ("To explain why the claim has been refused entirely", False,
     "Ngược lại — công ty đồng ý chi trả.")]),

  ("What does the assessor mention that is NOT covered?", Q.RC_DETAIL,
   "rc_detail", 0.45, "corrosion along the lower door frame that predates the incident",
   [("Rust that was present before the accident", True,
     "Bài nói rõ ăn mòn có từ trước sự cố nên không được bảo hiểm."),
    ("Damage to the nearside panel", False, "Phần này ĐƯỢC chi trả."),
    ("The cost of the assessor's visit", False,
     "Không có thông tin về phí giám định."),
    ("Repairs carried out at the depot", False,
     "Bài không nhắc tới việc sửa chữa nào tại kho.")]),

  ("What is Mr Ferreira advised to do if payment is late?", Q.RC_DETAIL,
   "rc_detail", 0.40, "please telephone the claims line rather than writing",
   [("Call rather than send a letter", True,
     "Bài khuyên gọi điện vì thư từ mất thêm vài ngày."),
    ("Write to the claims department", False, "Bài khuyên KHÔNG viết thư."),
    ("Wait a further ten working days", False,
     "25 tháng Tư đã là mốc sau mười ngày làm việc."),
    ("Contact the assessor directly", False,
     "Giám định viên không phải đầu mối liên hệ.")])]),

(PassageType.SCHEDULE, """MERRIDALE COMMUNITY CENTRE — Room Timetable, Spring Term

MONDAY
  09:00–11:00  Parent and toddler group      Hall
  18:30–20:00  Conversational Spanish        Room 2
  19:00–21:00  Badminton club                Hall

TUESDAY
  10:00–12:00  Job-search workshop           Room 2
  14:00–16:00  Chair-based exercise          Hall
  18:00–20:30  Photography society           Room 3

WEDNESDAY
  09:30–11:30  Sewing circle                 Room 3
  17:30–19:00  Junior football (indoor)      Hall
  19:30–21:00  Book group                    Room 2

Rooms may be booked for private use on any weekday between 12:00 and 14:00,
except Tuesday. Bookings are made at reception and must be paid for on the day.

The hall is unavailable throughout the last week of term while the floor is
resurfaced. Groups normally using the hall will be offered Room 3.

Tea and coffee are available from the machine in the foyer during all sessions.
Groups wishing to serve refreshments of their own must tell reception in advance
so that the kitchen can be unlocked and a food-safety notice displayed.

Please leave rooms as you found them. Chairs stacked against the far wall,
windows closed, and lights switched off at the panel by the door. Any equipment
borrowed from the store cupboard must be signed back in the same evening; the
centre cannot chase items the following morning.""",
 [("Which room can be booked privately on Wednesday at midday?", Q.RC_DETAIL,
   "rc_detail", 0.45, "Rooms may be booked for private use on any weekday between 12:00 and 14:00",
   [("Any of the rooms", True,
     "Quy định cho phép đặt phòng bất kỳ ngày thường từ 12:00–14:00, trừ thứ Ba."),
    ("Only Room 2, as the hall is in use", False,
     "Buổi trưa thứ Tư không có hoạt động nào trong lịch."),
    ("None, because bookings are only on Tuesday", False,
     "Thứ Ba là ngày DUY NHẤT không cho đặt."),
    ("Only the hall, as the other rooms are booked", False,
     "Không phòng nào có lịch vào khung giờ đó.")]),

  ("What happens to the badminton club in the final week?", Q.RC_INFERENCE,
   "rc_inference", 0.55, "Groups normally using the hall will be offered Room 3",
   [("It will be moved to a different room", True,
     "Câu lạc bộ dùng hội trường, mà hội trường đóng cửa nên được chuyển sang Room 3."),
    ("It will be cancelled for that week", False,
     "Bài nói các nhóm sẽ được bố trí phòng khác, không huỷ."),
    ("It will move to a Tuesday evening", False,
     "Không có chi tiết nào về đổi ngày."),
    ("It will share the hall with junior football", False,
     "Hội trường đóng cửa hoàn toàn tuần đó.")]),

  ("Which activity uses Room 2 on more than one day?", Q.RC_DETAIL,
   "rc_detail", 0.40, "19:30–21:00  Book group                    Room 2",
   [("None — each Room 2 activity meets once a week", True,
     "Ba hoạt động ở Room 2 là tiếng Tây Ban Nha, tìm việc và câu lạc bộ sách, "
     "mỗi hoạt động một ngày khác nhau."),
    ("Conversational Spanish", False, "Chỉ họp tối thứ Hai."),
    ("The job-search workshop", False, "Chỉ họp sáng thứ Ba."),
    ("The book group", False, "Chỉ họp tối thứ Tư.")])]),
]

# --- DOUBLE ---------------------------------------------------------------
DOUBLE_PASSAGES = [
 (PassageType.NOTICE, """STAFF NOTICE — Cycle to Work Scheme, Quillon Systems

The company will again take part in the Cycle to Work scheme this year. Staff may
buy a bicycle and safety equipment through the scheme and repay the cost from
salary over twelve or eighteen months.

The maximum value is £1,500 including accessories. Helmets, lights and locks may
be included; clothing and repair services may not.

To take part you must have completed six months' service and must not be in a
probation or notice period. Applications open on 1 May and close on 31 May. We
cannot accept late applications, as the scheme is administered externally.

Collect an application form from Reception or download one from the intranet.
Completed forms go to Payroll, not to your line manager."""),

 (PassageType.EMAIL, """From: Tomasz Wierzbicki
To: Payroll
Date: 26 May
Subject: Cycle scheme — query before applying

Hello,

I joined Quillon Systems on 2 January this year and my probation ended in April,
so I believe I am eligible.

I would like to buy a bicycle at £1,280 together with a helmet and two lights,
which come to £145. I would prefer to repay over eighteen months if that is
still an option at this value.

Could you also confirm whether I can include a waterproof jacket? The shop has
offered one at £70 as part of the package, and it would be genuinely useful on
the stretch of road between the station and our site, which has no shelter at
all when the weather turns.

If the jacket cannot be included I will simply buy it myself, but I would rather
ask than assume and then have the whole application sent back.

Thanks,
Tomasz"""),
]

DOUBLE_QUESTIONS = [
 ("Is Mr Wierzbicki eligible for the scheme?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.65, 1,
  "you must have completed six months' service and must not be in a probation or notice period",
  [("No, because he has not yet completed six months' service", True,
    "Thông báo đòi SÁU tháng làm việc. Anh vào ngày 2 tháng Một, viết thư ngày "
    "26 tháng Năm — mới gần năm tháng. Hết thử việc không thay thế được điều kiện này."),
   ("Yes, because he joined in January and has passed his probation period", False,
    "Hết thử việc chỉ là MỘT trong hai điều kiện; điều kiện sáu tháng chưa đạt."),
   ("No, because the application window closed on the first of May", False,
    "1 tháng Năm là ngày MỞ đơn, ngày đóng là 31 tháng Năm."),
   ("Yes, provided that his line manager approves the request first", False,
    "Thông báo nói rõ đơn gửi thẳng Payroll, không qua quản lý trực tiếp.")]),

 ("Can the jacket be included in his application?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.60, 1, "clothing and repair services may not",
  [("No, because clothing is excluded from the scheme", True,
    "Thông báo loại trừ quần áo; áo khoác chống nước là quần áo."),
   ("Yes, because the total stays under £1,500", False,
    "Giới hạn tiền không phải là lý do — loại hàng mới là điều quyết định."),
   ("Yes, because it was offered as part of a package", False,
    "Cách bán không thay đổi quy định của chương trình."),
   ("No, because the total would exceed the maximum value", False,
    "1280 + 145 + 70 = 1495, vẫn dưới £1,500.")]),

 ("Why does Mr Wierzbicki contact Payroll before submitting his application?",
  Q.RC_INTENT, "rc_intent", 0.55, 2,
  "I would rather\nask than assume and then have the whole application sent back",
  [("To check whether the waterproof jacket can be included", True,
    "Ông hỏi trước để xác nhận áo khoác có được tính vào chương trình hay không."),
   ("To ask Payroll to waive the six-month service rule", False,
    "Email không xin ngoại lệ về thâm niên."),
   ("To change the repayment period to twelve months", False,
    "Ông muốn trả trong mười tám tháng, không phải mười hai tháng."),
   ("To request a higher maximum purchase value", False,
    "Ông không yêu cầu nâng giới hạn £1,500.")]),

 ("Where should the completed form be sent?", Q.RC_DETAIL, "rc_detail", 0.30, 1,
  "Completed forms go to Payroll, not to your line manager",
 [("To Payroll", True, "Thông báo nói rõ nộp cho bộ phận Payroll."),
   ("To the employee's line manager", False, "Thông báo nói rõ KHÔNG gửi quản lý."),
   ("To Reception, where forms are collected", False,
    "Lễ tân chỉ phát mẫu đơn, không nhận đơn đã điền."),
   ("To the external scheme administrator", False,
    "Bên ngoài quản trị chương trình nhưng nhân viên không nộp thẳng cho họ.")]),

 ("What is the maximum value allowed under the scheme?", Q.RC_DETAIL,
  "rc_detail", 0.30, 1, "The maximum value is £1,500 including accessories",
  [("£1,500 including accessories", True,
    "Thông báo nêu rõ mức tối đa đã bao gồm phụ kiện."),
   ("£1,500 excluding accessories", False,
    "Phụ kiện được tính trong giới hạn, không nằm ngoài."),
   ("£1,425 including clothing", False,
    "Đây là tổng mua dự kiến của người viết thư, không phải giới hạn chương trình."),
   ("£1,280 for the bicycle only", False,
    "Đây là giá chiếc xe trong email, không phải mức tối đa.")]),
]


def main() -> int:
    groups = []
    idx = 200

    for ptype, text, rows in SINGLE:
        g, idx = build_group([(ptype, text)], rows, idx)
        groups.append(g)

    g, idx = build_group(DOUBLE_PASSAGES, DOUBLE_QUESTIONS, idx)
    groups.append(g)

    n_q = sum(len(g.questions) for g in groups)
    print(f"Part 7 batch 003: {len(groups)} group, {n_q} câu")
    for g in groups:
        n_p = len(g.passages)
        if n_p > 1:
            print(f"  group {n_p} passage: "
                  f"{sum(1 for q in g.questions if q.question_type is Q.RC_CROSS_REFERENCE)}"
                  f" câu đọc chéo (cần ≥2)")
        for p in g.passages:
            w = len(p.text.split())
            lo, hi = (150, 260) if n_p == 1 else (100, 200)
            if not (lo <= w <= hi):
                print(f"  ⚠ passage {w} từ, ngoài {lo}–{hi}")
    print(f"  evidence_span: "
          f"{sum(1 for g in groups for q in g.questions if q.evidence_span)}/{n_q}\n")
    for w in report_bias(groups):
        print(f"  ⚠ {w}")
    print()

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_reading_part7_003", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
