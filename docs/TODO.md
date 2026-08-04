# TODO — English Learning Data Pipeline

Bám theo [`AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md`](AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md).
Mỗi phase kết thúc bằng STOP GATE — chờ Owner gõ `APPROVE PHASE <N>` mới sang phase kế.

**Cập nhật lần cuối:** 2026-08-04
**Đang ở:** STOP GATE 0 — chờ `APPROVE PHASE 0`

---

## 🔴 BLOCKER — cần Owner quyết trước

| # | Vấn đề | Chặn phase | Lựa chọn |
|---|---|---|---|
| B1 | Không có Postgres nào chạy được. Không có server ở `localhost:5432`, không có `psql`, Docker socket thuộc user `admin` → permission denied. **Không xác định được `vector` extension đã cài chưa** | DoD 2, Phase 11 | (a) Owner chạy Docker `pgvector/pgvector:pg16` · (b) cho phép `sudo chown` Homebrew để cài local · (c) Owner cấp connection string remote |
| B2 | Java 21 + Maven chưa cài (cùng gốc vấn đề quyền Homebrew) | Phase 11 | Cài SDKMAN vào `~/.sdkman` (không cần root), hoặc Owner tự cài |
| B3 | Chưa chốt embedding model + dimension + normalize. DDL không biết `vector(N)` với N bao nhiêu | DoD 2, Phase 11 | Owner chốt → ghi `schemas/embedding_config.yaml` |
| B4 | §2.8 work order bảo xuất DDL ra `data_pipeline/migrations/`, nhưng repo dùng Flyway đọc `classpath:db/migration` và `ddl-auto: validate` | Phase 2 | Đề xuất: xuất vào `src/main/resources/db/migration/V1__content_tables.sql` — chờ xác nhận |
| B5 | Chưa chốt TTS engine (chi phí + license khác nhau nhiều) | Phase 8 | Owner chốt tại GATE 7 |
| B6 | Chưa có nguồn ảnh cho Part 1 Listening | Phase 8 | (a) ảnh CC0 · (b) sinh ảnh · (c) để `audio_url=null` + `blocked_on: "image_asset"` |

---

## Phase 0 — Recon & Setup ✅ HOÀN THÀNH (chờ duyệt)

- [x] Liệt kê cấu trúc repo
- [x] Xác định backend giữ schema DB → **Spring Boot 3.5.16 / Java 21** (không có FastAPI)
- [x] Xác định migration tool → **Flyway** (`classpath:db/migration`, hiện rỗng)
- [x] Xác định DB đích → `jdbc:postgresql://localhost:5432/englow3`, override qua env `DB_URL`
- [ ] ~~Kiểm tra Postgres version + `vector` extension~~ → **FAIL, blocked bởi B1**
- [x] Dựng cây thư mục `data_pipeline/`
- [x] `requirements.txt` + cài đặt (Python 3.12.13 qua `uv`, hệ thống chỉ có 3.9.6)
- [x] [`docs/phase0-recon.md`](phase0-recon.md)

**→ STOP GATE 0**

---

## Phase 1 — Concept Taxonomy

- [ ] `taxonomy/concepts.yaml` — ≥100 concept, dự kiến ~147:
  - [ ] Grammar 60–90 concept, A1→C1, bám English Grammar Profile band
  - [ ] Reading skills: `rc_main_idea`, `rc_detail`, `rc_inference`, `rc_vocab_in_context`, `rc_paraphrase`, `rc_not_true`, `rc_cross_reference`, `rc_intent`
  - [ ] Listening skills: `lc_photo_action`, `lc_photo_state`, `lc_wh_question`, `lc_yes_no`, `lc_indirect_response`, `lc_gist`, `lc_detail`, `lc_inference`, `lc_speaker_role`, `lc_graphic_reference`
  - [ ] Vocabulary: topic × CEFR band
  - [ ] Speaking/Writing: theo rubric dimension
- [ ] `validators/check_taxonomy.py` — unique id, DAG không cycle, parent tồn tại, `p_guess` khớp số đáp án (3→0.33, 4→0.25), mọi `p_*` ∈ (0,1)
- [ ] `reports/taxonomy_summary.md` — phân bố theo domain × CEFR, độ sâu cây, node lá
- [ ] Nhắm ~10–30 item/concept. Nếu ước tính >200 concept → DỪNG, hỏi Owner

**→ STOP GATE 1**

---

## Phase 2 — Schema

- [ ] `stable_id()` — sha256 16 hex, prefix theo loại
- [ ] Pydantic models: `BatchMetadata`, `Flashcard`, `ExamItem`, `ExamGroup`, `Passage`, `AudioAsset`, `Option`, `IRTParams`, `EvidenceSpan`, `GrammarPoint`, `SpeakingTask`, `WritingTask`, `Rubric`
- [ ] Enum `QuestionType` (§3.1), `CEFRLevel` (A1–C1)
- [ ] `validators/part_rules.py` — bảng ràng buộc part 1–7 (§2.5)
- [ ] `schemas/embedding_config.yaml` — **blocked bởi B3**
- [ ] `schemas/export_json_schema.py` → `schemas/json/*.schema.json` cho cả 8 module_type
- [ ] `schemas/export_ddl.py` → DDL có `vector(N)`, index HNSW, UNIQUE trên stable id, FK `concept_ids` → `concepts` — **blocked bởi B3, B4**
- [ ] Unit test `stable_id` idempotent
- [ ] Validator từ chối đúng 3 case sai cố ý: Part 2 có 4 đáp án · 2 đáp án đúng · Part 7 có 4 passage

