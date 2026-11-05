package com.englow3.translation.entity;

import java.util.UUID;

import com.englow3.shared.error.BadRequestException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** One Vietnamese sentence in a set, with an English rendering that guides the grader. */
@Entity
@Table(name = "translation_items")
@Getter
public class TranslationItem {

    @Id
    private UUID id;

    @Column(name = "translation_set_id", nullable = false, updatable = false)
    private UUID translationSetId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(name = "source_vi", nullable = false)
    private String sourceVi;

    @Column(name = "reference_en", nullable = false)
    private String referenceEn;

    private String notes;

    protected TranslationItem() {
    }

    public static TranslationItem of(UUID setId, int orderNo, String sourceVi, String referenceEn, String notes) {
        if (sourceVi == null || sourceVi.isBlank()) {
            throw new BadRequestException("TRANSLATION_ITEM_NO_SOURCE", "Each sentence needs its Vietnamese text");
        }
        if (referenceEn == null || referenceEn.isBlank()) {
            throw new BadRequestException("TRANSLATION_ITEM_NO_REFERENCE",
                    "Each sentence needs a reference English translation");
        }
        TranslationItem item = new TranslationItem();
        item.id = UUID.randomUUID();
        item.translationSetId = setId;
        item.orderNo = orderNo;
        item.sourceVi = sourceVi.strip();
        item.referenceEn = referenceEn.strip();
        item.notes = notes == null || notes.isBlank() ? null : notes.strip();
        return item;
    }
}
