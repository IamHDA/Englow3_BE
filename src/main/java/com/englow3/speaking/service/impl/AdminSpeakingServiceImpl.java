package com.englow3.speaking.service.impl;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.speaking.dto.command.CreateSpeakingPromptCommand;
import com.englow3.speaking.dto.result.SpeakingPromptReviewResult;
import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.entity.SpeakingPromptStatus;
import com.englow3.speaking.repository.SpeakingPromptRepository;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import com.englow3.speaking.service.*;

/** Authoring prompts, and the review workflow every other content type already runs. */
@Service
@RequiredArgsConstructor
public class AdminSpeakingServiceImpl implements AdminSpeakingService {

    private final SpeakingPromptRepository promptRepo;
    private final UserDirectory userDirectory;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public SpeakingPromptReviewResult create(CreateSpeakingPromptCommand command) {
        if (promptRepo.existsBySlug(command.slug())) {
            throw new ConflictException("SPEAKING_PROMPT_SLUG_TAKEN",
                    "A prompt already uses the slug %s".formatted(command.slug()));
        }

        SpeakingPrompt prompt = promptRepo
                .save(SpeakingPrompt.draft(command.slug(), command.title(), command.category(), command.targetLevel(),
                        command.referenceText(), command.ipaTranscript(), command.translationVi(),
                        command.phonemeTarget(), tipsJson(command), userDirectory.requireCurrentUserId()));

        return SpeakingPromptReviewResult.of(prompt);
    }

    /**
     * Every prompt, whatever its status - the catalogue shows published ones only, and an administrator with no way to
     * see a draft has no way to review one.
     */
    @Transactional(readOnly = true)
    public Page<SpeakingPromptReviewResult> searchForAuthoring(SpeakingPromptStatus status, String title,
            Pageable pageable) {
        return promptRepo.searchForAuthoring(status, title, pageable).map(SpeakingPromptReviewResult::of);
    }

    @Transactional
    public SpeakingPromptReviewResult submitForReview(UUID promptId) {
        SpeakingPrompt prompt = requirePrompt(promptId);
        prompt.submitForReview(clock.instant());

        return SpeakingPromptReviewResult.of(prompt);
    }

    /** The reviewer's id comes from the token, not the request - nobody credits an approval to someone else. */
    @Transactional
    public SpeakingPromptReviewResult approve(UUID promptId) {
        SpeakingPrompt prompt = requirePrompt(promptId);
        prompt.approve(userDirectory.requireCurrentUserId(), clock.instant());

        return SpeakingPromptReviewResult.of(prompt);
    }

    @Transactional
    public SpeakingPromptReviewResult reject(UUID promptId, String note) {
        SpeakingPrompt prompt = requirePrompt(promptId);
        prompt.reject(userDirectory.requireCurrentUserId(), note, clock.instant());

        return SpeakingPromptReviewResult.of(prompt);
    }

    @Transactional
    public SpeakingPromptReviewResult publish(UUID promptId) {
        SpeakingPrompt prompt = requirePrompt(promptId);
        prompt.publish(clock.instant());

        return SpeakingPromptReviewResult.of(prompt);
    }

    @Transactional
    public SpeakingPromptReviewResult restore(UUID promptId) {
        SpeakingPrompt prompt = requirePrompt(promptId);
        prompt.restore();

        return SpeakingPromptReviewResult.of(prompt);
    }

    @Transactional
    public SpeakingPromptReviewResult archive(UUID promptId) {
        SpeakingPrompt prompt = requirePrompt(promptId);
        prompt.archive();

        return SpeakingPromptReviewResult.of(prompt);
    }

    /** Tips arrive as a list and are stored as JSON, so the serialisation happens once, here. */
    private String tipsJson(CreateSpeakingPromptCommand command) {
        try {
            return objectMapper.writeValueAsString(command.tips() == null ? java.util.List.of() : command.tips());
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Could not serialise the prompt tips", impossible);
        }
    }

    @Transactional(readOnly = true)
    public com.englow3.speaking.dto.result.AuthoringResult authoringDetail(UUID id) {
        var item = requirePrompt(id);
        return new com.englow3.speaking.dto.result.AuthoringResult(id, item.getVersion(), item.getStatus().name(),
                item.getReviewNote(),
                new CreateSpeakingPromptCommand(item.getSlug(), item.getTitle(), item.getCategory(),
                        item.getTargetLevel(), item.getReferenceText(), item.getIpaTranscript(),
                        item.getTranslationVi(), item.getPhonemeTarget(), parseTips(item.getTips())));
    }

    @Transactional
    public com.englow3.speaking.dto.result.AuthoringResult saveAuthoring(
            com.englow3.speaking.dto.command.SaveAuthoringCommand command) {
        UUID id = command.id();
        if (id == null) {
            id = create(command.metadata()).id();
        } else {
            if (command.version() == null) {
                throw new com.englow3.shared.error.BadRequestException("CONTENT_VERSION_REQUIRED",
                        "A version is required when editing");
            }
            var item = requirePrompt(id);
            if (promptRepo.existsBySlugAndIdNot(command.metadata().slug(), id)) {
                throw new ConflictException("CONTENT_SLUG_TAKEN", "Another item uses this slug");
            }
            item.updateDraft(command.metadata(), command.version(), clock.instant(), tipsJson(command.metadata()));
            promptRepo.flush();
        }

        promptRepo.flush();
        return authoringDetail(id);
    }

    private java.util.List<String> parseTips(String json) {
        try {
            return objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                    });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored coaching notes", e);
        }
    }

    private SpeakingPrompt requirePrompt(UUID promptId) {
        return promptRepo.findById(promptId).orElseThrow(() -> new NotFoundException("SPEAKING_PROMPT_NOT_FOUND",
                "No speaking prompt with id %s".formatted(promptId)));
    }
}
