package com.github.senkex.headrender.skin.provider;

import com.github.senkex.headrender.api.SkinProvider;
import com.github.senkex.headrender.skin.HttpImages;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * {@link SkinProvider} backed by <a href="https://skinmc.net">SkinMC</a>.
 *
 * <p>Uses the username face endpoint
 * ({@code /api/v1/face/username/<name>/<size>}), which returns the current
 * 2D face of the player. The helmet flag is ignored: the endpoint already
 * bakes the visible face.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class SkinMcSkinProvider implements SkinProvider {

    private static final String FACE_URL = "https://skinmc.net/api/v1/face/username/%s/%d";

    /**
     * Default HTTP timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private final int timeoutMillis;

    /**
     * Creates a provider with the default timeout.
     */
    public SkinMcSkinProvider() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Creates a provider with the given timeout.
     *
     * @param timeoutMillis the HTTP timeout in milliseconds, must be positive
     */
    public SkinMcSkinProvider(final int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) throws IOException {
        Objects.requireNonNull(target, "Target cannot be null");
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Target cannot be empty");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        final String encoded = URLEncoder.encode(target, StandardCharsets.UTF_8);
        final String url = String.format(FACE_URL, encoded, size);
        return HttpImages.download(url, timeoutMillis);
    }
}
