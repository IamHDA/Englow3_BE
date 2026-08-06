#!/usr/bin/env python3
"""Sinh DDL Postgres cho các bảng nội dung. §2.8.

Bảng và thứ tự nạp bám theo docs/storage-layout.md — mỗi file JSONL ở
output/_db/ ứng với đúng một bảng dưới đây.

    python schemas/export_ddl.py
    python schemas/export_ddl.py --out <path>

⚠️  Mặc định ghi vào data_pipeline/migrations/, KHÔNG ghi thẳng vào
    src/main/resources/db/migration/ của Spring Boot. Blocker B4 chưa chốt —
    xem docs/TODO.md. Flyway phải là chủ sở hữu schema duy nhất, nên việc đưa
    file vào đó là quyết định của Owner chứ không phải của script này.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
EMBEDDING_CONFIG = ROOT / "schemas" / "embedding_config.yaml"
DEFAULT_OUT = ROOT / "migrations" / "V1__content_tables.sql"


def load_embedding_config() -> dict:
    if not EMBEDDING_CONFIG.exists():
        sys.exit(f"Thiếu {EMBEDDING_CONFIG} — chưa chốt số chiều thì không sinh DDL được")
    return yaml.safe_load(EMBEDDING_CONFIG.read_text(encoding="utf-8"))


def build_ddl(dim: int, index_type: str, opclass: str, m: int, ef: int) -> str:
    return f"""-- =============================================================================
-- Bảng nội dung — SINH TỰ ĐỘNG bởi schemas/export_ddl.py
-- Không sửa tay. Sửa Pydantic model rồi chạy lại (§0.6).
--
-- Số chiều vector lấy từ schemas/embedding_config.yaml (quyết định D2).
-- Đổi số chiều = phải drop cột, dựng lại index, sinh lại toàn bộ embedding.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- --- Taxonomy: xương sống BKT ------------------------------------------------

CREATE TABLE IF NOT EXISTS concepts (
    concept_id      TEXT PRIMARY KEY,
    name_en         TEXT NOT NULL,
    name_vi         TEXT NOT NULL,
    domain          TEXT NOT NULL CHECK (domain IN
                        ('grammar','vocabulary','reading','listening','speaking','writing')),
    cefr_band_min   TEXT NOT NULL CHECK (cefr_band_min IN ('A1','A2','B1','B2','C1')),
    cefr_band_max   TEXT NOT NULL CHECK (cefr_band_max IN ('A1','A2','B1','B2','C1')),
    cefr_bands      TEXT[] NOT NULL,
    parent_id       TEXT REFERENCES concepts(concept_id),
    p_init          REAL NOT NULL CHECK (p_init  > 0 AND p_init  < 1),
    p_learn         REAL NOT NULL CHECK (p_learn > 0 AND p_learn < 1),
    p_slip          REAL NOT NULL CHECK (p_slip  > 0 AND p_slip  < 1),
    p_guess         REAL NOT NULL CHECK (p_guess > 0 AND p_guess < 1),
    description_vi  TEXT NOT NULL
);

-- DAG: cưỡng chế không self-loop ở tầng bảng; chống cycle dài hơn là việc của
-- validators/check_taxonomy.py, Postgres không diễn đạt được ràng buộc đó.
CREATE TABLE IF NOT EXISTS concept_prerequisites (
    concept_id      TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE CASCADE,
    prerequisite_id TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE CASCADE,
    PRIMARY KEY (concept_id, prerequisite_id),
    CHECK (concept_id <> prerequisite_id)
);

-- --- Flashcard ---------------------------------------------------------------

