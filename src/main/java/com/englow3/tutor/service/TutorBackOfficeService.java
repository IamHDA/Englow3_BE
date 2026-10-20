package com.englow3.tutor.service;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.englow3.tutor.dto.command.*;
import com.englow3.tutor.dto.result.*;

public interface TutorBackOfficeService {
    Page<TutorResourceResult> resources(String status, Pageable page);

    TutorResourceResult resource(UUID id);

    TutorResourceResult create(TutorResourceCommand command);

    TutorResourceResult update(UUID id, TutorResourceCommand command);

    TutorResourceResult resourceAction(UUID id, TutorReviewCommand command);

    Page<TutorReportResult> reports(String status, Pageable page);

    TutorReportResult report(UUID id);

    TutorReportResult reportAction(UUID id, TutorReviewCommand command);
}
