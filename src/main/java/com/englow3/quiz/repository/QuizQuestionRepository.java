package com.englow3.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.quiz.entity.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByQuizIdOrderByOrderNo(UUID quizId);

    long countByQuizId(UUID quizId);

    /** Zero for a quiz with no questions - coalesce because sum over nothing is null, not 0. */
    @Query("select coalesce(sum(q.points), 0) from QuizQuestion q where q.quizId = :quizId")
    long sumPoints(@Param("quizId") UUID quizId);

    /**
     * Counts for a page of parents in one query. One count per row would make a page of twenty into twenty-one round
     * trips to fill a single column.
     */
    @Query("""
            select c.quizId, count(c) from QuizQuestion c
            where c.quizId in :quizIds
            group by c.quizId
            """)
    List<Object[]> countByQuizIdsRaw(@Param("quizIds") Collection<UUID> quizIds);

    default Map<UUID, Long> countByQuizIds(Collection<UUID> quizIds) {
        if (quizIds.isEmpty()) {
            return Map.of();
        }
        return countByQuizIdsRaw(quizIds).stream()
                .collect(java.util.stream.Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }
}
