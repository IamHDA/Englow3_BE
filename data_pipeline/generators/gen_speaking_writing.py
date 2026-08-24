#!/usr/bin/env python3
"""Speaking & Writing — 11 + 8 task theo định dạng TOEIC S&W, viết tay.

Nội dung viết mới hoàn toàn (§0.4). Tên công ty và người đều hư cấu.

Rubric tách riêng, task chỉ giữ `rubric_ref` (§2.6). Mỗi chiều chấm có đủ band
0–5, mỗi band một mô tả riêng — không được để trống band nào (DoD Phase 9).

    python generators/gen_speaking_writing.py
"""

from __future__ import annotations

import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from guarded_write import guarded_write_batch  # noqa: E402
from schemas import (  # noqa: E402
    AudioAsset, BatchMetadata, ModuleType, Rubric, SpeakingBatch, SpeakingTask,
    WritingBatch, WritingTask,
)
from schemas.enums import Accent  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT_SP = ROOT / "output" / "speaking_writing" / "speaking_batch_001.json"
OUT_WR = ROOT / "output" / "speaking_writing" / "writing_batch_001.json"
GENERATED_BY = "claude-opus-5"

# ---------------------------------------------------------------------------
# RUBRIC — band 0–5 cho từng chiều. Mô tả viết theo cái giám khảo QUAN SÁT
# được, không phải theo cảm nhận chung chung ("khá tốt", "tạm ổn").
# ---------------------------------------------------------------------------

SPEAKING_DIMS = [
    ("pronunciation", 0.20, "sp_pronunciation", [
        ("No intelligible speech is produced.", "Không tạo ra lời nói nào nghe hiểu được."),
        ("Most words are unintelligible; the listener cannot follow.",
         "Phần lớn từ không nghe ra; người nghe không theo được."),
        ("Frequent sound errors force the listener to re-interpret whole phrases.",
         "Sai âm liên tục khiến người nghe phải đoán lại cả cụm."),
        ("Individual words are clear but final consonants and word stress are often dropped.",
         "Từng từ rõ nhưng thường nuốt phụ âm cuối và sai trọng âm từ."),
        ("Consistently clear; occasional slips do not affect understanding.",
         "Rõ ràng đều đặn; đôi chỗ sai nhỏ không ảnh hưởng việc hiểu."),
        ("Clear throughout, including consonant clusters and final sounds.",
         "Rõ suốt bài, kể cả cụm phụ âm và âm cuối."),
    ]),
    ("intonation_stress", 0.15, "sp_intonation_stress", [
        ("No speech to assess.", "Không có lời nói để đánh giá."),
        ("Flat delivery throughout; no sentence stress at all.",
         "Nói đều đều suốt bài, hoàn toàn không có trọng âm câu."),
        ("Stress falls on the wrong syllables often enough to obscure meaning.",
         "Trọng âm rơi sai âm tiết đủ nhiều để làm mờ nghĩa."),
        ("Some sentence stress present but rising and falling tones are used interchangeably.",
         "Có trọng âm câu nhưng ngữ điệu lên xuống dùng lẫn lộn."),
        ("Stress and intonation generally support meaning; minor flatness in long turns.",
         "Trọng âm và ngữ điệu nhìn chung hỗ trợ nghĩa; hơi đều ở lượt nói dài."),
        ("Stress and intonation are used deliberately to highlight key information.",
         "Dùng trọng âm và ngữ điệu có chủ đích để làm nổi thông tin chính."),
    ]),
    ("fluency", 0.20, "sp_fluency", [
        ("No response.", "Không có phản hồi."),
        ("Speech breaks down after a few words.", "Lời nói đứt đoạn sau vài từ."),
        ("Long pauses before most clauses; frequent restarts.",
         "Ngừng lâu trước hầu hết mệnh đề; nói lại từ đầu nhiều lần."),
        ("Audible hesitation while searching for words, but the turn is completed.",
         "Có ngập ngừng thấy rõ khi tìm từ, nhưng vẫn nói hết lượt."),
        ("Speech flows with only brief natural pauses; self-corrections are quick.",
         "Nói trôi chảy, chỉ ngừng ngắn tự nhiên; tự sửa nhanh gọn."),
        ("Sustained natural pace for the full response time.",
         "Giữ nhịp nói tự nhiên suốt thời lượng yêu cầu."),
    ]),
    ("grammar", 0.15, "sp_grammar", [
        ("No language produced.", "Không tạo ra ngôn ngữ nào."),
        ("Isolated words only; no clause structure.",
         "Chỉ có từ rời rạc, không thành mệnh đề."),
        ("Simple sentences with errors in tense and agreement that obscure meaning.",
         "Câu đơn có lỗi thì và hoà hợp chủ vị làm mờ nghĩa."),
        ("Simple sentences are mostly accurate; complex structures break down.",
         "Câu đơn phần lớn đúng; câu phức thì hỏng."),
        ("Both simple and complex sentences used accurately; slips are self-corrected.",
         "Dùng đúng cả câu đơn lẫn câu phức; sai thì tự sửa được."),
        ("A wide range of structures used accurately and appropriately.",
         "Dùng đa dạng cấu trúc, chính xác và phù hợp ngữ cảnh."),
    ]),
    ("vocabulary", 0.15, "sp_vocabulary", [
        ("No vocabulary produced.", "Không có từ vựng nào."),
        ("A handful of memorised words, used regardless of context.",
         "Vài từ học thuộc, dùng bất kể ngữ cảnh."),
        ("Very limited range; the same words are repeated throughout.",
         "Vốn từ rất hạn chế; lặp đi lặp lại cùng vài từ."),
        ("Adequate for everyday topics; noticeable repetition on work topics.",
         "Đủ cho chủ đề đời thường; lặp từ thấy rõ ở chủ đề công việc."),
        ("Precise word choice with some natural collocations.",
         "Chọn từ chính xác, có dùng collocation tự nhiên."),
        ("Wide range used precisely, including idiomatic business expressions.",
         "Vốn từ rộng, dùng chính xác, kể cả thành ngữ trong công việc."),
    ]),
    ("content", 0.15, "sp_content", [
        ("Nothing relevant is said.", "Không nói được gì liên quan."),
        ("A single fragment loosely connected to the prompt.",
         "Một mẩu rời rạc, liên hệ lỏng lẻo với đề."),
        ("Addresses the prompt but with no supporting reason or example.",
         "Có trả lời đề nhưng không có lý do hay ví dụ nào."),
        ("Answers the prompt with one reason; the response ends early.",
         "Trả lời đề kèm một lý do; kết thúc sớm hơn thời lượng."),
        ("Clear position supported by reasons and at least one example.",
         "Nêu rõ quan điểm, có lý do và ít nhất một ví dụ."),
        ("Fully developed answer using the whole response time, with specific detail.",
         "Ý triển khai đầy đủ, dùng hết thời lượng, có chi tiết cụ thể."),
    ]),
]

