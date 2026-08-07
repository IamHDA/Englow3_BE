#!/usr/bin/env python3
"""Part 7 — đọc hiểu, viết tay theo định dạng TOEIC.

Nội dung viết mới hoàn toàn (§0.4). Tên công ty và người đều hư cấu.

Hai ràng buộc riêng của Part 7, part_rules cưỡng chế:
  1. MỌI câu phải có `evidence_span` — offset tính bằng string-match trong CODE
     (authoring.find_span), không để LLM tự khai. Không định vị được câu chứa
     đáp án nghĩa là câu hỏi không hợp lệ, phải sửa nội dung.
  2. Group nhiều passage phải có ≥2 câu `rc_cross_reference` — bắt buộc đọc chéo
     ≥2 văn bản mới trả lời được. Nếu không thì tách ra làm đề đơn là xong.

    python generators/gen_reading_part7.py
"""

from __future__ import annotations

import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from authoring import LABELS, find_span, place_options, report_bias  # noqa: E402
from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    BatchMetadata, Definition, ExamBatch, ExamGroup, ExamItem, ModuleType,
    Option, Passage, QuestionType,
)
from schemas.enums import PassageType  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "reading" / "exam_reading_part7_001.json"
GENERATED_BY = "claude-opus-5"

Q = QuestionType

# ===========================================================================
# SINGLE PASSAGE
# ===========================================================================
# (passage_type, text, [(stem, qtype, concept, diff, evidence_quote, options)])

