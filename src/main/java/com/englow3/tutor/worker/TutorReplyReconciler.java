package com.englow3.tutor.worker;

import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.tutor.entity.TutorMessageStatus;
import com.englow3.tutor.repository.TutorMessageRepository;
import com.englow3.tutor.service.TutorReplyWriter;
import lombok.RequiredArgsConstructor;

/** Repairs a terminal job whose notification transaction was interrupted. */
@Component
@ConditionalOnProperty(name = { "app.ai.enabled", "app.ai.worker.enabled" }, havingValue = "true")
@RequiredArgsConstructor
public class TutorReplyReconciler {
    private final TutorMessageRepository messageRepo;
    private final AiJobQueue jobs;
    private final TutorReplyWriter writer;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.ai.worker.reconcile-delay:30s}")
    public void reconcile() {
        messageRepo.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TutorMessageStatus.PENDING,
                clock.instant().minusSeconds(30), PageRequest.of(0, 50)).forEach(message -> {
                    if (jobs.hasFailedTutorReply(message.getId(), message.getRevision()))
                        writer.markFailed(message.getId(), "TUTOR_REPLY_FAILED");
                });
    }
}
