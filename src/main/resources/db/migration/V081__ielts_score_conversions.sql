-- IELTS bands for Listening and Academic Reading, from the number of correct answers out of 40.
--
-- Reference data, like level_check_weights: the scoring rule of a certificate, not demo content. The rows from 10
-- correct answers up follow the conversion tables IELTS publishes as a guide; IELTS itself adjusts them slightly per
-- test version, so a band computed here is an estimate and is shown as one. Rows under 10 correct answers are not
-- published at all and are a rough extension of the table. A later effective_from replaces any of them without
-- changing results already recorded.
--
-- Listening is the same for Academic and General Training, so its rows carry no certificate_variant. The CEFR column
-- follows the alignment Cambridge publishes for IELTS (4.0-5.0 B1, 5.5-6.5 B2, 7.0-8.0 C1, 8.5-9.0 C2), extended
-- downwards as A2 and A1.

-- The original unique key treats NULLs as distinct, so two Listening rows for the same score could both be inserted.
do $$
declare
    existing text;
begin
    select conname into existing
      from pg_constraint
     where conrelid = 'score_conversions'::regclass and contype = 'u';
    if existing is not null then
        execute format('alter table score_conversions drop constraint %I', existing);
    end if;
end
$$;

alter table score_conversions
    add constraint uq_score_conversions
        unique nulls not distinct (certificate_type, certificate_variant, section_type, raw_score_from, effective_from),
    add constraint ck_score_conversions_range check (raw_score_from <= raw_score_to);

insert into score_conversions (certificate_type, certificate_variant, section_type, raw_score_from, raw_score_to,
                               converted_score, cefr_level, effective_from)
values
    ('IELTS', null, 'LISTENING', 39, 40, 9.0, 'C2', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 37, 38, 8.5, 'C2', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 35, 36, 8.0, 'C1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 32, 34, 7.5, 'C1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 30, 31, 7.0, 'C1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 26, 29, 6.5, 'B2', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 23, 25, 6.0, 'B2', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 18, 22, 5.5, 'B2', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 16, 17, 5.0, 'B1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 13, 15, 4.5, 'B1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 10, 12, 4.0, 'B1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 8, 9, 3.5, 'A2', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 6, 7, 3.0, 'A2', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 4, 5, 2.5, 'A1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 2, 3, 2.0, 'A1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 1, 1, 1.0, 'A1', '2026-01-01'),
    ('IELTS', null, 'LISTENING', 0, 0, 0.0, 'A1', '2026-01-01'),

    ('IELTS', 'ACADEMIC', 'READING', 39, 40, 9.0, 'C2', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 37, 38, 8.5, 'C2', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 35, 36, 8.0, 'C1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 33, 34, 7.5, 'C1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 30, 32, 7.0, 'C1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 27, 29, 6.5, 'B2', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 23, 26, 6.0, 'B2', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 19, 22, 5.5, 'B2', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 15, 18, 5.0, 'B1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 13, 14, 4.5, 'B1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 10, 12, 4.0, 'B1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 8, 9, 3.5, 'A2', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 6, 7, 3.0, 'A2', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 4, 5, 2.5, 'A1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 2, 3, 2.0, 'A1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 1, 1, 1.0, 'A1', '2026-01-01'),
    ('IELTS', 'ACADEMIC', 'READING', 0, 0, 0.0, 'A1', '2026-01-01');
