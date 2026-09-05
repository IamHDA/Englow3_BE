#!/usr/bin/env python3
"""Part 2 — 25 câu hỏi–đáp, viết tay theo định dạng TOEIC.

Part 2 chỉ có 3 lựa chọn nên p_guess = 0.33, cao hơn hẳn các part khác. Bù lại,
distractor phải đánh vào lỗi nghe cụ thể chứ không được là câu vô nghĩa:

  - bẫy đồng âm / gần âm  (fare ~ fair, weather ~ whether)
  - bẫy lặp từ            nhắc lại một từ trong câu hỏi ở ngữ cảnh sai
  - bẫy sai loại câu hỏi  trả lời yes/no cho câu hỏi Wh-

Không có audio_url — §Phase 8 cấm nhét URL giả. Trường script là nội dung sẽ
đưa vào TTS ở phase sau; alignment_status để pending cho tới khi có timestamp thật.

    python generators/gen_listening_part2.py
"""

from __future__ import annotations

import collections
import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from authoring import place_options  # noqa: E402
from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    AudioAsset, BatchMetadata, Definition, ExamBatch, ExamGroup, ExamItem,
    ModuleType, Option, QuestionType,
)
from schemas.enums import Accent  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "listening" / "exam_listening_part2_001.json"
GENERATED_BY = "claude-opus-5"
LABELS = ["A", "B", "C"]
Q = QuestionType

# Chỉ tiêu giọng của §Phase 8: US 50%, UK/AU/CA mỗi giọng ~17%.
ACCENT_CYCLE = [Accent.US, Accent.UK, Accent.US, Accent.AU,
                Accent.US, Accent.CA, Accent.US, Accent.UK,
                Accent.US, Accent.AU, Accent.US, Accent.CA]

