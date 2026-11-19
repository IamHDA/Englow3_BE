package com.englow3.blog.entity;

import java.util.Arrays;
import java.util.Objects;

import com.englow3.shared.error.BadRequestException;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

/**
 * One tag of a post: a kind and one of that kind's values. Immutable, and only {@link #of} makes one, so a tag whose
 * value does not belong to its kind - a level named WRITING - cannot exist. Values are compared exactly, so lower case
 * is as unknown as any other word.
 */
@Embeddable
@Getter
public class BlogTag {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private BlogTagKind kind;

    @Column(name = "tag_value", nullable = false, updatable = false)
    private String value;

    protected BlogTag() {
    }

    private BlogTag(BlogTagKind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    public static BlogTag of(BlogTagKind kind, String value) {
        if (kind == null || value == null || !isKnown(kind, value)) {
            throw new BadRequestException("BLOG_TAG_INVALID", "Unknown %s tag '%s'".formatted(kind, value));
        }
        return new BlogTag(kind, value);
    }

    private static boolean isKnown(BlogTagKind kind, String value) {
        return switch (kind) {
            case SKILL -> Arrays.stream(BlogSkill.values()).anyMatch(skill -> skill.name().equals(value));
            case LEVEL -> Arrays.stream(BlogLevel.values()).anyMatch(level -> level.name().equals(value));
            case CERTIFICATE ->
                    Arrays.stream(BlogCertificate.values()).anyMatch(certificate -> certificate.name().equals(value));
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlogTag that && kind == that.kind && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }
}
