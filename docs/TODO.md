# TODO — Englow3 Data Pipeline

**Cập nhật:** 2026-08-06 22:00 · **Nhánh:** `feat/english-data-pipeline-phase1`
Nguồn chân lý: [AGENT_WORK_ORDER](AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md) · Quyết định đã chốt: [decisions.md](decisions.md) (D1–D8)

---

## 🔴 LÀM NGAY

### 1. Xoay vòng khoá Groq

```
gsk_en1Zoa…ExFRz   (khoá đầy đủ nằm trong lịch sử shell, không chép lại ở đây)
```

Từng hardcode trong 4 file generator. GitHub push protection đã chặn — **chưa lọt lên
remote** (kiểm bằng `git log --all -S`). Code đã sửa đọc từ `os.environ["GROQ_API_KEY"]`.

Vẫn phải vô hiệu hoá khoá này và tạo khoá mới: nó đã nằm plaintext trên đĩa, có thể
đã vào shell history hoặc backup. Tốn 2 phút, rủi ro thì không đo được.

### 2. Sinh lại câu ví dụ flashcard

```
diversity definition.en    88.4%  OK
diversity examples[0].en   14.0%  ← REJECT (ngưỡng 60%)
```

3 000 thẻ có định nghĩa tốt nhưng **câu ví dụ vẫn điền khuôn** — 1 000 thẻ chỉ có
~70 mẫu câu, mỗi mẫu dùng lại ~14 lần. Cổng `guarded_write` sẽ chặn nếu ghi lại.

Sửa prompt để câu ví dụ thật sự khác nhau. **Đừng hạ ngưỡng.**

---

## Hiện trạng dữ liệu

| Hạng mục | Số lượng | Trạng thái |
|---|---:|---|
| Concept taxonomy | 171 (150 lá) | ✅ |
| Seed từ vựng | 3 000 | ✅ |
| Flashcard | 3 000 | 🟡 định nghĩa OK, **ví dụ điền khuôn** |
| Câu hỏi thi | 376 | ✅ 0 trùng, 0 vi phạm part rules |
| Bộ đề | 10 | 🔴 L=10 R=46, chuẩn 100+100 |
| Grammar point | 106 | 🟡 chưa kiểm chất lượng nội dung |
| File MP3 | 123 | ✅ audio thật |
| Concept lá có ≥10 item | 25/150 | 🔴 14 concept 0 item, 111 concept có 1–9 |

**Audit: 0 LỖI, 14 CẢNH BÁO** — chạy `make gate`.

---

## Hạ tầng — ĐÃ XONG ✅

| | |
|---|---|
| Taxonomy + validator | 171 concept, DAG không cycle |
| Schema | 23 Pydantic model, 17 JSON Schema, DDL 21 bảng |
| Lưới chắn đa dạng | `validators/diversity.py` — che token rồi mới đếm |
| **Cổng ghi** | `generators/guarded_write.py` — chặn **TRƯỚC** khi ghi |
| Sửa lỗi hàng loạt | `generators/repair_exam_bank.py` |
| Cổng chất lượng | `make gate` → `validators/audit_data.py`, exit ≠ 0 khi có LỖI |
| Test | **64 xanh** |
| Tầng staging | `output/_db/` — 21 file JSONL = 21 bảng |

Lệnh: `make bootstrap` · `taxonomy` · `seed` · `gen-flashcards` · `gen-part5`
· `gen-part6` · `repair` · `gate` · `export-db` · `test`

---

## Còn phải làm

### Nội dung — nút thắt chính

| Việc | Còn thiếu | Ghi chú |
|---|---:|---|
| Câu ví dụ flashcard | ~3 000 cặp EN/VI | Đang điền khuôn, phải sinh lại |
| Collocation | 4 200 | Chưa chốt nguồn hợp pháp (B9) |
| Câu Listening | ~1 400 | Để bộ đề đủ 100+100 |
| Speaking task | 11 | Concept `sp_*` đang 0 item |
| Writing task | 8 | Concept `wr_*` đang 0 item |
| Rubric | 2 | Band 0–5 đủ mọi dimension |
| Assessment prompt | 2 | Phase 10 |

Khối lượng tiếng Việt không có nguồn mở — WordNet chỉ phủ ~22% tổng nội dung
(2 796 định nghĩa EN + 2 126 ví dụ EN trên tổng 22 200 đơn vị).

### Kỹ thuật

- [ ] Nối `guarded_write` vào generator còn lại: `gen_toeic_reading_bank`,
      `gen_toeic_listening_bank`, `gen_full_exam_sets`, `gen_exam_sets_clean`
- [ ] Bảo vệ file `_001` khỏi bị ghi đè — đã mất 30 câu Part 5 viết tay một lần
      (khôi phục được vì generator nằm trong git)
- [ ] `duration_ms` đọc từ file MP3 thật thay vì khai cứng 8000
- [ ] Chạy forced alignment để `alignment_status: "aligned"` là sự thật
      (hiện 0/160 evidence_span có mốc thời gian)
- [ ] Dọn `.flashcards_groq.checkpoint.json` khỏi `output/flashcards/` — không phải
      batch, làm loader vấp
- [ ] Mở rộng `LEXNAME_TOPIC` để nâng tỉ lệ gán concept ngữ nghĩa (hiện 75%)

---

## Blocker cần Owner quyết

| # | Việc | Chặn |
|---|---|---|
| ~~B5~~ | ~~TTS engine~~ → **đã giải: Groq**, 123 MP3 thật | — |
| B6 | Nguồn ảnh Part 1 (6 câu cần `image_url`) | Phase 8 |
| B9 | Nguồn collocation hợp pháp — Oxford/Macmillan có bản quyền; NLTK cần corpus phù hợp (Reuters là tin tài chính 1980s, lệch văn phong) | 4 200 cụm |
| B10 | Giữ chỉ tiêu 3 000 từ, hay giảm còn 800–1 000 chất lượng cao? | Khối lượng toàn bộ |
| B1 | Postgres — Owner chốt để local, chưa dựng. DDL chưa được DB nào xác nhận cú pháp | Phase 11 |
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