SINGLE: list[tuple] = [

(PassageType.NOTICE, """NOTICE TO RESIDENTS — Kestrel Court

The water supply to Blocks A and B will be shut off on Wednesday, 18 March, from
09:00 until approximately 15:00 while the contractor replaces the main valve in
the basement. Block C is served by a separate line and will not be affected.

Residents are advised to store enough water for the morning. The management
office will place two 20-litre containers in each lobby from Tuesday evening for
anyone who is unable to do so.

If the work finishes early, we will post an update on the noticeboard by the
lifts rather than contacting residents individually. Should the work overrun
past 17:00, affected residents may claim a credit of £15 against next month's
service charge by emailing the office before Friday.

We apologise for the disruption. The valve has failed twice since December and
replacing it now avoids a more serious failure later in the year.""",
 [("What is the main purpose of the notice?", Q.RC_MAIN_IDEA, "rc_main_idea", 0.35,
   "The water supply to Blocks A and B will be shut off",
   [("To warn residents about a planned interruption to their water supply", True,
     "Cả thông báo xoay quanh việc cắt nước theo kế hoạch và cách ứng phó."),
    ("To announce that the monthly service charge for the building will increase", False,
     "Có nhắc £15 nhưng đó là khoản bồi hoàn, không phải tăng phí."),
    ("To ask residents to report faults with the main valve", False,
     "Van đã được xác định hỏng rồi; thông báo không yêu cầu ai báo cáo."),
    ("To invite residents to a meeting about building maintenance", False,
     "Không có cuộc họp nào được nhắc tới.")]),

  ("Which residents will NOT lose their water supply?", Q.RC_NOT_TRUE, "rc_not_true", 0.40,
   "Block C is served by a separate line and will not be affected",
   [("Those living in Block C", True, "Bài nói rõ Block C dùng đường ống riêng."),
    ("Those living in Block A", False, "Block A nằm trong danh sách bị cắt nước."),
    ("Those who collect water containers", False,
     "Lấy thùng nước không thay đổi việc bị cắt nước."),
    ("Those who email the office before Friday", False,
     "Email là để xin bồi hoàn, không liên quan tới việc có nước hay không.")]),

  ("How will residents be told if the work ends ahead of schedule?", Q.RC_DETAIL,
   "rc_detail", 0.45, "we will post an update on the noticeboard by the lifts",
   [("A note will be displayed near the lifts", True,
     "Bài nói rõ sẽ dán thông báo trên bảng tin cạnh thang máy."),
    ("Each resident will receive an email", False,
     "Bài nói rõ sẽ KHÔNG liên hệ từng người."),
    ("The contractor will knock on each door", False,
     "Không có chi tiết nào về việc gõ cửa."),
    ("An announcement will be made in the lobby", False,
     "Sảnh chỉ được nhắc tới liên quan tới thùng nước.")])]),

(PassageType.EMAIL, """From: Rosalind Achebe, Procurement
To: Devendra Kulkarni, Facilities
Date: 4 September
Subject: Replacement chairs — revised quantity

Devendra,

I have been through the figures you sent on Monday and I need to change the
order before it goes to the supplier on Friday.

You asked for 84 chairs, based on one per desk across the three floors. However,
the second floor is being converted to meeting rooms in November, which removes
22 desks. Ordering chairs for that floor now would mean storing them for two
months and then finding we no longer need most of them.

I propose we order 62 chairs now and revisit the meeting-room furniture as a
separate purchase once the layout is confirmed. That also lets us use the
meeting-room budget rather than the desk-replacement budget, which is nearly
exhausted.

Could you confirm by Thursday afternoon? If I do not hear from you I will place
the order for 62 and note that the balance is pending.

Rosalind""",
 [("Why does Ms Achebe want to reduce the order?", Q.RC_INFERENCE, "rc_inference", 0.50,
   "the second floor is being converted to meeting rooms in November, which removes 22 desks",
   [("Part of the building will no longer need desk chairs", True,
     "Tầng hai chuyển thành phòng họp nên mất 22 bàn làm việc."),
    ("The supplier has raised its prices since the original quotation", False,
     "Giá cả không được nhắc tới ở đâu trong email."),
    ("The chairs ordered were the wrong model", False,
     "Vấn đề là số lượng, không phải mẫu mã."),
    ("Fewer staff will be working in the building", False,
     "Bài không nói gì về số lượng nhân viên.")]),

  ("What does Ms Achebe say about the desk-replacement budget?", Q.RC_DETAIL,
   "rc_detail", 0.45, "the desk-replacement budget, which is nearly exhausted",
   [("It has almost been fully spent", True, "Bài dùng đúng chữ 'nearly exhausted'."),
    ("It has been transferred to Facilities", False,
     "Không có chuyện chuyển ngân sách sang bộ phận khác."),
    ("It will increase in November", False,
     "Tháng Mười một liên quan tới việc cải tạo, không phải ngân sách."),
    ("It cannot be used for meeting rooms", False,
     "Ngược lại — bà ấy đề xuất DÙNG ngân sách phòng họp cho phần đó.")]),

  ("What will happen if Mr Kulkarni does not reply?", Q.RC_DETAIL, "rc_detail", 0.40,
   "If I do not hear from you I will place the order for 62",
   [("The smaller order will be placed anyway", True,
     "Bài nói rõ sẽ đặt 62 chiếc nếu không nhận được phản hồi."),
    ("The order will be postponed until November", False,
     "Không có chuyện hoãn đơn hàng."),
    ("The original quantity of 84 will be ordered", False,
     "Ngược lại — 84 là con số bà ấy muốn thay đổi."),
    ("The supplier will contact him directly", False,
     "Nhà cung cấp không liên hệ với ai trong email này.")])]),

(PassageType.ARTICLE, """Harrow & Vance Reports Steady Growth in Regional Deliveries

Harrow & Vance, the logistics firm founded in Thorncastle in 2011, has reported
a fourteen percent rise in regional deliveries over the past year. The increase
comes almost entirely from contracts with independent grocers rather than from
the large retail chains that made up most of the firm's early business.

Managing director Beatriz Okonjo said the shift was deliberate. "Chains
negotiate hard on price and switch supplier for very small savings," she said.
"Independents stay with you for years if the service is reliable, and they are
far less sensitive to a few pence per parcel."

The strategy has required investment. The company has bought eleven smaller vans
to reach premises that its existing fleet could not serve, and has moved to
six-day operation in two of its four depots.

Analysts note that margins on independent contracts are thinner, and that the
approach depends on retaining a large number of small customers rather than a
handful of large ones. Ms Okonjo accepts this. "It is a slower way to grow," she
said, "but the revenue does not disappear overnight when one contract ends." """,
 [("What is the main reason for the company's growth?", Q.RC_MAIN_IDEA,
   "rc_main_idea", 0.45,
   "The increase comes almost entirely from contracts with independent grocers",
   [("New business with independent retailers", True,
     "Bài nói tăng trưởng đến gần như hoàn toàn từ hợp đồng với cửa hàng độc lập."),
    ("Larger contracts signed with the national retail chains", False,
     "Ngược lại — chuỗi lớn là mảng công ty đang giảm phụ thuộc."),
    ("The opening of two new depots", False,
     "Bài nói chuyển sang làm sáu ngày ở hai kho CÓ SẴN, không mở kho mới."),
    ("A reduction in delivery prices", False,
     "Giá được nhắc tới nhưng không phải nguyên nhân tăng trưởng.")]),

  ("What does Ms Okonjo suggest about chain retailers?", Q.RC_INTENT, "rc_intent", 0.55,
   "Chains negotiate hard on price and switch supplier for very small savings",
   [("They will change supplier to save even a small amount", True,
     "Đó chính là điều bà ấy nói trong câu trích dẫn."),
    ("They are willing to pay more per parcel than independent shops do", False,
     "Bài ngụ ý ngược lại — chuỗi ép giá mạnh."),
    ("They require deliveries six days a week", False,
     "Sáu ngày là quyết định của công ty, không phải yêu cầu của chuỗi."),
    ("They are located in areas the fleet cannot reach", False,
     "Đó là lý do mua xe nhỏ, liên quan tới cửa hàng độc lập.")]),

  ("What concern do analysts raise?", Q.RC_DETAIL, "rc_detail", 0.50,
   "margins on independent contracts are thinner",
   [("The profit on each contract is smaller", True,
     "Bài dùng đúng chữ 'margins ... are thinner'."),
    ("The company now depends on far too few individual customers", False,
     "Lo ngại là phải giữ RẤT NHIỀU khách nhỏ, không phải quá ít khách."),
    ("The new vans were too expensive", False,
     "Bài không bình luận gì về giá xe."),
    ("Independent grocers pay late", False,
     "Không có chi tiết nào về việc thanh toán chậm.")])]),

(PassageType.ADVERTISEMENT, """WHITMORE COURSE CENTRE — Autumn Short Courses

All courses run for four consecutive Tuesdays at our Fenchurch Street rooms.
Fees include materials but not refreshments.

  Bookkeeping for Small Businesses     18:30–20:30   £120
  Presenting with Confidence           18:00–20:00   £140
  Introduction to Data Analysis        18:30–21:00   £185
  Writing for the Workplace            18:00–19:30   £ 95

Booking opens on 1 September. Places are limited to twelve per course so that
every participant can receive individual feedback.

Employers booking three or more places on the same course receive a fifteen
percent reduction. This cannot be combined with the early-booking discount
available to individuals who register before 15 September.

Anyone who has completed a Whitmore course in the previous two years may attend
a second course at the reduced rate of £75, regardless of the advertised fee.

Each course is taught by a practitioner rather than a full-time trainer, and
tutors are asked to bring examples from their own working week rather than
textbook cases. Participants consistently tell us this is what makes the
sessions worth the evening.

Cancellations made more than seven days before the first session are refunded in
full. After that point we can transfer your place to a colleague or to the next
term, but we cannot offer a refund, as materials will already have been printed
and the tutor booked.""",
 [("What is included in the course fee?", Q.RC_DETAIL, "rc_detail", 0.35,
   "Fees include materials but not refreshments",
   [("Course materials", True, "Bài nói rõ phí bao gồm tài liệu."),
    ("Refreshments during the session", False, "Bài nói rõ KHÔNG bao gồm đồ ăn uống."),
    ("A certificate of completion", False, "Chứng chỉ không được nhắc tới."),
    ("Parking at Fenchurch Street", False, "Không có thông tin về chỗ đỗ xe.")]),

  ("Why are class sizes limited?", Q.RC_INFERENCE, "rc_inference", 0.45,
   "so that every participant can receive individual feedback",
   [("To allow the tutor to give personal feedback", True,
     "Bài nêu đúng lý do này."),
    ("Because the rooms at Fenchurch Street are small", False,
     "Kích thước phòng không được nhắc tới."),
    ("To keep the course fees low", False,
     "Lớp nhỏ thường làm phí cao hơn, và bài không nói vậy."),
    ("Because materials are in short supply", False,
     "Không có chi tiết nào về việc thiếu tài liệu.")]),

  ("A company books four places on 'Writing for the Workplace'. What discount applies?",
   Q.RC_CROSS_REFERENCE, "rc_paraphrase", 0.60,
   "Employers booking three or more places on the same course receive a fifteen",
   [("Fifteen percent off", True,
     "Bốn suất trên cùng một khoá thoả điều kiện 'ba suất trở lên' của doanh nghiệp."),
    ("The £75 returning-participant rate", False,
     "Mức £75 chỉ dành cho người đã học khoá Whitmore trong hai năm qua."),
    ("Both the employer and early-booking discounts", False,
     "Bài nói rõ hai ưu đãi này KHÔNG cộng dồn."),
    ("No discount, because the course is the cheapest", False,
     "Ưu đãi doanh nghiệp không phụ thuộc giá khoá học.")])]),
]

