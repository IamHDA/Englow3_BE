package com.englow3.user.dto.command;

import java.util.Set;

public record SelectLearningPurposesCommand(Set<Integer> purposeIds) {
}
