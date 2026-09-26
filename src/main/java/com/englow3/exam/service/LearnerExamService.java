package com.englow3.exam.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.result.LearnerExamListItemResult;
import com.englow3.exam.entity.*;

public interface LearnerExamService {
    Page<LearnerExamListItemResult> search(ExamType examType, CertificateType certificateType,
            CertificateVariant certificateVariant, TargetLevel targetLevel, String title, Pageable pageable);

    LearnerExamListItemResult detail(UUID examId);

    LearnerExamListItemResult placementExam();
}
