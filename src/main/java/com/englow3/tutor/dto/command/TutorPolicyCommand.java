package com.englow3.tutor.dto.command;

import java.util.List;

public record TutorPolicyCommand(boolean accepting, List<String> allowedModes, long version, String reason) {
}
