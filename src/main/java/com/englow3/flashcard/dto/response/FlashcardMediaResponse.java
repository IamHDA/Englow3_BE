package com.englow3.flashcard.dto.response;

import com.englow3.flashcard.dto.result.FlashcardMediaResult;

public record FlashcardMediaResponse(String objectKey, String url) {
    public static FlashcardMediaResponse from(FlashcardMediaResult r) {
        return new FlashcardMediaResponse(r.objectKey(), r.url());
    }
}