WRITING_DIMS = [
    ("task_response", 0.25, "wr_task_response", [
        ("Nothing written.", "Không viết gì."),
        ("Text is unrelated to the task.", "Bài viết không liên quan tới đề."),
        ("Only part of the task is addressed; word count far below the minimum.",
         "Chỉ đáp ứng một phần đề; số từ thấp hơn nhiều so với mức tối thiểu."),
        ("All parts touched on, but one is answered in a single clause.",
         "Có chạm tới mọi phần, nhưng một phần chỉ trả lời bằng một mệnh đề."),
        ("All parts of the task are answered with adequate development.",
         "Trả lời đủ mọi phần của đề, triển khai vừa đủ."),
        ("All parts answered thoroughly, with detail that fits the reader and purpose.",
         "Trả lời đủ và sâu mọi phần, chi tiết phù hợp người đọc và mục đích."),
    ]),
    ("organization", 0.20, "wr_organization", [
        ("Nothing written.", "Không viết gì."),
        ("A single undivided block with no discernible order.",
         "Một khối liền không xuống dòng, không nhận ra trình tự."),
        ("Ideas appear in a random order; no opening or closing.",
         "Ý xuất hiện lộn xộn; không có mở hay kết."),
        ("Recognisable opening and closing, but the middle mixes unrelated points.",
         "Có mở và kết nhận ra được, nhưng phần giữa trộn lẫn các ý rời."),
        ("Clear paragraphs, each with one main idea.",
         "Đoạn văn rõ ràng, mỗi đoạn một ý chính."),
        ("Structure serves the argument: each paragraph builds on the previous one.",
         "Bố cục phục vụ lập luận: mỗi đoạn xây trên đoạn trước."),
    ]),
    ("coherence", 0.15, "wr_coherence", [
        ("Nothing written.", "Không viết gì."),
        ("Sentences do not connect to one another.", "Các câu không nối được với nhau."),
        ("Linking words are absent or used incorrectly.",
         "Thiếu từ nối hoặc dùng sai từ nối."),
        ("Basic linkers are correct but overused; pronoun reference is sometimes unclear.",
         "Từ nối cơ bản dùng đúng nhưng lặp nhiều; tham chiếu đại từ đôi chỗ không rõ."),
        ("A range of linkers used accurately; reference is clear throughout.",
         "Dùng đa dạng từ nối chính xác; tham chiếu rõ suốt bài."),
        ("Cohesion is unobtrusive — the reader never has to reread to follow.",
         "Liên kết nhuần nhuyễn — người đọc không phải đọc lại để theo kịp."),
    ]),
    ("grammar", 0.15, "wr_grammar", [
        ("Nothing written.", "Không viết gì."),
        ("No complete sentence is formed.", "Không câu nào hoàn chỉnh."),
        ("Errors in basic tense and agreement appear in most sentences.",
         "Lỗi thì và hoà hợp chủ vị cơ bản xuất hiện ở phần lớn câu."),
        ("Simple sentences are accurate; errors cluster in complex sentences.",
         "Câu đơn chính xác; lỗi dồn ở câu phức."),
        ("Both simple and complex sentences are accurate; errors are rare.",
         "Câu đơn và câu phức đều chính xác; lỗi hiếm."),
        ("A wide range of structures is used accurately and with control.",
         "Dùng đa dạng cấu trúc, chính xác và có kiểm soát."),
    ]),
    ("vocabulary", 0.15, "wr_vocabulary", [
        ("Nothing written.", "Không viết gì."),
        ("Vocabulary is insufficient to convey any message.",
         "Vốn từ không đủ để truyền đạt bất kỳ thông điệp nào."),
        ("Everyday words only; register is inappropriate for a work context.",
         "Chỉ dùng từ đời thường; văn phong không hợp bối cảnh công việc."),
        ("Adequate range; some word-choice errors and noticeable repetition.",
         "Vốn từ vừa đủ; có lỗi chọn từ và lặp từ thấy rõ."),
        ("Precise, appropriate word choice for a professional reader.",
         "Chọn từ chính xác, phù hợp với người đọc trong môi trường chuyên nghiệp."),
        ("Wide range used precisely, including collocations typical of business writing.",
         "Vốn từ rộng, dùng chính xác, gồm cả collocation đặc trưng văn viết công việc."),
    ]),
    ("mechanics", 0.10, "wr_mechanics", [
        ("Nothing written.", "Không viết gì."),
        ("Spelling and punctuation prevent the reader from decoding the text.",
         "Chính tả và dấu câu khiến người đọc không giải mã được bài."),
        ("Frequent spelling and capitalisation errors slow the reader down.",
         "Lỗi chính tả và viết hoa liên tục làm người đọc chậm lại."),
        ("Errors are noticeable but never block understanding; email format is incomplete.",
         "Lỗi thấy rõ nhưng không chặn việc hiểu; định dạng email chưa đủ."),
        ("Few errors; email conventions such as greeting and sign-off are observed.",
         "Ít lỗi; giữ đúng quy ước email như lời chào và ký tên."),
        ("Virtually error-free, with formatting appropriate to the document type.",
         "Gần như không lỗi, định dạng đúng với loại văn bản."),
    ]),
]


