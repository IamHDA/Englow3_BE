package com.englow3.exam.entity;

/**
 * What a question trains. Grouping attempt answers by this is what the {@code user} module turns into target-skill
 * recommendations - it is translated at that boundary, never shared as one enum across the two modules.
 */
public enum SkillType {
    LISTENING, READING, WRITING, SPEAKING
}
