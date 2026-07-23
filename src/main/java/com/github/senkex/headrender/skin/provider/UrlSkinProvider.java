package com.github.senkex.headrender.skin.provider;

import com.github.senkex.headrender.api.SkinProvider;
import com.github.senkex.headrender.skin.HttpImages;
import com.github.senkex.headrender.skin.SkinFaces;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

/**
 * {@link SkinProvider} that treats the target as a direct image URL.
 *
 * <p>The downloaded image may be a full {@code 64x64} skin (in which case the
 * face is cropped via {@link SkinFaces}) or an already-cropped face/avatar.
 * Handy for rendering the raw Mojang texture URL or any custom CDN.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class UrlSkinProvider implements SkinProvider {

    /**
     * Default HTTP timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private final int timeoutMillis;

    /**
     * Creates a provider with the default timeout.
     */
    public UrlSkinProvider() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Creates a provider with the given timeout.
     *
     * @param timeoutMillis the HTTP timeout in milliseconds, must be positive
     */
    public UrlSkinProvider(final int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) throws IOException {
        Objects.requireNonNull(target, "Target (URL) cannot be null");
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Target (URL) cannot be empty");
        }
        return SkinFaces.faceOf(HttpImages.download(target, timeoutMillis), includeHelmet);
    }
}
