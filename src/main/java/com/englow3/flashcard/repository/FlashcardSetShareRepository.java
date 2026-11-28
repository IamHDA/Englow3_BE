package com.englow3.flashcard.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.flashcard.entity.FlashcardSetShare;
import com.englow3.flashcard.entity.FlashcardSetShareId;

public interface FlashcardSetShareRepository extends JpaRepository<FlashcardSetShare, FlashcardSetShareId> {

    @Query("select s.id.userId from FlashcardSetShare s where s.id.flashcardSetId = :setId order by s.createdAt, s.id.userId")
    List<UUID> findUserIdsBySetId(@Param("setId") UUID setId);

    /** Flushed first so a replace (delete, then insert the new list) never collides with rows still pending. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from FlashcardSetShare s where s.id.flashcardSetId = :setId")
    int deleteBySetId(@Param("setId") UUID setId);
}
