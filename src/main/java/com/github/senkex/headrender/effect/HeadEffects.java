package com.github.senkex.headrender.effect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Factory of ready-made {@link HeadEffect}s.
 *
 * <p>All effects preserve the alpha channel (transparent pixels stay
 * transparent) and never mutate the source image. They mirror the kind of
 * tools other head services expose (grayscale, invert, hue shift...) but run
 * locally with no resource pack and no network call.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HeadEffects {

    private HeadEffects() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns an effect that desaturates the head to grayscale.
     *
     * @return the grayscale effect
     */
    public static HeadEffect grayscale() {
        return source -> mapColors(source, (r, g, b) -> {
            final int lum = clamp(Math.round(0.299f * r + 0.587f * g + 0.114f * b));
            return new int[] {lum, lum, lum};
        });
    }

    /**
     * Returns an effect that inverts every color channel.
     *
     * @return the invert effect
     */
    public static HeadEffect invert() {
        return source -> mapColors(source, (r, g, b) -> new int[] {255 - r, 255 - g, 255 - b});
    }

    /**
     * Returns an effect that rotates the hue of every pixel.
     *
     * @param degrees the hue rotation in degrees (positive or negative)
     * @return the hue-shift effect
     */
    public static HeadEffect hueShift(final float degrees) {
        final float delta = degrees / 360.0f;
        return source -> mapColors(source, (r, g, b) -> {
            final float[] hsb = Color.RGBtoHSB(r, g, b, null);
            float hue = (hsb[0] + delta) % 1.0f;
            if (hue < 0) {
                hue += 1.0f;
            }
            final int rgb = Color.HSBtoRGB(hue, hsb[1], hsb[2]);
            return new int[] {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        });
    }

    /**
     * Returns an effect that scales color saturation.
     *
     * @param factor the saturation multiplier ({@code 0} = gray, {@code 1} = unchanged)
     * @return the saturation effect
     */
    public static HeadEffect saturate(final float factor) {
        return source -> mapColors(source, (r, g, b) -> {
            final float[] hsb = Color.RGBtoHSB(r, g, b, null);
            final int rgb = Color.HSBtoRGB(hsb[0], clamp01(hsb[1] * factor), hsb[2]);
            return new int[] {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        });
    }

    /**
     * Returns an effect that scales brightness.
     *
     * @param factor the brightness multiplier ({@code 1} = unchanged)
     * @return the brightness effect
     */
    public static HeadEffect brightness(final float factor) {
        return source -> mapColors(source, (r, g, b) -> new int[] {
                clamp(Math.round(r * factor)),
                clamp(Math.round(g * factor)),
                clamp(Math.round(b * factor))
        });
    }

    /**
     * Returns an effect that applies a warm sepia tone.
     *
     * @return the sepia effect
     */
    public static HeadEffect sepia() {
        return source -> mapColors(source, (r, g, b) -> new int[] {
                clamp(Math.round(0.393f * r + 0.769f * g + 0.189f * b)),
                clamp(Math.round(0.349f * r + 0.686f * g + 0.168f * b)),
                clamp(Math.round(0.272f * r + 0.534f * g + 0.131f * b))
        });
    }

    /**
     * Returns an effect that blends every pixel towards a target color.
     *
     * @param color the tint color, must not be {@code null}
     * @param strength the blend strength in {@code [0, 1]}
     * @return the tint effect
     */
    public static HeadEffect tint(final Color color, final float strength) {
        Objects.requireNonNull(color, "Color cannot be null");
        final float s = clamp01(strength);
        final int tr = color.getRed();
        final int tg = color.getGreen();
        final int tb = color.getBlue();
        return source -> mapColors(source, (r, g, b) -> new int[] {
                Math.round(r + (tr - r) * s),
                Math.round(g + (tg - g) * s),
                Math.round(b + (tb - b) * s)
        });
    }

    /**
     * Returns an effect that mirrors the head horizontally.
     *
     * @return the horizontal flip effect
     */
    public static HeadEffect flipHorizontal() {
        return source -> remap(source, (x, y, w, h) -> new int[] {w - 1 - x, y});
    }

    /**
     * Returns an effect that mirrors the head vertically.
     *
     * @return the vertical flip effect
     */
    public static HeadEffect flipVertical() {
        return source -> remap(source, (x, y, w, h) -> new int[] {x, h - 1 - y});
    }

    /**
     * Returns an effect that rotates the head 180 degrees.
     *
     * @return the 180-degree rotation effect
     */
    public static HeadEffect rotate180() {
        return source -> remap(source, (x, y, w, h) -> new int[] {w - 1 - x, h - 1 - y});
    }

    // region Internal helpers

    private interface ColorOp {
        int[] apply(int r, int g, int b);
    }

    private interface CoordOp {
        int[] apply(int x, int y, int width, int height);
    }

    private static BufferedImage mapColors(final BufferedImage source, final ColorOp op) {
        Objects.requireNonNull(source, "Source image cannot be null");
        final int width = source.getWidth();
        final int height = source.getHeight();
        final BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = source.getRGB(x, y);
                final int alpha = (argb >>> 24) & 0xFF;
                final int[] rgb = op.apply((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
                final int packed = (alpha << 24)
                        | (clamp(rgb[0]) << 16)
                        | (clamp(rgb[1]) << 8)
                        | clamp(rgb[2]);
                out.setRGB(x, y, packed);
            }
        }
        return out;
    }

    private static BufferedImage remap(final BufferedImage source, final CoordOp op) {
        Objects.requireNonNull(source, "Source image cannot be null");
        final int width = source.getWidth();
        final int height = source.getHeight();
        final BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int[] src = op.apply(x, y, width, height);
                out.setRGB(x, y, source.getRGB(src[0], src[1]));
            }
        }
        return out;
    }

    private static int clamp(final int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(final float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    // endregion
}
