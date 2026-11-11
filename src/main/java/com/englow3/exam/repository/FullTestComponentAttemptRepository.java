package com.englow3.exam.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.englow3.exam.entity.FullTestComponentAttempt;

public interface FullTestComponentAttemptRepository extends JpaRepository<FullTestComponentAttempt, UUID> {
    List<FullTestComponentAttempt> findBySittingId(UUID sittingId);

    Optional<FullTestComponentAttempt> findBySittingIdAndSlot(UUID sittingId, String slot);
}
