-- Phase 8C/10 content that was previously present in the data schema but had
-- no relational storage in V1.

CREATE TABLE IF NOT EXISTS assessment_prompts (
    prompt_id          TEXT PRIMARY KEY,
    target             TEXT NOT NULL CHECK (target IN ('speaking', 'writing')),
    rubric_ref         TEXT NOT NULL REFERENCES rubrics(rubric_id),
    system_prompt      TEXT NOT NULL,
    output_schema_ref  TEXT NOT NULL DEFAULT 'AssessmentResult',
    version            TEXT NOT NULL,
    review_status      TEXT NOT NULL DEFAULT 'draft',
    CHECK (btrim(system_prompt) <> ''),
    CHECK (version ~ '^\d+\.\d+\.\d+$'),
    CHECK (review_status IN ('draft', 'auto_validated', 'human_approved'))
);

CREATE TABLE IF NOT EXISTS assessment_calibration_cases (
    case_id                TEXT PRIMARY KEY,
    target                 TEXT NOT NULL CHECK (target IN ('speaking', 'writing')),
    prompt_id              TEXT NOT NULL REFERENCES assessment_prompts(prompt_id),
    task                   TEXT NOT NULL,
    learner_response       TEXT NOT NULL,
    delivery_observations  JSONB,
    expected_result        JSONB NOT NULL,
    CHECK (btrim(task) <> ''),
    CHECK (btrim(learner_response) <> ''),
    CHECK (jsonb_typeof(expected_result) = 'object')
);

CREATE TABLE IF NOT EXISTS shadowing_clips (
    clip_id            TEXT PRIMARY KEY,
    cefr_level         TEXT NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1')),
    accent             TEXT NOT NULL CHECK (accent IN ('US','UK','AU','CA')),
    script             TEXT NOT NULL,
    audio_url          TEXT,
    duration_ms        INT CHECK (duration_ms IS NULL OR duration_ms >= 0),
    practice_modes     TEXT[] NOT NULL DEFAULT ARRAY['shadowing','dictation'],
    review_status      TEXT NOT NULL DEFAULT 'draft',
    CHECK (btrim(script) <> ''),
    CHECK (audio_url IS NULL OR btrim(audio_url) <> ''),
    CHECK (cardinality(practice_modes) > 0),
    CHECK (practice_modes <@ ARRAY['shadowing','dictation']::TEXT[]),
    CHECK (review_status IN ('draft', 'auto_validated', 'human_approved'))
);

CREATE TABLE IF NOT EXISTS shadowing_segments (
    clip_id            TEXT NOT NULL REFERENCES shadowing_clips(clip_id) ON DELETE CASCADE,
    "order"            INT NOT NULL CHECK ("order" >= 1),
    text               TEXT NOT NULL,
    start_ms           INT CHECK (start_ms IS NULL OR start_ms >= 0),
    end_ms             INT CHECK (end_ms IS NULL OR end_ms >= 0),
    PRIMARY KEY (clip_id, "order"),
    CHECK (btrim(text) <> ''),
    CHECK (start_ms IS NULL OR end_ms IS NULL OR end_ms > start_ms)
);

CREATE TABLE IF NOT EXISTS shadowing_clip_concepts (
    clip_id            TEXT NOT NULL REFERENCES shadowing_clips(clip_id) ON DELETE CASCADE,
    concept_id         TEXT NOT NULL REFERENCES concepts(concept_id),
    PRIMARY KEY (clip_id, concept_id)
);

CREATE INDEX IF NOT EXISTS idx_shadowing_clips_cefr ON shadowing_clips (cefr_level);
CREATE INDEX IF NOT EXISTS idx_shadowing_concepts_concept ON shadowing_clip_concepts (concept_id);
