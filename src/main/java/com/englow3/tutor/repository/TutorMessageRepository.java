package com.englow3.tutor.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.tutor.entity.TutorMessage;
import com.englow3.tutor.entity.TutorMessageStatus;

public interface TutorMessageRepository extends JpaRepository<TutorMessage, UUID> {
    @Query("select m from TutorMessage m where m.tutorConversationId=:conversation and m.orderNo>:after and (m.summary=false or m.correction is not null) and m.status=com.englow3.tutor.entity.TutorMessageStatus.READY order by m.orderNo")
    List<TutorMessage> memoryBatch(@Param("conversation") UUID conversation, @Param("after") int after,
            org.springframework.data.domain.Pageable page);

    @Query("select count(m) from TutorMessage m where m.tutorConversationId=:conversation and m.orderNo>:after and (m.summary=false or m.correction is not null) and m.status=com.englow3.tutor.entity.TutorMessageStatus.READY")
    long memoryRemaining(@Param("conversation") UUID conversation, @Param("after") int after);

    List<TutorMessage> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TutorMessageStatus status, Instant cutoff,
            org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from TutorMessage m where m.id = :id")
    Optional<TutorMessage> lockById(@Param("id") UUID id);

    org.springframework.data.domain.Page<TutorMessage> findByTutorConversationIdOrderByOrderNoDesc(UUID conversationId,
            org.springframework.data.domain.Pageable page);

    @Query("select m from TutorMessage m where m.tutorConversationId=:conversation and m.orderNo<:cursor and m.status=com.englow3.tutor.entity.TutorMessageStatus.READY and (m.summary=false or m.correction is not null) order by m.orderNo desc")
    List<TutorMessage> recentContext(@Param("conversation") UUID conversation, @Param("cursor") int cursor,
            org.springframework.data.domain.Pageable page);

    Optional<TutorMessage> findByTutorConversationIdAndOrderNo(UUID conversationId, int orderNo);

    boolean existsByTutorConversationIdAndStatus(UUID conversationId, TutorMessageStatus status);

    List<TutorMessage> findByTutorConversationIdOrderByOrderNo(UUID conversationId);

    Optional<TutorMessage> findByIdAndTutorConversationId(UUID id, UUID conversationId);

}
