create unique index uq_exam_attempts_active_user_exam
    on exam_attempts (user_id, exam_id)
    where status = 'IN_PROGRESS';
