package com.github.senkex.headrender.skin;

import com.github.senkex.headrender.api.SkinProvider;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * {@link SkinProvider} that always returns a fixed in-memory image,
 * regardless of the target.
 *
 * <p>Handy as a default/placeholder source, for unit tests, or as the tail of
 * a {@link FallbackSkinProvider} chain so rendering never fails.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class StaticSkinProvider implements SkinProvider {

    private final BufferedImage image;

    /**
     * Creates a provider returning the given image (a full skin is cropped to
     * its face lazily on each fetch according to the helmet flag).
     *
     * @param image the image to serve, must not be {@code null}
     */
    public StaticSkinProvider(final BufferedImage image) {
        this.image = Objects.requireNonNull(image, "Image cannot be null");
    }

    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) {
        return SkinFaces.faceOf(image, includeHelmet);
    }

    @Override
    public BufferedImage fetchSkin(final String target) {
        return image;
    }

    @Override
    public boolean supportsFullSkin() {
        return true;
    }
}
