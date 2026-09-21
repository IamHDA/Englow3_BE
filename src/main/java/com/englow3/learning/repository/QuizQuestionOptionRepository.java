package com.englow3.learning.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.learning.entity.QuizQuestionOption;

public interface QuizQuestionOptionRepository extends JpaRepository<QuizQuestionOption, UUID> {

    /** Loaded for a whole paper at once - one query for every question rather than one each. */
    List<QuizQuestionOption> findByQuizQuestionIdInOrderByOrderNo(Collection<UUID> quizQuestionIds);
}
