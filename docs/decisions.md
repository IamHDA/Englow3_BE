# Quyết định đã chốt

Các blocker đang treo được chốt ngày **2026-08-06**. Mỗi mục ghi lựa chọn, lý do,
và cái gì phải làm lại nếu đổi ý.

---

## D1 — Dữ liệu học liệu chỉ lưu local, không đẩy git

**Chốt:** `output/**/*.json`, `output/media/`, `output/exams/individual_sets/`,
`sources/`, `seeds/vocab_seed.csv`, `seeds/by_level/`, `seeds/raw/`,
`rejects/*.json` đều nằm trong `.gitignore`.

**Vẫn nằm trong git:** script sinh dữ liệu, schema, taxonomy, validator, báo cáo QA
và file attribution/licence.
Nghĩa là repo giữ *cách tạo ra* dữ liệu, không giữ *dữ liệu*.

**Lý do:**
- Đề thi, flashcard, quiz là sản phẩm, không phải mã nguồn. Diff của 3000 dòng CSV
  hay vài nghìn item JSON không ai đọc được, và làm repo phình theo mỗi lần regenerate.
- Gỡ luôn nghĩa vụ share-alike: wordlist nguồn là CC BY-SA, nhưng share-alike chỉ
  phát sinh khi **phân phối**. Không đẩy lên repo công khai = không phân phối.
  Xem [ATTRIBUTION.md](../data_pipeline/seeds/ATTRIBUTION.md).

**Dựng lại trên máy mới:**
```bash
cd data_pipeline/seeds/raw && ./fetch_wordlists.sh   # tải wordlist gốc
cd ../.. && make seed                                # dựng vocab_seed.csv
make sources                                         # tải corpus mở đã pin commit/hash
```

**Hệ quả phải nhớ:** dữ liệu không có bản sao trên remote. Cần cơ chế backup riêng
trước khi Phase 5–9 sinh ra khối lượng lớn nội dung tốn tiền LLM.

---

## D6 — Chưa dùng DB; lưu thẳng ra đĩa theo bố cục map 1-1 với bảng

**Chốt:** hai tầng — `output/<module>/` là batch do generator sinh, `output/_db/`
là tầng staging phẳng, mỗi file JSONL ứng với một bảng. Chi tiết 21 bảng và thứ tự
nạp: [storage-layout.md](storage-layout.md).

**Lý do:** Phase 2–10 không cần DB, chỉ Phase 11 mới cần. Tách sẵn tầng staging thì
lúc nạp vào Postgres chỉ là đọc file và `INSERT`, không phải viết lại logic làm phẳng.
Ba việc khiến bước đó dễ: `concept_ids` tách thành bảng nối thay vì mảng khoá ngoại;
khoá chính dùng `stable_id()` có sẵn nên `ON CONFLICT DO UPDATE` idempotent; cột
`embedding` để `NULL` lúc nạp, sinh vector là bước riêng.

**Hệ quả:** blocker **B1** (không có Postgres) không chặn gì cho tới Phase 11.

---

## D2 — Embedding: 1024 chiều, `bge-m3`

**Chốt:** [schemas/embedding_config.yaml](../data_pipeline/schemas/embedding_config.yaml) — `dimension: 1024`, `BAAI/bge-m3`, chạy local, normalize L2, index HNSW `vector_cosine_ops`.

**Lý do:** quyết định thật sự không phải chọn model mà là chốt **số chiều**, vì cột
`vector(N)` đổi N là phải drop cột, dựng lại index, sinh lại toàn bộ embedding.
1024 là số chiều chung của cả ba lựa chọn thực tế — `bge-m3`, `voyage-3.5`,
`multilingual-e5-large` — nên đổi model về sau chỉ tốn thời gian re-embed.

Chọn local làm mặc định vì `embedding_text` (§2.7) chỉ có tiếng Anh, khối lượng lớn
(3000 flashcard + ~1200 exam item), và sẽ sinh lại nhiều lần trong lúc phát triển.
Anthropic không có embeddings API; bên được khuyến nghị là Voyage AI — `voyage-3.5`
để sẵn làm phương án trả phí, cùng 1024 chiều nên thay được không cần migration.

