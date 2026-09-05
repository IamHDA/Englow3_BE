#!/usr/bin/env python3
"""Part 5 — 30 câu hoàn thành câu, viết tay theo định dạng TOEIC.

Nội dung viết mới hoàn toàn (§0.4 cấm sao chép đề ETS thật). Chất lượng bám
docs/exam-quality-bar.md: mỗi distractor nhắm vào một hiểu lầm cụ thể và có
`rationale_vi` riêng, không có đáp án sai vô nghĩa.

Tên công ty và tên người đều hư cấu (§Phase 7).

    python generators/gen_reading_part5.py
"""

from __future__ import annotations

import hashlib
import json
import random
import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schemas import (  # noqa: E402
    BatchMetadata, Definition, ExamBatch, ExamGroup, ExamItem, ModuleType,
    Option, Passage, QuestionType,
)
from schemas.enums import PassageType  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "reading" / "exam_reading_part5_001.json"

GENERATED_BY = "claude-opus-5"

# (stem, question_type, concept_id, difficulty, [(text, correct, rationale)])
# Nhãn A–D gán theo thứ tự; vị trí đáp án đúng được xáo có chủ đích để không
# dồn về B/C (thiên lệch B-1 trong exam-quality-bar.md).
ITEMS: list[tuple] = [
    ("All maintenance requests must be submitted ____ the building supervisor "
     "before 5:00 P.M. on weekdays.",
     QuestionType.GR_PREPOSITION, "gram_dependent_preposition", 0.35, [
        ("to", True, "submit something TO someone — giới từ đi kèm cố định của 'submit'."),
        ("at", False, "'at' chỉ vị trí hoặc thời điểm, không dùng cho người nhận."),
        ("for", False, "'for' mang nghĩa 'thay mặt / vì lợi ích của', không phải người nhận."),
        ("by", False, "'by' chỉ tác nhân thực hiện, nhưng ở đây supervisor là người NHẬN."),
     ]),

    ("The board approved the proposal only after a thorough ____ of the "
     "quarterly figures.",
     QuestionType.GR_WORD_FORM, "gram_word_form_noun", 0.45, [
        ("review", True, "Sau mạo từ 'a' và tính từ 'thorough' cần một danh từ."),
        ("reviewed", False, "Dạng quá khứ phân từ, không đứng sau 'a thorough' được."),
        ("reviewing", False, "V-ing ở đây sẽ thành danh động từ nhưng không đi với mạo từ 'a'."),
        ("reviewable", False, "Tính từ; sau 'a thorough' đã có tính từ rồi, cần danh từ."),
     ]),

    ("Ms. Farrow ____ with the Halden Group for twelve years before she opened "
     "her own consultancy.",
     QuestionType.GR_TENSE, "gram_present_perfect", 0.55, [
        ("had worked", True, "Việc xảy ra TRƯỚC một mốc quá khứ khác ('before she opened')."),
        ("has worked", False, "Hiện tại hoàn thành nối với hiện tại, nhưng cả câu ở quá khứ."),
        ("works", False, "Hiện tại đơn mâu thuẫn với 'before she opened' ở quá khứ."),
        ("will have worked", False, "Tương lai hoàn thành, không hợp với mốc quá khứ."),
     ]),

    ("The renovated conference rooms on the third floor ____ for client "
     "presentations starting next Monday.",
     QuestionType.GR_VOICE, "gram_passive_present", 0.40, [
        ("will be used", True, "Phòng họp là vật, không tự thực hiện hành động → bị động."),
        ("will use", False, "Chủ động: phòng họp không thể 'sử dụng' thứ gì."),
        ("are using", False, "Chủ động và sai thì so với 'starting next Monday'."),
        ("have used", False, "Chủ động, và hiện tại hoàn thành không hợp với mốc tương lai."),
     ]),

    ("Employees ____ complete the online safety module are not permitted to "
     "enter the warehouse floor.",
     QuestionType.GR_RELATIVE_CLAUSE, "gram_relative_defining", 0.50, [
        ("who did not", True, "Mệnh đề quan hệ xác định, chủ ngữ là người → 'who'."),
        ("which did not", False, "'which' dùng cho vật, không dùng cho 'employees'."),
        ("whose did not", False, "'whose' chỉ sở hữu, phải đi kèm danh từ ngay sau."),
        ("they did not", False, "Đại từ nhân xưng không nối được hai mệnh đề."),
     ]),

    ("____ the shipment arrived two days late, the production schedule was not "
     "affected.",
     QuestionType.GR_CONJUNCTION, "gram_linking_contrast", 0.50, [
        ("Although", True, "Nối hai mệnh đề đầy đủ và diễn tả tương phản."),
        ("Despite", False, "'Despite' đi với danh từ hoặc V-ing, không đi với cả mệnh đề."),
        ("However", False, "Trạng từ nối, cần dấu chấm hoặc chấm phẩy trước, không nối trực tiếp."),
        ("Nevertheless", False, "Cũng là trạng từ nối, không dùng làm liên từ nối mệnh đề."),
     ]),

    ("Reznik Logistics attributes its recent growth ____ an aggressive expansion "
     "into regional markets.",
     QuestionType.GR_PREPOSITION, "gram_dependent_preposition", 0.60, [
        ("to", True, "attribute something TO something — giới từ cố định."),
        ("on", False, "'on' đi với 'blame' hoặc 'rely', không đi với 'attribute'."),
        ("for", False, "'for' đi với 'account', không đi với 'attribute'."),
        ("with", False, "'with' đi với 'credit somebody with', khác cấu trúc."),
     ]),

    ("The technician worked ____ than expected and finished the installation "
     "before noon.",
     QuestionType.GR_COMPARISON, "gram_comparative_adj", 0.35, [
        ("more quickly", True, "Bổ nghĩa cho động từ 'worked' → cần trạng từ so sánh hơn."),
        ("more quick", False, "'quick' là tính từ, không bổ nghĩa cho động từ được."),
        ("most quickly", False, "So sánh nhất, nhưng 'than' đòi so sánh hơn."),
        ("quickly", False, "Thiếu dạng so sánh hơn trong khi câu có 'than'."),
     ]),

    ("Please confirm your attendance by replying ____ this message no later than "
     "Thursday.",
     QuestionType.GR_PREPOSITION, "gram_dependent_preposition", 0.40, [
        ("to", True, "reply TO something — giới từ cố định của 'reply'."),
        ("at", False, "'at' không đi với 'reply'."),
        ("on", False, "'on' đi với 'comment on', không đi với 'reply'."),
        ("back", False, "'reply back' thừa nghĩa và 'back' không phải giới từ ở đây."),
     ]),

    ("The new expense policy applies to all staff, ____ of department or "
     "seniority.",
     QuestionType.VC_WORD_CHOICE, "vocab_business_office_b2", 0.55, [
        ("regardless", True, "'regardless of' = bất kể, đúng nghĩa 'áp dụng cho tất cả'."),
        ("in spite", False, "Phải là 'in spite of' + danh từ, nhưng nghĩa là 'mặc dù', không khớp."),
        ("instead", False, "'instead of' = thay vì, làm câu mất nghĩa."),
        ("because", False, "'because of' chỉ nguyên nhân, ngược với ý 'bất kể'."),
     ]),

    ("Ms. Oyelaran is responsible ____ coordinating travel arrangements for the "
     "entire sales team.",
     QuestionType.GR_PREPOSITION, "gram_dependent_preposition", 0.35, [
        ("for", True, "responsible FOR — giới từ cố định của tính từ này."),
        ("of", False, "'of' không đi với 'responsible'."),
        ("to", False, "'responsible to somebody' nghĩa là chịu trách nhiệm TRƯỚC ai, khác nghĩa."),
        ("with", False, "'with' không đi với 'responsible'."),
     ]),

    ("Before ____ the contract, please verify that all delivery dates match the "
     "original quotation.",
     QuestionType.GR_PARTICIPLE, "gram_gerund_after_prep", 0.45, [
        ("signing", True, "Sau giới từ 'before' phải dùng V-ing."),
        ("sign", False, "Động từ nguyên mẫu không đứng sau giới từ."),
        ("to sign", False, "To-infinitive không đứng sau giới từ 'before'."),
        ("signed", False, "Quá khứ phân từ không hợp sau giới từ ở vị trí này."),
     ]),

    ("The seminar was cancelled ____ the unexpectedly low number of "
     "registrations.",
     QuestionType.GR_CONJUNCTION, "gram_linking_cause_result", 0.50, [
        ("owing to", True, "Đi với cụm danh từ, chỉ nguyên nhân."),
        ("because", False, "'because' đòi cả mệnh đề, ở đây chỉ có cụm danh từ."),
        ("so that", False, "Chỉ mục đích, không chỉ nguyên nhân."),
        ("therefore", False, "Trạng từ nối chỉ kết quả, đặt sai vị trí và sai quan hệ."),
     ]),

    ("All visitors must sign in at the front desk and wear a badge ____ they are "
     "on the premises.",
     QuestionType.GR_CONJUNCTION, "gram_conjunction_subordinating", 0.45, [
        ("while", True, "Chỉ khoảng thời gian song song, nối được mệnh đề đầy đủ."),
        ("during", False, "'during' là giới từ, đi với danh từ chứ không với mệnh đề."),
        ("meanwhile", False, "Trạng từ nối, không nối trực tiếp hai mệnh đề."),
        ("whereas", False, "Chỉ sự tương phản, không hợp nghĩa ở đây."),
     ]),

    ("The finance team has requested that each receipt ____ scanned and uploaded "
     "within 48 hours.",
     QuestionType.GR_VOICE, "gram_passive_present", 0.65, [
        ("be", True, "Sau 'request that' dùng thức giả định: nguyên mẫu không chia."),
        ("is", False, "Chia thì bình thường, sai với cấu trúc 'request that'."),
        ("was", False, "Quá khứ, và cũng sai với thức giả định."),
        ("being", False, "V-ing không đứng một mình làm động từ chính của mệnh đề."),
     ]),

    ("Ticket holders may exchange their seats for a later showing at no ____ "
     "charge.",
     QuestionType.VC_COLLOCATION, "vocab_shopping_finance_b1", 0.50, [
        ("additional", True, "'additional charge' là collocation chuẩn cho phí phát sinh."),
        ("added", False, "'added charge' không tự nhiên trong văn cảnh thương mại."),
        ("addition", False, "Danh từ, không bổ nghĩa trực tiếp cho 'charge' được."),
        ("additionally", False, "Trạng từ, không đứng giữa 'no' và danh từ."),
     ]),

    ("Neither the supplier nor the freight company ____ willing to cover the cost "
     "of the damaged pallets.",
     QuestionType.GR_TENSE, "gram_subject_verb_agreement", 0.60, [
        ("was", True, "Với 'neither...nor', động từ chia theo chủ ngữ GẦN nhất (số ít)."),
        ("were", False, "Chia số nhiều — bẫy vì câu nhắc tới hai bên."),
        ("have been", False, "Số nhiều và sai thì so với ngữ cảnh kể lại."),
        ("being", False, "V-ing không làm động từ chính được."),
     ]),

    ("The updated manual explains ____ to reset the terminal after a power "
     "interruption.",
     QuestionType.GR_PRONOUN, "gram_question_formation", 0.40, [
        ("how", True, "'explain how to do something' — từ để hỏi + to-infinitive."),
        ("what", False, "'what to reset' hỏi về đối tượng, nhưng đối tượng đã nêu rõ."),
        ("which", False, "'which' cần một tập lựa chọn, câu không đưa ra lựa chọn nào."),
        ("that", False, "'that' không đứng trước to-infinitive theo cấu trúc này."),
     ]),

    ("Delegates who register before 15 March will receive a ____ discount on the "
     "full conference fee.",
     QuestionType.GR_WORD_FORM, "gram_word_form_adj", 0.45, [
        ("substantial", True, "Cần tính từ bổ nghĩa cho danh từ 'discount'."),
        ("substantially", False, "Trạng từ, không bổ nghĩa trực tiếp cho danh từ."),
        ("substance", False, "Danh từ, không đứng giữa mạo từ và danh từ chính."),
        ("substantiate", False, "Động từ, sai từ loại hoàn toàn."),
     ]),

    ("Mr. Adeyemi asked his assistant to remind ____ about the vendor call "
     "scheduled for Friday.",
     QuestionType.GR_PRONOUN, "gram_pronoun_reflexive", 0.50, [
        ("him", True, "Người nhắc và người được nhắc khác nhau → đại từ tân ngữ."),
        ("himself", False, "Phản thân chỉ dùng khi chủ ngữ và tân ngữ là một người."),
        ("his", False, "Tính từ sở hữu, phải đi kèm danh từ."),
        ("he", False, "Đại từ chủ ngữ, không làm tân ngữ của 'remind'."),
     ]),

    ("The laboratory equipment arrived in ____ condition despite the long "
     "overseas journey.",
     QuestionType.VC_WORD_CHOICE, "vocab_travel_transport_b2", 0.55, [
        ("excellent", True, "Hợp nghĩa với 'despite' — vẫn tốt dù đi đường xa."),
        ("excellence", False, "Danh từ, không bổ nghĩa cho 'condition'."),
        ("excellently", False, "Trạng từ, không đứng trước danh từ."),
        ("excel", False, "Động từ, sai từ loại."),
     ]),

    ("Once the software patch ____ , all workstations will need to be restarted.",
     QuestionType.GR_TENSE, "gram_time_clause_future", 0.55, [
        ("is installed", True, "Mệnh đề thời gian chỉ tương lai dùng hiện tại, không dùng will."),
        ("will be installed", False, "Sau 'once' không dùng 'will' dù nghĩa là tương lai."),
        ("was installed", False, "Quá khứ, mâu thuẫn với 'will need' ở mệnh đề chính."),
        ("installing", False, "V-ing không làm động từ chính của mệnh đề."),
     ]),

    ("Only a ____ of the survey respondents reported difficulty accessing the "
     "customer portal.",
     QuestionType.VC_WORD_CHOICE, "vocab_business_office_b2", 0.60, [
        ("handful", True, "'a handful of' = một số ít, hợp với 'Only'."),
        ("number", False, "'a number of' nghĩa là 'khá nhiều', ngược với 'Only'."),
        ("majority", False, "'a majority of' là phần lớn, mâu thuẫn với 'Only'."),
        ("quantity", False, "'a quantity of' dùng cho vật đếm bằng khối lượng, không dùng cho người."),
     ]),

    ("Employees are encouraged to familiarize ____ with the revised evacuation "
     "procedures.",
     QuestionType.GR_PRONOUN, "gram_pronoun_reflexive", 0.50, [
        ("themselves", True, "'familiarize oneself with' — chủ ngữ và tân ngữ cùng người."),
        ("them", False, "Đại từ tân ngữ thường, làm câu ám chỉ người khác."),
        ("their", False, "Tính từ sở hữu, phải đi kèm danh từ."),
        ("they", False, "Đại từ chủ ngữ, không làm tân ngữ."),
     ]),

    ("The contract stipulates that payment is due ____ thirty days of the invoice "
     "date.",
     QuestionType.GR_PREPOSITION, "gram_preposition_time_advanced", 0.55, [
        ("within", True, "'within thirty days' = trong vòng ba mươi ngày."),
        ("during", False, "'during' cần một khoảng thời gian xác định, không dùng với số ngày đếm ngược."),
        ("until", False, "'until' chỉ mốc kết thúc liên tục, không chỉ hạn chót đếm từ mốc."),
        ("by", False, "'by' đi với một mốc ngày cụ thể, không đi với 'thirty days'."),
     ]),

    ("Hawthorne Freight has expanded its fleet in order ____ the growing demand "
     "for same-day delivery.",
     QuestionType.GR_PARTICIPLE, "gram_linking_purpose", 0.50, [
        ("to meet", True, "'in order to' + động từ nguyên mẫu, chỉ mục đích."),
        ("meeting", False, "V-ing không đứng sau 'in order to'."),
        ("for meeting", False, "Thừa giới từ; 'in order for' đòi một chủ ngữ đi kèm."),
        ("met", False, "Quá khứ phân từ không hợp sau 'in order to'."),
     ]),

    ("The auditor asked for ____ documentation before approving the reimbursement "
     "request.",
     QuestionType.VC_WORD_CHOICE, "vocab_business_office_b2", 0.55, [
        ("supporting", True, "'supporting documentation' là cụm chuẩn trong kế toán."),
        ("supported", False, "Quá khứ phân từ mang nghĩa bị động, sai sắc thái."),
        ("supportive", False, "'supportive' nói về thái độ con người, không dùng cho giấy tờ."),
        ("support", False, "Danh từ đứng cạnh danh từ khác nhưng không tạo cụm tự nhiên ở đây."),
     ]),

    ("If the prototype ____ the durability tests, production will begin in the "
     "third quarter.",
     QuestionType.GR_TENSE, "gram_conditional_first", 0.45, [
        ("passes", True, "Điều kiện loại 1: if + hiện tại đơn, mệnh đề chính dùng will."),
        ("will pass", False, "Không dùng 'will' trong mệnh đề if của điều kiện loại 1."),
        ("passed", False, "Quá khứ đơn thuộc điều kiện loại 2, mâu thuẫn với 'will begin'."),
        ("had passed", False, "Quá khứ hoàn thành thuộc điều kiện loại 3."),
     ]),

    ("____ of the two proposals addressed the issue of long-term maintenance "
     "costs.",
     QuestionType.GR_ARTICLE, "gram_quantifier_advanced", 0.60, [
        ("Neither", True, "Dùng cho hai đối tượng và đi với động từ số ít 'addressed'."),
        ("None", False, "'None of' dùng cho ba trở lên; với đúng hai thì dùng 'neither'."),
        ("Both", False, "'Both' đòi động từ số nhiều và mang nghĩa khẳng định."),
        ("Any", False, "'Any of' không đứng đầu câu khẳng định theo cách này."),
     ]),

    ("The IT department will ____ the outdated servers over the weekend to "
     "minimize disruption.",
     QuestionType.VC_PHRASAL_VERB, "gram_phrasal_verb_separable", 0.55, [
        ("phase out", True, "'phase out' = loại bỏ dần, đúng nghĩa với thiết bị cũ."),
        ("phase in", False, "'phase in' là đưa vào dần, ngược nghĩa."),
        ("put off", False, "'put off' là hoãn lại, không phải loại bỏ."),
        ("take over", False, "'take over' là tiếp quản, sai nghĩa."),
     ]),
]

