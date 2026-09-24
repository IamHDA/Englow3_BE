package com.englow3.speaking.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.speaking.entity.SpeakingAttempt;

public interface SpeakingAttemptRepository extends JpaRepository<SpeakingAttempt, UUID> {

    /**
     * Every read of one attempt goes through the owner's id as well as the attempt's. A learner asking for someone
     * else's recording gets the same answer as asking for one that does not exist, which is the answer they should get.
     */
    Optional<SpeakingAttempt> findByIdAndUserId(UUID id, UUID userId);

    List<SpeakingAttempt> findByUserIdAndSpeakingPromptIdOrderByCreatedAtDesc(UUID userId, UUID speakingPromptId);

    /** Best pronunciation score per prompt, for a page of prompts, in one query. */
    @Query("""
            select a.speakingPromptId, max(a.pronunciationPercent) from SpeakingAttempt a
            where a.userId = :userId and a.speakingPromptId in :promptIds
              and a.status = com.englow3.speaking.entity.SpeakingAttemptStatus.ASSESSED
            group by a.speakingPromptId
            """)
    List<Object[]> bestScoresRaw(@Param("userId") UUID userId, @Param("promptIds") Collection<UUID> promptIds);

    default Map<UUID, BigDecimal> bestScores(UUID userId, Collection<UUID> promptIds) {
        if (promptIds.isEmpty()) {
            return Map.of();
        }
        return bestScoresRaw(userId, promptIds).stream().filter(row -> row[1] != null)
                .collect(java.util.stream.Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));
    }

}
