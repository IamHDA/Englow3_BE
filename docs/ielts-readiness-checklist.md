# Việc cần làm trước khi tạo dữ liệu đề IELTS

**Cập nhật:** 2026-11-05 · **Đọc từ nhánh:** `testing` (`58308b3`)

Dữ liệu IELTS nhập vào trước khi xong các mục dưới đây sẽ không lưu được, không chấm
được, hoặc phải sinh lại. Thứ tự làm:

1. Chốt quyết định (mục 0). Các quyết định này định hình từng câu hỏi sẽ viết.
2. Migration DB (mục 1).
3. Backend (mục 2) và BFF/FE (mục 3).
4. Pipeline (mục 4).
5. Chạy thử trọn vòng với **một** đề (mục 5), rồi mới sinh hàng loạt.

---

## 0. Quyết định phải chốt trước (Owner)

Ghi từng quyết định vào [decisions.md](decisions.md).

- [x] **D-IELTS-1. Nguồn nội dung.** Đã chốt: tự viết đề gốc, không cào. Xem
  [decisions.md](decisions.md#d-ielts-1--tự-viết-đề-không-cào).
- [x] **D-IELTS-2. Phạm vi.** Đã chốt: làm Academic trước. Xem
  [decisions.md](decisions.md#d-ielts-2--làm-academic-trước).
  - Đề mock chỉ gồm Listening và Reading. Writing và Speaking bị từ chối trong đề mock
    (`ExamContentServiceImpl.java:189`, `ExamReviewServiceImpl.java:76`), nên đi qua
    module `assessment`.
- [x] **D-IELTS-3. Câu "Choose TWO/THREE letters".** Đã chốt: một câu N điểm, mỗi lựa
  chọn đúng được 1 điểm. Hệ thống hiện chấm kiểu tất cả hoặc không
  (`ExamAttemptServiceImpl.java:321`). Còn phải đếm số câu `MULTIPLE_CHOICE` đang có
  trong DB thật. Xem
  [decisions.md](decisions.md#d-ielts-3--choose-twothree-letters-là-một-câu-nhiều-điểm).
- [x] **D-IELTS-4. Quy tắc chấm câu điền từ.** Đã chốt: code chỉ bỏ phân biệt hoa thường
  và khoảng trắng thừa; mọi biến thể khác người viết đề liệt kê thành nhiều đáp án. Xem
  [decisions.md](decisions.md#d-ielts-4--chấm-câu-điền-từ-code-chỉ-chuẩn-hoá-tối-thiểu-biến-thể-nằm-ở-dữ-liệu).
- [x] **D-IELTS-5. Ô trống và số câu.** Đã chốt: ô trống được đánh dấu `{{n}}` theo thứ
  tự câu trong nhóm. Số câu hiển thị không lưu DB, mà BE tính rồi trả qua API. FE hiện
  đánh số liên tục qua cả đề, nên Reading IELTS đang ra câu 41–80; phải sửa cùng lúc. Xem
  [decisions.md](decisions.md#d-ielts-5--ô-trống-đánh-dấu-theo-thứ-tự-trong-nhóm-số-câu-do-be-tính).
- [x] **D-IELTS-6. Band tổng.** Đã chốt: band tổng chỉ có từ full test 4 kỹ năng; band
  tạm là band thấp hơn của L và R. Còn chờ xác nhận cách tính band Writing và Speaking.
  Xem [decisions.md](decisions.md#d-ielts-6--band-tổng-chỉ-có-từ-full-test-4-kỹ-năng).
- [x] **D-IELTS-10. Full test.** Đã chốt: full test là test đầu vào của người học IELTS,
  đặt ở module `exam`, chỉ người học chứng chỉ được làm. Còn 4 điểm để ngỏ. Xem
  [decisions.md](decisions.md#d-ielts-10--full-test-là-test-đầu-vào-của-người-học-ielts-đặt-ở-module-exam).
- [x] **D-IELTS-7. Listening.** Đã chốt: chế độ FULL phát một lần, có 2 phút kiểm tra
  cuối bài; chế độ PRACTICE tua thoải mái. Xem
  [decisions.md](decisions.md#d-ielts-7--listening-full-phát-một-lần-practice-tua-thoải-mái).
- [x] **D-IELTS-8. Speaking.** Đã chốt: mỗi part một lần ghi; Part 1 và Part 3 không quá
  5 phút; FE đếm 60 giây chuẩn bị cho Part 2. Không phải đổi DB. Xem
  [decisions.md](decisions.md#d-ielts-8--speaking-mỗi-part-một-lần-ghi).
- [x] **D-IELTS-9. AI chấm Writing Task 1 Academic.** Đã chốt: số liệu biểu đồ ghi dạng
  text trong `rubric_notes`, ảnh vẽ bằng code từ chính số liệu đó. Xem
  [decisions.md](decisions.md#d-ielts-9--ai-chấm-writing-task-1-bằng-số-liệu-dạng-text).

---

## 1. Database

Các bảng cho Listening và Reading **đã có sẵn**: V011, V014, V015, V017, V019, V020,
V021. Theo [module-map.md](module-map.md), module `exam` sở hữu chúng. Chỉ có Java là
chưa map. Những việc còn phải làm ở DB:

- [ ] **Nạp dữ liệu `score_conversions`** (bảng có từ V017 nhưng đang rỗng). Cần 3
  bảng quy đổi:
  - Listening, với `certificate_variant` để NULL vì Academic và GT dùng chung;
  - Reading `ACADEMIC`;
  - Reading `GENERAL`.

  Đây là dữ liệu tham chiếu, cùng loại với `V079__level_check_weights.sql`, không phải
  seed demo. Ghi nguồn của bảng quy đổi và ghi rõ đây là band **ước tính**.
- [ ] **(Nên làm) Sửa ràng buộc unique của `score_conversions`.** Hai cột
  `certificate_variant` và `section_type` cho phép NULL, mà Postgres coi các NULL là
  khác nhau, nên dòng trùng không bị chặn. DB chạy pg17, nên đổi được sang
  `unique nulls not distinct (...)` ngay trong migration ở trên.
- [ ] **Writing Task 1 Academic:** `assessment_tasks` (V049, V075) chưa có cột ảnh.
  - Thêm `image_object_key varchar(500)`.
  - Thêm `certificate_variant` nếu cần tách Academic với GT. Task 1 bên GT là viết thư,
    không có ảnh.
- [ ] **Full test (D-IELTS-10):** thêm 2 bảng trong `exam`, gồm định nghĩa full test và
  lượt full test. Lượt full test cần cột `version`, và một ràng buộc để mỗi học viên chỉ
  có một lượt đang mở cho mỗi full test.
- [ ] **Roadmap:** `roadmap_milestones` phải trỏ được tới full test và lượt full test.
  Hiện bảng chỉ có `exam_id` và `result_exam_attempt_id`.

**Những thứ không cần đổi DB:**

- Loại câu hỏi mới: `questions.question_type varchar(20)` không có CHECK. Chỉ cần giữ
  tên ≤ 20 ký tự.
- Giới hạn số từ: để trong `questions.metadata jsonb`.
- Bản nháp bài làm: `exam_attempt_drafts.answers` là jsonb.
- Academic/GT của đề mock: `exams.certificate_variant` đã có, và
  `Exam.java:316` đã kiểm tra cặp giá trị.

**Đánh số migration:** số lớn nhất hiện tại là V080. Nhánh vừa phải đánh lại số
(`58308b3`), nên kiểm tra lại số lớn nhất ngay trước khi đặt tên file.

---

## 2. Backend

### 2.1 Loại câu hỏi và entity

- [ ] Thêm giá trị vào `exam/entity/QuestionType.java`, ví dụ `TEXT_ENTRY` và
  `MATCHING`. True/False/Not Given và Yes/No/Not Given dùng `SINGLE_CHOICE` với 3 lựa
  chọn cố định.
- [ ] Tạo entity và repository cho `question_accepted_answers`, `question_set_options`
  và `question_matching_answers`.
- [ ] `AttemptAnswer`: map cột `text_answer`.
- [ ] `AttemptAnswerOption`: map `question_set_option_id`. Hiện entity chỉ có
  `questionOptionId`, trong khi CHECK ở V021 yêu cầu đúng một trong hai cột có giá trị.
- [ ] Map `questions.metadata` nếu giới hạn số từ để ở đó.

### 2.2 Chấm điểm

- [ ] **Chuyển quy tắc chấm vào entity.** Hiện nó nằm trong service
  (`ExamAttemptServiceImpl.java:316–331`) và chỉ so sánh tập option id.
  - Quy tắc điền từ theo D-IELTS-4, quy tắc matching, và chấm từng phần theo D-IELTS-3.
  - Viết entity test cho từng quy tắc, theo `CONTRIBUTING.md` mục *Tests*.
- [ ] `ExamGradingQuery.GradingQuestion` hiện chỉ mang `options`. Thêm accepted answers
  và đáp án matching.
- [ ] **Quy đổi band.**
  - Tra `score_conversions` theo (IELTS, variant, section) rồi gán
    `ExamAttempt.convertedScore`.
  - Hiện không chỗ nào gán giá trị này, nên `RoadmapProgressionImpl.java:120` luôn bỏ
    qua lần thi mock. **TOEIC cũng đang bị lỗi này.**
  - Lưu band từng kỹ năng vào `attempt_section_results`. Bảng này chưa có entity.

### 2.3 Xuất bản đề

- [ ] `QuestionRepository.findIncompleteQuestionOrderNos` (dòng 63) coi câu nào có
  dưới 2 `question_options` là chưa hoàn chỉnh. Vì vậy **mọi câu điền từ và matching
  đều chặn xuất bản**. Viết lại theo từng loại:
  - `TEXT_ENTRY` cần ít nhất 1 accepted answer;
  - `MATCHING` cần đúng 1 option của bộ lựa chọn chung.
- [ ] Sửa nội dung lỗi `EXAM_HAS_INCOMPLETE_QUESTION` (`Exam.java:269`) cho các loại
  mới.
- [ ] `Exam.requireSittable` (`Exam.java:263`) bắt tổng điểm các section phải bằng
  `exams.max_raw_score`. Vì vậy đề IELTS L+R khai **80** (40 + 40), không phải 9. Band
  nằm ở `converted_score`.

### 2.4 Soạn, sửa và copy nội dung

- [ ] `UpdateExamContentRequest` và `SaveAuthoringRequest`: thêm accepted answers, bộ
  lựa chọn chung và đáp án matching.
- [ ] `ExamContentServiceImpl.deleteContent` (dòng 130–135) xoá rồi lưu lại toàn bộ nội
  dung đề. Mọi khoá ngoại đều là `on delete restrict`, nên phải xoá
  `question_matching_answers`, `question_accepted_answers` và `question_set_options`
  **trước** khi xoá câu hỏi và nhóm câu.
- [ ] `QuestionBankServiceImpl`: khi copy nhóm câu từ ngân hàng, phải copy cả 3 bảng
  mới.

### 2.5 Làm bài, xem kết quả, tutor

- [ ] `SubmitExamAttemptRequest` và `SaveExamDraftRequest` chỉ có `selectedOptionIds`.
  Thêm câu trả lời dạng chữ, và cho `validateSubmission` kiểm tra kiểu câu trả lời khớp
  với loại câu.
- [ ] Tính số câu hiển thị ở một chỗ (D-IELTS-5), rồi trả nó trong đề cho học viên,
  màn kết quả và ngữ cảnh tutor.
- [ ] Đề trả cho học viên (`LearnerExamPaperProjection`, `LearnerExamPaperQuery`):
  - trả bộ lựa chọn chung cho câu matching;
  - **không bao giờ** trả accepted answers;
  - không gửi transcript trong lúc làm bài (giống TOEIC: transcript nằm ở
    `question_sets.metadata`).
- [ ] Màn kết quả (`ExamAttemptServiceImpl.java:537`) hiện chỉ trả `correctOptionIds`.
  Thêm đáp án dạng chữ.
- [ ] `SubmittedExamContextReaderImpl` (ngữ cảnh cho tutor) hiện chỉ đọc option. Thêm
  câu trả lời dạng chữ.

### 2.6 Writing và Speaking (module `assessment`)

- [ ] `AssessmentTask` đã hỗ trợ `TASK_1`/`TASK_2`, `PART_1`/`PART_2`/`PART_3` và
  `certificateType = IELTS`. Còn phải:
  - map cột ảnh cho Task 1 Academic (sau migration ở mục 1);
  - xử lý D-IELTS-9 trong `AssessmentRubric`.

### 2.7 Full test (D-IELTS-10, module `exam`)

- [ ] Entity lượt full test giữ các luật sau, mỗi luật có entity test riêng:
  - chỉ gắn được lượt làm bài đúng task và của đúng học viên;
  - có band tạm khi đã có band L và R;
  - chỉ có band tổng khi đủ 4 kỹ năng.
- [ ] Chỉ người học chứng chỉ được bắt đầu full test. Kiểm tra qua
  `user.api.LearnerGoalReader`.
- [ ] Nghe `assessment.api.AssessmentAnswerGraded` và cập nhật lượt full test trong lúc
  giữ khoá, vì hai bài có thể được chấm cùng lúc. Sự kiện không bền, nên việc đọc lượt
  full test và một job định kỳ phải tự bắt kịp, giống level check.
- [ ] `assessment.api`: thêm reader trả band của một bài Writing.
- [ ] `user.api.PlacementRecorder`: thêm hàm ghi band IELTS vào
  `learner_profiles.current_score`, kèm CEFR lấy từ `score_conversions.cefr_level`. Ghi
  band tạm trước, band chính thức sau, không bắt làm lại onboarding.
- [ ] `exam.api`: phát sự kiện khi một lượt full test có band chính thức. Roadmap nghe
  sự kiện này để tính mốc thi thử IELTS.

### 2.8 Tài liệu

- [ ] Cập nhật [module-map.md](module-map.md) khi các bảng trên bắt đầu có entity.
- [ ] Ghi các quyết định D-IELTS-* vào [decisions.md](decisions.md).

---

## 3. BFF và FE (repo `Englow3_FE`, nhánh `testing`)

- [ ] `apps/bff/src/modules/exam/exam.typeDefs.ts`: thêm field mới, rồi sinh lại
  `apps/bff/src/generated/backend-openapi.ts` và `graphql.ts`.
- [ ] `apps/web/src/features/exam/components/blocks/QuestionCard`: hiện chỉ render radio
  và checkbox. Thêm:
  - ô nhập chữ;
  - dropdown cho câu matching;
  - ô trống nằm ngay trong đoạn văn hoặc bảng, theo quy ước D-IELTS-5.
- [ ] `ExamSittingView`, `hooks/useExamAutosave.ts` và `graphql/exam-attempt.graphql`:
  gửi và lưu nháp câu trả lời dạng chữ.
- [ ] Bỏ việc tự đếm số câu trong `ExamSittingView` và `QuestionCard`; dùng `numberFrom`
  và `numberTo` từ API (D-IELTS-5).
- [ ] `ExamResultView`: hiện đáp án dạng chữ.
- [ ] `features/content/components/blocks/ExamTreeEditor.tsx` và
  `features/content/api/authoringValidation.ts`: admin phải **soạn và duyệt** được loại
  câu mới.
  - **Không nạp đề IELTS vào môi trường mà editor chưa hỗ trợ loại câu mới.** Nếu admin
    mở đề rồi lưu lại, thao tác sẽ lỗi khoá ngoại hoặc làm mất đáp án.
- [ ] Writing Task 1: hiển thị ảnh biểu đồ.
- [ ] Màn full test: bắt đầu, tiến độ 4 kỹ năng, band tạm và band chính thức.
- [ ] Onboarding của người học IELTS: làm full test ở bước trình độ, rồi đi tiếp với
  band tạm.

---

## 4. Data pipeline

- [ ] Viết đề gốc theo D-IELTS-1. Đề mẫu chính thức chỉ dùng để đối chiếu format, không
  đưa vào thư mục nguồn của pipeline.
- [ ] **Schema.** Trong `schemas/exam.py`, `part_number` 1–7 và quy tắc 3/4 lựa chọn là
  của TOEIC. Cần một schema IELTS riêng:
  - Listening 4 part, Reading 3 section, 40 câu mỗi kỹ năng;
  - các loại câu IELTS;
  - accepted answers và giới hạn số từ.
- [ ] **Validator** thay cho `validators/part_rules.py`. Cần kiểm tra:
  - đủ 40 câu mỗi kỹ năng;
  - đáp án điền từ nằm trong giới hạn số từ và xuất hiện trong passage hoặc transcript;
  - câu điền từ xếp theo thứ tự thông tin xuất hiện trong bài;
  - phân bố True/False/Not Given hợp lý.
- [ ] **Tiêu chuẩn chất lượng IELTS**, tương đương [exam-quality-bar.md](exam-quality-bar.md)
  của TOEIC. Lưu ý `generators/authoring.py` đang cố định nhãn A–D và ngưỡng vị trí
  đáp án theo TOEIC.
- [ ] **Loader `load_ielts_sets.py`**, theo mẫu `generators/load_toeic_sets.py`:
  - insert SQL trực tiếp;
  - id cố định sinh từ một namespace riêng, để chạy lại không bị trùng;
  - thêm insert vào 3 bảng mới;
  - transcript lưu vào `question_sets.metadata`.
- [ ] **Audio.** `generators/synthesize_listening_audio.py` đã có giọng US, UK, AU, CA.
  - Part 1 và Part 3 là hội thoại; Part 2 và Part 4 là độc thoại.
  - Mỗi part một file, gắn vào `section_parts.audio_object_key`.
- [ ] **Ảnh** cho câu gắn nhãn bản đồ/sơ đồ: gắn vào `question_sets.image_object_key`.
- [ ] **Biểu đồ Writing Task 1:** vẽ bằng code từ bộ số liệu ghi trong `rubric_notes`
  (D-IELTS-9).

---

## 5. Chạy thử với một đề trước khi sinh hàng loạt

- [ ] Viết tay 1 đề Listening và 1 đề Reading, phủ đủ mọi loại câu ở bảng tham chiếu
  bên dưới.
- [ ] Nạp đề bằng loader. Admin mở editor, lưu lại, gửi duyệt, duyệt rồi xuất bản. Đề
  phải đi qua cả vòng này mà không mất dữ liệu.
- [ ] Học viên làm bài ở cả chế độ FULL và PRACTICE, lưu nháp rồi nộp. Kiểm tra: điểm
  thô, band từng kỹ năng, band tổng, màn kết quả, ngữ cảnh tutor.
- [ ] Roadmap: milestone `CERTIFICATE_MOCK` phải ghi nhận được lần thi.
- [ ] Full test: một học viên IELTS mới đăng ký làm trọn full test từ onboarding. Band
  tạm hiện ngay sau phần L+R, và band chính thức thay vào khi Writing/Speaking chấm xong.
- [ ] Chạy các lệnh kiểm tra trong `CONTRIBUTING.md` mục *Verification*:
  - `mvn formatter:validate`
  - `mvn --batch-mode clean verify`
  - `python -m pytest -q` trong `data_pipeline`

---

## Tham chiếu: cấu trúc đề IELTS

| Kỹ năng | Cấu trúc | Số câu | Thời gian |
|---|---|---:|---|
| Listening (chung Academic và GT) | Part 1: hội thoại đời thường · Part 2: độc thoại đời thường · Part 3: hội thoại học thuật, tối đa 4 người · Part 4: bài giảng | 40 | ~30 phút |
| Reading Academic | 3 bài dài, tổng 2 150–2 750 từ | 40 | 60 phút |
| Reading GT | Section 1: 2–3 văn bản ngắn · Section 2: 2 văn bản về công việc · Section 3: 1 bài dài | 40 | 60 phút |
| Writing | Task 1: ≥150 từ (Academic tả biểu đồ, GT viết thư) · Task 2: ≥250 từ | 2 bài | 60 phút |
| Speaking | Part 1: 4–5 phút · Part 2: 1 phút chuẩn bị + 1–2 phút nói · Part 3: 4–5 phút | 3 part | 11–14 phút |

Mỗi câu Listening và Reading được 1 điểm.

## Tham chiếu: dạng câu IELTS và kiểu tương ứng trong hệ thống

| Dạng câu IELTS | Kiểu trong hệ thống | Hiện chạy được? |
|---|---|---|
| Multiple choice, 1 đáp án | `SINGLE_CHOICE` | Có |
| Choose TWO/THREE letters | `MULTIPLE_CHOICE`, hoặc tách câu (D-IELTS-3) | Một phần: đang chấm kiểu tất cả hoặc không |
| True/False/Not Given, Yes/No/Not Given | `SINGLE_CHOICE`, 3 lựa chọn | Có |
| Matching headings, information, features, sentence endings | `MATCHING` + `question_set_options` | Chưa |
| Gắn nhãn bản đồ/sơ đồ bằng chữ cái | `MATCHING` + ảnh ở `question_sets` | Chưa |
| Summary completion chọn từ trong hộp | `MATCHING` | Chưa |
| Form, note, table, flow-chart, sentence, summary completion (tự điền) | `TEXT_ENTRY` + `question_accepted_answers` | Chưa |
| Short-answer questions | `TEXT_ENTRY` | Chưa |
| Điền nhãn sơ đồ (tự điền) | `TEXT_ENTRY` + ảnh | Chưa |
