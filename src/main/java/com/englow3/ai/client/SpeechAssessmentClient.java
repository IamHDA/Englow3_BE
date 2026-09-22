package com.englow3.ai.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to {@code ai_service}, which is the only thing that holds provider credentials.
 * <p>
 * Spring never calls Azure directly and does not know it is Azure: swapping the speech provider is a change inside the
 * FastAPI adapter and a redeploy of it, with nothing here to touch. The shared secret in the header is what stops
 * anything else on the network from spending the provider quota.
 */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SpeechAssessmentClient {

    private static final String ASSESS_PATH = "/internal/v1/speech/assess";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;

    public SpeechAssessmentClient(@Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.internal-api-key:}") String internalApiKey,
            @Value("${app.ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.ai.read-timeout:45s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // Both timeouts set explicitly: the default is none at all, and a worker blocked forever on a provider that
        // stopped answering holds its job RUNNING until the stall reconciler notices - minutes of nothing.
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .defaultHeader(INTERNAL_KEY_HEADER, internalApiKey).build();
    }

    /**
     * Scores one recording. Returns the adapter's JSON as a string rather than a parsed shape: the queue stores it
     * untouched and the module that asked for it is the one that knows what to read.
     *
     * @param referenceText
     *            what the learner was asked to say. Assessment without it is plain transcription - the accuracy score
     *            only means something against an expected sentence.
     *
     * @throws SpeechAssessmentException
     *             carrying whether the request is worth repeating
     */
    public String assess(byte[] audio, String contentType, String locale, String referenceText) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", new NamedByteArrayResource(audio, "recording", contentType));
        form.add("locale", locale);
        if (referenceText != null) {
            form.add("reference_text", referenceText);
        }

        try {
            return restClient.post().uri(ASSESS_PATH).contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve()
                    // The distinction that matters is not "did it fail" but "would the same request work in a
                    // minute", and only the status says that - hence a handler rather than a catch on the default.
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new SpeechAssessmentException("SPEECH_ASSESSMENT_REJECTED",
                                "ai_service answered %s".formatted(response.getStatusCode()),
                                retryable(response.getStatusCode()));
                    }).body(String.class);
        } catch (RestClientException transportFailure) {
            // Nothing came back at all: a timeout, a refused connection, a DNS failure. Always worth another go -
            // none of them says anything about the recording.
            throw new SpeechAssessmentException("SPEECH_SERVICE_UNREACHABLE", transportFailure.getMessage(), true);
        }
    }

    /**
     * 4xx means the adapter read the request and refused it - a format it does not accept, audio too large, a missing
     * key - and sending it again changes nothing. The exceptions are 408 and 429, which are explicitly "later".
     */
    static boolean retryable(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return true;
        }
        return status.value() == 408 || status.value() == 429;
    }

    /**
     * Multipart needs a filename or the adapter receives a plain field instead of a file, and its content type is
     * dropped. {@code ByteArrayResource} has no filename, so this supplies one.
     */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] content, String baseName, String contentType) {
            super(content);
            this.filename = baseName + extensionFor(contentType);
        }

        @Override
        public String getFilename() {
            return filename;
        }

        private static String extensionFor(String contentType) {
            return switch (contentType) {
                case "audio/ogg" -> ".ogg";
                case "audio/wav", "audio/x-wav" -> ".wav";
                // The adapter checks the type itself and refuses anything else; the extension is a hint, not a gate.
                default -> ".bin";
            };
        }
    }
}