**Gỡ blocker B3** → DDL Phase 2 viết được `vector(1024)`.

---

## D3 — Thêm `octanove` vào enum `cefr_source`

**Chốt:** enum thành `evp | cefrj | octanove | ngsl_band | llm_estimate | human_verified`.

**Lý do:** 600/3000 từ band C1 đến từ Octanove Vocabulary Profile — dataset riêng của
bên thứ ba, không phải CEFR-J. Gán bừa thành `cefrj` là khai sai nguồn, trái §0.4
("CEFR level phải có `cefr_source` truy vết được"). Thêm một giá trị enum rẻ hơn
nhiều so với mất truy vết trên 20% dữ liệu.

Đây là **thêm field ngoài schema đã duyệt** theo §3.4 — ghi rõ ở đây thay vì làm lặng lẽ.

---

## D4 — Ngân hàng câu hỏi tách khỏi bộ đề (`bank/` + `sets/`)

**Chốt:** áp dụng cấu trúc trong [exam-set-structure.md](exam-set-structure.md).
Lệch với §3.3 của work order (quy ước phẳng), nhưng quy ước đặt tên file **bên trong**
`bank/` vẫn giữ đúng §3.3.

**Lý do:** §2.4 và lỗi P1-12 nói `item_id` ổn định và tái sử dụng qua nhiều đề,
`position` mới thuộc về một đề cụ thể. Lưu mỗi bộ đề thành một bản sao câu hỏi thì
sửa một chỗ lệch các chỗ còn lại, và `stable_id` mất ý nghĩa idempotent.

---

## D5 — 3 bộ đề + `quick_exercises` 12 câu/point + syllabus mở ra A1–C1

**Chốt:** phương án (a) cho blocker B7.

**Lý do:** tính bằng code — 90 grammar concept cần ≥900 item để BKT hội tụ ở ngưỡng 10.
Part 5+6 chỉ cho 46 item/bộ đề, nên giải bằng bộ đề sẽ cần 20 bộ, không khả thi.

| Nguồn | Công thức | Item |
|---|---|---:|
| Phase 6 `quick_exercises` | 90 concept × 12 câu | 1080 |
| 3 bộ đề Part 5+6 | 3 × 46 | 138 |
| | **Tổng** | **1218** → **13.5/concept** ✅ |

Work order Phase 4 hiện chỉ định syllabus **B1–C1**, để hở 41 concept A1–A2 không có
item nào — nên mở ra **A1–C1**.

**Sửa so với work order:** `quick_exercises` 5 → 12 câu/point (Phase 6);
`grammar_syllabus.yaml` phủ A1–C1 thay vì B1–C1 (Phase 4).

---

## D7 — Owner từ chối 3 ràng buộc tôi thêm ngoài spec

**Chốt:** gỡ cả ba khỏi schema và DDL.

| # | Ràng buộc đã gỡ | Hệ quả để lại |
|---|---|---|
| R1 | `irt_params` không được khai `calibrated` khi `n_responses` chưa đủ | `calibration_status` **không được cưỡng chế**. Phase 12 phải tính lại độ tin cậy từ `n_responses` thật, đừng đọc cột này |
| R2 | `ExamSet.title` phải kèm "format"/"định dạng" khi có chữ TOEIC | §0.7 vẫn áp dụng nhưng do con người giữ. Generator Phase 7 phải đặt title đúng ngay từ đầu vì không có lưới chắn |
| R3 | Nhãn option phải liên tục A,B,C[,D] | Nhãn trùng hoặc nhảy cóc lọt qua schema. Nếu gặp ở Phase 7 thì phải bắt bằng QA thủ công |

Ghi lại để sau này gặp lỗi tương ứng thì biết ngay là do đã cố ý bỏ lưới chắn,
chứ không phải sót.