# (prompt, question_type, concept_id, difficulty, [(text, correct, rationale_vi)])
ITEMS: list[tuple] = [

("Where did you put the shipping invoices?", Q.LC_WH_QUESTION, "lc_wh_question", 0.30, [
 ("In the top drawer of your desk.", True,
  "Câu hỏi Where cần một địa điểm — 'in the top drawer' trả lời đúng."),
 ("Yes, I sent every one of them this morning.", False,
  "Bẫy sai loại câu hỏi: Where không trả lời được bằng Yes."),
 ("About forty boxes altogether.", False,
  "Đây là câu trả lời cho How many, không phải Where."),
]),

("Who's covering the front desk while Marta is away?", Q.LC_WH_QUESTION,
 "lc_wh_question", 0.35, [
 ("Devansh volunteered for the whole week.", True,
  "Câu hỏi Who cần tên người — 'Devansh' đáp đúng."),
 ("She'll be back on the fourteenth.", False,
  "Bẫy: nhắc tới Marta nhưng trả lời When chứ không phải Who."),
 ("Right by the main entrance on the ground floor.", False,
  "Đây là địa điểm của quầy lễ tân, không phải người trực."),
]),

("When does the quarterly report have to be submitted?", Q.LC_WH_QUESTION,
 "lc_wh_question", 0.30, [
 ("Not until the end of the month.", True,
  "Câu hỏi When cần mốc thời gian — 'end of the month' đáp đúng."),
 ("To the finance department, I believe.", False,
  "Đây trả lời Where/To whom, không phải When."),
 ("Because the figures came in late.", False,
  "'Because' trả lời Why, không phải When."),
]),

("Why was the training session moved to Thursday?", Q.LC_WH_QUESTION,
 "lc_wh_question", 0.45, [
 ("The room we booked wasn't available.", True,
  "Câu hỏi Why cần lý do — phòng đã đặt không dùng được."),
 ("In the second-floor training room.", False,
  "Bẫy lặp từ 'training' nhưng trả lời Where."),
 ("Yes, it was moved last week as well, wasn't it?", False,
  "Câu hỏi Why không trả lời bằng Yes."),
]),

("How long will the software update take?", Q.LC_WH_QUESTION, "lc_wh_question", 0.35, [
 ("Around two hours, they said.", True,
  "How long cần khoảng thời gian — 'two hours' đáp đúng."),
 ("It's the new inventory system.", False,
  "Đây trả lời 'which software', không phải thời lượng."),
 ("No, I haven't updated mine yet.", False,
  "Bẫy lặp từ 'update' và trả lời Yes/No cho câu hỏi How long."),
]),

("Which supplier did we use for the office chairs?", Q.LC_WH_QUESTION,
 "lc_wh_question", 0.40, [
 ("Kelsingham, the same as last year.", True,
  "Câu hỏi Which cần một lựa chọn cụ thể — tên nhà cung cấp."),
 ("They're quite comfortable, actually.", False,
  "Nhận xét về ghế, không trả lời nhà cung cấp nào."),
 ("We ordered twelve of them.", False,
  "Số lượng trả lời How many, không phải Which."),
]),

("Haven't the new badges arrived yet?", Q.LC_YES_NO,
 "lc_negative_question", 0.55, [
 ("They came in yesterday, actually.", True,
  "Câu hỏi phủ định vẫn trả lời theo sự thật — hàng đã đến hôm qua."),
 ("Yes, I'd like a new one please.", False,
  "Bẫy lặp từ 'new' nhưng lạc sang yêu cầu cá nhân."),
 ("At the security office downstairs.", False,
  "Trả lời Where, trong khi câu hỏi là có hay chưa."),
]),

("You've worked with Ms Adeyemi before, haven't you?", Q.LC_YES_NO,
 "lc_tag_question", 0.50, [
 ("Only briefly, on the Rothwell account.", True,
  "Câu hỏi đuôi cần xác nhận — 'only briefly' vừa xác nhận vừa bổ sung."),
 ("She works on the fourth floor.", False,
  "Bẫy lặp 'work' nhưng trả lời vị trí phòng làm việc."),
 ("I'd rather not work late again tonight, honestly.", False,
  "Lặp từ 'work' ở nghĩa hoàn toàn khác."),
]),

("Would you like me to book a taxi, or are you driving?", Q.LC_INDIRECT_RESPONSE,
 "lc_alternative_question", 0.50, [
 ("I'll take my own car, thanks.", True,
  "Câu hỏi lựa chọn — chọn vế thứ hai bằng cách nói lái xe riêng."),
 ("Yes, the fare was reasonable last time.", False,
  "Câu trả lời nói về giá của chuyến trước, không chọn taxi hay tự lái lần này."),
 ("The taxi fare was quite reasonable.", False,
  "Bẫy lặp 'taxi' và nói về chuyện đã qua."),
]),

("Should we order lunch in, or is there time to go out?", Q.LC_INDIRECT_RESPONSE,
 "lc_alternative_question", 0.55, [
 ("There's a place two minutes away.", True,
  "Gợi ý quán gần đó tức là chọn vế 'đi ra ngoài'."),
 ("No, I ordered mine already.", False,
  "Câu hỏi lựa chọn không trả lời Yes/No; ngoài ra 'order' là bẫy lặp từ."),
 ("It was out of stock the last time I looked.", False,
  "Bẫy lặp 'out' ở nghĩa hoàn toàn khác."),
]),

("Could you send me the attendance figures before the meeting?",
 Q.LC_INDIRECT_RESPONSE, "lc_request_offer", 0.35, [
 ("Of course — give me ten minutes.", True,
  "Yêu cầu lịch sự được chấp nhận kèm mốc thời gian cụ thể."),
 ("The meeting is in room 3B.", False,
  "Bẫy lặp 'meeting' nhưng không đáp lại lời nhờ."),
 ("About sixty people attended in the end.", False,
  "Trả lời con số, nhưng người hỏi cần được GỬI số liệu."),
]),

("Would you mind opening a window?", Q.LC_INDIRECT_RESPONSE, "lc_request_offer", 0.55, [
 ("Not at all, it is rather warm.", True,
  "Với 'Would you mind', 'Not at all' nghĩa là đồng ý — bẫy logic phủ định."),
 ("Yes, I minded it very much.", False,
  "Dùng sai 'mind'; và 'Yes' ở đây thành từ chối một cách khó hiểu."),
 ("The windows were cleaned on Friday.", False,
  "Bẫy lặp 'window' nhưng nói chuyện khác."),
]),

("Why don't we postpone the site visit until the weather improves?",
 Q.LC_INDIRECT_RESPONSE, "lc_suggestion", 0.50, [
 ("That sounds sensible to me.", True,
  "'Why don't we' là lời đề nghị — đây là câu đồng ý."),
 ("Because the forecast was wrong.", False,
  "Bẫy: 'Why don't we' KHÔNG phải câu hỏi lý do nên 'Because' sai."),
 ("Whether it rains or not.", False,
  "Bẫy gần âm weather ~ whether."),
]),

("How about meeting the client at their office instead?", Q.LC_INDIRECT_RESPONSE,
 "lc_suggestion", 0.45, [
 ("Good idea — it's closer for them.", True,
  "'How about' là lời đề nghị, đây là câu tán thành kèm lý do."),
 ("About an hour by train.", False,
  "Bẫy lặp 'about' nhưng trả lời thời lượng đi lại."),
 ("Yes, I met her there last spring, I think.", False,
  "Bẫy lặp 'meet' ở thì quá khứ, không đáp lại đề nghị."),
]),

("I'm afraid the printer on this floor is out of order again.",
 Q.LC_INDIRECT_RESPONSE, "lc_statement_response", 0.45, [
 ("Try the one in the mail room.", True,
  "Đáp lại một lời phàn nàn bằng giải pháp thực tế."),
 ("Yes, I ordered more paper.", False,
  "Bẫy lặp 'order' ở nghĩa 'đặt hàng' thay vì 'out of order'."),
 ("On the third floor, just past the lifts.", False,
  "Bẫy lặp 'floor' nhưng không đáp lại vấn đề."),
]),

("The figures in this column don't seem to add up.", Q.LC_INDIRECT_RESPONSE,
 "lc_statement_response", 0.55, [
 ("Let me check the source file.", True,
  "Đáp lại một vấn đề bằng hành động kiểm tra — phản hồi tự nhiên."),
 ("Yes, please add me to the list.", False,
  "Bẫy lặp 'add' ở nghĩa khác hẳn."),
 ("They're in the second column.", False,
  "Bẫy lặp 'column' mà không xử lý vấn đề sai số."),
]),

("I thought Anwar was leading this project.", Q.LC_INDIRECT_RESPONSE,
 "lc_statement_response", 0.60, [
 ("He was, until he moved to the Leeds office.", True,
  "Xác nhận thông tin cũ đúng và giải thích vì sao đã thay đổi."),
 ("Yes, the project is due to start on Monday.", False,
  "Bẫy lặp 'project' nhưng không nói về người phụ trách."),
 ("It's the second door on the left.", False,
  "Trả lời địa điểm, không liên quan."),
]),

("Do you know whether the lift has been repaired?", Q.LC_YES_NO,
 "lc_yes_no", 0.45, [
 ("Facilities said it would be done today.", True,
  "Trả lời gián tiếp nhưng cung cấp đúng thông tin được hỏi."),
 ("Yes, I lifted it myself.", False,
  "Bẫy lặp 'lift' ở nghĩa động từ 'nâng'."),
 ("On the ground floor, next to reception.", False,
  "Trả lời vị trí thang máy, không phải tình trạng sửa chữa."),
]),

("Has the shipment cleared customs?", Q.LC_YES_NO, "lc_yes_no", 0.40, [
 ("It should be released this afternoon.", True,
  "Trả lời gián tiếp về tiến độ thông quan — dạng đáp án rất phổ biến ở Part 2."),
 ("Yes, the office was cleaned last night.", False,
  "Bẫy gần âm cleared ~ cleaned."),
 ("We usually ship on Wednesdays.", False,
  "Bẫy lặp 'ship' nhưng nói về thói quen, không phải lô hàng này."),
]),

("Is the conference room free at three?", Q.LC_YES_NO, "lc_yes_no", 0.35, [
 ("Sorry, HR booked it this morning.", True,
  "Câu trả lời phủ định gián tiếp kèm lý do."),
 ("The registration fee is free for staff.", False,
  "Bẫy lặp 'free' ở nghĩa 'miễn phí'."),
 ("About thirty people, at most.", False,
  "Trả lời sức chứa, không phải phòng có trống hay không."),
]),

("What time does the branch in Ipswich close?", Q.LC_WH_QUESTION, "lc_wh_question",
 0.35, [
 ("Half past five on weekdays.", True,
  "Câu hỏi What time cần giờ cụ thể."),
 ("It's quite close to the station.", False,
  "Bẫy đồng âm khác nghĩa: close (đóng cửa) ~ close (gần)."),
 ("Yes, I've been there twice.", False,
  "Câu hỏi What time không trả lời bằng Yes."),
]),

("How did the presentation go this morning?", Q.LC_WH_QUESTION, "lc_wh_question",
 0.45, [
 ("Better than I expected, honestly.", True,
  "'How did it go' hỏi về kết quả — đây là nhận xét đúng trọng tâm."),
 ("By train — it only takes about an hour.", False,
  "Bẫy: 'How' ở đây KHÔNG hỏi phương tiện."),
 ("At nine o'clock sharp.", False,
  "Trả lời When, không phải kết quả."),
]),

("Who should I speak to about a parking permit?", Q.LC_WH_QUESTION, "lc_wh_question",
 0.40, [
 ("Try Nkechi in Facilities.", True,
  "Câu hỏi Who cần người — kèm cả bộ phận cho rõ."),
 ("There's a car park behind the building.", False,
  "Bẫy lặp 'park' nhưng trả lời địa điểm."),
 ("It costs forty pounds a month.", False,
  "Trả lời giá, không phải người phụ trách."),
]),

("I can't find the contract Priya sent over.", Q.LC_INDIRECT_RESPONSE,
 "lc_statement_response", 0.55, [
 ("Check your junk folder — mine ended up there.", True,
  "Đáp lại vấn đề bằng gợi ý cụ thể, kèm kinh nghiệm bản thân."),
 ("Yes, she sent a copy to the client as well.", False,
  "Bẫy lặp 'sent' nhưng không giúp tìm được thư."),
 ("The contract runs for three years.", False,
  "Bẫy lặp 'contract' nhưng nói về nội dung hợp đồng."),
]),

("Aren't we supposed to wear high-visibility jackets in the yard?",
 Q.LC_YES_NO, "lc_negative_question", 0.60, [
 ("Only past the yellow line, I think.", True,
  "Trả lời có điều kiện — đúng tinh thần câu hỏi phủ định xác nhận quy định."),
 ("Yes, the jacket was on the chair.", False,
  "Bẫy lặp 'jacket' nhưng nói về một chiếc áo cụ thể."),
 ("Because the yard is being resurfaced.", False,
  "Trả lời Why trong khi câu hỏi là về quy định."),
]),
]


