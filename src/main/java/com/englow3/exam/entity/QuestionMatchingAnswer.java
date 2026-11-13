package com.englow3.exam.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/** The key of a MATCHING question: which entry of its set's list is the answer. Answer-key data, admin only. */
@Entity
@Table(name = "question_matching_answers")
@Getter
public class QuestionMatchingAnswer {

    @EmbeddedId
    private Key key;

    @Embeddable
    @Getter
    public static class Key implements Serializable {

        @Column(name = "question_id", nullable = false)
        private UUID questionId;

        @Column(name = "question_set_option_id", nullable = false)
        private UUID questionSetOptionId;

        protected Key() {
        }

        Key(UUID questionId, UUID questionSetOptionId) {
            this.questionId = questionId;
            this.questionSetOptionId = questionSetOptionId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key that && questionId.equals(that.questionId)
                    && questionSetOptionId.equals(that.questionSetOptionId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(questionId, questionSetOptionId);
        }
    }

    protected QuestionMatchingAnswer() {
    }

    public static QuestionMatchingAnswer of(UUID questionId, UUID questionSetOptionId) {
        QuestionMatchingAnswer answer = new QuestionMatchingAnswer();
        answer.key = new Key(questionId, questionSetOptionId);
        return answer;
    }

    public UUID getQuestionId() {
        return key.getQuestionId();
    }

    public UUID getQuestionSetOptionId() {
        return key.getQuestionSetOptionId();
    }
}
