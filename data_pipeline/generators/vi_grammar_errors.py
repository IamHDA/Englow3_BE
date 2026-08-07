"""Lỗi ngữ pháp đặc trưng người Việt — phân tích L1 transfer.

Đây là thứ **không nguồn mở nào có**. WordNet, CEFR-J, NGSL đều không nói được
vì sao người Việt sai chỗ này mà người Nhật sai chỗ khác.

Cơ chế gốc: tiếng Việt là ngôn ngữ **đơn lập** — không biến đổi hình thái. Không
chia động từ theo ngôi, không đánh dấu số nhiều bằng đuôi từ, không có mạo từ,
thì diễn đạt bằng trạng từ ("đã", "đang", "sẽ") chứ không bằng dạng động từ. Phần
lớn lỗi dưới đây bắt nguồn từ đó.

Mỗi mục:  concept_id -> (theory_vi, theory_en_summary, [(sai, đúng, vì sao)])

Concept nào chưa có trong file này thì generator BỎ QUA, không sinh point rỗng.
"""

from __future__ import annotations

__all__ = ["GRAMMAR_CONTENT"]

GRAMMAR_CONTENT: dict[str, tuple[str, str, list[tuple[str, str, str]]]] = {

"gram_plural_forms": (
 "Tiếng Anh bắt buộc đánh dấu số nhiều ngay trên danh từ bằng -s/-es, kể cả khi "
 "câu đã có số đếm. Tiếng Việt không biến đổi hình thái — 'hai quyển sách' thì "
 "'sách' vẫn nguyên dạng — nên người Việt hay bỏ quên -s vì thấy đã có 'hai' rồi.",
 "English marks plurality on the noun itself with -s/-es, even when a number is "
 "already present.",
 [("I bought three book yesterday.", "I bought three books yesterday.",
   "Đã có 'three' nhưng tiếng Anh vẫn bắt buộc -s. Tiếng Việt thì 'ba quyển sách' "
   "không đổi dạng từ 'sách', nên người học thấy -s là thừa."),
  ("There are many childs in the room.", "There are many children in the room.",
   "'child' là danh từ bất quy tắc. Người học áp quy tắc -s đều cho mọi từ vì "
   "tiếng Việt không có khái niệm bất quy tắc hình thái."),
  ("She has two informations for you.", "She has two pieces of information for you.",
   "'information' không đếm được trong tiếng Anh, nhưng 'thông tin' trong tiếng "
   "Việt đếm được bình thường ('hai thông tin'), nên người Việt gán nhầm."),
  ("The equipments are new.", "The equipment is new.",
   "'equipment' luôn số ít. Tiếng Việt 'các thiết bị' số nhiều tự nhiên nên dễ "
   "thêm -s và chia động từ số nhiều theo.")]),

"gram_article_indefinite": (
 "Tiếng Việt không có mạo từ. 'Tôi là kỹ sư' không cần từ nào trước 'kỹ sư', "
 "nhưng tiếng Anh bắt buộc 'a/an' trước danh từ đếm được số ít. Đây là lỗi phổ "
 "biến nhất và dai dẳng nhất của người Việt học tiếng Anh.",
 "English requires a/an before a singular countable noun; Vietnamese has no articles.",
 [("She is engineer at Kelbrook.", "She is an engineer at Kelbrook.",
   "Nghề nghiệp số ít bắt buộc có mạo từ. Tiếng Việt 'Cô ấy là kỹ sư' không có "
   "từ tương ứng nên người học không thấy chỗ trống cần điền."),
  ("I need umbrella.", "I need an umbrella.",
   "'umbrella' bắt đầu bằng nguyên âm nên dùng 'an'. Bỏ mạo từ là do L1, chọn "
   "sai a/an là do quy tắc âm đầu."),
  ("He gave me advice about a job.", "He gave me some advice about a job.",
   "'advice' không đếm được nên không dùng 'a', phải dùng 'some'. Người học "
   "thường sửa quá tay — biết phải có mạo từ nên thêm 'a' vào cả danh từ không "
   "đếm được."),
  ("It was a very useful information.", "It was very useful information.",
   "Cùng lỗi sửa quá tay: 'information' không đếm được, không có mạo từ.")]),

"gram_article_definite": (
 "'the' dùng khi người nghe biết chính xác vật nào đang nói tới — do đã nhắc "
 "trước, do duy nhất, hoặc do ngữ cảnh. Tiếng Việt diễn đạt ý này bằng 'đó', "
 "'ấy', 'này' hoặc bỏ trống, nên người Việt hoặc bỏ 'the' hoàn toàn, hoặc dùng "
 "'the' cho mọi danh từ vì sợ thiếu.",
 "'the' marks a noun the listener can identify uniquely.",
 [("Please close door.", "Please close the door.",
   "Chỉ có một cái cửa trong ngữ cảnh nên bắt buộc 'the'. Tiếng Việt 'đóng cửa "
   "lại' không cần từ nào."),
  ("The life is difficult.", "Life is difficult.",
   "Danh từ trừu tượng mang nghĩa chung thì không có mạo từ. Người học đã học "
   "'phải có mạo từ' nên thêm vào cả chỗ không cần."),
  ("I go to the work by bus.", "I go to work by bus.",
   "'work', 'school', 'home' theo nghĩa hoạt động thì không mạo từ."),
  ("She is the manager of the marketing.", "She is the manager of marketing.",
   "'the' đầu đúng vì chức danh duy nhất; 'the' sau sai vì tên phòng ban dùng "
   "như danh từ chung.")]),

"gram_subject_verb_agreement": (
 "Động từ tiếng Anh đổi dạng theo ngôi ba số ít ở hiện tại đơn (-s/-es). Tiếng "
 "Việt không chia động từ theo ngôi — 'tôi đi', 'anh ấy đi' đều là 'đi'. Đây là "
 "lỗi người Việt vẫn mắc kể cả ở trình độ cao, vì nó cần một thao tác mà tiếng "
 "mẹ đẻ không hề có.",
 "English adds -s/-es to the verb for third person singular in the present simple.",
 [("He work in the finance department.", "He works in the finance department.",
   "Ngôi ba số ít bắt buộc -s. Tiếng Việt không có thao tác này nên não người "
   "học không tự động kiểm tra."),
  ("The list of items are on your desk.", "The list of items is on your desk.",
   "Chủ ngữ là 'list' (số ít), không phải 'items'. Người học chia theo danh từ "
   "gần nhất."),
  ("Everyone have received the memo.", "Everyone has received the memo.",
   "'everyone' luôn số ít trong tiếng Anh dù nghĩa chỉ nhiều người — trái với "
   "trực giác từ tiếng Việt 'mọi người'."),
  ("She don't know the answer.", "She doesn't know the answer.",
   "Trợ động từ cũng phải chia. Lỗi này thường tồn tại rất lâu.")]),

"gram_present_perfect_vs_past_simple": (
 "Tiếng Việt đánh dấu thời gian bằng trạng từ ('đã', 'rồi') chứ không bằng dạng "
 "động từ, và không phân biệt việc đã xong hẳn với việc còn liên quan hiện tại. "
 "Cả 'Tôi đã ăn rồi' lẫn 'Tôi ăn lúc 7 giờ' đều dùng 'đã/rồi', nên người Việt "
 "không có chỗ bám để chọn giữa hai thì này.",
 "Present perfect links a past event to now; past simple places it at a finished time.",
 [("I have seen him yesterday.", "I saw him yesterday.",
   "'yesterday' là mốc quá khứ đã kết thúc nên không dùng hiện tại hoàn thành. "
   "Người học thấy 'đã' nên chọn 'have + V3'."),
  ("She works here since 2019.", "She has worked here since 2019.",
   "'since' đánh dấu việc kéo dài tới hiện tại → bắt buộc hiện tại hoàn thành."),
  ("Did you finish the report yet?", "Have you finished the report yet?",
   "'yet' thuộc nhóm đi với hiện tại hoàn thành trong văn viết trang trọng."),
  ("I have gone to Hanoi last month.", "I went to Hanoi last month.",
   "Cùng lỗi mốc thời gian xác định. Ngoài ra 'have gone' nghĩa là đi và chưa "
   "về, khác 'have been'.")]),

"gram_adj_order": (
 "Tiếng Việt đặt tính từ SAU danh từ: 'chiếc áo đỏ'. Tiếng Anh đặt trước, và khi "
 "có nhiều tính từ thì phải theo thứ tự cố định: ý kiến – kích thước – tuổi – "
 "hình dáng – màu – nguồn gốc – chất liệu – mục đích.",
 "English places adjectives before the noun in a fixed order.",
 [("She bought a red beautiful dress.", "She bought a beautiful red dress.",
   "Ý kiến ('beautiful') đứng trước màu ('red'). Người Việt không có thứ tự này "
   "vì tiếng Việt xếp tính từ sau danh từ theo trật tự khác."),
  ("a table wooden small", "a small wooden table",
   "Trật tự tiếng Việt 'cái bàn gỗ nhỏ' bị bê nguyên sang tiếng Anh."),
  ("an old nice Japanese car", "a nice old Japanese car",
   "Ý kiến trước tuổi, tuổi trước nguồn gốc."),
  ("the leather black expensive bag", "the expensive black leather bag",
   "Ba tính từ đảo ngược hoàn toàn thứ tự chuẩn.")]),

"gram_dependent_preposition": (
 "Giới từ đi kèm động từ và tính từ trong tiếng Anh là quy ước, không suy ra "
 "được từ nghĩa. Người Việt thường dịch thẳng giới từ tiếng Việt sang, hoặc bỏ "
 "hẳn vì tiếng Việt không cần.",
 "Dependent prepositions are fixed by convention and must be memorised as chunks.",
 [("She is married with a doctor.", "She is married to a doctor.",
   "Tiếng Việt 'kết hôn VỚI' dịch thẳng thành 'with', nhưng tiếng Anh dùng 'to'."),
  ("I am interested about this position.", "I am interested in this position.",
   "'interested' luôn đi với 'in'. Người học đoán theo nghĩa 'về'."),
  ("They discussed about the budget.", "They discussed the budget.",
   "'discuss' là ngoại động từ, KHÔNG có giới từ — nhưng tiếng Việt 'thảo luận "
   "VỀ' làm người học thêm 'about'."),
  ("He explained me the process.", "He explained the process to me.",
   "'explain' không nhận hai tân ngữ trực tiếp; phải có 'to'. Tiếng Việt "
   "'giải thích cho tôi' làm người học bỏ 'to'.")]),

"gram_passive_present": (
 "Tiếng Việt có 'bị' (nghĩa xấu) và 'được' (nghĩa tốt) — mang sắc thái đánh giá. "
 "Bị động tiếng Anh trung tính, chỉ dùng khi tác nhân không quan trọng. Người "
 "Việt hoặc tránh bị động vì thấy nó tiêu cực, hoặc dựng sai cấu trúc.",
 "English passive is neutral: be + past participle, used when the agent is unimportant.",
 [("The report was wrote by the intern.", "The report was written by the intern.",
   "Phải dùng quá khứ phân từ (V3), không phải quá khứ đơn (V2)."),
  ("The meeting will postpone until Friday.", "The meeting will be postponed until Friday.",
   "Cuộc họp không tự hoãn nó → bắt buộc bị động. Tiếng Việt 'cuộc họp sẽ hoãn' "
   "nghe bình thường nên người học giữ nguyên chủ động."),
  ("The documents are keeping in the safe.", "The documents are kept in the safe.",
   "Nhầm tiếp diễn với bị động vì cả hai đều có 'be'."),
  ("This building built in 1998.", "This building was built in 1998.",
   "Thiếu hẳn 'be'. Tiếng Việt 'toà nhà này xây năm 1998' không cần từ nối nào.")]),

"gram_verb_gerund_vs_infinitive": (
 "Tiếng Việt không đổi dạng động từ sau động từ khác: 'thích đi', 'quyết định "
 "đi' đều là 'đi'. Tiếng Anh bắt buộc chọn V-ing hay to-V tuỳ động từ đứng "
 "trước, và một số động từ đổi hẳn nghĩa theo lựa chọn đó.",
 "Some verbs take a gerund, others an infinitive; a few change meaning depending on which.",
 [("I enjoy to work with this team.", "I enjoy working with this team.",
   "'enjoy' luôn đi với V-ing. Không có tín hiệu nào từ tiếng Việt để nhớ."),
  ("She decided going to the conference.", "She decided to go to the conference.",
   "'decide' đi với to-V."),
  ("He stopped to smoke five years ago.", "He stopped smoking five years ago.",
   "'stop + V-ing' = bỏ hẳn thói quen; 'stop + to V' = dừng lại ĐỂ làm. Câu sai "
   "mang nghĩa ngược hoàn toàn."),
  ("Remember locking the door before you leave.", "Remember to lock the door before you leave.",
   "'remember + to V' = nhớ mà làm (việc chưa làm); 'remember + V-ing' = nhớ là "
   "đã làm.")]),

"gram_question_formation": (
 "Tiếng Việt tạo câu hỏi bằng cách thêm tiểu từ ở cuối ('không', 'à', 'chứ') và "
 "giữ nguyên trật tự từ. Tiếng Anh phải đảo trợ động từ lên trước chủ ngữ, hoặc "
 "mượn 'do/does/did' nếu không có trợ động từ nào.",
 "English questions invert the auxiliary with the subject, or insert do/does/did.",
 [("You are coming tomorrow?", "Are you coming tomorrow?",
   "Chỉ lên giọng thì không đủ trong văn viết. Tiếng Việt chỉ cần thêm 'à' ở cuối."),
  ("Where you did put the file?", "Where did you put the file?",
   "Sau từ để hỏi phải đảo ngay trợ động từ."),
  ("What time the meeting starts?", "What time does the meeting start?",
   "Không có trợ động từ nên phải mượn 'does', và động từ chính trở về nguyên mẫu."),
  ("Do you know where is the printer?", "Do you know where the printer is?",
   "Câu hỏi gián tiếp KHÔNG đảo. Người học đảo quá tay vì vừa học quy tắc đảo.")]),

"gram_conditional_first": (
 "Tiếng Việt dùng 'nếu... thì...' và không đổi dạng động từ ở vế nào. Tiếng Anh "
 "cấm dùng 'will' trong vế 'if' của điều kiện loại 1 — một quy tắc không có "
 "tương ứng nào trong tiếng Việt.",
 "First conditional: if + present simple, main clause with will.",
 [("If it will rain, we will cancel the picnic.", "If it rains, we will cancel the picnic.",
   "Vế 'if' dùng hiện tại đơn dù nói về tương lai. Tiếng Việt 'nếu trời sẽ mưa' "
   "cũng không tự nhiên, nhưng người học suy từ nghĩa tương lai."),
  ("If you will send the file, I check it.", "If you send the file, I will check it.",
   "Đặt 'will' nhầm vế — sai cả hai đầu."),
  ("If she comes, I would tell her.", "If she comes, I will tell her.",
   "Trộn loại 1 với loại 2. Tiếng Việt không phân biệt điều kiện có thật và giả định."),
  ("When it will be ready, please call me.", "When it is ready, please call me.",
   "Mệnh đề thời gian chỉ tương lai cũng cấm 'will', giống vế 'if'.")]),

"gram_word_form_noun": (
 "Tiếng Việt không có hậu tố phái sinh — 'phát triển' làm được cả danh từ lẫn "
 "động từ mà không đổi dạng. Tiếng Anh bắt buộc đổi đuôi theo vị trí trong câu, "
 "và đây là dạng câu hỏi chủ lực của Part 5.",
 "English derives nouns with suffixes such as -tion, -ment, -ance, -ity, -ness.",
 [("The company announced an expand into Asia.", "The company announced an expansion into Asia.",
   "Sau mạo từ phải là danh từ. Tiếng Việt 'một mở rộng' cũng sai nhưng người "
   "học không có tín hiệu hình thái để nhận ra."),
  ("We need your approve before Friday.", "We need your approval before Friday.",
   "Sau tính từ sở hữu 'your' cần danh từ."),
  ("His manage of the project was excellent.", "His management of the project was excellent.",
   "Chủ ngữ phải là danh từ."),
  ("There is a different between the two reports.", "There is a difference between the two reports.",
   "'different' là tính từ; sau 'a' cần danh từ 'difference'.")]),

"gram_comparative_adj": (
 "Tiếng Việt so sánh bằng 'hơn' đặt sau tính từ, không đổi dạng tính từ. Tiếng "
 "Anh đổi dạng (-er) hoặc thêm 'more', và tuyệt đối không dùng cả hai.",
 "English forms comparatives with -er or more, never both.",
 [("This method is more easier than the old one.", "This method is easier than the old one.",
   "Dùng cả 'more' lẫn '-er'. Người học thêm 'more' cho chắc vì tiếng Việt chỉ "
   "có một cách duy nhất là 'hơn'."),
  ("She is more tall than her brother.", "She is taller than her brother.",
   "Tính từ một âm tiết dùng -er, không dùng 'more'."),
  ("This report is more good than the last.", "This report is better than the last.",
   "'good' bất quy tắc."),
  ("The new office is bigger from the old one.", "The new office is bigger than the old one.",
   "So sánh hơn đi với 'than'; 'from' là do dịch từ 'khác với'.")]),

"gram_relative_defining": (
 "Tiếng Việt dùng 'mà' hoặc không dùng gì, và không phân biệt người với vật. "
 "Tiếng Anh chọn who/which/that theo đối tượng, và cho phép lược bỏ khi đại từ "
 "quan hệ làm tân ngữ.",
 "Defining relative clauses use who for people, which/that for things.",
 [("The colleague which helped me has left.", "The colleague who helped me has left.",
   "'which' chỉ dùng cho vật. Tiếng Việt 'người mà' và 'thứ mà' đều là 'mà'."),
  ("The report who I sent is outdated.", "The report which I sent is outdated.",
   "Ngược lại: dùng 'who' cho vật."),
  ("The manager, that approved it, is on leave.",
   "The manager, who approved it, is on leave.",
   "Mệnh đề không xác định (có dấu phẩy) không được dùng 'that'."),
  ("The file what you need is in the drawer.", "The file that you need is in the drawer.",
   "'what' không phải đại từ quan hệ. Đây là lỗi dịch từ 'cái mà'.")]),

"gram_modal_obligation": (
 "Tiếng Việt dùng 'phải' cho gần như mọi mức bắt buộc. Tiếng Anh phân biệt "
 "must/have to/should, và đặc biệt 'mustn't' (cấm) khác hẳn 'don't have to' "
 "(không cần) — hai thứ mà tiếng Việt đều diễn đạt quanh chữ 'không phải'.",
 "must, have to and should differ in strength; mustn't means prohibited, don't have to means unnecessary.",
 [("You mustn't come if you are busy.", "You don't have to come if you are busy.",
   "'mustn't' là CẤM. Câu sai mang nghĩa 'cấm bạn đến', trái hẳn ý định."),
  ("He must to submit the form.", "He must submit the form.",
   "Sau khuyết thiếu là động từ nguyên mẫu không 'to'."),
  ("You should to check the figures.", "You should check the figures.",
   "Cùng lỗi thừa 'to'."),
  ("I must work late yesterday.", "I had to work late yesterday.",
   "'must' không có dạng quá khứ; phải dùng 'had to'.")]),

"gram_noun_countability": (
 "Ranh giới đếm được / không đếm được của tiếng Anh không trùng với tiếng Việt. "
 "'thông tin', 'lời khuyên', 'đồ đạc' trong tiếng Việt đếm được bình thường "
 "nhưng tiếng Anh thì không.",
 "Countability boundaries differ between English and Vietnamese.",
 [("Can you give me some advices?", "Can you give me some advice?",
   "'advice' không đếm được. Tiếng Việt 'vài lời khuyên' đếm được tự nhiên."),
  ("We bought new furnitures for the office.", "We bought new furniture for the office.",
   "'furniture' luôn số ít."),
  ("How many money do you need?", "How much money do you need?",
   "'money' không đếm được nên dùng 'much'."),
  ("There were less people than expected.", "There were fewer people than expected.",
   "'people' đếm được nên dùng 'fewer'.")]),
}
