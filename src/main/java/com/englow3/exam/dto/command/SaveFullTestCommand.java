package com.englow3.exam.dto.command;

import java.util.List;
import java.util.UUID;

import com.englow3.exam.entity.CertificateVariant;

/**
 * @param id
 *            null to create a new draft
 */
public record SaveFullTestCommand(UUID id, String title, String description, CertificateVariant certificateVariant,
        UUID examId, UUID writingTask1Id, UUID writingTask2Id, UUID speakingPart1Id, UUID speakingPart2Id,
        UUID speakingPart3Id, Long version) {
    public SaveFullTestCommand(UUID id, String title, String description, CertificateVariant variant, UUID examId,
            UUID writing1, UUID writing2, UUID speaking1, UUID speaking2, UUID speaking3) {
        this(id, title, description, variant, examId, writing1, writing2, speaking1, speaking2, speaking3, null);
    }

    public List<UUID> taskIds() {
        return List.of(writingTask1Id, writingTask2Id, speakingPart1Id, speakingPart2Id, speakingPart3Id);
    }
}