CREATE TABLE IF NOT EXISTS flashcards (
    id              TEXT PRIMARY KEY,
    lemma           TEXT NOT NULL,
    pos             TEXT NOT NULL,
    sense_index     INT  NOT NULL DEFAULT 1 CHECK (sense_index >= 1),
    sense_label_en  TEXT NOT NULL,
    ipa_us          TEXT NOT NULL,
    ipa_uk          TEXT,
    ipa_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    definition_en   TEXT NOT NULL,
    definition_vi   TEXT NOT NULL,
    mnemonic_tip_vi TEXT,
    cefr_level      TEXT NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1')),
    cefr_source     TEXT NOT NULL CHECK (cefr_source IN
                        ('evp','cefrj','octanove','ngsl_band','llm_estimate','human_verified')),
    frequency_rank  INT,
    topics          TEXT[] NOT NULL,
    difficulty_prior REAL NOT NULL CHECK (difficulty_prior BETWEEN 0 AND 1),
    embedding_text  TEXT NOT NULL,
    embedding       vector({dim}),
    review_status   TEXT NOT NULL DEFAULT 'draft',
    -- Lỗi P1-8: một nghĩa là một bản ghi
    UNIQUE (lemma, pos, sense_index)
);

CREATE TABLE IF NOT EXISTS flashcard_examples (
    flashcard_id    TEXT NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,
    idx             INT  NOT NULL,
    sentence        TEXT NOT NULL,
    translation     TEXT NOT NULL,
    source          TEXT NOT NULL DEFAULT 'generated',
    PRIMARY KEY (flashcard_id, idx)
);

CREATE TABLE IF NOT EXISTS flashcard_collocations (
    flashcard_id    TEXT NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,
    idx             INT  NOT NULL,
    pattern         TEXT NOT NULL,
    text            TEXT NOT NULL,
    cefr            TEXT NOT NULL,
    PRIMARY KEY (flashcard_id, idx)
);

-- --- Đề thi ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS exam_groups (
    group_id        TEXT PRIMARY KEY,
    part_number     INT  NOT NULL CHECK (part_number BETWEEN 1 AND 7),
    image_url       TEXT
);

CREATE TABLE IF NOT EXISTS passages (
    group_id        TEXT NOT NULL REFERENCES exam_groups(group_id) ON DELETE CASCADE,
    "order"         INT  NOT NULL CHECK ("order" >= 1),
    passage_type    TEXT NOT NULL,
    text            TEXT NOT NULL,
    graphic_url     TEXT,
    speaker         TEXT,
    ts              TEXT,
    PRIMARY KEY (group_id, "order")
);

