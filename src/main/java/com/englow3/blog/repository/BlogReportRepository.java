package com.englow3.blog.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.blog.entity.BlogReport;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;

public interface BlogReportRepository extends JpaRepository<BlogReport, UUID> {

    /**
     * Reporting the same thing twice - two tabs, a retried request - is one open report: the second insert finds the
     * partial unique index taken and does nothing. {@code type} and {@code reason} are the enum names. Returns 1 for a
     * new report and 0 when this reader already has an open one for this target.
     */
    @Modifying
    @Query(value = """
            insert into blog_reports (id, reporter_id, target_type, target_id, reason, detail, status, created_at)
            values (:id, :reporter, :type, :target, :reason, :detail, 'OPEN', :now)
            on conflict (reporter_id, target_type, target_id) where status = 'OPEN' do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("reporter") UUID reporterId, @Param("type") String type,
            @Param("target") UUID targetId, @Param("reason") String reason, @Param("detail") String detail,
            @Param("now") Instant now);

    /** The reader's open report for this target, if any. Closed ones may exist beside it. */
    Optional<BlogReport> findByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, BlogTargetType type,
            UUID targetId, BlogReportStatus status);

    List<BlogReport> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(BlogTargetType type, UUID targetId);

    Optional<BlogReport> findFirstByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(BlogTargetType type,
            UUID targetId, BlogReportStatus status);

    /**
     * Closes every open report of one target in one statement, so that two moderators acting at once cannot each close
     * half. Flushes first, so what the transaction has already changed is written before the statement runs, and clears
     * afterwards, so that no report loaded earlier stays in the persistence context showing OPEN: whatever the caller
     * needs next is read again.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update BlogReport r
               set r.status = :status, r.resolvedByUserId = :moderator, r.resolutionNote = :note, r.resolvedAt = :now
             where r.targetType = :type and r.targetId = :target
               and r.status = com.englow3.blog.entity.BlogReportStatus.OPEN
            """)
    int closeOpen(@Param("type") BlogTargetType type, @Param("target") UUID targetId,
            @Param("status") BlogReportStatus status, @Param("moderator") UUID moderatorId, @Param("note") String note,
            @Param("now") Instant now);
}