---

## D8 — DDL đặt tại `src/main/resources/db/migration/V1__content_tables.sql`

**Chốt:** `make schema` ghi thẳng vào thư mục Flyway của Spring Boot. Gỡ blocker B4.

**Lý do:** Flyway phải là chủ sở hữu schema duy nhất (`ddl-auto: validate`).
Để DDL ở chỗ khác nghĩa là có hai nguồn schema, sớm muộn cũng lệch.

⚠️ **Flyway không bao giờ được sửa migration đã chạy.** Một khi V1 đã áp lên một
DB thật, mọi thay đổi schema phải là V2, V3... `make schema` sinh đè lên V1 chỉ
an toàn **trước** lần chạy đầu tiên.

---

## Đề IELTS

Chốt ngày **2026-11-05**. Danh sách đầy đủ, kể cả các quyết định còn đang bàn
(D-IELTS-3, 5, 6, 8, 9), nằm ở [ielts-readiness-checklist.md](ielts-readiness-checklist.md).

### D-IELTS-1 — Tự viết đề, không cào

**Chốt:** mọi đề IELTS là nội dung gốc, do người hoặc agent viết qua `data_pipeline`.
Đề mẫu của British Council, IDP, ielts.org và Cambridge chỉ dùng để tham khảo format
và độ khó. Không chép, không cào, kể cả từ các site đề bên thứ ba.

**Lý do:** đề mẫu chính thức chỉ cho dùng cá nhân, phi thương mại. Muốn đăng lại hay
sửa thì phải có giấy phép bằng văn bản. Các site bên thứ ba thường đăng lại đề trong
sách Cambridge, nên không có quyền cấp phép cho ai. Đây cũng là kết luận đã có với
TOEIC trong [source_data_status.md](../data_pipeline/reports/source_data_status.md).

**Đổi ý thì:** muốn dùng đề có bản quyền thì phải có hợp đồng với Cambridge trước khi
nạp. Các đề tự viết vẫn giữ được.

### D-IELTS-2 — Làm Academic trước

**Chốt:** đợt đầu gồm Listening, Reading Academic, Writing Task 1 Academic (tả biểu đồ),
Writing Task 2 và Speaking. Làm sau: Reading General Training và Writing Task 1 GT
(viết thư).

**Lý do:** Listening, Writing Task 2 và Speaking giống nhau ở cả hai loại; chỉ Reading và
Writing Task 1 khác. Làm Academic trước thì GT về sau chỉ phải thêm hai phần khác biệt.

**Hệ quả:** đề mock đợt đầu có `certificate_variant = ACADEMIC`. `score_conversions`
đợt đầu chỉ cần bảng Listening và bảng Reading `ACADEMIC`.

**Đổi ý thì:** không mất gì; GT chỉ là phần thêm vào.

### D-IELTS-3 — "Choose TWO/THREE letters" là một câu nhiều điểm

**Chốt:** một câu `MULTIPLE_CHOICE` có N đáp án đúng thì được N điểm. Mỗi lựa chọn đúng
được 1 điểm, không tính thứ tự. FE cho chọn tối đa N; BE từ chối bài nộp chọn nhiều hơn N.

**Lý do:** nếu tách thành N câu thì phải chấm theo cặp, vì "câu 21 = B, câu 22 = D" và
ngược lại đều phải tính là đúng. Gộp thành một câu thì quy tắc nằm gọn trong một entity.

**Hệ quả:**
- Khi hiển thị, câu này chiếm N số ("21–22"), nên cách đánh số câu (D-IELTS-5) phải tính
  được điều này.
- Số câu của lượt thi (`question_count`) sẽ ít hơn 40, dù tổng điểm vẫn là 40.

**Đã kiểm tra (2026-11-05):** DB thật chưa có câu `MULTIPLE_CHOICE` nào. Query dùng để
kiểm tra (các bảng nằm trong schema `englow3`, không phải `public`):
`select question_type, count(*) from englow3.questions group by question_type`.

