package com.englow3.tutor.service.impl;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.tutor.entity.TutorMessageStatus;
import com.englow3.tutor.repository.TutorMessageRepository;
import com.englow3.tutor.service.TutorPrompt;
import com.englow3.tutor.service.TutorReplyWriter;

import lombok.RequiredArgsConstructor;

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
    private final Clock clock;
    private final com.englow3.tutor.repository.TutorMemoryRepository memories;
    private final com.englow3.ai.api.AiJobQueue aiJobQueue;

    @Transactional
    public void storeSummaryReply(UUID messageId, int revision, long epoch, int throughOrder, String content,
            String model, Integer inputTokens, Integer outputTokens) {
        messageRepo
                .lockById(messageId).filter(message -> message.isSummary()
                        && message.getStatus() == TutorMessageStatus.PENDING && message.getRevision() == revision)
                .ifPresent(message -> {
                    message.attachSources("[]");
                    message.answer(content, model, com.englow3.tutor.service.TutorMemoryPrompt.VERSION, inputTokens,
                            outputTokens, clock.instant());
                });
        messageRepo.flush();
        memories.complete(messageId, revision, epoch, throughOrder,
                com.englow3.tutor.helper.TutorMemoryText.clip(content, 4000));
    }

    @Transactional(readOnly = true)
    public boolean isPending(UUID messageId, int revision) {
        return messageRepo.findById(messageId)
                .filter(m -> m.getStatus() == TutorMessageStatus.PENDING && m.getRevision() == revision).isPresent();
    }

    @Transactional
    public UUID startStream(UUID messageId, int revision) {
        return messageRepo.lockById(messageId).map(message -> message.startStream(revision)).orElse(null);
    }

    @Transactional
    public boolean partial(UUID messageId, int revision, UUID epoch, String text) {
        return messageRepo.lockById(messageId).map(message -> message.partial(revision, epoch, text)).orElse(false);
    }

    /**
     * Fills the pending turn.
     * <p>
     * Silently does nothing if the message is gone - a learner who deleted the conversation while the provider was
     * thinking has already said they do not want this - or if it is no longer pending, which is what a job running
     * twice after a stall reclaim looks like.
     */
    @Transactional
    public void storeReply(UUID messageId, String content, String model, Integer inputTokens, Integer outputTokens) {
        storeReply(messageId, 0, content, model, inputTokens, outputTokens);
    }

    @Transactional
    public void storeReply(UUID messageId, int revision, String content, String model, Integer inputTokens,
            Integer outputTokens) {
        messageRepo.lockById(messageId).filter(
                message -> message.getStatus() == TutorMessageStatus.PENDING && message.getRevision() == revision)
                .ifPresent(message -> {
                    message.attachSources(
                            com.englow3.tutor.helper.TutorSources.cited(message.getSourcesJson(), content));
                    message.answer(content, model, TutorPrompt.VERSION, inputTokens, outputTokens, clock.instant());
                });
    }

    @Transactional
    public void markFailed(UUID messageId, String errorCode) {
        messageRepo.lockById(messageId)
                .filter(message -> message.getStatus() == TutorMessageStatus.PENDING
                        && aiJobQueue.hasFailedTutorReply(messageId, message.getRevision()))
                .ifPresent(message -> message.fail(errorCode, clock.instant()));
    }
}
