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

"gram_be_present": (
 "Tiếng Việt dùng 'là' chỉ để nối hai danh từ ('Tôi là sinh viên'), còn với tính "
 "từ thì không cần từ nối ('Tôi mệt'). Tiếng Anh thì 'be' bắt buộc trong CẢ HAI "
 "trường hợp, và còn phải chia theo ngôi: am / is / are.",
 "English 'be' is required before adjectives as well as nouns, and inflects for person.",
 [("I tired today.", "I am tired today.",
   "Tiếng Việt 'Tôi mệt' không có từ nối nên người học thấy 'am' là thừa. "
   "Đây là lỗi A1 dai dẳng nhất."),
  ("She a doctor at the hospital.", "She is a doctor at the hospital.",
   "Có 'là' trong tiếng Việt thì người học nhớ, nhưng khi nói nhanh vẫn hay rơi."),
  ("They is my colleagues.", "They are my colleagues.",
   "Tiếng Việt không chia động từ theo ngôi nên 'is/are' là khái niệm hoàn toàn mới."),
  ("Are you a student? — Yes, I am student.", "Are you a student? — Yes, I am.",
   "Trả lời ngắn tiếng Anh dừng ở trợ động từ. Người Việt lặp lại cả danh từ vì "
   "tiếng Việt trả lời 'Vâng, tôi là sinh viên'.")]),

"gram_present_simple": (
 "Hiện tại đơn diễn tả thói quen và sự thật hiển nhiên. Cái bẫy với người Việt là "
 "đuôi -s ở ngôi thứ ba số ít: tiếng Việt không chia động từ theo ngôi bao giờ, "
 "nên chỗ này không có gì để 'chuyển' sang — phải học thuộc.",
 "Third-person singular takes -s; Vietnamese verbs never inflect for person.",
 [("He work at a bank in the city.", "He works at a bank in the city.",
   "Ngôi thứ ba số ít bắt buộc -s. Không có tương ứng nào trong tiếng Việt."),
  ("She don't like coffee.", "She doesn't like coffee.",
   "Đã dùng trợ động từ thì -s chuyển sang trợ động từ: does. Người học nhớ quy "
   "tắc -s ở câu khẳng định nhưng quên ở câu phủ định."),
  ("Does he works here?", "Does he work here?",
   "Sửa quá tay: đã có 'does' thì động từ chính về nguyên thể. Chỉ một chỗ mang -s."),
  ("My parents lives in Hue.", "My parents live in Hue.",
   "Chủ ngữ số nhiều thì KHÔNG thêm -s. Người học nhìn 'parents' có -s rồi thêm "
   "-s cho cả động từ.")]),

"gram_present_continuous": (
 "Tiếng Việt dùng 'đang' đứng trước động từ, và 'đang' là một từ riêng không dính "
 "vào động từ. Tiếng Anh phải dùng be + V-ing — hai thành phần, và người học "
 "thường chỉ nhớ một.",
 "Present continuous needs both 'be' and '-ing'; Vietnamese marks it with a separate word.",
 [("I working on the report now.", "I am working on the report now.",
   "Thiếu 'be'. Người học coi -ing là bản dịch của 'đang' và cho là đủ."),
  ("She is work in the garden.", "She is working in the garden.",
   "Thiếu -ing. Lỗi đối xứng với lỗi trên."),
  ("I am knowing the answer.", "I know the answer.",
   "Động từ chỉ trạng thái ('know', 'like', 'want') không dùng tiếp diễn. Tiếng "
   "Việt nói 'tôi đang biết' vẫn được nên người học không thấy vướng."),
  ("He is go to the airport tomorrow.", "He is going to the airport tomorrow.",
   "Hiện tại tiếp diễn còn dùng cho kế hoạch đã sắp xếp, nhưng vẫn phải đủ -ing.")]),

