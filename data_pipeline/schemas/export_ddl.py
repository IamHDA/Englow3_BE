#!/usr/bin/env python3
"""Sinh DDL Postgres cho các bảng nội dung. §2.8.

Bảng và thứ tự nạp bám theo docs/storage-layout.md — mỗi file JSONL ở
output/_db/ ứng với đúng một bảng dưới đây.

    python schemas/export_ddl.py
    python schemas/export_ddl.py --out <path>

Ghi vào src/main/resources/db/migration/ để Flyway là chủ sở hữu schema duy
nhất (blocker B4 đã chốt — xem docs/decisions.md D8).

⚠️  Flyway KHÔNG BAO GIỜ được sửa migration đã chạy. Một khi file này đã áp lên
    một DB thật, mọi thay đổi schema phải là V2, V3... chứ không phải sinh đè
    lên V1. Trước lần chạy đầu tiên thì sinh đè thoải mái.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
EMBEDDING_CONFIG = ROOT / "schemas" / "embedding_config.yaml"
DEFAULT_OUT = ROOT.parent / "src" / "main" / "resources" / "db" / "migration" / "V1__content_tables.sql"


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
    cues                JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(cues) = 'array'),
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
    review_status   TEXT NOT NULL DEFAULT 'draft'
    -- Owner từ chối CHECK ràng buộc calibration_status với n_responses (D7)
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
    review_status   TEXT NOT NULL DEFAULT 'draft',
    image_url       TEXT,
    stimulus_text   TEXT,
    prompt_audio    JSONB CHECK (prompt_audio IS NULL OR jsonb_typeof(prompt_audio) = 'object')
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
    image_url       TEXT,
    stimulus_text   TEXT,
    CHECK (max_words IS NULL OR min_words IS NULL OR max_words >= min_words)
);

-- --- Shadowing / AI assessment ----------------------------------------------

CREATE TABLE IF NOT EXISTS assessment_prompts (
    prompt_id          TEXT PRIMARY KEY,
    target             TEXT NOT NULL CHECK (target IN ('speaking','writing')),
    rubric_ref         TEXT NOT NULL REFERENCES rubrics(rubric_id),
    system_prompt      TEXT NOT NULL,
    output_schema_ref  TEXT NOT NULL DEFAULT 'AssessmentResult',
    version            TEXT NOT NULL,
    review_status      TEXT NOT NULL DEFAULT 'draft'
        CHECK (review_status IN ('draft','auto_validated','human_approved')),
    CHECK (btrim(system_prompt) <> ''),
    CHECK (version ~ '^\d+\.\d+\.\d+$')
);

CREATE TABLE IF NOT EXISTS assessment_calibration_cases (
    case_id                TEXT PRIMARY KEY,
    target                 TEXT NOT NULL CHECK (target IN ('speaking','writing')),
    prompt_id              TEXT NOT NULL REFERENCES assessment_prompts(prompt_id),
    task                   TEXT NOT NULL,
    learner_response       TEXT NOT NULL,
    delivery_observations  JSONB,
    expected_result        JSONB NOT NULL CHECK (jsonb_typeof(expected_result) = 'object'),
    CHECK (btrim(task) <> ''),
    CHECK (btrim(learner_response) <> '')
);

CREATE TABLE IF NOT EXISTS shadowing_clips (
    clip_id            TEXT PRIMARY KEY,
    cefr_level         TEXT NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1')),
    accent             TEXT NOT NULL CHECK (accent IN ('US','UK','AU','CA')),
    script             TEXT NOT NULL,
    audio_url          TEXT,
    duration_ms        INT CHECK (duration_ms IS NULL OR duration_ms >= 0),
    practice_modes     TEXT[] NOT NULL DEFAULT ARRAY['shadowing','dictation'],
    review_status      TEXT NOT NULL DEFAULT 'draft'
        CHECK (review_status IN ('draft','auto_validated','human_approved')),
    CHECK (btrim(script) <> ''),
    CHECK (audio_url IS NULL OR btrim(audio_url) <> ''),
    CHECK (cardinality(practice_modes) > 0),
    CHECK (practice_modes <@ ARRAY['shadowing','dictation']::TEXT[])
);

CREATE TABLE IF NOT EXISTS shadowing_segments (
    clip_id            TEXT NOT NULL REFERENCES shadowing_clips(clip_id) ON DELETE CASCADE,
    "order"            INT NOT NULL CHECK ("order" >= 1),
    text               TEXT NOT NULL CHECK (btrim(text) <> ''),
    start_ms           INT CHECK (start_ms IS NULL OR start_ms >= 0),
    end_ms             INT CHECK (end_ms IS NULL OR end_ms >= 0),
    PRIMARY KEY (clip_id, "order"),
    CHECK (start_ms IS NULL OR end_ms IS NULL OR end_ms > start_ms)
);

CREATE TABLE IF NOT EXISTS shadowing_clip_concepts (
    clip_id            TEXT NOT NULL REFERENCES shadowing_clips(clip_id) ON DELETE CASCADE,
    concept_id         TEXT NOT NULL REFERENCES concepts(concept_id),
    PRIMARY KEY (clip_id, concept_id)
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
CREATE INDEX IF NOT EXISTS idx_shadowing_clips_cefr  ON shadowing_clips (cefr_level);
CREATE INDEX IF NOT EXISTS idx_shadowing_concepts    ON shadowing_clip_concepts (concept_id);

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

    later_migrations = list(DEFAULT_OUT.parent.glob("V[2-9]*__*.sql"))
    if (args.out.resolve() == DEFAULT_OUT.resolve() and DEFAULT_OUT.exists()
            and later_migrations):
        sys.exit(
            "Refusing to overwrite V1 because later Flyway migrations exist. "
            "Use --out <scratch-path> to inspect a fresh-schema DDL, and add a new "
            "versioned migration for live schema changes.")

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
    print("CHƯA chạy trên Postgres nào — cú pháp chưa được xác nhận (blocker B1).")
    print("Flyway sẽ áp file này khi app khởi động lần đầu với một DB thật.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
