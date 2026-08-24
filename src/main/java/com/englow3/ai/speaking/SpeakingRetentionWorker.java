package com.englow3.ai.speaking;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.shared.storage.ObjectStorageClient;

@Component
class SpeakingRetentionWorker {

    private static final Logger log = LoggerFactory.getLogger(SpeakingRetentionWorker.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectStorageClient storage;

    SpeakingRetentionWorker(JdbcTemplate jdbcTemplate, ObjectStorageClient storage) {
        this.jdbcTemplate = jdbcTemplate;
        this.storage = storage;
    }

    @Scheduled(cron = "${app.speech.retention-cron:0 0 * * * *}")
    void removeExpiredRecordings() {
        List<ExpiredRecording> recordings = jdbcTemplate.query("""
                select id, audio_bucket, audio_object_key from speaking_sessions
                where retention_until < now() and status not in ('PROCESSING', 'DELETED')
                order by retention_until limit 100
                """, (rs, row) -> new ExpiredRecording(rs.getObject("id", UUID.class), rs.getString("audio_bucket"),
                rs.getString("audio_object_key")));
        for (ExpiredRecording recording : recordings) {
            try {
                storage.delete(recording.bucket(), recording.objectKey());
                jdbcTemplate.update("""
                        update speaking_sessions set status = 'DELETED', deleted_at = now() where id = ?
                        """, recording.id());
            } catch (RuntimeException ex) {
                log.warn("Could not delete expired speaking recording {}", recording.id(), ex);
            }
        }
    }

    private record ExpiredRecording(UUID id, String bucket, String objectKey) {
    }
}
