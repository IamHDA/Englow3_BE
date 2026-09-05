#!/usr/bin/env python3
"""Part 6 — 4 đoạn văn × 4 chỗ trống = 16 câu, viết tay theo định dạng TOEIC.

Nội dung viết mới hoàn toàn (§0.4). Tên công ty và người đều hư cấu.
Độ dài đoạn 120–160 từ theo §Phase 7.

Mỗi đoạn có một câu dạng `ds_sentence_insertion` — chọn cả một câu để chèn vào
chỗ trống. Đây là dạng đặc trưng của Part 6 mà Part 5 không có.

    python generators/gen_reading_part6.py
"""

from __future__ import annotations

import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from authoring import LABELS, place_options, report_bias, write_batch  # noqa: E402
from schemas import (  # noqa: E402
    BatchMetadata, Definition, ExamBatch, ExamGroup, ExamItem, ModuleType,
    Option, Passage, QuestionType,
)
from schemas.enums import PassageType  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "reading" / "exam_reading_part6_001.json"
GENERATED_BY = "claude-opus-5"

# --- Đoạn 1: email nội bộ về chuyển văn phòng --------------------------------
P1 = """To: All Marlowe Analytics staff
From: Devika Ranganathan, Facilities Manager
Subject: Relocation of the Bishopsgate office

As announced last month, our Bishopsgate team will move to the new premises on
Calder Street during the second week of April. Packing crates ____(1) to each
floor on 3 April, and staff should label every box with their department code
before leaving on 8 April.

Please note that the loading bay on Calder Street is narrow, so the movers have
asked us to stagger arrivals. ____(2) Departments will therefore be assigned
individual time slots, which your team lead will circulate by 1 April.

Personal items such as plants and framed photographs will ____(3) be
transported by the moving company. Staff who wish to bring these to the new
office should carry them ____(4).

Thank you for your patience during the transition."""

P1_ITEMS = [
    ("(1)", QuestionType.GR_VOICE, "gram_passive_present", 0.45, [
        ("will be delivered", True, "Crates là vật, không tự giao đến → bị động tương lai."),
        ("will deliver", False, "Chủ động: thùng đóng gói không thể tự giao thứ gì."),
        ("have delivered", False, "Chủ động và sai thì so với mốc tương lai '3 April'."),
        ("are delivering", False, "Chủ động, thùng không phải tác nhân."),
    ]),
    ("(2)", QuestionType.DS_SENTENCE_INSERTION, "gram_discourse_marker", 0.60, [
        ("Only two vehicles can be unloaded at any one time.", True,
         "Giải thích lý do phải giãn giờ, nối trực tiếp với câu trước về lối vào hẹp."),
        ("The new office has been fitted with adjustable desks.", False,
         "Đúng chủ đề chuyển văn phòng nhưng không liên quan tới lối vào hẹp."),
        ("Staff parking permits will remain valid until December.", False,
         "Lạc sang chuyện chỗ đỗ xe, không giải thích việc giãn giờ."),
        ("Please recycle any documents you no longer need.", False,
         "Lời nhắc chung, không nối được với câu trước."),
    ]),
    ("(3)", QuestionType.GR_ARTICLE, "gram_negation", 0.50, [
        ("not", True, "Câu sau nói nhân viên phải tự mang → công ty KHÔNG chuyển giúp."),
        ("also", False, "Nghĩa 'cũng sẽ được chuyển', mâu thuẫn với câu tiếp theo."),
        ("still", False, "Nghĩa 'vẫn sẽ được chuyển', cũng mâu thuẫn."),
        ("soon", False, "Chỉ thời gian, không tạo được nghĩa phủ định mà mạch văn đòi."),
    ]),
    ("(4)", QuestionType.GR_PRONOUN, "gram_pronoun_reflexive", 0.55, [
        ("themselves", True, "Nhân viên tự mang — 'carry them themselves' nhấn mạnh tự làm."),
        ("himself", False, "Số ít, không hợp với chủ ngữ số nhiều 'Staff'."),
        ("their own", False, "Thiếu danh từ đi kèm; 'their own' không đứng một mình ở đây."),
        ("each other", False, "Nghĩa 'mang giúp nhau', không phải ý của thông báo."),
    ]),
]

