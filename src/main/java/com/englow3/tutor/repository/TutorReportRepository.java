package com.englow3.tutor.repository;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.englow3.tutor.entity.TutorReport;

public interface TutorReportRepository extends JpaRepository<TutorReport, UUID> {
    Optional<TutorReport> findByMessageIdAndRevisionAndReporterId(UUID messageId, int revision, UUID reporterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TutorReport r where r.id = :id")
    Optional<TutorReport> lockById(@Param("id") UUID id);

    @Query("select r from TutorReport r where (:admin = true or r.assignedTo = :user) and (:status = '' or r.status = :status) order by r.createdAt desc, r.id")
    Page<TutorReport> search(@Param("user") UUID user, @Param("admin") boolean admin, @Param("status") String status,
            Pageable page);
}
