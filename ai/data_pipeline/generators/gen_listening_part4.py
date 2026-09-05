#!/usr/bin/env python3
"""Part 4 — 10 bài nói một người, mỗi bài 3 câu hỏi.

Phủ đủ các dạng bài nói của đề thật: thông báo tại chỗ, tin nhắn thoại, quảng
cáo phát thanh, hướng dẫn tham quan, phát biểu mở đầu, bản tin, hướng dẫn thao
tác, thông báo hoãn chuyến.

Bài nói một người dài hơn hội thoại và không có ai hỏi lại, nên thông tin chỉ
xuất hiện MỘT lần — distractor vì thế nhắm vào chi tiết đứng cạnh chi tiết đúng,
đúng cách đề thật đánh bẫy.

Kịch bản viết mới hoàn toàn (§0.4). Không có audio_url — §Phase 8 cấm URL giả.

    python generators/gen_listening_part4.py
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
OUT = ROOT / "output" / "exams" / "bank" / "listening" / "exam_listening_part4_001.json"
GENERATED_BY = "claude-opus-5"
LABELS = ["A", "B", "C", "D"]
Q = QuestionType

ACCENT_CYCLE = [Accent.US, Accent.UK, Accent.US, Accent.AU, Accent.US,
                Accent.CA, Accent.US, Accent.UK, Accent.US, Accent.AU]

TALKS: list[tuple[str, list[tuple]]] = [

("""Good morning, and thank you for your patience. This is a service update for
passengers waiting on platform four.

The nine-forty to Draymouth is currently showing a delay of thirty minutes. The
cause is not at this station — a freight train has broken down south of
Ashcombe, and every service on that line is being held while it is moved.

I want to be straight with you about what that means. Once the line reopens, the
trains that were held will go first, in the order they were stopped. Our
nine-forty is fourth in that queue. So while the board says thirty minutes, my
own expectation is closer to fifty.

Passengers travelling only as far as Ashcombe may find the bus from stand six
quicker this morning. It leaves at ten past and takes about forty minutes.
Anyone holding a ticket for the nine-forty may use it on that bus without paying
again.""",
 [("What is the main purpose of the announcement?", Q.LC_GIST, "lc_gist", 0.40, [
   ("To explain the reason for a delay", True,
    "Toàn bộ thông báo giải thích vì sao chuyến 9:40 bị chậm và chậm bao lâu."),
   ("To announce that a platform has changed", False,
    "Sân ga bốn vẫn giữ nguyên, không có thay đổi nào."),
   ("To advertise a new bus route to Ashcombe", False,
    "Xe buýt là phương án thay thế, không phải tuyến mới được quảng cáo."),
   ("To ask passengers to buy tickets in advance", False,
    "Không có yêu cầu nào về việc mua vé.")]),

  ("Why does the speaker expect a longer delay than the board shows?",
   Q.LC_INFERENCE, "lc_inference", 0.70, [
   ("Several trains will be let through before his", True,
    "Chuyến 9:40 đứng thứ tư trong hàng đợi khi đường ray mở lại."),
   ("The freight train cannot be moved at all", False,
    "Tàu hàng đang được di chuyển, chỉ là mất thời gian."),
   ("The information board is not working correctly", False,
    "Bảng vẫn hoạt động; vấn đề là nó chưa tính tới hàng đợi."),
   ("Another service has been cancelled this morning", False,
    "Không có chuyến nào bị huỷ.")]),

  ("What are Ashcombe passengers told?", Q.LC_DETAIL, "lc_detail", 0.55, [
   ("They may use their existing ticket on a bus", True,
    "Vé chuyến 9:40 dùng được trên xe buýt mà không phải trả thêm."),
   ("They should wait for the next train instead", False,
    "Người thông báo gợi ý xe buýt sẽ nhanh hơn."),
   ("They must buy a new ticket at stand six", False,
    "Thông báo nói rõ không phải trả tiền lần nữa."),
   ("They will be given a refund at the ticket office", False,
    "Không ai nhắc tới việc hoàn tiền.")])]),

("""Hello, this is Rasheeda Farooqi calling from Millgate Veterinary. This message
is for Mr Delacroix about Tobias.

