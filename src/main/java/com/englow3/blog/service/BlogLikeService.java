package com.englow3.blog.service;

import java.util.UUID;

import com.englow3.blog.dto.result.LikeStateResult;

public interface BlogLikeService {

    /** Likes a published post the caller may read. Liking twice is one like. */
    LikeStateResult like(UUID postId);

    /**
     * Takes the like back from any post that exists, also one that has since been hidden or removed. When the caller
     * cannot read the post, the count it reports is 0 rather than the real one.
     */
    LikeStateResult unlike(UUID postId);
}
