package com.englow3.blog.dto.response;

import com.englow3.blog.dto.result.LikeStateResult;

public record LikeStateResponse(boolean liked, long likeCount) {

    public static LikeStateResponse from(LikeStateResult result) {
        return new LikeStateResponse(result.liked(), result.likeCount());
    }
}