# --- Đoạn 2: thông báo về bãi đỗ xe -----------------------------------------
P2 = """NOTICE TO ALL TENANTS — Thornbury Business Park

Resurfacing work in the north car park will begin on Monday, 12 May, and is
expected to last three weeks. ____(1) this period, the north entrance will be
closed to all vehicles.

Tenants holding annual permits may park in the south car park at no extra
charge. Spaces there are limited, so we recommend arriving early or using the
shuttle service that runs every fifteen minutes from Thornbury station.

We apologise for any ____(2) this may cause. The resurfacing has been postponed
twice already, and further delay would risk damage to the drainage system
beneath the tarmac.

____(3) Tenants who need overnight access for deliveries should contact the
site office at least 48 hours ____(4) so that alternative arrangements can be
made."""

P2_ITEMS = [
    ("(1)", QuestionType.GR_PREPOSITION, "gram_preposition_time_advanced", 0.45, [
        ("Throughout", True, "Đi với danh từ chỉ khoảng thời gian, nghĩa 'suốt cả'."),
        ("While", False, "Liên từ, đòi cả mệnh đề chứ không đi với cụm danh từ."),
        ("Since", False, "Chỉ mốc bắt đầu tính tới hiện tại, sai nghĩa và sai thì."),
        ("Until", False, "Chỉ mốc kết thúc, không diễn tả 'suốt khoảng thời gian'."),
    ]),
    ("(2)", QuestionType.GR_WORD_FORM, "gram_word_form_noun", 0.40, [
        ("inconvenience", True, "Sau 'any' cần danh từ; 'apologise for any inconvenience' là cụm chuẩn."),
        ("inconvenient", False, "Tính từ, không đứng sau 'any' ở vị trí tân ngữ."),
        ("inconveniently", False, "Trạng từ, sai từ loại."),
        ("inconvenienced", False, "Quá khứ phân từ, không làm tân ngữ của 'for'."),
    ]),
    ("(3)", QuestionType.DS_SENTENCE_INSERTION, "gram_discourse_marker", 0.60, [
        ("Access arrangements outside normal hours will be handled separately.", True,
         "Mở đầu cho câu sau về giao hàng ban đêm, tạo cầu nối đúng chủ đề."),
        ("The shuttle service was introduced last year in response to tenant feedback.", False,
         "Quay lại chuyện shuttle đã nói ở trên, không dẫn được vào câu sau."),
        ("Annual parking permits may be renewed at the site office from May onwards.", False,
         "Đúng bối cảnh nhưng không liên quan tới truy cập ban đêm."),
        ("Drainage repairs beneath the tarmac are scheduled for the following year.", False,
         "Nhắc lại chi tiết phụ, làm đứt mạch dẫn vào câu sau."),
    ]),
    ("(4)", QuestionType.VC_WORD_CHOICE, "vocab_business_office_b1", 0.55, [
        ("in advance", True, "'48 hours in advance' = báo trước 48 tiếng."),
        ("beforehand", False, "Nghĩa gần đúng nhưng không đi được sau cụm chỉ thời lượng."),
        ("ahead", False, "'ahead' cần 'of' để nối, đứng một mình thì thiếu."),
        ("early", False, "Không tạo được cụm 'at least 48 hours early' tự nhiên."),
    ]),
]

# --- Đoạn 3: quảng cáo dịch vụ ----------------------------------------------
P3 = """Keldane Document Services — Now Serving the Riverside District

For over a decade, Keldane has helped small firms manage the paperwork that
____(1) with growth. Our scanning team digitises invoices, contracts and
personnel files, then indexes them so that any document can be retrieved in
seconds.

Clients who sign a twelve-month agreement receive a free consultation and a
____(2) rate on all bulk scanning. ____(3)

Every file we handle is stored on servers located within the country, and
clients retain full ownership of their data at all times. Physical originals are
returned or securely destroyed according to written instructions supplied by the
client before collection begins.

Our Riverside branch opens on 2 June at 14 Ferryman's Walk. To mark the
opening, we are offering a twenty percent discount to any business that books a
collection ____(4) the end of July. Call 555-0142 or visit our website to
arrange a no-obligation quote."""

