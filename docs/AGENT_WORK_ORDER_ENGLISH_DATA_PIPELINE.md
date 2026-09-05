# AGENT WORK ORDER — English Learning Platform Data Pipeline

**Version:** 1.0.0
**Owner:** Quang Anh
**Ngày phát hành:** 2026-08-04
**Trạng thái:** ACTIVE — chờ Phase 0
**Phạm vi:** Xây dựng toàn bộ data pipeline (schema → validation → sinh dữ liệu → ingest) cho hệ thống học tiếng Anh A1–C1 + luyện thi định dạng TOEIC (L&R, S&W), tích hợp với knowledge tracing BKT/Elo/IRT và pgvector.

---

## 0. NGUYÊN TẮC VẬN HÀNH (BẮT BUỘC — ĐỌC TRƯỚC KHI LÀM BẤT CỨ VIỆC GÌ)

Đây là các ràng buộc cứng. Vi phạm bất kỳ điều nào = dừng công việc, báo cáo, chờ hướng dẫn.

### 0.1. Phase gating — KHÔNG tự động chạy tiếp

- Mỗi Phase kết thúc bằng một **STOP GATE**.
- Tại STOP GATE, agent **dừng hoàn toàn**, in báo cáo theo template ở §0.5, và chờ Owner gõ chính xác `APPROVE PHASE <N>` mới được sang Phase tiếp theo.
- **Không** được gộp phase. **Không** được "làm trước cho nhanh". **Không** được bắt đầu Phase N+1 rồi mới xin duyệt Phase N.
- Nếu Owner không phản hồi, agent chờ. Không tự suy diễn là đã được duyệt.

### 0.2. Bằng chứng thực thi — paste output thật

- Mọi khẳng định về trạng thái hệ thống phải kèm **lệnh chính xác đã chạy** và **output nguyên văn**.
- Cấm các câu như "đã chạy test và pass", "schema đã hợp lệ", "dữ liệu trông ổn" mà không có output.
- Nếu lệnh fail: paste nguyên traceback. **Không** được im lặng sửa rồi chỉ báo kết quả cuối.
- Định dạng:
  ```
  $ <lệnh>
  <output nguyên văn, không cắt xén; nếu quá dài thì cắt giữa và ghi rõ [... truncated N lines ...]>
  ```

### 0.3. Giả định phải được khai báo

- Mọi giả định (tên bảng, kiểu dữ liệu, đường dẫn, thư viện, quy ước đặt tên) phải được liệt kê trong mục `ASSUMPTIONS` của báo cáo phase.
- Giả định nào ảnh hưởng tới schema hoặc DB → **không được tự quyết**, phải hỏi trước.

### 0.4. Cấm nội dung "chế"

- **Cấm bịa nguồn dữ liệu.** Nếu cần wordlist NGSL/TSL/EVP/CEFR-J mà không tải được, báo cáo là không tải được. Không được tự sinh danh sách rồi gán nhãn là "NGSL".
- **Cấm bịa IPA.** IPA phải qua validator (§ Phase 5). Không tin output LLM trực tiếp.
- **Cấm bịa CEFR level.** Level phải có `cefr_source` truy vết được.
- **Cấm sao chép đề thi TOEIC thật.** Mọi item phải là nội dung sinh mới theo *định dạng* TOEIC. Không crawl, không tái tạo từ trí nhớ.

### 0.5. Template báo cáo bắt buộc tại mỗi STOP GATE

```markdown
## PHASE <N> REPORT — <tên phase>

### 1. ĐÃ LÀM
- <bullet, mỗi bullet gắn với 1 file/artifact cụ thể>

### 2. LỆNH ĐÃ CHẠY + OUTPUT
$ <cmd>
<output>

### 3. FILE TẠO/SỬA
| Path | Loại | LOC | Ghi chú |
|---|---|---|---|

### 4. ASSUMPTIONS
- <giả định 1> — mức rủi ro: LOW/MED/HIGH

### 5. VẤN ĐỀ PHÁT HIỆN
- <vấn đề> — đề xuất xử lý — cần Owner quyết? Y/N

### 6. DEFINITION OF DONE — CHECKLIST
- [x] / [ ] <từng tiêu chí DoD của phase>

### 7. CÂU HỎI CHO OWNER
- <nếu không có: "Không có">

**STATUS: WAITING FOR `APPROVE PHASE <N>`**
```

### 0.6. Ràng buộc kỹ thuật

- Python 3.11+. Pydantic v2. `jsonschema` (draft 2020-12). PostgreSQL 15+ với `pgvector`.
- Mọi model dữ liệu định nghĩa **một lần** trong Pydantic, JSON Schema **sinh ra từ** Pydantic (`model_json_schema()`) — không viết tay hai bản rồi lệch nhau.
- Validation là **reject-only**. Cấm auto-repair dữ liệu sai. Bản ghi sai → ghi vào `rejects/` kèm lý do, không tự sửa rồi cho qua.
- Mọi output JSON: UTF-8, `ensure_ascii=false`, không escape tiếng Việt.
- Không commit secret. Key đọc từ env.

### 0.7. Pháp lý & nhãn hiệu

- TOEIC® là nhãn hiệu của ETS. Trong **mọi** title, metadata, UI string: dùng `"TOEIC-format practice"` / `"Đề luyện theo định dạng TOEIC"`. Không dùng `"TOEIC Practice Test"` trần.
- `batch_metadata.is_ai_generated = true` là **bắt buộc** với mọi batch.
- Không lưu, không tham chiếu, không tái tạo nội dung đề thi ETS thật.

