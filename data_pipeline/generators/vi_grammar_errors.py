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

"gram_conditional_zero": (
 "Điều kiện loại 0 nói về sự thật luôn đúng: cả hai vế đều hiện tại đơn. Người "
 "Việt hay chèn 'will' vào vế sau vì 'thì' trong tiếng Việt nghe như hệ quả tương lai.",
 "Zero conditional: present simple in both clauses, for general truths.",
 [("If you heat water to 100 degrees, it will boil.",
   "If you heat water to 100 degrees, it boils.",
   "Sự thật khoa học luôn đúng thì cả hai vế hiện tại đơn."),
  ("If the light is red, you will stop.", "If the light is red, you stop.",
   "Quy tắc chung, không phải dự đoán một lần."),
  ("When the machine will overheat, it shuts down.",
   "When the machine overheats, it shuts down.",
   "Mệnh đề 'when' chỉ điều kiện cũng không dùng 'will'."),
  ("If ice melts, it became water.", "If ice melts, it becomes water.",
   "Không trộn thì quá khứ vào điều kiện loại 0.")]),

"gram_conditional_second": (
 "Điều kiện loại 2 nói về tình huống KHÔNG có thật ở hiện tại: if + quá khứ đơn, "
 "vế chính would + nguyên thể. Tiếng Việt không đổi dạng động từ để đánh dấu 'giả "
 "định', chỉ dùng 'nếu ... thì', nên người học dùng hiện tại và mất hẳn sắc thái.",
 "Second conditional: past simple in the if-clause, would + infinitive in the main clause.",
 [("If I have more time, I would learn Japanese.",
   "If I had more time, I would learn Japanese.",
   "Vế 'if' phải là quá khứ đơn để đánh dấu điều không có thật."),
  ("If I would be you, I would accept the offer.",
   "If I were you, I would accept the offer.",
   "Không dùng 'would' trong vế 'if'; và 'were' là dạng giả định chuẩn."),
  ("If she studied harder, she will pass.", "If she studied harder, she would pass.",
   "Trộn loại 2 với loại 1. Vế chính phải là 'would'."),
  ("If he was here, he would know what to do.",
   "If he were here, he would know what to do.",
   "Trong văn phong trang trọng dùng 'were' cho mọi ngôi.")]),

"gram_conditional_third": (
 "Điều kiện loại 3 nói về việc ĐÃ KHÔNG xảy ra trong quá khứ: if + quá khứ hoàn "
 "thành, vế chính would have + phân từ hai. Đây là cấu trúc ba tầng, và tiếng Việt "
 "diễn đạt trọn vẹn chỉ bằng 'giá mà ... thì đã ...'.",
 "Third conditional: past perfect in the if-clause, would have + past participle.",
 [("If I knew about the meeting, I would have come.",
   "If I had known about the meeting, I would have come.",
   "Vế 'if' phải là quá khứ hoàn thành, không phải quá khứ đơn."),
  ("If she had left earlier, she would catch the train.",
   "If she had left earlier, she would have caught the train.",
   "Vế chính thiếu 'have' — nửa loại 3 nửa loại 2."),
  ("If they would have asked, we would have helped.",
   "If they had asked, we would have helped.",
   "Không dùng 'would have' trong vế 'if'."),
  ("If he had studied, he would has passed.", "If he had studied, he would have passed.",
   "Sau 'would' luôn là 'have', không chia.")]),

"gram_conditional_mixed": (
 "Điều kiện hỗn hợp ghép hai mốc thời gian: nguyên nhân quá khứ, hệ quả hiện tại "
 "(hoặc ngược lại). Người học nắm được loại 2 và loại 3 riêng lẻ vẫn thường không "
 "nghĩ tới việc trộn.",
 "Mixed conditionals combine a past condition with a present result, or the reverse.",
 [("If I had taken that job, I would have been happier now.",
   "If I had taken that job, I would be happier now.",
   "Hệ quả ở HIỆN TẠI ('now') nên vế chính bỏ 'have'."),
  ("If she was more careful, she would not have lost the file.",
   "If she were more careful, she would not have lost the file.",
   "Điều kiện là đặc điểm hiện tại nên dùng 'were', hệ quả ở quá khứ giữ 'would have'."),
  ("If I would have studied medicine, I would be a doctor.",
   "If I had studied medicine, I would be a doctor.",
   "Vế 'if' không dùng 'would have'."),
  ("If he had not missed the flight, he is here now.",
   "If he had not missed the flight, he would be here now.",
   "Hệ quả giả định vẫn cần 'would', không dùng hiện tại đơn.")]),

"gram_wish_regret": (
 "Sau 'wish' phải lùi một bậc thời gian: tiếc hiện tại thì dùng quá khứ, tiếc quá "
 "khứ thì dùng quá khứ hoàn thành. Tiếng Việt chỉ có 'ước gì' và không lùi thì.",
 "'wish' backshifts one step: past for present regrets, past perfect for past regrets.",
 [("I wish I have more free time.", "I wish I had more free time.",
   "Tiếc về hiện tại thì lùi về quá khứ đơn."),
  ("She wishes she didn't say that yesterday.",
   "She wishes she hadn't said that yesterday.",
   "Tiếc về quá khứ thì lùi về quá khứ hoàn thành."),
  ("I wish I can speak French.", "I wish I could speak French.",
   "Khuyết thiếu cũng lùi: can thành could."),
  ("I wish you will stop interrupting.", "I wish you would stop interrupting.",
   "Phàn nàn về thói quen người khác dùng 'would'.")]),

"gram_passive_past": (
 "Bị động quá khứ và tương lai chỉ đổi phần 'be', phân từ hai giữ nguyên. Người "
 "Việt hay quên 'be' hoàn toàn vì tiếng Việt đánh dấu bị động bằng 'được/bị' đứng "
 "trước động từ mà không đổi dạng động từ.",
 "Passive changes only the form of 'be'; the past participle stays constant.",
 [("The report finished yesterday.", "The report was finished yesterday.",
   "Thiếu 'be'. Báo cáo không tự hoàn thành được."),
  ("The parcel will delivered on Monday.", "The parcel will be delivered on Monday.",
   "Sau 'will' phải có 'be'."),
  ("The road was repair last month.", "The road was repaired last month.",
   "Thiếu phân từ hai."),
  ("The results were announce by the committee.",
   "The results were announced by the committee.",
   "Cùng lỗi, kèm 'by' chỉ tác nhân.")]),

"gram_passive_causative": (
 "Thể sai khiến 'have/get something done' nghĩa là nhờ người khác làm. Tiếng Việt "
 "nói 'tôi đi cắt tóc' — chủ ngữ vẫn là tôi — nên người học dịch thẳng thành 'I cut "
 "my hair', mang nghĩa tự cắt.",
 "'have/get + object + past participle' means arranging for someone else to do it.",
 [("I cut my hair last week at the salon.",
   "I had my hair cut last week at the salon.",
   "Dịch thẳng từ 'tôi đi cắt tóc' thành ra nghĩa tự cầm kéo cắt."),
  ("We repaired the roof by a contractor.",
   "We had the roof repaired by a contractor.",
   "Nhờ người khác làm thì dùng thể sai khiến."),
  ("She got repaired her laptop.", "She got her laptop repaired.",
   "Trật tự là have/get + TÂN NGỮ + phân từ hai."),
  ("He had his car to be washed.", "He had his car washed.",
   "Không dùng 'to be' trong cấu trúc này.")]),

