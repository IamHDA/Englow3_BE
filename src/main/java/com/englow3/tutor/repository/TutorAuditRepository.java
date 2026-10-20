package com.englow3.tutor.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.englow3.tutor.entity.TutorAudit;

public interface TutorAuditRepository extends JpaRepository<TutorAudit, UUID> {
}
