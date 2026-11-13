package com.englow3.tutor.service;

import java.util.UUID;

public interface TutorTranscriptionWriter {
    void complete(UUID recordingId, String transcript);

    void failed(UUID recordingId, String code);
}
