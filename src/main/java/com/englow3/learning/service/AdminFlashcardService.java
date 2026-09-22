package com.englow3.learning.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.learning.dto.command.AddFlashcardsCommand;
import com.englow3.learning.dto.command.CreateFlashcardSetCommand;
import com.englow3.learning.dto.result.ContentReviewResult;
import com.englow3.learning.dto.result.FlashcardSetSummaryResult;
import com.englow3.learning.entity.Flashcard;
import com.englow3.learning.entity.FlashcardSet;
import com.englow3.learning.entity.FlashcardSetStatus;
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

    /**
     * Every set, whatever its status - the catalogue shows published ones only, and an administrator with no way to see
     * a draft has no way to review one. A null status means all of them, so one query serves both the full list and the
     * review queue. Card counts come from one grouped query rather than one per row.
     */
    @Transactional(readOnly = true)
    public Page<ContentReviewResult> searchForAuthoring(FlashcardSetStatus status, String title, Pageable pageable) {
        Page<FlashcardSet> page = setRepo.searchForAuthoring(status, title, pageable);
        Map<UUID, Long> counts = cardRepo.countBySetIds(page.getContent().stream().map(FlashcardSet::getId).toList());

        return page.map(set -> ContentReviewResult.of(set, counts.getOrDefault(set.getId(), 0L)));
    }

    @Transactional
    public ContentReviewResult publish(UUID setId) {
        FlashcardSet set = requireSet(setId);
        set.publish(cardRepo.countByFlashcardSetId(setId), Instant.now());
        return reviewStateOf(set);
    }

    @Transactional
    public ContentReviewResult submitForReview(UUID setId) {
        FlashcardSet set = requireSet(setId);
        set.submitForReview(cardRepo.countByFlashcardSetId(setId), Instant.now());

        return reviewStateOf(set);
    }

    /**
     * The reviewer's id is resolved here rather than taken from the request: a caller that could name its own reviewer
     * could credit the approval to someone else.
     */
    @Transactional
    public ContentReviewResult approve(UUID setId) {
        FlashcardSet set = requireSet(setId);
        set.approve(userDirectory.requireCurrentUserId(), cardRepo.countByFlashcardSetId(setId), Instant.now());

        return reviewStateOf(set);
    }

    @Transactional
    public ContentReviewResult reject(UUID setId, String note) {
        FlashcardSet set = requireSet(setId);
        set.reject(userDirectory.requireCurrentUserId(), note, Instant.now());

        return reviewStateOf(set);
    }

    @Transactional
    public ContentReviewResult archive(UUID setId) {
        FlashcardSet set = requireSet(setId);
        set.archive();
        return reviewStateOf(set);
    }

    /**
     * The catalogue summary carries three per-learner numbers that mean nothing to an author and would be zero here
     * anyway. The review state is what an authoring action changed, so it is what an authoring action returns.
     */
    private ContentReviewResult reviewStateOf(FlashcardSet set) {
        return ContentReviewResult.of(set, cardRepo.countByFlashcardSetId(set.getId()));
    }

    private FlashcardSetSummaryResult summaryOf(FlashcardSet set) {
        return FlashcardSetSummaryResult.of(set, cardRepo.countByFlashcardSetId(set.getId()), 0L, 0L, null);
    }

    private FlashcardSet requireSet(UUID setId) {
        return setRepo.findById(setId).orElseThrow(
                () -> new NotFoundException("FLASHCARD_SET_NOT_FOUND", "No flashcard set with id %s".formatted(setId)));
    }
}