Vì vậy đổi thẳng cách chấm của `MULTIPLE_CHOICE` sang điểm từng phần, không cần loại câu
mới. Chạy lại query này ngay trước khi triển khai thay đổi cách chấm: nếu lúc đó đã có
câu `MULTIPLE_CHOICE`, điểm của các câu đó sẽ đổi theo.

**Đổi ý thì:** muốn tách câu về sau thì phải viết lại dữ liệu của các câu đã có.

### D-IELTS-4 — Chấm câu điền từ: code chỉ chuẩn hoá tối thiểu, biến thể nằm ở dữ liệu

**Chốt:** code chỉ chuẩn hoá hai thứ:
- không phân biệt hoa thường;
- bỏ khoảng trắng thừa ở đầu, ở cuối và giữa các từ.

Mọi biến thể khác do người viết đề liệt kê, mỗi biến thể một dòng trong
`question_accepted_answers`:
- cách viết Anh-Anh và Anh-Mỹ: `colour`, `color`;
- số viết bằng chữ và bằng số: `3`, `three`;
- có và không có từ tuỳ chọn: `museum`, `the museum`.

Sai chính tả thì tính là sai. Không đáp án nào được vượt giới hạn số từ của câu.

**Lý do:** kết quả chấm đoán trước được. Khi chấm sai một đáp án, sửa dữ liệu của câu
đó chứ không phải sửa code rồi chấm lại mọi đề.

**Hệ quả:** validator của pipeline phải kiểm tra mọi đáp án được chấp nhận đều nằm
trong giới hạn số từ của câu.

**Đổi ý thì:** nếu sau này code tự hiểu được số hay biến thể chính tả, các dòng đã liệt
kê vẫn đúng, chỉ thành thừa.

### D-IELTS-5 — Ô trống đánh dấu theo thứ tự trong nhóm; số câu do BE tính

**Chốt:**
- Ô trống trong `question_sets.content` được đánh dấu bằng `{{n}}`, với n là
  `questions.order_no` của câu trong nhóm đó (`{{1}}`, `{{2}}`…). FE thay mỗi dấu bằng ô
  nhập của câu tương ứng.
- Số câu hiển thị không lưu vào DB. BE tính ở một chỗ rồi trả qua API (`numberFrom`,
  `numberTo`); FE thôi tự đếm.
- Quy tắc tính:
  - IELTS đánh lại từ 1 ở mỗi section; TOEIC đánh liên tục cả đề.
  - Câu N điểm (D-IELTS-3) chiếm N số.
  - Số được tính trên cả đề, nên một lượt PRACTICE vẫn giữ số câu của đề đầy đủ.

**Lý do:**
- Chèn thêm câu ở đầu đề thì không phải sửa dấu ở các nhóm phía sau.
- Mỗi lần lưu, nội dung đề bị xoá rồi ghi lại (`ExamContentServiceImpl.deleteContent`),
  nên số câu lưu sẵn cũng sẽ bị tính lại mỗi lần. Lưu vào DB chỉ thêm nguy cơ lệch, thêm
  một migration, và phải điền số cho mọi đề TOEIC đang có.
- BE dùng lại được cùng con số cho thông báo lỗi và cho tutor ("câu 14").

**Hệ quả:**
- FE hiện đánh số liên tục qua cả đề (`ExamSittingView`, `flatMap`), nên Reading IELTS
  đang hiện câu 41–80. Phải sửa cùng lúc.
- Lỗi `EXAM_HAS_INCOMPLETE_QUESTION` nên báo theo số câu hiển thị, thay vì `order_no`
  trong nhóm.
- Validator của pipeline phải kiểm tra: mỗi dấu `{{n}}` ứng với đúng một câu điền từ
  trong nhóm, và không câu nào có hai dấu.

**Đổi ý thì:** nếu sau này cần đặt số câu tuỳ ý thì mới thêm cột; các dấu `{{n}}` không
phải đổi.

