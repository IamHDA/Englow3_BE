package com.englow3.blog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class BlogModerationActionTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private final UUID target = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID report = UUID.randomUUID();

    private BlogModerationAction record(String reason, UUID reportId) {
        return BlogModerationAction.record(BlogTargetType.COMMENT, target, BlogModerationActionType.HIDE, reason,
                moderator, reportId, NOW);
    }

    @Test
    void anActionKeepsWhatWasDoneToWhatByWhomAndWhy() {
        BlogModerationAction action = record("Insulting another learner", report);

        assertThat(action.getId()).isNotNull();
        assertThat(action.getTargetType()).isEqualTo(BlogTargetType.COMMENT);
        assertThat(action.getTargetId()).isEqualTo(target);
        assertThat(action.getAction()).isEqualTo(BlogModerationActionType.HIDE);
        assertThat(action.getReason()).isEqualTo("Insulting another learner");
        assertThat(action.getModeratorId()).isEqualTo(moderator);
        assertThat(action.getReportId()).isEqualTo(report);
        assertThat(action.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void anActionNeedNotCloseAReport() {
        assertThat(record("Found it myself", null).getReportId()).isNull();
    }

    @Test
    void theReasonIsKeptStrippedOfTheSpacesAroundIt() {
        assertThat(record("  Spam \n", null).getReason()).isEqualTo("Spam");
    }

    @Test
    void aReasonOf500CharactersIsAcceptedAndOf501IsRefused() {
        assertThat(record("x".repeat(500), null).getReason()).hasSize(500);

        assertThatThrownBy(() -> record("x".repeat(501), null)).isInstanceOfSatisfying(BadRequestException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_MODERATION_REASON_INVALID"));
    }

    @Test
    void aMissingOrBlankReasonIsRefused() {
        for (String reason : new String[] { null, "", "   ", "\n\t" }) {
            assertThatThrownBy(() -> record(reason, null)).isInstanceOfSatisfying(BadRequestException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_MODERATION_REASON_INVALID"));
        }
    }

    @Test
    void theReasonCanBeCheckedOnItsOwnWithTheSameRule() {
        assertThat(BlogModerationAction.validReason("  Hidden  ")).isEqualTo("Hidden");
        assertThatThrownBy(() -> BlogModerationAction.validReason(" ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> BlogModerationAction.validReason("x".repeat(501)))
                .isInstanceOf(BadRequestException.class);
    }
}