"gram_reported_statement": (
 "Tường thuật câu kể phải đổi đại từ, trạng ngữ thời gian và thường lùi thì. Tiếng "
 "Việt gần như chỉ đổi đại từ, giữ nguyên mọi thứ còn lại.",
 "Reported statements shift pronouns, time expressions and usually the tense.",
 [('He said "I am tired".', "He said he was tired.",
   "Lùi thì từ hiện tại về quá khứ và đổi đại từ."),
  ('She said "I will call you tomorrow".',
   "She said she would call me the next day.",
   "Đổi cả 'will' thành 'would' và 'tomorrow' thành 'the next day'."),
  ("He told that he was busy.", "He told me he was busy.",
   "'tell' bắt buộc có tân ngữ người; 'say' thì không."),
  ("She said me she had finished.", "She said she had finished.",
   "Lỗi đối xứng: 'say' không đi trực tiếp với tân ngữ người.")]),

"gram_reported_question": (
 "Câu hỏi tường thuật trở lại TRẬT TỰ CÂU KỂ — không đảo chủ ngữ, không dùng 'do'. "
 "Đây là chỗ người Việt sai nhiều nhất vì vẫn giữ dạng câu hỏi.",
 "Reported questions use statement word order, with no auxiliary 'do'.",
 [('She asked "Where do you live?"', "She asked where I lived.",
   "Bỏ 'do', đưa về trật tự câu kể."),
  ("He asked me what did I want.", "He asked me what I wanted.",
   "Giữ nguyên đảo ngữ là lỗi phổ biến nhất ở mục này."),
  ('They asked "Are you ready?"', "They asked if I was ready.",
   "Câu hỏi yes/no tường thuật bằng 'if' hoặc 'whether'."),
  ("I asked her that where she was going.", "I asked her where she was going.",
   "Không dùng 'that' khi đã có từ để hỏi.")]),

"gram_relative_nondefining": (
 "Mệnh đề quan hệ không xác định chỉ bổ sung thông tin phụ, luôn có dấu phẩy và "
 "KHÔNG dùng 'that'. Tiếng Việt không phân biệt hai loại mệnh đề này bằng dấu câu.",
 "Non-defining relative clauses take commas and never use 'that'.",
 [("My brother that lives in Hue is a teacher, he is my only brother.",
   "My brother, who lives in Hue, is a teacher.",
   "Chỉ có một người anh nên thông tin là phụ — phải có phẩy và dùng 'who'."),
  ("The Hoan Kiem Lake which is in Hanoi attracts many visitors.",
   "Hoan Kiem Lake, which is in Hanoi, attracts many visitors.",
   "Tên riêng đã xác định nên mệnh đề phải không xác định."),
  ("Our manager, that joined last year, is leaving.",
   "Our manager, who joined last year, is leaving.",
   "Mệnh đề không xác định không dùng 'that'."),
  ("She passed the exam which surprised everyone.",
   "She passed the exam, which surprised everyone.",
   "Thiếu phẩy làm đổi nghĩa: thành ra 'kỳ thi khiến mọi người ngạc nhiên'.")]),

"gram_relative_reduced": (
 "Mệnh đề quan hệ rút gọn bỏ đại từ quan hệ và 'be', giữ lại V-ing (chủ động) hoặc "
 "phân từ hai (bị động). Người học thường rút sai vế hoặc rút cả khi không được.",
 "Reduced relatives keep a participle: -ing for active, -ed for passive.",
 [("The man who is standing there is our client.",
   "The man standing there is our client.",
   "Rút gọn chủ động giữ V-ing."),
  ("The report which was written by Ha is ready.",
   "The report written by Ha is ready.",
   "Rút gọn bị động giữ phân từ hai."),
  ("The people waited outside were angry.", "The people waiting outside were angry.",
   "Rút sai vế: 'waited' làm câu thành ra hai động từ chính."),
  ("The letter sending yesterday has arrived.",
   "The letter sent yesterday has arrived.",
   "Thư ĐƯỢC gửi nên phải phân từ hai.")]),

"gram_infinitive_purpose": (
 "Chỉ mục đích dùng 'to + động từ'. Người Việt hay dùng 'for + V-ing' vì tiếng "
 "Việt nói 'để làm gì' và 'cho việc gì' gần như nhau.",
 "Purpose is expressed with 'to + infinitive', not 'for + -ing'.",
 [("I came here for learning English.", "I came here to learn English.",
   "Mục đích của hành động dùng 'to + V'."),
  ("She went to the shop for buy some milk.",
   "She went to the shop to buy some milk.",
   "Sau 'for' không thể là động từ nguyên thể."),
  ("This tool is used to cutting metal.", "This tool is used to cut metal.",
   "Sau 'to' chỉ mục đích là nguyên thể."),
  ("He works hard for to support his family.",
   "He works hard to support his family.",
   "Không dùng cả 'for' lẫn 'to'.")]),

"gram_gerund_as_subject": (
 "Động từ làm chủ ngữ phải chuyển sang V-ing. Tiếng Việt để động từ nguyên dạng ở "
 "vị trí chủ ngữ ('Đọc sách rất tốt') nên người học viết thẳng động từ nguyên thể.",
 "A verb used as subject takes the -ing form.",
 [("Read books is good for you.", "Reading books is good for you.",
   "Chủ ngữ là hành động thì dùng V-ing."),
  ("Swimming are my favourite sport.", "Swimming is my favourite sport.",
   "Danh động từ làm chủ ngữ luôn là số ít."),
  ("To smoke is banned in this building.", "Smoking is banned in this building.",
   "Cả hai đều đúng ngữ pháp nhưng V-ing tự nhiên hơn hẳn ở vị trí chủ ngữ."),
  ("I enjoy to read in the evening.", "I enjoy reading in the evening.",
   "'enjoy' luôn đi với V-ing.")]),

"gram_adj_ed_ing": (
 "-ed mô tả CẢM GIÁC của người, -ing mô tả TÍNH CHẤT của vật gây ra cảm giác đó. "
 "Tiếng Việt dùng một từ 'chán' cho cả hai nên đây là lỗi kinh điển.",
 "-ed adjectives describe how someone feels; -ing adjectives describe what causes it.",
 [("I am very boring in this class.", "I am very bored in this class.",
   "'boring' nghĩa là bản thân người nói nhạt nhẽo — nghĩa hoàn toàn khác ý định."),
  ("The film was very interested.", "The film was very interesting.",
   "Phim là thứ GÂY hứng thú nên dùng -ing."),
  ("She was surprising by the news.", "She was surprised by the news.",
   "Người nhận cảm giác dùng -ed."),
  ("The results were disappointed.", "The results were disappointing.",
   "Kết quả gây thất vọng nên dùng -ing.")]),

