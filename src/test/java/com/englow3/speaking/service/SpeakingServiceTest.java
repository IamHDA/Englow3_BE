package com.englow3.speaking.service;

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
import com.englow3.user.api.UserDirectory;

class SpeakingServiceTest {

    private final SpeakingPromptRepository promptRepo = mock(SpeakingPromptRepository.class);
    private final SpeakingAttemptRepository attemptRepo = mock(SpeakingAttemptRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final SpeakingService service = new com.englow3.speaking.service.impl.SpeakingServiceImpl(promptRepo,
            attemptRepo, userDirectory);

    @Test
    void returnsPublishedPromptDetailWithTheLearnersBestScore() {
        UUID userId = UUID.randomUUID();
        SpeakingPrompt prompt = SpeakingPrompt.draft("seat-sit", "Seat vs sit", "Minimal Pairs", "A2",
                "Please sit on this seat.", null, null, "/iː/ vs /ɪ/", "[]", UUID.randomUUID());
        prompt.publish(Instant.now());
        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(promptRepo.findById(prompt.getId())).thenReturn(Optional.of(prompt));
        when(attemptRepo.bestScores(userId, List.of(prompt.getId())))
                .thenReturn(Map.of(prompt.getId(), java.math.BigDecimal.valueOf(92)));

        assertThat(service.promptDetail(prompt.getId()).bestScorePercent()).isEqualByComparingTo("92");
    }
}
