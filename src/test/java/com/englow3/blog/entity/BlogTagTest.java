package com.englow3.blog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class BlogTagTest {

    @Test
    void aValueOfTheKindIsAccepted() {
        BlogTag skill = BlogTag.of(BlogTagKind.SKILL, "WRITING");
        BlogTag level = BlogTag.of(BlogTagKind.LEVEL, "B2");
        BlogTag certificate = BlogTag.of(BlogTagKind.CERTIFICATE, "IELTS");

        assertThat(skill.getKind()).isEqualTo(BlogTagKind.SKILL);
        assertThat(skill.getValue()).isEqualTo("WRITING");
        assertThat(level.getValue()).isEqualTo("B2");
        assertThat(certificate.getValue()).isEqualTo("IELTS");
    }

    @Test
    void everyValueOfEveryKindsEnumIsAccepted() {
        for (BlogSkill skill : BlogSkill.values()) {
            BlogTag.of(BlogTagKind.SKILL, skill.name());
        }
        for (BlogLevel level : BlogLevel.values()) {
            BlogTag.of(BlogTagKind.LEVEL, level.name());
        }
        for (BlogCertificate certificate : BlogCertificate.values()) {
            BlogTag.of(BlogTagKind.CERTIFICATE, certificate.name());
        }
    }

    @Test
    void aValueOfAnotherKindIsRefused() {
        assertThatThrownBy(() -> BlogTag.of(BlogTagKind.SKILL, "B2")).isInstanceOfSatisfying(BadRequestException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_TAG_INVALID"));
        assertThatThrownBy(() -> BlogTag.of(BlogTagKind.LEVEL, "IELTS")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> BlogTag.of(BlogTagKind.CERTIFICATE, "WRITING"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void lowerCaseIsNotTheSameValue() {
        assertThatThrownBy(() -> BlogTag.of(BlogTagKind.SKILL, "writing")).isInstanceOfSatisfying(
                BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_TAG_INVALID"));
    }

    @Test
    void aMissingKindOrValueIsRefused() {
        assertThatThrownBy(() -> BlogTag.of(null, "WRITING")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> BlogTag.of(BlogTagKind.SKILL, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void twoTagsOfTheSameKindAndValueAreEqual() {
        assertThat(BlogTag.of(BlogTagKind.LEVEL, "B2")).isEqualTo(BlogTag.of(BlogTagKind.LEVEL, "B2"))
                .hasSameHashCodeAs(BlogTag.of(BlogTagKind.LEVEL, "B2"));
        assertThat(BlogTag.of(BlogTagKind.LEVEL, "B2")).isNotEqualTo(BlogTag.of(BlogTagKind.LEVEL, "B1"));
    }
}
