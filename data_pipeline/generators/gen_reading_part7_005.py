#!/usr/bin/env python3
"""Part 7 batch 005 — hai dạng câu ngân hàng còn thiếu.

Đề thật có hai dạng mà ba batch trước chưa chạm tới:
  - từ vựng theo ngữ cảnh: "the word X ... is closest in meaning to"
  - chèn câu:              "In which of the positions marked [1]–[4] ..."

Batch này làm bank vượt 54 câu Part 7. Đó là chủ ý: bank là kho, bộ đề chỉ
lấy đúng số câu cần và phần dôi ra dành cho đề sau.

    python generators/gen_reading_part7_005.py
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
OUT = ROOT / "output" / "exams" / "bank" / "reading" / "exam_reading_part7_005.json"
GENERATED_BY = "claude-opus-5"
Q = QuestionType

SINGLE = [

(PassageType.MEMO, """MEMORANDUM

To:      All department heads
From:    Ingrid Vasquez-Rowbotham, Chief Operating Officer
Subject: Supplier review — revised approach

The annual supplier review will proceed this year, but on a narrower basis.

In previous years we have assessed all one hundred and forty suppliers against
the same eleven criteria. The exercise absorbed roughly six weeks of departmental
time and, on examination, changed our arrangements with only four of them.

This year we will scrutinise the twenty suppliers who together account for
eighty-two per cent of spend. The remaining suppliers will be checked only for
insurance and accreditation, which takes minutes rather than days.

I am aware that some colleagues regard the full review as a safeguard, and I want
to address that directly. A process that examines everything equally examines
nothing carefully. Concentrating effort where the money is gives us a better
chance of noticing a genuine problem.

Departmental returns are due by 30 November. Please do not send partial returns;
an incomplete form is harder to work with than a late one.""",
 [("The word \"absorbed\" in paragraph 2 is closest in meaning to",
   Q.RC_VOCAB_IN_CONTEXT, "rc_vocab_in_context", 0.55,
   "The exercise absorbed roughly six weeks of departmental time",
   [("consumed", True,
     "Ở đây 'absorbed' nói việc rà soát ngốn mất sáu tuần thời gian — nghĩa là "
     "tiêu tốn."),
    ("understood", False,
     "'absorb' có nghĩa 'tiếp thu' nhưng không hợp với tân ngữ là thời gian."),
    ("attracted", False,
     "Không có nghĩa thu hút nào ở đây; chủ ngữ là công việc, tân ngữ là thời gian."),
    ("reduced", False,
     "Ngược hẳn — việc rà soát làm MẤT thời gian chứ không tiết kiệm.")]),

  ("The word \"scrutinise\" in paragraph 3 is closest in meaning to",
   Q.RC_VOCAB_IN_CONTEXT, "rc_vocab_in_context", 0.50,
   "This year we will scrutinise the twenty suppliers",
   [("examine closely", True,
     "'scrutinise' là xem xét kỹ lưỡng, đối lập với việc chỉ kiểm tra qua loa ở "
     "câu ngay sau."),
    ("replace with others", False,
     "Bản ghi nhớ không nói tới việc thay nhà cung cấp."),
    ("pay more quickly", False,
     "Không có nội dung nào về thanh toán."),
    ("reduce in number", False,
     "Con số hai mươi là nhóm được rà soát, không phải mục tiêu cắt giảm.")]),

  ("What does the writer say about the previous approach?", Q.RC_DETAIL,
   "rc_detail", 0.50, "changed our arrangements with only four of them",
   [("It rarely led to any change", True,
     "Rà soát 140 nhà cung cấp nhưng chỉ thay đổi thoả thuận với bốn nơi."),
    ("It was completed ahead of schedule each year", False,
     "Không có thông tin nào về tiến độ so với kế hoạch."),
    ("It used a different set of criteria each year", False,
     "Mười một tiêu chí được dùng chung cho tất cả, năm nào cũng vậy."),
    ("It was carried out by an external consultancy firm", False,
     "Các phòng ban tự làm — bài nhắc tới thời gian của phòng ban.")]),

  ("What are department heads asked to avoid?", Q.RC_DETAIL, "rc_detail", 0.40,
   "Please do not send partial returns",
   [("Submitting a form that is not finished", True,
     "Bản ghi nhớ nói rõ bản khai dở dang còn khó xử lý hơn bản nộp muộn."),
    ("Contacting suppliers before the review", False,
     "Không có yêu cầu nào như vậy."),
    ("Assessing suppliers against eleven criteria", False,
     "Mười một tiêu chí là cách làm CŨ, không phải điều bị cấm."),
    ("Sending their return after 30 November", False,
     "Nộp muộn tuy không tốt nhưng bài nói nó vẫn hơn nộp thiếu.")])]),

(PassageType.ARTICLE, """The Quiet Return of the Repair Shop

For thirty years the economics were simple: a new kettle cost less than an hour
of a technician's time, so the kettle went in the bin. — [1] —

