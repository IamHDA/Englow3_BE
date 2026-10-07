package com.englow3.assessment.service;

import java.util.UUID;
import org.springframework.data.domain.Page;
import com.englow3.assessment.dto.result.AssessmentNotificationResult;

public interface AssessmentNotificationService {
    Page<AssessmentNotificationResult> unread(int page);

    void markRead(UUID id, long version);
}
