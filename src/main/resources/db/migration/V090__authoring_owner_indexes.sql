-- Staff lists filter by owner before pagination; support that lookup without scanning every draft.
create index idx_quizzes_author_status on quizzes(created_by_user_id,status);
create index idx_flashcard_sets_author_status on flashcard_sets(created_by_user_id,status);
create index idx_dictation_lessons_author_status on dictation_lessons(created_by_user_id,status);
create index idx_speaking_prompts_author_status on speaking_prompts(created_by_user_id,status);
create index idx_translation_sets_author_status on translation_sets(created_by_user_id,status);
create index idx_full_tests_author_created on full_tests(created_by_user_id,created_at desc);
