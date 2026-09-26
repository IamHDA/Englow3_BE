package com.englow3.flashcard.dto.result;

import java.util.List;

public record FlashcardSetDetailResult(FlashcardSetSummaryResult set, List<FlashcardResult> cards) {
}
