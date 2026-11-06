package com.englow3.exam.entity;

/**
 * The answer shapes the sitting engine can grade.
 * <ul>
 * <li>{@code SINGLE_CHOICE}: exactly one correct option, right or wrong.</li>
 * <li>{@code MULTIPLE_CHOICE}: "choose N letters" - N correct options, each worth an equal share of the question, in no
 * order, and never more than N chosen.</li>
 * <li>{@code TEXT_ENTRY}: the learner types the answer, graded against {@code question_accepted_answers} (gap-fill,
 * completion, short answer).</li>
 * </ul>
 * Matching still needs {@code question_set_options} and {@code question_matching_answers}, which are not mapped yet.
 */
public enum QuestionType {
    SINGLE_CHOICE, MULTIPLE_CHOICE, TEXT_ENTRY
}
