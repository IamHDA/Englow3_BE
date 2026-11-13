package com.englow3.speaking.service;

import com.englow3.speaking.entity.SpeakingPromptCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.repository.SpeakingAttemptRepository;
import com.englow3.speaking.repository.SpeakingPromptRepository;
import com.englow3.speaking.service.impl.SpeakingServiceImpl;
import com.englow3.user.api.UserDirectory;

class SpeakingServiceTest {

    private final SpeakingPromptRepository promptRepo = mock(SpeakingPromptRepository.class);
    private final SpeakingAttemptRepository attemptRepo = mock(SpeakingAttemptRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final SpeakingService service = new SpeakingServiceImpl(promptRepo, attemptRepo, userDirectory);

    @Test
    void returnsPublishedPromptDetailWithTheLearnersBestScore() {
        UUID userId = UUID.randomUUID();
        SpeakingPrompt prompt = SpeakingPrompt.draft("seat-sit", "Seat vs sit", SpeakingPromptCategory.SOUNDS, "A2",
                "Please sit on this seat.", null, null, "/iː/ vs /ɪ/", "[]", UUID.randomUUID());
        prompt.publish(Instant.now());
        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(promptRepo.findById(prompt.getId())).thenReturn(Optional.of(prompt));
        when(attemptRepo.bestScores(userId, List.of(prompt.getId())))
                .thenReturn(Map.of(prompt.getId(), java.math.BigDecimal.valueOf(92)));

        assertThat(service.promptDetail(prompt.getId()).bestScorePercent()).isEqualByComparingTo("92");
    }

    @Test
    void findsNothingForACategoryThatDoesNotExist() {
        when(userDirectory.requireCurrentUserId()).thenReturn(UUID.randomUUID());

        var page = service.searchPublished("Minimal Pairs", null,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(promptRepo);
    }

    @Test
    void filtersByTheCategoryItIsGiven() {
        when(userDirectory.requireCurrentUserId()).thenReturn(UUID.randomUUID());
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(promptRepo.searchByStatus(com.englow3.speaking.entity.SpeakingPromptStatus.PUBLISHED,
                SpeakingPromptCategory.WORD_STRESS, null, pageable))
                        .thenReturn(org.springframework.data.domain.Page.empty(pageable));

        service.searchPublished("WORD_STRESS", null, pageable);

        org.mockito.Mockito.verify(promptRepo).searchByStatus(
                com.englow3.speaking.entity.SpeakingPromptStatus.PUBLISHED, SpeakingPromptCategory.WORD_STRESS, null,
                pageable);
    }
}
