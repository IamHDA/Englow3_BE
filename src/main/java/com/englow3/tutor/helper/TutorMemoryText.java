package com.englow3.tutor.helper;

/** Bounded untrusted extracts, never a verified learner profile or a source citation. */
public final class TutorMemoryText {
    private TutorMemoryText() {
    }

    public static String clip(String input, int maxBytes) {
        if (input == null)
            return "";
        String text = input.replaceAll("\\[S\\d+\\]", "[previous reference]");
        StringBuilder kept = new StringBuilder();
        int bytes = 0, index = 0;
        while (index < text.length()) {
            int cp = text.codePointAt(index), length = Character.charCount(cp);
            if (Character.isISOControl(cp) && cp != '\n' && cp != '\t')
                cp = ' ';
            int cost = cp < 128 ? 1 : cp < 2048 ? 2 : cp < 65536 ? 3 : 4;
            if (bytes + cost > maxBytes - 14)
                break;
            kept.appendCodePoint(cp);
            bytes += cost;
            index += length;
        }
        if (index < text.length())
            kept.append(" [truncated]");
        return kept.toString();
    }
}
