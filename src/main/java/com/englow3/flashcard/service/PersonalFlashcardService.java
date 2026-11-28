package com.englow3.flashcard.service;

import java.util.UUID;

import com.englow3.flashcard.dto.command.PersonalCardCommand;
import com.englow3.flashcard.dto.command.SavePersonalSetCommand;
import com.englow3.flashcard.dto.result.PersonalCardResult;
import com.englow3.flashcard.dto.result.PersonalSetResult;

/**
 * A learner's own sets, as their owner manages them. Studying one goes through {@link FlashcardService} like any other
 * set; this contract is only the editor. Every method answers "not found" for a set the caller does not own, whether or
 * not they can see it, so an id never tells anyone more than they could already study.
 */
public interface PersonalFlashcardService {

    PersonalSetResult createSet(SavePersonalSetCommand command);

    PersonalSetResult ownSet(UUID setId);

    PersonalSetResult updateSet(UUID setId, SavePersonalSetCommand command);

    void deleteSet(UUID setId);

    PersonalCardResult addCard(UUID setId, PersonalCardCommand command);

    PersonalCardResult updateCard(UUID setId, UUID cardId, PersonalCardCommand command);

    void deleteCard(UUID setId, UUID cardId);
}
