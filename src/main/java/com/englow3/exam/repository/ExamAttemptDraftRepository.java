package com.englow3.exam.repository;

import java.util.UUID;
import com.englow3.exam.entity.ExamAttemptDraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamAttemptDraftRepository extends JpaRepository<ExamAttemptDraft, UUID> {
}
