package com.englow3.chat.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.chat.dto.command.StartImageUploadCommand;
import com.englow3.chat.dto.result.ChatImageUploadTicket;
import com.englow3.chat.entity.ChatImageUpload;
import com.englow3.chat.helper.ChatImages;
import com.englow3.chat.repository.ChatImageUploadRepository;
import com.englow3.chat.repository.ChatParticipantRepository;
import com.englow3.chat.service.ChatImageService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hands out upload URLs for chat images and sweeps the ones nobody sent. The upload row is what lets the sweep find an
 * image that never became a message: without it a learner could put any number of files in the bucket and never send
 * one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatImageServiceImpl implements ChatImageService {

    /** Each start signs a URL for up to 5 MB, so a script must not be able to ask for them without end. */
    static final int MAX_STARTS_PER_WINDOW = 20;
    static final Duration START_WINDOW = Duration.ofMinutes(10);

    /** Long past the minutes an upload URL lasts, so nothing still in progress is ever swept. */
    static final Duration ABANDONED_UPLOAD_AGE = Duration.ofHours(24);

    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final ChatParticipantRepository participantRepo;
    private final ChatImageUploadRepository imageUploadRepo;
    private final ObjectStorageClient objectStorage;
    private final UserRateLimiter rateLimiter;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Value("${app.storage.chat-bucket}")
    private String chatBucket;

    @Value("${app.storage.chat-upload-url-ttl:PT10M}")
    private Duration uploadUrlTtl;

    @Override
    @Transactional
    public ChatImageUploadTicket startUpload(StartImageUploadCommand command) {
        ChatImages.requireAccepted(command.contentType(), command.contentLength());

        UUID me = userDirectory.requireCurrentUserId();
        UUID conversationId = command.conversationId();
        if (!participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)) {
            throw new NotFoundException("CHAT_CONVERSATION_NOT_FOUND",
                    "No conversation with id %s".formatted(conversationId));
        }
        // No point in an image the sender could not send: the same test a message passes, asked now rather than at
        // send.
        UUID other = participantRepo.findOtherParticipant(conversationId, me).orElse(null);
        if (other == null || !friendshipReader.areFriends(me, other)) {
            throw new ForbiddenException("CHAT_NOT_FRIENDS", "You can only chat with friends");
        }
        rateLimiter.check(me, "chat-image-upload", MAX_STARTS_PER_WINDOW, START_WINDOW);

        UUID uploadId = UUID.randomUUID();
        String objectKey = ChatImages.objectKey(conversationId, uploadId, command.contentType());
        imageUploadRepo.save(ChatImageUpload.issue(uploadId, conversationId, me, objectKey, command.contentType(),
                command.contentLength(), Instant.now(clock)));
        // Signing is arithmetic on the credentials, not a request to the store, so it is safe inside the transaction.
        String uploadUrl = objectStorage
                .presignPut(chatBucket, objectKey, command.contentType(), command.contentLength(), uploadUrlTtl)
                .toString();

        return new ChatImageUploadTicket(uploadId, uploadUrl, command.contentType(), uploadUrlTtl.getSeconds());
    }

    /**
     * An upload still unclaimed a day after its URL was issued will never be: the URL lasted minutes, and the screen
     * sends as soon as the upload finishes. Each row is deleted in its own short transaction, and only whoever deletes
     * it removes the object, so two instances sweeping at once cannot both claim one, and neither can race a send that
     * is consuming it. The object is deleted after the row is gone, outside any transaction.
     */
    @Override
    public int removeAbandonedUploads() {
        Instant cutoff = Instant.now(clock).minus(ABANDONED_UPLOAD_AGE);
        int removed = 0;
        while (true) {
            List<ChatImageUpload> stale = imageUploadRepo.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(cutoff);
            int removedInBatch = 0;
            for (ChatImageUpload upload : stale) {
                Integer deleted = transactions.execute(tx -> imageUploadRepo.deleteByIdReturningCount(upload.getId()));
                if (deleted == null || deleted == 0) {
                    continue;
                }
                removedInBatch++;
                try {
                    objectStorage.delete(chatBucket, upload.getObjectKey());
                } catch (RuntimeException failure) {
                    // The row is what anyone reads; a file left behind is a stray in the bucket, not a broken upload.
                    log.warn("Could not remove the object of abandoned chat upload {}", upload.getId(), failure);
                }
            }
            removed += removedInBatch;
            if (removedInBatch == 0) {
                return removed;
            }
        }
    }
}
