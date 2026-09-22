package com.englow3.tutor.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.tutor.entity.TutorMessage;

public interface TutorMessageRepository extends JpaRepository<TutorMessage, UUID> {

    List<TutorMessage> findByTutorConversationIdOrderByOrderNo(UUID conversationId);

    Optional<TutorMessage> findByIdAndTutorConversationId(UUID id, UUID conversationId);

    /**
     * How many questions this learner has asked today.
     * <p>
     * Counts the learner's own turns, not the tutor's: one question produces two rows, and counting both would halve
     * the limit without anyone having changed it. Joined through the conversation because a message has no owner of its
     * own - its owner is whoever owns the thread it is in.
     */
    @Query("""
            select count(m) from TutorMessage m
            where m.role = com.englow3.tutor.entity.TutorMessageRole.USER
              and m.createdAt >= :from
              and m.tutorConversationId in (
                  select c.id from TutorConversation c where c.userId = :userId
              )
            """)
    long countAskedSince(@Param("userId") UUID userId, @Param("from") Instant from);
}