LABELS = ["A", "B", "C", "D"]


def place_options(idx: int, stem: str, options: list[tuple[str, bool, str]]
                  ) -> list[tuple[str, bool, str]]:
    """Đặt đáp án đúng vào vị trí xoay vòng A→B→C→D, distractor xáo tất định.

    Nội dung viết tay luôn để đáp án đúng đầu tiên cho dễ đọc, nên nếu giữ
    nguyên thì 100% đáp án đúng rơi vào A — học viên chỉ cần chọn A. Đó là
    thiên lệch B-1 trong docs/exam-quality-bar.md.

    Xáo ngẫu nhiên có seed thì vẫn lệch: trên 30 câu đã cho ra D=40%. Xoay vòng
    theo chỉ số câu mới bảo đảm đúng 25% mỗi nhãn.

    Distractor xáo theo seed lấy từ chính câu hỏi (không dùng random() trần) để
    chạy lại pipeline ra cùng một đề.
    """
    correct = next(o for o in options if o[1])
    distractors = [o for o in options if not o[1]]
    seed = int(hashlib.sha256(stem.encode("utf-8")).hexdigest()[:8], 16)
    random.Random(seed).shuffle(distractors)

    slot = idx % len(options)          # 0→A, 1→B, 2→C, 3→D
    out = distractors[:]
    out.insert(slot, correct)
    return out


