package com.github.senkex.headrender.skin;

import com.github.senkex.headrender.api.SkinProvider;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

/**
 * {@link SkinProvider} backed by <a href="https://crafatar.com">Crafatar</a>.
 *
 * <p>Crafatar's avatar endpoint is keyed by <b>UUID</b> (dashed or trimmed),
 * not by name. Use it together with {@link FallbackSkinProvider} or a
 * name-capable source if you need name lookups.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class CrafatarSkinProvider implements SkinProvider {

    private static final String AVATAR_URL = "https://crafatar.com/avatars/%s?size=%d";

    /**
     * Default HTTP timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private final int timeoutMillis;

    /**
     * Creates a provider with the default timeout.
     */
    public CrafatarSkinProvider() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Creates a provider with the given timeout.
     *
     * @param timeoutMillis the HTTP timeout in milliseconds, must be positive
     */
    public CrafatarSkinProvider(final int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) throws IOException {
        Objects.requireNonNull(target, "Target cannot be null");
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        String url = String.format(AVATAR_URL, target, size);
        if (includeHelmet) {
            url += "&overlay";
        }
        return HttpImages.download(url, timeoutMillis);
    }
}