The blood results came back this morning and they're better than we expected.
His kidney values have come down into the normal range, which means we can stop
the twice-daily tablets.

Now, there's one thing I want to be careful about. The improvement is almost
certainly because of the diet change, not the tablets — so please do keep him on
the prescription food even though he's off the medication. I've seen a few cases
where the tablets stopped and the food stopped at the same time, and the numbers
went straight back up.

There's no need to bring him in for another six weeks. If you'd like to talk any
of this through, I'm here until six today and all day Thursday.""",
 [("Why is the speaker calling?", Q.LC_GIST, "lc_gist", 0.40, [
   ("To give the results of a test", True,
    "Bà báo kết quả xét nghiệm máu và hệ quả của nó."),
   ("To arrange an appointment for next week", False,
    "Bà nói không cần đưa tới trong sáu tuần."),
   ("To order more of a prescription medicine", False,
    "Ngược lại — bà cho dừng thuốc."),
   ("To ask about a bill that has not been paid", False,
    "Không có chi tiết nào về thanh toán.")]),

  ("What does the speaker ask Mr Delacroix to continue doing?", Q.LC_DETAIL,
   "lc_detail", 0.55, [
   ("Feeding the animal the special food", True,
    "Bà nhấn mạnh giữ thức ăn kê đơn dù đã dừng thuốc."),
   ("Giving the tablets twice each day", False,
    "Thuốc là thứ được DỪNG."),
   ("Bringing the animal in every six weeks", False,
    "Sáu tuần là lần khám tiếp theo, không phải lịch định kỳ."),
   ("Recording the kidney values at home", False,
    "Không có yêu cầu nào về việc tự theo dõi.")]),

  ("Why does the speaker mention other cases?", Q.LC_INFERENCE, "lc_inference",
   0.70, [
   ("To explain why one change should not be made", True,
    "Bà kể các ca dừng cả thuốc lẫn thức ăn rồi chỉ số xấu lại — để ngăn lặp lại."),
   ("To show that the treatment rarely works", False,
    "Ca này kết quả tốt hơn mong đợi."),
   ("To suggest that a second opinion should be sought", False,
    "Bà không đề nghị hỏi ý kiến ai khác."),
   ("To warn that the animal may need surgery", False,
    "Không có chi tiết nào về phẫu thuật.")])]),

("""Before we begin the tour, three things about this building that the guidebook
gets wrong.

First, it was never a monastery. The arches at the east end look monastic and
that is where the story comes from, but the earliest record we have is a wool
warehouse of 1487.

Second, the stained glass is not medieval. It was installed in 1892 by a
Manchester firm, copying a window in Chartres. It is beautiful, and it is a
copy, and there is no shame in either.

Third — and this is the one people mind — the tunnel does not exist. Every town
of this age has a tunnel story. Ours has been searched for three times, most
recently with ground radar in 2019, and there is nothing there.

Now, if you'll follow me through the arch on the left, we'll start with the
warehouse floor, which is the oldest part you can still walk on.""",
 [("Where does the talk most likely take place?", Q.LC_SPEAKER_ROLE,
   "lc_speaker_role", 0.45, [
   ("At a historic building open to visitors", True,
    "Người nói dẫn khách tham quan, nhắc sách hướng dẫn và mời đi theo."),
   ("At a meeting of local historians", False,
    "Đây là buổi tham quan có hướng dẫn, không phải hội thảo."),
   ("At a shop selling stained glass windows", False,
    "Kính màu là hiện vật trong toà nhà, không phải hàng bán."),
   ("At a construction site that is being excavated", False,
    "Việc tìm đường hầm đã kết thúc từ 2019.")]),

  ("What does the speaker say about the stained glass?", Q.LC_DETAIL,
   "lc_detail", 0.55, [
   ("It is a nineteenth-century copy of another window", True,
    "Lắp năm 1892, sao chép một cửa sổ ở Chartres."),
   ("It was brought here from a monastery nearby", False,
    "Toà nhà chưa bao giờ là tu viện."),
   ("It is the oldest part of the building", False,
    "Phần cổ nhất là sàn kho len, không phải kính."),
   ("It was made in Chartres in the medieval period", False,
    "Do một xưởng ở Manchester làm, và chỉ là bản sao.")]),

  ("What is the speaker's attitude to the tunnel story?", Q.LC_INFERENCE,
   "lc_inference", 0.70, [
   ("He is certain it is untrue", True,
    "Ông nói đã tìm ba lần, gần nhất bằng radar xuyên đất, và không có gì."),
   ("He believes it will be proved one day", False,
    "Ông kết luận dứt khoát là không có đường hầm."),
   ("He thinks it is too dangerous to investigate", False,
    "Việc tìm kiếm đã được tiến hành, không có gì nguy hiểm được nêu."),
   ("He has not heard the story before today", False,
    "Ông biết rõ và nói mọi thị trấn cùng tuổi đều có chuyện tương tự.")])]),

