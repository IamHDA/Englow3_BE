package com.englow3.exam.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.englow3.exam.entity.FullTestBandRevision;

public interface FullTestBandRevisionRepository extends JpaRepository<FullTestBandRevision, UUID> {
}
