package com.englow3.exam.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.ScoreConversion;

public interface ScoreConversionRepository extends JpaRepository<ScoreConversion, Integer> {
    @org.springframework.data.jpa.repository.Query(value = "select cast(pg_advisory_xact_lock(8723149203) as text)", nativeQuery = true)
    String lockConfiguration();

    boolean existsByCertificateTypeAndEffectiveFrom(CertificateType certificateType, java.time.LocalDate effectiveFrom);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            insert into score_conversions(certificate_type, section_type, raw_score_from, raw_score_to, converted_score, effective_from, source_reference, created_by_user_id)
            select 'TOEIC', r."sectionType", r."rawScoreFrom", r."rawScoreTo", r."convertedScore", :day, :source, :actor
            from jsonb_to_recordset(cast(:rows as jsonb)) as r("sectionType" text, "rawScoreFrom" integer, "rawScoreTo" integer, "convertedScore" numeric)
            """, nativeQuery = true)
    int insertToeicTable(@org.springframework.data.repository.query.Param("rows") String rows,
            @org.springframework.data.repository.query.Param("day") java.time.LocalDate day,
            @org.springframework.data.repository.query.Param("source") String source,
            @org.springframework.data.repository.query.Param("actor") java.util.UUID actor);

    /** Every row of one certificate's tables, all variants and dates: a few dozen rows, filtered in memory. */
    List<ScoreConversion> findByCertificateType(CertificateType certificateType);
}
