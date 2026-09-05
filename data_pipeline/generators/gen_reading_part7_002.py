#!/usr/bin/env python3
"""Part 7 batch 002 — thêm single, double và TRIPLE passage.

Triple passage là dạng khó nhất của Part 7: ba văn bản liên kết, và ≥2 câu bắt
buộc đọc chéo qua nhiều hơn một văn bản mới trả lời được.

    python generators/gen_reading_part7_002.py
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
OUT = ROOT / "output" / "exams" / "bank" / "reading" / "exam_reading_part7_002.json"
GENERATED_BY = "claude-opus-5"
Q = QuestionType

# ===========================================================================
# SINGLE
# ===========================================================================
SINGLE = [

(PassageType.MEMO, """MEMORANDUM

To:      All warehouse staff, Fennimore Distribution
From:    Anneke Vorster, Site Manager
Date:    22 October
Subject: Changes to the shift handover

From Monday 4 November the shift handover will move from the loading bay to the
first-floor briefing room. The change follows two incidents last month in which
handover notes were misheard over the noise of the conveyor.

The handover will still begin at 06:00, 14:00 and 22:00, but outgoing staff
should now finish their final pallet check five minutes early so that everyone
arrives on time. Team leaders will bring the printed shift log rather than
relying on the whiteboard, which will be removed.

I appreciate that the briefing room is a longer walk from the north aisle. If
this causes a consistent delay for your team, tell me by 29 October and I will
look at staggering the north aisle handover by ten minutes.

Nothing else about the handover procedure changes. The same three checks —
outstanding orders, equipment faults, and staff absences — must still be
recorded and signed by both team leaders.""",
 [("Why is the handover being moved?", Q.RC_INFERENCE, "rc_inference", 0.45,
   "handover notes were misheard over the noise of the conveyor",
   [("The loading bay is too noisy for staff to hear each other", True,
     "Bài nêu hai sự cố do nghe nhầm vì tiếng băng chuyền."),
    ("The loading bay is now needed for additional pallet storage space", False,
     "Không có chi tiết nào về việc dùng khu bốc dỡ để chứa hàng."),
    ("The briefing room is closer to the north aisle", False,
     "Ngược lại — bài nói phòng họp XA hơn lối bắc."),
    ("Team leaders asked for a printed shift log", False,
     "Bản in là hệ quả của thay đổi, không phải nguyên nhân.")]),

  ("What should outgoing staff do differently?", Q.RC_DETAIL, "rc_detail", 0.40,
   "should now finish their final pallet check five minutes early",
   [("Complete their last check slightly sooner", True,
     "Bài nói rõ kết thúc kiểm pallet cuối sớm hơn năm phút."),
    ("Start their shift five minutes later", False,
     "Giờ giao ca không đổi — vẫn 06:00, 14:00 và 22:00."),
    ("Record the three checks on the whiteboard", False,
     "Bảng trắng sẽ bị tháo bỏ."),
    ("Walk to the loading bay before the handover", False,
     "Khu bốc dỡ chính là nơi họ đang RỜI ĐI.")]),

  ("What has NOT changed?", Q.RC_NOT_TRUE, "rc_not_true", 0.45,
   "The same three checks — outstanding orders, equipment faults, and staff absences",
   [("The three items that must be recorded", True,
     "Đoạn cuối nói rõ ba hạng mục kiểm tra giữ nguyên."),
    ("The location of the handover", False, "Chuyển từ khu bốc dỡ lên phòng họp."),
    ("The use of the whiteboard", False, "Bảng trắng sẽ bị tháo bỏ."),
    ("The time staff finish their pallet check", False,
     "Phải xong sớm hơn năm phút.")])]),

(PassageType.WEB_PAGE, """Larkspur Serviced Offices — Frequently Asked Questions

Can I visit before signing?
Yes. Viewings run on weekday afternoons and take about twenty minutes. We ask
that you book at least one working day ahead so that a member of the team is
free to show you the floor.

What is included in the monthly fee?
Desk space, chairs, high-speed internet, cleaning, and use of the shared kitchen
and two meeting rooms. Printing is charged separately at four pence per page.

How much notice do I need to give?
One calendar month for desks of up to four people, and three months for larger
suites. Notice must be given in writing; we cannot accept notice by telephone.

