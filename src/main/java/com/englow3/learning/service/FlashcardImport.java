package com.englow3.learning.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.englow3.learning.dto.command.AddFlashcardsCommand.NewCard;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the data pipeline's flashcard JSON into cards this module can store.
 * <p>
 * Pure, so a file can be checked without a database - which is the whole point of offering a dry run. The pipeline
 * validates its own output against a schema before writing it; this validates again on the way in, because a file that
 * reached a person's disk is a file that could have been edited, and because the two sides disagree about what is
 * required: the pipeline demands fields this module has no column for, and this module demands a Vietnamese definition
 * the pipeline does not produce.
 * <p>
 * Nothing here decides what to do about a bad row. It reports them, and the caller decides whether a file with
 * rejections is worth importing - a judgement that belongs to the person looking at the report, not to the parser.
 */
public final class FlashcardImport {

    /**
     * The largest file that will be read in one go.
     * <p>
     * A whole generated batch is about three thousand cards, so this takes one comfortably while refusing a file that
     * would hold a request thread open building a list nobody asked for.
     */
    static final int MAX_CARDS = 5_000;

    private FlashcardImport() {
    }

    /**
     * What a row was rejected for, in words an author can act on.
     *
     * @param index
     *            position in the file, one-based, so it matches what a person counting rows would say
     */
    public record Rejection(int index, String lemma, String reason) {
    }

    /**
     * @param cards
     *            the rows that can be stored, in file order
     * @param rejections
     *            the rows that cannot, with the reason for each
     */
    public record Report(List<NewCard> cards, List<Rejection> rejections) {

        public int acceptedCount() {
            return cards.size();
        }

        public int rejectedCount() {
            return rejections.size();
        }

        public boolean isEmpty() {
            return cards.isEmpty();
        }
    }

    /**
     * Reads a file.
     *
     * @throws BadRequestException
     *             if the file is not a JSON array at all - that is a wrong file, not a bad row, and reporting it as
     *             three thousand rejections would bury the one thing the person needs to know
     */
    public static Report read(ObjectMapper objectMapper, String json) {
        JsonNode root = parse(objectMapper, json);
        if (!root.isArray()) {
            throw new BadRequestException("FLASHCARD_IMPORT_NOT_A_LIST", "The file must be a JSON array of cards");
        }
        if (root.size() > MAX_CARDS) {
            throw new BadRequestException("FLASHCARD_IMPORT_TOO_LARGE",
                    "A file may hold at most %d cards; this one has %d".formatted(MAX_CARDS, root.size()));
        }

        List<NewCard> cards = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        // Within one file only. A lemma already in the set is the service's business, not the parser's - it is the
        // one check that needs the database.
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < root.size(); i++) {
            JsonNode row = root.get(i);
            String lemma = text(row, "lemma");
            String reason = rejectionFor(row, lemma, seen);

            if (reason != null) {
                rejections.add(new Rejection(i + 1, lemma == null ? "" : lemma, reason));
                continue;
            }
            seen.add(key(lemma, text(row, "sense_label_en")));
            cards.add(cardFrom(row, lemma));
        }

        return new Report(List.copyOf(cards), List.copyOf(rejections));
    }

    /** The first thing wrong with a row, or null if nothing is. One reason, because a list of five is not read. */
    private static String rejectionFor(JsonNode row, String lemma, Set<String> seen) {
        if (!row.isObject()) {
            return "Not a card object";
        }
        if (lemma == null) {
            return "No lemma";
        }
        if (text(row, "definition") == null) {
            return "No English definition";
        }
        // Both are not-null columns. Letting them through would turn a bad row into a failed insert, which fails the
        // whole batch and reports as a server error rather than as the one card that needs fixing.
        if (text(row, "pos") == null) {
            return "No part of speech";
        }
        if (text(row, "ipa_us") == null) {
            return "No US pronunciation";
        }
        // The one field the pipeline does not produce, and a not-null column here. Rejected rather than stored empty:
        // this platform teaches Vietnamese speakers, and a card with no Vietnamese definition is half a card. A
        // generated batch therefore needs translating before it can be imported, and this says so per row rather than
        // letting three thousand blanks reach learners.
        if (text(row, "definition_vi") == null) {
            return "No Vietnamese definition";
        }
        if (firstExample(row) == null) {
            return "No example sentence";
        }
        // A sense label is what separates two entries for the same word. Without it, two senses of "bank" are one
        // card as far as the set is concerned, and the second silently replaces the first.
        if (text(row, "sense_label_en") == null) {
            return "No sense label";
        }
        if (seen.contains(key(lemma, text(row, "sense_label_en")))) {
            return "Already in this file";
        }
        return null;
    }

    private static NewCard cardFrom(JsonNode row, String lemma) {
        return new NewCard(lemma, text(row, "pos"), text(row, "sense_label_en"), text(row, "ipa_us"),
                text(row, "ipa_uk"), text(row, "audio_url_us"), text(row, "audio_url_uk"), text(row, "definition"),
                text(row, "definition_vi"), firstExample(row), text(row, "example_translation_vi"),
                text(row, "mnemonic_tip_vi"), text(row, "cefr_level"));
    }

    /**
     * The pipeline writes examples as a list. Only the first is stored: a card shows one sentence, and keeping the rest
     * in a column nothing reads would be storage pretending to be a feature.
     */
    private static String firstExample(JsonNode row) {
        JsonNode examples = row.path("examples");
        if (examples.isArray() && !examples.isEmpty()) {
            JsonNode first = examples.get(0);
            return first.isObject() ? text(first, "text") : blankToNull(first.asText(null));
        }
        return text(row, "example_sentence");
    }

    private static String key(String lemma, String senseLabel) {
        return lemma.strip().toLowerCase(Locale.ROOT) + "|"
                + (senseLabel == null ? "" : senseLabel.strip().toLowerCase(Locale.ROOT));
    }

    private static String text(JsonNode row, String field) {
        return blankToNull(row.path(field).asText(null));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static JsonNode parse(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException malformed) {
            throw new BadRequestException("FLASHCARD_IMPORT_UNREADABLE", "The file is not valid JSON");
        }
    }
}
