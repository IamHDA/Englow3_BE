package com.englow3.tutor.service;

import java.util.List;
import java.util.UUID;

import com.englow3.tutor.dto.command.ReportTutorMessageCommand;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.tutor.dto.result.*;

public interface TutorService {
    TutorConversationResult send(SendTutorMessageCommand command);

    TutorConversationResult conversation(UUID conversationId);

    List<TutorConversationSummaryResult> conversations();

    TutorConversationSummaryResult archive(UUID conversationId);

    TutorMessageResult report(UUID conversationId, UUID messageId, ReportTutorMessageCommand command);
}
