package com.englow3.assessment.helper;

import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Evidence is optional, but references must describe the submitted response. */
public final class AssessmentEvidence {
    private AssessmentEvidence() {
    }

    public static void validate(ObjectMapper mapper, AssessmentSkill skill, String report, String answer,
            Long wavBytes) {
        try {
            for (JsonNode c : mapper.readTree(report).path("criteria")) {
                JsonNode quote = c.path("quote");
                if (!quote.isMissingNode() && !quote.isNull()
                        && (!quote.isTextual() || quote.asText().isBlank() || quote.asText().length() > 500
                                || skill != AssessmentSkill.WRITING || answer == null
                                || !answer.contains(quote.asText()))) {
                    throw invalid();
                }
                JsonNode start = c.path("audioStart"), end = c.path("audioEnd");
                if ((!start.isMissingNode() && !start.isNull()) || (!end.isMissingNode() && !end.isNull())) {
                    // Learner recordings are PCM16 mono WAV at 16 kHz, encoded by the recording flow.
                    double maximum = wavBytes == null ? 0 : Math.max(0, (wavBytes - 44) / 32000.0);
                    if (skill != AssessmentSkill.SPEAKING || !start.isNumber() || !end.isNumber()
                            || start.asDouble() < 0 || end.asDouble() <= start.asDouble() || end.asDouble() > maximum) {
                        throw invalid();
                    }
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException("ASSESSMENT_EVIDENCE_INVALID",
                "Use an exact quote or a time range within the submitted recording");
    }
}
