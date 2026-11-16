package com.englow3.blog.service;

import java.util.UUID;

import com.englow3.blog.dto.result.LikeStateResult;

public interface BlogLikeService {

    /** Likes a published post the caller may read. Liking twice is one like. */
    LikeStateResult like(UUID postId);

    /** Takes the like back, also from a post that has since been hidden or removed. */
    LikeStateResult unlike(UUID postId);
}
