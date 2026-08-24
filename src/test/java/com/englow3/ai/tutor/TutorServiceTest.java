package com.englow3.ai.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class TutorServiceTest {

    private TutorConversationRepository conversationRepository;
    private UserRepository userRepository;
    private CurrentUser currentUser;
    private TutorService service;
    private User user;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(TutorConversationRepository.class);
        userRepository = mock(UserRepository.class);
        currentUser = mock(CurrentUser.class);
        user = mock(User.class);
        service = new TutorService(conversationRepository, mock(TutorMessageRepository.class),
                mock(TutorFeedbackRepository.class), mock(TutorGroundingService.class), mock(AiPromptService.class),
                mock(AiJobService.class), userRepository, mock(LearnerProfileRepository.class), currentUser,
                new ObjectMapper());
    }

    @Test
    void createsConversationForAuthenticatedInternalUser() {
        UUID authId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUser.authProviderId()).thenReturn(authId);
        when(userRepository.findByAuthProviderId(authId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(conversationRepository.save(any(TutorConversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TutorDtos.ConversationResponse response = service.create("Speaking practice");

        assertThat(response.title()).isEqualTo("Speaking practice");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(conversationRepository).save(any(TutorConversation.class));
    }

    @Test
    void listsOnlyCurrentUsersConversations() {
        UUID authId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUser.authProviderId()).thenReturn(authId);
        when(userRepository.findByAuthProviderId(authId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of());

        assertThat(service.list()).isEmpty();

        verify(conversationRepository).findByUserIdOrderByUpdatedAtDesc(userId);
    }
}