**→ STOP GATE 2**

---

## Phase 3 — Validation Harness + Round-trip Test

- [ ] `validators/validate_batch.py` — 6 layer:
  - [ ] L1 JSON parse + JSON Schema
  - [ ] L2 Pydantic
  - [ ] L3 Cross-ref (`concept_ids` ⊆ taxonomy, `rubric_ref` tồn tại)
  - [ ] L4 Business rules (part rules, đúng 1 đáp án, URL `^https://`, không còn cú pháp Markdown link)
  - [ ] L5 Count assertion (`total_records == len(data)`)
  - [ ] L6 Duplicate (ID trùng; rapidfuzz ≥ 0.92)
- [ ] Output `reports/validation_<batch_id>.json` + reject → `rejects/`
- [ ] `tests/test_roundtrip.py` — JSON → Pydantic → DB → SELECT → Pydantic → JSON, so sha256 canonical
- [ ] 7 fixture cố ý sai: `bad_markdown_url`, `bad_part2_four_options`, `bad_two_correct`, `bad_unknown_concept`, `bad_count_mismatch`, `bad_missing_irt`, `bad_duplicate_id`
- [ ] `make validate BATCH=<path>` chạy được, exit code ≠ 0 khi có reject

**→ STOP GATE 3**

---

## Phase 4 — Seed Lists

- [ ] Tải wordlist nguồn thật: NGSL / NAWL / TSL → CEFR-J → EVP. **Không tải được nguồn nào → DỪNG, hỏi. Cấm tự bịa danh sách**
- [ ] `seeds/vocab_seed.csv`: `lemma, pos, cefr_level, cefr_source, frequency_rank, topic_hint`
- [ ] Khử trùng theo `(lemma, pos)`
- [ ] Chỉ tiêu: A1 400 · A2 500 · B1 700 · B2 800 · C1 600 (B2/C1 bắt buộc collocations)
- [ ] `seeds/grammar_syllabus.yaml` — map 1-1 với `concept_id` Phase 1
- [ ] `seeds/topic_taxonomy.yaml` — 8 topic + subtopic

**→ STOP GATE 4**

---

## Phase 5 — Flashcard Enrichment

- [ ] Generator: chunk **8 từ/call** (không quá 10), `temperature ≤ 0.4`
- [ ] Prompt truyền vào: danh sách từ cố định + CEFR band + topic + **danh sách concept_id hợp lệ** (không cho LLM tự đặt tên concept)
- [ ] Validate ngay sau mỗi chunk, retry tối đa 2 lần, fail → `rejects/`, không chặn chunk sau
- [ ] Checkpoint `output/flashcards/.progress.json` để resume
- [ ] Post-processing: IPA validation qua CMUdict → `ipa_verified` · CEFR cross-check (lệch ≥2 band → reject) · sense check · B2/C1 mà <3 collocation → reject · near-dup rapidfuzz 0.92
- [ ] ≥95% seed word có record hợp lệ
- [ ] `reports/flashcard_qa.md`
- [ ] In 30 record random cho Owner đọc

**→ STOP GATE 5** (nếu 30 mẫu không đạt → sửa prompt, chạy lại **toàn bộ**, không vá lẻ)

---

## Phase 6 — Grammar & Collocations Bank

- [ ] Mỗi grammar point trong syllabus → 1 `GrammarPoint`
- [ ] `common_mistakes` ≥3, ưu tiên lỗi đặc trưng người Việt (thiếu article, present perfect vs past simple, sai giới từ, thiếu -s ngôi 3, word order tính từ)
- [ ] `quick_exercises` 5 câu/point, tái dùng `ExamItem` schema (part_number=5)
- [ ] Collocation bank: gom từ Phase 5, nhóm `pattern` × `topic`, khử trùng
- [ ] `reports/collocation_coverage.md`

**→ STOP GATE 6**

---

## Phase 7 — Exam Bank: Reading (Part 5/6/7)

- [ ] Part 5: 30 câu · Part 6: 16 câu (4 group × 4) · Part 7: 54 câu (single 29, double 10, triple 15)
- [ ] Quy trình 4 bước: sinh passage → sinh questions (call riêng) → `evidence_span` bằng **string-match trong code** (không để LLM khai offset) → validate group
- [ ] Distractor phải có lý do sai cụ thể trong `rationale_vi`
- [ ] Double/triple: ≥2 câu `rc_cross_reference`
- [ ] Tên công ty/người **hư cấu**, bối cảnh business trung tính
- [ ] Độ dài: Part 6 ~120–160 từ · Part 7 single ~150–250 · mỗi passage multi ~100–180
- [ ] `reports/reading_distribution.md` + báo cáo concept nào <5 item
- [ ] In 15 câu random cho Owner

