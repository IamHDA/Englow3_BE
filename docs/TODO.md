# TODO — Englow3 Data Pipeline

**Cập nhật:** 2026-08-24 · **Nhánh:** `feat/english-data-pipeline-completion`
Nguồn chân lý: [AGENT_WORK_ORDER](AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md) · Quyết định đã chốt: [decisions.md](decisions.md) (D1–D8)

---

## 🔴 LÀM NGAY — việc của Owner

### Xoay vòng khoá Groq

Một khoá Groq từng xuất hiện trong file local và có thể còn trong shell history hoặc
backup. Không lưu bất kỳ phần nào của credential vào tài liệu hay Git. Code hiện tại
không còn phụ thuộc trực tiếp vào khoá này.

Vẫn phải vô hiệu hoá khoá này và tạo khoá mới: nó đã nằm plaintext trên đĩa, có thể
đã vào shell history hoặc backup. Tốn 2 phút, rủi ro thì không đo được.

---

## Hiện trạng dữ liệu

Data sinh ra chỉ lưu local/object storage; Git chỉ giữ generator, schema, validator và
báo cáo QA. Mọi nội dung tự động vẫn ở trạng thái `draft` cho tới khi con người duyệt.

| Hạng mục | Số lượng | Trạng thái |
|---|---:|---|
| TOEIC-format | 10 bộ × 200 câu | ✅ 2.000 item không tái sử dụng giữa các bộ |
| Ngân hàng câu hỏi | 2.020 | ✅ 1.000 Listening + 1.020 Reading |
| Listening media | 540/540 | ✅ MP3 thật, duration và cue đo bằng công cụ |
| Ảnh Exam/Speaking/Writing | 105/105 | ✅ có file local và checksum |
| Flashcard | 3.000 | ✅ IPA xác minh, 6.000 audio US/UK |
| Grammar point | 90 | ✅ 450 quick exercise |
| Speaking / Writing | 11 + 8 | ✅ kèm 2 rubric 12 chiều × 6 band |
| Shadowing / Dictation | 30 clip | ✅ 120 segment có timestamp |
| Assessment | 2 prompt + 10 fixture | ✅ hợp schema; chưa phải human gold |
| Concept taxonomy | 177 (156 lá) | ✅ **156/156 lá đã có item** |

**Audit: 0 LỖI, 0 CẢNH BÁO** — chạy `make gate`. Data-pipeline test: **85 xanh**.

Chỉ số thiên lệch trên toàn bộ 2.020 câu:

```
B-1 (4 lựa chọn, 1.770 câu) A=27% B=26% C=24% D=23%  ✅
B-1 (3 lựa chọn,   250 câu) A=36% B=32% C=32%        ✅
B-2 đáp án đúng dài nhất    21%                      ✅ (ngưỡng 35%)
```

Không còn concept lá rỗng. Tuy nhiên 105/156 concept lá mới có 1–9 liên kết,
chưa đủ dữ liệu để tuyên bố BKT ổn định cho từng concept.

---

## Việc tiếp theo, theo thứ tự

| # | Việc | Còn thiếu | Ghi chú |
|---|---|---:|---|
| 1 | Human review | 5.161 record `draft` | Dùng hai human-review packet, chỉ reviewer mới được chuyển trạng thái |
| 2 | Object storage | 6.726 media | Chạy upload script và smoke-test URL `images/` + `audio/` trong môi trường được cấp quyền |
| 3 | IRT calibration | 2.020 item chưa calibrated | Cần response thật của learner |
| 4 | Assessment calibration | 10 fixture offline | Cần provider thật, chạy lặp 3 lần và human gold |
| 5 | BKT coverage | 105 concept dưới 10 liên kết | Cần thêm dữ liệu học thực tế hoặc nội dung đã human-review |

Các gate cấu trúc đã hoàn tất; các mục trên là gate con người hoặc production
observation, không được tự động đánh dấu hoàn thành.

---

## Hạ tầng — ĐÃ XONG ✅

| | |
|---|---|
| Taxonomy + validator | 177 concept, DAG không cycle |
| Schema | Pydantic + JSON Schema, DDL/Flyway 26 bảng staging |
| Lưới chắn đa dạng | `validators/diversity.py` — che token rồi mới đếm |
| **Cổng ghi** | `generators/guarded_write.py` — chặn **TRƯỚC** khi ghi |
| Kiểm thiên lệch | `authoring.report_bias` — B-1/B-2 in ra ngay lúc sinh |
| Cổng chất lượng | `make gate` → `validators/audit_data.py`, exit ≠ 0 khi có LỖI |
| Tầng staging | `output/_db/` — 26 file JSONL = 26 bảng |

