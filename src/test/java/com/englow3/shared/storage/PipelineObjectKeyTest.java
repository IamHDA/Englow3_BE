package com.englow3.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The pipeline writes media as a URL into whatever storage it last ran against; this application stores keys. A URL
 * kept as a key would be signed as an object literally named {@code http://localhost:9000/...}.
 */
class PipelineObjectKeyTest {

    @Test
    void keepsThePathOfAUrlAsTheKey() {
        assertThat(PipelineObjectKey.objectKey("http://localhost:9000/audio/flashcards/vocab_8e75a7579f0c2530_us.mp3"))
                .isEqualTo("audio/flashcards/vocab_8e75a7579f0c2530_us.mp3");
    }

    /** The key does not depend on which host the pipeline happened to run against. */
    @Test
    void givesTheSameKeyWhereverTheFileWasServedFrom() {
        assertThat(PipelineObjectKey.objectKey("https://cdn.example.com/audio/shadowing/shadow_001.mp3"))
                .isEqualTo(PipelineObjectKey.objectKey("http://localhost:9000/audio/shadowing/shadow_001.mp3"));
    }

    @Test
    void leavesSomethingThatIsAlreadyAKeyAlone() {
        assertThat(PipelineObjectKey.objectKey("audio/flashcards/a.mp3")).isEqualTo("audio/flashcards/a.mp3");
        assertThat(PipelineObjectKey.objectKey("/audio/flashcards/a.mp3")).isEqualTo("audio/flashcards/a.mp3");
    }

    /** No audio is no key - not an empty string a player would try to fetch. */
    @Test
    void hasNoKeyForNoAudio() {
        assertThat(PipelineObjectKey.objectKey(null)).isNull();
        assertThat(PipelineObjectKey.objectKey("   ")).isNull();
        assertThat(PipelineObjectKey.objectKey("http://localhost:9000/")).isNull();
    }
}
