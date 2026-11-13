package com.englow3.flashcard.dto.result;

import java.util.List;

public record FlashcardCatalogueInfoResult(List<String> topics, long totalDueCount) {
}