---

## 1. LỖI ĐÃ BIẾT TRONG SPEC GỐC — PHẢI SỬA, KHÔNG ĐƯỢC NHÂN BẢN

Spec gốc (`MASTER DATA EXTRACTION REPORT`) chứa các lỗi sau. Agent **không được** copy pattern từ spec gốc. Bản work order này là nguồn chân lý duy nhất.

| # | Lỗi trong spec gốc | Bắt buộc sửa thành |
|---|---|---|
| P0-1 | URL bị bọc cú pháp Markdown: `"[https://a](https://a)"` | URL trần. Validator regex `^https://` |
| P0-2 | `options` là object hard-code `{A,B,C,D}` | Array of option objects — Part 2 chỉ có 3 đáp án |
| P0-3 | Không có chỗ chứa passage → Part 5/6/7 bất khả biểu diễn | `groups[].passages[]` là **array** (Part 7 double/triple passage) |
| P0-4 | `transcript` liệt kê (A),(B) nhưng `options` có A–D | Transcript phải khớp đúng số lựa chọn của part |
| P0-5 | `total_records` / `total_questions` do LLM tự khai | Pipeline **tính lại**, assert bằng nhau, mismatch = reject |
| P1-1 | Không có `concept_ids` | Bắt buộc, ≥1 phần tử, phải tồn tại trong taxonomy |
| P1-2 | Không có `irt_params` | Bắt buộc, khởi tạo `calibration_status: "uncalibrated"` |
| P1-3 | Không có `difficulty_prior` | Bắt buộc, float 0–1, seed cho Elo |
| P1-4 | Không có `question_type` | Enum bắt buộc (§3.4) |
| P1-5 | Chỉ 1 `explanation` chung | Mỗi option có `rationale` riêng |
| P1-6 | Không có accent tag | `accent` enum US/UK/AU/CA cho mọi audio item |
| P1-7 | `id` LLM tự sinh (`vocab_c1_001`) → collide giữa các batch | Deterministic hash (§3.1) |
| P1-8 | Flashcard không có sense disambiguation | Khóa `(lemma, pos, sense_index)` |
| P1-9 | `collocations` flat string array | Object có `pattern` (mâu thuẫn với Mô-đun II của spec gốc) |
| P1-10 | Không có provenance | `generated_by`, `generated_at`, `review_status`, `schema_version` |
| P1-11 | Không định nghĩa field nào được embed | `embedding_text` tường minh trong schema |
| P1-12 | `question_number` dùng làm khóa | Tách `item_id` (ổn định) khỏi `position` (trong 1 đề) |

---

## 2. LỘ TRÌNH PHASE

```
Phase 0  Recon & Setup                    [no data written]
Phase 1  Concept Taxonomy                 [xương sống BKT]
Phase 2  Schema (Pydantic → JSON Schema → DDL)
Phase 3  Validation Harness + Round-trip Test
Phase 4  Seed Lists (wordlist, grammar syllabus)
Phase 5  Flashcard Enrichment
Phase 6  Grammar & Collocations Bank
Phase 7  Exam Bank — Reading (Part 5/6/7)
Phase 8  Exam Bank — Listening scripts + Audio pipeline
Phase 9  Speaking & Writing Bank
Phase 10 AI Assessment Prompts
Phase 11 Ingest → Postgres + pgvector
Phase 12 Final QA Report
```

Thứ tự này là bắt buộc. Lý do: taxonomy trước schema, schema trước validator, validator trước dữ liệu, Reading trước Listening (Reading không phụ thuộc audio).

---

## PHASE 0 — RECON & SETUP

**Mục tiêu:** Hiểu hiện trạng repo, dựng skeleton. **Không sinh dữ liệu.**

### Việc phải làm
1. Liệt kê cấu trúc repo hiện tại (`tree -L 3 -I 'node_modules|__pycache__|.git'`).
2. Xác định: backend nào đang giữ schema DB (Spring Boot hay FastAPI)? Migration tool là gì (Flyway/Liquibase/Alembic)? Báo cáo, **không tự chọn**.
3. Kiểm tra Postgres: version, đã có extension `vector` chưa (`SELECT * FROM pg_extension;`).
4. Dựng cây thư mục:
   ```
   ai/data_pipeline/
     taxonomy/
     schemas/          # Pydantic models
     schemas/json/     # JSON Schema sinh tự động
     seeds/
     generators/
     validators/
     output/
       flashcards/
       grammar/
       exams/
       speaking_writing/
       prompts/
     rejects/
     reports/
     tests/
   ```
5. `requirements.txt`: pydantic>=2.6, jsonschema>=4.21, psycopg[binary], sqlalchemy, python-dotenv, pytest, orjson, eng-to-ipa hoặc phonemizer, rapidfuzz.
6. Chạy `pip install -r requirements.txt`, paste output.

### DoD
- [ ] Cây thư mục tồn tại (paste `tree ai/data_pipeline`)
- [ ] `pip list | grep -E 'pydantic|jsonschema'` có output
- [ ] Đã trả lời rõ: migration tool nào, DB ở đâu, `vector` extension có chưa
- [ ] Chưa có một dòng dữ liệu nội dung nào được sinh

**STOP GATE 0**

---

## PHASE 1 — CONCEPT TAXONOMY

