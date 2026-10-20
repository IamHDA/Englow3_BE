package com.englow3.ai.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobStatus;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
    interface QueueStats {
        long getSamples();

        Double getQueueP95Ms();

        Double getCompletionP95Ms();

        long getKnownInputTokens();

        long getKnownOutputTokens();

        long getUnknownUsageJobs();
    }

    interface FailureStats {
        String getCode();

        long getCount();
    }

    @Query(value = """
            select count(*) as "samples",
              percentile_cont(0.95) within group(order by greatest(0,extract(epoch from (started_at-created_at))*1000))
                filter(where started_at is not null) as "queueP95Ms",
              percentile_cont(0.95) within group(order by greatest(0,extract(epoch from (completed_at-created_at))*1000))
                filter(where status='SUCCEEDED' and completed_at is not null) as "completionP95Ms",
              coalesce(sum((output_payload->>'input_tokens')::bigint)
                filter(where job_type='TUTOR_REPLY' and status='SUCCEEDED' and jsonb_typeof(output_payload->'input_tokens')='number'),0) as "knownInputTokens",
              coalesce(sum((output_payload->>'output_tokens')::bigint)
                filter(where job_type='TUTOR_REPLY' and status='SUCCEEDED' and jsonb_typeof(output_payload->'output_tokens')='number'),0) as "knownOutputTokens",
              count(*) filter(where job_type='TUTOR_REPLY' and status='SUCCEEDED' and
                (coalesce(jsonb_typeof(output_payload->'input_tokens'),'null')<>'number' or coalesce(jsonb_typeof(output_payload->'output_tokens'),'null')<>'number')) as "unknownUsageJobs"
            from ai_jobs where queue_scope=:scope and created_at>=:since
            """, nativeQuery = true)
    QueueStats queueStats(@Param("scope") String scope, @Param("since") Instant since);

    @Query(value = """
            select coalesce(error_code,'UNKNOWN') as code,count(*) as count from ai_jobs
            where queue_scope=:scope and status='FAILED' and completed_at>=:since
              and error_code is distinct from 'AI_CONTENT_REMOVED'
            group by error_code order by count(*) desc limit 10
            """, nativeQuery = true)
    List<FailureStats> terminalFailures(@Param("scope") String scope, @Param("since") Instant since);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AiJob j where j.requestedByUserId = :user and j.targetType in ('TUTOR_MESSAGE', 'TUTOR_RECORDING') order by j.id")
    List<AiJob> lockTutorDataForUser(@Param("user") UUID user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AiJob j where j.targetType = :type and j.targetId = :target and j.requestedByUserId = :user")
    List<AiJob> lockOwnedTarget(@Param("type") String type, @Param("target") UUID target, @Param("user") UUID user);

    long countByQueueScopeAndStatus(String queueScope, AiJobStatus status);

    long countByQueueScopeAndStatusAndCompletedAtGreaterThanEqual(String queueScope, AiJobStatus status, Instant since);

    Optional<AiJob> findFirstByQueueScopeAndStatusOrderByCreatedAtAsc(String queueScope, AiJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AiJob j where j.id = :id")
    Optional<AiJob> lockById(@Param("id") UUID id);

    @Query(value = """
            select * from ai_jobs where status = 'PENDING' and queue_scope = :scope
              and job_type in (:types)
              and (next_retry_at is null or next_retry_at <= :now)
            order by created_at, id limit :limit for update skip locked
            """, nativeQuery = true)
    List<AiJob> lockNextByTypes(@Param("now") Instant now, @Param("limit") int limit,
            @Param("types") List<String> types, @Param("scope") String scope);

    @Modifying
    @Query("update AiJob j set j.leaseUntil = :until where j.id = :id and j.status = AiJobStatus.RUNNING and j.startedAt = :claimedAt")
    int heartbeat(@Param("id") UUID id, @Param("claimedAt") Instant claimedAt, @Param("until") Instant until);

    @Query(value = """
            select * from ai_jobs where status = 'RUNNING' and queue_scope = :scope
              and (lease_until <= :now or (lease_until is null and started_at <= :threshold))
            order by started_at limit 100 for update skip locked
            """, nativeQuery = true)
    List<AiJob> lockStalled(@Param("now") Instant now, @Param("threshold") Instant threshold,
            @Param("scope") String scope);

    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:userId,0)) as text)", nativeQuery = true)
    String lockProductiveBudget(@Param("userId") String userId);

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
             where j.status = AiJobStatus.RUNNING
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
