package com.englow3.translation.service;

import java.util.UUID;

/**
 * The short writes at the end of grading. A bean of its own so the handler, which calls the provider outside any
 * transaction, crosses a proxy to reach a transactional method.
 */
public interface TranslationGradeWriter {

    /**
     * @param report
     *            the validated report from {@code TranslationRubric}, holding the total score
     */
    void storeGrade(UUID attemptId, String report);

    void markFailed(UUID attemptId, String errorCode);
}