"gram_phrasal_verb_inseparable": (
 "Cụm động từ không tách được thì tân ngữ luôn đứng SAU giới từ, kể cả khi tân ngữ "
 "là đại từ. Tiếng Việt không có loại cấu trúc này nên phải học thuộc từng cụm.",
 "Inseparable phrasal verbs keep the object after the particle.",
 [("I ran my old teacher into at the station.",
   "I ran into my old teacher at the station.",
   "'run into' không tách được."),
  ("She looks her younger sister after every day.",
   "She looks after her younger sister every day.",
   "'look after' không tách được."),
  ("We need to deal it with immediately.", "We need to deal with it immediately.",
   "Kể cả đại từ cũng đứng sau giới từ."),
  ("He got the flu over quickly.", "He got over the flu quickly.",
   "'get over' không tách được.")]),

"gram_conjunction_coordinating": (
 "Tiếng Việt cho phép 'Tuy ... nhưng ...' và 'Vì ... nên ...' đi thành cặp. Tiếng "
 "Anh chỉ dùng MỘT liên từ cho một quan hệ — dùng cả cặp là lỗi rất dễ nhận ra.",
 "English uses one conjunction per relationship, never a pair.",
 [("Although it was raining, but we went out.",
   "Although it was raining, we went out.",
   "Dịch thẳng cặp 'Tuy ... nhưng ...'. Bỏ 'but'."),
  ("Because he was late, so the meeting started without him.",
   "Because he was late, the meeting started without him.",
   "Dịch thẳng cặp 'Vì ... nên ...'. Bỏ 'so'."),
  ("She is tired, she keeps working.", "She is tired, but she keeps working.",
   "Ngược lại: quan hệ tương phản thì bắt buộc có liên từ."),
  ("I like tea and I don't like coffee.", "I like tea but I don't like coffee.",
   "Quan hệ tương phản dùng 'but', không dùng 'and'.")]),

"gram_word_form_adverb": (
 "Trạng từ tiếng Anh thường có đuôi -ly. Tiếng Việt dùng cùng một từ cho tính từ "
 "và trạng từ ('nhanh') nên người học hay để nguyên dạng tính từ.",
 "Most English adverbs are formed by adding -ly to the adjective.",
 [("He drives very careful.", "He drives very carefully.",
   "Bổ nghĩa cho động từ thì phải là trạng từ."),
  ("She speaks English fluent.", "She speaks English fluently.",
   "Cùng lỗi: tính từ đứng nguyên ở vị trí cần trạng từ."),
  ("The team worked hardly all week.", "The team worked hard all week.",
   "'hardly' nghĩa là 'hầu như không' — nghĩa ngược hẳn. Bẫy nguy hiểm nhất mục này."),
  ("He arrived lately for the third time.", "He arrived late for the third time.",
   "'lately' nghĩa là 'dạo gần đây', không phải 'muộn'.")]),

"gram_prefix_negative": (
 "Tiền tố phủ định (un-, in-, im-, ir-, dis-) không chọn tự do — mỗi từ gốc đi với "
 "một tiền tố cố định. Tiếng Việt chỉ có 'không' và 'bất' nên không có gì để đối chiếu.",
 "Negative prefixes are fixed per word and cannot be chosen freely.",
 [("The instructions were unclear and unpossible to follow.",
   "The instructions were unclear and impossible to follow.",
   "'possible' đi với 'im-', không phải 'un-'."),
  ("His answer was inpolite.", "His answer was impolite.",
   "Trước 'p' thì 'in-' biến thành 'im-'."),
  ("The data seems unrelevant to the question.",
   "The data seems irrelevant to the question.",
   "Trước 'r' thì dùng 'ir-'."),
  ("I unagree with that conclusion.", "I disagree with that conclusion.",
   "Động từ 'agree' đi với 'dis-'.")]),

"gram_question_tag": (
 "Câu hỏi đuôi đảo dấu so với mệnh đề chính và lặp lại đúng trợ động từ. Tiếng "
 "Việt chỉ cần thêm 'phải không' cho mọi câu, nên người học không quen phải tính toán.",
 "Tags reverse the polarity of the main clause and repeat its auxiliary.",
 [("You are coming tonight, aren't you coming?",
   "You are coming tonight, aren't you?",
   "Đuôi chỉ gồm trợ động từ và đại từ."),
  ("She works here, doesn't it?", "She works here, doesn't she?",
   "Đại từ trong đuôi phải khớp chủ ngữ."),
  ("He can't drive, can't he?", "He can't drive, can he?",
   "Mệnh đề phủ định thì đuôi khẳng định."),
  ("Let's start now, shall we not?", "Let's start now, shall we?",
   "'Let's' luôn đi với 'shall we'.")]),

"gram_inversion_negative": (
 "Khi trạng từ phủ định đứng đầu câu, tiếng Anh đảo trợ động từ lên trước chủ ngữ. "
 "Tiếng Việt đưa 'Không bao giờ' lên đầu mà trật tự phần còn lại giữ nguyên.",
 "A fronted negative adverbial triggers subject-auxiliary inversion.",
 [("Never I have seen such a mistake.", "Never have I seen such a mistake.",
   "Trạng từ phủ định đứng đầu thì đảo trợ động từ."),
  ("Rarely the company changes its policy.",
   "Rarely does the company change its policy.",
   "Không có trợ động từ sẵn thì mượn 'do/does/did'."),
  ("Not only she speaks French, but also German.",
   "Not only does she speak French, but also German.",
   "'Not only' đầu câu cũng gây đảo ngữ."),
  ("Under no circumstances you should open the door.",
   "Under no circumstances should you open the door.",
   "Cụm giới từ mang nghĩa phủ định cũng gây đảo ngữ.")]),

"gram_present_perfect_continuous": (
 "Hiện tại hoàn thành tiếp diễn nhấn mạnh QUÁ TRÌNH kéo dài tới hiện tại, khác với "
 "hiện tại hoàn thành nhấn mạnh KẾT QUẢ. Tiếng Việt dùng chung 'đã ... được' nên "
 "ranh giới này gần như vô hình với người học.",
 "Present perfect continuous stresses duration; present perfect stresses result.",
 [("I am working here for five years.", "I have been working here for five years.",
   "Việc bắt đầu trong quá khứ và còn tiếp diễn thì không dùng hiện tại tiếp diễn."),
  ("She has been finishing the report.", "She has finished the report.",
   "Việc đã xong hẳn thì dùng hiện tại hoàn thành, không phải tiếp diễn."),
  ("How long are you waiting?", "How long have you been waiting?",
   "Hỏi về khoảng thời gian tính tới hiện tại."),
  ("He has been knowing her since 2019.", "He has known her since 2019.",
   "Động từ trạng thái không dùng dạng tiếp diễn.")]),

