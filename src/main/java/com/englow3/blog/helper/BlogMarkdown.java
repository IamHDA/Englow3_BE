package com.englow3.blog.helper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the module needs to know about a post's Markdown without rendering it: which of its own images the text points
 * at, and a plain-text excerpt for a list. Pure.
 * <p>
 * Images are written {@code blog-image:<id>}, never as a URL, so the bucket can stay private and no stored text goes
 * stale when a link expires or the storage domain changes. The reader's page swaps each reference for a fresh link.
 */
public final class BlogMarkdown {

    /** The longest excerpt, the ellipsis included. */
    public static final int EXCERPT_LENGTH = 200;

    private static final String ELLIPSIS = "…";
    private static final String HEX = "[0-9a-fA-F]";

    /**
     * Only a well-formed id counts: {@code blog-image:oops} is text a person typed, not a reference, and is ignored
     * rather than refused.
     */
    private static final Pattern IMAGE_REFERENCE = Pattern
            .compile("blog-image:(" + HEX + "{8}-" + HEX + "{4}-" + HEX + "{4}-" + HEX + "{4}-" + HEX + "{12})");

    /**
     * An image, also when the text ends inside it: the excerpt is taken from the first 1200 characters of a post, which
     * can stop in the middle of {@code ![alt](blog-image:6f1c}. Every part after {@code ![} is therefore optional.
     */
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]\\n]*\\]?(?:\\([^)\\n]*\\)?)?");

    /** A link, also with its address cut off; the text of the link is what stays. */
    private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]*)\\]\\([^)\\n]*\\)?");

    /** A link whose text is cut off by the end of the input. */
    private static final Pattern OPEN_LINK_AT_END = Pattern.compile("\\[([^\\]\\n]*)$");

    /** Whatever is left of a reference: in link text, in code, or typed by hand. */
    private static final Pattern IMAGE_FRAGMENT = Pattern.compile("blog-image:[0-9a-fA-F-]*");

    private static final Pattern HEADING_MARK = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+");
    private static final Pattern QUOTE_MARK = Pattern.compile("(?m)^[ \\t]*>+[ \\t]?");

    /** Emphasis underscores only: the one inside {@code snake_case} is part of a word. */
    private static final Pattern EMPHASIS_UNDERSCORE = Pattern.compile("(?<![\\p{L}\\p{N}])_+|_+(?![\\p{L}\\p{N}])");

    private static final Pattern INLINE_MARKS = Pattern.compile("[*`]+|~~");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private BlogMarkdown() {
    }

    /** Ids in {@code blog-image:<uuid>} references, in order of first appearance, without duplicates. */
    public static List<UUID> imageReferences(String body) {
        if (body == null) {
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        Matcher matcher = IMAGE_REFERENCE.matcher(body);
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(1)));
        }
        return List.copyOf(ids);
    }

    /**
     * Plain text for a list: image syntax removed, links reduced to their text, the Markdown symbols stripped,
     * whitespace collapsed, and cut at a word boundary to at most {@value #EXCERPT_LENGTH} characters - the ellipsis
     * counts - when it is longer.
     * <p>
     * Accepts text that ends in the middle of an image or a link: nothing of an unfinished
     * {@code ![alt](blog-image:...} reaches the excerpt.
     */
    public static String excerpt(String body) {
        if (body == null) {
            return "";
        }
        String text = IMAGE.matcher(body).replaceAll("");
        text = LINK.matcher(text).replaceAll("$1");
        text = OPEN_LINK_AT_END.matcher(text).replaceAll("$1");
        text = IMAGE_FRAGMENT.matcher(text).replaceAll("");
        text = HEADING_MARK.matcher(text).replaceAll("");
        text = QUOTE_MARK.matcher(text).replaceAll("");
        text = EMPHASIS_UNDERSCORE.matcher(text).replaceAll("");
        text = INLINE_MARKS.matcher(text).replaceAll("");
        text = WHITESPACE.matcher(text).replaceAll(" ").strip();
        return cut(text);
    }

    private static String cut(String text) {
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        int limit = EXCERPT_LENGTH - ELLIPSIS.length();
        int end = limit;
        if (!Character.isWhitespace(text.charAt(limit))) {
            // The cut would land inside a word: fall back to the last space before it.
            int lastSpace = text.lastIndexOf(' ', limit);
            if (lastSpace > 0) {
                end = lastSpace;
            } else if (Character.isHighSurrogate(text.charAt(end - 1))) {
                // One word longer than the excerpt: cut it, but not between the halves of a character.
                end--;
            }
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }
}
