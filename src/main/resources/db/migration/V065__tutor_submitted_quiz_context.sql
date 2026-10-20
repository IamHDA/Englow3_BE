alter table tutor_conversations add column quiz_attempt_id uuid;
alter table tutor_conversations add column quiz_question_id uuid;
alter table tutor_conversations add constraint ck_tutor_quiz_context_pair
    check ((quiz_attempt_id is null) = (quiz_question_id is null));