"gram_present_simple_vs_continuous": (
 "Chọn giữa hai thì này là chọn giữa 'nói chung' và 'ngay lúc này'. Tiếng Việt "
 "phân biệt bằng có hay không có 'đang', nhưng 'đang' của tiếng Việt rộng hơn "
 "-ing của tiếng Anh, nên người học dùng tiếp diễn nhiều quá mức.",
 "Simple present for habits and states; continuous for actions in progress.",
 [("I am going to the gym every Tuesday.", "I go to the gym every Tuesday.",
   "'every Tuesday' là thói quen nên phải hiện tại đơn."),
  ("Look — it rains!", "Look — it is raining!",
   "'Look' báo hiệu việc đang diễn ra trước mắt, phải dùng tiếp diễn."),
  ("She is having three brothers.", "She has three brothers.",
   "'have' nghĩa sở hữu là trạng thái, không dùng tiếp diễn."),
  ("What do you do? — I am working for Kelbrook.",
   "What do you do? — I work for Kelbrook.",
   "Hỏi về nghề nghiệp nói chung thì trả lời bằng hiện tại đơn.")]),

"gram_past_simple": (
 "Tiếng Việt đánh dấu quá khứ bằng 'đã' hoặc bằng trạng ngữ thời gian, và động từ "
 "không đổi. Tiếng Anh bắt buộc đổi dạng động từ, kể cả khi câu đã có 'yesterday'.",
 "English marks past on the verb itself, even when a time adverb is present.",
 [("I go to Da Nang last summer.", "I went to Da Nang last summer.",
   "Đã có 'last summer' nhưng động từ vẫn phải đổi. Tiếng Việt 'Tôi đi Đà Nẵng "
   "hè năm ngoái' không đổi gì cả."),
  ("She buyed a new phone.", "She bought a new phone.",
   "Động từ bất quy tắc. Người học áp -ed đều vì tiếng Việt không có bất quy tắc."),
  ("Did you went to the meeting?", "Did you go to the meeting?",
   "Đã có 'did' thì động từ chính về nguyên thể — chỉ một chỗ mang dấu quá khứ."),
  ("He didn't came yesterday.", "He didn't come yesterday.",
   "Cùng quy tắc, ở câu phủ định.")]),

"gram_past_continuous": (
 "Quá khứ tiếp diễn dựng nền cho một việc khác xen vào. Người Việt thường dùng "
 "quá khứ đơn cho cả hai vế vì tiếng Việt chỉ cần 'đang' ở vế nền.",
 "Past continuous sets the background; past simple marks the interrupting event.",
 [("I watched TV when the phone rang.", "I was watching TV when the phone rang.",
   "Việc kéo dài làm nền phải là tiếp diễn; việc xen vào mới là quá khứ đơn."),
  ("While she was cook, the alarm went off.", "While she was cooking, the alarm went off.",
   "Thiếu -ing sau 'was'."),
  ("They were knowing about the change.", "They knew about the change.",
   "Động từ trạng thái không dùng tiếp diễn, kể cả ở quá khứ."),
  ("When I arrived, he was leaving already.", "When I arrived, he had already left.",
   "Việc xảy ra XONG trước mốc quá khứ thì dùng quá khứ hoàn thành, không phải "
   "tiếp diễn.")]),

"gram_past_perfect": (
 "Quá khứ hoàn thành đánh dấu 'việc xảy ra trước một mốc quá khứ khác'. Tiếng "
 "Việt diễn đạt thứ tự bằng trật tự kể chuyện và bằng 'trước đó', không bằng dạng "
 "động từ — nên người Việt hầu như không tự dùng thì này.",
 "Past perfect marks the earlier of two past events.",
 [("When I arrived, the train already left.", "When I arrived, the train had already left.",
   "Tàu đi TRƯỚC lúc tôi đến, nên vế đó phải là quá khứ hoàn thành."),
  ("She told me she has finished the report.", "She told me she had finished the report.",
   "Trong câu tường thuật ở quá khứ, hiện tại hoàn thành lùi thành quá khứ hoàn thành."),
  ("After he had left, she had called him.", "After he had left, she called him.",
   "Sửa quá tay: chỉ vế XẢY RA TRƯỚC mới dùng quá khứ hoàn thành."),
  ("I never saw such a thing before that day.", "I had never seen such a thing before that day.",
   "'before that day' đặt mốc quá khứ, nên việc trước đó phải là quá khứ hoàn thành.")]),