P3_ITEMS = [
    ("(1)", QuestionType.GR_TENSE, "gram_subject_verb_agreement", 0.55, [
        ("comes", True, "Chủ ngữ của mệnh đề quan hệ là 'paperwork' — danh từ không đếm được, số ít."),
        ("come", False, "Số nhiều — bẫy vì 'firms' đứng gần, nhưng chủ ngữ thật là 'paperwork'."),
        ("coming", False, "V-ing không làm động từ chính của mệnh đề quan hệ."),
        ("to come", False, "To-infinitive không đứng sau 'that' làm động từ chính."),
    ]),
    ("(2)", QuestionType.VC_COLLOCATION, "vocab_shopping_finance_b1", 0.50, [
        ("discounted", True, "'discounted rate' là collocation chuẩn cho giá ưu đãi."),
        ("reduction", False, "Cần tính từ bổ nghĩa cho 'rate'; 'reduction' là danh từ."),
        ("lowered", False, "'lowered rate' không phải cụm thông dụng trong quảng cáo."),
        ("cheap", False, "'cheap rate' mang sắc thái rẻ tiền, không dùng trong văn quảng cáo trang trọng."),
    ]),
    ("(3)", QuestionType.DS_SENTENCE_INSERTION, "gram_discourse_marker", 0.60, [
        ("Longer contracts also include quarterly reviews at no additional cost.", True,
         "Mở rộng ý ưu đãi cho khách ký hợp đồng dài hạn ở câu ngay trước."),
        ("All of our scanning staff undergo background checks before handling files.", False,
         "Đúng chủ đề dịch vụ nhưng không nối với ý ưu đãi hợp đồng."),
        ("The Riverside district has seen rapid commercial development recently.", False,
         "Thông tin nền, cắt ngang mạch nói về quyền lợi khách hàng."),
        ("Paper records take up valuable office space in most small firms.", False,
         "Lặp lại ý mở đầu, không phát triển ý câu trước."),
    ]),
    ("(4)", QuestionType.GR_PREPOSITION, "gram_preposition_time_advanced", 0.50, [
        ("before", True, "'before the end of July' = trước hạn cuối tháng Bảy."),
        ("until", False, "'until' chỉ tính liên tục tới mốc, không dùng cho hành động đặt lịch một lần."),
        ("within", False, "'within' cần một khoảng thời lượng, không đi với một mốc như 'the end of July'."),
        ("since", False, "Chỉ mốc bắt đầu trong quá khứ, sai hướng thời gian."),
    ]),
]

# --- Đoạn 4: memo về chương trình đào tạo -----------------------------------
P4 = """MEMORANDUM

To: Regional supervisors
From: Ngozi Abara, Learning and Development
Date: 3 September
Re: Revised onboarding programme

Beginning in October, all new technicians will complete a four-day onboarding
programme instead of the current two-day session. The extension ____(1) us to
cover the updated safety regulations in far greater depth.

____(2) Supervisors will no longer be expected to deliver the equipment module
themselves; a specialist trainer will visit each site instead.

Please submit the names of technicians joining in October ____(3) 20 September.
Late submissions cannot be accommodated, as training rooms must be reserved a
month ahead.

The first three days will still be held at the regional training centre, but the
final day will now take place on site so that new technicians can practise on
the equipment they will actually use. Feedback from last year's cohort suggested
that classroom sessions alone left them underprepared for field conditions.

We expect the revised programme to reduce the number of ____(4) errors reported
during a technician's first six months."""

