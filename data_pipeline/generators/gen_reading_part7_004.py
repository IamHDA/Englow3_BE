#!/usr/bin/env python3
"""Part 7 batch 004 — 4 single + 1 triple, khép lại Part 7 ở 54 câu.

    python generators/gen_reading_part7_004.py
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
OUT = ROOT / "output" / "exams" / "bank" / "reading" / "exam_reading_part7_004.json"
GENERATED_BY = "claude-opus-5"
Q = QuestionType

SINGLE = [

(PassageType.ARTICLE, """Warehouse Robots Have Not Emptied the Warehouse

When Draycote Logistics installed forty mobile shelving robots at its Wrexham
site two years ago, the local press predicted job losses. The opposite has
happened. The site now employs 312 people, up from 280.

The explanation is less surprising than it first appears. The robots carry
shelving units to human pickers rather than picking items themselves, because
gripping an unpredictable mix of objects remains difficult and expensive to
automate. What changed was the volume the site could handle: orders processed
per shift rose by roughly half, and the company moved work to Wrexham from two
smaller depots.

Those depots closed. Draycote offered relocation to all affected staff, and
about a third accepted. The remainder took redundancy or found work locally.

Managers are candid that the picture would look different had the depots been
further away. "We got lucky with geography," the operations director admits.
"If those sites had been two hundred miles off, this would be a very different
article." Industry analysts caution against reading the Wrexham figures as a
general rule.""",
 [("What is the main point of the article?", Q.RC_MAIN_IDEA, "rc_main_idea", 0.50,
   "The opposite has happened",
   [("Automation at one site increased rather than reduced employment", True,
     "Bài xoay quanh việc số nhân sự tăng từ 280 lên 312 sau khi lắp robot."),
    ("Robots are now able to pick items without human help", False,
     "Bài nói ngược lại: cầm nắm vật thể vẫn khó tự động hoá."),
    ("Draycote Logistics is planning to close its Wrexham site", False,
     "Wrexham là nơi được mở rộng, hai kho nhỏ khác mới đóng."),
    ("Local newspapers accurately predicted the effect the robots would have on staffing", False,
     "Báo địa phương dự đoán mất việc, và điều đó đã không xảy ra.")]),

  ("Why do the robots not pick items themselves?", Q.RC_DETAIL, "rc_detail", 0.45,
   "gripping an unpredictable mix of objects remains difficult and expensive to automate",
   [("Handling varied objects is still hard to automate", True,
     "Bài nêu rõ lý do kỹ thuật và chi phí."),
    ("The company wanted to protect the jobs of its existing pickers", False,
     "Bài không nêu đây là lý do thiết kế."),
    ("Local regulations require human pickers", False,
     "Không có chi tiết nào về quy định."),
    ("The shelving units are too heavy for robots", False,
     "Robot chính là thứ chở kệ hàng đi.")]),

  ("What does the operations director suggest?", Q.RC_INFERENCE, "rc_inference",
   0.65, "We got lucky with geography",
   [("The outcome depended partly on circumstances beyond the company's control",
     True, "Câu \"got lucky with geography\" hàm ý kết quả tốt nhờ vị trí gần, "
     "không hoàn toàn do quyết định của công ty."),
    ("The company should have installed the robots several years earlier than it did", False,
     "Không có chi tiết nào về thời điểm."),
    ("Other companies will certainly see the same results", False,
     "Ông nói ngược lại, và các nhà phân tích cũng cảnh báo."),
    ("The two closed depots will eventually be reopened", False,
     "Bài không nhắc tới khả năng mở lại.")])]),

(PassageType.NOTICE, """NOTICE TO RESIDENTS — Ashgrove Court

Lift Replacement Works, 6 October – 19 December

The passenger lift serving floors 1 to 9 will be taken out of service for full
replacement. The existing lift is 34 years old and parts are no longer
manufactured; three breakdowns since April have each taken over a week to
resolve.

