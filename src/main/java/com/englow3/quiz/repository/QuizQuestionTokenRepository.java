package com.englow3.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.quiz.entity.QuizQuestionToken;

public interface QuizQuestionTokenRepository extends JpaRepository<QuizQuestionToken, UUID> {

    /** Loaded for a whole paper at once - one query for every question rather than one each. */
    List<QuizQuestionToken> findByQuizQuestionIdInOrderByOrderNo(Collection<UUID> quizQuestionIds);
}
