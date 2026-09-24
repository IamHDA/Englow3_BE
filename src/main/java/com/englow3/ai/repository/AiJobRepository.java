package com.englow3.ai.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.ai.entity.AiJob;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    Optional<AiJob> findByIdempotencyKey(String idempotencyKey);

    /** What the module that asked for the work reads to find out how it went. Newest first. */
    List<AiJob> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, UUID targetId);

    /**
     * The next jobs to run, locked so no other instance takes them.
     * <p>
     * {@code for update skip locked} is the whole point and is why this is native SQL: two workers polling the same
     * table would otherwise both read the same rows and both call the provider, and JPQL cannot express the skip. With
     * it, the second worker steps over the locked rows and picks up different ones, which is also how the queue scales
     * to more than one instance.
     * <p>
     * Must run inside a transaction - the lock is released at commit, by which time the rows are RUNNING and no longer
     * match the filter.
     */
    @Query(value = """
            select * from ai_jobs
             where status = 'PENDING'
               and (next_retry_at is null or next_retry_at <= :now)
             order by created_at
             limit :limit
             for update skip locked
            """, nativeQuery = true)
    List<AiJob> lockNextPending(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * Jobs claimed before {@code threshold} and never finished - a worker that died mid-flight. Left alone they would
     * sit RUNNING forever, so something has to notice.
     */
    @Query("""
            select j from AiJob j
             where j.status = com.englow3.ai.entity.AiJobStatus.RUNNING
               and j.startedAt <= :threshold
            """)
    List<AiJob> findStalled(@Param("threshold") Instant threshold);

    /**
     * Jobs a learner has caused since {@code from}, across every feature. Retries are not counted - a retry is the same
     * job again, not a new request - and neither is a second submit of the same work, which idempotency collapses into
     * the first job.
     */
    @Query("select count(j) from AiJob j where j.requestedByUserId = :userId and j.createdAt >= :from")
    long countRequestedSince(@Param("userId") UUID userId, @Param("from") Instant from);
}
