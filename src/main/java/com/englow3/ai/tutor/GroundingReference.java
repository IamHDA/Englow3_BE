package com.englow3.ai.tutor;

record GroundingReference(String referenceId, String contentType, String contentId, int revision, String contentLevel,
        String accessScope, String label, String text, String groundingHash, double score) {
}
