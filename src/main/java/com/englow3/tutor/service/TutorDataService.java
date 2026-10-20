package com.englow3.tutor.service;

import com.englow3.tutor.dto.result.TutorDataPageResult;

public interface TutorDataService {
    TutorDataPageResult export(String kind, int page);

    void purge();
}
