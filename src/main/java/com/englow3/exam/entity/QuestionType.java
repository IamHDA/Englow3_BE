package com.englow3.exam.entity;

/**
 * The answer shapes the sitting engine can grade.
 * <ul>
 * <li>{@code SINGLE_CHOICE}: exactly one correct option, right or wrong.</li>
 * <li>{@code MULTIPLE_CHOICE}: "choose N letters" - N correct options, each worth an equal share of the question, in no
 * order, and never more than N chosen.</li>
 * <li>{@code TEXT_ENTRY}: the learner types the answer, graded against {@code question_accepted_answers} (gap-fill,
 * completion, short answer).</li>
 * <li>{@code MATCHING}: the learner picks one entry from the list the whole question set shares
 * ({@code question_set_options}) - matching headings, information, features, sentence endings, map labels. The key is
 * in {@code question_matching_answers}.</li>
 * </ul>
 */
public enum QuestionType {
    SINGLE_CHOICE, MULTIPLE_CHOICE, TEXT_ENTRY, MATCHING
}