During the works the goods lift at the rear of the building will be available to
all residents between 07:00 and 22:00. Outside those hours the stairs must be
used, as the goods lift shares a power supply with the fire alarm panel and
cannot be left running unattended overnight.

Residents who cannot manage stairs and who have registered with the building
office will be offered assistance with deliveries and refuse. Please register by
29 September; we cannot arrange cover at short notice.

We recognise that eleven weeks is a long time. The contractor has been asked to
work Saturdays, which is reflected in the end date above; without Saturday
working the programme would run to late January.

Building Office, Ashgrove Court""",
 [("Why is the lift being replaced rather than repaired?", Q.RC_DETAIL,
   "rc_detail", 0.40, "parts are no longer manufactured",
   [("Replacement parts are no longer made", True,
     "Bài nêu thang máy 34 tuổi và phụ tùng đã ngừng sản xuất."),
    ("Residents complained about the noise it made", False,
     "Không có chi tiết nào về tiếng ồn."),
    ("It does not reach the upper floors of the building", False,
     "Thang phục vụ tầng 1 đến 9, không nói thiếu tầng."),
    ("A new safety regulation came into force in April", False,
     "Tháng Tư được nhắc tới vì các lần hỏng, không phải quy định.")]),

  ("What must residents do after ten at night?", Q.RC_DETAIL, "rc_detail", 0.35,
   "Outside those hours the stairs must be used",
   [("Use the stairs", True, "Thang chở hàng chỉ chạy 07:00–22:00."),
    ("Use the goods lift as normal", False, "Thang chở hàng dừng lúc 22:00."),
    ("Contact the building office for access", False,
     "Văn phòng chỉ hỗ trợ cư dân đã đăng ký, và về giao hàng/rác."),
    ("Wait until the fire alarm panel is switched off", False,
     "Bảng báo cháy dùng chung nguồn nên KHÔNG được tắt.")]),

  ("What does the notice imply about the end date?", Q.RC_INFERENCE,
   "rc_inference", 0.60, "without Saturday working the programme would run to late January",
   [("It is earlier than it would otherwise have been", True,
     "19 tháng Mười Hai có được là nhờ làm thêm thứ Bảy; không thì tới cuối tháng Một."),
    ("It is likely to be delayed until late January", False,
     "Cuối tháng Một là kịch bản ĐÃ tránh được, không phải dự báo."),
    ("It was set by the contractor without consultation", False,
     "Bài nói nhà thầu \"đã được yêu cầu\" làm thứ Bảy."),
    ("It depends on how many residents register for help", False,
     "Việc đăng ký liên quan tới hỗ trợ cá nhân, không tới tiến độ.")])]),

(PassageType.EMAIL, """From: Adaeze Nwachukwu, Head of Customer Operations
To: All contact-centre team leaders
Subject: Average handling time — change of approach

Team leaders,

From next month we will stop reporting average handling time as a team target.

I want to be clear about why, because some of you have built your coaching
around it. AHT measures how long a call lasts. It does not measure whether the
customer's problem was solved. Our own data shows the two pulling apart: the
three teams with the shortest calls last quarter also had the highest rate of
customers calling back within seven days.

We will still collect AHT, and I will still look at it — a call averaging
eighteen minutes when the floor average is six tells me something. But it will
not appear on the team scorecard, and no one's review will reference it.

Replacing it are two measures: first-contact resolution, and a short customer
question asked after the call closes.

I am aware this will feel like moving the goalposts, particularly for teams who
have worked hard to bring their times down. Please come to Thursday's session
with your questions. I would rather have an uncomfortable hour then than six
months of quiet confusion.