Something has shifted. Kestrelbridge now has four repair businesses where in
2015 it had one, and the newest of them has a two-week waiting list.

The owners themselves are cautious about the reasons. — [2] — Mira Deshpande,
who reopened her father's shop in 2022, is blunt: "People say it's about the
environment. For most of my customers it's about the price of a new machine."

Legislation has helped. Since 2021 manufacturers selling certain appliances in
this market have been required to make spare parts available for ten years, at a
price that is not deliberately prohibitive. — [3] — Before that rule, a common
answer to a repair request was simply that the part did not exist.

The limits are real. Nobody in Kestrelbridge will repair a laptop screen for
less than the cost of a replacement laptop. — [4] — But for washing machines,
kettles and lawnmowers, the arithmetic has changed, and with it the habit.""",
 [("In which of the positions marked [1], [2], [3] and [4] does the following "
   "sentence best belong?  \"That obligation turned an impossible job into a "
   "merely awkward one.\"",
   Q.RC_SENTENCE_INSERTION, "rc_sentence_insertion", 0.70,
   "at a price that is not deliberately prohibitive. — [3] —",
   [("[3]", True,
     "Câu chèn nói về 'nghĩa vụ đó', tức quy định bắt buộc bán phụ tùng vừa được "
     "nêu ngay trước [3]; câu sau [3] giải thích tình trạng trước khi có quy định."),
    ("[1]", False,
     "Vị trí [1] nằm ở đoạn nói về kinh tế học cũ, chưa có nghĩa vụ pháp lý nào "
     "được nhắc tới để 'that obligation' quy chiếu."),
    ("[2]", False,
     "Vị trí [2] mở đầu phần các chủ tiệm nói về nguyên nhân, chưa liên quan tới "
     "quy định về phụ tùng."),
    ("[4]", False,
     "Vị trí [4] thuộc đoạn nói về giới hạn của việc sửa chữa, không phải về "
     "nghĩa vụ của nhà sản xuất.")]),

  ("What does Ms Deshpande suggest about her customers?", Q.RC_INFERENCE,
   "rc_inference", 0.60, "For most of my customers it's about the price of a new machine",
   [("Cost matters to them more than the environment does", True,
     "Bà nói thẳng phần lớn khách tới vì giá máy mới, không phải vì môi trường."),
    ("They are willing to wait two weeks for a repair", False,
     "Danh sách chờ hai tuần là của tiệm mới nhất, không phải tiệm của bà."),
    ("Most of them are repairing laptops", False,
     "Bài nói không ai sửa màn hình laptop vì không kinh tế."),
    ("They were already customers of her father's shop", False,
     "Bà mở lại tiệm của cha nhưng bài không nói khách là khách cũ.")]),

  ("The word \"prohibitive\" in paragraph 4 is closest in meaning to",
   Q.RC_VOCAB_IN_CONTEXT, "rc_vocab_in_context", 0.65,
   "at a price that is not deliberately prohibitive",
   [("too high to be practical", True,
     "Nói về giá thì 'prohibitive' nghĩa là cao tới mức khiến người ta không mua nổi."),
    ("forbidden by the authorities", False,
     "Đây là nghĩa gốc của 'prohibit' nhưng khi bổ nghĩa cho 'price' thì không dùng."),
    ("difficult to calculate", False,
     "Không có nội dung nào về việc tính giá phức tạp."),
    ("fixed by the manufacturer", False,
     "Nhà sản xuất đúng là bên đặt giá, nhưng đó không phải nghĩa của từ này.")]),

  ("What point does the final paragraph make?", Q.RC_MAIN_IDEA, "rc_main_idea",
   0.55, "But for washing machines, kettles and lawnmowers, the arithmetic has changed",
   [("Repair now makes sense for some products but not all", True,
     "Đoạn cuối nêu giới hạn với laptop rồi đối lập với máy giặt, ấm đun, máy cắt cỏ."),
    ("Laptops will soon become cheaper to repair", False,
     "Bài không dự đoán gì về tương lai của laptop."),
    ("Most people now choose to repair rather than replace", False,
     "Bài không đưa số liệu về tỉ lệ người sửa so với người mua mới."),
    ("The new legislation has had no real effect", False,
     "Ngược lại — bài nói quy định đã giúp ích rõ rệt.")])]),
]


def main() -> int:
    groups, idx = [], 400
    for ptype, text, rows in SINGLE:
        g, idx = build_group([(ptype, text)], rows, idx)
        groups.append(g)

    n_q = sum(len(g.questions) for g in groups)
    print(f"Part 7 batch 005: {len(groups)} group, {n_q} câu")
    for g in groups:
        for p in g.passages:
            w = len(p.text.split())
            if not (150 <= w <= 300):
                print(f"  ⚠ passage {w} từ, ngoài 150–300")
    print(f"  evidence_span: "
          f"{sum(1 for g in groups for q in g.questions if q.evidence_span)}/{n_q}")
    for w in report_bias(groups):
        print(f"  ⚠ {w}")
    print()

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_reading_part7_005", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
