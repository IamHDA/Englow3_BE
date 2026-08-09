alter table learner_profiles
    add column placement_attempt_id uuid unique references exam_attempts (id) on delete restrict;
