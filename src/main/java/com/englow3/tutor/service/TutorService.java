package com.englow3.tutor.service;

import java.util.List;
import java.util.UUID;

import com.englow3.tutor.dto.command.ReportTutorMessageCommand;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.tutor.dto.result.TutorConversationResult;
import com.englow3.tutor.dto.result.TutorConversationSummaryResult;
import com.englow3.tutor.dto.result.TutorMessageResult;

public interface TutorService {
    com.englow3.tutor.dto.result.TutorMemoryResult memory(UUID conversationId);

    TutorConversationResult summarize(UUID conversationId, UUID clientMessageId);

    void clearMemory(UUID conversationId);

    com.englow3.tutor.dto.result.TutorReplyHistoryResult replyHistory(UUID conversationId, UUID messageId);

    TutorConversationResult selectReply(UUID conversationId, UUID messageId, int revision, UUID clientMessageId);

    TutorConversationResult branch(UUID conversationId, UUID questionId, String message, UUID clientMessageId,
            Boolean personalized);

    com.englow3.tutor.dto.result.TutorUsageResult usage();

    TutorConversationResult receipt(UUID clientMessageId);

    TutorConversationResult restart(UUID conversationId, UUID messageId, UUID clientMessageId, boolean regenerate);

    TutorMessageResult cancel(UUID conversationId, UUID messageId);

    TutorMessageResult reply(UUID conversationId, UUID messageId);

    com.englow3.tutor.dto.result.TutorMessagePageResult messages(UUID id,
            org.springframework.data.domain.Pageable page);

    org.springframework.data.domain.Page<TutorConversationSummaryResult> search(String search, boolean archived,
            org.springframework.data.domain.Pageable page);

    TutorConversationSummaryResult restore(UUID id);

    TutorConversationSummaryResult rename(UUID id, String title);

    TutorConversationResult send(SendTutorMessageCommand command);

    TutorConversationResult conversation(UUID conversationId);

    List<TutorConversationSummaryResult> conversations();

    TutorConversationSummaryResult archive(UUID conversationId);

    TutorMessageResult report(UUID conversationId, UUID messageId, ReportTutorMessageCommand command);
}
