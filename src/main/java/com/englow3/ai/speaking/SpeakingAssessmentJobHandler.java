package com.englow3.ai.speaking;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.englow3.ai.foundation.AiCapability;
import com.englow3.ai.foundation.AiGateway;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobExecutionResult;
import com.englow3.ai.foundation.AiJobHandler;
import com.englow3.ai.foundation.AiProviderException;
import com.englow3.ai.foundation.AiTextResult;
import com.englow3.shared.storage.ObjectStorageClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
class SpeakingAssessmentJobHandler implements AiJobHandler {

    private final SpeechAssessmentClient speechClient;
    private final AiGateway aiGateway;
    private final ObjectStorageClient storage;
    private final JdbcTemplate jdbcTemplate;
    private final SpeakingAssessmentPersistence persistence;
    private final ObjectMapper objectMapper;

    SpeakingAssessmentJobHandler(SpeechAssessmentClient speechClient, AiGateway aiGateway, ObjectStorageClient storage,
            JdbcTemplate jdbcTemplate, SpeakingAssessmentPersistence persistence, ObjectMapper objectMapper) {
        this.speechClient = speechClient;
        this.aiGateway = aiGateway;
        this.storage = storage;
        this.jdbcTemplate = jdbcTemplate;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return "SPEAKING_ASSESSMENT";
    }

    @Override
    public AiJobExecutionResult execute(AiJob job) {
        JsonNode payload = job.getInputPayload();
        UUID sessionId = UUID.fromString(payload.path("sessionId").asText());
        SessionInput input = load(sessionId);
        byte[] audio = storage.download(input.bucket(), input.objectKey());
        validateMagic(audio, input.contentType());
        SpeechAssessmentResult speech = speechClient.assess(audio, input.contentType(), input.locale(),
                input.referenceText());
        LanguageFeedback language = languageFeedback(job.getRequesterUserId(), payload, speech);
        persistence.save(sessionId, speech, language.grammar(), language.vocabulary());

        ObjectNode output = objectMapper.createObjectNode().put("sessionId", sessionId.toString()).put("recognizedText",
                speech.recognizedText());
        output.put("speechProvider", speech.provider());
        putNullable(output, "accuracy", speech.accuracy());
        putNullable(output, "fluency", speech.fluency());
        putNullable(output, "completeness", speech.completeness());
        putNullable(output, "prosody", speech.prosody());
        putNullable(output, "pronunciation", speech.pronunciation());
        output.put("grammarFeedback", language.grammar());
        output.put("vocabularyFeedback", language.vocabulary());
        return new AiJobExecutionResult(output, language.inputTokens(), language.outputTokens(),
                language.estimatedCost());
    }

    @Override
    public void onFailure(AiJob job, boolean willRetry) {
        if (!willRetry) {
            persistence.markFailed(job.getTargetId());
        }
    }

    private LanguageFeedback languageFeedback(UUID userId, JsonNode payload, SpeechAssessmentResult speech) {
        String scores = "accuracy=%s, fluency=%s, completeness=%s, prosody=%s, pronunciation=%s".formatted(
                speech.accuracy(), speech.fluency(), speech.completeness(), speech.prosody(), speech.pronunciation());
        String prompt = payload.path("userPromptTemplate").asText().replace("__TRANSCRIPT__", speech.recognizedText())
                .replace("__SCORES__", scores);
        try {
            AiTextResult result = aiGateway.generate(userId, AiCapability.SPEAKING,
                    payload.path("systemPrompt").asText(), prompt, true);
            JsonNode structured = objectMapper.readTree(result.content());
            return new LanguageFeedback(requiredFeedback(structured, "grammarFeedback"),
                    requiredFeedback(structured, "vocabularyFeedback"), result.inputTokens(), result.outputTokens(),
                    result.estimatedCost());
        } catch (JsonProcessingException | RuntimeException ex) {
            return new LanguageFeedback("Language feedback is temporarily unavailable",
                    "Vocabulary feedback is temporarily unavailable", 0, 0, BigDecimal.ZERO);
        }
    }

    static String requiredFeedback(JsonNode structured, String field) {
        String value = structured.path(field).asText().strip();
        if (value.isBlank() || value.length() > 4_000) {
            throw new AiProviderException("AI_SPEAKING_FEEDBACK_SCHEMA_INVALID",
                    "Speaking language feedback has an invalid " + field, true);
        }
        return value;
    }