CREATE TABLE IF NOT EXISTS audio_assets (
    group_id            TEXT PRIMARY KEY REFERENCES exam_groups(group_id) ON DELETE CASCADE,
    audio_url           TEXT,               -- NULL cho tới khi Phase 8 TTS xong
    script              TEXT NOT NULL,
    accent              TEXT NOT NULL CHECK (accent IN ('US','UK','AU','CA')),
    speaker_count       INT  NOT NULL DEFAULT 1,
    duration_ms         INT,
    alignment_status    TEXT NOT NULL DEFAULT 'pending',
    -- Cấm khai đã căn chỉnh khi chưa có audio (§Phase 8)
    CHECK (alignment_status <> 'aligned' OR audio_url IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS exam_items (
    item_id         TEXT PRIMARY KEY,
    group_id        TEXT NOT NULL REFERENCES exam_groups(group_id) ON DELETE CASCADE,
    part_number     INT  NOT NULL CHECK (part_number BETWEEN 1 AND 7),
    question_text   TEXT,                   -- Part 1 có thể NULL
    question_type   TEXT NOT NULL,
    difficulty_prior REAL NOT NULL CHECK (difficulty_prior BETWEEN 0 AND 1),
    irt_a           REAL,
    irt_b           REAL,
    irt_c           REAL,
    calibration_status TEXT NOT NULL DEFAULT 'uncalibrated'
                        CHECK (calibration_status IN ('uncalibrated','provisional','calibrated')),
    n_responses     INT  NOT NULL DEFAULT 0,
    evidence_passage_order INT,
    evidence_char_start    INT,
    evidence_char_end      INT,
    evidence_audio_start_ms INT,
    evidence_audio_end_ms   INT,
    explanation_en  TEXT NOT NULL,
    explanation_vi  TEXT NOT NULL,
    embedding_text  TEXT NOT NULL,
    embedding       vector({dim}),
    review_status   TEXT NOT NULL DEFAULT 'draft',
    -- Chưa có lượt trả lời thì không được khai đã hiệu chuẩn
    CHECK (calibration_status <> 'calibrated'  OR n_responses >= 200),
    CHECK (calibration_status <> 'provisional' OR n_responses >= 30)
);

CREATE TABLE IF NOT EXISTS exam_item_options (
    item_id         TEXT NOT NULL REFERENCES exam_items(item_id) ON DELETE CASCADE,
    label           TEXT NOT NULL CHECK (label IN ('A','B','C','D')),
    text            TEXT NOT NULL,
    is_correct      BOOLEAN NOT NULL,
    rationale_vi    TEXT NOT NULL,          -- lỗi P1-5: cả distractor cũng phải có
    PRIMARY KEY (item_id, label)
);

-- Đúng một đáp án đúng mỗi câu. Postgres không có CHECK liên dòng, nên dùng
-- unique partial index — hiệu quả tương đương và cưỡng chế được ở tầng bảng.
CREATE UNIQUE INDEX IF NOT EXISTS exam_item_one_correct
    ON exam_item_options (item_id) WHERE is_correct;

CREATE TABLE IF NOT EXISTS exam_sets (
    set_id          TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    total_questions INT  NOT NULL CHECK (total_questions >= 0)
);

CREATE TABLE IF NOT EXISTS exam_set_items (
    set_id          TEXT NOT NULL REFERENCES exam_sets(set_id) ON DELETE CASCADE,
    section         TEXT NOT NULL CHECK (section IN ('listening','reading')),
    position        INT  NOT NULL CHECK (position >= 1),
    item_id         TEXT NOT NULL REFERENCES exam_items(item_id),
    PRIMARY KEY (set_id, section, position),
    -- Một câu không được xuất hiện hai lần trong cùng một đề
    UNIQUE (set_id, item_id)
);

-- --- Grammar -----------------------------------------------------------------

CREATE TABLE IF NOT EXISTS grammar_points (
    id              TEXT PRIMARY KEY,
    title_en        TEXT NOT NULL,
    title_vi        TEXT NOT NULL,
    cefr_level      TEXT NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1')),
    theory_vi       TEXT NOT NULL,
    theory_en_summary TEXT NOT NULL,
    form_patterns   TEXT[] NOT NULL,
    embedding_text  TEXT NOT NULL,
    embedding       vector({dim}),
    review_status   TEXT NOT NULL DEFAULT 'draft'
);

-- --- Speaking / Writing ------------------------------------------------------

CREATE TABLE IF NOT EXISTS rubrics (
    rubric_id       TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    version         TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS rubric_dimensions (
    rubric_id       TEXT NOT NULL REFERENCES rubrics(rubric_id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    weight          REAL NOT NULL CHECK (weight > 0 AND weight <= 1),
    concept_id      TEXT NOT NULL REFERENCES concepts(concept_id),
    band_descriptors JSONB NOT NULL,
    PRIMARY KEY (rubric_id, name)
);

CREATE TABLE IF NOT EXISTS speaking_tasks (
    task_id         TEXT PRIMARY KEY,
    part_number     INT  NOT NULL CHECK (part_number BETWEEN 1 AND 11),
    prompt          TEXT NOT NULL,
    prep_time_sec   INT  NOT NULL,
    response_time_sec INT NOT NULL,
    sample_answer_c1 TEXT NOT NULL,
    rubric_ref      TEXT NOT NULL REFERENCES rubrics(rubric_id),
    difficulty_prior REAL NOT NULL CHECK (difficulty_prior BETWEEN 0 AND 1),
    review_status   TEXT NOT NULL DEFAULT 'draft'
);

CREATE TABLE IF NOT EXISTS writing_tasks (
    task_id         TEXT PRIMARY KEY,
    task_type       TEXT NOT NULL CHECK (task_type IN
                        ('email','opinion_essay','picture_description')),
    prompt          TEXT NOT NULL,
    min_words       INT,
    max_words       INT,
    sample_answer_c1 TEXT NOT NULL,
    high_scoring_vocab TEXT[] NOT NULL DEFAULT '{{}}',
    rubric_ref      TEXT NOT NULL REFERENCES rubrics(rubric_id),
    difficulty_prior REAL NOT NULL CHECK (difficulty_prior BETWEEN 0 AND 1),
    review_status   TEXT NOT NULL DEFAULT 'draft',
    CHECK (max_words IS NULL OR min_words IS NULL OR max_words >= min_words)
);

-- --- Bảng nối concept --------------------------------------------------------
-- Lỗi P1-1 + quyết định D6: concept_ids KHÔNG lưu thành mảng khoá ngoại.
-- Mảng thì Postgres không ràng buộc được, không index tốt, không join được.

CREATE TABLE IF NOT EXISTS flashcard_concepts (
    flashcard_id    TEXT NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,
    concept_id      TEXT NOT NULL REFERENCES concepts(concept_id),
    PRIMARY KEY (flashcard_id, concept_id)
);

CREATE TABLE IF NOT EXISTS exam_item_concepts (
    item_id         TEXT NOT NULL REFERENCES exam_items(item_id) ON DELETE CASCADE,
    concept_id      TEXT NOT NULL REFERENCES concepts(concept_id),
    PRIMARY KEY (item_id, concept_id)
);

CREATE TABLE IF NOT EXISTS grammar_point_concepts (
    grammar_point_id TEXT NOT NULL REFERENCES grammar_points(id) ON DELETE CASCADE,
    concept_id      TEXT NOT NULL REFERENCES concepts(concept_id),
    PRIMARY KEY (grammar_point_id, concept_id)
);

CREATE TABLE IF NOT EXISTS task_concepts (
    task_id         TEXT NOT NULL,
    task_kind       TEXT NOT NULL CHECK (task_kind IN ('speaking','writing')),
    concept_id      TEXT NOT NULL REFERENCES concepts(concept_id),
    PRIMARY KEY (task_id, task_kind, concept_id)
);

-- --- Index -------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_flashcards_cefr      ON flashcards (cefr_level);
CREATE INDEX IF NOT EXISTS idx_exam_items_part      ON exam_items (part_number);
CREATE INDEX IF NOT EXISTS idx_exam_items_type      ON exam_items (question_type);
CREATE INDEX IF NOT EXISTS idx_exam_items_group     ON exam_items (group_id);
CREATE INDEX IF NOT EXISTS idx_concepts_domain      ON concepts (domain);
CREATE INDEX IF NOT EXISTS idx_fc_concepts_concept  ON flashcard_concepts (concept_id);
CREATE INDEX IF NOT EXISTS idx_ei_concepts_concept  ON exam_item_concepts (concept_id);

-- Vector: {index_type} với {opclass}. embedding để NULL lúc nạp; sinh vector là
-- bước riêng sau đó (quyết định D6).
CREATE INDEX IF NOT EXISTS idx_flashcards_embedding ON flashcards
    USING {index_type} (embedding {opclass}) WITH (m = {m}, ef_construction = {ef});
CREATE INDEX IF NOT EXISTS idx_exam_items_embedding ON exam_items
    USING {index_type} (embedding {opclass}) WITH (m = {m}, ef_construction = {ef});
CREATE INDEX IF NOT EXISTS idx_grammar_embedding    ON grammar_points
    USING {index_type} (embedding {opclass}) WITH (m = {m}, ef_construction = {ef});
"""


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = ap.parse_args()

    cfg = load_embedding_config()
    dim = cfg["dimension"]
    pg = cfg["pgvector"]
    ddl = build_ddl(dim, pg["index_type"], pg["opclass"], pg["m"], pg["ef_construction"])

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(ddl, encoding="utf-8")

    n_tables = ddl.count("CREATE TABLE")
    n_index = ddl.count("CREATE INDEX") + ddl.count("CREATE UNIQUE INDEX")
    print(f"Ghi {args.out.relative_to(ROOT.parent)}")
    print(f"  {n_tables} bảng, {n_index} index, {ddl.count(chr(10))} dòng")
    print(f"  vector({dim}) — {pg['index_type']} / {pg['opclass']}")
    print(f"  model: {cfg['model']['name']}")
    print()
    print("Chưa chạy được trên DB: không có Postgres nào truy cập được (blocker B1).")
    print("Chưa ghi vào src/main/resources/db/migration/: blocker B4 chưa chốt.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