### D-IELTS-6 — Band tổng chỉ có từ full test 4 kỹ năng

**Chốt:**
- Band tổng IELTS chỉ có từ một lượt full test (D-IELTS-10). Cách tính theo IELTS:
  trung bình 4 kỹ năng; phần lẻ .25 làm tròn lên .5, phần lẻ .75 làm tròn lên số nguyên
  kế tiếp.
- **Band tạm:** khi đã có band Listening và Reading, band tạm là band thấp hơn trong
  hai band đó. Ghi rõ "chỉ dựa trên Listening và Reading". Khi đủ 4 kỹ năng thì band
  chính thức thay vào.
- Đề L+R làm riêng, ngoài full test, chỉ để luyện tập: hiện band từng kỹ năng, không
  ra band tổng.
- Mốc thi thử trên roadmap chỉ tính band chính thức của full test.
  - Đã làm (V083): với mục tiêu IELTS, roadmap chỉ đưa full test vào danh sách mốc thi
    thử. Hệ quả: **chưa có full test nào được xuất bản thì không tạo được roadmap IELTS**
    (báo "There is not enough published content to plan this goal yet"). Mốc TOEIC vẫn
    dùng đề như cũ.

**Lý do:**
- Đề L+R thiếu hai kỹ năng thường kéo band tổng xuống, nên trung bình L+R dễ làm học
  viên tưởng mình đã đạt.
- `NEEDS_REVIEW` nghĩa là AI chưa chấm, nên chưa có điểm nào (`AssessmentAttempt.submit`,
  `requestReview`). Vì vậy band tạm chỉ có thể lấy từ L và R, hai phần chấm ngay khi
  nộp.
- Lấy band thấp hơn, vì thà đánh giá thấp còn hơn để học viên tưởng mình đã đạt.

**Band từng kỹ năng**, tính theo cách IELTS:
- Writing: Task 2 tính gấp đôi Task 1, tức (Task 1 + 2 × Task 2) / 3.
- Speaking: trung bình 3 part (D-IELTS-8). Thi thật thì giám khảo cho một band cho cả
  bài; khi mỗi part được chấm riêng, trung bình 3 part là cách gần nhất với điều đó.
- Mọi band đều làm tròn theo cùng quy tắc với band tổng: về nửa band gần nhất, phần lẻ
  .25 và .75 làm tròn lên.

**Đổi ý thì:** chỉ đổi phép tính, không đổi dữ liệu. Các lượt đã có điểm nên giữ band
đã tính lúc đó.

### D-IELTS-7 — Listening: FULL phát một lần, PRACTICE tua thoải mái

**Chốt:**
- Chế độ FULL: audio phát một lần, không tua, không dừng. Cuối bài có 2 phút kiểm tra
  đáp án, như bản thi trên máy tính.
- Chế độ PRACTICE: tua và dừng thoải mái.

**Hệ quả:**
- Thời gian của section Listening ở chế độ FULL bằng tổng thời lượng audio cộng 120
  giây.
- Chỉ FE chặn được việc phát lại; BE không biết audio đã phát bao nhiêu lần. Tải lại
  trang không được phát lại từ đầu, mà phải chạy tiếp từ mốc ứng với thời gian đã trôi.

**Đổi ý thì:** chỉ đổi phía FE, dữ liệu không ảnh hưởng.

### D-IELTS-8 — Speaking: mỗi part một lần ghi

**Chốt:**
- Part 1, 2 và 3 là ba task riêng (`PART_1`, `PART_2`, `PART_3`). Mỗi part có một bản ghi
  và một kết quả chấm.
- Part 1 và Part 3: bấm ghi một lần, các câu hỏi hiện lần lượt trong lúc ghi. FE dừng ghi
  khi đủ 5 phút.
- Part 2: FE đếm 60 giây chuẩn bị (không ghi âm), sau đó ghi tối đa 2 phút.

