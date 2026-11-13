package com.englow3.exam.dto.command;

import java.util.UUID;

/**
 * @param note
 *            why the paper is being turned back. Required at the entity, not only here, so no caller can reject
 *            silently.
 */
public record RejectExamCommand(UUID examId, String note) {
}
