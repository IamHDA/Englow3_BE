package com.englow3.tutor.dto.result;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

public record TutorDataPageResult(List<JsonNode> items, int page, boolean hasMore) {
}
