package com.englow3.exam.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.result.LearnerExamListItemResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;
import com.englow3.exam.query.ExamOutlineQuery;

public interface LearnerExamService {
    Page<LearnerExamListItemResult> search(ExamType examType, CertificateType certificateType,
            CertificateVariant certificateVariant, TargetLevel targetLevel, String title, Pageable pageable);

    LearnerExamListItemResult detail(UUID examId);

    List<ExamOutlineQuery.OutlinePart> outline(UUID examId);

    LearnerExamListItemResult placementExam();
}
