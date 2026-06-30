package com.github.senkex.headrender.skin;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Crops the front-facing parts (face, full body) out of a complete Minecraft
 * skin sheet.
 *
 * <p>Modern skins are {@code 64x64}; legacy skins are {@code 64x32} and have no
 * dedicated left arm/leg regions, so those are mirrored from the right side.
 * All overlay layers (hat, jacket, sleeves, trousers) are composited on top
 * when requested.</p>
 *
 * <p>The composed body canvas is {@code 16x32}:</p>
 * <pre>
 *   . H .      head  8x8  at (4,0)
 *   A T A      arms  4x12 at (0,8) and (12,8), torso 8x12 at (4,8)
 *   . L L .    legs  4x12 at (4,20) and (8,20)
 * </pre>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class SkinParts {

    private SkinParts() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the {@code 8x8} face (optionally with the hat overlay).
     *
     * @param skin the full skin sheet (or an already-cropped face), must not be {@code null}
     * @param includeOverlay {@code true} to composite the hat layer
     * @return the face image
     */
    public static BufferedImage face(final BufferedImage skin, final boolean includeOverlay) {
        return SkinFaces.faceOf(skin, includeOverlay);
    }

    /**
     * Returns the full front body silhouette as a {@code 16x32} image.
     *
     * @param skin the full skin sheet, must not be {@code null} and must be a full skin
     * @param includeOverlay {@code true} to composite the second (overlay) layers
     * @return the composed body image
     * @throws IllegalArgumentException if the image is not a full skin sheet
     */
    public static BufferedImage body(final BufferedImage skin, final boolean includeOverlay) {
        Objects.requireNonNull(skin, "Skin cannot be null");
        if (!SkinFaces.isFullSkin(skin)) {
            throw new IllegalArgumentException("Body rendering requires a full skin sheet (64x64 or 64x32)");
        }

        final boolean modern = skin.getHeight() >= 64;
        final BufferedImage canvas = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = canvas.createGraphics();
        try {
            // Base layers.
            draw(g, skin, 8, 8, 8, 8, 4, 0);     // head
            draw(g, skin, 20, 20, 8, 12, 4, 8);  // torso
            draw(g, skin, 44, 20, 4, 12, 12, 8); // right arm -> screen-left
            draw(g, skin, 4, 20, 4, 12, 4, 20);  // right leg

            if (modern) {
                draw(g, skin, 36, 52, 4, 12, 0, 8);  // left arm
                draw(g, skin, 20, 52, 4, 12, 8, 20); // left leg
            } else {
                // Legacy: mirror the right limbs.
                drawMirrored(g, skin, 44, 20, 4, 12, 0, 8);  // left arm
                drawMirrored(g, skin, 4, 20, 4, 12, 8, 20);  // left leg
            }

            if (includeOverlay) {
                draw(g, skin, 40, 8, 8, 8, 4, 0);    // hat
                if (modern) {
                    draw(g, skin, 20, 36, 8, 12, 4, 8);  // jacket
                    draw(g, skin, 44, 36, 4, 12, 12, 8); // right sleeve
                    draw(g, skin, 52, 52, 4, 12, 0, 8);  // left sleeve
                    draw(g, skin, 4, 36, 4, 12, 4, 20);  // right trouser
                    draw(g, skin, 4, 52, 4, 12, 8, 20);  // left trouser
                }
            }
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private static void draw(final Graphics2D g, final BufferedImage skin,
                             final int sx, final int sy, final int w, final int h,
                             final int dx, final int dy) {
        final BufferedImage region = skin.getSubimage(sx, sy, w, h);
        g.drawImage(region, dx, dy, null);
    }

    private static void drawMirrored(final Graphics2D g, final BufferedImage skin,
                                     final int sx, final int sy, final int w, final int h,
                                     final int dx, final int dy) {
        final BufferedImage region = skin.getSubimage(sx, sy, w, h);
        // Flip horizontally into the destination rectangle.
        g.drawImage(region, dx + w, dy, dx, dy + h, 0, 0, w, h, null);
    }
}