"gram_future_perfect_continuous": (
 "Tương lai hoàn thành đặt mốc 'xong TRƯỚC một thời điểm tương lai'; tương lai tiếp "
 "diễn đặt mốc 'đang diễn ra VÀO một thời điểm tương lai'. Tiếng Việt không có "
 "phương tiện ngữ pháp nào cho hai sắc thái này.",
 "Future perfect: completed before a future point. Future continuous: in progress at it.",
 [("By next June I will work here for ten years.",
   "By next June I will have been working here for ten years.",
   "'By next June' đặt mốc tương lai nên phải dùng dạng hoàn thành."),
  ("This time tomorrow I will fly to Singapore.",
   "This time tomorrow I will be flying to Singapore.",
   "Đang diễn ra tại một thời điểm tương lai thì dùng tương lai tiếp diễn."),
  ("She will have finish the course by December.",
   "She will have finished the course by December.",
   "Sau 'will have' là phân từ hai."),
  ("They will have been arrived by six.", "They will have arrived by six.",
   "'arrive' là việc tức thời, không dùng dạng tiếp diễn ở đây.")]),

"gram_modal_permission_request": (
 "Xin phép và nhờ vả có thang độ lịch sự: can < could < may < would you mind. "
 "Tiếng Việt điều chỉnh lịch sự bằng tiểu từ cuối câu ('ạ', 'nhé') chứ không bằng "
 "động từ, nên người học hay dùng 'can' ở cả tình huống trang trọng.",
 "Politeness in English is carried by the modal itself, not by sentence-final particles.",
 [("Can I to leave early today?", "Can I leave early today?",
   "Sau khuyết thiếu là động từ nguyên thể không 'to'."),
  ("Do you mind to open the window?", "Do you mind opening the window?",
   "Sau 'mind' là V-ing."),
  ("Would you mind if I will use your phone?",
   "Would you mind if I used your phone?",
   "Sau 'Would you mind if' dùng quá khứ đơn cho lịch sự."),
  ("May you pass me the salt?", "Could you pass me the salt?",
   "'may' dùng để XIN phép cho bản thân, không dùng để nhờ người khác.")]),

"gram_modal_deduction": (
 "must / might / can't dùng để suy đoán theo mức độ chắc chắn. Tiếng Việt dùng "
 "'chắc là', 'có lẽ', 'không thể nào' — là trạng từ chứ không phải khuyết thiếu, "
 "nên người học ít khi tự nghĩ tới cấu trúc này.",
 "must = near certainty, might = possibility, can't = impossibility.",
 [("He must to be at home now.", "He must be at home now.",
   "Sau khuyết thiếu là nguyên thể không 'to'."),
  ("She mustn't be the manager, she is far too young.",
   "She can't be the manager, she is far too young.",
   "Suy đoán phủ định dùng 'can't'; 'mustn't' nghĩa là cấm."),
  ("It maybe rains later.", "It might rain later.",
   "'maybe' là trạng từ, 'may be' mới là khuyết thiếu + be."),
  ("They must knowing about the change.", "They must know about the change.",
   "Sau khuyết thiếu không dùng V-ing.")]),

"gram_modal_perfect": (
 "Khuyết thiếu hoàn thành (must have, should have, could have) suy đoán hoặc tiếc "
 "nuối về QUÁ KHỨ. Tiếng Việt gói trọn trong 'lẽ ra' và 'chắc là đã'.",
 "Modal + have + past participle refers to the past.",
 [("You should have told me earlier, but you didn't tell.",
   "You should have told me earlier.",
   "Cấu trúc đã hàm ý việc không xảy ra, không cần nói thêm."),
  ("He must has forgotten the meeting.", "He must have forgotten the meeting.",
   "Sau khuyết thiếu luôn là 'have', không chia."),
  ("She could have came if she wanted.", "She could have come if she wanted.",
   "Sau 'have' là phân từ hai, không phải quá khứ đơn."),
  ("I shouldn't have went there.", "I shouldn't have gone there.",
   "Cùng lỗi phân từ hai với động từ bất quy tắc.")]),

"gram_passive_perfect_modal": (
 "Bị động ghép với hoàn thành hoặc khuyết thiếu tạo chuỗi ba bốn thành phần. Người "
 "học thường rơi mất một mắt xích giữa chừng.",
 "Perfect and modal passives stack: have been + participle, modal + be + participle.",
 [("The decision has already made.", "The decision has already been made.",
   "Thiếu 'been' — quyết định không tự đưa ra được."),
  ("The form must completed before Friday.",
   "The form must be completed before Friday.",
   "Sau khuyết thiếu bị động phải có 'be'."),
  ("The building had been destroy in the fire.",
   "The building had been destroyed in the fire.",
   "Thiếu phân từ hai ở cuối chuỗi."),
  ("It should have been sent last week, but nobody sent.",
   "It should have been sent last week.",
   "Chuỗi 'should have been + phân từ hai' đã đủ nghĩa.")]),

"gram_reported_backshift": (
 "Lùi thì là bắt buộc khi động từ tường thuật ở quá khứ, NHƯNG không lùi khi nội "
 "dung vẫn đúng ở hiện tại. Người học được dạy quy tắc lùi thì rồi áp máy móc cho "
 "mọi trường hợp.",
 "Backshift is required after a past reporting verb, but optional for still-true facts.",
 [('He said "Water boils at 100 degrees".',
   "He said water boils at 100 degrees.",
   "Sự thật vẫn đúng thì không cần lùi thì."),
  ('She says "I am busy".', "She says she is busy.",
   "Động từ tường thuật ở hiện tại thì không lùi thì."),
  ('He said "I saw her yesterday".', "He said he had seen her the day before.",
   "Quá khứ đơn lùi thành quá khứ hoàn thành, và 'yesterday' đổi theo."),
  ('She said "I must go now".', "She said she had to go then.",
   "'must' lùi thành 'had to'; 'now' đổi thành 'then'.")]),

"gram_relative_preposition": (
 "Trong văn phong trang trọng, giới từ đứng TRƯỚC đại từ quan hệ và bắt buộc dùng "
 "whom / which. Tiếng Việt không có đại từ quan hệ nên toàn bộ cấu trúc này là mới.",
 "In formal style the preposition precedes whom/which; 'that' is then impossible.",
 [("The colleague who I spoke to is on leave.",
   "The colleague to whom I spoke is on leave.",
   "Giới từ lên trước thì bắt buộc dùng 'whom'."),
  ("The report in that we found the error is here.",
   "The report in which we found the error is here.",
   "Sau giới từ không dùng 'that'."),
  ("The company for which he works for is Kelbrook.",
   "The company for which he works is Kelbrook.",
   "Không lặp giới từ ở cả hai vị trí."),
  ("The reason for who she resigned is unclear.",
   "The reason for which she resigned is unclear.",
   "'reason' là vật nên dùng 'which'.")]),

