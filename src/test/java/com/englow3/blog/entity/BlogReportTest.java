package com.englow3.blog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class BlogReportTest {

    private static final Instant FILED = Instant.parse("2026-11-16T08:00:00Z");
    private static final Instant CLOSED = Instant.parse("2026-11-16T09:00:00Z");

    private final UUID reporter = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();

    private BlogReport report(String detail) {
        return BlogReport.open(reporter, BlogTargetType.POST, target, BlogReportReason.SPAM, detail, FILED);
    }

    @Nested
    class Opening {

        @Test
        void aNewReportIsOpenAndKeepsWhatWasReported() {
            BlogReport report = report("Links to a shop");

            assertThat(report.getId()).isNotNull();
            assertThat(report.getReporterId()).isEqualTo(reporter);
            assertThat(report.getTargetType()).isEqualTo(BlogTargetType.POST);
            assertThat(report.getTargetId()).isEqualTo(target);
            assertThat(report.getReason()).isEqualTo(BlogReportReason.SPAM);
            assertThat(report.getDetail()).isEqualTo("Links to a shop");
            assertThat(report.getStatus()).isEqualTo(BlogReportStatus.OPEN);
            assertThat(report.isOpen()).isTrue();
            assertThat(report.getCreatedAt()).isEqualTo(FILED);
            assertThat(report.getResolvedAt()).isNull();
            assertThat(report.getResolvedByUserId()).isNull();
            assertThat(report.getResolutionNote()).isNull();
        }

        @Test
        void aBlankOrMissingNoteIsNoNote() {
            assertThat(report(null).getDetail()).isNull();
            assertThat(report("").getDetail()).isNull();
            assertThat(report("  \n\t ").getDetail()).isNull();
        }

        @Test
        void theNoteIsKeptStrippedOfTheSpacesAroundIt() {
            assertThat(report("  spam  \n").getDetail()).isEqualTo("spam");
        }

        @Test
        void aNoteOf500CharactersIsAcceptedAndOf501IsRefused() {
            assertThat(report("x".repeat(500)).getDetail()).hasSize(500);

            assertThatThrownBy(() -> report("x".repeat(501))).isInstanceOfSatisfying(BadRequestException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_DETAIL_TOO_LONG"));
        }

        @Test
        void spacesAroundANoteDoNotCountTowardTheLimit() {
            assertThat(report("  " + "x".repeat(500) + "  ").getDetail()).hasSize(500);
        }
    }

    @Nested
    class Closing {

        @Test
        void resolvingClosesItAsResolvedWithWhoWhenAndWhy() {
            BlogReport report = report(null);

            report.resolve(moderator, "Hidden: advertising", CLOSED);

            assertThat(report.getStatus()).isEqualTo(BlogReportStatus.RESOLVED);
            assertThat(report.isOpen()).isFalse();
            assertThat(report.getResolvedByUserId()).isEqualTo(moderator);
            assertThat(report.getResolutionNote()).isEqualTo("Hidden: advertising");
            assertThat(report.getResolvedAt()).isEqualTo(CLOSED);
        }

        @Test
        void dismissingClosesItAsDismissedWithWhoWhenAndWhy() {
            BlogReport report = report(null);

            report.dismiss(moderator, "Nothing wrong with it", CLOSED);

            assertThat(report.getStatus()).isEqualTo(BlogReportStatus.DISMISSED);
            assertThat(report.getResolvedByUserId()).isEqualTo(moderator);
            assertThat(report.getResolutionNote()).isEqualTo("Nothing wrong with it");
            assertThat(report.getResolvedAt()).isEqualTo(CLOSED);
        }

        @Test
        void aReportIsClosedOnceWhicheverWayItWasClosed() {
            BlogReport resolved = report(null);
            resolved.resolve(moderator, "first", CLOSED);
            BlogReport dismissed = report(null);
            dismissed.dismiss(moderator, "first", CLOSED);

            for (Runnable again : new Runnable[] { () -> resolved.resolve(moderator, "second", CLOSED.plusSeconds(1)),
                    () -> resolved.dismiss(moderator, "second", CLOSED.plusSeconds(1)),
                    () -> dismissed.resolve(moderator, "second", CLOSED.plusSeconds(1)),
                    () -> dismissed.dismiss(moderator, "second", CLOSED.plusSeconds(1)) }) {
                assertThatThrownBy(again::run).isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_CLOSED"));
            }
        }

        @Test
        void aSecondCloseChangesNothing() {
            BlogReport report = report(null);
            report.resolve(moderator, "first", CLOSED);

            assertThatThrownBy(() -> report.dismiss(UUID.randomUUID(), "second", CLOSED.plusSeconds(1)))
                    .isInstanceOf(ConflictException.class);

            assertThat(report.getStatus()).isEqualTo(BlogReportStatus.RESOLVED);
            assertThat(report.getResolvedByUserId()).isEqualTo(moderator);
            assertThat(report.getResolutionNote()).isEqualTo("first");
            assertThat(report.getResolvedAt()).isEqualTo(CLOSED);
        }
    }
}
