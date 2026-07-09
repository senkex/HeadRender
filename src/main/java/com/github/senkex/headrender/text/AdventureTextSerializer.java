package com.github.senkex.headrender.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bridges HeadRender's legacy {@code §x§r§r§g§g§b§b} output to Kyori
 * Adventure {@link Component}s.
 *
 * <p>The library renders to legacy HEX strings internally for maximum
 * compatibility. This serializer converts those strings into Adventure
 * components so they can be sent through Paper/Velocity APIs, decorated
 * with hover/click events, or merged into a MiniMessage pipeline.</p>
 *
 * <p>Requires the optional {@code net.kyori:adventure-api} and
 * {@code adventure-text-serializer-legacy} dependencies on the classpath.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class AdventureTextSerializer {

    /**
     * Section serializer configured for the {@code §x}-prefixed HEX format
     * that HeadRender emits.
     */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private AdventureTextSerializer() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a single rendered line into a component.
     *
     * @param line the legacy HEX line, must not be {@code null}
     * @return the parsed component
     */
    public static Component toComponent(final String line) {
        Objects.requireNonNull(line, "Line cannot be null");
        return LEGACY.deserialize(line);
    }

    /**
     * Converts a list of rendered lines into components, preserving order.
     *
     * @param lines the legacy HEX lines, must not be {@code null}
     * @return one component per input line
     */
    public static List<Component> toComponents(final List<String> lines) {
        Objects.requireNonNull(lines, "Lines cannot be null");
        final List<Component> components = new ArrayList<>(lines.size());
        for (final String line : lines) {
            components.add(LEGACY.deserialize(line));
        }
        return components;
    }

    /**
     * Joins a list of rendered lines into a single multi-line component,
     * inserting a newline between each row.
     *
     * <p>Handy for hologram or lore APIs that take one component.</p>
     *
     * @param lines the legacy HEX lines, must not be {@code null}
     * @return a single newline-joined component
     */
    public static Component toMultilineComponent(final List<String> lines) {
        Objects.requireNonNull(lines, "Lines cannot be null");
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(LEGACY.deserialize(lines.get(i)));
        }
        return result;
    }
}
