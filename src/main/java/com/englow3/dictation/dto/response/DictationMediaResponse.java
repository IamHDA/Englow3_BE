package com.englow3.dictation.dto.response;

import com.englow3.dictation.dto.result.DictationMediaResult;

public record DictationMediaResponse(String objectKey, String url) {
    public static DictationMediaResponse from(DictationMediaResult r) {
        return new DictationMediaResponse(r.objectKey(), r.url());
    }
}
