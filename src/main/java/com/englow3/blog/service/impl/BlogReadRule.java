package com.englow3.blog.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.englow3.blog.entity.BlogPost;
import com.englow3.friendship.api.FriendshipReader;

import lombok.RequiredArgsConstructor;

/**
 * The one read rule of the blog, asked by every service that opens, lists under, edits, comments on, likes, reports or
 * deletes a post: a post the viewer may not read does not exist for them. Hidden and removed posts are read only by
 * their author, and a post by someone the viewer has a block with, in either direction, is not read at all. What a
 * service answers when the rule refuses is its own - always the "not found" of what was asked for, so neither the
 * moderation state nor a block is ever revealed - and so is what it loads; this holds only the question.
 * <p>
 * A bean rather than a static helper because the answer to "blocked" is the friendship module's. Package-private: no
 * other module and no controller asks the blog's rule.
 */
@Component
@RequiredArgsConstructor
class BlogReadRule {

    private final FriendshipReader friendshipReader;

    /** Published, or the viewer's own in any status; and no block between the viewer and the author. */
    boolean canRead(BlogPost post, UUID viewerId) {
        return post.isReadableBy(viewerId) && !isBlockedWith(post.getAuthorId(), viewerId);
    }

    /**
     * Whether the viewer and this author have a block between them, either way. A null author is an erased account:
     * nobody's to be blocked with, and nothing is asked. Nobody is blocked with themselves.
     */
    boolean isBlockedWith(UUID authorId, UUID viewerId) {
        return authorId != null && !authorId.equals(viewerId)
                && friendshipReader.isBlockedEitherWay(viewerId, authorId);
    }
}