P4_ITEMS = [
    ("(1)", QuestionType.GR_TENSE, "gram_verb_object_infinitive", 0.50, [
        ("will allow", True, "Chương trình bắt đầu tháng Mười → kết quả thuộc tương lai."),
        ("allowed", False, "Quá khứ, mâu thuẫn với 'Beginning in October'."),
        ("has allowed", False, "Hiện tại hoàn thành, nhưng việc chưa xảy ra."),
        ("allowing", False, "V-ing không làm động từ chính của câu."),
    ]),
    ("(2)", QuestionType.DS_SENTENCE_INSERTION, "gram_discourse_marker", 0.60, [
        ("The additional days also change how the programme is staffed.", True,
         "Cầu nối giữa việc kéo dài chương trình và việc đổi người phụ trách ở câu sau."),
        ("The safety regulations themselves were last revised four years ago.", False,
         "Thông tin nền về quy định, không dẫn được vào chuyện phân công."),
        ("Technicians are expected to bring their own protective equipment.", False,
         "Yêu cầu với học viên, không liên quan tới vai trò supervisor."),
        ("All training rooms are located at the regional headquarters building.", False,
         "Chi tiết hậu cần, cắt ngang mạch ý."),
    ]),
    ("(3)", QuestionType.GR_PREPOSITION, "gram_preposition_time_advanced", 0.45, [
        ("no later than", True, "Chỉ hạn chót nộp danh sách, hợp với câu sau về nộp muộn."),
        ("as long as", False, "Chỉ điều kiện, không chỉ hạn chót."),
        ("as soon as", False, "Liên từ chỉ thời điểm, đòi mệnh đề chứ không đi với ngày."),
        ("far from", False, "Không phải cụm chỉ thời gian."),
    ]),
    ("(4)", QuestionType.GR_WORD_FORM, "gram_word_form_adj", 0.55, [
        ("preventable", True, "Cần tính từ bổ nghĩa cho 'errors'; nghĩa 'lỗi có thể phòng tránh'."),
        ("prevention", False, "Danh từ, ghép 'prevention errors' không tạo nghĩa."),
        ("preventing", False, "V-ing ở đây cho nghĩa 'lỗi đang ngăn chặn', vô lý."),
        ("prevent", False, "Động từ, sai từ loại."),
    ]),
]

PASSAGES = [
    ("Marlowe Analytics relocation email", PassageType.EMAIL, P1, P1_ITEMS),
    ("Thornbury Business Park notice", PassageType.NOTICE, P2, P2_ITEMS),
    ("Keldane Document Services advertisement", PassageType.ADVERTISEMENT, P3, P3_ITEMS),
    ("Onboarding programme memo", PassageType.MEMO, P4, P4_ITEMS),
]


def build_group(g_idx: int, ptype: PassageType, text: str, rows: list) -> ExamGroup:
    questions = []
    for i, (blank, qtype, concept, difficulty, options) in enumerate(rows):
        # Chỉ số toàn cục để vị trí đáp án đúng xoay vòng đều trên cả bộ 16 câu
        opts_placed = place_options(g_idx * 4 + i, text + blank, options)
        opts = [Option(label=LABELS[j], text=t, is_correct=c, rationale_vi=r)
                for j, (t, c, r) in enumerate(opts_placed)]
        questions.append(ExamItem(
            part_number=6,
            question_text=f"Chỗ trống {blank}",
            question_type=qtype,
            options=opts,
            concept_ids=[concept],
            difficulty_prior=difficulty,
            explanation=Definition(
                en=f'The correct answer is "{next(t for t, c, _ in options if c)}".',
                vi=next(r for _, c, r in options if c)),
        ))
    return ExamGroup(
        part_number=6,
        passages=[Passage(order=1, passage_type=ptype, text=text)],
        questions=questions,
    )


def main() -> int:
    groups = [build_group(i, ptype, text, rows)
              for i, (_, ptype, text, rows) in enumerate(PASSAGES)]

    batch = ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_reading_part6_001", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups)

    write_batch(batch, OUT, ROOT)
    n_q = sum(len(g.questions) for g in groups)
    print(f"  {len(groups)} đoạn, {n_q} câu")
    print()
    for name, _, text, _ in PASSAGES:
        w = len(text.split())
        flag = "" if 120 <= w <= 190 else "  ⚠ ngoài khoảng 120–190"
        print(f"  {name:45} {w:3d} từ{flag}")
    print()
    warns = report_bias(groups)
    for w in warns:
        print(f"  ⚠ {w}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