# ===========================================================================
# DOUBLE PASSAGE — ≥2 câu bắt buộc đọc chéo
# ===========================================================================

DOUBLE_PASSAGES = [
 (PassageType.EMAIL, """From: Callum Brightwater
To: Bookings, Aldergate Conference Rooms
Date: 11 January
Subject: Room hire, 6 February

Good morning,

I would like to book a room for a training day on Thursday 6 February. We expect
thirty-two participants and will need the room from 08:30 to 17:00.

We require a projector and a flipchart. Participants will bring their own
laptops, but we will need power sockets at the tables rather than around the
walls.

Could you also confirm whether catering can be arranged for a mid-morning break
and lunch? Four of our participants have advised us of dietary requirements.

Kind regards,
Callum Brightwater
Learning and Development, Pemberton Analytics"""),

 (PassageType.LETTER, """ALDERGATE CONFERENCE ROOMS — Room Availability, February

Room          Capacity   Layout options            Daily rate
Sandringham       40      Theatre, cabaret, U       £340
Ashworth          25      Boardroom only            £260
Thornbury         60      Theatre, cabaret          £480
Marlowe           18      Boardroom, U              £190

All rooms include a projector and screen. Flipcharts are supplied on request at
no charge. Floor sockets are fitted in the Sandringham and Thornbury rooms only;
other rooms have wall sockets.

Catering must be confirmed at least ten working days before the booking date.
Dietary requirements can be accommodated with the same notice.

Rooms are available from 08:00 and must be vacated by 18:00 unless an evening
extension has been agreed in advance. The daily rate covers the full period; we
do not charge by the hour.

Bookings are held for five working days pending written confirmation. After that
the room is released without further notice, so please reply promptly if the
date matters to you."""),
]

