package com.englow3.learning.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "quizzes")
@Getter
public class Quiz {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "target_level")
    private String targetLevel;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    @Column(name = "passing_score_percent", nullable = false)
    private short passingScorePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected Quiz() {
    }

    public static Quiz draft(String slug, String title, String description, String category, String targetLevel,
            int timeLimitSeconds, short passingScorePercent, UUID createdByUserId) {
        Quiz quiz = new Quiz();
        quiz.id = UUID.randomUUID();
        quiz.slug = slug;
        quiz.title = title;
        quiz.description = description == null ? "" : description;
        quiz.category = category;
        quiz.targetLevel = targetLevel;
        quiz.timeLimitSeconds = timeLimitSeconds;
        quiz.passingScorePercent = passingScorePercent;
        quiz.status = QuizStatus.DRAFT;
        quiz.createdByUserId = createdByUserId;
        return quiz;
    }

    /**
     * A quiz with no question opens onto nothing, and one with no points cannot be scored - the percentage would divide
     * by zero. Both are refused here rather than discovered by the first learner to try.
     */
    public void publish(long questionCount, long totalPoints, Instant now) {
        if (status != QuizStatus.DRAFT) {
            throw new ConflictException("QUIZ_NOT_DRAFT",
                    "Only a draft quiz can be published; this one is %s".formatted(status));
        }
        if (questionCount == 0) {
            throw new ConflictException("QUIZ_EMPTY", "A quiz with no questions cannot be published");
        }
        if (totalPoints == 0) {
            throw new ConflictException("QUIZ_ZERO_POINTS",
                    "A quiz whose questions are all worth zero cannot be scored");
        }
        this.status = QuizStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void archive() {
        if (status == QuizStatus.ARCHIVED) {
            throw new ConflictException("QUIZ_ALREADY_ARCHIVED", "This quiz is already archived");
        }
        this.status = QuizStatus.ARCHIVED;
    }
}
