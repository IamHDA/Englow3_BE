package com.englow3.tutor.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.tutor.entity.TutorMessageStatus;
import com.englow3.tutor.repository.TutorMessageRepository;

import lombok.RequiredArgsConstructor;
import com.englow3.tutor.service.*;

/**
 * The single write at the end of a reply.
 * <p>
 * A separate bean from the handler for the same reason {@code SpeakingAssessmentWriter} is: {@code @Transactional}
 * works through a proxy, so a handler calling its own transactional method would run it with no transaction and nothing
 * would say so.
 */
@Service
@RequiredArgsConstructor
public class TutorReplyWriterImpl implements TutorReplyWriter {

    private final TutorMessageRepository messageRepo;

    /**
     * Fills the pending turn.
     * <p>
     * Silently does nothing if the message is gone - a learner who deleted the conversation while the provider was
     * thinking has already said they do not want this - or if it is no longer pending, which is what a job running
     * twice after a stall reclaim looks like.
     */
    @Transactional
    public void storeReply(UUID messageId, String content, String model, Integer inputTokens, Integer outputTokens) {
        messageRepo.findById(messageId).filter(message -> message.getStatus() == TutorMessageStatus.PENDING)
                .ifPresent(message -> message.answer(content, model, TutorPrompt.VERSION, inputTokens, outputTokens,
                        Instant.now()));
    }

    @Transactional
    public void markFailed(UUID messageId, String errorCode) {
        messageRepo.findById(messageId).filter(message -> message.getStatus() == TutorMessageStatus.PENDING)
                .ifPresent(message -> message.fail(errorCode, Instant.now()));
    }
}
