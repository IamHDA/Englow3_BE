package com.englow3.user.helper;

import java.util.List;

import com.englow3.shared.error.BadRequestException;

/**
 * The profile pictures a learner can choose from. A picture is only ever one of these: there is no upload, so nothing a
 * user sends is ever published from the public bucket as their face.
 * <p>
 * The images are static files of the web app; what is stored on the user is a key naming one of them, and the URL is
 * built from it on every read. A key that names no preset - a picture uploaded before presets existed - is still an
 * object in the avatar bucket and is served from there until the learner picks a preset.
 */
public final class AvatarPreset {

    /** Every preset, in the order the picker shows them. Each has {@code <id>.svg} under the preset base URL. */
    public static final List<String> IDS = List.of("fox", "owl", "cat", "panda", "bear", "rabbit", "penguin", "frog");

    private static final String PREFIX = "preset:";

    private AvatarPreset() {
    }

    /** What a user stores for a chosen preset, or a refusal for an id that names none. */
    public static String storedKey(String presetId) {
        if (presetId == null || !IDS.contains(presetId)) {
            throw new BadRequestException("AVATAR_PRESET_UNKNOWN", "Choose one of the available profile pictures");
        }
        return PREFIX + presetId;
    }

    public static boolean isPreset(String storedKey) {
        return storedKey != null && storedKey.startsWith(PREFIX);
    }

    public static String url(String presetBaseUrl, String presetId) {
        return presetBaseUrl + "/" + presetId + ".svg";
    }

    /**
     * The picture's URL whichever kind it is: a preset from the web app's files, anything older from the bucket.
     */
    public static String urlForStoredKey(String storedKey, String presetBaseUrl, String publicBaseUrl) {
        if (storedKey == null) {
            return null;
        }
        return isPreset(storedKey) ? url(presetBaseUrl, storedKey.substring(PREFIX.length()))
                : publicBaseUrl + "/" + storedKey;
    }
}
