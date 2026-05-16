package com.github.senkex.headrender.image;

import com.github.senkex.headrender.color.HexColorConverter;
import com.github.senkex.headrender.model.RenderOptions;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts images into Minecraft chat pixel lines using HEX colors.
 *
 * <p>Each row of the image becomes one chat line. Pixels whose alpha
 * is below the configured threshold are rendered as blank spaces.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class PixelRenderer {

    private PixelRenderer() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts an image into colored chat lines using the given options.
     *
     * @param image the source image
     * @param options the render configuration
     * @return one chat line per image row
     */
    public static List<String> renderImage(final BufferedImage image, final RenderOptions options) {
        Objects.requireNonNull(image, "Image cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");

        final int width = image.getWidth();
        final int height = image.getHeight();
        final String character = options.getCharacter();
        final int alphaThreshold = options.getAlphaThreshold();

        final List<String> lines = new ArrayList<>(height);
        for (int y = 0; y < height; y++) {
            final StringBuilder builder = new StringBuilder(width * 14);
            for (int x = 0; x < width; x++) {
                final Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() < alphaThreshold) {
                    builder.append(' ');
                } else {
                    builder.append(HexColorConverter.toHex(color)).append(character);
                }
            }
            lines.add(builder.toString());
        }
        return lines;
    }
}
