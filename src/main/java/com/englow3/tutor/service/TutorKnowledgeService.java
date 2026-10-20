package com.englow3.tutor.service;

import java.util.List;
import com.englow3.tutor.dto.result.TutorSourceResult;

public interface TutorKnowledgeService {
    List<TutorSourceResult> retrieve(String question);
}
