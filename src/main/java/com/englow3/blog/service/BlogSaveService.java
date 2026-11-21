package com.englow3.blog.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.SaveStateResult;

/** A reader's private shelf of posts. Nobody else sees what was saved. */
public interface BlogSaveService {

    /** Saves a published post the caller may read. Saving twice is one save. */
    SaveStateResult save(UUID postId);

    /** Takes the save back from any post that exists, also one that has since been hidden or removed. */
    SaveStateResult unsave(UUID postId);

    /**
     * The caller's saved posts that they may still read, the latest save first. A post hidden or removed since, or by
     * someone the caller now has a block with, is left out and returns to the list if that is undone.
     */
    Page<BlogPostSummaryResult> listSaved(Pageable page);
}
