package com.github.senkex.headrender.skin.provider;

import com.github.senkex.headrender.api.SkinProvider;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@link SkinProvider} that delegates to an ordered list of providers,
 * returning the first successful result.
 *
 * <p>Use it to combine sources for resilience, e.g. try Minotar, then
 * Crafatar, then a bundled {@link StaticSkinProvider} fallback so a render
 * never fails outright.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class FallbackSkinProvider implements SkinProvider {

    private final List<SkinProvider> providers;

    /**
     * Creates a fallback chain from the given providers, tried in order.
     *
     * @param providers the ordered providers, must not be {@code null} or empty
     */
    public FallbackSkinProvider(final List<SkinProvider> providers) {
        Objects.requireNonNull(providers, "Providers cannot be null");
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("At least one provider is required");
        }
        this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
    }

    /**
     * Creates a fallback chain from the given providers, tried in order.
     *
     * @param providers the ordered providers, must not be empty
     */
    public FallbackSkinProvider(final SkinProvider... providers) {
        this(asList(providers));
    }

    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) throws IOException {
        IOException last = null;
        for (final SkinProvider provider : providers) {
            try {
                return provider.fetch(target, size, includeHelmet);
            } catch (final IOException | RuntimeException exception) {
                last = exception instanceof IOException
                        ? (IOException) exception
                        : new IOException(exception);
            }
        }
        throw last != null ? last : new IOException("No provider could fetch " + target);
    }

    private static List<SkinProvider> asList(final SkinProvider... providers) {
        Objects.requireNonNull(providers, "Providers cannot be null");
        final List<SkinProvider> list = new ArrayList<>(providers.length);
        for (final SkinProvider provider : providers) {
            list.add(Objects.requireNonNull(provider, "Provider cannot be null"));
        }
        return list;
    }
}
