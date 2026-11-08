package com.englow3.generation.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.generation.entity.GenerationRequest;
import com.englow3.generation.entity.GenerationStatus;

import jakarta.persistence.LockModeType;

public interface GenerationRequestRepository extends JpaRepository<GenerationRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GenerationRequest r where r.id = :id")
    Optional<GenerationRequest> findByIdForUpdate(@Param("id") UUID id);

    /** Held for the surrounding transaction, so two presses at once cannot both pass the limit. */
    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:key,0)) as text)", nativeQuery = true)
    String lockUser(@Param("key") String key);

    /** Requests that count against today's limit: everything but a failed one. */
    @Query("""
            select count(r) from GenerationRequest r
             where r.requestedByUserId = :userId and r.createdAt >= :from
               and r.status <> com.englow3.generation.entity.GenerationStatus.FAILED
            """)
    long countCountedSince(@Param("userId") UUID userId, @Param("from") Instant from);

    boolean existsByRequestedByUserIdAndStatusIn(UUID userId, List<GenerationStatus> statuses);

    Page<GenerationRequest> findAllByOrderByCreatedAtDesc(Pageable page);

    /** The plans of every set that got past planning, so a new plan avoids their topics. */
    @Query(value = """
            select cast(components -> 'plan' as text) from generation_requests
             where components -> 'plan' is not null and status <> 'FAILED'
             order by created_at desc limit 500
            """, nativeQuery = true)
    List<String> recentPlans();
}
