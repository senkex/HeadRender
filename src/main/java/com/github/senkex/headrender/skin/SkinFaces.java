package com.github.senkex.headrender.skin;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Extracts the 8x8 face out of a full Minecraft skin.
 *
 * <p>A full skin is a {@code 64x64} (or legacy {@code 64x32}) sheet where the
 * face lives at {@code (8,8)} and the hat/helmet overlay at {@code (40,8)}.
 * Sources that already return a cropped avatar/face are passed through
 * untouched.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class SkinFaces {

    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int FACE_SIZE = 8;
    private static final int OVERLAY_X = 40;
    private static final int OVERLAY_Y = 8;

    private SkinFaces() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the face of the given image.
     *
     * <p>If the image looks like a full skin sheet it is cropped to the 8x8
     * face (optionally compositing the helmet overlay). Otherwise the image is
     * assumed to already be a face/avatar and returned unchanged.</p>
     *
     * @param image the source image, must not be {@code null}
     * @param includeHelmet {@code true} to composite the hat/helmet overlay
     * @return the 8x8 face, or the original image when it is not a full skin
     */
    public static BufferedImage faceOf(final BufferedImage image, final boolean includeHelmet) {
        Objects.requireNonNull(image, "Image cannot be null");
        if (!isFullSkin(image)) {
            return image;
        }

        final BufferedImage face = image.getSubimage(FACE_X, FACE_Y, FACE_SIZE, FACE_SIZE);
        if (!includeHelmet) {
            return copy(face);
        }

        final BufferedImage result = copy(face);
        final BufferedImage overlay = image.getSubimage(OVERLAY_X, OVERLAY_Y, FACE_SIZE, FACE_SIZE);
        final Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(overlay, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    /**
     * Returns whether the given image is a full skin sheet (face needs cropping).
     *
     * @param image the image to test
     * @return {@code true} for {@code 64x64} / {@code 64x32} sheets
     */
    public static boolean isFullSkin(final BufferedImage image) {
        Objects.requireNonNull(image, "Image cannot be null");
        return image.getWidth() >= 64 && image.getHeight() >= 32;
    }

    private static BufferedImage copy(final BufferedImage source) {
        final BufferedImage out = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = out.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return out;
    }
}
