package com.englow3.exam.entity;

/**
 * How a paper is being sat. {@link #FULL} is the real sitting - every part on the paper's own clock - and the only mode
 * that counts toward progress, the best score and placement. {@link #PRACTICE} covers the parts the learner picked, on
 * a clock they chose or none, and is kept in history only.
 */
public enum ExamAttemptMode {
    FULL, PRACTICE
}