**Mục tiêu:** Danh sách concept cố định. Đây là thứ BKT cập nhật mastery lên. Không có nó thì mọi dữ liệu sinh ra đều phải làm lại.

### Việc phải làm
1. Tạo `taxonomy/concepts.yaml` với schema mỗi node:
   ```yaml
   - concept_id: gram_gerund_after_prep      # snake_case, ổn định vĩnh viễn
     name_en: "Gerund after preposition"
     name_vi: "Danh động từ sau giới từ"
     domain: grammar                          # grammar|vocabulary|reading|listening|speaking|writing
     cefr_band: [A2, B1]
     parent_id: gram_verb_patterns            # null nếu là root
     prerequisites: [gram_preposition_basic]  # DAG, không được có cycle
     bkt_priors:
       p_init: 0.25      # xác suất đã biết trước khi học
       p_learn: 0.15     # xác suất học được sau 1 lần gặp
       p_slip: 0.10      # biết nhưng làm sai
       p_guess: 0.25     # không biết nhưng đoán đúng (4 đáp án → 0.25)
     description_vi: "..."
   ```
2. Phủ tối thiểu:
   - **Grammar:** 60–90 concept, A1→C1, bám sát English Grammar Profile band.
   - **Reading skills:** `rc_main_idea`, `rc_detail`, `rc_inference`, `rc_vocab_in_context`, `rc_paraphrase`, `rc_not_true`, `rc_cross_reference` (Part 7 multi-passage), `rc_intent` (Part 7 chat).
   - **Listening skills:** `lc_photo_action`, `lc_photo_state`, `lc_wh_question`, `lc_yes_no`, `lc_indirect_response`, `lc_gist`, `lc_detail`, `lc_inference`, `lc_speaker_role`, `lc_graphic_reference`.
   - **Vocabulary:** theo topic × CEFR band (`vocab_business_b2`, ...).
   - **Speaking/Writing:** theo rubric dimension (`sp_pronunciation`, `sp_fluency`, `sp_grammar`, `sp_vocabulary`, `sp_content`, `wr_organization`, `wr_task_response`, ...).
3. Viết `validators/check_taxonomy.py`:
   - concept_id unique
   - prerequisites DAG không cycle (topological sort)
   - parent_id tồn tại
   - `p_guess` khớp số đáp án của dạng câu hỏi tương ứng (3 đáp án → 0.33, 4 → 0.25)
   - mọi `p_*` trong (0,1)
4. Xuất `reports/taxonomy_summary.md`: số concept theo domain, theo CEFR band, độ sâu cây, danh sách node lá.

### Chống lỗi thường gặp
- **Quá mịn:** 500 concept → mỗi concept có quá ít item → BKT không bao giờ hội tụ. Nhắm **~10–30 item/concept**.
- **Quá thô:** "grammar" là 1 concept → không chẩn đoán được gì.
- Nếu ước tính số concept vượt 200, **dừng lại hỏi Owner** trước khi viết tiếp.

### DoD
- [ ] `taxonomy/concepts.yaml` tồn tại, ≥100 concept
- [ ] `python validators/check_taxonomy.py` exit 0 — paste output
- [ ] Không có cycle trong prerequisite graph — paste kết quả topo sort
- [ ] `reports/taxonomy_summary.md` có bảng phân bố

**STOP GATE 1**

---

## PHASE 2 — SCHEMA

**Mục tiêu:** Định nghĩa dữ liệu một lần, sinh ra JSON Schema + DDL từ đó.

### 2.1. Quy tắc ID (áp dụng toàn hệ thống)

```python
def stable_id(prefix: str, *parts: str) -> str:
    raw = "|".join(p.strip().lower() for p in parts)
    return f"{prefix}_{hashlib.sha256(raw.encode()).hexdigest()[:16]}"
```
- Flashcard: `stable_id("vocab", lemma, pos, str(sense_index))`
- Exam item: `stable_id("itm", part_number, question_text, correct_option_text)`
- Group: `stable_id("grp", part_number, passage_hash)`

Tính idempotent: chạy lại pipeline trên cùng input → cùng ID → upsert thay vì nhân bản.

### 2.2. Base metadata (mọi batch đều có)

```python
class BatchMetadata(BaseModel):
    schema_version: Literal["1.0.0"]
    batch_id: str
    module_type: Literal["FLASHCARD","GRAMMAR","COLLOCATION","EXAM","SPEAKING","WRITING","SHADOWING","ASSESSMENT_PROMPT"]
    is_ai_generated: bool = True
    generated_by: str          # "claude-sonnet-4-5" — model + version cụ thể
    generated_at: datetime     # UTC ISO-8601
    review_status: Literal["draft","auto_validated","human_approved"] = "draft"
    total_records: int         # PIPELINE tính, không phải LLM khai
```

### 2.3. Flashcard