DOUBLE_QUESTIONS = [
 ("Which room should Mr Brightwater be offered?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.65, 2, "Floor sockets are fitted in the Sandringham and Thornbury rooms only",
  [("Sandringham", True,
    "Cần chỗ cho 32 người và ổ điện ở sàn. Sandringham chứa 40 người và có ổ sàn; "
    "Thornbury cũng có ổ sàn nhưng đắt hơn nhiều mà không cần thiết."),
   ("Ashworth", False, "Chỉ chứa 25 người, ít hơn 32 người dự kiến."),
   ("Marlowe", False, "Chỉ chứa 18 người và chỉ có ổ điện trên tường."),
   ("Any room, since all include a projector", False,
    "Máy chiếu có ở mọi phòng, nhưng sức chứa và ổ điện sàn mới là điều kiện quyết định.")]),

 ("Is the catering request likely to be accepted?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.70, 2, "Catering must be confirmed at least ten working days before the booking date",
  [("Yes, because the request was made well before the deadline", True,
    "Email gửi ngày 11 tháng Một cho sự kiện ngày 6 tháng Hai — hơn mười ngày làm việc."),
   ("No, because special dietary requirements can never be accommodated at all", False,
    "Bảng nói rõ yêu cầu ăn kiêng ĐƯỢC đáp ứng nếu báo đúng hạn."),
   ("No, because catering is not offered in February", False,
    "Không có thông tin nào nói tháng Hai không phục vụ ăn uống."),
   ("Yes, but only for the mid-morning break", False,
    "Không có giới hạn nào chỉ cho phép một bữa.")]),

 ("What does Mr Brightwater specifically ask for that is NOT standard?",
  Q.RC_NOT_TRUE, "rc_not_true", 0.55, 1, "we will need power sockets at the tables rather than around the walls",
  [("Power sockets at the tables", True,
    "Ổ điện ở sàn chỉ có ở hai phòng, còn máy chiếu và bảng lật thì phòng nào cũng có."),
   ("A projector", False, "Bảng giá nói mọi phòng đều có máy chiếu."),
   ("A flipchart", False, "Bảng lật được cung cấp miễn phí theo yêu cầu."),
   ("A room for thirty-two people", False,
    "Sức chứa là điều kiện chọn phòng, không phải yêu cầu ngoài tiêu chuẩn.")]),

 ("How long does Mr Brightwater need the room?", Q.RC_DETAIL, "rc_detail", 0.35, 1,
  "will need the room from 08:30 to 17:00",
  [("Eight and a half hours", True, "Từ 08:30 đến 17:00 là tám tiếng rưỡi."),
   ("Six hours, finishing in the early afternoon", False, "Ngắn hơn khoảng thời gian nêu trong email."),
   ("A full two days", False, "Email chỉ nói một ngày, thứ Năm 6 tháng Hai."),
   ("Only the morning", False, "Email yêu cầu tới 17:00.")]),
]


