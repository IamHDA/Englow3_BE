package com.englow3.blog.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.blog.dto.command.SaveCommentCommand;
import com.englow3.blog.dto.result.BlogCommentResult;

/** Flat comments on a post, under the same read rule as the post itself. */
public interface BlogCommentService {

    /**
     * Oldest first. Everyone's visible comments, plus the caller's own in any status - the only way an author finds one
     * that was hidden or removed - each with its status. Comments by anyone the caller has a block with are left out.
     */
    Page<BlogCommentResult> list(UUID postId, Pageable page);

    BlogCommentResult create(UUID postId, SaveCommentCommand command);

    BlogCommentResult update(UUID commentId, SaveCommentCommand command);

    /** The author's, in every status, and also once the post has been hidden. */
    void delete(UUID commentId);
}
