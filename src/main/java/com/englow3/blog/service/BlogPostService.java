package com.englow3.blog.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.blog.dto.command.ListPostsCommand;
import com.englow3.blog.dto.command.SavePostCommand;
import com.englow3.blog.dto.result.BlogPostDetailResult;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.BlogTagCatalogue;

/**
 * Posts: write, edit, delete, read one, list. A post that cannot be read by the caller - missing, hidden from them, or
 * by someone they have a block with - is the same {@code BLOG_POST_NOT_FOUND}, so neither the moderation state nor a
 * block is ever revealed.
 */
public interface BlogPostService {

    BlogPostDetailResult create(SavePostCommand command);

    /** Needs the version the editor opened; a post that has changed since is refused as stale. */
    BlogPostDetailResult update(UUID postId, SavePostCommand command);

    /** The author's, in every status. Removes the post, its images' rows and, after the commit, their objects. */
    void delete(UUID postId);

    BlogPostDetailResult get(UUID postId);

    /**
     * Published posts the caller may see. An author who lists their own posts ({@code authorId} is theirs) gets every
     * post of theirs, hidden and removed included, each with its status; nobody else's list holds them.
     */
    Page<BlogPostSummaryResult> list(ListPostsCommand command, Pageable page);

    /** The values a tag may take, for a filter. */
    BlogTagCatalogue tagCatalogue();
}
