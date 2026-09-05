package com.englow3.ai.foundation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    Optional<AiJob> findByRequesterUserIdAndIdempotencyKey(UUID requesterUserId, String idempotencyKey);

    Optional<AiJob> findByIdAndRequesterUserId(UUID id, UUID requesterUserId);

    @Query(value = """
            select * from ai_jobs
            where status in ('PENDING', 'RETRY_SCHEDULED') and available_at <= :now
            order by available_at, created_at
            for update skip locked
            limit 1
            """, nativeQuery = true)
    Optional<AiJob> findNextReady(Instant now);

    @Query(value = """
            select * from ai_jobs
            where status = 'PROCESSING' and locked_at < :staleBefore
            order by locked_at for update skip locked limit 100
            """, nativeQuery = true)
    List<AiJob> findStaleProcessing(Instant staleBefore);

    @Modifying
    @Query(value = """
            update ai_jobs
            set status = case when retry_count < max_retry_count then 'RETRY_SCHEDULED' else 'FAILED' end,
                available_at = :now, next_retry_at = :now,
                locked_at = null, locked_by = null, error_code = 'AI_WORKER_LOCK_EXPIRED',
                error_message = 'The previous worker stopped before completing this job',
                completed_at = case when retry_count < max_retry_count then null else :now end,
                retry_count = retry_count + 1, version = version + 1
            where status = 'PROCESSING' and locked_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(Instant staleBefore, Instant now);
}
