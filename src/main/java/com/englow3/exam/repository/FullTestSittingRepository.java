package com.englow3.exam.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.FullTestSitting;
import com.englow3.exam.entity.FullTestSittingStatus;

import jakarta.persistence.LockModeType;

public interface FullTestSittingRepository extends JpaRepository<FullTestSitting, UUID> {

    /** Two grades can land at once; each settles the sitting under this lock, one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FullTestSitting s where s.id = :id")
    Optional<FullTestSitting> findByIdForUpdate(@Param("id") UUID id);

    Optional<FullTestSitting> findFirstByUserIdAndFullTestIdAndStatus(UUID userId, UUID fullTestId,
            FullTestSittingStatus status);

    List<FullTestSitting> findByUserIdAndStatusIn(UUID userId, Collection<FullTestSittingStatus> statuses);

    List<FullTestSitting> findByUserIdOrderByStartedAtDesc(UUID userId);

    List<FullTestSitting> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable page);

    Optional<FullTestSitting> findFirstByUserIdAndFullTestIdAndStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(
            UUID userId, UUID fullTestId, FullTestSittingStatus status, Instant since);

    /** Sittings the sweep should look at again: past their deadline, or waiting on a grade. */
    @Query(value = """
            select s.* from full_test_sittings s
             where s.status in ('IN_PROGRESS','AWAITING_GRADES')
                or (s.status='COMPLETED' and s.assessment_version_sum <> (select coalesce(sum(a.version),0) from assessment_attempts a
                    where a.id in (s.writing1_attempt_id,s.writing2_attempt_id,s.speaking1_attempt_id,s.speaking2_attempt_id,s.speaking3_attempt_id)))
             order by coalesce(s.last_reconciled_at,s.started_at),s.id
            """, nativeQuery = true)
    List<FullTestSitting> findUnsettled(Pageable page);

    @Query(value = """
            select s.* from full_test_sittings s where s.user_id=:user and
            (s.status in ('IN_PROGRESS','AWAITING_GRADES') or
              (s.status='COMPLETED' and s.assessment_version_sum <> (select coalesce(sum(a.version),0) from assessment_attempts a
                 where a.id in (s.writing1_attempt_id,s.writing2_attempt_id,s.speaking1_attempt_id,s.speaking2_attempt_id,s.speaking3_attempt_id))))
            """, nativeQuery = true)
    List<FullTestSitting> findRefreshableForUser(@Param("user") UUID user);
}
