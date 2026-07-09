package com.github.senkex.headrender.render;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Utility for scaling {@link BufferedImage} instances.
 *
 * <p>Provides smooth (bilinear) and pixel (nearest-neighbour) algorithms.
 * Pixel scaling is the right choice for Minecraft skins because it
 * preserves sharp edges.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class ImageScaler {

    private ImageScaler() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Scales the given image using bilinear interpolation.
     *
     * @param source the source image
     * @param width the target width in pixels, must be positive
     * @param height the target height in pixels, must be positive
     * @return a new scaled image
     */
    public static BufferedImage scaleSmooth(final BufferedImage source, final int width, final int height) {
        validate(source, width, height);

        final BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    /**
     * Scales the given image using nearest-neighbour scaling.
     *
     * @param source the source image
     * @param width the target width in pixels, must be positive
     * @param height the target height in pixels, must be positive
     * @return a new scaled image
     */
    public static BufferedImage scalePixel(final BufferedImage source, final int width, final int height) {
        validate(source, width, height);

        final BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    /**
     * Scales the image preserving its aspect ratio.
     *
     * @param source the source image
     * @param targetSize the maximum target dimension in pixels, must be positive
     * @return a new scaled image
     */
    public static BufferedImage scaleAuto(final BufferedImage source, final int targetSize) {
        Objects.requireNonNull(source, "Source image cannot be null");
        if (targetSize <= 0) {
            throw new IllegalArgumentException("Target size must be greater than zero");
        }

        final int width = source.getWidth();
        final int height = source.getHeight();
        final float ratio = Math.min((float) targetSize / width, (float) targetSize / height);

        return scalePixel(source, Math.round(width * ratio), Math.round(height * ratio));
    }

    private static void validate(final BufferedImage source, final int width, final int height) {
        Objects.requireNonNull(source, "Source image cannot be null");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Target dimensions must be greater than zero");
        }
    }
}
