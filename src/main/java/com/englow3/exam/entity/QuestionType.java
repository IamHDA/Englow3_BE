package com.englow3.exam.entity;

/**
 * Only the shapes that {@code question_options} can express. Matching, gap-fill and free text need
 * {@code question_matching_answers}, {@code question_accepted_answers} and {@code question_set_options}, none of which
 * is mapped while the catalogue is TOEIC L&R - adding their values here first would be enum entries nothing can read.
 */
public enum QuestionType {
    SINGLE_CHOICE, MULTIPLE_CHOICE
}
