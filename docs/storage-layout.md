# Bố cục lưu trữ trên đĩa

Chưa dùng database. Dữ liệu nằm trên máy, chia thư mục sao cho lúc đưa vào Postgres
(Phase 11) chỉ là đọc file và `INSERT`, không phải viết lại logic.

---

## Hai tầng, đừng lẫn

```
output/
├── flashcards/  grammar/  exams/  speaking_writing/  prompts/
│        ↑ TẦNG 1 — batch do generator sinh ra, có lồng nhau, con người đọc được
│
└── _db/    ← TẦNG 2 — phẳng, mỗi file = một bảng, máy đọc
```

| | Tầng 1 `output/<module>/` | Tầng 2 `output/_db/` |
|---|---|---|
| Là gì | Nguồn chân lý, do generator sinh | Dẫn xuất, sinh lại được bất cứ lúc nào |
| Định dạng | JSON, có cấu trúc lồng | **JSONL** — 1 dòng = 1 row |
| Cấu trúc | Nhóm lồng câu hỏi, mảng `concept_ids` | Phẳng, quan hệ nhiều-nhiều tách thành bảng nối |
| Sửa tay | Không (validator từ chối) | Không bao giờ — xoá và sinh lại |
| Vào git | Không | Không |

Sửa dữ liệu thì sửa ở tầng 1 rồi chạy lại exporter. Tầng 2 xoá được mà không mất gì.

**Vì sao JSONL:** một dòng một row nên `COPY`/batch-insert thẳng được, nối thêm được
mà không phải đọc lại cả file, và không giữ cả tập trong RAM khi load.

---

## Bảng và thứ tự nạp

Nạp đúng thứ tự dưới đây thì khoá ngoại không bao giờ trỏ vào chỗ trống.

### Đợt 1 — không phụ thuộc gì

| File | Bảng | Khoá chính | Ghi chú |
|---|---|---|---|
| `concepts.jsonl` | `concepts` | `concept_id` | `parent_id` tự tham chiếu — nạp cả đợt rồi mới bật FK |
| `rubrics.jsonl` | `rubrics` | `rubric_id` | |

### Đợt 2 — phụ thuộc đợt 1

| File | Bảng | Khoá chính | FK |
|---|---|---|---|
| `concept_prerequisites.jsonl` | `concept_prerequisites` | (`concept_id`, `prerequisite_id`) | → `concepts` ×2 |
| `rubric_dimensions.jsonl` | `rubric_dimensions` | (`rubric_id`, `name`) | → `rubrics` |
| `flashcards.jsonl` | `flashcards` | `id` | |
| `grammar_points.jsonl` | `grammar_points` | `id` | |
| `exam_groups.jsonl` | `exam_groups` | `group_id` | |
| `exam_sets.jsonl` | `exam_sets` | `set_id` | |

### Đợt 3 — phụ thuộc đợt 2

| File | Bảng | Khoá chính | FK |
|---|---|---|---|
| `passages.jsonl` | `passages` | (`group_id`, `order`) | → `exam_groups` |
| `audio_assets.jsonl` | `audio_assets` | `group_id` | → `exam_groups` |
| `exam_items.jsonl` | `exam_items` | `item_id` | → `exam_groups` |
| `speaking_tasks.jsonl` | `speaking_tasks` | `task_id` | → `rubrics` |
| `writing_tasks.jsonl` | `writing_tasks` | `task_id` | → `rubrics` |

### Đợt 4 — bảng nối và bảng con

| File | Bảng | Khoá chính | FK |
|---|---|---|---|
| `options.jsonl` | `exam_item_options` | (`item_id`, `label`) | → `exam_items` |
| `flashcard_concepts.jsonl` | `flashcard_concepts` | (`flashcard_id`, `concept_id`) | → `flashcards`, `concepts` |
| `exam_item_concepts.jsonl` | `exam_item_concepts` | (`item_id`, `concept_id`) | → `exam_items`, `concepts` |
| `grammar_point_concepts.jsonl` | `grammar_point_concepts` | (`grammar_point_id`, `concept_id`) | → `grammar_points`, `concepts` |
| `task_concepts.jsonl` | `task_concepts` | (`task_id`, `concept_id`) | → speaking/writing, `concepts` |
| `flashcard_examples.jsonl` | `flashcard_examples` | (`flashcard_id`, `idx`) | → `flashcards` |
| `flashcard_collocations.jsonl` | `flashcard_collocations` | (`flashcard_id`, `idx`) | → `flashcards` |
| `exam_set_items.jsonl` | `exam_set_items` | (`set_id`, `section`, `position`) | → `exam_sets`, `exam_items` |

---

## Ba quy tắc khiến việc nạp DB về sau dễ

**1. `concept_ids` bị tách thành bảng nối.** Trong batch JSON nó là mảng
(`concept_ids: ["gram_article_definite", "vocab_business_b2"]`). Postgres không nên
lưu mảng khoá ngoại — không ràng buộc được, không index tốt, không join được. Exporter
tách thành `*_concepts.jsonl`, mỗi cặp một dòng.

**2. Khoá chính đã có sẵn, không sinh ở tầng DB.** `stable_id()` (§2.1) cho ID tất
định, nên `INSERT ... ON CONFLICT (id) DO UPDATE` là idempotent — chạy loader hai lần
row count không đổi, đúng DoD Phase 11. Không dùng `SERIAL`/`IDENTITY`.

**3. `embedding_text` đi kèm row, vector để trống.** Cột `embedding vector(1024)`
(xem [decisions.md](decisions.md) D2) để `NULL` lúc nạp; sinh embedding là bước riêng
sau đó. Không nhét vector vào JSONL — file sẽ phình vô ích và không đọc được.

---

## Lệnh

```bash
make export-db      # tầng 1 → tầng 2
```

Chạy lại được nhiều lần: exporter ghi đè `_db/`, không nối thêm.
`_db/_manifest.json` ghi số dòng mỗi file, thời điểm sinh, và thứ tự nạp — loader
Phase 11 đọc file này chứ không tự đoán.

## Khi nào mới cần DB

Chỉ Phase 11 (ingest + vector search) mới bắt buộc. Phase 2–10 sinh và validate dữ liệu
hoàn toàn trên đĩa. Nghĩa là blocker **B1** (không có Postgres) không chặn gì cho tới
lúc đó — xem [TODO.md](TODO.md).
