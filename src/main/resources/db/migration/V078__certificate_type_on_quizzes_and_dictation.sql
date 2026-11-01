-- Quizzes and dictation lessons can be marked as IELTS or TOEIC preparation, as writing/speaking tasks already can.
-- Empty means general practice, which suits every learner. The learning roadmap offers a certificate learner general
-- content plus content for their own certificate, never the other certificate's.

alter table quizzes
    add column certificate_type varchar(10) check (certificate_type in ('IELTS', 'TOEIC'));

alter table dictation_lessons
    add column certificate_type varchar(10) check (certificate_type in ('IELTS', 'TOEIC'));
