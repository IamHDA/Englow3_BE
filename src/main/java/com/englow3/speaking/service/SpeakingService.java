package com.englow3.speaking.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.speaking.dto.result.*;

public interface SpeakingService {
    Page<SpeakingPromptResult> searchPublished(String category, String title, Pageable pageable);

    SpeakingPromptResult promptDetail(UUID promptId);
}