"gram_future_will": (
 "'will' dùng cho quyết định ngay lúc nói, lời hứa và dự đoán. Người Việt hay "
 "dùng 'will' cho mọi thứ tương lai vì tiếng Việt chỉ có một từ 'sẽ'.",
 "'will' expresses decisions made at the moment of speaking, promises and predictions.",
 [("I will go to Hanoi next week, I bought the ticket yesterday.",
   "I am going to Hanoi next week, I bought the ticket yesterday.",
   "Đã mua vé tức là kế hoạch có sẵn — dùng 'be going to' hoặc hiện tại tiếp diễn."),
  ("If it will rain, we will cancel.", "If it rains, we will cancel.",
   "Mệnh đề 'if' không dùng 'will'. Tiếng Việt nói 'nếu trời sẽ mưa' vẫn xuôi tai."),
  ("I will to call you tonight.", "I will call you tonight.",
   "Sau 'will' là động từ nguyên thể không 'to'."),
  ("She will finishes the work by Friday.", "She will finish the work by Friday.",
   "Sau 'will' động từ không chia.")]),

"gram_future_going_to": (
 "'be going to' dùng cho kế hoạch đã định và cho dự đoán có bằng chứng trước mắt. "
 "Ranh giới với 'will' không tồn tại trong tiếng Việt — cả hai đều là 'sẽ'.",
 "'be going to' for prior plans and evidence-based predictions.",
 [("Look at those clouds — it will rain.", "Look at those clouds — it is going to rain.",
   "Có bằng chứng trước mắt (mây đen) thì dùng 'be going to'."),
  ("I going to meet her tomorrow.", "I am going to meet her tomorrow.",
   "Thiếu 'be'. Cùng gốc lỗi với hiện tại tiếp diễn."),
  ("We are going to visiting the factory.", "We are going to visit the factory.",
   "Sau 'going to' là động từ nguyên thể, không phải V-ing."),
  ("The phone is ringing — I am going to answer it.",
   "The phone is ringing — I will answer it.",
   "Quyết định ngay lúc nói thì dùng 'will', không phải 'be going to'.")]),

"gram_pronoun_subject_object": (
 "Tiếng Việt dùng cùng một từ cho chủ ngữ và tân ngữ ('tôi' ở cả hai vị trí). "
 "Tiếng Anh đổi dạng: I/me, he/him, she/her, we/us, they/them.",
 "English pronouns change form between subject and object position.",
 [("Give the file to I.", "Give the file to me.",
   "Sau giới từ phải dùng dạng tân ngữ. Tiếng Việt 'đưa cho tôi' không đổi gì."),
  ("Him and I went to the meeting.", "He and I went to the meeting.",
   "Vị trí chủ ngữ dùng 'he'. Thử bỏ 'and I' đi sẽ thấy ngay: 'Him went' sai."),
  ("She invited my colleague and I.", "She invited my colleague and me.",
   "Sửa quá tay: nhiều người học được dạy 'and I' nghe lịch sự hơn nên dùng cả ở "
   "vị trí tân ngữ."),
  ("Between you and I, the plan will not work.",
   "Between you and me, the plan will not work.",
   "Sau giới từ 'between' bắt buộc dạng tân ngữ.")]),

"gram_pronoun_possessive": (
 "Tiếng Anh phân biệt tính từ sở hữu (my, your, his) đứng trước danh từ, và đại từ "
 "sở hữu (mine, yours, his) đứng một mình. Tiếng Việt dùng 'của tôi' cho cả hai.",
 "Possessive adjectives precede a noun; possessive pronouns stand alone.",
 [("This book is my.", "This book is mine.",
   "Đứng một mình phải dùng đại từ sở hữu 'mine'."),
  ("Mine car is in the car park.", "My car is in the car park.",
   "Đứng trước danh từ phải dùng tính từ sở hữu 'my'."),
  ("Is this your's?", "Is this yours?",
   "Đại từ sở hữu không có dấu nháy. Người học nhầm với sở hữu cách của danh từ."),
  ("The company changed it's policy.", "The company changed its policy.",
   "'its' sở hữu không có nháy; \"it's\" là 'it is'. Lỗi này người bản ngữ cũng mắc.")]),

