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
  Xem [ATTRIBUTION.md](../ai/data_pipeline/seeds/ATTRIBUTION.md).

**Dựng lại trên máy mới:**
```bash
cd ai/data_pipeline/seeds/raw && ./fetch_wordlists.sh   # tải wordlist gốc
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

**Chốt:** [schemas/embedding_config.yaml](../ai/data_pipeline/schemas/embedding_config.yaml) — `dimension: 1024`, `BAAI/bge-m3`, chạy local, normalize L2, index HNSW `vector_cosine_ops`.

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
