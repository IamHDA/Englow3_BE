package com.englow3.blog.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.englow3.blog.dto.projection.PostSummaryRow;
import com.englow3.blog.dto.projection.PostTagRow;
import com.englow3.blog.dto.result.BlogAuthorResult;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.helper.BlogMarkdown;
import com.englow3.blog.query.BlogPostListQuery;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;

import lombok.RequiredArgsConstructor;

/**
 * Turns the rows of a post list into the summaries a caller shows: an excerpt, the tags and the author's name and
 * picture. Used by every list of posts - the feed, the saved list, the recommendations - so each of them reads the tags
 * and the profiles of a whole page in one call and none of them in a call per post. Package-private: the rows it takes
 * are the query's, and nothing outside the blog's services has them.
 */
@Component
@RequiredArgsConstructor
class BlogPostSummaries {

    private final BlogPostListQuery listQuery;
    private final PublicProfileReader publicProfileReader;

    /** One summary per row, in the order of the rows. */
    List<BlogPostSummaryResult> of(List<PostSummaryRow> rows) {
        Set<UUID> postIds = rows.stream().map(PostSummaryRow::id).collect(Collectors.toSet());
        Map<UUID, List<BlogTagResult>> tags = listQuery.tagsOf(postIds).stream()
                .collect(Collectors.groupingBy(PostTagRow::postId,
                        Collectors.mapping(row -> new BlogTagResult(row.kind(), row.value()), Collectors.toList())));
        // One call for the whole page. A null author is a person who erased their account: no profile to look up.
        Set<UUID> authorIds = rows.stream().map(PostSummaryRow::authorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PublicProfile> profiles = authorIds.isEmpty() ? Map.of() : publicProfileReader.profiles(authorIds);

        return rows.stream()
                .map(row -> new BlogPostSummaryResult(row.id(), row.title(), BlogMarkdown.excerpt(row.bodyHead()),
                        authorOf(row.authorId(), profiles), row.status(),
                        sorted(tags.getOrDefault(row.id(), List.of())), row.likeCount(), row.commentCount(),
                        row.likedByMe(), row.savedByMe(), row.createdAt(), row.editedAt()))
                .toList();
    }

    /** Null for an erased account, and for one whose profile is gone since the rows were read. */
    static BlogAuthorResult authorOf(UUID authorId, Map<UUID, PublicProfile> profiles) {
        PublicProfile profile = authorId == null ? null : profiles.get(authorId);
        return profile == null ? null : new BlogAuthorResult(profile.id(), profile.displayName(), profile.avatarUrl());
    }

    static List<BlogTagResult> sorted(List<BlogTagResult> tags) {
        return tags.stream().sorted(BlogTagResult.DISPLAY_ORDER).toList();
    }
}
