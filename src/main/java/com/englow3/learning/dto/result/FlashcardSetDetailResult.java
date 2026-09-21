package com.englow3.learning.dto.result;

import java.util.List;

public record FlashcardSetDetailResult(FlashcardSetSummaryResult set, List<FlashcardResult> cards) {
}
