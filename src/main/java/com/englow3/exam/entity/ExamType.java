package com.englow3.exam.entity;

public enum ExamType {
    PLACEMENT, MOCK,
    /**
     * The vocabulary, grammar and listening part of the learning roadmap's level test for learners who are not
     * preparing for a certificate. Taken in full only, kept out of the mock-exam library, and never recorded as a
     * placement: the roadmap combines it with a spoken answer before deciding a level.
     */
    LEVEL_CHECK
}
