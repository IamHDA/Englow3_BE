package com.englow3.learning.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByQuizIdOrderByOrderNo(UUID quizId);

    long countByQuizId(UUID quizId);

    /** Zero for a quiz with no questions - coalesce because sum over nothing is null, not 0. */
    @Query("select coalesce(sum(q.points), 0) from QuizQuestion q where q.quizId = :quizId")
    long sumPoints(@Param("quizId") UUID quizId);
}