```python
class Definition(BaseModel):
    en: str = Field(min_length=5)
    vi: str = Field(min_length=2)

class Example(BaseModel):
    sentence: str
    translation: str
    source: Literal["generated","corpus"] = "generated"

class Collocation(BaseModel):
    pattern: Literal["V+N","ADJ+N","N+N","V+PREP","PREP+N","ADV+ADJ","N+PREP"]
    text: str
    cefr: CEFRLevel

class Flashcard(BaseModel):
    id: str                        # stable_id
    lemma: str
    pos: Literal["noun","verb","adjective","adverb","preposition","conjunction",
                 "pronoun","determiner","phrasal_verb","idiom","collocation"]
    sense_index: int = 1           # phân biệt address(n) vs address(v) vs address(v, "giải quyết")
    sense_label_en: str            # nhãn ngắn phân biệt nghĩa, vd "to deal with a problem"
    ipa_us: str                    # BẮT BUỘC — TOEIC là American English
    ipa_uk: str | None = None
    definition: Definition
    examples: list[Example] = Field(min_length=2, max_length=4)
    collocations: list[Collocation] = []   # bắt buộc ≥3 nếu cefr in {B2,C1}
    mnemonic_tip_vi: str | None = None
    cefr_level: CEFRLevel
    cefr_source: Literal["evp","cefrj","ngsl_band","llm_estimate","human_verified"]
    frequency_rank: int | None = None
    topics: list[str] = Field(min_length=1)
    concept_ids: list[str] = Field(min_length=1)
    difficulty_prior: float = Field(ge=0.0, le=1.0)
    embedding_text: str            # chuỗi ĐÚNG sẽ đưa vào pgvector — xem §2.7
    review_status: Literal["draft","auto_validated","human_approved"] = "draft"
```

### 2.4. Exam

```python
class Option(BaseModel):
    label: Literal["A","B","C","D"]
    text: str
    is_correct: bool
    rationale_vi: str              # BẮT BUỘC cho cả đáp án đúng lẫn distractor

class IRTParams(BaseModel):
    a: float | None = None         # discrimination
    b: float | None = None         # difficulty
    c: float | None = None         # guessing
    calibration_status: Literal["uncalibrated","provisional","calibrated"] = "uncalibrated"
    n_responses: int = 0

class EvidenceSpan(BaseModel):
    passage_order: int | None = None
    char_start: int | None = None
    char_end: int | None = None
    audio_start_ms: int | None = None
    audio_end_ms: int | None = None

class ExamItem(BaseModel):
    item_id: str                   # stable, tái sử dụng qua nhiều đề
    position: int | None = None    # vị trí trong đề CỤ THỂ, không phải khóa
    part_number: int = Field(ge=1, le=7)
    question_text: str | None      # Part 1 có thể null (câu hỏi nằm trong audio)
    question_type: QuestionType
    options: list[Option] = Field(min_length=3, max_length=4)
    concept_ids: list[str] = Field(min_length=1)
    difficulty_prior: float = Field(ge=0.0, le=1.0)
    irt_params: IRTParams = IRTParams()
    evidence_span: EvidenceSpan | None = None
    explanation: Definition        # {en, vi}
    embedding_text: str

    @model_validator(mode="after")
    def exactly_one_correct(self):
        n = sum(o.is_correct for o in self.options)
        if n != 1:
            raise ValueError(f"Phải có đúng 1 đáp án đúng, có {n}")
        return self

    @model_validator(mode="after")
    def part2_has_three_options(self):
        if self.part_number == 2 and len(self.options) != 3:
            raise ValueError("Part 2 bắt buộc 3 lựa chọn")
        if self.part_number != 2 and len(self.options) != 4:
            raise ValueError(f"Part {self.part_number} bắt buộc 4 lựa chọn")
        return self

class Passage(BaseModel):
    order: int
    passage_type: Literal["email","letter","notice","advertisement","article",
                          "memo","form","schedule","chart","chat_message","invoice","web_page"]
    text: str
    graphic_url: HttpUrl | None = None
    speaker: str | None = None     # cho chat chain
    timestamp: str | None = None

class AudioAsset(BaseModel):
    audio_url: HttpUrl | None = None       # null cho tới khi Phase 8 TTS xong
    script: str
    accent: Literal["US","UK","AU","CA"]
    speaker_count: int = 1
    duration_ms: int | None = None
    alignment_status: Literal["pending","aligned","failed"] = "pending"

class ExamGroup(BaseModel):
    group_id: str
    part_number: int
    passages: list[Passage] = []            # ARRAY — Part 7 double/triple
    image_url: HttpUrl | None = None
    audio: AudioAsset | None = None
    questions: list[ExamItem] = Field(min_length=1)
```

### 2.5. Ràng buộc theo part (viết thành `validators/part_rules.py`)

| Part | Kỹ năng | Đáp án | passages | image | audio | questions/group |
|---|---|---|---|---|---|---|
| 1 | L | 4 | 0 | bắt buộc | bắt buộc | 1 |
| 2 | L | **3** | 0 | 0 | bắt buộc | 1 |
| 3 | L | 4 | 0 | optional (graphic) | bắt buộc | 3 |
| 4 | L | 4 | 0 | optional (graphic) | bắt buộc | 3 |
| 5 | R | 4 | 1 (câu đơn) | 0 | 0 | 1 |
| 6 | R | 4 | 1 | 0 | 0 | 4 |
| 7 | R | 4 | 1–3 | optional | 0 | 2–5 |

Sai bất kỳ ô nào → reject.

### 2.6. Grammar / Collocation / Speaking / Writing

