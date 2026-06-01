package com.github.senkex.headrender.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code <head>NAME</head>} tags out of arbitrary text.
 *
 * <p>The parser is intentionally lightweight: it only locates the tags
 * and splits the input into ordered text and head segments. Rendering
 * the resulting head segments is delegated to the service.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HeadTagParser {

    /**
     * Pattern matching {@code <head>NAME</head>} tags.
     *
     * <p>Names accept any sequence of non-whitespace, non-angle-bracket
     * characters so trimmed UUIDs and usernames both work.</p>
     */
    public static final Pattern PATTERN = Pattern.compile(
            "<head>([^<>\\s]+)</head>",
            Pattern.CASE_INSENSITIVE
    );

    private HeadTagParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds a tag pattern for the given tag name.
     *
     * <p>The returned pattern matches {@code <NAME>VALUE</NAME>} where
     * {@code NAME} is the supplied tag name (case-insensitive).</p>
     *
     * @param tagName the tag name (e.g. {@code "head"}, {@code "face"})
     * @return the compiled pattern
     */
    public static Pattern patternFor(final String tagName) {
        Objects.requireNonNull(tagName, "Tag name cannot be null");
        if (tagName.isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }
        final String escaped = Pattern.quote(tagName);
        return Pattern.compile(
                "<" + escaped + ">([^<>\\s]+)</" + escaped + ">",
                Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * Splits the given text into ordered segments using the default
     * {@code <head>NAME</head>} pattern.
     *
     * @param text the input text, must not be {@code null}
     * @return the ordered list of segments
     */
    public static List<Segment> parse(final String text) {
        return parse(text, PATTERN);
    }

    /**
     * Splits the given text into ordered segments using a custom pattern.
     *
     * <p>The pattern must expose the head name as capture group {@code 1}.</p>
     *
     * @param text the input text, must not be {@code null}
     * @param pattern the pattern that matches head tags, must not be {@code null}
     * @return the ordered list of segments
     */
    public static List<Segment> parse(final String text, final Pattern pattern) {
        Objects.requireNonNull(text, "Text cannot be null");
        Objects.requireNonNull(pattern, "Pattern cannot be null");
        if (text.isEmpty()) {
            return Collections.emptyList();
        }

        final List<Segment> segments = new ArrayList<>();
        final Matcher matcher = pattern.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                segments.add(Segment.text(text.substring(cursor, matcher.start())));
            }
            segments.add(Segment.head(matcher.group(1)));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            segments.add(Segment.text(text.substring(cursor)));
        }
        return segments;
    }

    /**
     * Returns {@code true} when the given text contains at least one tag
     * matching the default {@code <head>NAME</head>} pattern.
     *
     * @param text the input text
     * @return whether a head tag is present
     */
    public static boolean containsTag(final String text) {
        return containsTag(text, PATTERN);
    }

    /**
     * Returns {@code true} when the given text contains at least one tag
     * matching the supplied pattern.
     *
     * @param text the input text
     * @param pattern the pattern to test
     * @return whether the pattern is present in the text
     */
    public static boolean containsTag(final String text, final Pattern pattern) {
        Objects.requireNonNull(pattern, "Pattern cannot be null");
        return text != null && pattern.matcher(text).find();
    }

    /**
     * Ordered segment produced by the parser.
     *
     * <p>Developed by <b>Senkex</b></p>
     */
    public static final class Segment {

        private final boolean head;
        private final String value;

        private Segment(final boolean head, final String value) {
            this.head = head;
            this.value = value;
        }

        static Segment text(final String value) {
            return new Segment(false, value);
        }

        static Segment head(final String value) {
            return new Segment(true, value);
        }

        /**
         * Returns whether this segment is a head tag.
         *
         * @return {@code true} when this segment represents a head tag
         */
        public boolean isHead() {
            return head;
        }

        /**
         * Returns the segment payload (raw text or head target).
         *
         * @return the segment value
         */
        public String value() {
            return value;
        }
    }
}