"gram_participle_clause": (
 "Mệnh đề phân từ rút gọn hai câu thành một, nhưng chủ ngữ của phân từ phải TRÙNG "
 "với chủ ngữ mệnh đề chính. Không trùng thì thành 'dangling participle' — lỗi khiến "
 "câu buồn cười.",
 "The participle's implied subject must match the subject of the main clause.",
 [("Walking to the office, the rain started.",
   "While I was walking to the office, the rain started.",
   "Như đang viết là 'cơn mưa đi bộ tới văn phòng'."),
  ("Having finished the report, the printer broke.",
   "Having finished the report, I found that the printer had broken.",
   "Chủ ngữ phải là người hoàn thành báo cáo."),
  ("Being late, the meeting had started.", "Being late, I found the meeting had started.",
   "Cùng lỗi chủ ngữ treo."),
  ("Written in 1890, we still read the book today.",
   "Written in 1890, the book is still read today.",
   "Cuốn sách mới là thứ được viết năm 1890, không phải 'chúng ta'.")]),

"gram_noun_clause": (
 "Mệnh đề danh từ đóng vai chủ ngữ hoặc tân ngữ và giữ TRẬT TỰ CÂU KỂ. Người Việt "
 "hay giữ dạng câu hỏi khi mệnh đề bắt đầu bằng từ để hỏi.",
 "Noun clauses keep statement word order even when they begin with a wh-word.",
 [("I don't know where is the station.", "I don't know where the station is.",
   "Mệnh đề danh từ không đảo ngữ."),
  ("Can you tell me what time does the shop open?",
   "Can you tell me what time the shop opens?",
   "Bỏ 'does' và đưa về trật tự câu kể."),
  ("That he was late it surprised everyone.",
   "That he was late surprised everyone.",
   "Mệnh đề đã làm chủ ngữ thì không thêm 'it'."),
  ("I wonder if will she come.", "I wonder if she will come.",
   "Sau 'if' cũng giữ trật tự câu kể.")]),

"gram_comparative_adv": (
 "So sánh của trạng từ theo cùng quy tắc độ dài như tính từ, nhưng trạng từ -ly luôn "
 "dùng 'more'. Người học hay gắn -er vào cả trạng từ dài.",
 "Adverbs ending in -ly always take 'more' in the comparative.",
 [("She speaks more clear than her colleague.",
   "She speaks more clearly than her colleague.",
   "Bổ nghĩa động từ thì phải là trạng từ."),
  ("He runs more fast than me.", "He runs faster than me.",
   "'fast' là trạng từ ngắn nên dùng -er."),
  ("They finished the work quicklier than expected.",
   "They finished the work more quickly than expected.",
   "Trạng từ -ly luôn dùng 'more'."),
  ("She works more hard than anyone.", "She works harder than anyone.",
   "'hard' là trạng từ ngắn, dùng -er.")]),

"gram_double_comparative": (
 "So sánh kép có hai khuôn: 'the + so sánh hơn, the + so sánh hơn' và 'so sánh hơn "
 "and so sánh hơn'. Tiếng Việt nói 'càng ... càng ...' rất tự nhiên nên người học "
 "hiểu nghĩa nhưng hay quên 'the'.",
 "'The + comparative, the + comparative' and 'comparative and comparative'.",
 [("More you practise, better you get.", "The more you practise, the better you get.",
   "Cả hai vế đều bắt buộc có 'the'."),
  ("The weather is getting cold and cold.",
   "The weather is getting colder and colder.",
   "Khuôn lặp phải dùng dạng so sánh hơn."),
  ("The more expensive, the more good the quality.",
   "The more expensive it is, the better the quality.",
   "'good' bất quy tắc thành 'better'."),
  ("The harder he tried, the more he failed more.",
   "The harder he tried, the more he failed.",
   "Không lặp 'more' hai lần trong một vế.")]),

"gram_adverb_formation": (
 "Không phải trạng từ nào cũng thêm -ly: có từ giữ nguyên dạng (fast, hard), có từ "
 "đổi chính tả (-y thành -ily), và có từ thêm -ly thì đổi nghĩa hẳn.",
 "Adverb formation is irregular: some keep the base form, some change spelling.",
 [("He did the job good.", "He did the job well.",
   "Trạng từ của 'good' là 'well', bất quy tắc hoàn toàn."),
  ("She reacted angryly.", "She reacted angrily.",
   "Tính từ kết thúc -y thì đổi thành -ily."),
  ("The train arrived fastly.", "The train arrived fast.",
   "'fast' giữ nguyên dạng khi làm trạng từ."),
  ("He works very hardly every day.", "He works very hard every day.",
   "'hardly' nghĩa 'hầu như không', dùng vào đây là nói ngược ý mình.")]),

"gram_adverb_degree": (
 "Trạng từ chỉ mức độ chia hai nhóm: nhóm đi với tính từ thường (very, quite) và "
 "nhóm đi với tính từ tuyệt đối (absolutely, completely). Ghép nhầm nhóm nghe rất "
 "sai với người bản ngữ dù ngữ pháp không hỏng.",
 "Gradable adjectives take 'very'; non-gradable ones take 'absolutely'.",
 [("The film was very excellent.", "The film was absolutely excellent.",
   "'excellent' đã là mức tuyệt đối nên không dùng 'very'."),
  ("I am absolutely tired after the trip.", "I am absolutely exhausted after the trip.",
   "'tired' là tính từ thường; muốn dùng 'absolutely' thì đổi sang từ tuyệt đối."),
  ("It was very impossible to finish in time.",
   "It was completely impossible to finish in time.",
   "'impossible' là tuyệt đối."),
  ("She was quite delighted, almost a little.", "She was quite delighted.",
   "'quite' với tính từ tuyệt đối nghĩa là 'hoàn toàn', không phải 'khá'.")]),

"gram_preposition_movement": (
 "Giới từ chuyển động (into, onto, through, across, along) khác với giới từ vị trí. "
 "Tiếng Việt dùng 'vào', 'qua' cho cả hai nên người học hay dùng 'in' thay 'into'.",
 "Movement prepositions differ from static ones: into vs in, onto vs on.",
 [("She walked in the room and sat down.", "She walked into the room and sat down.",
   "Có chuyển động từ ngoài vào trong thì dùng 'into'."),
  ("He put the box on the shelf carefully, lifting it up.",
   "He lifted the box onto the shelf carefully.",
   "Chuyển động lên bề mặt dùng 'onto'."),
  ("We drove trough the tunnel.", "We drove through the tunnel.",
   "Lỗi chính tả rất phổ biến: 'trough' là cái máng."),
  ("They swam cross the river.", "They swam across the river.",
   "Giới từ là 'across'; 'cross' là động từ.")]),

