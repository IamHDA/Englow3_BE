package com.englow3.dictation.service;

import com.englow3.dictation.dto.result.DictationMediaResult;
import java.io.InputStream;

public interface DictationMediaService {
    DictationMediaResult upload(InputStream content, long length, String contentType);
}