("""Right — everyone's got the handout, so I'll keep this short.

We're changing how expenses are claimed, and I know that sentence makes people
groan. The change is that you no longer need a manager's signature for anything
under fifty pounds. Photograph the receipt, upload it, done.

The reason isn't generosity. We looked at last year's claims and found that
ninety-one per cent were under fifty pounds, and every one of them was approved.
The signature was costing managers about four hundred hours a year and rejecting
nothing.

Above fifty pounds, nothing changes at all. Same form, same signature.

One caution. Because approval is automatic, the checking moves to the end — the
finance team will audit a random sample each month. If something is wrong, it
will be found later rather than sooner, and that is a worse conversation to
have. So please read the categories properly before you submit.""",
 [("What is the speaker mainly announcing?", Q.LC_GIST, "lc_gist", 0.45, [
   ("A simpler process for claiming small amounts", True,
    "Dưới năm mươi bảng không còn cần chữ ký quản lý."),
   ("An increase in the amount staff may claim", False,
    "Mức tiền không đổi; chỉ quy trình phê duyệt đổi."),
   ("A new team that will handle all expense claims", False,
    "Đội tài chính đã có sẵn và chỉ nhận thêm việc kiểm mẫu."),
   ("A reduction in the number of managers", False,
    "Không có chi tiết nào về nhân sự quản lý.")]),

  ("What reason does the speaker give for the change?", Q.LC_DETAIL,
   "lc_detail", 0.60, [
   ("The old approval step was not rejecting anything", True,
    "91% khoản chi dưới năm mươi bảng và tất cả đều được duyệt."),
   ("Managers asked for more responsibility", False,
    "Quản lý được GIẢM việc, không phải xin thêm."),
   ("The finance team was too small to cope", False,
    "Đội tài chính nhận thêm việc kiểm mẫu chứ không phải quá tải."),
   ("Staff had complained about the fifty-pound limit", False,
    "Không có khiếu nại nào được nhắc tới.")]),

  ("What does the speaker warn listeners about?", Q.LC_INFERENCE,
   "lc_inference", 0.65, [
   ("Mistakes will now be discovered after payment", True,
    "Kiểm tra chuyển về sau, nên sai sót bị phát hiện muộn hơn."),
   ("Claims over fifty pounds will be refused", False,
    "Trên năm mươi bảng không có gì thay đổi."),
   ("Receipts will no longer need to be kept", False,
    "Vẫn phải chụp và tải hoá đơn lên."),
   ("The new system may well be withdrawn next year", False,
    "Không có chi tiết nào về việc rút lại.")])]),

