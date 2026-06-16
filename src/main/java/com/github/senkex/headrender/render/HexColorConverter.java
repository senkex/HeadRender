package com.github.senkex.headrender.render;

import java.awt.Color;
import java.util.Objects;

/**
 * Converts {@link Color} instances into the Minecraft HEX chat format.
 *
 * <p>Output uses the {@code §x§r§r§g§g§b§b} sequence available since
 * Minecraft 1.16 and supported in every subsequent version.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HexColorConverter {

    private static final char SECTION = '§';

    private HexColorConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a color into the Minecraft HEX chat format.
     *
     * @param color the color to convert
     * @return the HEX chat color string (e.g. {@code §x§F§F§0§0§0§0})
     */
    public static String toHex(final Color color) {
        Objects.requireNonNull(color, "Color cannot be null");

        final String hex = String.format("%06X", color.getRGB() & 0xFFFFFF);
        final StringBuilder builder = new StringBuilder(14);
        builder.append(SECTION).append('x');
        for (int i = 0; i < hex.length(); i++) {
            builder.append(SECTION).append(hex.charAt(i));
        }
        return builder.toString();
    }
}