Can I change the number of desks mid-contract?
You may add desks at any time, subject to availability, and the additional
charge begins on the first day of the following month. Reducing the number of
desks is treated as partial notice and follows the periods set out above.""",
 [("What must someone do before viewing an office?", Q.RC_DETAIL, "rc_detail", 0.35,
   "book at least one working day ahead",
   [("Arrange the visit a day or more in advance", True,
     "Bài yêu cầu đặt lịch trước ít nhất một ngày làm việc."),
    ("Pay a deposit for the meeting rooms", False,
     "Không có chi tiết nào về đặt cọc."),
    ("Give written notice of their intention to take a desk", False,
     "Thông báo bằng văn bản áp dụng cho việc chấm dứt hợp đồng."),
    ("Confirm how many desks they need", False,
     "Số bàn liên quan tới hợp đồng, không phải tới việc đi xem.")]),

  ("What is charged in addition to the monthly fee?", Q.RC_DETAIL, "rc_detail", 0.40,
   "Printing is charged separately at four pence per page",
   [("Printing", True, "Bài nói in ấn tính riêng bốn xu mỗi trang."),
    ("Cleaning", False, "Dọn dẹp nằm trong phí tháng."),
    ("Use of the meeting rooms", False, "Hai phòng họp đã bao gồm."),
    ("Internet access", False, "Internet tốc độ cao đã bao gồm.")]),

  ("A company with six desks wants to give up two. How much notice is required?",
   Q.RC_CROSS_REFERENCE, "rc_paraphrase", 0.60,
   "Reducing the number of desks is treated as partial notice",
   [("Three months, because the suite holds more than four people", True,
     "Giảm bàn tính là thông báo một phần, mà khu sáu bàn thuộc nhóm 'lớn hơn' "
     "nên áp dụng ba tháng."),
    ("One calendar month", False,
     "Một tháng chỉ áp dụng cho khu tối đa bốn người."),
    ("No notice at all, because the number of desks can be changed at any time", False,
     "'Bất cứ lúc nào' chỉ áp dụng khi THÊM bàn, không phải bớt."),
    ("One working day, as for a viewing", False,
     "Một ngày làm việc là quy định đặt lịch tham quan.")])]),
]

# ===========================================================================
# TRIPLE PASSAGE — ba văn bản, ≥2 câu đọc chéo
# ===========================================================================
TRIPLE_PASSAGES = [
 (PassageType.ADVERTISEMENT, """VENDOR APPLICATIONS OPEN — Ravensmere Winter Market
Saturday 7 and Sunday 8 December, Ravensmere Town Square

Stall types and fees (for the full weekend):
  Standard stall, 2m         £ 90
  Corner stall, 2m           £115
  Hot food stall, 3m         £180   (includes power connection)
  Craft table, 1m            £ 55

Applications close on 1 November. Hot food vendors must supply a current food
hygiene certificate with their application; applications without one will not be
considered.

Successful applicants will be notified by 8 November and must pay in full within
ten days of notification. Stalls not paid for by the deadline will be reallocated
to the waiting list."""),

 (PassageType.EMAIL, """From: Yusuf Oyinlola, Ravensmere Market Office
To: Delphine Marchetti
Date: 6 November
Subject: Your application — craft table

Dear Ms Marchetti,

Thank you for applying for a craft table at the Winter Market. I am pleased to
confirm your application has been successful.

You asked whether you could upgrade to a larger space. A standard 2m stall has
become available following a withdrawal, and I can transfer your booking to it
if you let me know by 14 November. You would pay the difference between the two
fees rather than the full amount again.

Please note that payment for whichever space you choose is due within the period
set out in our advertisement.

Kind regards,
Yusuf Oyinlola"""),

 (PassageType.EMAIL, """From: Delphine Marchetti
To: Yusuf Oyinlola
Date: 12 November
Subject: RE: Your application — craft table

Dear Mr Oyinlola,

Thank you for the offer. Yes, please transfer my booking to the standard stall —
I will be bringing more stock than I first planned.

I will make the payment this week. Could you confirm the exact amount I owe, as
I have already paid nothing so far?

One further question: my neighbour sells hot soup and asked me whether she could
still apply. I told her I thought applications had closed, but I said I would
check.

