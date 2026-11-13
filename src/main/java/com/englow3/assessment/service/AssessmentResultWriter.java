package com.englow3.assessment.service;

import java.util.UUID;

public interface AssessmentResultWriter {
    void complete(UUID id, int revision, String report, String transcript);

    void fail(UUID id, String code);
}