    private SessionInput load(UUID sessionId) {
        SessionInput input = jdbcTemplate.query("""
                select audio_bucket, audio_object_key, audio_content_type, locale, reference_text
                from speaking_sessions where id = ? and status = 'PROCESSING'
                """,
                rs -> rs.next() ? new SessionInput(rs.getString("audio_bucket"), rs.getString("audio_object_key"),
                        rs.getString("audio_content_type"), rs.getString("locale"), rs.getString("reference_text"))
                        : null,
                sessionId);
        if (input == null) {
            throw new AiProviderException("SPEAKING_SESSION_NOT_PROCESSABLE",
                    "Speaking session is not ready for processing", false);
        }
        return input;
    }

    static void validateMagic(byte[] audio, String contentType) {
        boolean validWav = contentType.startsWith("audio/wav") && audio.length >= 12
                && Arrays.equals(Arrays.copyOfRange(audio, 0, 4), new byte[] { 'R', 'I', 'F', 'F' })
                && Arrays.equals(Arrays.copyOfRange(audio, 8, 12), new byte[] { 'W', 'A', 'V', 'E' });
        boolean validOgg = contentType.startsWith("audio/ogg") && audio.length >= 4
                && Arrays.equals(Arrays.copyOfRange(audio, 0, 4), new byte[] { 'O', 'g', 'g', 'S' });
        if (!validWav && !validOgg) {
            throw new AiProviderException("SPEAKING_AUDIO_SIGNATURE_INVALID",
                    "Audio signature does not match its declared format", false);
        }
        if (validWav) {
            validateWaveEncoding(audio);
        } else if (indexOf(audio, "OpusHead".getBytes(StandardCharsets.US_ASCII), 512) < 0) {
            throw invalidEncoding("OGG audio must contain Opus data");
        }
    }

    private static void validateWaveEncoding(byte[] audio) {
        ByteBuffer buffer = ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN);
        int position = 12;
        int byteRate = 0;
        long dataBytes = -1;
        boolean validFormat = false;
        while (position + 8 <= audio.length) {
            String chunk = new String(audio, position, 4, StandardCharsets.US_ASCII);
            long length = Integer.toUnsignedLong(buffer.getInt(position + 4));
            long next = position + 8L + length + (length & 1L);
            if (next > audio.length || next > Integer.MAX_VALUE) {
                throw invalidEncoding("WAV chunk length is invalid");
            }
            if ("fmt ".equals(chunk) && length >= 16) {
                int format = Short.toUnsignedInt(buffer.getShort(position + 8));
                int channels = Short.toUnsignedInt(buffer.getShort(position + 10));
                long sampleRate = Integer.toUnsignedLong(buffer.getInt(position + 12));
                byteRate = buffer.getInt(position + 16);
                int bits = Short.toUnsignedInt(buffer.getShort(position + 22));
                validFormat = format == 1 && channels == 1 && sampleRate == 16_000 && bits == 16 && byteRate > 0;
            } else if ("data".equals(chunk)) {
                dataBytes = length;
            }
            position = (int) next;
        }
        if (!validFormat || dataBytes < 0) {
            throw invalidEncoding("WAV audio must be mono PCM, 16-bit, 16 kHz");
        }
        if (dataBytes * 1000L / byteRate > 60_000L) {
            throw new AiProviderException("SPEAKING_AUDIO_TOO_LONG", "Speech audio cannot exceed 60 seconds", false);
        }
    }

    private static int indexOf(byte[] source, byte[] target, int maxBytes) {
        int limit = Math.min(source.length - target.length, maxBytes - target.length);
        for (int i = 0; i <= limit; i++) {
            if (Arrays.equals(Arrays.copyOfRange(source, i, i + target.length), target)) {
                return i;
            }
        }
        return -1;
    }

    private static AiProviderException invalidEncoding(String message) {
        return new AiProviderException("SPEAKING_AUDIO_ENCODING_INVALID", message, false);
    }

    private void putNullable(ObjectNode output, String name, Double value) {
        if (value == null) {
            output.putNull(name);
        } else {
            output.put(name, value);
        }
    }

    private record SessionInput(String bucket, String objectKey, String contentType, String locale,
            String referenceText) {
    }

    private record LanguageFeedback(String grammar, String vocabulary, int inputTokens, int outputTokens,
            BigDecimal estimatedCost) {
    }
}