- `GrammarPoint`: `id, title_en, title_vi, cefr_level, concept_ids, theory_vi, form_patterns[], examples[], common_mistakes[{wrong, right, why_vi}], quick_exercises[], embedding_text`
- `SpeakingTask`: `task_id, part_number, prompt, prep_time_sec, response_time_sec, sample_answer_c1, rubric_ref, concept_ids, difficulty_prior`
- `WritingTask`: `task_id, task_type(email|opinion_essay|picture_description), prompt, min_words, max_words, sample_answer_c1, high_scoring_vocab[], rubric_ref, concept_ids`
- `Rubric`: `rubric_id, dimensions[{name, weight, band_descriptors{0..5}}]` — tách riêng, task chỉ tham chiếu `rubric_ref`.

### 2.7. Quy ước `embedding_text` (chốt cứng, không được ad-hoc)

| Loại | Công thức |
|---|---|
| Flashcard | `f"{lemma} ({pos}, {sense_label_en}). {definition.en} Examples: {'; '.join(e.sentence for e in examples)}"` |
| ExamItem | `f"[Part {part_number}][{question_type}] {question_text or ''} Correct: {correct_option.text}"` |
| GrammarPoint | `f"{title_en}. {theory_en_summary} Patterns: {'; '.join(form_patterns)}"` |

Embedding model chốt trước, ghi vào `schemas/embedding_config.yaml`: model name, dimension, normalize hay không. **Hỏi Owner** nếu chưa chốt.

### 2.8. Sinh artifact
```bash
python schemas/export_json_schema.py     # Pydantic → schemas/json/*.schema.json
python schemas/export_ddl.py             # → migrations/xxx_content_tables.sql
```
DDL phải có: `vector(N)` column, index `ivfflat`/`hnsw`, `UNIQUE` trên stable id, FK `concept_ids` → bảng `concepts`.

### DoD
- [ ] `python -c "import schemas; print('ok')"` chạy được
- [ ] JSON Schema sinh ra cho **cả 8** module_type — paste `ls schemas/json/`
- [ ] DDL sinh ra, `psql --dry-run` hoặc chạy trên DB test thành công — paste output
- [ ] Unit test cho `stable_id` (cùng input → cùng output) — paste `pytest` output
- [ ] Validator part rules từ chối đúng 3 case sai cố ý (Part 2 có 4 đáp án; 2 đáp án đúng; Part 7 có 4 passage)

**STOP GATE 2**

---

## PHASE 3 — VALIDATION HARNESS + ROUND-TRIP TEST

**Mục tiêu:** Có lưới chắn **trước khi** sinh dòng dữ liệu nào. Sinh trước validate sau = phải làm lại từ đầu.

### Việc phải làm
1. `validators/validate_batch.py`:
   - Layer 1: JSON parse + JSON Schema
   - Layer 2: Pydantic model validation
   - Layer 3: Cross-reference (`concept_ids` ⊆ taxonomy; `rubric_ref` tồn tại)
   - Layer 4: Business rules (part rules, đúng 1 đáp án, URL `^https://`, không còn cú pháp Markdown link)
   - Layer 5: Count assertion (`total_records == len(data)`)
   - Layer 6: Duplicate detection (ID trùng; nội dung gần trùng bằng rapidfuzz ≥ 0.92)
   - Output: `reports/validation_<batch_id>.json` + bản ghi hỏng → `rejects/`
2. **Round-trip integrity test** (`tests/test_roundtrip.py`) — cơ chế giống bài GSM:
   ```
   JSON gốc → Pydantic → DB row (insert) → SELECT → Pydantic → JSON
   assert sha256(canonical(json_in)) == sha256(canonical(json_out))
   ```
   `canonical()` = sort key, chuẩn hóa float, strip whitespace thừa. Bất kỳ field nào rơi rụng qua vòng này → test đỏ.
3. Fixture cố ý sai để test validator **thật sự từ chối** (không phải validator luôn trả pass):
   - `tests/fixtures/bad_markdown_url.json`
   - `tests/fixtures/bad_part2_four_options.json`
   - `tests/fixtures/bad_two_correct.json`
   - `tests/fixtures/bad_unknown_concept.json`
   - `tests/fixtures/bad_count_mismatch.json`
   - `tests/fixtures/bad_missing_irt.json`
   - `tests/fixtures/bad_duplicate_id.json`
4. `make validate BATCH=<path>` chạy được từ CLI.

### DoD
- [ ] `pytest tests/ -v` — **tất cả** fixture xấu bị reject với đúng error code — paste full output
- [ ] Round-trip test pass trên ≥1 sample mỗi module_type
- [ ] Validator exit code ≠ 0 khi có reject (để CI chặn được)

**STOP GATE 3**

---

## PHASE 4 — SEED LISTS

**Mục tiêu:** Chuyển mô hình từ *generation* sang *enrichment*. LLM **không được tự chọn từ vựng** — nó sẽ lặp `unprecedented`, `ubiquitous`, `leverage`, `paradigm` ở mọi batch.

### Việc phải làm
1. Thu thập wordlist từ nguồn thật (ưu tiên theo thứ tự):
   - NGSL / NAWL / TSL (TOEIC Service List) — mở, có trên GitHub
   - CEFR-J Wordlist
   - English Vocabulary Profile (nếu truy cập được)
2. Nếu **không** tải được nguồn nào: **dừng, báo cáo**. Không tự bịa danh sách.
3. Tạo `seeds/vocab_seed.csv`: `lemma, pos, cefr_level, cefr_source, frequency_rank, topic_hint`
4. Khử trùng lặp theo `(lemma, pos)`; phân bổ chỉ tiêu:

   | Level | Số mục tiêu | Ghi chú |
   |---|---|---|
   | A1 | 400 | |
   | A2 | 500 | |
   | B1 | 700 | |
   | B2 | 800 | bắt buộc có collocations |
   | C1 | 600 | bắt buộc có collocations |

   (Owner điều chỉnh nếu cần — hỏi tại gate.)