Adaeze""",
 [("What is the purpose of the email?", Q.RC_MAIN_IDEA, "rc_main_idea", 0.40,
   "we will stop reporting average handling time as a team target",
   [("To announce that a performance measure is being dropped", True,
     "Toàn bộ email xoay quanh việc bỏ AHT khỏi bảng chỉ tiêu."),
    ("To criticise teams whose calls take too long", False,
     "Ngược lại — các đội gọi NGẮN nhất mới là đội có vấn đề."),
    ("To introduce a new customer complaints procedure", False,
     "Không có chi tiết nào về quy trình khiếu nại."),
    ("To announce that the contact centre will be reorganised", False,
     "Email chỉ nói về chỉ số, không nói về cơ cấu tổ chức.")]),

  ("What did the data show about teams with the shortest calls?", Q.RC_DETAIL,
   "rc_detail", 0.55, "also had the highest rate of customers calling back within seven days",
   [("More of their customers rang back within a week", True,
     "Đây chính là bằng chứng bà Nwachukwu đưa ra."),
    ("They resolved the most problems at first contact", False,
     "Dữ liệu cho thấy điều ngược lại."),
    ("They received the highest customer scores", False,
     "Câu hỏi khách hàng là chỉ số MỚI, chưa có kết quả."),
    ("They had the fewest team leaders per person", False,
     "Không có thông tin nào về tỉ lệ quản lý.")]),

  ("What will happen to AHT figures?", Q.RC_DETAIL, "rc_detail", 0.60,
   "We will still collect AHT, and I will still look at it",
   [("They will be collected but kept off the scorecard", True,
     "Bà nói vẫn thu thập và vẫn xem, nhưng không đưa lên bảng chỉ tiêu."),
    ("They will no longer be collected at all", False,
     "Email nói rõ vẫn tiếp tục thu thập."),
    ("They will be reported directly to each customer", False,
     "Không có chi tiết nào như vậy."),
    ("They will replace first-contact resolution as a target", False,
     "First-contact resolution mới là chỉ số THAY THẾ cho AHT.")])]),

(PassageType.ARTICLE, """Why the Bus Lane Camera Reduced Journey Times More Than the Bus Lane

Kettlewell Borough painted a bus lane along Marsden Road in 2019. Journey times
for the number 14 service improved by ninety seconds. In 2023 the council
installed an enforcement camera on the same stretch. Journey times improved by a
further four and a half minutes.

The lane had always been there; what was missing was any consequence for
ignoring it. Council surveys before the camera found that on a weekday morning
around one vehicle in six using the lane had no right to be there — mostly cars
avoiding a queue at the Halliwell junction. A single such vehicle, travelling at
the speed of the general traffic beside it, removes the advantage of the lane
for every bus behind it.

The finding is not unique to Kettlewell, though the size of the effect there is
at the upper end of what has been reported. Councils considering similar schemes
should note that the camera generated £411,000 in its first year and £96,000 in
its third — a falling income that the council describes, correctly, as the
scheme working.""",
 [("What does the article suggest about the 2019 bus lane?", Q.RC_INFERENCE,
   "rc_inference", 0.65, "what was missing was any consequence for ignoring it",
   [("It was not effective on its own because it was not enforced", True,
     "Làn xe buýt chỉ tiết kiệm 90 giây cho tới khi có camera phạt."),
    ("It was painted along the wrong section of road", False,
     "Camera lắp đúng đoạn đó và có hiệu quả."),
    ("It was removed before the camera was installed", False,
     "Bài nói làn đường \"vẫn luôn ở đó\"."),
    ("It reduced journey times by more than the enforcement camera later did", False,
     "90 giây so với bốn phút rưỡi — camera hiệu quả hơn nhiều.")]),

  ("Why does the council regard the falling income as a good sign?", Q.RC_INFERENCE,
   "rc_inference", 0.70, "a falling income that the council describes, correctly, as the scheme working",
   [("Fewer drivers are now entering the lane illegally", True,
     "Tiền phạt giảm nghĩa là ít xe vi phạm hơn — đúng mục tiêu của camera."),
    ("The cost of running the camera has decreased", False,
     "Bài nói về doanh thu, không phải chi phí vận hành."),
    ("The council has reduced the size of the fine issued to each driver", False,
     "Không có chi tiết nào về mức phạt."),
    ("Bus passenger numbers have risen since 2023", False,
     "Bài không đưa số liệu hành khách.")]),

  ("What did surveys find before the camera was installed?", Q.RC_DETAIL,
   "rc_detail", 0.50, "around one vehicle in six using the lane had no right to be there",
   [("About one in six vehicles in the lane was not permitted", True,
     "Đây là con số khảo sát buổi sáng ngày thường."),
    ("About one in six buses was running late", False,
     "Tỉ lệ này nói về xe vi phạm, không phải xe buýt trễ."),
    ("Most drivers were unaware that the bus lane existed at all", False,
     "Bài nói họ tránh ùn tắc — tức là biết rõ."),
    ("Journey times were worst at the weekend", False,
     "Khảo sát tiến hành vào buổi sáng ngày thường.")])]),
]

# --- TRIPLE ---------------------------------------------------------------
TRIPLE_PASSAGES = [
 (PassageType.ADVERTISEMENT, """HOLBECK MILL STUDIOS — Workspace to Let