("""This is the ten o'clock news on Harborne Sound.

The city council has voted to keep the Saturday market on Nelson Square for at
least another three years. The vote was eighteen to fifteen, closer than either
side expected.

Traders had argued that the proposed move to the retail park would have cut
their weekday passing trade, since many of the same customers shop in the square
during the week. Councillors supporting the move pointed to congestion and the
cost of closing the road each Saturday.

The compromise agreed last night keeps the market where it is but ends the road
closure. From September, stalls will be set out along the pavement on the north
side only, which reduces the number of pitches from sixty-one to about forty.

The market association says it will now have to decide how those pitches are
allocated, and that some traders will lose their place.""",
 [("What did the council decide?", Q.LC_GIST, "lc_gist", 0.50, [
   ("The market will stay in its current location", True,
    "Hội đồng bỏ phiếu giữ chợ ở Nelson Square thêm ít nhất ba năm."),
   ("The market will move to the retail park", False,
    "Phương án di dời đã bị bác."),
   ("The market will close down completely", False,
    "Không ai đề xuất đóng chợ."),
   ("The market will open on weekdays as well", False,
    "Ngày thường được nhắc tới ở khía cạnh khách quen, không phải lịch họp chợ.")]),

  ("What will change from September?", Q.LC_DETAIL, "lc_detail", 0.55, [
   ("The road will no longer be closed for the market", True,
    "Thoả hiệp giữ chợ nhưng chấm dứt việc chặn đường."),
   ("The market will run for three years only", False,
    "Ba năm là thời hạn tối THIỂU."),
   ("Stalls will be set out on both sides of Nelson Square", False,
    "Chỉ vỉa hè phía bắc."),
   ("The number of pitches will rise to sixty-one", False,
    "Số quầy GIẢM từ 61 xuống khoảng 40.")]),

  ("What problem does the report suggest will follow?", Q.LC_INFERENCE,
   "lc_inference", 0.70, [
   ("Some traders will not be given a pitch", True,
    "Từ 61 xuống 40 quầy, và hiệp hội nói một số người sẽ mất chỗ."),
   ("Customers will stop coming to the square", False,
    "Không có dự đoán nào về lượng khách."),
   ("The council will hold a second vote", False,
    "Cuộc bỏ phiếu đã kết thúc và có thoả hiệp."),
   ("The road will become more congested", False,
    "Bỏ chặn đường là nhằm giảm ùn tắc.")])]),

("""Thank you all for coming, and a particular welcome to those who have travelled.

I have been asked to say a few words about Hyacinth before we begin. Thirty-one
years is a long time in one organisation, and it would be easy to list the
posts. I would rather say one thing about how she worked.

When Hyacinth took over the archive in 1998, it was three rooms of unsorted
boxes and a card index that stopped in 1974. She did not ask for more staff. She
asked for a photocopier and a year, and at the end of that year the collection
had a catalogue that we still use.

What I want the newer colleagues to take from this is not that she worked hard,
though she did. It is that she started with the thing that made everything else
possible, and she was willing to be invisible while she did it.

Hyacinth, we will miss you. The tea, I am told, is through the double doors.""",
 [("What is the purpose of the talk?", Q.LC_GIST, "lc_gist", 0.45, [
   ("To mark a colleague's departure", True,
    "Bài phát biểu tri ân một người sắp rời tổ chức sau 31 năm."),
   ("To introduce a new head of the archive", False,
    "Bà Hyacinth đang RA ĐI, không phải mới nhận việc."),
   ("To announce that the archive will be reorganised", False,
    "Việc sắp xếp lại đã diễn ra từ 1998."),
   ("To ask for volunteers to catalogue a collection", False,
    "Danh mục đã hoàn thành và vẫn đang được dùng.")]),

  ("What did Hyacinth ask for in 1998?", Q.LC_DETAIL, "lc_detail", 0.50, [
   ("A photocopier and twelve months", True,
    "Bà xin một máy photocopy và một năm, không xin thêm người."),
   ("Three more rooms for the collection", False,
    "Ba căn phòng là hiện trạng lúc đó, không phải yêu cầu."),
   ("A larger team of assistants", False,
    "Bài nói rõ bà KHÔNG xin thêm nhân sự."),
   ("A new card index system to replace the old one", False,
    "Mục lục cũ dừng ở 1974; bà làm danh mục mới nhưng đó là kết quả, không phải điều bà xin.")]),

  ("What lesson does the speaker draw for newer colleagues?", Q.LC_INFERENCE,
   "lc_inference", 0.75, [
   ("Groundwork matters even when nobody notices it", True,
    "Ông nhấn mạnh bà bắt đầu từ việc nền tảng và chấp nhận không ai thấy."),
   ("Long service is the surest mark of a good career", False,
    "Ông nói rõ không muốn chỉ liệt kê các chức vụ."),
   ("Hard work is more important than planning", False,
    "Ông nói bài học KHÔNG phải là chăm chỉ."),
   ("Archives should be given more funding", False,
    "Không có lời kêu gọi nào về ngân sách.")])]),