5. `seeds/grammar_syllabus.yaml`: danh sách grammar point B1–C1 map 1-1 với `concept_id` từ Phase 1.
6. `seeds/topic_taxonomy.yaml`: 8 topic từ spec gốc + subtopic.

### DoD
- [ ] `seeds/vocab_seed.csv` tồn tại — paste `wc -l` và `head -5`
- [ ] Nguồn của **từng** dòng truy vết được qua cột `cefr_source`
- [ ] Không có `(lemma, pos)` trùng — paste kết quả check
- [ ] Mọi grammar point trong syllabus có `concept_id` hợp lệ

**STOP GATE 4**

---

## PHASE 5 — FLASHCARD ENRICHMENT

**Mục tiêu:** Với **mỗi từ đã có trong seed**, sinh phần nội dung. Không thêm từ mới ngoài seed.

### Quy tắc sinh
- **Chunk size: 8 từ / 1 lần gọi LLM.** Không bao giờ vượt 10. Lý do: JSON truncation + chất lượng tụt rõ sau ~15 item.
- `temperature ≤ 0.4`.
- Prompt **bắt buộc** truyền vào: danh sách từ (cố định), CEFR band, topic, và **danh sách concept_id hợp lệ** để LLM chọn (không cho tự đặt tên concept).
- Validate **ngay sau mỗi chunk**. Chunk fail → retry tối đa 2 lần → vẫn fail thì đẩy vào `rejects/`, **không** chặn chunk sau.
- Checkpoint sau mỗi chunk (`output/flashcards/.progress.json`) để resume được.

### Post-processing bắt buộc
1. **IPA validation:** đối chiếu `ipa_us` với CMUdict / `eng-to-ipa` / `phonemizer`. Lệch → gắn `ipa_verified: false`, đưa vào danh sách review. **Không tin IPA do LLM sinh.**
2. **CEFR cross-check:** so `cefr_level` LLM gán với seed. Lệch ≥2 band → reject.
3. **Sense check:** nếu một `(lemma, pos)` có >1 nghĩa phổ biến mà chỉ sinh 1 record → cảnh báo vào report.
4. **Collocation rule:** B2/C1 mà `len(collocations) < 3` → reject.
5. **Near-duplicate:** rapidfuzz trên `definition.en` giữa các record, ngưỡng 0.92.

### DoD
- [ ] ≥95% seed word có record hợp lệ — paste con số thật
- [ ] `reports/flashcard_qa.md`: phân bố level, tỉ lệ IPA verified, tỉ lệ reject + lý do top-5
- [ ] **QA thủ công 30 record random** — agent in ra để Owner đọc, không tự tuyên bố đạt
- [ ] Tất cả file output pass `validate_batch.py`

**STOP GATE 5** — Gate này Owner đọc kỹ. Nếu chất lượng 30 mẫu không đạt, **quay lại sửa prompt và chạy lại toàn bộ**, không vá lẻ.

---

## PHASE 6 — GRAMMAR & COLLOCATIONS BANK

### Việc phải làm
- Mỗi grammar point trong `seeds/grammar_syllabus.yaml` → 1 `GrammarPoint` record.
- `common_mistakes`: tối thiểu 3 lỗi, **ưu tiên lỗi đặc trưng người Việt** (thiếu article, sai thì hiện tại hoàn thành vs quá khứ đơn, sai giới từ, thiếu -s ngôi 3, word order của tính từ).
- `quick_exercises`: 5 câu/point, tái dùng `ExamItem` schema (part_number = 5) để không tạo schema thứ hai.
- Collocation bank: gom từ `Collocation` đã sinh ở Phase 5, nhóm theo `pattern` × `topic`, khử trùng.

### DoD
- [ ] 100% grammar point có record — paste đối chiếu count
- [ ] Mọi `quick_exercise` pass `ExamItem` validator
- [ ] `reports/collocation_coverage.md`: số collocation theo pattern × topic

**STOP GATE 6**

---

## PHASE 7 — EXAM BANK: READING (Part 5, 6, 7)

Làm Reading trước vì không phụ thuộc audio.

### Chỉ tiêu (1 bộ đề đầy đủ = 100 câu Reading)
- Part 5: 30 câu (1 câu/group)
- Part 6: 16 câu (4 group × 4 câu)
- Part 7: 54 câu — single passage 29, double 10, triple 15

### Ràng buộc chất lượng
- **Distractor phải có lý do sai cụ thể**, ghi trong `rationale_vi`. Cấm distractor vô nghĩa hiển nhiên.
- Part 7: mỗi item bắt buộc có `evidence_span` trỏ vào passage — nếu không định vị được câu chứa đáp án thì item đó không hợp lệ.
- Double/triple passage: ≥2 câu phải là `rc_cross_reference` (bắt buộc đọc chéo ≥2 văn bản).
- Passage phải là nội dung mới hoàn toàn, bối cảnh business trung tính, **tên công ty/người phải là hư cấu**.
- Độ dài: Part 6 ~120–160 từ; Part 7 single ~150–250; mỗi passage của multi ~100–180.