def build_group(part_passages: list[tuple], rows: list[tuple], start_idx: int
                ) -> tuple[ExamGroup, int]:
    passages = [Passage(order=i + 1, passage_type=pt, text=txt)
                for i, (pt, txt) in enumerate(part_passages)]
    idx = start_idx
    questions = []
    for row in rows:
        if len(row) == 7:                      # multi-passage: có passage_order
            stem, qtype, concept, diff, p_order, quote, opts = row
        else:                                  # single passage
            stem, qtype, concept, diff, quote, opts = row
            p_order = 1
        span = find_span(passages[p_order - 1].text, quote, p_order)
        placed = place_options(idx, stem, opts)
        idx += 1
        questions.append(ExamItem(
            part_number=7, question_text=stem, question_type=qtype,
            options=[Option(label=LABELS[i], text=t, is_correct=c, rationale_vi=r)
                     for i, (t, c, r) in enumerate(placed)],
            concept_ids=[concept], difficulty_prior=diff,
            evidence_span=span,
            explanation=Definition(
                en=f'The answer is supported by: "{quote}"',
                vi=next(r for _, c, r in opts if c))))
    return ExamGroup(part_number=7, passages=passages, questions=questions), idx


def main() -> int:
    groups: list[ExamGroup] = []
    idx = 0

    for ptype, text, rows in SINGLE:
        g, idx = build_group([(ptype, text)], rows, idx)
        groups.append(g)

    g, idx = build_group(DOUBLE_PASSAGES, DOUBLE_QUESTIONS, idx)
    groups.append(g)

    n_q = sum(len(g.questions) for g in groups)
    n_single = sum(1 for g in groups if len(g.passages) == 1)
    n_multi = len(groups) - n_single
    print(f"Part 7: {len(groups)} group ({n_single} single, {n_multi} multi), {n_q} câu")
    print(f"  evidence_span tính bằng string-match: "
          f"{sum(1 for g in groups for q in g.questions if q.evidence_span)}/{n_q}")
    for g in groups:
        if len(g.passages) > 1:
            n_cross = sum(1 for q in g.questions
                          if q.question_type is Q.RC_CROSS_REFERENCE)
            print(f"  group {len(g.passages)} passage: {n_cross} câu đọc chéo (cần ≥2)")
    for g in groups:
        for p in g.passages:
            w = len(p.text.split())
            lo, hi = (150, 260) if len(g.passages) == 1 else (100, 200)
            if not (lo <= w <= hi):
                print(f"  ⚠ passage {w} từ, ngoài khoảng {lo}–{hi}")
    print()
    for w in report_bias(groups):
        print(f"  ⚠ {w}")
    print()

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_reading_part7_001", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
