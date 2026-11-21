package com.englow3.blog.dto.response;

import com.englow3.blog.dto.result.SaveStateResult;

public record SaveStateResponse(boolean saved) {

    public static SaveStateResponse from(SaveStateResult result) {
        return new SaveStateResponse(result.saved());
    }
}
