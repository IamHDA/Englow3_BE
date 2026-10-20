package com.englow3.tutor.dto.result;

import java.util.UUID;
import com.englow3.tutor.entity.TutorRecording;

public record TutorRecordingResult(UUID id, String status, String transcript, String errorCode) {
    public static TutorRecordingResult of(TutorRecording recording) {
        return new TutorRecordingResult(recording.getId(), recording.getStatus(), recording.getTranscript(),
                recording.getErrorCode());
    }
}