"gram_phrasal_verb_three_part": (
 "Cụm động từ ba thành phần (look forward to, put up with, get on with) là khối cố "
 "định — không tách, không đổi giới từ. Đặc biệt 'to' trong 'look forward to' là "
 "giới từ nên theo sau là V-ing.",
 "Three-part phrasal verbs are fixed; the final 'to' is a preposition, not an infinitive marker.",
 [("I look forward to hear from you.", "I look forward to hearing from you.",
   "'to' ở đây là giới từ nên theo sau là V-ing. Lỗi kinh điển trong thư công việc."),
  ("She can't put with his attitude up.", "She can't put up with his attitude.",
   "Cụm ba thành phần không tách được."),
  ("We need to catch on up the schedule.", "We need to catch up on the schedule.",
   "Trật tự cố định là catch up on."),
  ("He gets on well to his colleagues.", "He gets on well with his colleagues.",
   "Giới từ cuối là 'with', không thể thay.")]),

"gram_word_form_verb": (
 "Hậu tố tạo động từ (-ise/-ize, -en, -ify, en-) không suy ra được từ danh từ gốc. "
 "Tiếng Việt dùng cùng một từ cho cả danh từ và động từ nên không có manh mối nào.",
 "Verb-forming suffixes (-ise, -en, -ify) must be learned per word.",
 [("The company will modern its systems.", "The company will modernise its systems.",
   "'modern' là tính từ; động từ là 'modernise'."),
  ("We need to strong the security policy.", "We need to strengthen the security policy.",
   "Động từ của 'strong' là 'strengthen'."),
  ("Please clarity the second paragraph.", "Please clarify the second paragraph.",
   "'clarity' là danh từ; động từ là 'clarify'."),
  ("They will able the new feature next week.",
   "They will enable the new feature next week.",
   "Động từ là 'enable' với tiền tố en-.")]),

"gram_cleft_sentence": (
 "Câu chẻ ('It is X that ...', 'What ... is ...') dùng để nhấn mạnh một thành phần. "
 "Tiếng Việt nhấn mạnh bằng 'chính là', 'mới là' — đơn giản hơn nhiều nên người học "
 "hiếm khi tự dựng câu chẻ.",
 "Cleft sentences front the emphasised element: 'It is X that ...'.",
 [("It was John who did break the window.", "It was John who broke the window.",
   "Trong câu chẻ, động từ ở mệnh đề sau chia bình thường."),
  ("What I need is to have more time.", "What I need is more time.",
   "Sau 'What ... is' dùng thẳng danh ngữ."),
  ("It is on Monday when the meeting starts.",
   "It is on Monday that the meeting starts.",
   "Câu chẻ dùng 'that' kể cả với thời gian."),
  ("It was the report which caused the delay it.",
   "It was the report that caused the delay.",
   "Không lặp lại tân ngữ ở cuối.")]),

"gram_subjunctive_mandative": (
 "Sau các động từ chỉ yêu cầu, đề nghị (suggest, insist, demand, recommend), mệnh "
 "đề 'that' dùng động từ NGUYÊN THỂ cho mọi ngôi, kể cả ngôi thứ ba số ít. Đây là "
 "chỗ duy nhất trong tiếng Anh hiện đại bỏ -s một cách hệ thống.",
 "Mandative subjunctive: bare infinitive in the that-clause, regardless of person.",
 [("She suggested that he goes to the doctor.",
   "She suggested that he go to the doctor.",
   "Thức giả định dùng nguyên thể, không thêm -s."),
  ("They insisted that the report is submitted today.",
   "They insisted that the report be submitted today.",
   "Bị động ở thức giả định là 'be + phân từ hai'."),
  ("I recommend that she will take the earlier train.",
   "I recommend that she take the earlier train.",
   "Không dùng 'will' trong mệnh đề giả định."),
  ("The rule demands that everyone wears a badge.",
   "The rule demands that everyone wear a badge.",
   "'everyone' vẫn dùng nguyên thể trong thức giả định.")]),

"gram_quantifier_advanced": (
 "Lượng từ tiếng Anh phụ thuộc vào danh từ đếm được hay không đếm được và vào sắc thái "
 "khẳng định hoặc phủ định. Tiếng Việt thường dùng chung 'nhiều', 'ít', 'mỗi' nên người học "
 "dễ ghép một lượng từ với sai loại danh từ hoặc bỏ mất khác biệt giữa few và a few.",
 "Advanced quantifiers encode countability and meaning differences such as few versus a few.",
 [("There is many information in the report.", "There is much information in the report.",
   "'information' không đếm được nên dùng 'much', không dùng 'many'."),
  ("Only few employees completed the survey.", "Only a few employees completed the survey.",
   "'a few' nghĩa là vẫn có một số người; 'few' nhấn mạnh gần như không có ai."),
  ("Every of the applicants received an email.", "Each of the applicants received an email.",
   "Trước 'of the + danh từ số nhiều' phải dùng 'each', không dùng 'every'."),
  ("Neither options is suitable for the client.", "Neither option is suitable for the client.",
   "Sau 'neither' dùng danh từ số ít khi nói riêng từng lựa chọn.")]),

"gram_pronoun_reflexive": (
 "Đại từ phản thân dùng khi chủ ngữ và tân ngữ là cùng một người hoặc để nhấn mạnh. Tiếng Việt "
 "có thể dùng 'mình', 'bản thân' hoặc bỏ hẳn, nên người học thường dùng đại từ tân ngữ thông "
 "thường ở nơi tiếng Anh cần myself, yourself, himself và các dạng tương ứng.",
 "Reflexive pronouns are required when subject and object refer to the same participant.",
 [("She taught her to use the new software.", "She taught herself to use the new software.",
   "Người dạy và người học đều là 'she', vì vậy tân ngữ phải là 'herself'."),
  ("Please help you to some refreshments.", "Please help yourself to some refreshments.",
   "Cụm cố định là 'help yourself to', nghĩa là tự nhiên lấy đồ dùng."),
  ("We really enjoyed us at the conference.", "We really enjoyed ourselves at the conference.",
   "Cụm 'enjoy oneself' bắt buộc dùng đại từ phản thân phù hợp với chủ ngữ."),
  ("The device resets it every night.", "The device resets itself every night.",
   "Thiết bị tự đặt lại chính nó nên dùng 'itself', không dùng 'it'.")]),

"gram_present_perfect": (
 "Hiện tại hoàn thành nối một sự việc trong quá khứ với hiện tại: kinh nghiệm, kết quả hiện còn "
 "tác dụng hoặc khoảng thời gian chưa kết thúc. Tiếng Việt dùng các từ 'đã', 'rồi', 'từng' mà "
 "không đổi dạng động từ, nên người học thường giữ hiện tại đơn hoặc quá khứ đơn.",
 "The present perfect connects an earlier event or continuing situation with the present.",
 [("I live here for five years.", "I have lived here for five years.",
   "Khoảng thời gian bắt đầu trong quá khứ và còn tiếp tục đến nay cần hiện tại hoàn thành."),
  ("She just finished the monthly report.", "She has just finished the monthly report.",
   "'just' diễn tả kết quả rất gần hiện tại nên dùng 'has finished'."),
  ("They didn't arrive yet.", "They haven't arrived yet.",
   "'yet' trong câu phủ định về việc chưa xảy ra đến hiện tại đi với hiện tại hoàn thành."),
  ("Have you ever went to Canada?", "Have you ever gone to Canada?",
   "Sau 'have' phải dùng quá khứ phân từ 'gone', không dùng quá khứ đơn 'went'.")]),

