package com.englow3.ai.tutor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class TutorDtos {

    private TutorDtos() {
    }

    record CreateConversationRequest(@Size(max = 200) String title) {
    }

    record SendMessageRequest(@NotBlank @Size(max = 4000) String message,
            @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record FeedbackRequest(@Min(-1) @Max(1) Short rating, ReportReason reportReason, @Size(max = 1000) String comment) {
    }

    enum ReportReason {
        INCORRECT, UNSAFE, IRRELEVANT, OTHER
    }

    record MessageResponse(UUID id, String role, String status, String content, UUID jobId, Instant createdAt) {

        static MessageResponse from(TutorMessage message) {
            return new MessageResponse(message.getId(), message.getRole().name(), message.getStatus().name(),
                    message.getContent(), message.getAiJobId(), message.getCreatedAt());
        }
    }

    record ConversationResponse(UUID id, String title, String status, Instant updatedAt,
            List<MessageResponse> messages) {

        static ConversationResponse from(TutorConversation conversation, List<TutorMessage> messages) {
            return new ConversationResponse(conversation.getId(), conversation.getTitle(),
                    conversation.getStatus().name(), conversation.getUpdatedAt(),
                    messages.stream().map(MessageResponse::from).toList());
        }
    }

    record SendMessageResponse(UUID userMessageId, UUID assistantMessageId, UUID jobId, String jobStatus) {
    }
}
