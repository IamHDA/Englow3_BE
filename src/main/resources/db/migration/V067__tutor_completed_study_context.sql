ALTER TABLE tutor_conversations
    ADD COLUMN assessment_attempt_id uuid,
    ADD COLUMN exam_attempt_id uuid,
    ADD COLUMN exam_question_id uuid,
    ADD CONSTRAINT tutor_exam_context_pair CHECK ((exam_attempt_id IS NULL) = (exam_question_id IS NULL)),
    ADD CONSTRAINT tutor_single_study_context CHECK (
        (CASE WHEN quiz_attempt_id IS NOT NULL THEN 1 ELSE 0 END)
      + (CASE WHEN assessment_attempt_id IS NOT NULL THEN 1 ELSE 0 END)
      + (CASE WHEN exam_attempt_id IS NOT NULL THEN 1 ELSE 0 END) <= 1
    );