("""Before you use the machine for the first time, two minutes of this will save
you an afternoon.

The tray at the back holds the blanks. It takes forty, and it will physically
take fifty, and that is where most jams come from. Forty means to the line, not
to the top.

The screen will ask you for a material. This is not cosmetic. Choosing the wrong
one changes the cutting speed, and on the thin acrylic the difference between
correct and one setting too fast is a cracked sheet rather than a cut one.

When the job finishes, the extraction fan runs on for ninety seconds. Do not
open the lid during that time, even though the machine looks finished. That
ninety seconds is what stops the fumes coming out at you.

Finally: if something goes wrong, the red button stops the head but leaves the
fan running. That is deliberate. Use it.""",
 [("Who is the talk most likely intended for?", Q.LC_SPEAKER_ROLE,
   "lc_speaker_role", 0.45, [
   ("People about to operate a machine for the first time", True,
    "Người nói mở đầu bằng 'trước khi bạn dùng máy lần đầu'."),
   ("Engineers who have come to repair the machine", False,
    "Nội dung là hướng dẫn vận hành, không phải sửa chữa."),
   ("Customers ordering cut acrylic sheets", False,
    "Người nghe là người tự vận hành máy."),
   ("Staff who have used the machine for years", False,
    "Đây là hướng dẫn cho người mới.")]),

  ("What does the speaker say causes most jams?", Q.LC_DETAIL, "lc_detail",
   0.60, [
   ("Loading more blanks than the tray should hold", True,
    "Khay chứa được 40 nhưng nhét vừa 50 — đó là nguồn kẹt phổ biến nhất."),
   ("Choosing the wrong material setting on the screen", False,
    "Chọn sai vật liệu làm nứt tấm, không gây kẹt."),
   ("Opening the lid before the fan stops", False,
    "Mở nắp sớm khiến khói thoát ra, không gây kẹt."),
   ("Pressing the red button during a job", False,
    "Nút đỏ là biện pháp an toàn được khuyến khích dùng.")]),

  ("Why must the lid stay shut for ninety seconds?", Q.LC_DETAIL, "lc_detail",
   0.55, [
   ("The fan is still clearing fumes", True,
    "Quạt hút chạy thêm 90 giây và đó là thứ giữ khói không thoát ra."),
   ("The cutting head is still moving", False,
    "Đầu cắt đã xong việc; quạt mới là thứ còn chạy."),
   ("The material needs time to cool down", False,
    "Bài không nhắc tới việc làm nguội."),
   ("The machine is saving the job settings", False,
    "Không có chi tiết nào về lưu cài đặt.")])]),

("""Good afternoon. This is a customer announcement for anyone who came in today
for the sale.

The sale is running, but not in the way the leaflet describes. The leaflet says
half price on all winter stock. What we can actually offer is half price on
winter coats and knitwear, and a third off winter boots.

That is our mistake, not the printer's, and I am sorry. Anyone who has travelled
here today specifically for the boots should come to the desk on the ground
floor. We will honour the half price on boots for today only, for anyone who
asks. We will not be doing it tomorrow, so please do speak to us before you
leave rather than afterwards.

Everything else in the leaflet is correct, including the extended opening hours
this Thursday.""",
 [("What problem is the announcement about?", Q.LC_GIST, "lc_gist", 0.45, [
   ("A printed leaflet gave incorrect discounts", True,
    "Tờ rơi ghi giảm nửa giá toàn bộ, nhưng thực tế chỉ áp dụng một phần."),
   ("The sale has been cancelled for today", False,
    "Đợt giảm giá vẫn đang diễn ra."),
   ("Some winter stock has sold out completely", False,
    "Không có chi tiết nào về hết hàng."),
   ("The shop will close earlier than advertised", False,
    "Giờ mở cửa kéo dài thứ Năm vẫn đúng như tờ rơi.")]),

  ("What is offered to customers who came for boots?", Q.LC_DETAIL, "lc_detail",
   0.55, [
   ("The advertised price, if they ask today", True,
    "Cửa hàng chấp nhận nửa giá cho giày, chỉ trong hôm nay và phải hỏi."),
   ("A third off any winter item in the shop", False,
    "Giảm một phần ba chỉ áp dụng cho giày mùa đông."),
   ("A refund of their travel costs to the shop", False,
    "Không ai nhắc tới chi phí đi lại."),
   ("A voucher to use on their next visit", False,
    "Ưu đãi chỉ có hiệu lực trong ngày hôm nay.")]),

  ("What does the speaker stress about the offer?", Q.LC_INFERENCE,
   "lc_inference", 0.65, [
   ("Customers must raise it before they leave", True,
    "Bà nhấn mạnh nói với nhân viên TRƯỚC khi ra về, không phải sau."),
   ("It will also be available tomorrow", False,
    "Bà nói rõ mai sẽ không áp dụng nữa."),
   ("It applies to every single item in the leaflet", False,
    "Chỉ giày mùa đông được xử lý riêng."),
   ("It requires the leaflet to be shown", False,
    "Không có yêu cầu nào về việc trình tờ rơi.")])]),

