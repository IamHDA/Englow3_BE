package com.englow3.speaking.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;
import com.englow3.speaking.dto.result.SpeakingPromptResult;
import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.entity.SpeakingPromptStatus;
import com.englow3.speaking.repository.SpeakingAttemptRepository;
import com.englow3.speaking.repository.SpeakingPromptRepository;
import com.englow3.speaking.service.SpeakingService;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Published speaking prompt catalogue. */
@Service
@RequiredArgsConstructor
public class SpeakingServiceImpl implements SpeakingService {

    private final SpeakingPromptRepository promptRepo;
    private final SpeakingAttemptRepository attemptRepo;
    private final UserDirectory userDirectory;

    @Transactional(readOnly = true)
    public Page<SpeakingPromptResult> searchPublished(String category, String title, Pageable pageable) {
        UUID userId = userDirectory.requireCurrentUserId();
        Page<SpeakingPrompt> page = promptRepo.searchByStatus(SpeakingPromptStatus.PUBLISHED, category, title,
                pageable);
        Map<UUID, BigDecimal> bestScores = attemptRepo.bestScores(userId,
                page.getContent().stream().map(SpeakingPrompt::getId).toList());
        return page.map(prompt -> SpeakingPromptResult.of(prompt, bestScores.get(prompt.getId())));
    }

    @Transactional(readOnly = true)
    public SpeakingPromptResult promptDetail(UUID promptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        SpeakingPrompt prompt = requirePublishedPrompt(promptId);
        return SpeakingPromptResult.of(prompt, attemptRepo.bestScores(userId, List.of(promptId)).get(promptId));
    }

    private SpeakingPrompt requirePublishedPrompt(UUID promptId) {
        return promptRepo.findById(promptId).filter(prompt -> prompt.getStatus() == SpeakingPromptStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("SPEAKING_PROMPT_NOT_FOUND",
                        "No speaking prompt with id %s".formatted(promptId)));
    }
}
