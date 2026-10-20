package com.englow3.tutor.service.impl;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.tutor.repository.TutorRecordingRepository;
import com.englow3.tutor.service.TutorTranscriptionWriter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorTranscriptionWriterImpl implements TutorTranscriptionWriter {
    private final TutorRecordingRepository recordingRepo;
    private final AiJobQueue jobs;

    @Override
    @Transactional
    public void complete(UUID id, String transcript) {
        recordingRepo.lockById(id).ifPresent(row -> row.ready(transcript));
    }

    @Override
    @Transactional
    public void failed(UUID id, String code) {
        if (jobs.hasFailedTutorTranscription(id))
            recordingRepo.lockById(id).ifPresent(row -> row.failed(code));
    }
}
