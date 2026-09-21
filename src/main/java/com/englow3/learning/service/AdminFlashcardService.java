package com.englow3.learning.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.learning.dto.command.AddFlashcardsCommand;
import com.englow3.learning.dto.command.CreateFlashcardSetCommand;
import com.englow3.learning.dto.result.FlashcardSetSummaryResult;
import com.englow3.learning.entity.Flashcard;
import com.englow3.learning.entity.FlashcardSet;
import com.englow3.learning.repository.FlashcardRepository;
import com.englow3.learning.repository.FlashcardSetRepository;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Authoring. The per-learner counts a summary carries are zero here - an administrator looking at the catalogue is not
 * studying it, and reporting their own due count on someone else's set would be meaningless.
 */
@Service
@RequiredArgsConstructor
public class AdminFlashcardService {

    private final FlashcardSetRepository setRepo;
    private final FlashcardRepository cardRepo;
    private final UserDirectory userDirectory;

    @Transactional
    public FlashcardSetSummaryResult createSet(CreateFlashcardSetCommand command) {
        if (setRepo.existsBySlug(command.slug())) {
            throw new ConflictException("FLASHCARD_SET_SLUG_TAKEN",
                    "A set already uses the slug %s".formatted(command.slug()));
        }

        FlashcardSet set = setRepo.save(FlashcardSet.draft(command.slug(), command.name(), command.description(),
                command.topic(), command.targetLevel(), userDirectory.requireCurrentUserId()));
        return summaryOf(set);
    }

    /**
     * Appends cards. Allowed on a published set as well as a draft: a new card is NEW for every learner and disturbs
     * nothing already scheduled. Editing or removing one would not be safe in the same way, which is why neither is
     * offered here.
     */
    @Transactional
    public FlashcardSetSummaryResult addCards(AddFlashcardsCommand command) {
        FlashcardSet set = requireSet(command.flashcardSetId());
        int nextOrderNo = cardRepo.findMaxOrderNo(set.getId()).orElse(0) + 1;

        List<Flashcard> cards = new ArrayList<>();
        for (AddFlashcardsCommand.NewCard card : command.cards()) {
            cards.add(Flashcard.of(set.getId(), nextOrderNo++, card.lemma(), card.partOfSpeech(), card.senseLabel(),
                    card.ipaUs(), card.ipaUk(), card.audioUsObjectKey(), card.audioUkObjectKey(), card.definitionEn(),
                    card.definitionVi(), card.exampleSentence(), card.exampleTranslationVi(), card.mnemonicTipVi(),
                    card.cefrLevel()));
        }
        cardRepo.saveAll(cards);

        return summaryOf(set);
    }

    @Transactional
    public FlashcardSetSummaryResult publish(UUID setId) {
        FlashcardSet set = requireSet(setId);
        set.publish(cardRepo.countByFlashcardSetId(setId), Instant.now());
        return summaryOf(set);
    }

    @Transactional
    public FlashcardSetSummaryResult archive(UUID setId) {
        FlashcardSet set = requireSet(setId);
        set.archive();
        return summaryOf(set);
    }

    private FlashcardSetSummaryResult summaryOf(FlashcardSet set) {
        return FlashcardSetSummaryResult.of(set, cardRepo.countByFlashcardSetId(set.getId()), 0L, 0L, null);
    }

    private FlashcardSet requireSet(UUID setId) {
        return setRepo.findById(setId).orElseThrow(
                () -> new NotFoundException("FLASHCARD_SET_NOT_FOUND", "No flashcard set with id %s".formatted(setId)));
    }
}
