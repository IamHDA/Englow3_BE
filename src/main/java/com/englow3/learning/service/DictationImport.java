package com.englow3.learning.service;

import java.util.ArrayList;
import java.util.List;

import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the data pipeline's shadowing batch into dictation lessons.
 * <p>
 * A clip is one recording of a whole passage with the timings of each sentence inside it, so it becomes one lesson
 * whose sentences are windows into the same file. That is why the offset columns exist: the alternative was cutting
 * every clip into a file per line, or playing the whole passage back for each one.
 * <p>
 * Pure, like {@link FlashcardImport}, so a file can be checked without a database. The shapes it expects are held to
 * the schema by {@code DictationImportContractTest} - the importer and the generator live in one repository and
 * otherwise never meet until someone uploads a file.
 */
public final class DictationImport {

    /** A generated batch is thirty clips. This takes a large one and refuses a file that is not a batch at all. */
    static final int MAX_CLIPS = 500;

    private DictationImport() {
    }

    /** One sentence, as a window into the clip's recording. */
    public record Segment(int orderNo, String text, Integer startMs, Integer endMs) {
    }

    /**
     * @param audioObjectKey
     *            the clip's own recording. Every sentence points at it and differs only by where it starts.
     */
    public record Lesson(String slug, String title, String targetLevel, String audioObjectKey, int durationSeconds,
            List<Segment> segments) {
    }

    public record Rejection(int index, String clipId, String reason) {
    }

    public record Report(List<Lesson> lessons, List<Rejection> rejections) {

        public int acceptedCount() {
            return lessons.size();
        }

        public int rejectedCount() {
            return rejections.size();
        }

        public int sentenceCount() {
            return lessons.stream().mapToInt(lesson -> lesson.segments().size()).sum();
        }

        public boolean isEmpty() {
            return lessons.isEmpty();
        }
    }

    /**
     * Reads a file.
     *
     * @throws BadRequestException
     *             if the file is not a shadowing batch - a wrong file is one thing to say, not thirty rejections
     */
    public static Report read(ObjectMapper objectMapper, String json) {
        JsonNode clips = parse(objectMapper, json).path("clips");
        if (!clips.isArray()) {
            throw new BadRequestException("DICTATION_IMPORT_NOT_A_BATCH",
                    "The file must be a shadowing batch with a clips array");
        }
        if (clips.size() > MAX_CLIPS) {
            throw new BadRequestException("DICTATION_IMPORT_TOO_LARGE",
                    "A file may hold at most %d clips; this one has %d".formatted(MAX_CLIPS, clips.size()));
        }

        List<Lesson> lessons = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();

        for (int i = 0; i < clips.size(); i++) {
            JsonNode clip = clips.get(i);
            String clipId = text(clip, "clip_id");
            String reason = rejectionFor(clip, clipId);

            if (reason != null) {
                rejections.add(new Rejection(i + 1, clipId == null ? "" : clipId, reason));
                continue;
            }
            lessons.add(lessonFrom(clip, clipId));
        }

        return new Report(List.copyOf(lessons), List.copyOf(rejections));
    }

    private static String rejectionFor(JsonNode clip, String clipId) {
        if (!clip.isObject()) {
            return "Not a clip object";
        }
        if (clipId == null) {
            return "No clip id";
        }
        // Not-null on the sentence, and there is nothing to listen to without it. The pipeline leaves it null until
        // the audio has actually been produced, so this is the row that says "this clip has no recording yet".
        if (text(clip, "audio_url") == null) {
            return "No audio";
        }
        if (segments(clip).isEmpty()) {
            return "No segments";
        }
        return null;
    }

    private static Lesson lessonFrom(JsonNode clip, String clipId) {
        return new Lesson(clipId, title(clip, clipId), text(clip, "cefr_level"), text(clip, "audio_url"),
                // Milliseconds on the wire, seconds in the column. Rounded up: a clip of 4.2 seconds reported as 4
                // would have the player stop before the last word.
                ceilSeconds(clip.path("duration_ms").asInt(0)), segments(clip));
    }

    /**
     * The clip's script as a title, cut to something a list can show.
     * <p>
     * The pipeline gives clips an id and a script but no title, and a list of "shadow-0043" is a list nobody can read.
     */
    private static String title(JsonNode clip, String clipId) {
        String script = text(clip, "script");
        if (script == null) {
            return clipId;
        }
        String firstLine = script.strip().lines().findFirst().orElse(clipId).strip();
        return firstLine.length() <= 120 ? firstLine : firstLine.substring(0, 119).stripTrailing() + "…";
    }

    /**
     * The sentences, in the order the clip gives them.
     * <p>
     * A segment with no window is kept: it means the sentence is the whole file, which is what the rest of this module
     * has always meant by no offsets. A segment with half a window is dropped - the player would have nowhere to stop.
     */
    private static List<Segment> segments(JsonNode clip) {
        JsonNode raw = clip.path("segments");
        if (!raw.isArray()) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>();
        for (JsonNode segment : raw) {
            String text = text(segment, "text");
            if (text == null) {
                continue;
            }
            Integer startMs = integer(segment, "start_ms");
            Integer endMs = integer(segment, "end_ms");
            if ((startMs == null) != (endMs == null) || (startMs != null && endMs <= startMs)) {
                continue;
            }
            segments.add(new Segment(segment.path("order").asInt(segments.size() + 1), text, startMs, endMs));
        }

        return List.copyOf(segments);
    }

    static int ceilSeconds(int milliseconds) {
        return milliseconds <= 0 ? 0 : (milliseconds + 999) / 1000;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static JsonNode parse(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException malformed) {
            throw new BadRequestException("DICTATION_IMPORT_UNREADABLE", "The file is not valid JSON");
        }
    }
}
