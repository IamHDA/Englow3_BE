package com.englow3.tutor.dto.command;

import jakarta.validation.constraints.Size;

/** A note is optional: a learner who can see the answer is wrong should not have to explain why to say so. */
public record ReportTutorMessageCommand(@Size(max = 2_000) String note) {
}
