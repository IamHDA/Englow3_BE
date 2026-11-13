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

    Optional<FullTestSitting> findFirstByUserIdAndFullTestIdAndStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtDesc(
            UUID userId, UUID fullTestId, FullTestSittingStatus status, Instant since);

    /** Sittings the sweep should look at again: past their deadline, or waiting on a grade. */
    @Query("""
            select s from FullTestSitting s
             where (s.status = com.englow3.exam.entity.FullTestSittingStatus.IN_PROGRESS and s.deadlineAt < :now)
                or s.status = com.englow3.exam.entity.FullTestSittingStatus.AWAITING_GRADES
             order by s.deadlineAt
            """)
    List<FullTestSitting> findUnsettled(@Param("now") Instant now, Pageable page);
}
