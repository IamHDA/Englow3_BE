package com.englow3.blog.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.services.s3.model.S3Exception;

import com.englow3.blog.dto.command.ListPostsCommand;
import com.englow3.blog.dto.command.SavePostCommand;
import com.englow3.blog.dto.projection.PostListFilter;
import com.englow3.blog.dto.projection.PostSummaryRow;
import com.englow3.blog.dto.projection.PostTagRow;
import com.englow3.blog.dto.result.BlogAuthorResult;
import com.englow3.blog.dto.result.BlogPostDetailResult;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.BlogTagCatalogue;
import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.entity.BlogCertificate;
import com.englow3.blog.entity.BlogCommentStatus;
import com.englow3.blog.entity.BlogLevel;
import com.englow3.blog.entity.BlogModerationAction;
import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostImage;
import com.englow3.blog.entity.BlogPostSort;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.entity.BlogSkill;
import com.englow3.blog.entity.BlogTag;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.helper.BlogImages;
import com.englow3.blog.helper.BlogMarkdown;
import com.englow3.blog.query.BlogPostListQuery;
import com.englow3.blog.repository.BlogCommentRepository;
import com.englow3.blog.repository.BlogModerationActionRepository;
import com.englow3.blog.repository.BlogPostImageRepository;
import com.englow3.blog.repository.BlogPostLikeRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.service.BlogPostService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.ObjectStorageClient.StoredObjectMetadata;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Posts: writing, editing, deleting, reading and listing.
 * <p>
 * Saving a post that has images is several steps because one of them asks object storage, which must never happen
 * inside a transaction: read the post and its images to decide (a short transaction), ask the bucket whether the new
 * images were really uploaded (none), then write (a short transaction). Every path that removes images - an edit that
 * drops one, deleting the post - collects the object keys inside the transaction and deletes the objects after it
 * commits, because the rows go with the post by cascade and the objects never do. The rules about a post live in
 * {@link BlogPost}; what is here is the order things happen in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlogPostServiceImpl implements BlogPostService {

    /** A person writes a post every few minutes at most; a script or a stuck retry loop does not. */
    static final int MAX_POSTS_PER_WINDOW = 5;
    static final Duration POST_WINDOW = Duration.ofMinutes(10);

    /**
     * Twenty saves in ten minutes is more than anyone edits by hand; more is a script, or an editor stuck in a loop.
     */
    static final int MAX_UPDATES_PER_WINDOW = 20;
    static final Duration UPDATE_WINDOW = Duration.ofMinutes(10);

    static final int MAX_IMAGES_PER_POST = 10;
    static final int MIN_SEARCH_TERM_LENGTH = 2;
    static final int MAX_PAGE_SIZE = 50;

    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final PublicProfileReader publicProfileReader;
    private final BlogReadRule readRule;
    private final BlogPostRepository postRepo;
    private final BlogCommentRepository commentRepo;
    private final BlogPostImageRepository imageRepo;
    private final BlogPostLikeRepository likeRepo;
    private final BlogModerationActionRepository actionRepo;
    private final BlogPostListQuery listQuery;
    private final ObjectStorageClient objectStorage;
    private final BlogImageLinks imageLinks;
    private final UserRateLimiter rateLimiter;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Value("${app.storage.blog-bucket}")
    private String blogBucket;

    @Override
    public BlogPostDetailResult create(SavePostCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-post", MAX_POSTS_PER_WINDOW, POST_WINDOW);

        Set<BlogTag> tags = tagsOf(command);
        List<UUID> referenced = referencedImages(command.body());
        List<BlogPostImage> unattached = transactions.execute(tx -> checkImages(referenced, me, null));
        requireUploaded(unattached);

        UUID postId = transactions.execute(tx -> {
            // Flushed now: attaching an image names the post's id, and the post must be in the table first.
            BlogPost post = postRepo
                    .saveAndFlush(BlogPost.publish(me, command.title(), command.body(), tags, Instant.now(clock)));
            attach(post.getId(), unattached, me);
            return post.getId();
        });
        return transactions.execute(tx -> read(postId, me));
    }

    @Override
    public BlogPostDetailResult update(UUID postId, SavePostCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-post-update", MAX_UPDATES_PER_WINDOW, UPDATE_WINDOW);

        Set<BlogTag> tags = tagsOf(command);
        List<UUID> referenced = referencedImages(command.body());
        List<BlogPostImage> unattached = transactions.execute(tx -> {
            requireCurrentVersion(readablePost(postId, me), command.expectedVersion());
            return checkImages(referenced, me, postId);
        });
        requireUploaded(unattached);

        List<String> removedKeys = transactions.execute(tx -> {
            // Loaded again inside the write: anything that changed the post since the read above is seen here, and the
            // version the database holds is what Hibernate's own check is made against when this commits.
            BlogPost post = postRepo.findWithTagsById(postId).orElseThrow(() -> postNotFound(postId));
            requireCurrentVersion(post, command.expectedVersion());
            post.edit(me, command.title(), command.body(), tags, Instant.now(clock));

            List<BlogPostImage> before = imageRepo.findByPostId(postId);
            attach(postId, unattached, me);
            List<BlogPostImage> dropped = before.stream().filter(image -> !referenced.contains(image.getId())).toList();
            List<String> keys = dropped.stream().map(BlogPostImage::getObjectKey).toList();
            imageRepo.deleteAll(dropped);
            return keys;
        });
        deleteObjects(removedKeys);
        return transactions.execute(tx -> read(postId, me));
    }

    @Override
    public void delete(UUID postId) {
        UUID me = userDirectory.requireCurrentUserId();

        List<String> keys = transactions.execute(tx -> {
            // The first and only read of the post, and it locks the row like a moderator's action does: the delete
            // then waits for an action in progress and removes the post as it is. Left to the version check alone, a
            // delete that had read the post before a hide was committed would fail on it as CONCURRENT_UPDATE, for the
            // author to repeat.
            // The read rule decides what a caller who may not read the post is told: a post that does not exist for
            // them is not a 403 that would say it does. Another person's post they can read is the entity's to refuse;
            // their own goes in any status.
            BlogPost post = postRepo.findByIdForUpdate(postId).filter(found -> readRule.canRead(found, me))
                    .orElseThrow(() -> postNotFound(postId));
            post.ensureDeletableBy(me);
            // Read before the post goes: its images' rows go with it, by cascade, and the keys with them.
            List<String> imageKeys = imageRepo.findKeysByPostId(postId);
            postRepo.delete(post);
            return imageKeys;
        });
        deleteObjects(keys);
    }

    @Override
    @Transactional(readOnly = true)
    public BlogPostDetailResult get(UUID postId) {
        return read(postId, userDirectory.requireCurrentUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BlogPostSummaryResult> list(ListPostsCommand command, Pageable page) {
        UUID me = userDirectory.requireCurrentUserId();
        Set<UUID> hidden = friendshipReader.blockedEitherWay(me);
        String titleTerm = titleTerm(command.q());

        int size = Math.max(1, Math.min(page.getPageSize(), MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(page.getPageNumber(), size);
        // An offset past what an int can hold is past any table: the empty page it gets is the right answer.
        int offset = (int) Math.min(pageable.getOffset(), Integer.MAX_VALUE);
        BlogPostSort sort = command.sort() == null ? BlogPostSort.NEWEST : command.sort();
        PostListFilter filter = new PostListFilter(me, hidden, titleTerm, command.skill(), command.level(),
                command.certificate(), command.authorId(), sort, size, offset);

        List<PostSummaryRow> rows = listQuery.list(filter);
        Set<UUID> postIds = rows.stream().map(PostSummaryRow::id).collect(Collectors.toSet());
        Map<UUID, List<BlogTagResult>> tags = listQuery.tagsOf(postIds).stream()
                .collect(Collectors.groupingBy(PostTagRow::postId,
                        Collectors.mapping(row -> new BlogTagResult(row.kind(), row.value()), Collectors.toList())));
        // One call for the whole page. A null author is a person who erased their account: no profile to look up.
        Set<UUID> authorIds = rows.stream().map(PostSummaryRow::authorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PublicProfile> profiles = authorIds.isEmpty() ? Map.of() : publicProfileReader.profiles(authorIds);

        List<BlogPostSummaryResult> content = rows.stream()
                .map(row -> new BlogPostSummaryResult(row.id(), row.title(), BlogMarkdown.excerpt(row.bodyHead()),
                        authorOf(row.authorId(), profiles), row.status(),
                        sorted(tags.getOrDefault(row.id(), List.of())), row.likeCount(), row.commentCount(),
                        row.likedByMe(), row.createdAt(), row.editedAt()))
                .toList();
        return PageableExecutionUtils.getPage(content, pageable, () -> listQuery.count(filter));
    }

    @Override
    public BlogTagCatalogue tagCatalogue() {
        return new BlogTagCatalogue(names(BlogSkill.values()), names(BlogLevel.values()),
                names(BlogCertificate.values()));
    }

    // --- reading ---------------------------------------------------------------------------------------------------

    /** The post if {@link BlogReadRule} lets the caller read it; otherwise it is not there for them. */
    private BlogPost readablePost(UUID postId, UUID me) {
        return postRepo.findWithTagsById(postId).filter(found -> readRule.canRead(found, me))
                .orElseThrow(() -> postNotFound(postId));
    }

    /** One post in full, loaded inside the caller's transaction: the response needs nothing that is lazy. */
    private BlogPostDetailResult read(UUID postId, UUID me) {
        BlogPost post = readablePost(postId, me);
        Map<UUID, PublicProfile> profiles = post.getAuthorId() == null ? Map.of()
                : publicProfileReader.profiles(Set.of(post.getAuthorId()));
        List<BlogTagResult> tags = sorted(
                post.getTags().stream().map(tag -> new BlogTagResult(tag.getKind(), tag.getValue())).toList());
        return new BlogPostDetailResult(post.getId(), post.getTitle(), post.getBody(),
                imageLinks.of(post.getId(), post.getBody()), authorOf(post.getAuthorId(), profiles), tags,
                post.getStatus(), post.getVersion(), likeRepo.countByIdPostId(postId),
                commentRepo.countByPostIdAndStatus(postId, BlogCommentStatus.VISIBLE),
                likeRepo.existsByIdPostIdAndIdUserId(postId, me), post.isEditableBy(me),
                // A block would have made the post unreadable above, so what is left to ask is whether it is open.
                post.isOpenForInteraction(), post.getCreatedAt(), post.getEditedAt(), moderationNote(post, me));
    }

    /**
     * Why a moderator hid or removed the post, for its author and only while it is not published - the one case in
     * which this use case returns a post that is not. The last hide or removal counts: a dismissal or a restore after
     * it is not a reason the post is down. Null when there is none, e.g. a status set by hand.
     */
    private String moderationNote(BlogPost post, UUID me) {
        if (post.getStatus() == BlogPostStatus.PUBLISHED || !post.isAuthor(me)) {
            return null;
        }
        return actionRepo
                .findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(BlogTargetType.POST,
                        post.getId(), List.of(BlogModerationActionType.HIDE, BlogModerationActionType.REMOVE))
                .map(BlogModerationAction::getReason).orElse(null);
    }

    private static BlogAuthorResult authorOf(UUID authorId, Map<UUID, PublicProfile> profiles) {
        PublicProfile profile = authorId == null ? null : profiles.get(authorId);
        return profile == null ? null : new BlogAuthorResult(profile.id(), profile.displayName(), profile.avatarUrl());
    }

    private static List<BlogTagResult> sorted(List<BlogTagResult> tags) {
        return tags.stream().sorted(BlogTagResult.DISPLAY_ORDER).toList();
    }

    private static String titleTerm(String q) {
        String trimmed = q == null ? "" : q.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() < MIN_SEARCH_TERM_LENGTH) {
            throw new BadRequestException("SEARCH_TERM_TOO_SHORT",
                    "Type at least %d characters to search".formatted(MIN_SEARCH_TERM_LENGTH));
        }
        return trimmed;
    }

    private static <E extends Enum<E>> List<String> names(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    // --- saving ----------------------------------------------------------------------------------------------------

    private static Set<BlogTag> tagsOf(SavePostCommand command) {
        return command.tags().stream().map(tag -> BlogTag.of(tag.kind(), tag.value()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** The ids of the images the text names, at most {@value #MAX_IMAGES_PER_POST}. */
    private static List<UUID> referencedImages(String body) {
        List<UUID> references = BlogMarkdown.imageReferences(body);
        if (references.size() > MAX_IMAGES_PER_POST) {
            throw new BadRequestException("BLOG_TOO_MANY_IMAGES",
                    "A post may have at most %d images".formatted(MAX_IMAGES_PER_POST));
        }
        return references;
    }

    /**
     * Every image the text names must exist, be the caller's, and be either unattached or already this post's. Returns
     * the unattached ones: those are what the bucket still has to be asked about and what the write takes. Runs inside
     * a read transaction.
     */
    private List<BlogPostImage> checkImages(List<UUID> referenced, UUID me, UUID thisPostId) {
        if (referenced.isEmpty()) {
            return List.of();
        }
        List<BlogPostImage> images = imageRepo.findByIdIn(referenced);
        boolean allTheirs = images.size() == referenced.size()
                && images.stream().allMatch(image -> me.equals(image.getUploaderId())
                        && (image.getPostId() == null || image.getPostId().equals(thisPostId)));
        if (!allTheirs) {
            throw imageInvalid();
        }
        return images.stream().filter(image -> image.getPostId() == null).toList();
    }

    /**
     * Checked outside any transaction: this asks the store. Only "no such object" means the author has not uploaded;
     * anything else is the store failing, and telling them their image is missing would send them to upload it again
     * for nothing.
     */
    private void requireUploaded(List<BlogPostImage> unattached) {
        for (BlogPostImage image : unattached) {
            StoredObjectMetadata stored;
            try {
                stored = objectStorage.metadata(blogBucket, image.getObjectKey());
            } catch (S3Exception notThere) {
                if (notThere.statusCode() != 404) {
                    throw notThere;
                }
                throw new ConflictException("BLOG_IMAGE_NOT_UPLOADED",
                        "An image of this post has not been uploaded yet");
            }
            // The signature binds the size, so this holds unless the bucket was written some other way.
            BlogImages.requireAccepted(image.getContentType(), stored.contentLength());
        }
    }

    /**
     * Gives the post the images checked as unattached, and insists that it got all of them: another post may have taken
     * one since, or the sweep removed it. Failing here rolls the write back, so a post is never saved that points at an
     * image which is not its own.
     */
    private void attach(UUID postId, List<BlogPostImage> unattached, UUID me) {
        if (unattached.isEmpty()) {
            return;
        }
        List<UUID> ids = unattached.stream().map(BlogPostImage::getId).toList();
        if (imageRepo.attach(postId, ids, me) != ids.size()) {
            throw imageInvalid();
        }
    }

    private static void requireCurrentVersion(BlogPost post, Long expectedVersion) {
        if (expectedVersion == null || post.getVersion() != expectedVersion) {
            throw new ConflictException("BLOG_POST_STALE", "The post changed since you opened it");
        }
    }

    /** After the commit, outside any transaction: a file that will not go is a stray in the bucket, nothing worse. */
    private void deleteObjects(Collection<String> keys) {
        for (String key : keys) {
            try {
                objectStorage.delete(blogBucket, key);
            } catch (RuntimeException failure) {
                log.warn("Could not remove the object {} of a blog image that is gone", key, failure);
            }
        }
    }

    private static BadRequestException imageInvalid() {
        return new BadRequestException("BLOG_IMAGE_INVALID", "An image in this post is not one of yours");
    }

    private static NotFoundException postNotFound(UUID postId) {
        return new NotFoundException("BLOG_POST_NOT_FOUND", "No post with id %s".formatted(postId));
    }
}