("""Thanks for dialling in. This is the weekly update for the Fenwick project.

Where we are: the survey work finished on Tuesday, four days ahead of the plan,
which is the first thing that has run early on this job.

Where we are not: the planning application. We were told six weeks. It has now
been eleven, and the case officer changed in week eight, which in practice means
somebody started again.

What I want to flag is that these two facts interact badly. The survey team is
now free, and we are paying to hold them. Holding them costs about two thousand
a week. Releasing them means a probable six-week wait to get them back, and we
will need them again the moment permission comes through.

I do not have a recommendation yet. I want a decision by Friday, and I would
rather it were a considered one than a fast one.""",
 [("What is the purpose of the update?", Q.LC_GIST, "lc_gist", 0.50, [
   ("To report progress and raise a difficulty", True,
    "Người nói báo phần chạy sớm, phần chậm, và nêu vấn đề cần quyết."),
   ("To announce that the project has been approved", False,
    "Giấy phép quy hoạch vẫn chưa có."),
   ("To introduce a new case officer to the team", False,
    "Việc đổi cán bộ thụ lý là nguyên nhân chậm, không phải nội dung giới thiệu."),
   ("To ask for more money for the survey work", False,
    "Khảo sát đã xong sớm; vấn đề là giữ hay giải tán đội.")]),

  ("What does the speaker say about the planning application?", Q.LC_DETAIL,
   "lc_detail", 0.60, [
   ("It has taken almost twice as long as promised", True,
    "Được hứa sáu tuần, nay đã mười một tuần."),
   ("It was refused in the eighth week", False,
    "Tuần thứ tám là lúc đổi cán bộ, không phải bị từ chối."),
   ("It will be decided by Friday this week", False,
    "Thứ Sáu là hạn cho quyết định của chính nhóm, không phải của cơ quan cấp phép."),
   ("It was submitted a full four days ahead of plan", False,
    "Bốn ngày sớm là của công việc khảo sát.")]),

  ("What decision does the speaker want made?", Q.LC_NEXT_ACTION,
   "lc_next_action", 0.65, [
   ("Whether to keep paying the survey team while waiting", True,
    "Giữ đội tốn hai nghìn một tuần; giải tán thì chờ sáu tuần mới gọi lại được."),
   ("Whether to withdraw the planning application entirely", False,
    "Không ai đề nghị rút hồ sơ."),
   ("Which company should carry out the survey", False,
    "Khảo sát đã hoàn thành."),
   ("When the project should be handed over", False,
    "Không có chi tiết nào về bàn giao.")])]),