### Quy trình
1. Sinh passage trước (1 call).
2. Sinh questions cho passage đó (1 call riêng, truyền passage vào context).
3. Sinh `evidence_span` bằng cách **string-match** đáp án vào passage (tính offset bằng code, **không** để LLM tự khai offset — nó sẽ khai sai).
4. Validate group.

### DoD
- [ ] ≥1 bộ Reading đủ 100 câu pass validator
- [ ] 100% Part 7 item có `evidence_span` khớp (offset verify bằng code)
- [ ] Phân bố `question_type` báo cáo trong `reports/reading_distribution.md`
- [ ] Mỗi item có ≥1 `concept_ids` hợp lệ; báo cáo concept nào <5 item (thiếu để BKT hội tụ)
- [ ] QA thủ công 15 câu random in ra cho Owner

**STOP GATE 7**

---

## PHASE 8 — LISTENING + AUDIO PIPELINE

**Cảnh báo:** LLM không sinh được audio. Đây là tầng riêng, tách hẳn.

### 8A — Script (LLM)
- Part 1: 6 câu — cần `image_url`. **Ảnh là điểm mù**: hoặc dùng ảnh CC0 có sẵn, hoặc sinh ảnh, hoặc để null + gắn `blocked_on: "image_asset"`. Agent **báo cáo và hỏi**, không tự quyết.
- Part 2: 25 câu — 3 đáp án. ≥30% phải là `lc_indirect_response` (đáp gián tiếp — đặc trưng khó của Part 2).
- Part 3: 39 câu — 13 hội thoại × 3 câu, 2–3 người nói.
- Part 4: 30 câu — 10 bài nói × 3 câu.
- **Phân bổ accent bắt buộc:** US 50%, UK ~17%, AU ~17%, CA ~17%. Tag `accent` trên từng `AudioAsset`.
- 2–3 group có graphic (bảng/lịch/biểu đồ) → `lc_graphic_reference`.

### 8B — Audio (không phải LLM)
Pipeline: `script → TTS multi-voice → forced alignment → duration → upload CDN → patch audio_url`

- TTS: chọn engine hỗ trợ nhiều giọng theo accent. **Hỏi Owner** engine nào (chi phí + license khác nhau nhiều).
- Forced alignment: WhisperX hoặc Montreal Forced Aligner → `audio_start_ms` / `audio_end_ms` cho `evidence_span` và cho shadowing.
- Trước khi có audio: `audio_url = null`, `alignment_status = "pending"`. Schema đã cho phép — **không** được nhét URL giả để "cho đẹp".

### 8C — Shadowing / Dictation
- 20–30 đoạn 30–60 giây, phân tầng theo CEFR, có timestamp từng câu.

### DoD
- [ ] 100 script Listening pass validator
- [ ] Phân bố accent đúng chỉ tiêu — paste bảng đếm
- [ ] Audio pipeline: có script chạy được **end-to-end trên 3 mẫu**, paste output + duration thật
- [ ] Item chưa có audio đều `alignment_status: "pending"`, không có URL giả
- [ ] Vấn đề ảnh Part 1 đã được nêu và Owner đã quyết

**STOP GATE 8**

---

## PHASE 9 — SPEAKING & WRITING

### Speaking (11 task, định dạng TOEIC S&W)
| Q# | Dạng | prep | response |
|---|---|---|---|
| 1–2 | Read aloud | 45s | 45s |
| 3–4 | Describe picture | 45s | 30s |
| 5–7 | Respond to questions | 0–3s | 15–30s |
| 8–10 | Respond using info provided | 45s | 15–30s |
| 11 | Express an opinion | 45s | 60s |

### Writing (8 task)
| Q# | Dạng | thời gian | min_words |
|---|---|---|---|
| 1–5 | Write a sentence based on a picture | 8 phút tổng | — |
| 6–7 | Respond to written request (email) | 10 phút/câu | 50 |
| 8 | Opinion essay | 30 phút | 300 |

### Yêu cầu
- `sample_answer_c1`: bài mẫu thật, đạt band cao nhất, có chú thích cấu trúc ăn điểm.
- `high_scoring_vocab`: liên kết ngược về `flashcard.id` nếu từ đó có trong bank.
- `Rubric` tách file riêng, band descriptor 0–5 đầy đủ từng dimension. Task chỉ giữ `rubric_ref`.

### DoD
- [ ] 11 Speaking + 8 Writing task pass validator
- [ ] Rubric có descriptor đủ mọi band × mọi dimension — không được để trống band nào
- [ ] Sample answer đếm từ đạt ngưỡng — paste kết quả đếm bằng code
- [ ] Mọi task có `concept_ids` map về rubric dimension concept

**STOP GATE 9**

---

## PHASE 10 — AI ASSESSMENT PROMPTS

**Mục tiêu:** System prompt để LLM chấm bài Speaking/Writing của học viên.

### Yêu cầu
- Mỗi prompt phải: nhận `rubric` + `task` + `student_answer`, trả **JSON đúng schema** (`AssessmentResult`).
- `AssessmentResult`: `{overall_band, dimension_scores[{dimension, score, evidence_quote, feedback_vi}], errors[{type, span, correction, concept_id}], next_concepts[]}`
- **Bắt buộc:** `errors[].concept_id` map về taxonomy → đây là đường dẫn feedback chấm điểm ngược về BKT. Không có nó thì việc chấm không cập nhật được mastery.
- Prompt phải chống: nịnh điểm (grade inflation), chấm lệch theo độ dài bài, chấp nhận bài lạc đề.
- Có **calibration set**: 10 bài mẫu ở các band khác nhau + điểm tham chiếu, để đo prompt có chấm nhất quán không.