**Lý do:**
- Trong từng part, cách làm này gần với thi thật, mà vẫn nằm trong giới hạn hiện có:
  file WAV tối đa 10 MB (`AssessmentServiceImpl.java:100`) và tối đa 5 phút
  (`ai_service/app/providers/speech.py`).
- Tiêu chí Speaking của IELTS không chấm theo từng câu hỏi, nên một bản ghi cho cả part
  vẫn đủ để AI chấm.
- Lỗi mạng chỉ làm mất một part, không mất cả bài.
- Không phải đổi DB.

**Hệ quả:**
- Câu hỏi của Part 1 và Part 3 nằm trong `instructions` của task. Cần quy ước cách ghi
  danh sách câu hỏi trong đó, để FE tách ra và hiện lần lượt.
- Band Speaking của một bài full test được gộp từ 3 part; cách gộp chốt cùng D-IELTS-6.

**Đổi ý thì:** muốn ghi cả bài trong một lần thì phải nâng giới hạn audio và thêm một
loại task. Các task đã viết vẫn dùng được làm từng part.

### D-IELTS-9 — AI chấm Writing Task 1 bằng số liệu dạng text

**Chốt:**
- Số liệu của biểu đồ ghi dạng text trong `assessment_tasks.rubric_notes`. AI chấm nhận
  trường này qua bản snapshot của task (`AssessmentServiceImpl.java:108`).
- Ảnh biểu đồ được vẽ bằng code từ chính bộ số liệu đó (ví dụ matplotlib trong
  pipeline), để ảnh học viên thấy và số liệu AI đọc luôn khớp nhau.
- Bản đồ và sơ đồ quy trình không có số liệu thì mô tả bằng lời trong `rubric_notes`.

**Lý do:** `ai_service/app/providers/llm.py` chỉ gửi text cho Groq. Muốn gửi ảnh thì
phải sửa ai_service và đổi sang một model đọc được ảnh. Chấm theo text cũng cho kết quả
ổn định hơn.

**Hệ quả:**
- Vẫn cần thêm cột ảnh vào `assessment_tasks` để học viên xem được biểu đồ.
- Học viên đọc được `rubric_notes` sau khi bài đã chấm xong (`hideSolution` chỉ áp dụng
  khi bài chưa `COMPLETED`). Số liệu thì không sao, vì nó vốn hiện trên biểu đồ. Nhưng
  không ghi vào đây điều gì không muốn học viên thấy.

**Đổi ý thì:** chuyển sang gửi ảnh thì số liệu đã ghi vẫn dùng được làm thông tin bổ sung
cho AI.

### D-IELTS-10 — Full test là test đầu vào của người học IELTS, đặt ở module `exam`

**Chốt:**
- Một full test gồm một đề L+R, Writing Task 1, Writing Task 2 và Speaking Part 1, 2,
  3. Mỗi phần vẫn do module của nó chấm. Lượt full test chỉ nối các lượt làm bài lại
  và tính band (D-IELTS-6).
- Full test là **test đầu vào** của người học IELTS, thay cho đề `PLACEMENT` dùng chung
  hiện nay. Học viên đi tiếp onboarding với band tạm. Khi có band chính thức, trình độ
  được cập nhật mà không phải làm lại onboarding.
- Học viên không có roadmap cũng tự chọn làm được trong thư viện đề.
- **Chỉ người học có mục tiêu IELTS** được làm full test.
  - Người học TOEIC không làm full test IELTS; test đầu vào của họ vẫn là đề
    `PLACEMENT`.
  - Người không học chứng chỉ vẫn dùng level check của roadmap.
  - Vì vậy full test luôn giữ đúng format và độ dài của bài thi IELTS.
- Full test được tạo và duyệt theo **cùng quy trình duyệt như đề**: nháp, gửi duyệt,
  duyệt hoặc từ chối, xuất bản. Full test chỉ xuất bản được khi đề L+R và cả 5 task đều
  đã xuất bản và đúng loại IELTS. Đây là luật nằm trong entity.
