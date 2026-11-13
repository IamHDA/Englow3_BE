package com.englow3.tutor.service;

import java.util.List;
import com.englow3.tutor.dto.command.TutorPolicyCommand;
import com.englow3.tutor.dto.result.TutorPolicyResult;

public interface TutorPolicyService {
    boolean accepting();

    List<String> allowedModes();

    TutorPolicyResult view();

    TutorPolicyResult update(TutorPolicyCommand command);
}