def build_group(idx: int, prompt: str, qtype: QuestionType, concept: str,
                difficulty: float, options: list[tuple[str, bool, str]]) -> ExamGroup:
    options = place_options(idx, prompt, options)
    opts = [Option(label=LABELS[i], text=t, is_correct=c, rationale_vi=r)
            for i, (t, c, r) in enumerate(options)]
    correct_text, correct_vi = next((t, r) for t, c, r in options if c)

    # Script là những gì thí sinh NGHE: câu hỏi rồi ba phương án theo đúng thứ tự
    # đã gán nhãn. Không có bản in nào cho Part 2.
    script = f"{prompt}\n" + "\n".join(
        f"({LABELS[i]}) {t}" for i, (t, _, _) in enumerate(options))

    # Các dạng nhờ vả / đề nghị / đáp trần thuật đều là bài tập đáp GIÁN TIẾP.
    # Gắn thêm concept cha để BKT cập nhật cả kỹ năng chung, không chỉ dạng câu.
    concepts = [concept]
    if qtype is Q.LC_INDIRECT_RESPONSE and concept != "lc_indirect_response":
        concepts.append("lc_indirect_response")

    item = ExamItem(
        part_number=2, question_text=prompt, question_type=qtype, options=opts,
        concept_ids=concepts, difficulty_prior=difficulty,
        explanation=Definition(en=f'The correct response is "{correct_text}".',
                               vi=correct_vi))
    return ExamGroup(
        part_number=2,
        audio=AudioAsset(script=script,
                         accent=ACCENT_CYCLE[idx % len(ACCENT_CYCLE)],
                         speaker_count=2),
        questions=[item])


def main() -> int:
    groups = [build_group(i, *row) for i, row in enumerate(ITEMS)]
    n = len(groups)

    pos = collections.Counter(
        next(o.label for o in g.questions[0].options if o.is_correct) for g in groups)
    print(f"Part 2: {n} câu")
    print("  B-1 vị trí đáp án đúng: " +
          "  ".join(f"{k}={pos[k]} ({pos[k]/n*100:.0f}%)" for k in LABELS))

    longest = sum(1 for g in groups
                  if max(g.questions[0].options, key=lambda o: len(o.text)).is_correct)
    print(f"  B-2 đáp án đúng dài nhất: {longest}/{n} ({longest/n*100:.0f}%)")

    acc = collections.Counter(g.audio.accent.value for g in groups)
    print("  giọng: " + "  ".join(f"{k}={v} ({v/n*100:.0f}%)" for k, v in acc.most_common()))
    print(f"  audio_url: 0/{n} (chưa chạy TTS — §Phase 8 cấm URL giả)")
    types = collections.Counter(g.questions[0].question_type.value for g in groups)
    print(f"  question_type: {len(types)} loại\n")

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_listening_part2_001", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=n),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
