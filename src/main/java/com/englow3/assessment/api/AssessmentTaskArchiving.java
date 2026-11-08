package com.englow3.assessment.api;

import java.util.UUID;

/** Synchronous veto point, published inside the archive transaction before changing the task. */
public record AssessmentTaskArchiving(UUID taskId) {
}
