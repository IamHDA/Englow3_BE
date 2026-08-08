# TODO — Englow3 Data Pipeline

**Cập nhật:** 2026-08-08 · **Nhánh:** `feat/english-data-pipeline-phase1`
Nguồn chân lý: [AGENT_WORK_ORDER](AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md) · Quyết định đã chốt: [decisions.md](decisions.md) (D1–D8)

---

## 🔴 LÀM NGAY — việc của Owner

### Xoay vòng khoá Groq

```
gsk_en1Zoa…ExFRz   (khoá đầy đủ nằm trong lịch sử shell, không chép lại ở đây)
```

Từng hardcode trong 4 file generator. GitHub push protection đã chặn — **chưa lọt lên
remote** (kiểm bằng `git log --all -S`). Code đã sửa đọc từ `os.environ["GROQ_API_KEY"]`.

Vẫn phải vô hiệu hoá khoá này và tạo khoá mới: nó đã nằm plaintext trên đĩa, có thể
đã vào shell history hoặc backup. Tốn 2 phút, rủi ro thì không đo được.

---

## Hiện trạng dữ liệu

Toàn bộ đây là nội dung **viết tay, không gọi API**. Data cũ do LLM sinh hàng loạt đã
bị loại vào `rejects/` vì rỗng nội dung (xem "Bài học" ở cuối).

| Hạng mục | Số lượng | Trạng thái |
|---|---:|---|
| **Reading** | **100/100** | ✅ **đủ một phần thi hoàn chỉnh** |
| — Part 5 hoàn thành câu | 30/30 | ✅ |
| — Part 6 điền đoạn văn | 16/16 | ✅ 4 đoạn, mỗi đoạn 1 câu chèn câu |
| — Part 7 đọc hiểu | 54/54 | ✅ 13 single, 2 double, 3 triple |
| **Listening** | **46/100** | 🟡 đang viết |
| — Part 1 mô tả ảnh | 0/6 | 🔴 chặn bởi B6 (nguồn ảnh) |
| — Part 2 hỏi–đáp | 25/25 | ✅ |
| — Part 3 hội thoại | 21/39 | 🟡 còn 6 hội thoại |
| — Part 4 bài nói | 0/30 | 🔴 chưa bắt đầu |
| Flashcard | 60 | 🟡 thật 100%, nhưng ít (mục tiêu 800–1 000) |
| Grammar point | 16 | 🟡 64 lỗi L1 người Việt, 53 bài tập |
| Speaking / Writing | 11 + 8 | ✅ kèm 2 rubric 12 chiều × 6 band |
| Concept taxonomy | 177 (156 lá) | ✅ +6 concept lá nghe |
| File MP3 | 123 | 🔴 **mồ côi** — TTS của data đã bị loại |

**Audit: 0 LỖI, 3 CẢNH BÁO** — chạy `make gate`. Test: **66 xanh**.

Chỉ số thiên lệch trên toàn bộ 125 câu hiện có:

```
B-1 (4 lựa chọn, 100 câu)  A=26% B=25% C=25% D=24%   ✅
B-1 (3 lựa chọn,  25 câu)  A=36% B=32% C=32%         ✅
B-2 đáp án đúng dài nhất   15/125 = 12%              ✅ (ngưỡng 35%)
```

---

## Việc tiếp theo, theo thứ tự

| # | Việc | Còn thiếu | Ghi chú |
|---|---|---:|---|
| 1 | **Part 3** hội thoại | 18 câu (6 hội thoại) | Đang làm dở, batch 002 |
| 2 | **Part 4** bài nói | 30 câu (10 bài) | Thông báo, tin nhắn thoại, quảng cáo, hướng dẫn tham quan |
| 3 | **Part 1** mô tả ảnh | 6 câu | ⛔ chặn bởi B6 |
| 4 | Nối 123 MP3 mồ côi | — | Hoặc xoá, hoặc TTS lại theo kịch bản mới |
| 5 | `duration_ms` từ file MP3 thật | — | Hiện null; §Phase 8 cấm khai cứng |
| 6 | Forced alignment | — | `alignment_status` giữ `pending` cho tới khi có timestamp thật |
| 7 | Flashcard 60 → 800 | ~740 thẻ | ⛔ chặn bởi B10 |
| 8 | Grammar 16 → 90 concept lá | 74 | |
| 9 | Collocation | 4 200 | ⛔ chặn bởi B9 |

**91 concept lá vẫn 0 item** — BKT không cập nhật được cho những concept đó. Đây là
cảnh báo lớn nhất còn lại và nó sẽ tự giảm khi Part 3/4 và flashcard xong.

---

## Hạ tầng — ĐÃ XONG ✅

| | |
|---|---|
| Taxonomy + validator | 177 concept, DAG không cycle |
| Schema | 23 Pydantic model, 17 JSON Schema, DDL 21 bảng |
| Lưới chắn đa dạng | `validators/diversity.py` — che token rồi mới đếm |
| **Cổng ghi** | `generators/guarded_write.py` — chặn **TRƯỚC** khi ghi |
| Kiểm thiên lệch | `authoring.report_bias` — B-1/B-2 in ra ngay lúc sinh |
| Cổng chất lượng | `make gate` → `validators/audit_data.py`, exit ≠ 0 khi có LỖI |
| Tầng staging | `output/_db/` — 21 file JSONL = 21 bảng |

Lệnh: `make bootstrap` · `taxonomy` · `seed` · `gen-flashcards` · `gen-part5`
· `gen-part6` · `gen-part7` · `build-set` · `repair` · `gate` · `export-db` · `test`

---

## Blocker cần Owner quyết

| # | Việc | Chặn |
|---|---|---|
| B6 | Nguồn ảnh Part 1 (6 câu cần `image_url` hợp pháp) | Part 1, tức 6 câu cuối của Listening |
| B9 | Nguồn collocation hợp pháp — Oxford/Macmillan có bản quyền; NLTK cần corpus phù hợp (Reuters là tin tài chính 1980s, lệch văn phong) | 4 200 cụm |
| B10 | Giữ chỉ tiêu 3 000 flashcard, hay 800–1 000 chất lượng cao? | Khối lượng toàn bộ |
| B1 | Postgres — Owner chốt để local, chưa dựng | Phase 11 |
| B2 | Java 21 + Maven chưa cài | Phase 11 |

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
