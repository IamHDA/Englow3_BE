package com.englow3.assessment.helper;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssessmentEvidenceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void quotesMustOccurInTheActualWriting() {
        assertThatCode(() -> AssessmentEvidence.validate(mapper, AssessmentSkill.WRITING,
                "{\"criteria\":[{\"quote\":\"every day\"}]}", "I practice every day.", null))
                        .doesNotThrowAnyException();
        assertThatThrownBy(() -> AssessmentEvidence.validate(mapper, AssessmentSkill.WRITING,
                "{\"criteria\":[{\"quote\":\"invented text\"}]}", "I practice every day.", null))
                        .isInstanceOf(BadRequestException.class);
    }

    @Test
    void audioEvidenceMustHaveBothTimesWithinTheRecording() {
        assertThatCode(() -> AssessmentEvidence.validate(mapper, AssessmentSkill.SPEAKING,
                "{\"criteria\":[{\"audioStart\":2,\"audioEnd\":5}]}", null, 320044L)).doesNotThrowAnyException();
        for (String range : new String[] { "\"audioStart\":2", "\"audioStart\":6,\"audioEnd\":5",
                "\"audioStart\":2,\"audioEnd\":11" }) {
            assertThatThrownBy(() -> AssessmentEvidence.validate(mapper, AssessmentSkill.SPEAKING,
                    "{\"criteria\":[{" + range + "}]}", null, 320044L)).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void legacyFeedbackWithoutEvidenceRemainsValid() {
        assertThatCode(() -> AssessmentEvidence.validate(mapper, AssessmentSkill.WRITING,
                "{\"criteria\":[{\"feedback\":\"Clear idea\"}]}", "answer", null)).doesNotThrowAnyException();
    }
}
