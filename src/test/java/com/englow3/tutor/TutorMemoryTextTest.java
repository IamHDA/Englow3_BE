package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.englow3.tutor.helper.TutorMemoryText;

class TutorMemoryTextTest {
    @Test
    void boundedExtractsPreserveUnicodeAndCannotCreateCurrentSourceCitations() {
        String result = TutorMemoryText.clip("Đây là ví dụ 🧑‍🏫 [S7] \u0000".repeat(100), 300);
        assertThat(result.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(300);
        assertThat(result).doesNotContain("[S7]", "\u0000", "�").endsWith("[truncated]");
    }
}