**→ STOP GATE 7**

---

## Phase 8 — Listening + Audio Pipeline

### 8A Script
- [ ] Part 1: 6 câu — **blocked bởi B6 (ảnh)**
- [ ] Part 2: 25 câu, 3 đáp án, ≥30% `lc_indirect_response`
- [ ] Part 3: 39 câu (13 hội thoại × 3), 2–3 người nói
- [ ] Part 4: 30 câu (10 bài nói × 3)
- [ ] Accent: US 50% · UK ~17% · AU ~17% · CA ~17%
- [ ] 2–3 group có graphic → `lc_graphic_reference`

### 8B Audio — **blocked bởi B5**
- [ ] `script → TTS multi-voice → forced alignment → duration → upload CDN → patch audio_url`
- [ ] Forced alignment: WhisperX / MFA → `audio_start_ms`/`audio_end_ms`
- [ ] Chưa có audio → `audio_url=null`, `alignment_status="pending"`. **Cấm nhét URL giả**
- [ ] Chạy end-to-end trên 3 mẫu, paste duration thật

### 8C Shadowing
- [ ] 20–30 đoạn 30–60s, phân tầng CEFR, timestamp từng câu

**→ STOP GATE 8**

---

## Phase 9 — Speaking & Writing

- [ ] 11 Speaking task (read aloud ×2 · describe picture ×2 · respond to questions ×3 · respond using info ×3 · opinion ×1) với `prep_time_sec`/`response_time_sec` đúng bảng
- [ ] 8 Writing task (picture sentence ×5 · email ×2 min 50 từ · opinion essay ×1 min 300 từ)
- [ ] `sample_answer_c1` có chú thích cấu trúc ăn điểm
- [ ] `high_scoring_vocab` link ngược về `flashcard.id`
- [ ] `Rubric` tách file riêng, band descriptor 0–5 đầy đủ **mọi** dimension, không để trống band nào
- [ ] Đếm từ sample answer bằng code

**→ STOP GATE 9**

---

## Phase 10 — AI Assessment Prompts

- [ ] Prompt Speaking + Writing: nhận `rubric` + `task` + `student_answer` → JSON `AssessmentResult`
- [ ] `AssessmentResult`: `{overall_band, dimension_scores[], errors[{type, span, correction, concept_id}], next_concepts[]}`
- [ ] `errors[].concept_id` **bắt buộc** map về taxonomy — đây là đường feedback ngược về BKT
- [ ] Chống grade inflation, chấm lệch theo độ dài, chấp nhận bài lạc đề
- [ ] Calibration set 10 bài + điểm tham chiếu → bảng so sánh, độ lệch trung bình
- [ ] Chạy cùng 1 bài 3 lần, báo variance — lệch >1 band là chưa đạt
- [ ] 100% output parse được

**→ STOP GATE 10**

---

## Phase 11 — Ingest → Postgres + pgvector — **blocked bởi B1, B2, B3, B4**

- [ ] Migration tạo bảng (Flyway)
- [ ] Loader idempotent `INSERT ... ON CONFLICT (stable_id) DO UPDATE`
- [ ] Sinh embedding → cột `vector(N)`, index HNSW
- [ ] Round-trip test trên **dữ liệu thật**
- [ ] Chạy loader 2 lần liên tiếp → row count không đổi
- [ ] Sanity query: concept <5 item · `concept_ids` mồ côi · phân bố `difficulty_prior` (dồn quanh 0.5 → prior vô dụng cho Elo) · 5 truy vấn vector search
- [ ] `reports/coverage_gaps.md`
- [ ] Xác định module nào sở hữu bảng content (dùng skill `design-backend-module`; repo chưa có `docs/module-map.md`)

**→ STOP GATE 11**

---

## Phase 12 — Final QA Report

- [ ] `reports/FINAL_QA.md`: tổng record theo module × CEFR · ma trận phủ concept (highlight <5) · tỉ lệ reject + top-10 nguyên nhân · danh sách còn `review_status: draft` · `blocked_on` chưa giải quyết · nợ kỹ thuật · ước tính token/call LLM

**→ STOP GATE 12 — kết thúc work order**

---

## Luật không được quên

- Validation **reject-only** — cấm auto-repair, bản ghi sai vào `rejects/` kèm lý do
- Cấm bịa nguồn dữ liệu, bịa IPA, bịa CEFR level, sao chép đề TOEIC thật
- `total_records` do **pipeline tính**, không phải LLM khai — mismatch = reject
- Mọi JSON: UTF-8, `ensure_ascii=false`
- TOEIC® là nhãn hiệu ETS → dùng "TOEIC-format practice" / "Đề luyện theo định dạng TOEIC"
- `is_ai_generated = true` bắt buộc mọi batch
- Tỉ lệ reject >15% ở bất kỳ phase nào → **DỪNG, hỏi Owner**
- Phải sửa schema sau khi đã sinh dữ liệu → **DỪNG, hỏi** (có thể phải regenerate)
- Cấm gộp phase