Lệnh: `make bootstrap` · `taxonomy` · `seed` · `gen-flashcards` · `gen-part5`
· `gen-part6` · `gen-part7` · `build-set` · `repair` · `gate` · `export-db` · `test`

---

## Blocker cần Owner quyết

| # | Việc | Chặn |
|---|---|---|
| B13 | Human review | Chặn phát hành nội dung đang `draft` |
| B14 | Dữ liệu learner thật | Chặn IRT/BKT calibration |
| B15 | Assessment provider + human gold | Chặn đo variance và độ đồng thuận chấm điểm |
| B16 | Production object storage credentials | Chặn smoke-test URL ngoài local |
| B11 | Lịch sử git cũ còn 20 MB MP3 (đã gỡ khỏi tracking nhưng commit cũ vẫn giữ). Xoá hẳn phải `git filter-repo`, đổi mọi hash đã push | Dung lượng repo |

---

## Bài học — đừng lặp lại

1. **Lưới chắn phải nằm TRƯỚC lệnh ghi.** Đợt 2026-08-06 hỏng vì ghi trước kiểm sau —
   lúc phát hiện thì file đã nằm trên đĩa và đã được báo cáo "26 300 row, 0 lỗi".
2. **Đếm chuỗi thô không phát hiện được khuôn mẫu.** Thay một từ vào khuôn cố định
   vẫn ra chuỗi "duy nhất": dữ liệu hỏng đo được 83.9%. Che token rồi mới đếm → 0.03%.
3. **Đọc mẫu số, không chỉ đọc tỉ lệ.** "0 URL giả" trên 0 bản ghi khác hẳn trên 220 bản ghi.
4. **Không audit trong lúc dữ liệu đang được sinh.** Số liệu đã nhảy giữa hai lệnh liên tiếp.
5. **Commit script, không commit data.** Nhờ vậy khôi phục được 46 câu viết tay sau khi bị ghi đè.
6. **`git add -A` là cách nhanh nhất commit nhầm secret.** Kiểm `git diff --cached` trước khi commit.
7. **Validator hình thức không thay được QA nội dung.** Dữ liệu hỏng vượt 100% kiểm tra
   cấu trúc — phải mở file ra đọc mới thấy nó rỗng.
8. **Kiểm B-1/B-2 bắt được lỗi thật ở MỌI đợt nội dung, không sót đợt nào** — Part 5 dồn
   100% đáp án ở A, Part 6 50% đáp án đúng là câu dài nhất, Part 7 44% rồi 55% rồi 41%,
   Part 2 44%, Part 3 52%. Không có lỗi nào nhìn bằng mắt mà thấy.
9. **Audit cũng có lỗi của nó.** B-1 từng gộp câu 3 lựa chọn (Part 2, không có nhãn D)
   với câu 4 lựa chọn, làm D luôn tụt dưới 20% một cách giả tạo. Đã tách theo số lựa chọn.
10. **Tự kiểm số học trong chính nội dung mình viết.** Hai lỗi lọt qua schema vì schema
    không biết đếm: "vào làm 2/1, nộp đơn 26/5" bị tôi kết luận là đủ 6 tháng, và
    £470 × 12 bị viết thành £8,040.
11. **Chỉ tiêu của bộ đề phải nằm ở nơi dựng đề, không nằm ở kho.** `build_exam_set`
    từng gom mọi câu trong bank vào set_001, nên thêm 8 câu Part 7 là đề phình thành
    108 câu. Giờ bank là kho, đề lấy đúng 30/16/54 và 6/25/39/30.
12. **Gán chủ đề bằng tay thắng mọi suy đoán.** Suy chủ đề từ WordNet lexname chỉ
    đúng ~45% vì WordNet gộp mọi tính từ vào `adj.all`. `TOPIC_OVERRIDE` gán tay
    cho 126 từ, kèm khoá kiểm cấp độ — khoá này bắt được 3 thẻ bị dán nhãn sai.
13. **`.gitignore` không gỡ được thứ đã track.** 684 file MP3 (20 MB) vẫn nằm trong
    git và đã lên remote dù `.gitignore` có luật — vì chúng được commit TRƯỚC khi
    luật ra đời. Chỉ phát hiện ra khi tự đếm `git ls-files`, không phải khi đọc
    `.gitignore`. Đã `git rm --cached`, file vẫn nguyên trên đĩa.
14. **Chú thích trạng thái cũng hỏng theo thời gian.** Exporter in "(chờ Phase 8–10)"
    cho speaking/writing/rubric suốt nhiều đợt, trong khi dữ liệu đã có sẵn trên đĩa —
    nó in như vậy đơn giản vì chưa ai viết hàm đọc. 5 bảng rỗng vì code, không vì data.
