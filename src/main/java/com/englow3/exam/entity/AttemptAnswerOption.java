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

    protected AttemptAnswerOption() {
    }

    public static AttemptAnswerOption selected(UUID attemptAnswerId, UUID questionOptionId) {
        AttemptAnswerOption option = new AttemptAnswerOption();
        option.id = UUID.randomUUID();
        option.attemptAnswerId = attemptAnswerId;
        option.questionOptionId = questionOptionId;
        return option;
    }
}
