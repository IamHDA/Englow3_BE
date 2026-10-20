package com.englow3.tutor.dto.result;

import java.util.List;

public record TutorReplyHistoryResult(int currentRevision, boolean canSelect, List<Version> items) {
    public record Version(int revision, String status, String content, String model, String correction) {
    }
}
