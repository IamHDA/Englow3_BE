package com.englow3.dictation.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.dictation.entity.DictationAttempt;

public interface DictationAttemptRepository extends JpaRepository<DictationAttempt, UUID> {

    /**
     * The learner's best score on each sentence of one lesson. Best rather than latest: progress through a lesson
     * should not go backwards because they replayed a line and typed it carelessly.
     */
    @Query("""
            select a.dictationSentenceId, max(a.accuracyPercent) from DictationAttempt a
            where a.userId = :userId and a.dictationSentenceId in :sentenceIds
            group by a.dictationSentenceId
            """)
    List<Object[]> findBestAccuracyBySentence(@Param("userId") UUID userId,
            @Param("sentenceIds") Collection<UUID> sentenceIds);

    @Query("""
            select a.dictationLessonId, max(a.attemptedAt) from DictationAttempt a
            where a.userId = :userId and a.dictationLessonId in :lessonIds
            group by a.dictationLessonId
            """)
    List<Object[]> findLastPractisedAtByLesson(@Param("userId") UUID userId,
            @Param("lessonIds") Collection<UUID> lessonIds);

    List<DictationAttempt> findTop20ByUserIdOrderByAttemptedAtDesc(UUID userId);

    long countByUserIdAndAttemptedAtGreaterThanEqual(UUID userId, Instant from);
}
