package com.englow3.exam.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.ScoreConversion;

public interface ScoreConversionRepository extends JpaRepository<ScoreConversion, Integer> {

    /** Every row of one certificate's tables, all variants and dates: a few dozen rows, filtered in memory. */
    List<ScoreConversion> findByCertificateType(CertificateType certificateType);
}
