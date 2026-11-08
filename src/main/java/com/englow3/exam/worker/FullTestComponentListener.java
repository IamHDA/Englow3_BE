package com.englow3.exam.worker;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.englow3.assessment.api.AssessmentTaskArchiving;
import com.englow3.exam.repository.FullTestRepository;
import com.englow3.shared.error.ConflictException;

import lombok.RequiredArgsConstructor;

/** Archiving a task must not strand learners in a published or ongoing full test. */
@Component
@RequiredArgsConstructor
public class FullTestComponentListener {
    private final FullTestRepository fullTestRepo;

    @EventListener
    public void beforeArchive(AssessmentTaskArchiving event) {
        fullTestRepo.lockAvailability();
        if (fullTestRepo.protectsTask(event.taskId())) {
            throw new ConflictException("FULL_TEST_COMPONENT_IN_USE",
                    "Archive the full test and wait for its open sittings to settle before archiving this task");
        }
    }
}