def build_rubric(name: str, dims) -> Rubric:
    return Rubric(name=name, dimensions=[
        {"name": n, "weight": w, "concept_id": cid,
         "band_descriptors": [
             {"band": b, "descriptor_en": en, "descriptor_vi": vi}
             for b, (en, vi) in enumerate(bands)]}
        for n, w, cid, bands in dims])


SP_RUBRIC = build_rubric("TOEIC-format Speaking rubric", SPEAKING_DIMS)
WR_RUBRIC = build_rubric("TOEIC-format Writing rubric", WRITING_DIMS)

ALL_SP = [c for _, _, c, _ in SPEAKING_DIMS]
ALL_WR = [c for _, _, c, _ in WRITING_DIMS]

MEDIA_BASE = "http://localhost:9000/images/toeic/speaking-writing"
TRAINING_SCHEDULE = (
    "TRAINING SCHEDULE — Kelbrook Manufacturing — Tuesday, 14 October\n"
    "09:00–10:30  Workplace Safety          Room B2    Mr Idris Fanshawe\n"
    "10:45–12:00  New Equipment Operation   Workshop 3 Mrs Marta Oyelaran\n"
    "13:00–14:30  Incident Reporting         Room B2    Mr Idris Fanshawe"
)

# ---------------------------------------------------------------------------
# SPEAKING — 11 task, thời lượng theo bảng §Phase 9
# ---------------------------------------------------------------------------
# (part, prompt, prep, response, concepts, difficulty, sample_answer_c1)
SPEAKING = [
    (1, "Read the following announcement aloud.\n\n"
        "Thank you for calling Brightwater Dental. Our clinic is open from eight "
        "in the morning until six in the evening, Monday through Friday. If you "
        "would like to book an appointment, press one. To speak with a member of "
        "our reception team, please hold and someone will be with you shortly.",
     45, 45, ["sp_pronunciation", "sp_intonation_stress"], 0.30,
     "Thank you for calling BRIGHTWATER Dental. // Our clinic is OPEN from EIGHT "
     "in the morning ↗ until SIX in the evening, ↘ Monday through Friday. // If "
     "you would like to book an APPOINTMENT, ↗ press ONE. ↘ // To speak with a "
     "member of our reCEPtion team, ↗ please HOLD ↗ and someone will be with you "
     "SHORTly. ↘\n"
     "[Ghi chú ăn điểm: ngắt sau mỗi mệnh đề, không ngắt giữa cụm danh từ. Lên "
     "giọng ở vế điều kiện, xuống giọng ở mệnh đề chính. Bật rõ phụ âm cuối của "
     "'press', 'hold', 'shortly'.]"),

    (2, "Read the following advertisement aloud.\n\n"
        "Are you spending too much on office supplies? At Verrow Stationery, we "
        "deliver everything your team needs, from paper to printer cartridges, "
        "directly to your door. Order before noon and we will deliver the same "
        "day. Visit our website today to claim your first month free.",
     45, 45, ["sp_pronunciation", "sp_intonation_stress"], 0.30,
     "Are you spending TOO MUCH on office supPLIES? ↗ // At VERrow Stationery, ↗ "
     "we deLIVer EVerything your team needs, ↗ from PAper to PRINTer cartridges, "
     "↗ directly to your DOOR. ↘ // ORder before NOON ↗ and we will deliver the "
     "SAME DAY. ↘ // VIsit our website toDAY ↗ to claim your FIRST MONTH FREE. ↘\n"
     "[Ghi chú ăn điểm: câu hỏi yes-no lên giọng cuối. Nhấn từ mang thông tin "
     "mới ('too much', 'same day', 'free'), lướt qua từ chức năng.]"),

    (3, "Describe the picture in as much detail as you can.\n\n"
        "[Ảnh: Bốn người ngồi quanh bàn họp trong phòng kính. Một phụ nữ đứng "
        "cạnh bảng trắng, tay cầm bút, đang chỉ vào biểu đồ cột. Trên bàn có "
        "laptop mở, vài cốc cà phê giấy và tập tài liệu.]",
     45, 30, ["sp_content", "sp_vocabulary", "sp_fluency"], 0.40,
     "This picture shows a team meeting in a glass-walled conference room. Four "
     "people are seated around a long table, and a woman is standing beside a "
     "whiteboard. She is holding a marker and pointing at a bar chart, so she "
     "appears to be presenting some kind of quarterly results. On the table I can "
     "see open laptops, several paper coffee cups and a stack of documents. The "
     "seated colleagues are looking towards the chart rather than at their "
     "screens, which suggests the presentation has just begun.\n"
     "[Ghi chú ăn điểm: mở bằng câu tổng quát về nơi chốn, rồi mới đi vào chi "
     "tiết. Dùng 'appears to be' và 'suggests' để suy luận chứ không khẳng định "
     "điều ảnh không cho thấy.]"),

    (4, "Describe the picture in as much detail as you can.\n\n"
        "[Ảnh: Ga tàu vào giờ cao điểm. Hành khách đứng sau vạch vàng trên sân "
        "ga, phần lớn cầm điện thoại. Bảng điện tử phía trên hiển thị giờ tàu. "
        "Một nhân viên mặc áo phản quang đang nói vào bộ đàm.]",
     45, 30, ["sp_content", "sp_vocabulary", "sp_fluency"], 0.40,
     "This is a busy railway platform, probably during the morning rush hour. A "
     "crowd of passengers is waiting behind the yellow safety line, and most of "
     "them are looking down at their phones. Above the platform there is an "
     "electronic display showing departure times, although I cannot read the "
     "details. On the right, a member of staff in a high-visibility jacket is "
     "speaking into a radio, which makes me think there may be a delay.\n"
     "[Ghi chú ăn điểm: nêu bối cảnh trước, chi tiết sau. Thừa nhận giới hạn "
     "('I cannot read the details') thay vì bịa — giám khảo tính đó là điểm mạnh.]"),

    (5, "Respond to the question as if you were speaking to a colleague.\n\n"
        "How do you usually get to work in the morning?",
     3, 15, ["sp_fluency", "sp_content"], 0.25,
     "I usually take the metro because it is far more predictable than driving. "
     "The journey takes about twenty-five minutes door to door, and I can read on "
     "the way.\n"
     "[Ghi chú ăn điểm: trả lời thẳng câu hỏi ở câu đầu, rồi thêm một lý do và "
     "một chi tiết cụ thể. Không mở bài dài dòng khi chỉ có 15 giây.]"),

    (6, "Respond to the question as if you were speaking to a colleague.\n\n"
        "What do you like most about the place where you live?",
     3, 15, ["sp_fluency", "sp_content"], 0.25,
     "What I like most is how quiet it gets in the evening. It is only two stops "
     "from the centre, but once you turn off the main road you barely hear any "
     "traffic.\n"
     "[Ghi chú ăn điểm: dùng câu chẻ 'What I like most is...' để nhấn ý chính "
     "ngay. Nêu một tương phản cụ thể thay vì tính từ chung chung như 'nice'.]"),

    (7, "Respond to the question as if you were speaking to a colleague.\n\n"
        "Would you rather work in an open-plan office or in a private room?",
     3, 30, ["sp_content", "sp_grammar"], 0.35,
     "I would rather work in a private room, mainly because most of my work "
     "involves writing and I lose the thread whenever there is background "
     "conversation. That said, I can see the advantage of an open-plan layout "
     "when a team needs to make decisions quickly — you simply turn around and "
     "ask. If I had to choose, I would take the quiet room and book a meeting "
     "space when collaboration is needed.\n"
     "[Ghi chú ăn điểm: chọn một phía rõ ràng, thừa nhận phía kia, rồi kết bằng "
     "cách dung hoà. Dùng 'would rather' và câu điều kiện loại 2 đúng chỗ.]"),

    (8, "Respond using the information provided.\n\n" + TRAINING_SCHEDULE +
        "\n\nQuestion: I heard the training is all in one room. Is that right?",
     45, 15, ["sp_content", "sp_grammar"], 0.40,
     "Not quite. Two of the three sessions are in Room B2, but the equipment "
     "session from ten forty-five to twelve takes place in Workshop 3, so you "
     "will need to move across after the safety session.\n"
     "[Ghi chú ăn điểm: sửa thông tin sai ngay bằng 'Not quite', rồi mới đưa dữ "
     "liệu đúng. Nêu hệ quả thực tế cho người hỏi.]"),

    (9, "Respond using the information provided.\n\n" + TRAINING_SCHEDULE +
        "\n\nQuestion: Who is running the afternoon session, and what is it about?",
     45, 15, ["sp_content"], 0.35,
     "The afternoon session runs from one o'clock to two thirty and it covers "
     "incident reporting procedures. It is led by Mr Idris Fanshawe, the same "
     "trainer who takes the safety session in the morning.\n"
     "[Ghi chú ăn điểm: trả lời cả hai vế của câu hỏi. Thêm một liên hệ hữu ích "
     "('the same trainer') mà bảng có nhưng người hỏi chưa nhận ra.]"),

    (10, "Respond using the information provided.\n\n" + TRAINING_SCHEDULE +
         "\n\nQuestion: I can only arrive at eleven. What will I miss, and what can I "
         "still attend?",
     45, 30, ["sp_content", "sp_grammar", "sp_fluency"], 0.45,
     "If you arrive at eleven, you will miss the whole safety session, which runs "
     "from nine until half past ten, and the first fifteen minutes of the "
     "equipment training in Workshop 3. You can still join the rest of that "
     "session until twelve, and the incident reporting session in the afternoon "
     "starts at one, so you would attend that in full. I would suggest asking Mr "
     "Fanshawe whether the safety session can be repeated, since it is a "
     "requirement.\n"
     "[Ghi chú ăn điểm: tách rõ 'miss' và 'still attend' đúng như câu hỏi. Dùng "
     "điều kiện loại 1. Kết bằng một đề xuất — thể hiện hiểu bối cảnh.]"),

    (11, "Express an opinion.\n\n"
         "Some companies allow employees to choose their own working hours, while "
         "others require everyone to be present at the same times. Which policy "
         "do you think works better, and why?",
     45, 60, ["sp_content", "sp_grammar", "sp_vocabulary", "sp_fluency"], 0.55,
     "I think flexible hours work better for most companies, for two reasons. "
     "First, people concentrate at different times of day. A colleague of mine "
     "does her best analytical work before eight in the morning, and forcing her "
     "to start at nine simply wastes her most productive hours. Second, "
     "flexibility reduces the friction of ordinary life — school runs, medical "
     "appointments — which otherwise turns into absence or resentment. The "
     "obvious objection is coordination: if nobody overlaps, meetings become "
     "impossible. But that is solved by setting core hours, say between eleven "
     "and three, rather than by fixing the whole day. In my experience, teams "
     "that trust people to manage their own schedules lose very little and gain a "
     "great deal in retention.\n"
     "[Ghi chú ăn điểm: nêu quan điểm ở câu đầu, hai lý do có đánh số, một ví dụ "
     "cụ thể, phản biện lại ý ngược, rồi kết. Dùng hết 60 giây.]"),
]

