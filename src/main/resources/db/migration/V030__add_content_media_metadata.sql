ALTER TABLE audio_assets
    ADD COLUMN IF NOT EXISTS cues JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE audio_assets
    ADD CONSTRAINT ck_audio_assets_cues_array
        CHECK (jsonb_typeof(cues) = 'array');

ALTER TABLE speaking_tasks
    ADD COLUMN IF NOT EXISTS image_url TEXT,
    ADD COLUMN IF NOT EXISTS stimulus_text TEXT,
    ADD COLUMN IF NOT EXISTS prompt_audio JSONB;

ALTER TABLE speaking_tasks
    ADD CONSTRAINT ck_speaking_tasks_prompt_audio_object
        CHECK (prompt_audio IS NULL OR jsonb_typeof(prompt_audio) = 'object');

ALTER TABLE writing_tasks
    ADD COLUMN IF NOT EXISTS image_url TEXT,
    ADD COLUMN IF NOT EXISTS stimulus_text TEXT;
