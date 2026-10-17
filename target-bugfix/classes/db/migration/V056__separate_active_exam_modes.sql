drop index uq_exam_attempts_active_user_exam;

create unique index uq_exam_attempts_active_user_exam_mode
    on exam_attempts (user_id, exam_id, mode)
    where status = 'IN_PROGRESS';
