package com.englow3.exam.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.englow3.shared.persistence.BasePersistedEntity;
import lombok.Getter;

@Entity
@Table(name = "attempt_answer_options")
@Getter
public class AttemptAnswerOption extends BasePersistedEntity {

    @Column(name = "attempt_answer_id", nullable = false, updatable = false)
    private UUID attemptAnswerId;

    @Column(name = "question_option_id", updatable = false)
    private UUID questionOptionId;

    /** Set instead of {@code questionOptionId} on a MATCHING question; the database checks exactly one is. */
    @Column(name = "question_set_option_id", updatable = false)
    private UUID questionSetOptionId;

    protected AttemptAnswerOption() {
    }

    public static AttemptAnswerOption selected(UUID attemptAnswerId, UUID questionOptionId) {
        AttemptAnswerOption option = new AttemptAnswerOption();
        option.id = UUID.randomUUID();
        option.attemptAnswerId = attemptAnswerId;
        option.questionOptionId = questionOptionId;
        return option;
    }

    public static AttemptAnswerOption selectedFromSet(UUID attemptAnswerId, UUID questionSetOptionId) {
        AttemptAnswerOption option = new AttemptAnswerOption();
        option.id = UUID.randomUUID();
        option.attemptAnswerId = attemptAnswerId;
        option.questionSetOptionId = questionSetOptionId;
        return option;
    }

    /** Whichever of the two was chosen. */
    public UUID chosenId() {
        return questionOptionId != null ? questionOptionId : questionSetOptionId;
    }
}
