#!/usr/bin/env python3
"""Part 3 batch 002 — 6 hội thoại còn lại, khép Part 3 ở 39 câu.

Đề thật có 2–3 câu Part 3 dạng "nhìn bảng/biểu đồ". Ở đây chưa làm được:
PART_RULES cho Part 3 là 0 passage, còn hình thì phải qua `image_url` mà chưa có
nguồn ảnh hợp pháp (blocker B6). Hội thoại cuối vì vậy đọc bảng giá thành lời —
vẫn là câu hỏi chi tiết có thật, chỉ không phải dạng đồ hoạ.

    python generators/gen_listening_part3_002.py
"""

from __future__ import annotations

import collections
import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from authoring import report_bias  # noqa: E402
from gen_listening_part3 import build_group  # noqa: E402
from guarded_write import guarded_write_batch  # noqa: E402
from schemas import BatchMetadata, ExamBatch, ModuleType, QuestionType  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "output" / "exams" / "bank" / "listening" / "exam_listening_part3_002.json"
GENERATED_BY = "claude-opus-5"
Q = QuestionType

CONVERSATIONS: list[tuple[str, int, list[tuple]]] = [

("""M: Ms Boateng, I've looked at the quote. The figure isn't the problem — it's
   the timing. You've got the whole roof coming off in one go.
W: That's how we normally do it. It's cheaper and it's faster.
M: We've got exam candidates in that building from the ninth of May.
W: Ah. Then we'd have to work in three sections instead, and cover each one
   overnight. That adds about nine days and roughly four thousand.
M: What if we started in the second week of April?
W: Three weeks isn't enough for the whole roof, even in one go. We'd be
   half-open when your exams started, which is the worst of both.""", 2, [

 ("What is the man's concern about the proposal?", Q.LC_GIST, "lc_gist", 0.50, [
  ("The work would clash with examinations in the building", True,
   "Ông nói rõ tiền không phải vấn đề, mà là thời điểm trùng kỳ thi từ 9/5."),
  ("The quoted price is higher than he expected", False,
   "Ông nói thẳng con số không phải vấn đề."),
  ("The roofing company has too few workers available", False,
   "Không có chi tiết nào về nhân lực nhà thầu."),
  ("The building would have to be closed off for a full three weeks", False,
   "Ba tuần là khoảng thời gian ông đề xuất, không phải thời gian đóng cửa.")]),

 ("What would the sectioned approach involve?", Q.LC_DETAIL, "lc_detail",
  0.55, [
  ("A longer schedule and a higher cost", True,
   "Bà nói thêm khoảng chín ngày và khoảng bốn nghìn."),
  ("Covering the whole roof every night", False,
   "Chỉ che từng phần đang làm dở, không che toàn bộ mái."),
  ("Starting the work in the second week of April", False,
   "Đó là phương án của người đàn ông, không phải phần của cách chia đoạn."),
  ("Using a different material for the new roof", False,
   "Không có chi tiết nào về vật liệu.")]),

 ("Why does the woman reject the April start?", Q.LC_INFERENCE, "lc_inference",
  0.70, [
  ("The roof would still be open when the exams began", True,
   "Ba tuần không đủ, nên đến kỳ thi mái vẫn dở dang — bà gọi đó là tệ nhất."),
  ("Her team is already booked during April", False,
   "Bà không nói gì về lịch của đội thợ."),
  ("The weather in April is usually unsuitable", False,
   "Không có chi tiết nào về thời tiết."),
  ("The total cost would rise by roughly four thousand pounds", False,
   "Bốn nghìn là chi phí của cách chia đoạn, không phải của việc bắt đầu sớm.")])]),

("""W1: I've been asked why we're replacing a system that works.
M:  It does work. It also runs on a database version that stops receiving
    security patches in November.
W1: That's the part nobody put in the paper.
M:  I did put it in. It's in the risk annexe.
W2: Which is where things go to be ignored. If the argument is "unsupported
    after November", that belongs in the first paragraph, not the annexe.
M:  Fair. I'll move it.
W2: And take out the cost comparison table while you're there. It invites an
    argument about pennies when the real point is that we have no choice.""", 3, [

 ("What are the speakers discussing?", Q.LC_GIST, "lc_gist", 0.50, [
  ("How to present the case for replacing a computer system", True,
   "Cả đoạn bàn cách trình bày lập luận trong tài liệu, không bàn có thay hay không."),
  ("Whether the existing computer system should be replaced at all", False,
   "Việc thay đã được chấp nhận; họ chỉ bàn cách viết cho thuyết phục."),
  ("How much the new database will cost to install", False,
   "Bảng so sánh chi phí bị đề nghị bỏ đi, không được bàn tới."),
  ("When the security patches will next be released", False,
   "Tháng Mười Một là lúc NGỪNG nhận bản vá.")]),

 ("What does the man say about the security issue?", Q.LC_DETAIL, "lc_detail",
  0.55, [
  ("He had already included it in the document", True,
   "Ông nói đã đưa vào, nằm ở phụ lục rủi ro."),
  ("He only learned about it recently himself", False,
   "Ông biết rõ và đã viết ra."),
  ("He does not think it is a serious problem", False,
   "Ông nêu nó ra như lý do chính để thay hệ thống."),
  ("He will ask the supplier to extend its support period", False,
   "Không ai đề cập tới việc thương lượng với nhà cung cấp.")]),

 ("What does the second woman recommend removing?", Q.LC_NEXT_ACTION,
  "lc_next_action", 0.60, [
  ("The table comparing costs", True,
   "Bà nói bỏ bảng so sánh chi phí vì nó kéo tranh luận sang chuyện tiền lẻ."),
  ("The risk annexe at the end of the paper", False,
   "Bà đề nghị chuyển nội dung LÊN đầu, không đề nghị xoá phụ lục."),
  ("The first paragraph of the document", False,
   "Đoạn đầu là nơi bà muốn ĐƯA nội dung vào."),
  ("The reference to the November deadline", False,
   "Đó chính là lập luận bà muốn làm nổi bật.")])]),

("""M1: Right — the van's booked for the eleventh, so everything on this floor
    needs to be in crates by close of play on the tenth.
W:  That's the part I wanted to raise. The server cabinet can't just go in a
    crate. It needs an engineer to power it down properly, and the earliest
    they can come is the twelfth.
M1: Then the van comes back a second time.
M2: Or the cabinet stays and we run the two sites in parallel for a fortnight.
W:  We can't. The lease on this floor ends on the fifteenth.
M1: Then it's the second van. I'll ring them this afternoon and see what a
    return trip costs.""", 3, [

 ("What are the speakers mainly arranging?", Q.LC_GIST, "lc_gist", 0.45, [
  ("An office move to a new location", True,
   "Đóng thùng, thuê xe tải, hết hạn thuê nhà — đây là một cuộc chuyển văn phòng."),
  ("The purchase of new computer equipment", False,
   "Tủ máy chủ là thiết bị đang có, không phải hàng mua mới."),
  ("The renewal of a lease on an office floor", False,
   "Hợp đồng thuê sắp HẾT, và đó là lý do không hoãn được."),
  ("A repair to a server that has broken down", False,
   "Kỹ sư đến để tắt máy đúng quy trình, không phải để sửa.")]),

 ("What problem does the woman raise?", Q.LC_DETAIL, "lc_detail", 0.55, [
  ("The server cabinet cannot be ready in time for the van", True,
   "Kỹ sư sớm nhất là ngày 12, trong khi xe tải đến ngày 11."),
  ("The van has been booked for the wrong date", False,
   "Ngày xe tải đúng theo kế hoạch; vấn đề nằm ở tủ máy chủ."),
  ("There are not enough crates to pack up the whole floor", False,
   "Không có chi tiết nào về số lượng thùng."),
  ("The engineer has cancelled the appointment", False,
   "Kỹ sư vẫn đến, chỉ là muộn hơn một ngày.")]),

 ("What will the man do next?", Q.LC_NEXT_ACTION, "lc_next_action", 0.45, [
  ("Find out the cost of a second collection", True,
   "Ông nói sẽ gọi trong chiều nay để hỏi giá chuyến quay lại."),
  ("Ask the engineer to come a day earlier", False,
   "Ngày 12 đã là sớm nhất, và không ai đề nghị đổi."),
  ("Extend the lease on the current floor", False,
   "Người phụ nữ nói rõ hợp đồng kết thúc ngày 15."),
  ("Run both of the sites in parallel for a fortnight", False,
   "Phương án này bị loại vì hết hạn thuê.")])]),

("""W: Thanks for staying behind. The reason your marks dropped isn't the
   content — it's that you're answering a different question from the one
   that's set.
M: I thought I was covering everything.
W: You are. That's the problem. Question four asked you to evaluate one policy.
   You described four of them, accurately, and never said which worked.
M: Because I didn't want to be wrong.
W: A defended wrong answer scores more than an undefended right one here. The
   marks are for the defending.
M: So if I picked the weaker policy and argued it properly?
W: You'd pass comfortably. Try rewriting question four tonight — pick one, and
   don't mention the others at all.""", 2, [

 ("Who most likely are the speakers?", Q.LC_SPEAKER_ROLE, "lc_speaker_role",
  0.40, [
  ("A tutor and a student", True,
   "Bà chấm bài, giải thích cách tính điểm và giao bài viết lại."),
  ("A manager and a new employee", False,
   "Nội dung xoay quanh bài thi và điểm số, không phải công việc."),
  ("Two candidates preparing for an examination", False,
   "Chỉ một người làm bài; người kia đánh giá bài đó."),
  ("A journalist and a policy researcher", False,
   "Chính sách là chủ đề bài thi, không phải nghề của họ.")]),

 ("What mistake did the man make?", Q.LC_DETAIL, "lc_detail", 0.60, [
  ("He described several policies instead of judging one", True,
   "Câu hỏi yêu cầu đánh giá một chính sách; anh mô tả bốn cái mà không kết luận."),
  ("He wrote at length about a policy not on the syllabus", False,
   "Bà nói nội dung không phải vấn đề."),
  ("He ran out of time before finishing the paper", False,
   "Không có chi tiết nào về thời gian làm bài."),
  ("He made several factual errors in his answer", False,
   "Bà nói anh mô tả chính xác — sai sót không nằm ở dữ kiện.")]),

 ("What does the woman advise him to do?", Q.LC_NEXT_ACTION, "lc_next_action",
  0.55, [
  ("Rewrite the answer defending a single choice", True,
   "Bà bảo viết lại câu bốn, chọn một chính sách và không nhắc tới các chính sách khác."),
  ("Read more widely about all four policies", False,
   "Bà nói vấn đề không nằm ở lượng kiến thức."),
  ("Choose the strongest policy in every answer", False,
   "Bà nói ngay cả chính sách yếu hơn cũng được, miễn lập luận tốt."),
  ("Ask for the paper to be marked a second time", False,
   "Không ai nhắc tới việc phúc khảo.")])]),

("""M: I'm calling about the delivery slot on Thursday. It says between seven in
   the morning and seven at night, which isn't much use to me.
W: I can see why. Let me check what the driver's route looks like — one moment.
   Right, you're the fourth drop, so realistically before eleven.
M: Can you put that in writing? The last time I was told "before eleven" and it
   arrived at four.
W: I can't guarantee a time, and I won't pretend otherwise. What I can do is
   put a note on the order asking the driver to ring you when he leaves the
   depot. That gives you about forty minutes.
M: That's more useful than a guarantee I don't believe.""", 2, [

 ("Why is the man calling?", Q.LC_GIST, "lc_gist", 0.35, [
  ("To ask about the timing of a delivery", True,
   "Ông gọi vì khung giờ giao hàng 7 giờ sáng đến 7 giờ tối quá rộng."),
  ("To complain about an item that was damaged", False,
   "Không có chi tiết nào về hàng hoá bị hỏng."),
  ("To cancel an order he placed on Thursday", False,
   "Thứ Năm là ngày giao, và ông không huỷ đơn."),
  ("To change the address the order will go to", False,
   "Địa chỉ không được nhắc tới.")]),

 ("What does the woman refuse to do?", Q.LC_DETAIL, "lc_detail", 0.60, [
  ("Promise a specific delivery time", True,
   "Bà nói thẳng không thể bảo đảm giờ và không muốn giả vờ là có thể."),
  ("Check the driver's route for that day", False,
   "Bà đã kiểm tra và cho biết ông là điểm giao thứ tư."),
  ("Add any note at all to the order", False,
   "Bà chủ động đề xuất ghi chú nhờ tài xế gọi."),
  ("Tell him how many drops come before his", False,
   "Bà nói rõ ông là điểm thứ tư.")]),

 ("What will happen on Thursday?", Q.LC_NEXT_ACTION, "lc_next_action", 0.55, [
  ("The driver will telephone before setting out", True,
   "Ghi chú yêu cầu tài xế gọi khi rời kho, cho ông khoảng bốn mươi phút."),
  ("The delivery will be guaranteed before eleven", False,
   "Trước mười một giờ chỉ là ước tính, và bà từ chối bảo đảm."),
  ("The order will be moved to a different day", False,
   "Không ai đề nghị đổi ngày."),
  ("The man will collect the order from the depot", False,
   "Không có chi tiết nào về việc tự đến lấy.")])]),

("""W1: Before we book, I want to be clear which tier we're on. The pricing page
    charges by seat, but there's a floor.
M:  There is. One to nineteen seats is twenty-four pounds each, but anything
    under twenty is billed as twenty anyway.
W1: We're at fourteen.
W2: Which means going up to twenty costs us nothing extra — and twenty to
    forty-nine is only eighteen a seat.
M:  Correct. The next break is at fifty, where it drops to fifteen, and again
    at a hundred.
W1: We won't see fifty this year. Let's take twenty and stop worrying about
    who has an account.""", 3, [

 ("What rate per seat will the speakers pay?", Q.LC_DETAIL, "lc_detail",
  0.70, [
  ("Eighteen pounds", True,
   "Họ chốt lấy 20 chỗ, rơi vào bậc 20–49 chỗ — mười tám bảng mỗi chỗ."),
  ("Twenty-four pounds for each of the seats", False,
   "Đó là bậc 1–19 chỗ, nhưng dưới 20 vẫn bị tính thành 20 nên bậc này không áp dụng."),
  ("Fifteen pounds for each of the seats", False,
   "Mười lăm bảng là bậc từ 50 chỗ; họ nói năm nay không đạt tới 50."),
  ("Twelve pounds for each of the seats", False,
   "Mười hai bảng là bậc từ 100 chỗ trở lên, còn xa hơn nữa.")]),

 ("What does the second woman point out?", Q.LC_DETAIL, "lc_detail", 0.60, [
  ("Taking more seats than they need costs nothing", True,
   "Vì dưới 20 vẫn bị tính là 20, nên tăng từ 14 lên 20 không tốn thêm."),
  ("The company will reach fifty seats this year", False,
   "Người phụ nữ thứ nhất nói rõ năm nay không đạt tới 50."),
  ("The pricing page contains an error", False,
   "Không ai nói bảng giá sai."),
  ("Fourteen seats is more than they currently need", False,
   "Mười bốn là số chỗ hiện tại, không phải số dư thừa.")]),

 ("What do the speakers decide?", Q.LC_NEXT_ACTION, "lc_next_action", 0.50, [
  ("To buy twenty seats", True,
   "Họ chốt lấy 20 chỗ và thôi phải cân nhắc ai được cấp tài khoản."),
  ("To wait until they reach fifty seats", False,
   "Họ không định chờ, và năm nay cũng không tới 50."),
  ("To keep the current fourteen accounts", False,
   "Giữ 14 chỗ vẫn bị tính tiền như 20 nên họ không chọn."),
  ("To ask for a discount on the twenty-seat tier", False,
   "Không ai đề nghị thương lượng giá.")])]),
]

def main() -> int:
    groups, idx = [], 100
    for script, spk, rows in CONVERSATIONS:
        g, idx = build_group(idx, script, spk, rows)
        groups.append(g)

    n_q = sum(len(g.questions) for g in groups)
    print(f"Part 3 batch 002: {len(groups)} hội thoại, {n_q} câu")
    for g in groups:
        w = len(g.audio.script.split())
        if not (80 <= w <= 170):
            print(f"  ⚠ kịch bản {w} từ, ngoài 80–170")
    for w in report_bias(groups):
        print(f"  ⚠ {w}")
    acc = collections.Counter(g.audio.accent.value for g in groups)
    print("  giọng: " + "  ".join(f"{k}={v}" for k, v in acc.most_common()))
    print()

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_listening_part3_002", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
