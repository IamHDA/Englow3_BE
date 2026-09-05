#!/usr/bin/env python3
"""Part 3 — hội thoại, mỗi hội thoại 3 câu hỏi.

Mỗi hội thoại bám một khuôn TOEIC thật: câu 1 hỏi bối cảnh/vai người nói,
câu 2 hỏi chi tiết, câu 3 hỏi hành động kế tiếp hoặc suy luận. Người học nghe
một lần nên đáp án đúng KHÔNG được là câu lặp nguyên văn lời thoại — phải là
diễn đạt lại, đúng như đề thật.

Kịch bản viết mới hoàn toàn (§0.4). Tên người và công ty đều hư cấu (§Phase 7).
Không có audio_url — §Phase 8 cấm nhét URL giả.

    python generators/gen_listening_part3.py
"""

from __future__ import annotations

import collections
import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from authoring import place_options, report_bias  # noqa: E402
from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    AudioAsset, BatchMetadata, Definition, ExamBatch, ExamGroup, ExamItem,
    ModuleType, Option, QuestionType,
)
from schemas.enums import Accent  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "listening" / "exam_listening_part3_001.json"
GENERATED_BY = "claude-opus-5"
LABELS = ["A", "B", "C", "D"]
Q = QuestionType

ACCENT_CYCLE = [Accent.US, Accent.UK, Accent.US, Accent.AU,
                Accent.US, Accent.CA, Accent.US, Accent.UK,
                Accent.US, Accent.AU, Accent.US, Accent.CA, Accent.US]


