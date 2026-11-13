package com.englow3.tutor.service;

import java.util.UUID;
import com.englow3.tutor.dto.result.TutorRecordingResult;

public interface TutorAudioService {
    interface UploadPermit extends AutoCloseable {
        @Override
        void close();
    }

    UploadPermit reserveUpload();

    TutorRecordingResult upload(UUID clientMessageId, String locale, byte[] wav);

    TutorRecordingResult receipt(UUID clientMessageId);

    void discard(UUID id);

    void cleanup();
}