def build_item(idx: int, stem: str, qtype: QuestionType, concept: str,
               difficulty: float, options: list[tuple[str, bool, str]]) -> ExamGroup:
    """Part 5: mỗi câu là một group riêng, passage là chính câu đó (§2.5)."""
    options = place_options(idx, stem, options)
    opts = [
        Option(label=LABELS[i], text=text, is_correct=correct, rationale_vi=rationale)
        for i, (text, correct, rationale) in enumerate(options)
    ]
    correct_text = next(t for t, c, _ in options if c)
    item = ExamItem(
        part_number=5,
        question_text=stem,
        question_type=qtype,
        options=opts,
        concept_ids=[concept],
        difficulty_prior=difficulty,
        explanation=Definition(
            en=f'The correct answer is "{correct_text}".',
            vi=next(r for _, c, r in options if c),
        ),
    )
    return ExamGroup(
        part_number=5,
        passages=[Passage(order=1, passage_type=PassageType.NOTICE, text=stem)],
        questions=[item],
    )


def main() -> int:
    groups = [build_item(i, *row) for i, row in enumerate(ITEMS)]

    batch = ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_reading_part5_001",
            module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY,
            generated_at=datetime.now(UTC),
            total_records=len(groups),
        ),
        groups=groups,
    )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps(batch.model_dump(mode="json"), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")

    print(f"Ghi {OUT.relative_to(ROOT)}")
    print(f"  {len(groups)} câu, {OUT.stat().st_size // 1024} KB")

    # Phân bố vị trí đáp án đúng — thiên lệch B-1 của exam-quality-bar.md
    import collections
    pos = collections.Counter(
        next(o.label for o in g.questions[0].options if o.is_correct) for g in groups)
    print(f"\n  Vị trí đáp án đúng: " +
          "  ".join(f"{k}={pos[k]} ({pos[k]/len(groups)*100:.0f}%)" for k in LABELS))

    # Thiên lệch B-2: đáp án đúng có phải lựa chọn dài nhất không
    longest = sum(
        1 for g in groups
        if max(g.questions[0].options, key=lambda o: len(o.text)).is_correct)
    print(f"  Đáp án đúng là lựa chọn dài nhất: {longest}/{len(groups)} "
          f"({longest/len(groups)*100:.0f}%)")

    types = collections.Counter(g.questions[0].question_type.value for g in groups)
    print(f"\n  question_type: {dict(types.most_common())}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