def build_group(idx: int, script: str, speakers: int,
                rows: list[tuple]) -> tuple[ExamGroup, int]:
    """rows: (stem, question_type, concept_id, difficulty, options)."""
    questions = []
    for stem, qtype, concept, diff, options in rows:
        options = place_options(idx, stem, options)
        opts = [Option(label=LABELS[i], text=t, is_correct=c, rationale_vi=r)
                for i, (t, c, r) in enumerate(options)]
        correct_text, correct_vi = next((t, r) for t, c, r in options if c)
        questions.append(ExamItem(
            part_number=3, question_text=stem, question_type=qtype, options=opts,
            concept_ids=[concept], difficulty_prior=diff,
            explanation=Definition(en=f'The correct answer is "{correct_text}".',
                                   vi=correct_vi)))
        idx += 1
    return ExamGroup(
        part_number=3,
        audio=AudioAsset(script=script, speaker_count=speakers,
                         accent=ACCENT_CYCLE[(idx // 3) % len(ACCENT_CYCLE)]),
        questions=questions), idx


CONVERSATIONS: list[tuple[str, int, list[tuple]]] = [

("""W: Morning — I'm here about the flat on Cardwell Street. I rang yesterday
   about the viewing.
M: Ah yes. I'm afraid I have to be honest with you before we go over. The
   photographs on the website are about four years old, and the kitchen has
   been replaced since then. It's a different colour entirely.
W: That's not a problem. What I actually want to check is the parking. The
   listing says "residents' permit available" and I couldn't tell whether
   that means there's a space or just the right to apply for one.
M: It's the second one. You apply to the council, and there's usually a wait
   of six to eight weeks. I should say that most people in that street do get
   one eventually.
W: Right. That does change things — I start the new job in a fortnight and I
   need to drive to it.""", 2, [

 ("What is the woman mainly concerned about?", Q.LC_GIST, "lc_gist", 0.45, [
  ("Whether she will be able to park near the flat", True,
   "Cả đoạn hội thoại xoay quanh chỗ đỗ xe — cô nói rõ đó là điều cô muốn kiểm tra."),
  ("Whether the kitchen has been recently replaced", False,
   "Bếp là điều người đàn ông nêu ra, và cô nói đó không thành vấn đề."),
  ("Whether the photographs on the website are accurate", False,
   "Ảnh cũ được nhắc tới nhưng cô gạt đi ngay."),
  ("Whether the rent on the flat has recently changed", False,
   "Không ai nhắc tới tiền thuê nhà.")]),

 ("What does the man say about the parking permit?", Q.LC_DETAIL, "lc_detail",
  0.55, [
  ("Applicants usually wait around two months", True,
   "Ông nói thời gian chờ sáu đến tám tuần — tức khoảng hai tháng."),
  ("It is included with the flat automatically", False,
   "Ông nói rõ đây chỉ là QUYỀN nộp đơn, không phải chỗ đỗ có sẵn."),
  ("It can only be obtained through the landlord", False,
   "Đơn nộp cho hội đồng thành phố, không phải chủ nhà."),
  ("Very few residents in the street are successful", False,
   "Ông nói phần lớn cư dân cuối cùng đều xin được.")]),

 ("Why is the waiting time a difficulty for the woman?", Q.LC_INFERENCE,
  "lc_inference", 0.65, [
  ("She needs to drive to a job starting soon", True,
   "Cô bắt đầu việc mới sau hai tuần và cần lái xe đi làm, ngắn hơn thời gian chờ."),
  ("She is planning to move out of the area shortly", False,
   "Cô đang định chuyển ĐẾN, không phải đi."),
  ("She has already paid the council for a permit", False,
   "Không có chi tiết nào về việc đã nộp tiền."),
  ("She does not own a car at the present time", False,
   "Cô nói cần lái xe đi làm, tức là có xe.")])]),

("""M1: Priyanka, before you send the newsletter — how many people are on the
    list now?
W:  Just over nine thousand. But I want to flag something. About a third of
    those addresses have not opened anything we've sent in the past year.
M1: That still sounds like six thousand people reading it.
W:  Not quite. Open rates are around twenty per cent of the people who are
    still active. The dead addresses hurt us twice: they drag the rate down,
    and our mailing platform charges by list size.
M2: So you're suggesting we delete them?
W:  I'd rather send those three thousand one final message asking if they want
    to stay. Whoever doesn't reply comes off. It's cleaner than deleting people
    who might simply have been busy.""", 3, [

 ("What is the woman's main point?", Q.LC_GIST, "lc_gist", 0.55, [
  ("Inactive addresses are affecting both cost and performance", True,
   "Cô nêu rõ địa chỉ chết gây hại hai lần: kéo tỉ lệ mở xuống và làm tăng phí."),
  ("The newsletter should be sent out less frequently", False,
   "Không ai nói về tần suất gửi."),
  ("The mailing platform ought to be replaced with a cheaper one", False,
   "Nền tảng chỉ được nhắc tới ở khía cạnh cách tính phí."),
  ("The list has grown too slowly over the past year", False,
   "Vấn đề là chất lượng danh sách, không phải tốc độ tăng.")]),

 ("What does the woman propose doing?", Q.LC_NEXT_ACTION, "lc_next_action",
  0.50, [
  ("Emailing inactive subscribers once to ask if they want to remain", True,
   "Cô đề xuất gửi một thư cuối cùng hỏi ý, ai không trả lời mới bị loại."),
  ("Deleting every inactive address from the list straight away", False,
   "Cô nói rõ cách này 'sạch sẽ hơn' việc xoá thẳng."),
  ("Reducing the size of the list to six thousand", False,
   "Con số sáu nghìn là ước tính sai của người đàn ông."),
  ("Asking the platform to change how it charges", False,
   "Không có đề xuất nào gửi tới nhà cung cấp.")]),

 ("What does one of the men misunderstand?", Q.LC_INFERENCE, "lc_inference",
  0.70, [
  ("He assumes everyone who is still active reads the newsletter", True,
   "Ông nói 'sáu nghìn người đọc', nhưng tỉ lệ mở chỉ khoảng 20% của số hoạt động."),
  ("He believes the list contains nine thousand active readers", False,
   "Ông nói sáu nghìn, không phải chín nghìn."),
  ("He thinks the platform charges by message sent", False,
   "Ông không nói gì về cách tính phí."),
  ("He expects the newsletter to be cancelled entirely", False,
   "Không ai đề nghị dừng bản tin.")])]),

("""W1: Thanks for coming in. Before we start — you'll have seen the role
    described as hybrid. I want to be straightforward: in practice the team is
    in the office four days a week, not two.
M:  I appreciate you saying so now rather than in month three. Is that
    permanent, or is it because the team is new?
W1: Honestly, a bit of both. We're eighteen months old and there's a lot that
    still gets solved by turning round in a chair.
W2: It's also worth saying nobody counts days. If you need to be at home on a
    Thursday, you're at home on a Thursday.
M:  That's useful. My concern was less about the number and more about whether
    it was going to be enforced by somebody with a spreadsheet.""", 3, [

 ("Where does this conversation most likely take place?", Q.LC_SPEAKER_ROLE,
  "lc_speaker_role", 0.40, [
  ("At a job interview", True,
   "Vai trò công việc được mô tả, ứng viên hỏi về điều kiện — đây là buổi phỏng vấn."),
  ("At a meeting of the company board", False,
   "Không có chi tiết nào cho thấy đây là cuộc họp hội đồng."),
  ("At a training session for new starters", False,
   "Người đàn ông vẫn chưa nhận việc, vẫn đang hỏi về vai trò."),
  ("At an appointment with an estate agent", False,
   "Chuyện làm ở nhà là điều kiện làm việc, không phải chuyện thuê nhà.")]),

 ("What does the first woman clarify about the role?", Q.LC_DETAIL, "lc_detail",
  0.50, [
  ("Staff are in the office more often than advertised", True,
   "Bà nói thực tế bốn ngày một tuần chứ không phải hai như mô tả."),
  ("The role has recently changed from full-time to hybrid", False,
   "Không có chi tiết nào về thay đổi hình thức làm việc."),
  ("The team will move to a different office soon", False,
   "Không ai nhắc tới việc chuyển văn phòng."),
  ("The position was advertised at the wrong salary", False,
   "Lương không được nhắc tới trong đoạn hội thoại.")]),

 ("What reassures the man?", Q.LC_INFERENCE, "lc_inference", 0.70, [
  ("That attendance is not formally monitored", True,
   "Ông lo bị 'ai đó cầm bảng tính' đếm ngày; bà thứ hai nói không ai đếm."),
  ("That he will only need to attend twice a week", False,
   "Bốn ngày mới là con số thật, và ông chấp nhận điều đó."),
  ("That the team will grow larger within eighteen months", False,
   "Mười tám tháng là tuổi của nhóm, không phải kế hoạch."),
  ("That he can choose which office to work from", False,
   "Lựa chọn là ở nhà hay ở văn phòng, không phải giữa các văn phòng.")])]),

("""M: Dr Okonjo, the delivery of reagents came this morning but the temperature
   logger inside the box read four degrees above range for eleven hours.
W: Then we can't use it. Don't open anything else — I need the logger file
   before it gets overwritten.
M: I've already downloaded it. The excursion happened overnight on Saturday,
   which is when the depot is unstaffed.
W: That's the third time this year, and each time it's the weekend. I'll write
   to the supplier, but I'd rather we simply stopped taking Saturday deliveries.
M: We'd have to change the ordering day to Tuesday to make that work.
W: Then let's change it. Losing a batch costs more than ordering two days
   earlier.""", 2, [

 ("What problem does the man report?", Q.LC_DETAIL, "lc_detail", 0.45, [
  ("A delivery was kept too warm for several hours", True,
   "Bộ ghi nhiệt cho thấy vượt ngưỡng bốn độ trong mười một tiếng."),
  ("A delivery did not arrive on the day that was expected", False,
   "Hàng có đến — vấn đề là nhiệt độ."),
  ("The temperature logger has stopped working", False,
   "Bộ ghi hoạt động bình thường và anh đã tải được dữ liệu."),
  ("The wrong reagents were sent by the supplier", False,
   "Không có chi tiết nào về sai chủng loại.")]),

 ("What does the woman ask the man to do first?", Q.LC_NEXT_ACTION,
  "lc_next_action", 0.55, [
  ("Preserve the temperature record before it is lost", True,
   "Bà cần file của bộ ghi trước khi nó bị ghi đè."),
  ("Return the whole delivery to the depot immediately", False,
   "Không ai nói tới việc gửi trả."),
  ("Write to the supplier about the problem", False,
   "Chính bà sẽ viết thư, không phải anh."),
  ("Move the order day forward to Tuesday", False,
   "Đó là quyết định về sau, không phải việc làm ngay.")]),

 ("What does the woman decide?", Q.LC_INFERENCE, "lc_inference", 0.60, [
  ("To stop scheduling deliveries at the weekend", True,
   "Bà chốt đổi ngày đặt hàng để không còn nhận hàng thứ Bảy."),
  ("To find a different supplier for the reagents", False,
   "Bà chỉ viết thư cho nhà cung cấp hiện tại."),
  ("To ask the depot to work on Saturdays", False,
   "Bà tránh cuối tuần chứ không yêu cầu kho bố trí người."),
  ("To use the delivery despite the temperature", False,
   "Bà nói ngay từ đầu là không dùng được.")])]),

("""W: Your car's ready, but it's not the news you were hoping for. The noise
   isn't the wheel bearing — it's the differential.
M: Which means what, in money?
W: A rebuilt unit fitted is around eleven hundred. A second-hand one is about
   six, but I'd only give you three months on it.
M: The car's worth maybe two and a half thousand.
W: I know. I'm not going to tell you what to do, but I will say the rest of it
   is sound — the brakes and the tyres have plenty left, and the body's clean
   for its age.
M: Can you leave it as it is for now? I need to think, and I'd rather not
   decide standing here.
W: Of course. There's no charge for the inspection.""", 2, [

 ("Who most likely is the woman?", Q.LC_SPEAKER_ROLE, "lc_speaker_role", 0.35, [
  ("A vehicle mechanic", True,
   "Bà kiểm tra xe, chẩn đoán hỏng hóc và báo giá sửa chữa."),
  ("An insurance assessor", False,
   "Không có chi tiết nào về bảo hiểm hay bồi thường."),
  ("A used-car salesperson", False,
   "Bà sửa xe của ông chứ không bán xe cho ông."),
  ("A driving instructor at a local school", False,
   "Không có chi tiết nào về việc dạy lái.")]),

 ("What does the woman say about the second-hand part?", Q.LC_DETAIL,
  "lc_detail", 0.55, [
  ("It costs less but carries a short guarantee", True,
   "Khoảng sáu trăm thay vì mười một trăm, nhưng chỉ bảo hành ba tháng."),
  ("It is not available for this particular model of car", False,
   "Bà đưa ra như một lựa chọn có thật."),
  ("It would take three months to obtain", False,
   "Ba tháng là thời hạn bảo hành, không phải thời gian chờ."),
  ("It would cost more than a rebuilt unit", False,
   "Ngược lại — rẻ hơn khoảng năm trăm.")]),

 ("What does the man decide to do?", Q.LC_NEXT_ACTION, "lc_next_action",
  0.50, [
  ("Delay the decision until he has considered it", True,
   "Ông xin để nguyên xe và nói không muốn quyết ngay tại chỗ."),
  ("Have the second-hand part fitted today", False,
   "Ông chưa chọn phương án nào."),
  ("Sell the car to the garage where it was inspected", False,
   "Không ai nhắc tới việc bán xe."),
  ("Pay for the inspection before leaving", False,
   "Bà nói việc kiểm tra không tính phí.")])]),

("""M1: The lunchtime figures are the thing I can't explain. Evening trade is up
    nine per cent, but lunch has fallen every month since March.
W:  I don't think it's a mystery. The office block opposite emptied in
    February. That was three hundred people who could walk here in a minute.
M1: I'd assumed the new menu was the problem.
W:  The menu changed in April. The decline started in March.
M2: So we're chasing customers who no longer work nearby.
W:  Which is why I'd stop discounting at lunch. It hasn't moved the numbers and
    it's costing us on the covers we do get. I'd put that money into the
    evening, where people are actually coming.""", 3, [

 ("What are the speakers discussing?", Q.LC_GIST, "lc_gist", 0.45, [
  ("A fall in trade at a particular time of day", True,
   "Cả đoạn xoay quanh doanh thu buổi trưa giảm liên tục từ tháng Ba."),
  ("A plan to open a second restaurant nearby", False,
   "Không có chi tiết nào về mở thêm cơ sở."),
  ("Complaints that customers made about the menu", False,
   "Thực đơn được nhắc tới nhưng không có khiếu nại nào."),
  ("A rise in the cost of ingredients since March", False,
   "Chi phí nguyên liệu không được nhắc tới.")]),

 ("How does the woman show the menu is not the cause?", Q.LC_DETAIL,
  "lc_detail", 0.65, [
  ("The decline began before the menu was changed", True,
   "Sụt giảm từ tháng Ba, thực đơn đổi tháng Tư — nguyên nhân không thể sau kết quả."),
  ("Customers said they preferred the new dishes", False,
   "Không có phản hồi nào của khách được nêu."),
  ("Evening trade rose sharply after the menu was changed", False,
   "Bà không dùng lập luận này; mốc thời gian mới là bằng chứng."),
  ("The old menu had exactly the same problem", False,
   "Không có so sánh nào giữa hai thực đơn.")]),

 ("What does the woman recommend?", Q.LC_NEXT_ACTION, "lc_next_action",
  0.55, [
  ("Ending the lunchtime discounts and spending the money elsewhere", True,
   "Bà đề nghị bỏ giảm giá buổi trưa và chuyển khoản đó sang buổi tối."),
  ("Increasing the discounts offered at lunchtime", False,
   "Bà nói giảm giá không có tác dụng và đang gây tốn kém."),
  ("Closing the restaurant at lunchtime altogether", False,
   "Bà không đề nghị đóng cửa buổi trưa."),
  ("Advertising to the offices in the block across the road", False,
   "Toà nhà đó đã trống từ tháng Hai.")])]),

("""W: I've read the draft. It's thorough, but I don't think a councillor will
   get past page two.
M: It's forty pages because the objections were detailed.
W: I'm not asking you to cut the detail. I'm asking you to move it. Put a
   single page at the front that says what you want them to decide and why.
   Everything else becomes an appendix.
M: That feels like hiding the work.
W: It's the opposite. At the moment the argument is on page nineteen, and I
   only found it because I was looking. If I hadn't been, I'd have concluded
   the report didn't have one.
M: All right. Can you mark the paragraph you think should be at the front?
W: I'll do it this afternoon.""", 2, [

 ("What does the woman suggest about the report?", Q.LC_GIST, "lc_gist",
  0.55, [
  ("Its key argument should be moved to the beginning", True,
   "Bà đề nghị đưa một trang tóm tắt lên đầu, phần còn lại thành phụ lục."),
  ("It should be shortened to fewer than twenty pages", False,
   "Bà không nói gì về số trang; vấn đề là thứ tự trình bày chứ không phải độ dài."),
  ("It contains too much detail about the objections", False,
   "Bà nói rõ không yêu cầu cắt chi tiết, chỉ yêu cầu chuyển vị trí."),
  ("It should be rewritten by somebody else entirely", False,
   "Bà đề nghị sửa cấu trúc, không đề nghị đổi người viết.")]),

 ("Why does the woman mention page nineteen?", Q.LC_INFERENCE, "lc_inference",
  0.70, [
  ("To show that the main point is currently hard to find", True,
   "Bà nói chỉ tìm ra lập luận vì đang chủ động tìm — nếu không thì đã bỏ sót."),
  ("To point out a factual error on that particular page", False,
   "Bà không nói trang đó có lỗi."),
  ("To suggest that the report should end there", False,
   "Bà không nói gì về độ dài phần cuối."),
  ("To praise the quality of the writing in that section", False,
   "Bà không khen đoạn đó, chỉ nói nó khó tìm.")]),

 ("What will the woman do next?", Q.LC_NEXT_ACTION, "lc_next_action", 0.40, [
  ("Identify the paragraph that should come first", True,
   "Người đàn ông nhờ bà đánh dấu đoạn văn, bà nhận lời làm chiều nay."),
  ("Write the one-page summary herself", False,
   "Bà chỉ đánh dấu; việc viết vẫn thuộc về ông."),
  ("Send the report to the councillors directly", False,
   "Không ai nói tới việc gửi đi."),
  ("Move the detailed sections of the draft into an appendix", False,
   "Đó là việc ông sẽ làm sau khi bà chỉ đoạn.")])]),
]


def main() -> int:
    groups, idx = [], 0
    for script, spk, rows in CONVERSATIONS:
        g, idx = build_group(idx, script, spk, rows)
        groups.append(g)

    n_q = sum(len(g.questions) for g in groups)
    print(f"Part 3: {len(groups)} hội thoại, {n_q} câu")
    for g in groups:
        w = len(g.audio.script.split())
        if not (80 <= w <= 170):
            print(f"  ⚠ kịch bản {w} từ, ngoài 80–170")
    for w in report_bias(groups):
        print(f"  ⚠ {w}")
    acc = collections.Counter(g.audio.accent.value for g in groups)
    print("  giọng: " + "  ".join(f"{k}={v}" for k, v in acc.most_common()))
    types = collections.Counter(q.question_type.value
                                for g in groups for q in g.questions)
    print(f"  question_type: {dict(types.most_common())}\n")

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_listening_part3_001", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
