package com.englow3.chat.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.chat.dto.projection.ConversationRow;
import com.englow3.chat.dto.result.ConversationSummaryResult;
import com.englow3.chat.dto.result.ConversationSummaryResult.LastMessage;
import com.englow3.chat.dto.result.ConversationSummaryResult.OtherUser;
import com.englow3.chat.dto.result.OpenConversationOutcome;
import com.englow3.chat.entity.ChatConversation;
import com.englow3.chat.entity.ChatMessage;
import com.englow3.chat.query.ChatConversationListQuery;
import com.englow3.chat.repository.ChatConversationRepository;
import com.englow3.chat.repository.ChatMessageRepository;
import com.englow3.chat.repository.ChatMessageRepository.ImageKeyRow;
import com.englow3.chat.repository.ChatParticipantRepository;
import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatReadAdvanced;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Opens conversations, lists them and moves read marks. Whether two people may talk is asked of the friendship module
 * on every call that needs it and never remembered, so unfriending or blocking takes effect on the next request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatConversationServiceImpl implements ChatConversationService {

    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final PublicProfileReader publicProfileReader;
    private final ChatConversationRepository conversationRepo;
    private final ChatParticipantRepository participantRepo;
    private final ChatMessageRepository messageRepo;
    private final ChatConversationListQuery listQuery;
    private final ObjectStorageClient objectStorage;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Value("${app.storage.chat-bucket}")
    private String chatBucket;

    /** What one cleanup transaction removed: how many conversations, and the images that belonged to them. */
    private record RemovedBatch(int conversations, List<String> imageKeys) {
    }

    @Override
    @Transactional
    public OpenConversationOutcome open(UUID friendId) {
        UUID me = userDirectory.requireCurrentUserId();
        if (friendId.equals(me)) {
            throw new BadRequestException("CHAT_SELF", "You cannot chat with yourself");
        }
        requireFriends(me, friendId);

        Instant now = Instant.now(clock);
        String key = ChatConversation.directKey(me, friendId);
        // One statement that either creates the pair's conversation or finds it there: two friends opening a chat at
        // the same moment wait on the unique index and the second gets 0, so there is never a second conversation.
        boolean created = conversationRepo.insertIfAbsent(UUID.randomUUID(), key, now) == 1;
        ChatConversation conversation = conversationRepo.findByDirectKey(key)
                .orElseThrow(() -> new IllegalStateException("The conversation for " + key + " vanished"));
        if (created) {
            participantRepo.insertIfAbsent(conversation.getId(), me, now);
            participantRepo.insertIfAbsent(conversation.getId(), friendId, now);
        }
        return new OpenConversationOutcome(summaryOf(conversation, me, friendId, true), created);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationSummaryResult> list(Pageable page) {
        UUID me = userDirectory.requireCurrentUserId();
        List<ConversationRow> rows = listQuery.list(me, page.getPageSize(), page.getOffset());

        // One call for the whole page. A null is a person who erased their account: no profile to look up.
        Set<UUID> others = rows.stream().map(ConversationRow::otherUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PublicProfile> profiles = others.isEmpty() ? Map.of() : publicProfileReader.profiles(others);

        List<ConversationSummaryResult> content = rows.stream().map(row -> summaryOf(row, profiles)).toList();
        return PageableExecutionUtils.getPage(content, page, () -> listQuery.count(me));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationSummaryResult get(UUID conversationId) {
        UUID me = userDirectory.requireCurrentUserId();
        requireParticipant(conversationId, me);
        ChatConversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> notFound(conversationId));
        UUID other = participantRepo.findOtherParticipant(conversationId, me).orElse(null);
        boolean canSend = other != null && friendshipReader.areFriends(me, other);
        return summaryOf(conversation, me, other, canSend);
    }

    @Override
    @Transactional
    public void markRead(UUID conversationId, long upToSeq) {
        if (upToSeq < 0) {
            throw new BadRequestException("CHAT_READ_SEQ_INVALID", "upToSeq cannot be negative");
        }
        UUID me = userDirectory.requireCurrentUserId();
        // Also the participant check: there is a mark only for someone in the conversation.
        long before = participantRepo.findReadMark(conversationId, me).orElseThrow(() -> notFound(conversationId));

        Optional<Long> stored = participantRepo.advanceReadMark(conversationId, me, upToSeq);
        if (stored.isEmpty() || stored.get() <= before) {
            // Already at or past it, or two tabs wrote at once: nothing moved, so there is nothing to announce.
            return;
        }
        UUID other = participantRepo.findOtherParticipant(conversationId, me).orElse(null);
        events.publishEvent(
                new ChatReadAdvanced(conversationId, me, stored.get(), other == null ? Set.of() : Set.of(other)));
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadConversationCount() {
        return listQuery.unreadConversationCount(userDirectory.requireCurrentUserId());
    }

    /**
     * A conversation nobody is left in is unreachable, and so are its messages and images. Each batch is one
     * transaction in which the delete is conditional ("still has nobody in it") and says which conversations it
     * removed, so two sweeps at once never fail on one another and only the sweep whose delete took effect removes the
     * images. The objects are deleted after the batch commits, because the row is what anyone reads and a file left
     * behind is only a stray in the bucket.
     */
    @Override
    public int removeEmptyConversations() {
        int removed = 0;
        while (true) {
            RemovedBatch batch = transactions.execute(tx -> {
                List<UUID> candidates = conversationRepo.findEmpty();
                if (candidates.isEmpty()) {
                    return new RemovedBatch(0, List.of());
                }
                // Read before the delete: the messages go with the conversation, and their keys with them.
                List<ImageKeyRow> images = messageRepo.findImageKeys(candidates);
                Set<UUID> deleted = new HashSet<>(conversationRepo.deleteIfEmpty(candidates));
                List<String> imageKeys = images.stream().filter(image -> deleted.contains(image.getConversationId()))
                        .map(ImageKeyRow::getImageObjectKey).toList();
                return new RemovedBatch(deleted.size(), imageKeys);
            });
            if (batch.conversations() == 0) {
                // Nothing left, or another instance took what was left; the next sweep looks again.
                return removed;
            }
            removed += batch.conversations();
            batch.imageKeys().forEach(this::removeObject);
        }
    }

    private void requireFriends(UUID me, UUID other) {
        if (!friendshipReader.areFriends(me, other)) {
            throw new ForbiddenException("CHAT_NOT_FRIENDS", "You can only chat with friends");
        }
    }

    private void requireParticipant(UUID conversationId, UUID me) {
        if (!participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)) {
            throw notFound(conversationId);
        }
    }

    private static NotFoundException notFound(UUID conversationId) {
        // The same answer for a conversation that does not exist and one the caller is not in.
        return new NotFoundException("CHAT_CONVERSATION_NOT_FOUND",
                "No conversation with id %s".formatted(conversationId));
    }

    private void removeObject(String key) {
        try {
            objectStorage.delete(chatBucket, key);
        } catch (RuntimeException failure) {
            // The rows are gone and nobody can read them; a file left behind is a stray in the bucket, not an error.
            log.warn("Could not remove chat image {} of a conversation nobody is in", key, failure);
        }
    }

    /** One conversation worked out from its own rows: the caller's mark, the other person, the last message. */
    private ConversationSummaryResult summaryOf(ChatConversation conversation, UUID me, UUID other, boolean canSend) {
        PublicProfile profile = other == null ? null : publicProfileReader.profiles(Set.of(other)).get(other);
        long lastReadSeq = participantRepo.findReadMark(conversation.getId(), me).orElse(0L);
        ChatMessage last = conversation.getLastSeq() == 0 ? null
                : messageRepo.findByConversationIdAndSeqLessThanOrderBySeqDesc(conversation.getId(), Long.MAX_VALUE,
                        PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        LastMessage lastMessage = last == null ? null
                : new LastMessage(last.getSeq(), last.getSenderId(), last.preview(), last.getImageObjectKey() != null,
                        last.isRecalled(), last.getSentAt());
        return new ConversationSummaryResult(conversation.getId(), otherUser(profile), conversation.getLastSeq(),
                lastReadSeq, unread(conversation.getLastSeq(), lastReadSeq), lastMessage, canSend);
    }

    /** One row of the list. No {@code canSend}: that is a friendship call per person, which a page must not make. */
    private static ConversationSummaryResult summaryOf(ConversationRow row, Map<UUID, PublicProfile> profiles) {
        PublicProfile profile = row.otherUserId() == null ? null : profiles.get(row.otherUserId());
        LastMessage lastMessage = row.lastMessageSeq() == null ? null
                : new LastMessage(row.lastMessageSeq(), row.lastSenderId(), row.lastBodyPreview(), row.lastHasImage(),
                        row.lastRecalled(), row.lastSentAt());
        return new ConversationSummaryResult(row.id(), otherUser(profile), row.lastSeq(), row.lastReadSeq(),
                unread(row.lastSeq(), row.lastReadSeq()), lastMessage, null);
    }

    /** No profile means the other person's account is gone, whether the participant row was already or not yet. */
    private static OtherUser otherUser(PublicProfile profile) {
        return profile == null ? null : new OtherUser(profile.id(), profile.displayName(), profile.avatarUrl());
    }

    /** Never negative: a mark can sit at the last seq but not past it. */
    private static long unread(long lastSeq, long lastReadSeq) {
        return Math.max(0, lastSeq - lastReadSeq);
    }
}