("""If you are hearing this message, you have reached Kettleby Building Control
outside our opening hours.

I want to save you a call back where I can. Three things account for most of the
messages we get.

If you are asking whether your work needs approval, the flowchart on our website
answers that faster than we can. It is on the front page, not buried.

If you are chasing an inspection, we book those forty-eight hours ahead, not the
same day. Ringing in the morning for an afternoon visit will not work, however
urgent it feels.

And if your certificate has not arrived, they are issued eight working days
after the final inspection, and they go by post. Eight working days is nearly a
fortnight in real time.

If your question is none of those, leave your name, your site address and your
reference number. Without the reference number we cannot find you.""",
 [("What is the main purpose of the recorded message?", Q.LC_GIST, "lc_gist",
   0.50, [
   ("To answer the questions callers most often ask", True,
    "Người nói nêu ba việc chiếm phần lớn cuộc gọi để khỏi phải gọi lại."),
   ("To announce a change in the opening hours", False,
    "Ngoài giờ làm việc là bối cảnh, không phải nội dung thông báo."),
   ("To explain why certificates are no longer issued", False,
    "Chứng nhận vẫn được cấp, chỉ mất tám ngày làm việc."),
   ("To ask callers to use email instead of telephone", False,
    "Không có đề nghị nào về email.")]),

  ("Look at the graphic. Which project does NOT require approval?",
   Q.LC_GRAPHIC_REFERENCE, "lc_graphic_reference", 0.55, [
   ("Repainting an office interior", True,
    "Flowchart xếp việc chỉ sơn lại nội thất vào nhánh không cần phê duyệt."),
   ("Removing an internal wall", False,
    "Thay đổi kết cấu, kể cả dỡ tường, cần được phê duyệt."),
   ("Installing new electrical wiring", False,
    "Hệ thống dây điện mới nằm trong nhánh cần phê duyệt."),
   ("Changing the building's fire exits", False,
    "Thay đổi lối thoát hiểm ảnh hưởng an toàn nên cần phê duyệt.")]),

  ("What must callers include in a message?", Q.LC_DETAIL, "lc_detail", 0.50, [
   ("Their reference number", True,
    "Người nói nói rõ không có số hồ sơ thì không tra được."),
   ("The date of their final inspection", False,
    "Không nằm trong ba thứ được yêu cầu."),
   ("A copy of the flowchart from the website", False,
    "Sơ đồ để người gọi tự tra, không phải thứ phải gửi."),
   ("The name of the officer they spoke to", False,
    "Không có yêu cầu nào như vậy.")])]),
]


def build_group(idx: int, script: str, rows: list[tuple]) -> tuple[ExamGroup, int]:
    questions = []
    for stem, qtype, concept, diff, options in rows:
        options = place_options(idx, stem, options)
        opts = [Option(label=LABELS[i], text=t, is_correct=c, rationale_vi=r)
                for i, (t, c, r) in enumerate(options)]
        correct_text, correct_vi = next((t, r) for t, c, r in options if c)
        questions.append(ExamItem(
            part_number=4, question_text=stem, question_type=qtype, options=opts,
            concept_ids=[concept], difficulty_prior=diff,
            explanation=Definition(en=f'The correct answer is "{correct_text}".',
                                   vi=correct_vi)))
        idx += 1
    return ExamGroup(
        part_number=4,
        audio=AudioAsset(script=script, speaker_count=1,
                         accent=ACCENT_CYCLE[(idx // 3) % len(ACCENT_CYCLE)]),
        questions=questions), idx


def main() -> int:
    groups, idx = [], 0
    for group_index, (script, rows) in enumerate(TALKS):
        g, idx = build_group(idx, script, rows)
        if group_index == len(TALKS) - 1:
            payload = g.model_dump(mode="json")
            payload["image_url"] = (
                "http://localhost:9000/images/toeic/listening/graphics/approval_flowchart.svg")
            g = g.__class__.model_validate(payload)
        groups.append(g)

    n_q = sum(len(g.questions) for g in groups)
    print(f"Part 4: {len(groups)} bài nói, {n_q} câu")
    for g in groups:
        w = len(g.audio.script.split())
        if not (110 <= w <= 200):
            print(f"  ⚠ kịch bản {w} từ, ngoài 110–200")
    for w in report_bias(groups):
        print(f"  ⚠ {w}")
    acc = collections.Counter(g.audio.accent.value for g in groups)
    print("  giọng: " + "  ".join(f"{k}={v}" for k, v in acc.most_common()))
    types = collections.Counter(q.question_type.value
                                for g in groups for q in g.questions)
    print(f"  question_type: {dict(types.most_common())}\n")

    guarded_write_batch(ExamBatch(
        batch_metadata=BatchMetadata(
            batch_id="exam_listening_part4_001", module_type=ModuleType.EXAM,
            generated_by=GENERATED_BY, generated_at=datetime.now(UTC),
            total_records=len(groups)),
        groups=groups), OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