"gram_time_clause_future": (
 "Trong mệnh đề thời gian nói về tương lai sau when, before, after, until, as soon as, tiếng Anh "
 "dùng hiện tại đơn chứ không dùng will. Tiếng Việt vẫn có thể lặp từ 'sẽ' ở cả hai vế nên người "
 "học thường đưa will vào mệnh đề phụ.",
 "Future time clauses use the present simple after when, before, until and similar conjunctions.",
 [("I will call you when I will arrive.", "I will call you when I arrive.",
   "Mệnh đề sau 'when' dùng hiện tại đơn dù toàn câu nói về tương lai."),
  ("Wait here until the manager will return.", "Wait here until the manager returns.",
   "Sau 'until' dùng hiện tại đơn 'returns', không dùng 'will return'."),
  ("Before she will leave, she will lock the office.",
   "Before she leaves, she will lock the office.",
   "Mệnh đề mở đầu bằng 'before' dùng hiện tại đơn để chỉ tương lai."),
  ("We will ship the order as soon as payment will clear.",
   "We will ship the order as soon as payment clears.",
   "Sau 'as soon as' dùng hiện tại đơn; will chỉ nằm ở mệnh đề chính.")]),

"gram_gerund_after_prep": (
 "Mọi động từ đứng ngay sau giới từ phải ở dạng V-ing, kể cả khi giới từ đó là 'to' trong một "
 "cụm cố định. Người Việt thường nhận diện 'to' như dấu hiệu của nguyên mẫu và chọn sai to-V.",
 "A verb immediately after a preposition takes the gerund form, including after prepositional to.",
 [("She is interested in learn data analysis.", "She is interested in learning data analysis.",
   "Sau giới từ 'in' phải dùng danh động từ 'learning'."),
  ("Check every figure before submit the report.", "Check every figure before submitting the report.",
   "'before' là giới từ trong cấu trúc này nên động từ theo sau ở dạng V-ing."),
  ("He left without check the final total.", "He left without checking the final total.",
   "Sau 'without' luôn dùng danh từ hoặc V-ing, ở đây là 'checking'."),
  ("I look forward to meet the new team.", "I look forward to meeting the new team.",
   "'to' trong 'look forward to' là giới từ nên phải theo sau bằng 'meeting'.")]),

"gram_verb_object_infinitive": (
 "Một số động từ cần cấu trúc động từ + tân ngữ + to-V, như ask someone to do, allow someone to "
 "do và remind someone to do. Tiếng Việt đặt hai động từ cạnh nhau mà không có dấu hiệu 'to', "
 "nên người học thường bỏ mất thành phần này.",
 "Verbs such as ask, allow and remind take an object followed by a to-infinitive.",
 [("The manager asked me send the revised file.", "The manager asked me to send the revised file.",
   "Sau 'ask + tân ngữ' phải có 'to' trước động từ nguyên mẫu."),
  ("The guard allowed us enter the building.", "The guard allowed us to enter the building.",
   "Cấu trúc đúng là 'allow someone to do something'."),
  ("Please remind him calling the supplier.", "Please remind him to call the supplier.",
   "'remind + tân ngữ' đi với to-V khi nhắc ai làm một việc."),
  ("They encouraged the interns apply early.", "They encouraged the interns to apply early.",
   "Sau 'encourage + tân ngữ' cần to-V, vì vậy phải có 'to apply'.")]),

"gram_preposition_time_advanced": (
 "Các giới từ thời gian nâng cao phân biệt điểm bắt đầu, độ dài và hạn cuối: since + mốc, for + "
 "khoảng, by + không muộn hơn, until + kéo dài tới. Tiếng Việt thường dùng chung 'từ', 'trong', "
 "'đến' nên người học dễ hoán đổi các giới từ này.",
 "Advanced time prepositions distinguish starting points, duration, deadlines and end points.",
 [("She has worked here since three years.", "She has worked here for three years.",
   "'three years' là khoảng thời gian nên dùng 'for', không dùng 'since'."),
  ("The office has been closed for Monday.", "The office has been closed since Monday.",
   "'Monday' là mốc bắt đầu nên dùng 'since'."),
  ("Please submit the form until Friday.", "Please submit the form by Friday.",
   "Hạn cuối nghĩa là không muộn hơn thứ Sáu nên dùng 'by'."),
  ("The support desk is open by six o'clock.", "The support desk is open until six o'clock.",
   "Hoạt động kéo dài liên tục tới sáu giờ nên dùng 'until'.")]),

"gram_phrasal_verb_separable": (
 "Với cụm động từ tách được, tân ngữ danh từ có thể đứng giữa hoặc sau cụm, nhưng đại từ bắt "
 "buộc đứng giữa động từ và tiểu từ. Tiếng Việt không có sự dịch chuyển này nên người học hay "
 "đặt it, them ở cuối cụm.",
 "With separable phrasal verbs, a pronoun object must appear between the verb and particle.",
 [("Please turn off it before leaving.", "Please turn it off before leaving.",
   "Đại từ 'it' phải đứng giữa 'turn' và 'off'."),
  ("Could you pick up them after lunch?", "Could you pick them up after lunch?",
   "Đại từ 'them' phải tách cụm 'pick up'."),
  ("Fill out it and return it today.", "Fill it out and return it today.",
   "Với tân ngữ đại từ, cấu trúc đúng là 'fill it out'."),
  ("The director put off it until next week.", "The director put it off until next week.",
   "Đại từ đứng giữa hai phần của cụm động từ tách được 'put off'.")]),

"gram_conjunction_subordinating": (
 "Liên từ phụ thuộc nối một mệnh đề chính với mệnh đề nêu nguyên nhân, nhượng bộ, điều kiện hoặc "
 "thời gian. Không được ghép đồng thời hai dấu nối mang cùng chức năng như although ... but; "
 "đồng thời phải phân biệt liên từ với cụm giới từ như because và because of.",
 "Subordinating conjunctions introduce dependent clauses and must match the structure that follows.",
 [("We stayed inside because the storm.", "We stayed inside because of the storm.",
   "Trước danh ngữ 'the storm' phải dùng cụm giới từ 'because of'."),
  ("Because of the supplier was late, production stopped.",
   "Because the supplier was late, production stopped.",
   "Trước một mệnh đề đầy đủ dùng 'because', không dùng 'because of'."),
  ("He left early so that he was feeling ill.", "He left early because he was feeling ill.",
   "Đây là nguyên nhân nên dùng 'because'; 'so that' diễn tả mục đích."),
  ("Despite the road was flooded, the bus continued.",
   "Although the road was flooded, the bus continued.",
   "Trước mệnh đề đầy đủ dùng 'although'; 'despite' phải đi với danh ngữ hoặc V-ing.")]),

