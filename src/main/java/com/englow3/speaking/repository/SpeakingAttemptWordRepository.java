package com.englow3.speaking.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.speaking.entity.SpeakingAttemptWord;

public interface SpeakingAttemptWordRepository extends JpaRepository<SpeakingAttemptWord, UUID> {

    List<SpeakingAttemptWord> findBySpeakingAttemptIdOrderByOrderNo(UUID speakingAttemptId);

    /**
     * A reassessment replaces the breakdown rather than appending a second copy of it.
     * <p>
     * A bulk statement, executed when called, and that is the point. The derived {@code deleteBy...} this replaced
     * loaded each row and queued a remove - and Hibernate flushes queued inserts before queued deletes, so the new rows
     * hit the unique index on {@code (speaking_attempt_id, order_no)} while the old ones were still there. Every rerun
     * failed on exactly the case the delete existed for: a stalled job taken back and run again.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SpeakingAttemptWord w where w.speakingAttemptId = :attemptId")
    void deleteBySpeakingAttemptId(@Param("attemptId") UUID speakingAttemptId);
}
