package com.englow3.speaking.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.speaking.entity.SpeakingAttemptWord;

public interface SpeakingAttemptWordRepository extends JpaRepository<SpeakingAttemptWord, UUID> {

    List<SpeakingAttemptWord> findBySpeakingAttemptIdOrderByOrderNo(UUID speakingAttemptId);

    /** A reassessment replaces the breakdown rather than appending a second copy of it. */
    void deleteBySpeakingAttemptId(UUID speakingAttemptId);
}
