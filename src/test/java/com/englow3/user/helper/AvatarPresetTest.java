package com.englow3.user.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class AvatarPresetTest {

    @Test
    void servesAPresetFromTheWebAppAndAnOlderUploadFromTheBucket() {
        assertThat(AvatarPreset.urlForStoredKey("preset:fox", "/avatars", "https://cdn/avatars"))
                .isEqualTo("/avatars/fox.svg");
        assertThat(AvatarPreset.urlForStoredKey("users/u/avatar/a.png", "/avatars", "https://cdn/avatars"))
                .isEqualTo("https://cdn/avatars/users/u/avatar/a.png");
        assertThat(AvatarPreset.urlForStoredKey(null, "/avatars", "https://cdn/avatars")).isNull();
    }

    @Test
    void storesOnlyAnIdFromTheList() {
        assertThat(AvatarPreset.storedKey("panda")).isEqualTo("preset:panda");
        assertThatThrownBy(() -> AvatarPreset.storedKey("unicorn")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> AvatarPreset.storedKey(null)).isInstanceOf(BadRequestException.class);
    }
}