"gram_possessive_s": (
 "Tiếng Việt diễn đạt sở hữu bằng 'của' đặt SAU vật sở hữu: 'cái cặp của Lan'. "
 "Tiếng Anh đảo ngược thứ tự và dùng 's: 'Lan's bag'. Trật tự ngược nhau là gốc "
 "của phần lớn lỗi ở mục này.",
 "English marks possession with 's on the possessor, which precedes the thing possessed.",
 [("The bag of Lan is on the table.", "Lan's bag is on the table.",
   "Với người thì dùng 's, không dùng 'of'. Cấu trúc 'of' là dịch thẳng từ 'của'."),
  ("This is my parent's house, both of them live here.",
   "This is my parents' house, both of them live here.",
   "Số nhiều đã có -s thì dấu nháy đặt SAU -s."),
  ("The childrens' toys are here.", "The children's toys are here.",
   "'children' đã là số nhiều bất quy tắc nên thêm 's bình thường."),
  ("It is the roof's of the building.", "It is the roof of the building.",
   "Vật vô tri thường dùng 'of', và không dùng cả hai cùng lúc.")]),

"gram_quantifier_basic": (
 "'some' và 'any' phân bố theo loại câu, còn 'a few / a little' phân bố theo tính "
 "đếm được. Tiếng Việt dùng 'một ít', 'vài' không phân biệt như vậy.",
 "'some' in affirmatives and offers, 'any' in negatives and questions.",
 [("I don't have some money.", "I don't have any money.",
   "Câu phủ định dùng 'any'."),
  ("Would you like any tea?", "Would you like some tea?",
   "Câu mời dùng 'some' dù có dạng câu hỏi."),
  ("There are a little chairs left.", "There are a few chairs left.",
   "'chairs' đếm được nên dùng 'a few'."),
  ("She has few free time this week.", "She has little free time this week.",
   "'time' không đếm được nên dùng 'little'.")]),

"gram_article_zero": (
 "Có những chỗ tiếng Anh KHÔNG dùng mạo từ: danh từ số nhiều nói chung, danh từ "
 "không đếm được nói chung, tên riêng, bữa ăn, môn học. Người Việt sau khi học "
 "a/an/the thường sửa quá tay và thêm mạo từ vào cả những chỗ này.",
 "No article with generic plurals, uncountables, most proper nouns, meals and subjects.",
 [("The dogs are loyal animals.", "Dogs are loyal animals.",
   "Nói về loài nói chung thì không dùng 'the'."),
  ("She studies the economics at university.", "She studies economics at university.",
   "Tên môn học không dùng mạo từ."),
  ("We had the lunch at noon.", "We had lunch at noon.",
   "Tên bữa ăn không dùng mạo từ."),
  ("The happiness is not something you can buy.",
   "Happiness is not something you can buy.",
   "Danh từ trừu tượng nói chung không dùng mạo từ.")]),

"gram_there_is_are": (
 "Tiếng Việt dùng 'có' cho mọi trường hợp tồn tại. Tiếng Anh phải chọn there is / "
 "there are theo danh từ ĐỨNG SAU, và tuyệt đối không dùng 'have'.",
 "'There is/are' expresses existence; the verb agrees with the following noun.",
 [("Have three people in the room.", "There are three people in the room.",
   "Dịch thẳng 'có' thành 'have'. Đây là lỗi nhận ra ngay người học là người Việt."),
  ("There is many problems with this plan.", "There are many problems with this plan.",
   "Động từ hoà hợp với danh từ đứng sau: 'problems' số nhiều."),
  ("There are a lot of information on the site.",
   "There is a lot of information on the site.",
   "'information' không đếm được nên dùng số ít."),
  ("In the box there have two keys.", "In the box there are two keys.",
   "Cùng lỗi 'have', xuất hiện cả khi đảo trật tự.")]),

