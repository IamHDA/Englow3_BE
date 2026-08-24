package com.englow3.ai.tutor;

enum TutorMode {
    Q_AND_A(true), ROLE_PLAY(false), SENTENCE_CORRECTION(false), WRITING_FEEDBACK(false);

    private final boolean groundingRequired;

    TutorMode(boolean groundingRequired) {
        this.groundingRequired = groundingRequired;
    }

    boolean groundingRequired() {
        return groundingRequired;
    }
}
