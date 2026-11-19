package com.englow3.blog.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class BlogImagesTest {

    private static final long FIVE_MB = 5L * 1024 * 1024;

    @Test
    void jpegPngAndWebpUpToFiveMegabytesAreAccepted() {
        for (String type : new String[] { "image/jpeg", "image/png", "image/webp" }) {
            assertThatCode(() -> BlogImages.requireAccepted(type, 1)).doesNotThrowAnyException();
            assertThatCode(() -> BlogImages.requireAccepted(type, FIVE_MB)).doesNotThrowAnyException();
        }
    }

    @Test
    void anyOtherTypeIsRefused() {
        for (String type : new String[] { "image/gif", "image/svg+xml", "application/pdf", "IMAGE/PNG", "", null }) {
            assertThatThrownBy(() -> BlogImages.requireAccepted(type, 100)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_TYPE_NOT_ALLOWED"));
        }
    }

    @Test
    void emptyOrOverFiveMegabytesIsRefused() {
        for (long size : new long[] { 0, -1, FIVE_MB + 1 }) {
            assertThatThrownBy(() -> BlogImages.requireAccepted("image/png", size)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_TOO_LARGE"));
        }
    }

    @Test
    void theTypeIsCheckedBeforeTheSize() {
        assertThatThrownBy(() -> BlogImages.requireAccepted("image/gif", FIVE_MB + 1)).isInstanceOfSatisfying(
                BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void theKeyIsUnderTheUploadersFolderWithTheTypesExtension() {
        UUID uploader = UUID.randomUUID();
        UUID image = UUID.randomUUID();

        assertThat(BlogImages.objectKey(uploader, image, "image/jpeg"))
                .isEqualTo("posts/%s/%s.jpg".formatted(uploader, image));
        assertThat(BlogImages.objectKey(uploader, image, "image/png"))
                .isEqualTo("posts/%s/%s.png".formatted(uploader, image));
        assertThat(BlogImages.objectKey(uploader, image, "image/webp"))
                .isEqualTo("posts/%s/%s.webp".formatted(uploader, image));
    }
}