Best wishes,
Delphine Marchetti"""),
]

TRIPLE_QUESTIONS = [
 ("How much will Ms Marchetti pay in total?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.70, 1, "Standard stall, 2m         £ 90",
  [("£90", True,
    "Email nói chuyển sang quầy tiêu chuẩn và trả phần chênh lệch. Vì bà chưa "
    "trả gì (£55 chưa nộp), tổng phải trả chính là giá quầy tiêu chuẩn £90."),
   ("£35", False, "£35 là chênh lệch, nhưng bà chưa trả £55 ban đầu."),
   ("£145", False, "Cộng dồn cả hai mức phí — email nói rõ không phải trả lại toàn bộ."),
   ("£55", False, "Đó là phí bàn thủ công mà bà không còn dùng.")]),

 ("What is the deadline for Ms Marchetti's payment?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.75, 1,
  "must pay in full within ten days of notification",
  [("16 November", True,
    "Email thông báo kết quả ngày 6 tháng Mười một; mười ngày sau là 16 tháng Mười một."),
   ("14 November", False, "Đó là hạn trả lời việc đổi quầy, không phải hạn thanh toán."),
   ("1 November", False, "Đó là hạn nộp hồ sơ."),
   ("22 November, more than two weeks after notification", False,
    "Không mốc nào trong ba văn bản dẫn tới ngày này.")]),

 ("What should Ms Marchetti tell her neighbour?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.70, 1, "Applications close on 1 November",
  [("Applications closed on 1 November, so it is too late", True,
    "Quảng cáo ghi hạn 1 tháng Mười một; thư của bà đề ngày 12 tháng Mười một."),
   ("She may apply if she pays within ten days", False,
    "Quy định mười ngày là hạn thanh toán cho người đã trúng tuyển."),
   ("She may still apply provided that she holds a current hygiene certificate", False,
    "Giấy chứng nhận là điều kiện bắt buộc, nhưng hạn nộp hồ sơ đã qua."),
   ("She should contact the market office by 14 November", False,
    "14 tháng Mười một là hạn riêng của bà Marchetti về việc đổi quầy.")]),

 ("What did Mr Oyinlola offer Ms Marchetti?", Q.RC_DETAIL, "rc_detail", 0.45, 2,
  "A standard 2m stall has become available following a withdrawal",
  [("A larger space left free by another vendor", True,
    "Một quầy tiêu chuẩn 2m trống ra vì có người rút."),
   ("A discount on the craft table fee", False, "Không có giảm giá nào được nhắc tới."),
   ("A corner stall at the standard rate", False,
    "Quầy góc không được đề cập trong email."),
   ("An extension to the deadline for paying the stall fee", False,
    "Ông ấy nhắc lại hạn thanh toán chứ không gia hạn.")]),

 ("What does Ms Marchetti give as her reason for upgrading?", Q.RC_DETAIL,
  "rc_detail", 0.40, 3, "I will be bringing more stock than I first planned",
  [("She will have more goods to display than expected", True,
    "Bà nói sẽ mang nhiều hàng hơn dự kiến ban đầu."),
   ("Her neighbour will share the stall with her", False,
    "Người hàng xóm là một người nộp hồ sơ riêng, không dùng chung quầy."),
   ("The craft table she booked was too far from the square's entrance", False,
    "Vị trí không được nhắc tới trong bất kỳ văn bản nào."),
   ("She needs a power connection for hot food", False,
    "Điện chỉ đi kèm quầy đồ ăn nóng, không phải nhu cầu của bà.")]),
]


def main() -> int:
    groups = []
    idx = 100                      # lệch khỏi batch 001 để xoay vòng không trùng nhịp

    for ptype, text, rows in SINGLE:
        g, idx = build_group([(ptype, text)], rows, idx)
        groups.append(g)

    g, idx = build_group(TRIPLE_PASSAGES, TRIPLE_QUESTIONS, idx)
    groups.append(g)

    n_q = sum(len(g.questions) for g in groups)
    print(f"Part 7 batch 002: {len(groups)} group, {n_q} câu")
    for g in groups:
        n_p = len(g.passages)
        if n_p > 1:
            n_cross = sum(1 for q in g.questions
                          if q.question_type is Q.RC_CROSS_REFERENCE)
            print(f"  group {n_p} passage: {n_cross} câu đọc chéo (cần ≥2)")
        for p in g.passages:
            w = len(p.text.split())
            lo, hi = (150, 260) if n_p == 1 else (100, 200)
            if not (lo <= w <= hi):
                print(f"  ⚠ passage {w} từ, ngoài {lo}–{hi}")
    print(f"  evidence_span: "
          f"{sum(1 for g in groups for q in g.questions if q.evidence_span)}/{n_q}")
    print()
    for w in report_bias(groups):
        print(f"  ⚠ {w}")
    print()

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_reading_part7_002", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
