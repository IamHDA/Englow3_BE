package com.englow3.learning.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.learning.dto.command.RateFlashcardCommand;
import com.englow3.learning.dto.result.*;

public interface FlashcardService {
    Page<FlashcardSetSummaryResult> searchPublishedSets(String topic, String title, Pageable pageable);

    FlashcardSetDetailResult setDetail(UUID setId);

    List<FlashcardResult> studyQueue(UUID setId, int limit);

    FlashcardReviewResult rate(RateFlashcardCommand command);
}