Converted textile mill, five minutes from Holbeck station.

  Studio A   18 m²   £310 per month   ground floor, no window
  Studio B   24 m²   £425 per month   first floor, north light
  Studio C   24 m²   £470 per month   first floor, north light, sink
  Studio D   41 m²   £680 per month   second floor, goods hoist access

All rents include heating, wi-fi and 24-hour access. Electricity is metered
separately per studio. Minimum term twelve months.

The building has no passenger lift. Studios on the first and second floors are
reached by stairs; the goods hoist serves the second floor only and is for
materials, not people.

Viewings on Wednesdays. Contact Marguerite Oyelaran, 0113 496 0055."""),

 (PassageType.EMAIL, """From: Petra Halvorsen
To: Marguerite Oyelaran
Date: 14 February
Subject: Studio enquiry

Dear Ms Oyelaran,

I make large ceramic pieces and am looking for a studio from April. I saw your
advertisement and have two questions.

I need running water in the studio itself — carrying water along a corridor is
not practical with wet clay. I also need to get finished pieces out of the
building; the largest are about 90 cm tall and too heavy for me to carry down
stairs.

I would prefer to keep the rent under £500 a month. Could you advise which
studio would suit?

Best wishes,
Petra Halvorsen"""),

 (PassageType.EMAIL, """From: Marguerite Oyelaran
To: Petra Halvorsen
Date: 15 February
Subject: RE: Studio enquiry

Dear Ms Halvorsen,

Thank you for your enquiry. Your two requirements unfortunately point to
different studios, so I want to set out the position honestly rather than book
you a viewing that wastes your morning.

Only one studio has a sink, and only one has hoist access, and they are not the
same room. The hoist studio has no plumbing at all and installing it is not
possible — the second floor sits above the archive and the landlord will not
permit water there.

There is one option I can offer. The first-floor studio with the sink is next to
a former loading door, now sealed. The landlord has agreed in principle to
reinstate it with a small hoist, at your cost, estimated at £2,400. I raise it
only because you may find that cheaper over a twelve-month term than the
alternative of moving pieces by hand.

Do come on Wednesday 21 February in any case.