"gram_linking_contrast": (
 "Các từ nối tương phản có cấu trúc khác nhau: although + mệnh đề, despite + danh ngữ/V-ing, "
 "however đứng tách bằng dấu câu. Dịch tất cả thành 'mặc dù/tuy nhiên' khiến người học dùng "
 "chúng thay thế cho nhau mà không đổi cấu trúc.",
 "Contrast linkers differ syntactically: although takes a clause, despite a noun phrase, and however punctuation.",
 [("Despite of the delay, the team finished on time.",
   "Despite the delay, the team finished on time.",
   "'despite' không đi với 'of'; chỉ 'in spite of' mới có giới từ này."),
  ("However the high cost, the company approved the plan.",
   "Despite the high cost, the company approved the plan.",
   "Trước danh ngữ dùng 'despite'; 'however' không trực tiếp đứng trước danh ngữ như vậy."),
  ("Although demand fell. However, revenue increased.",
   "Although demand fell, revenue increased.",
   "Mệnh đề although phải gắn với mệnh đề chính, không được kết thúc thành câu rời."),
  ("In spite the rain, the event continued.", "In spite of the rain, the event continued.",
   "Cụm cố định là 'in spite of', bắt buộc có 'of'.")]),

"gram_linking_cause_result": (
 "Từ nối nguyên nhân và kết quả phải phản ánh đúng hướng logic: because/as nêu nguyên nhân, "
 "so/therefore/as a result nêu kết quả. Tiếng Việt có thể nối lỏng bằng 'nên', 'vì vậy', khiến "
 "người học đặt because và so vào sai vế hoặc dùng because of trước một mệnh đề.",
 "Cause linkers introduce reasons, while result linkers introduce consequences.",
 [("The server failed because of it overheated.", "The server failed because it overheated.",
   "Trước mệnh đề 'it overheated' dùng 'because', không dùng 'because of'."),
  ("It rained heavily, because the event was cancelled.",
   "It rained heavily, so the event was cancelled.",
   "Vế sau là kết quả nên dùng 'so', không dùng từ nối nguyên nhân 'because'."),
  ("The road was closed due to it was flooded.", "The road was closed because it was flooded.",
   "'due to' đi với danh ngữ; trước mệnh đề đầy đủ phải dùng 'because'."),
  ("Sales increased; because, the company hired more staff.",
   "Sales increased; therefore, the company hired more staff.",
   "Sau dấu chấm phẩy cần trạng từ kết quả 'therefore', không dùng 'because'.")]),

"gram_linking_purpose": (
 "Mục đích có thể diễn tả bằng to/in order to + động từ hoặc so that + mệnh đề. Tiếng Việt dùng "
 "'để' cho cả hai kiểu nên người học thường ghép nhầm so that với nguyên mẫu hoặc dùng for + V.",
 "Purpose is expressed with to or in order to plus a verb, or so that plus a full clause.",
 [("We upgraded the system for reduce errors.", "We upgraded the system to reduce errors.",
   "Trước động từ chỉ mục đích dùng 'to', không dùng 'for'."),
  ("She spoke slowly so that help the new staff.",
   "She spoke slowly to help the new staff.",
   "Nếu không có chủ ngữ mới, dùng 'to help'; 'so that' phải mở đầu một mệnh đề."),
  ("He saved the file in order that send it later.",
   "He saved the file in order to send it later.",
   "Cấu trúc với nguyên mẫu là 'in order to + động từ'."),
  ("They left early for they could avoid traffic.",
   "They left early so that they could avoid traffic.",
   "Trước mệnh đề chỉ mục đích dùng 'so that', không dùng 'for' trong văn phong này.")]),

"gram_discourse_marker": (
 "Từ đánh dấu diễn ngôn tổ chức lập luận và cho người đọc biết quan hệ giữa các ý. Nhiều cụm là "
 "khối cố định không thể dịch từng từ từ tiếng Việt; sai một giới từ vẫn khiến câu nghe thiếu tự nhiên.",
 "Discourse markers are fixed expressions that organise arguments and signal relationships between ideas.",
 [("Firstly of all, we need to define the problem.",
   "First of all, we need to define the problem.",
   "Cụm mở đầu cố định là 'first of all', không phải 'firstly of all'."),
  ("By the contrary, the second proposal is affordable.",
   "On the contrary, the second proposal is affordable.",
   "Cụm cố định để bác bỏ ý trước là 'on the contrary'."),
  ("In the other words, the deadline is flexible.",
   "In other words, the deadline is flexible.",
   "Cụm diễn đạt lại là 'in other words', không có mạo từ 'the'."),
  ("From one hand, automation saves time.",
   "On the one hand, automation saves time.",
   "Cặp đánh dấu hai mặt bắt đầu bằng 'on the one hand'.")]),

"gram_word_form_adj": (
 "Tính từ tiếng Anh thường được tạo bằng hậu tố -ful, -ous, -able, -al, -ive nhưng không thể "
 "đoán hoàn toàn từ danh từ gốc. Tiếng Việt ít biến đổi hình thái nên người học hay đặt thẳng "
 "danh từ trước một danh từ khác ở nơi cần tính từ.",
 "Adjective-forming suffixes must be selected for each word and cannot be replaced by the base noun.",
 [("The launch was a success project.", "The launch was a successful project.",
   "Trước danh từ 'project' cần tính từ 'successful', không dùng danh từ 'success'."),
  ("This is danger equipment for untrained staff.",
   "This is dangerous equipment for untrained staff.",
   "Tính từ của 'danger' là 'dangerous'."),
  ("We need a rely supplier for this component.",
   "We need a reliable supplier for this component.",
   "Trước danh từ cần tính từ 'reliable', không dùng động từ 'rely'."),
  ("The team proposed an economy solution.",
   "The team proposed an economical solution.",
   "'economical' nghĩa là tiết kiệm và là tính từ phù hợp trước 'solution'.")]),

"gram_negation": (
 "Tiếng Anh tạo phủ định bằng trợ động từ do/does/did hoặc đặt not sau be và động từ khuyết "
 "thiếu. Tiếng Việt chỉ đặt 'không' trước động từ, nên người học hay dịch trực tiếp thành no/not "
 "mà không thêm hoặc không chia trợ động từ.",
 "English negation uses an auxiliary plus not; lexical verbs do not take not directly.",
 [("I no understand the new procedure.", "I do not understand the new procedure.",
   "Động từ thường ở hiện tại cần trợ động từ 'do not' để tạo phủ định."),
  ("She doesn't knows the access code.", "She doesn't know the access code.",
   "Khi 'doesn't' đã mang dấu ngôi ba, động từ chính phải trở về nguyên mẫu."),
  ("They not are ready for the inspection.", "They are not ready for the inspection.",
   "Với động từ be, 'not' đứng sau dạng đã chia 'are'."),
  ("He didn't submitted the expense claim.", "He didn't submit the expense claim.",
   "Sau 'did not' phải dùng nguyên mẫu 'submit', không dùng quá khứ 'submitted'.")]),
}
