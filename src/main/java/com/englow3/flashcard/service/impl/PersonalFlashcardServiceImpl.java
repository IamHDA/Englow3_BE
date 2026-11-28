package com.englow3.flashcard.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.flashcard.dto.command.PersonalCardCommand;
import com.englow3.flashcard.dto.command.SavePersonalSetCommand;
import com.englow3.flashcard.dto.result.PersonalCardResult;
import com.englow3.flashcard.dto.result.PersonalSetResult;
import com.englow3.flashcard.entity.Flashcard;
import com.englow3.flashcard.entity.FlashcardSet;
import com.englow3.flashcard.entity.FlashcardSetShare;
import com.englow3.flashcard.entity.FlashcardSetVisibility;
import com.englow3.flashcard.repository.FlashcardRepository;
import com.englow3.flashcard.repository.FlashcardSetRepository;
import com.englow3.flashcard.repository.FlashcardSetShareRepository;
import com.englow3.flashcard.service.PersonalFlashcardService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * The editor for a learner's own sets. Who may see a set is the set's rule ({@link FlashcardSet#isVisibleTo}); this
 * class supplies the facts that span other modules - whether the chosen people are friends - and keeps the friend list
 * in step with the set.
 */
@Service
@RequiredArgsConstructor
public class PersonalFlashcardServiceImpl implements PersonalFlashcardService {

    private final FlashcardSetRepository flashcardSetRepo;
    private final FlashcardRepository flashcardRepo;
    private final FlashcardSetShareRepository flashcardSetShareRepo;
    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final PublicProfileReader publicProfileReader;
    private final Clock clock;

    /** Cards on create go in one batch: a new set has nobody's progress on it yet, so nothing can be disturbed. */
    @Override
    @Transactional
    public PersonalSetResult createSet(SavePersonalSetCommand command) {
        UUID ownerId = userDirectory.requireCurrentUserId();
        Instant now = clock.instant();
        Set<UUID> friendIds = distinct(command.friendIds());

        FlashcardSet draft = FlashcardSet.personal(command.name(), command.description(), command.topic(),
                command.targetLevel(), command.visibility(), friendIds.size(), ownerId, now);
        requireFriends(ownerId, friendIds);
        FlashcardSet set = flashcardSetRepo.save(draft);
        replaceShares(set.getId(), friendIds, now);

        int orderNo = 1;
        List<Flashcard> cards = new ArrayList<>();
        for (PersonalCardCommand card : command.cards()) {
            cards.add(Flashcard.personal(set.getId(), orderNo++, card));
        }
        flashcardRepo.saveAll(cards);
        flashcardSetRepo.flush();

        return resultOf(set);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonalSetResult ownSet(UUID setId) {
        return resultOf(requireOwnSet(setId, userDirectory.requireCurrentUserId()));
    }

    @Override
    @Transactional
    public PersonalSetResult updateSet(UUID setId, SavePersonalSetCommand command) {
        if (command.version() == null) {
            throw new BadRequestException("CONTENT_VERSION_REQUIRED", "A version is required when editing");
        }
        UUID ownerId = userDirectory.requireCurrentUserId();
        FlashcardSet set = requireOwnSet(setId, ownerId);
        Instant now = clock.instant();
        Set<UUID> friendIds = distinct(command.friendIds());

        set.revisePersonal(command.name(), command.description(), command.topic(), command.targetLevel(),
                command.visibility(), friendIds.size(), command.version(), now);
        requireFriends(ownerId, friendIds);
        replaceShares(setId, friendIds, now);
        // Flushed here so a save racing this one fails this request (409 CONCURRENT_UPDATE), and so the version in the
        // response is the one the database now holds.
        flashcardSetRepo.flush();

        return resultOf(set);
    }

    /**
     * Removes the set and, by the database's cascade, its cards, share rows and every learner's progress on them. A set
     * shared with others is still the owner's to withdraw.
     */
    @Override
    @Transactional
    public void deleteSet(UUID setId) {
        flashcardSetRepo.delete(requireOwnSet(setId, userDirectory.requireCurrentUserId()));
    }

    /**
     * Card edits leave the set row alone. Touching it would move the set's version, and the editor that just saved a
     * card would then have its next save of the set's details refused as stale.
     */
    @Override
    @Transactional
    public PersonalCardResult addCard(UUID setId, PersonalCardCommand command) {
        FlashcardSet set = requireOwnSet(setId, userDirectory.requireCurrentUserId());
        int orderNo = flashcardRepo.findMaxOrderNo(set.getId()).orElse(0) + 1;
        Flashcard card = flashcardRepo.save(Flashcard.personal(set.getId(), orderNo, command));
        return PersonalCardResult.of(card);
    }

    @Override
    @Transactional
    public PersonalCardResult updateCard(UUID setId, UUID cardId, PersonalCardCommand command) {
        FlashcardSet set = requireOwnSet(setId, userDirectory.requireCurrentUserId());
        Flashcard card = requireCardIn(set, cardId);
        card.revise(command);
        return PersonalCardResult.of(card);
    }

    @Override
    @Transactional
    public void deleteCard(UUID setId, UUID cardId) {
        FlashcardSet set = requireOwnSet(setId, userDirectory.requireCurrentUserId());
        flashcardRepo.delete(requireCardIn(set, cardId));
    }

    /**
     * Everyone chosen must be a friend now. Checked against one list rather than per person, so a set shared with
     * thirty friends is one query, not thirty.
     */
    private void requireFriends(UUID ownerId, Set<UUID> chosen) {
        if (chosen.isEmpty()) {
            return;
        }
        if (!friendshipReader.friendIdsOf(ownerId).containsAll(chosen)) {
            throw new BadRequestException("FLASHCARD_SHARE_NOT_FRIEND", "A set can only be shared with your friends");
        }
    }

    private void replaceShares(UUID setId, Set<UUID> friendIds, Instant now) {
        flashcardSetShareRepo.deleteBySetId(setId);
        flashcardSetShareRepo.saveAll(friendIds.stream().map(id -> FlashcardSetShare.of(setId, id, now)).toList());
    }

    private PersonalSetResult resultOf(FlashcardSet set) {
        List<UUID> sharedWith = set.getVisibility() == FlashcardSetVisibility.SELECTED_FRIENDS
                ? currentFriendsAmong(set.getCreatedByUserId(), flashcardSetShareRepo.findUserIdsBySetId(set.getId()))
                : List.of();
        Map<UUID, PublicProfileReader.PublicProfile> profiles = sharedWith.isEmpty() ? Map.of()
                : publicProfileReader.profiles(sharedWith);

        return new PersonalSetResult(set.getId(), set.getName(), set.getDescription(), set.getTopic(),
                set.getTargetLevel(), set.getVisibility(), set.getVersion(),
                sharedWith.stream().filter(profiles::containsKey).map(profiles::get)
                        .map(p -> new PersonalSetResult.Person(p.id(), p.displayName(), p.avatarUrl())).toList(),
                flashcardRepo.findByFlashcardSetIdOrderByOrderNo(set.getId()).stream().map(PersonalCardResult::of)
                        .toList());
    }

    private List<UUID> currentFriendsAmong(UUID ownerId, List<UUID> shared) {
        if (shared.isEmpty()) {
            return List.of();
        }
        Set<UUID> friends = friendshipReader.friendIdsOf(ownerId);
        return shared.stream().filter(friends::contains).toList();
    }

    private FlashcardSet requireOwnSet(UUID setId, UUID ownerId) {
        return flashcardSetRepo.findById(setId).filter(set -> set.isOwnedBy(ownerId))
                .orElseThrow(() -> new NotFoundException("FLASHCARD_SET_NOT_FOUND",
                        "No flashcard set of yours with id %s".formatted(setId)));
    }

    private Flashcard requireCardIn(FlashcardSet set, UUID cardId) {
        return flashcardRepo.findById(cardId).filter(card -> card.getFlashcardSetId().equals(set.getId()))
                .orElseThrow(() -> new NotFoundException("FLASHCARD_NOT_FOUND",
                        "No flashcard with id %s in this set".formatted(cardId)));
    }

    private static Set<UUID> distinct(List<UUID> ids) {
        return new LinkedHashSet<>(ids);
    }
}