# ---------------------------------------------------------------------------
# WRITING — 8 task
# ---------------------------------------------------------------------------
# (task_type, prompt, min_words, max_words, concepts, difficulty, sample, vocab)
WRITING = [
    ("picture_description",
     "Write ONE sentence based on the picture, using the two words given.\n\n"
     "Use the words: bicycle / lock",
     None, None, ["wr_grammar", "wr_vocabulary"], 0.25,
     "The man is locking his bicycle to the rack outside the office building.",
     []),

    ("picture_description",
     "Write ONE sentence based on the picture, using the two words given.\n\n"
     "Use the words: colleague / explain",
     None, None, ["wr_grammar", "wr_vocabulary"], 0.25,
     "One colleague is explaining the settings on the screen while the other listens.",
     []),

    ("picture_description",
     "Write ONE sentence based on the picture, using the two words given.\n\n"
     "Use the words: receptionist / hand",
     None, None, ["wr_grammar", "wr_vocabulary"], 0.25,
     "The receptionist is handing an envelope to the visitor across the counter.",
     []),

    ("picture_description",
     "Write ONE sentence based on the picture, using the two words given.\n\n"
     "Use the words: umbrella / because",
     None, None, ["wr_grammar", "wr_vocabulary"], 0.30,
     "The pedestrians are carrying umbrellas because it has started to rain heavily.",
     ["umbrella"]),

    ("picture_description",
     "Write ONE sentence based on the picture, using the two words given.\n\n"
     "Use the words: notice / board",
     None, None, ["wr_grammar", "wr_vocabulary"], 0.30,
     "She is pinning a notice to the board in the corridor so that everyone can read it.",
     []),

    ("email",
     "Read the email and respond.\n\n"
     "From: Priya Halvorsen, Office Manager\n"
     "To: All staff\n"
     "Subject: Desk booking system\n\n"
     "From next month we will introduce a desk booking system. Staff will need to "
     "reserve a desk online before coming into the office. Please reply with ONE "
     "question about how the system will work and ONE concern you have about it.",
     50, None, ["wr_task_response", "wr_mechanics", "wr_vocabulary"], 0.40,
     "Dear Ms Halvorsen,\n\n"
     "Thank you for letting us know about the new desk booking system.\n\n"
     "I have one question: how far in advance will we be able to reserve a desk? "
     "If bookings open only a day ahead, it may be difficult to plan team days.\n\n"
     "My main concern is what happens when someone arrives without a reservation "
     "and no desks are left. It would help to keep a small number of unbookable "
     "desks for those situations.\n\n"
     "Thank you for considering this.\n\n"
     "Best regards,\n"
     "Tomas Everleigh\n"
     "[Ghi chú ăn điểm: trả lời đủ CẢ HAI phần đề yêu cầu, mỗi phần một đoạn "
     "riêng. Có lời chào, lời cảm ơn và ký tên — chấm ở chiều mechanics.]",
     []),

    ("email",
     "Read the email and respond.\n\n"
     "From: Customer Services, Ashcombe Rail\n"
     "To: Passenger\n"
     "Subject: Your refund request\n\n"
     "We have received your refund request for the delayed service on 3 June. "
     "Before we can process it, please confirm your ticket number, tell us how "
     "long you were delayed, and let us know whether you would prefer a refund or "
     "travel credit.",
     50, None, ["wr_task_response", "wr_coherence", "wr_mechanics"], 0.45,
     "Dear Customer Services,\n\n"
     "Thank you for your message regarding my refund request.\n\n"
     "My ticket number is AR-4471902, booked for the 07:15 service from Ashcombe "
     "to Harringate on 3 June.\n\n"
     "The train was delayed by one hour and fifty minutes in total, so I arrived "
     "at my destination shortly before nine o'clock rather than at ten past eight.\n\n"
     "I would prefer a refund to my original payment method rather than travel "
     "credit, as I do not expect to use this route again this year.\n\n"
     "Please let me know if you need anything further.\n\n"
     "Yours faithfully,\n"
     "Ingrid Mbeki\n"
     "[Ghi chú ăn điểm: ba yêu cầu của đề được trả lời theo đúng thứ tự, mỗi ý "
     "một đoạn. Số liệu cụ thể thay vì nói chung chung.]",
     []),

    ("opinion_essay",
     "Write an essay of at least 300 words.\n\n"
     "Some people believe that companies should pay for their employees to learn "
     "new skills, even when those skills are not needed for the employee's "
     "current job. Others think training budgets should be spent only on skills "
     "the company needs right now. Which view do you agree with? Give reasons and "
     "examples to support your opinion.",
     300, None,
     ["wr_task_response", "wr_organization", "wr_coherence", "wr_grammar",
      "wr_vocabulary"], 0.60,
     "I agree that companies should fund learning that goes beyond an employee's "
     "immediate role, although this position needs one important qualification, "
     "which I will come to at the end.\n\n"
     "The first reason is that the skills a company needs right now are rarely "
     "the skills it will need in three years. When a firm trains only for present "
     "requirements, it is effectively betting that nothing will change. That bet "
     "has repeatedly failed. Consider the retailers who trained their staff "
     "thoroughly in in-store merchandising during the years when online ordering "
     "was quietly becoming the dominant channel. Their training budgets were spent "
     "efficiently against yesterday's needs, and the capability they actually "
     "required had to be bought in at far greater expense.\n\n"
     "The second reason concerns retention rather than capability. Employees who "
     "are permitted to learn something they are genuinely curious about tend to "
     "stay longer, and the cost of replacing an experienced colleague is "
     "considerably higher than the cost of a course. A developer at a former "
     "employer of mine was funded to study data visualisation, which had nothing "
     "to do with her role at the time. She remained with the company for another "
     "four years, and eventually rebuilt the reporting system that her manager "
     "had been planning to outsource.\n\n"
     "The obvious objection is that budgets are finite and that unfocused "
     "spending produces a workforce full of half-learned hobbies. This is a fair "
     "point, and it is where my qualification applies. Funding should be granted "
     "on the condition that the employee explains, in writing, how the skill "
     "might serve the organisation, and shares what they have learned afterwards. "
     "That requirement costs nothing and filters out purely personal projects "
     "without imposing a narrow definition of relevance.\n\n"
     "On balance, then, I believe training should look beyond the current job "
     "description. A company that invests only in what it needs today will keep "
     "finding itself short of what it needs tomorrow.\n"
     "[Ghi chú ăn điểm: mở bài nêu quan điểm và báo trước sẽ có điều kiện kèm "
     "theo. Hai lý do, mỗi lý do một đoạn kèm ví dụ cụ thể. Một đoạn phản biện "
     "rồi giải quyết. Kết bài nhắc lại quan điểm bằng cách diễn đạt khác.]",
     ["retention", "capability", "budget"]),
]