- Phần gom thuộc module `exam`; xem mục `exam` trong [module-map.md](module-map.md).

**Lý do:**
- Full test phải tồn tại cả khi không có roadmap, nên không đặt ở `roadmap`.
- `exam` đã sở hữu đề mock và lượt thi.
- Phần gom không có luật nào ngoài việc cộng band, nên tạo module riêng là thừa.
- Level check của roadmap đã gom `exam` với `assessment` theo đúng kiểu này.

**Hệ quả:**
- DB: thêm 2 bảng trong `exam` (định nghĩa full test và lượt full test).
  `roadmap_milestones` phải trỏ được tới full test.
- `user.api.PlacementRecorder` cần thêm một hàm ghi band IELTS. DB không phải đổi:
  `learner_profiles.current_score` đã có, và CEFR lấy từ `score_conversions.cefr_level`.
- `assessment.api` cần thêm một reader cho band Writing. `SpeakingLevelReader` hiện chỉ
  có cho Speaking.
- Chấm tự động Writing/Speaking **mặc định tắt** (`ASSESSMENT_AUTOMATIC_WRITING=false`,
  `ASSESSMENT_AUTOMATIC_SPEAKING=false`). Nếu môi trường thật cũng tắt, mọi band chính
  thức đều phải chờ staff chấm.

**Thời hạn làm bài:**
- Học viên được nghỉ giữa các phần, miễn là nộp đủ các phần trong **7 ngày** tính từ lúc
  bắt đầu lượt full test. Mỗi phần vẫn có giờ làm riêng.
- Hạn 7 ngày chỉ tính cho việc **nộp bài**. Bài nộp trước hạn vẫn được tính khi chấm xong
  sau hạn, kể cả khi phải chờ staff chấm.
- Quá hạn mà chưa nộp đủ thì lượt full test đóng lại. Band tạm đã ghi vào hồ sơ vẫn giữ
  nguyên; muốn có band tổng thì làm một lượt full test mới.
- Chọn 7 ngày vì IELTS thật cũng cho thi Speaking trong khoảng 7 ngày trước hoặc sau ba
  kỹ năng kia.
- Chấm cũng có hạn: **7 ngày sau hạn nộp** (`FullTestSitting.GRADING_WINDOW`). Hết hạn mà
  còn phần chưa có band thì lượt đóng (`EXPIRED`), giữ band tạm. Nếu không có hạn này,
  một bài AI bỏ cuộc (`FAILED`) mà học viên không gửi lại, hoặc một bài chờ staff mà
  không ai chấm, sẽ giữ lượt ở `AWAITING_GRADES` mãi.
- Màn hình lượt thi hiện trạng thái chấm của từng phần (`FullTestPartGrading`: đang chấm,
  chờ người chấm, lỗi, đã chấm), để học viên biết phần nào cần "Chấm lại" hoặc "Gửi
  người chấm" trước hạn chấm. Trạng thái đọc từ `assessment` mỗi lần xem, không lưu.

**Đổi ý thì:** chuyển phần gom sang module khác thì phải chuyển 2 bảng và các listener.
Các lượt làm bài trong `exam` và `assessment` không ảnh hưởng.

---

## Còn treo — chưa quyết được vì phụ thuộc môi trường

| # | Việc | Chặn |
|---|---|---|
| B1 | Không dựng Postgres — Owner chốt **để local trên máy thôi**. DDL đã sinh nhưng **chưa Postgres nào xác nhận cú pháp** | Phase 11 |
| B2 | Java 21 + Maven chưa cài (quyền Homebrew) | Phase 11 |
| B5 | TTS engine (chi phí + license khác nhau nhiều) | Phase 8 |
| B6 | Nguồn ảnh Part 1 | Phase 8 |

B1 và B2 là vấn đề môi trường máy, không phải quyết định thiết kế — cần thao tác của
Owner (khởi động Docker dưới account của bạn, hoặc cho phép `sudo chown` Homebrew,
hoặc cấp connection string remote).
