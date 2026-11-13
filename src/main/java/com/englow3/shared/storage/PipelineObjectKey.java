package com.englow3.shared.storage;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Turns the media references the data pipeline writes into object keys this application can sign.
 * <p>
 * The pipeline records audio as a full URL into whatever storage it was pointed at when it ran - in the batches on
 * disk, {@code http://localhost:9000/audio/flashcards/vocab_..._us.mp3}. This application stores keys and signs a fresh
 * URL on every read. Storing the pipeline's URL as if it were a key would have signed a URL for an object called
 * {@code http://localhost:9000/...}, and every imported card would have played nothing, anywhere but the one laptop the
 * pipeline happened to run on.
 * <p>
 * The key kept is the whole path - {@code audio/flashcards/vocab_..._us.mp3} - which is also the path the media
 * manifest lists each file under. So the upload and the import agree on where a file lives without either having to
 * know which bucket the pipeline used.
 */
public final class PipelineObjectKey {

    private PipelineObjectKey() {
    }

    /** A URL becomes its path; anything else is already a key and is left alone. */
    public static String objectKey(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isBlank()) {
            return null;
        }
        String value = urlOrKey.strip();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return value.startsWith("/") ? value.substring(1) : value;
        }
        try {
            String path = new URI(value).getPath();
            return path == null || path.isBlank() || path.equals("/") ? null : path.substring(1);
        } catch (URISyntaxException unreadable) {
            // Not a URL after all. Kept as given rather than dropped: a reviewer can see and fix a bad key, but not
            // one that was silently thrown away.
            return value;
        }
    }
}
