package com.github.senkex.headrender.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Merges a rendered head into a template of chat lines using per-line markers.
 *
 * <p>Instead of placing the head as a fixed block, the caller marks the exact
 * lines that should receive a head row. Each marker line (a line that starts
 * with the marker token, e.g. {@code <hd>}) is replaced by one row of the head
 * followed by the rest of that line's text. The head is only injected when the
 * number of marker lines equals the number of rendered rows (an 8-pixel head
 * needs exactly 8 marker lines); otherwise the markers are stripped and no head
 * is shown.</p>
 *
 * <p>Marker detection matches the token only at the very start of the line, so
 * it never collides with HEX color codes ({@code <#RRGGBB>}) or gradient tags
 * ({@code <#AABBCC:#DDEEFF>}).</p>
 *
 * <p>Example template:</p>
 * <pre>{@code
 * <hd>
 * <hd> &fName: Ssenkex
 * <hd> &fRank: VIP
 * <hd>
 * <hd> &fCoins: 1200
 * <hd>
 * <hd> &fWins: 42
 * <hd>
 * }</pre>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HeadRowMerger {

    /**
     * Default marker token placed at the start of a line to receive a head row.
     */
    public static final String DEFAULT_MARKER = "<hd>";

    private HeadRowMerger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns whether the given line begins with the marker token.
     *
     * @param line the line to test
     * @param marker the marker token, must not be {@code null} or empty
     * @return {@code true} when the line starts with the marker
     */
    public static boolean isMarker(final String line, final String marker) {
        validateMarker(marker);
        return line != null && line.startsWith(marker);
    }

    /**
     * Counts how many lines in the template begin with the marker token.
     *
     * @param template the template lines, must not be {@code null}
     * @param marker the marker token, must not be {@code null} or empty
     * @return the number of marker lines
     */
    public static int count(final List<String> template, final String marker) {
        Objects.requireNonNull(template, "Template cannot be null");
        validateMarker(marker);
        int count = 0;
        for (final String line : template) {
            if (line != null && line.startsWith(marker)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Removes the leading marker token (and a single following space, if present)
     * from a marker line.
     *
     * @param line the marker line
     * @param marker the marker token, must not be {@code null} or empty
     * @return the line with the marker removed, or the original line when it is
     *         not a marker line
     */
    public static String strip(final String line, final String marker) {
        validateMarker(marker);
        if (line == null || !line.startsWith(marker)) {
            return line;
        }
        String rest = line.substring(marker.length());
        if (rest.startsWith(" ")) {
            rest = rest.substring(1);
        }
        return rest;
    }

    /**
     * Merges the rendered head rows into the template using the default
     * {@code <hd>} marker.
     *
     * @param template the template lines, must not be {@code null}
     * @param headRows the rendered head rows (one per pixel row), may be empty
     * @return the merged lines
     */
    public static List<String> merge(final List<String> template, final List<String> headRows) {
        return merge(template, headRows, DEFAULT_MARKER);
    }

    /**
     * Merges the rendered head rows into the template.
     *
     * <p>When the number of marker lines equals {@code headRows.size()}, each
     * marker line is replaced by {@code headRow + " " + remainingText}. Otherwise
     * the head is omitted and the markers are stripped so no literal marker is
     * left behind. Non-marker lines are returned unchanged.</p>
     *
     * @param template the template lines, must not be {@code null}
     * @param headRows the rendered head rows, may be {@code null} or empty
     * @param marker the marker token, must not be {@code null} or empty
     * @return the merged lines
     */
    public static List<String> merge(final List<String> template, final List<String> headRows, final String marker) {
        Objects.requireNonNull(template, "Template cannot be null");
        validateMarker(marker);

        final boolean active = headRows != null
                && !headRows.isEmpty()
                && count(template, marker) == headRows.size();

        final List<String> output = new ArrayList<>(template.size());
        int rowIndex = 0;
        for (final String line : template) {
            if (line != null && line.startsWith(marker)) {
                final String remainder = strip(line, marker);
                if (active) {
                    final String row = headRows.get(rowIndex++);
                    output.add(remainder.isEmpty() ? row : row + " " + remainder);
                } else {
                    output.add(remainder);
                }
            } else {
                output.add(line);
            }
        }
        return output;
    }

    private static void validateMarker(final String marker) {
        Objects.requireNonNull(marker, "Marker cannot be null");
        if (marker.isEmpty()) {
            throw new IllegalArgumentException("Marker cannot be empty");
        }
    }
}
