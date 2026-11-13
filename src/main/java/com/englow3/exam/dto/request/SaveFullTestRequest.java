package com.englow3.exam.dto.request;

import java.util.UUID;

import com.englow3.exam.dto.command.SaveFullTestCommand;
import com.englow3.exam.entity.CertificateVariant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A full test's paper and five tasks. Whether they are published and of the right kind is checked when it goes for
 * review, not here, so a draft can name a task that is still being written.
 */
public record SaveFullTestRequest(@NotBlank @Size(max = 100) String title, @Size(max = 2000) String description,
        @NotNull @Schema(description = "ACADEMIC or GENERAL") CertificateVariant certificateVariant,
        @NotNull @Schema(description = "The Listening + Reading mock paper") UUID examId, @NotNull UUID writingTask1Id,
        @NotNull UUID writingTask2Id, @NotNull UUID speakingPart1Id, @NotNull UUID speakingPart2Id,
        @NotNull UUID speakingPart3Id) {

    public SaveFullTestCommand toCommand(UUID id) {
        return new SaveFullTestCommand(id, title, description, certificateVariant, examId, writingTask1Id,
                writingTask2Id, speakingPart1Id, speakingPart2Id, speakingPart3Id);
    }
}
