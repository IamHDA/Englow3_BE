package com.englow3.chat.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.services.s3.model.S3Exception;

import com.englow3.chat.dto.command.SendMessageCommand;
import com.englow3.chat.dto.result.ChatMessageResult;
import com.englow3.chat.dto.result.SendMessageOutcome;
import com.englow3.chat.entity.ChatConversation;
import com.englow3.chat.entity.ChatImageUpload;
import com.englow3.chat.entity.ChatMessage;
import com.englow3.chat.helper.ChatImages;
import com.englow3.chat.repository.ChatConversationRepository;
import com.englow3.chat.repository.ChatImageUploadRepository;
import com.englow3.chat.repository.ChatMessageRepository;
import com.englow3.chat.repository.ChatParticipantRepository;
import com.englow3.chat.service.ChatMessageRecalled;
import com.englow3.chat.service.ChatMessageSent;
import com.englow3.chat.service.ChatMessageService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.ObjectStorageClient.StoredObjectMetadata;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sending, history and recall.
 * <p>
 * A send is three steps because one of them talks to object storage, which must never happen inside a transaction: read
 * what is needed to decide (a short transaction), check the image is really in the bucket (no transaction), then write
 * (a short transaction that takes the conversation's row lock first, so seq order is commit order). The rules about a
 * message live in {@link ChatMessage}; what is here is the order things happen in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageServiceImpl implements ChatMessageService {

    /** A person types well under this; a script or a stuck retry loop does not. */
    static final int MAX_SENDS_PER_WINDOW = 60;
    static final Duration SEND_WINDOW = Duration.ofMinutes(1);

    static final int MAX_HISTORY_LIMIT = 100;

    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final ChatConversationRepository conversationRepo;
    private final ChatParticipantRepository participantRepo;
    private final ChatMessageRepository messageRepo;
    private final ChatImageUploadRepository imageUploadRepo;
    private final ObjectStorageClient objectStorage;
    private final PresignedUrlResolver presignedUrls;
    private final UserRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Value("${app.storage.chat-bucket}")
    private String chatBucket;

    @Value("${app.storage.chat-image-url-ttl:PT1H}")
    private Duration imageUrlTtl;

    /** What the first transaction found: the message this {@code clientId} already sent, or the image to check. */
    private record Preparation(ChatMessage existing, ChatImageUpload upload) {
    }

    private record Written(ChatMessage message, boolean created) {
    }

    private record Recalled(ChatMessage message, String removedImageKey) {
    }

    @Override
    public SendMessageOutcome send(SendMessageCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "chat-send", MAX_SENDS_PER_WINDOW, SEND_WINDOW);

        Preparation preparation = transactions.execute(tx -> prepare(me, command));
        if (preparation.existing() != null) {
            // A retry of a message that was already sent: hand back the first one and change nothing.
            return new SendMessageOutcome(result(preparation.existing(), me), false);
        }
        if (preparation.upload() != null) {
            requireImageUploaded(preparation.upload());
        }
        String imageKey = preparation.upload() == null ? null : preparation.upload().getObjectKey();

        Written written = transactions.execute(tx -> write(me, command, preparation.upload(), imageKey));
        return new SendMessageOutcome(result(written.message(), me), written.created());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResult> history(UUID conversationId, Long afterSeq, Long beforeSeq, int limit) {
        if (afterSeq != null && beforeSeq != null) {
            throw new BadRequestException("CHAT_HISTORY_CURSOR_CONFLICT",
                    "Ask for messages after a seq or before one, not both");
        }
        UUID me = userDirectory.requireCurrentUserId();
        requireParticipant(conversationId, me);

        Pageable page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_HISTORY_LIMIT)));
        List<ChatMessage> messages;
        if (afterSeq != null) {
            messages = messageRepo.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(conversationId, afterSeq, page);
        } else {
            // The newest page, or the one before a message already shown: fetched newest first so the limit keeps the
            // closest ones, then turned around so every answer reads in ascending seq order.
            messages = new ArrayList<>(messageRepo.findByConversationIdAndSeqLessThanOrderBySeqDesc(conversationId,
                    beforeSeq == null ? Long.MAX_VALUE : beforeSeq, page));
            Collections.reverse(messages);
        }
        return messages.stream().map(message -> result(message, me)).toList();
    }

    @Override
    public ChatMessageResult recall(UUID messageId) {
        UUID me = userDirectory.requireCurrentUserId();

        Recalled recalled = transactions.execute(tx -> {
            ChatMessage message = messageRepo.findById(messageId).orElseThrow(() -> messageNotFound(messageId));
            UUID conversationId = message.getConversationId();
            if (!participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)) {
                // Someone else's message looks exactly like one that does not exist.
                throw messageNotFound(messageId);
            }
            String removedImageKey = message.recall(me, Instant.now(clock));
            UUID other = participantRepo.findOtherParticipant(conversationId, me).orElse(null);
            events.publishEvent(new ChatMessageRecalled(conversationId, messageId, message.getSeq(),
                    other == null ? Set.of() : Set.of(other)));
            return new Recalled(message, removedImageKey);
        });

        if (recalled.removedImageKey() != null) {
            try {
                objectStorage.delete(chatBucket, recalled.removedImageKey());
            } catch (RuntimeException failure) {
                // The message is already recalled and its link no longer exists; a leftover file is only a stray.
                log.warn("Could not remove the image of recalled chat message {}", messageId, failure);
            }
        }
        return result(recalled.message(), me);
    }

    /** Reads what the send needs to decide, in one short transaction. Nothing here locks or writes. */
    private Preparation prepare(UUID me, SendMessageCommand command) {
        UUID conversationId = command.conversationId();
        Optional<ChatMessage> existing = messageRepo.findBySenderIdAndClientId(me, command.clientId());
        if (existing.isPresent()) {
            return new Preparation(requireSameConversation(existing.get(), conversationId), null);
        }
        requireParticipant(conversationId, me);
        ChatImageUpload upload = null;
        if (command.imageUploadId() != null) {
            upload = imageUploadRepo.findByIdAndUploaderIdAndConversationId(command.imageUploadId(), me, conversationId)
                    .orElseThrow(ChatMessageServiceImpl::uploadNotFound);
        }
        return new Preparation(null, upload);
    }

    /**
     * The write. The conversation's row is locked first and held to commit, so two sends at once take their seq one
     * after the other and seq order is commit order; everything that can refuse the send is checked under that lock.
     */
    private Written write(UUID me, SendMessageCommand command, ChatImageUpload upload, String imageKey) {
        UUID conversationId = command.conversationId();
        ChatConversation conversation = conversationRepo.findByIdForUpdate(conversationId)
                .orElseThrow(() -> conversationNotFound(conversationId));

        // Two requests with one clientId at the same moment: the second waited for the lock, so it sees the first.
        Optional<ChatMessage> already = messageRepo.findBySenderIdAndClientId(me, command.clientId());
        if (already.isPresent()) {
            return new Written(requireSameConversation(already.get(), conversationId), false);
        }
        UUID other = participantRepo.findOtherParticipant(conversationId, me)
                .orElseThrow(() -> new ConflictException("CHAT_PARTNER_GONE", "This person is no longer on Englow"));
        if (!friendshipReader.areFriends(me, other)) {
            throw new ForbiddenException("CHAT_NOT_FRIENDS", "You can only chat with friends");
        }
        if (upload != null && imageUploadRepo.deleteByIdReturningCount(upload.getId()) == 0) {
            // Used by another send, or swept, since it was read.
            throw uploadNotFound();
        }

        Instant now = Instant.now(clock);
        // An empty or over-long message is refused by the entity after the seq was taken in memory; the transaction
        // rolls back, so last_seq does not move.
        long seq = conversation.nextSeq(now);
        ChatMessage message = messageRepo
                .save(ChatMessage.send(conversationId, seq, me, command.clientId(), command.body(), imageKey, now));
        participantRepo.advanceReadMark(conversationId, me, seq);
        events.publishEvent(new ChatMessageSent(conversationId, message.getId(), seq, me, Set.of(other)));
        return new Written(message, true);
    }

    /**
     * Checked outside any transaction: this asks the store. Only "no such object" means the sender has not uploaded;
     * anything else is the store failing, and telling the learner their image is missing would send them to upload it
     * again for nothing.
     */
    private void requireImageUploaded(ChatImageUpload upload) {
        StoredObjectMetadata stored;
        try {
            stored = objectStorage.metadata(chatBucket, upload.getObjectKey());
        } catch (S3Exception notThere) {
            if (notThere.statusCode() != 404) {
                throw notThere;
            }
            throw new ConflictException("CHAT_IMAGE_NOT_UPLOADED", "The image has not been uploaded yet");
        }
        // The signature binds the size, so this holds unless the bucket was written some other way.
        ChatImages.requireAccepted(upload.getContentType(), stored.contentLength());
    }

    private ChatMessageResult result(ChatMessage message, UUID viewerId) {
        String imageUrl = presignedUrls.resolve(chatBucket, message.getImageObjectKey(), imageUrlTtl);
        return ChatMessageResult.of(message, imageUrl, viewerId);
    }

    private void requireParticipant(UUID conversationId, UUID me) {
        if (!participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)) {
            throw conversationNotFound(conversationId);
        }
    }

    /** A clientId names one message of one conversation: sending it to another is a client bug, not a retry. */
    private static ChatMessage requireSameConversation(ChatMessage message, UUID conversationId) {
        if (!message.getConversationId().equals(conversationId)) {
            throw new ConflictException("CHAT_CLIENT_ID_REUSED",
                    "This clientId was already used in another conversation");
        }
        return message;
    }

    private static NotFoundException conversationNotFound(UUID conversationId) {
        return new NotFoundException("CHAT_CONVERSATION_NOT_FOUND",
                "No conversation with id %s".formatted(conversationId));
    }

    private static NotFoundException messageNotFound(UUID messageId) {
        return new NotFoundException("CHAT_MESSAGE_NOT_FOUND", "No message with id %s".formatted(messageId));
    }

    private static NotFoundException uploadNotFound() {
        return new NotFoundException("CHAT_IMAGE_UPLOAD_NOT_FOUND",
                "This image upload does not exist or was already used");
    }
}