Marguerite"""),
]

TRIPLE_QUESTIONS = [
 ("Which studio meets Ms Halvorsen's water requirement?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.60, 1, "Studio C   24 m²   £470 per month   first floor, north light, sink",
  [("Studio C", True,
    "Chỉ Studio C có bồn rửa, và £470 vẫn dưới mức £500 bà đặt ra."),
   ("Studio A, as it is on the ground floor", False,
    "Studio A không có bồn rửa, và tầng trệt không phải yêu cầu của bà."),
   ("Studio D, because it has goods hoist access", False,
    "Studio D đáp ứng yêu cầu vận chuyển chứ không phải nước, và giá vượt £500."),
   ("Studio B, since it is the same size as Studio C", False,
    "Cùng diện tích nhưng Studio B không có bồn rửa.")]),

 ("Why can plumbing not be installed in the hoist studio?", Q.RC_CROSS_REFERENCE,
  "rc_cross_reference", 0.65, 3, "the second floor sits above the archive and the landlord will not permit water there",
  [("The landlord will not allow water above the archive", True,
    "Studio D ở tầng hai, ngay trên kho lưu trữ."),
   ("The studio is already let to another tenant", False,
    "Không có chi tiết nào cho thấy Studio D đã có người thuê."),
   ("The pipework would obstruct the goods hoist on the second floor", False,
    "Lý do là kho lưu trữ ở dưới, không phải vướng tời hàng."),
   ("The building has no water supply above ground level", False,
    "Studio C ở tầng một và có bồn rửa, nên toà nhà có nước ở tầng trên.")]),

 ("What does Ms Oyelaran propose?", Q.RC_CROSS_REFERENCE, "rc_cross_reference",
  0.65, 3, "reinstate it with a small hoist, at your cost, estimated at £2,400",
  [("Reopening a sealed door beside Studio C and fitting a hoist", True,
    "Đây là phương án bà nêu, chi phí do người thuê chịu, khoảng £2,400."),
   ("Moving Ms Halvorsen to the ground-floor studio instead", False,
    "Studio A không có bồn rửa nên không giải quyết được yêu cầu chính."),
   ("Reducing the rent on Studio D to under £500 a month", False,
    "Bà không đề nghị giảm giá bất kỳ studio nào."),
   ("Installing a sink in the second-floor studio at her own cost", False,
    "Bà nói rõ lắp nước ở tầng hai là KHÔNG thể.")]),

 ("What does Ms Oyelaran's email suggest about her approach?", Q.RC_INFERENCE,
  "rc_inference", 0.70, 3, "rather than book you a viewing that wastes your morning",
  [("She prefers to state the difficulty before arranging a visit", True,
    "Bà nói thẳng hai yêu cầu không trùng vào một phòng, để không lãng phí "
    "buổi sáng của khách."),
   ("She is trying to persuade Ms Halvorsen to rent Studio D", False,
    "Bà chỉ ra Studio D không có nước, tức là không phù hợp."),
   ("She does not expect Ms Halvorsen to become a tenant", False,
    "Bà vẫn mời tới xem phòng vào thứ Tư."),
   ("She has already reopened the loading door for another tenant", False,
    "Cửa vẫn đang bịt kín và mới chỉ được đồng ý về nguyên tắc.")]),

 ("How much would Ms Halvorsen pay in her first year under the proposal?",
  Q.RC_CROSS_REFERENCE, "rc_cross_reference", 0.75, 1,
  "Studio C   24 m²   £470 per month   first floor, north light, sink",
  [("£5,640 in rent plus about £2,400 for the hoist", True,
    "Studio C là £470/tháng, £470 × 12 = £5,640, cộng khoảng £2,400 chi phí tời "
    "hàng. Điện tính riêng nên đây là mức tối thiểu."),
   ("£5,640 in rent, with the cost of the hoist met by the landlord", False,
    "Tiền thuê đúng, nhưng thư ghi rõ tời hàng \"at your cost\" — người thuê trả."),
   ("£470 in rent altogether plus about £2,400 for the hoist", False,
    "£470 là tiền thuê MỘT tháng chứ không phải cả kỳ hạn mười hai tháng."),
   ("£8,160 in rent, which already includes the cost of the hoist", False,
    "£8,160 tương ứng £680/tháng, tức Studio D — phòng không có bồn rửa.")]),
]


def main() -> int:
    groups = []
    idx = 300

    for ptype, text, rows in SINGLE:
        g, idx = build_group([(ptype, text)], rows, idx)
        groups.append(g)

    g, idx = build_group(TRIPLE_PASSAGES, TRIPLE_QUESTIONS, idx)
    groups.append(g)

    n_q = sum(len(g.questions) for g in groups)
    print(f"Part 7 batch 004: {len(groups)} group, {n_q} câu")
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
            batch_id="exam_reading_part7_004", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
