package com.englow3.assessment.worker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.englow3.ai.client.*;
import com.englow3.assessment.service.AssessmentResultWriter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ProductiveAssessmentHandlerTest {
    @Mock
    LlmClient llm;
    @Mock
    SpeechAssessmentClient speech;
    @Mock
    ObjectStorageClient storage;
    @Mock
    AssessmentResultWriter writer;
    ObjectMapper mapper = new ObjectMapper();
    ProductiveAssessmentHandler handler;
    UUID id = UUID.randomUUID();

    @BeforeEach
    void setup() {
        handler = new ProductiveAssessmentHandler(llm, speech, storage, writer, mapper);
    }

    String input(String skill) throws Exception {
        var input = mapper.createObjectNode();
        input.put("attemptId", id.toString());
        input.put("skill", skill);
        input.put("revision", 3);
        input.put("taskSnapshot", "{}");
        input.put("answerText", "An essay response");
        input.put("objectKey", "sealed.wav");
        input.put("contentType", "audio/wav");
        return mapper.writeValueAsString(input);
    }

    @Test
    void providerOutageIsRetryableWithoutPublishingResult() throws Exception {
        when(llm.generateStructured(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("PROVIDER_UNAVAILABLE", "private provider details", true));
        var result = handler.run(UUID.randomUUID(), id, input("WRITING"));
        assertFalse(result.success());
        assertTrue(result.retryable());
        assertFalse(result.errorMessage().contains("private"));
        verifyNoInteractions(writer);
    }

    @Test
    void incompleteRubricNeverBecomesACompletedResult() throws Exception {
        when(llm.generateStructured(anyString(), anyString(), anyInt()))
                .thenReturn("{\"content\":\"{\\\"criteria\\\":[]}\"}");
        var result = handler.run(UUID.randomUUID(), id, input("WRITING"));
        assertFalse(result.success());
        assertFalse(result.retryable());
        assertEquals("ASSESSMENT_REPORT_INVALID", result.errorCode());
        verifyNoInteractions(writer);
    }

    @Test
    void speakingCannotInventEvidenceFromInvalidAudio() throws Exception {
        when(storage.download(any(), eq("sealed.wav"))).thenReturn(new byte[44]);
        var result = handler.run(UUID.randomUUID(), id, input("SPEAKING"));
        assertEquals("RECORDING_INVALID", result.errorCode());
        verifyNoInteractions(speech, llm, writer);
    }

    @Test
    void validReportIsNormalizedAndWrittenWithTheJobRevision() throws Exception {
        String report = """
                {"overall":9,"criteria":[{"key":"TASK_RESPONSE","score":6,"feedback":"Task evidence"},
                {"key":"COHERENCE_COHESION","score":6,"feedback":"Organization evidence"},
                {"key":"LEXICAL_RESOURCE","score":6,"feedback":"Vocabulary evidence"},
                {"key":"GRAMMATICAL_RANGE","score":6,"feedback":"Grammar evidence"}],
                "summary":"Practice feedback","strengths":[],"improvements":["Develop examples"]}
                """;
        when(llm.generateStructured(anyString(), anyString(), anyInt()))
                .thenReturn(mapper.createObjectNode().put("content", report).toString());
        var result = handler.run(UUID.randomUUID(), id, input("WRITING"));
        assertTrue(result.success());
        assertEquals(6, mapper.readTree(result.outputPayload()).path("overall").asInt());
        verify(writer).complete(eq(id), eq(3), eq(result.outputPayload()), isNull());
    }
}