"gram_adverb_frequency": (
 "Trạng từ tần suất trong tiếng Anh đứng TRƯỚC động từ thường nhưng SAU động từ "
 "'be'. Tiếng Việt đặt 'thường', 'luôn luôn' khá tự do nên người học hay đặt sai chỗ.",
 "Frequency adverbs go before the main verb but after 'be'.",
 [("I go always to work by bus.", "I always go to work by bus.",
   "Trạng từ tần suất đứng trước động từ thường."),
  ("She always is late for meetings.", "She is always late for meetings.",
   "Với 'be' thì trạng từ đứng SAU."),
  ("He never has finished a project early.", "He has never finished a project early.",
   "Có trợ động từ thì trạng từ đứng giữa trợ động từ và động từ chính."),
  ("Sometimes I am not understanding him.", "Sometimes I do not understand him.",
   "'understand' là động từ trạng thái, không dùng tiếp diễn.")]),

"gram_superlative_adj": (
 "So sánh nhất theo độ dài tính từ: -est cho tính từ ngắn, 'the most' cho tính từ "
 "dài. Tiếng Việt chỉ có một khuôn 'nhất' nên người học hay ghép cả hai.",
 "Short adjectives take -est; longer adjectives take 'the most'.",
 [("She is the most tallest in the class.", "She is the tallest in the class.",
   "Ghép cả hai cách. Lỗi rất phổ biến vì tiếng Việt chỉ có một khuôn."),
  ("This is the most easy question.", "This is the easiest question.",
   "Tính từ hai âm tiết kết thúc bằng -y dùng -est."),
  ("He is best student in the department.", "He is the best student in the department.",
   "So sánh nhất bắt buộc có 'the'."),
  ("It was the most bad experience of my life.",
   "It was the worst experience of my life.",
   "'bad' bất quy tắc: bad – worse – worst.")]),

"gram_as_as_comparison": (
 "So sánh bằng dùng as + tính từ NGUYÊN DẠNG + as. Người học hay chèn dạng so "
 "sánh hơn vào giữa vì trong tiếng Việt 'bằng' và 'hơn' cùng là so sánh.",
 "'as ... as' takes the base form of the adjective.",
 [("She is as taller as her brother.", "She is as tall as her brother.",
   "Giữa hai 'as' là tính từ nguyên dạng."),
  ("This model is not as expensive than that one.",
   "This model is not as expensive as that one.",
   "Cấu trúc là as ... as, không phải as ... than."),
  ("He works as harder as anyone here.", "He works as hard as anyone here.",
   "Trạng từ cũng giữ nguyên dạng."),
  ("The new office is twice as big than the old one.",
   "The new office is twice as big as the old one.",
   "Thêm bội số vẫn giữ cấu trúc as ... as.")]),

"gram_preposition_basic": (
 "in / on / at cho thời gian và nơi chốn không ánh xạ 1-1 sang tiếng Việt — tiếng "
 "Việt dùng 'ở', 'vào', 'trên' theo logic khác. Phải học theo cụm, không dịch từng từ.",
 "in for enclosed spaces and long periods, on for surfaces and days, at for points.",
 [("I will meet you in Monday.", "I will meet you on Monday.",
   "Thứ trong tuần dùng 'on'."),
  ("She arrives at March.", "She arrives in March.",
   "Tháng dùng 'in'."),
  ("The keys are on the drawer.", "The keys are in the drawer.",
   "Bên trong vật chứa dùng 'in'; 'on' là trên mặt."),
  ("We live at Ho Chi Minh City.", "We live in Ho Chi Minh City.",
   "Thành phố dùng 'in'; 'at' dành cho một điểm cụ thể như địa chỉ.")]),

"gram_modal_ability": (
 "'can' và 'could' không chia và không đi với 'to'. Tiếng Việt 'có thể' đứng "
 "trước động từ giống hệt, nên lỗi ở đây chủ yếu là chia động từ theo thói quen.",
 "Modals take the bare infinitive and never inflect.",
 [("She cans speak three languages.", "She can speak three languages.",
   "Khuyết thiếu không chia theo ngôi."),
  ("He can to drive a lorry.", "He can drive a lorry.",
   "Sau khuyết thiếu là động từ nguyên thể không 'to'."),
  ("I will can help you tomorrow.", "I will be able to help you tomorrow.",
   "Không dùng hai khuyết thiếu liền nhau; tương lai của 'can' là 'be able to'."),
  ("Do you can swim?", "Can you swim?",
   "Khuyết thiếu tự đảo lên để hỏi, không cần 'do'.")]),
}