### DoD
- [ ] Prompt cho Speaking + Writing, có test chạy thật
- [ ] Chạy calibration set, paste bảng: điểm tham chiếu vs điểm LLM, độ lệch trung bình
- [ ] Chạy **cùng 1 bài 3 lần**, báo cáo variance — nếu lệch >1 band thì prompt chưa đạt
- [ ] 100% output parse được thành `AssessmentResult`

**STOP GATE 10**

---

## PHASE 11 — INGEST → POSTGRES + PGVECTOR

### Việc phải làm
1. Migration tạo bảng (dùng đúng tool đã xác định ở Phase 0).
2. Loader idempotent: `INSERT ... ON CONFLICT (stable_id) DO UPDATE`.
3. Sinh embedding cho `embedding_text`, ghi vào cột `vector(N)`, tạo index HNSW.
4. Chạy lại **round-trip test** trên dữ liệu thật (không phải fixture).
5. Sanity query:
   - concept nào có <5 item (BKT sẽ không hội tụ)
   - item nào có `concept_ids` trỏ tới concept không tồn tại
   - phân bố `difficulty_prior` (nếu dồn cục quanh 0.5 → prior vô dụng cho Elo)
   - vector search thử 5 truy vấn, xem kết quả có hợp lý không

### DoD
- [ ] Migration chạy sạch trên DB trống — paste output
- [ ] Chạy loader **2 lần liên tiếp** → row count không đổi (chứng minh idempotent) — paste count cả 2 lần
- [ ] Round-trip test trên dữ liệu thật pass
- [ ] `reports/coverage_gaps.md` liệt kê concept thiếu item
- [ ] 5 truy vấn vector search có kết quả, paste top-3 mỗi truy vấn

**STOP GATE 11**

---

## PHASE 12 — FINAL QA REPORT

Xuất `reports/FINAL_QA.md`:
- Tổng số record theo module × CEFR level
- Ma trận phủ concept: concept × số item (highlight ô <5)
- Tỉ lệ reject và top-10 nguyên nhân
- Danh sách hạng mục còn `review_status: draft` (chưa human-approved)
- Danh sách `blocked_on` chưa giải quyết (ảnh Part 1, audio, v.v.)
- Nợ kỹ thuật + khuyến nghị bước tiếp theo
- Ước tính chi phí đã dùng (số token / số call LLM)

**STOP GATE 12 — kết thúc work order**

---

## 3. PHỤ LỤC

### 3.1. Enum `QuestionType`
```
# Reading
rc_main_idea, rc_detail, rc_inference, rc_vocab_in_context, rc_paraphrase,
rc_not_true, rc_cross_reference, rc_intent, rc_sentence_insertion
# Grammar (Part 5/6)
gr_word_form, gr_tense, gr_preposition, gr_conjunction, gr_pronoun,
gr_comparison, gr_relative_clause, gr_voice, gr_participle, gr_article
# Vocabulary (Part 5/6)
vc_word_choice, vc_collocation, vc_phrasal_verb
# Discourse (Part 6)
ds_cohesion, ds_sentence_insertion
# Listening
lc_photo_action, lc_photo_state, lc_wh_question, lc_yes_no, lc_indirect_response,
lc_gist, lc_detail, lc_inference, lc_speaker_role, lc_next_action, lc_graphic_reference
```

### 3.2. Enum `CEFRLevel`
`A1, A2, B1, B2, C1` (C2 ngoài phạm vi)

### 3.3. Cấu trúc file output
```
output/<module>/<module>_<level_or_part>_<topic>_<seq>.json
vd: output/flashcards/flashcard_C1_technology_001.json
    output/exams/exam_reading_part7_001.json
```

### 3.4. Bảng quyết định "khi nào phải dừng hỏi Owner"

| Tình huống | Hành động |
|---|---|
| Không tải được nguồn wordlist | DỪNG, hỏi |
| Số concept ước tính >200 | DỪNG, hỏi |
| Chưa chốt embedding model/dimension | DỪNG, hỏi |
| Chưa chốt TTS engine | DỪNG, hỏi (Phase 8) |
| Không có nguồn ảnh Part 1 | DỪNG, hỏi |
| Tỉ lệ reject >15% ở bất kỳ phase nào | DỪNG, hỏi |
| Phải sửa schema sau khi đã sinh dữ liệu | DỪNG, hỏi (có thể phải regenerate) |
| Cần chạy migration trên DB không phải test | DỪNG, hỏi |
| Muốn thêm field ngoài schema đã duyệt | DỪNG, hỏi |
| Muốn gộp phase cho nhanh | KHÔNG. Không hỏi, chỉ là không được làm |

### 3.5. Những câu agent KHÔNG được viết

- "Đã validate thành công" (không kèm output)
- "Dữ liệu chất lượng cao" (không kèm số liệu)
- "Tôi sẽ tiếp tục sang Phase tiếp theo" (khi chưa có APPROVE)
- "Tạm thời để URL placeholder cho đẹp"
- "Tôi giả định level của từ này là B2" (không có `cefr_source`)
- "Đã tự sửa các bản ghi lỗi" (validation là reject-only)

---

**HẾT WORK ORDER. Agent bắt đầu từ Phase 0 và dừng tại STOP GATE 0.**
