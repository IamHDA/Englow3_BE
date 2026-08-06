# Quyết định đã chốt

Các blocker đang treo được chốt ngày **2026-08-06**. Mỗi mục ghi lựa chọn, lý do,
và cái gì phải làm lại nếu đổi ý.

---

## D1 — Dữ liệu học liệu chỉ lưu local, không đẩy git

**Chốt:** `output/**/*.json`, `seeds/vocab_seed.csv`, `seeds/by_level/`,
`seeds/raw/`, `rejects/*.json` đều nằm trong `.gitignore`.

**Vẫn nằm trong git:** script sinh dữ liệu, schema, taxonomy, validator, báo cáo QA.
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
```

**Hệ quả phải nhớ:** dữ liệu không có bản sao trên remote. Cần cơ chế backup riêng
trước khi Phase 5–9 sinh ra khối lượng lớn nội dung tốn tiền LLM.

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

## Còn treo — chưa quyết được vì phụ thuộc môi trường

| # | Việc | Chặn |
|---|---|---|
| B1 | Không có Postgres nào chạy được (không server ở 5432, không psql, docker socket thuộc user `admin`) | DoD Phase 2, Phase 11 |
| B2 | Java 21 + Maven chưa cài (quyền Homebrew) | Phase 11 |
| B4 | Vị trí DDL — đề xuất `src/main/resources/db/migration/V1__content_tables.sql` để Flyway là chủ sở hữu schema duy nhất | Phase 2 |
| B5 | TTS engine (chi phí + license khác nhau nhiều) | Phase 8 |
| B6 | Nguồn ảnh Part 1 | Phase 8 |

B1 và B2 là vấn đề môi trường máy, không phải quyết định thiết kế — cần thao tác của
Owner (khởi động Docker dưới account của bạn, hoặc cho phép `sudo chown` Homebrew,
hoặc cấp connection string remote).