def main() -> int:
    speaking_images = {
        3: f"{MEDIA_BASE}/speaking/meeting_presentation.jpg",
        4: f"{MEDIA_BASE}/speaking/train_platform.jpg",
    }
    speaking_audio = {
        8: ("I heard the training is all in one room. Is that right?", Accent.US),
        9: ("Who is running the afternoon session, and what is it about?", Accent.UK),
        10: ("I can only arrive at eleven. What will I miss, and what can I still attend?", Accent.AU),
    }
    sp_tasks = [SpeakingTask(
        part_number=part, prompt=prompt, prep_time_sec=prep,
        response_time_sec=resp, sample_answer_c1=sample,
        rubric_ref=SP_RUBRIC.rubric_id, concept_ids=concepts,
        difficulty_prior=diff,
        image_url=speaking_images.get(part),
        stimulus_text=TRAINING_SCHEDULE if part in (8, 9, 10) else None,
        audio=(AudioAsset(script=speaking_audio[part][0], accent=speaking_audio[part][1])
               if part in speaking_audio else None))
        for part, prompt, prep, resp, concepts, diff, sample in SPEAKING]

    writing_images = [
        f"{MEDIA_BASE}/writing/bicycle_lock.jpg",
        f"{MEDIA_BASE}/writing/photocopier_help.jpg",
        f"{MEDIA_BASE}/writing/reception_envelope.jpg",
        f"{MEDIA_BASE}/writing/rainy_station.jpg",
        f"{MEDIA_BASE}/writing/notice_board.jpg",
    ]
    wr_tasks = [WritingTask(
        task_type=ttype, prompt=prompt, min_words=mn, max_words=mx,
        sample_answer_c1=sample, high_scoring_vocab=vocab,
        rubric_ref=WR_RUBRIC.rubric_id, concept_ids=concepts,
        difficulty_prior=diff,
        image_url=(writing_images[index] if index < len(writing_images) else None))
        for index, (ttype, prompt, mn, mx, concepts, diff, sample, vocab)
        in enumerate(WRITING)]

    def meta(bid, mt, n):
        return BatchMetadata(batch_id=bid, module_type=mt, generated_by=GENERATED_BY,
                             generated_at=datetime.now(UTC), total_records=n)

    print(f"Speaking: {len(sp_tasks)} task   Writing: {len(wr_tasks)} task")
    essay = next(t for t in wr_tasks if t.task_type == "opinion_essay")
    print(f"  bài luận mẫu: {len(essay.sample_answer_c1.split())} từ "
          f"(yêu cầu ≥{essay.min_words})")
    covered = {c for t in sp_tasks for c in t.concept_ids} | \
              {c for t in wr_tasks for c in t.concept_ids}
    print(f"  concept phủ: {len(covered)}/{len(ALL_SP) + len(ALL_WR)}  "
          f"thiếu: {sorted(set(ALL_SP + ALL_WR) - covered) or 'không'}")
    print(f"  rubric: {len(SP_RUBRIC.dimensions)} + {len(WR_RUBRIC.dimensions)} chiều, "
          f"mỗi chiều 6 band\n")

    guarded_write_batch(
        SpeakingBatch(batch_metadata=meta("speaking_batch_001", ModuleType.SPEAKING,
                                          len(sp_tasks)),
                      tasks=sp_tasks, rubrics=[SP_RUBRIC]), OUT_SP)
    guarded_write_batch(
        WritingBatch(batch_metadata=meta("writing_batch_001", ModuleType.WRITING,
                                         len(wr_tasks)),
                     tasks=wr_tasks, rubrics=[WR_RUBRIC]), OUT_WR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
