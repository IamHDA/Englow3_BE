package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.ConflictException;

class AiPromptServiceTest {

    @Test
    void rendersEveryDeclaredVariable() {
        String result = AiPromptService.renderText("Level={{level}}; message={{message}}",
                Map.of("level", "B1", "message", "Explain present perfect"));

        assertThat(result).isEqualTo("Level=B1; message=Explain present perfect");
    }

    @Test
    void rejectsAnUnresolvedVariable() {
        assertThatThrownBy(() -> AiPromptService.renderText("Hello {{name}}", Map.of()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("missing a required variable");
    }
}
