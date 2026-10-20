package com.englow3.tutor.dto.result;

import java.util.UUID;

public record TutorSourceResult(String label, UUID resourceId, String title, int versionNumber, String excerpt) {
}
