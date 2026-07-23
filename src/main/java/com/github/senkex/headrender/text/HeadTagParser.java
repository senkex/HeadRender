package com.github.senkex.headrender.text;

import com.github.senkex.headrender.skin.HeadSource;

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

    /**
     * Pattern matching Adventure-style self-closing {@code <head:VALUE>} tags.
     *
     * <p>This is the MiniMessage-compatible form. {@code VALUE} is handed to
     * {@link com.github.senkex.headrender.skin.HeadSource#parse(String)}, so it
     * accepts an explicit {@code type:value} pair, a bare value whose type is
     * detected, and an optional trailing {@code :true} / {@code :false} helmet
     * override:</p>
     *
     * <pre>{@code
     * <head:Senkex>
     * <head:player:Senkex>
     * <head:1f085b2d-9548-4159-a8c7-f3ccdf0c2054>
     * <head:base64:eyJ0aW1lc3RhbXAiOjE2...>
     * <head:url:https://textures.minecraft.net/texture/abc123>
     * <head:entity/player/wide/steve>
     * <head:Senkex:false>
     * }</pre>
     */
    public static final Pattern SEQUENTIAL = sequentialFor("head");

    /**
     * Pattern matching typed {@code %head:VALUE%} placeholders.
     *
     * <p>The placeholder counterpart of {@link #SEQUENTIAL}: the captured value
     * goes through the same
     * {@link com.github.senkex.headrender.skin.HeadSource#parse(String)} rules,
     * so {@code %head:base64:eyJ0...%} and {@code %head:Senkex:false%} both
     * work.</p>
     */
    public static final Pattern TYPED_PLACEHOLDER = typedPlaceholderFor("head");

    /**
     * Pattern matching PlaceholderAPI-style {@code %head-NAME%} and
     * {@code %head_NAME%} placeholders.
     *
     * <p>Both the {@code -} and {@code _} separators are accepted so the
     * same string works regardless of the convention you use. Names accept
     * any sequence of characters other than whitespace and {@code %} so
     * trimmed UUIDs and usernames both work.</p>
     */
    public static final Pattern PLACEHOLDER = placeholderFor("head");

    /**
     * Pattern matching namespaced {@code %headrender:NAME%} placeholders.
     *
     * <p>This is the canonical HeadRender placeholder. Names accept any
     * sequence of characters other than whitespace and {@code %} so trimmed
     * UUIDs and usernames both work.</p>
     */
    public static final Pattern NAMESPACED = namespacedFor("headrender");

    private HeadTagParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds a namespaced placeholder pattern for the given namespace.
     *
     * <p>The returned pattern matches {@code %NAMESPACE:VALUE%} where
     * {@code NAMESPACE} is the supplied namespace (case-insensitive). For
     * example {@code namespacedFor("headrender")} matches
     * {@code %headrender:Senkex%}.</p>
     *
     * @param namespace the placeholder namespace (e.g. {@code "headrender"})
     * @return the compiled pattern
     */
    public static Pattern namespacedFor(final String namespace) {
        Objects.requireNonNull(namespace, "Namespace cannot be null");
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("Namespace cannot be empty");
        }
        final String escaped = Pattern.quote(namespace);
        return Pattern.compile(
                "%" + escaped + ":([^%\\s]+)%",
                Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * Builds a self-closing tag pattern for the given tag name.
     *
     * <p>The returned pattern matches {@code <NAME:VALUE>} where {@code NAME} is
     * the supplied tag name (case-insensitive). For example
     * {@code sequentialFor("head")} matches {@code <head:Senkex>}.</p>
     *
     * @param tagName the tag name (e.g. {@code "head"}, {@code "face"})
     * @return the compiled pattern
     */
    public static Pattern sequentialFor(final String tagName) {
        Objects.requireNonNull(tagName, "Tag name cannot be null");
        if (tagName.isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }
        final String escaped = Pattern.quote(tagName);
        return Pattern.compile(
                "<" + escaped + ":([^<>\\s]+)>",
                Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * Builds a typed placeholder pattern for the given prefix.
     *
     * <p>The returned pattern matches {@code %PREFIX:VALUE%} where
     * {@code PREFIX} is the supplied prefix (case-insensitive). For example
     * {@code typedPlaceholderFor("head")} matches {@code %head:Senkex%} and
     * {@code %head:base64:eyJ0...%}.</p>
     *
     * @param prefix the placeholder prefix (e.g. {@code "head"}, {@code "face"})
     * @return the compiled pattern
     */
    public static Pattern typedPlaceholderFor(final String prefix) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("Prefix cannot be empty");
        }
        final String escaped = Pattern.quote(prefix);
        return Pattern.compile(
                "%" + escaped + ":([^%\\s]+)%",
                Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * Builds a placeholder pattern for the given prefix.
     *
     * <p>The returned pattern matches {@code %PREFIX-VALUE%} and
     * {@code %PREFIX_VALUE%} where {@code PREFIX} is the supplied prefix
     * (case-insensitive). For example {@code placeholderFor("head")} matches
     * both {@code %head-Senkex%} and {@code %head_Senkex%}.</p>
     *
     * @param prefix the placeholder prefix (e.g. {@code "head"}, {@code "face"})
     * @return the compiled pattern
     */
    public static Pattern placeholderFor(final String prefix) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("Prefix cannot be empty");
        }
        final String escaped = Pattern.quote(prefix);
        return Pattern.compile(
                "%" + escaped + "[-_]([^%\\s]+)%",
                Pattern.CASE_INSENSITIVE
        );
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
        private volatile HeadSource source;

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

        /**
         * Returns the resolved skin origin of this head segment.
         *
         * <p>The value is parsed lazily and cached, so every tag flavour —
         * {@code <head>NAME</head>}, {@code <head:TYPE:VALUE>},
         * {@code %head-NAME%}, {@code %head:TYPE:VALUE%} — funnels through the
         * same {@link HeadSource} rules.</p>
         *
         * @return the parsed source
         * @throws IllegalStateException if this segment is not a head segment
         * @throws IllegalArgumentException if the value is not a valid source
         */
        public HeadSource source() {
            if (!head) {
                throw new IllegalStateException("Text segments carry no head source");
            }
            HeadSource current = source;
            if (current == null) {
                current = HeadSource.parse(value);
                source = current;
            }
            return current;
        }
    }
}
