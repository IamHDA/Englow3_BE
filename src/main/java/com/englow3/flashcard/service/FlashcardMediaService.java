package com.englow3.flashcard.service;

import com.englow3.flashcard.dto.result.FlashcardMediaResult;
import java.io.InputStream;

public interface FlashcardMediaService {
    FlashcardMediaResult upload(InputStream content, long length, String contentType);
}
