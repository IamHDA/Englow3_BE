package com.englow3.exam.entity;

/**
 * Which part of the paper a section is. Deliberately not shared with {@link SkillType}, even though the values match
 * today: this one names a division of the paper, that one names what a question trains, and the recommendation path may
 * well grow finer values (grammar, vocabulary) that are no section of anything.
 */
public enum SectionType {
    LISTENING, READING, WRITING, SPEAKING
}
