package com.github.senkex.headrender.skin.provider;

import com.github.senkex.headrender.api.SkinProvider;
import com.github.senkex.headrender.skin.HeadSource;
import com.github.senkex.headrender.skin.HttpImages;
import com.github.senkex.headrender.skin.SkinFaces;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link SkinProvider} decorator that understands {@link HeadSource} targets.
 *
 * <p>Wraps any other provider and dispatches on the source type: player names
 * and UUIDs are delegated to the wrapped provider untouched, while base64
 * {@code textures} blobs, direct URLs and registered vanilla texture keys are
 * resolved here.</p>
 *
 * <p>Targets are ordinary strings — the canonical {@code type:value} form
 * produced by {@link HeadSource#canonical()} — so this slots into the existing
 * pipeline without changing the {@link SkinProvider} contract. A target that is
 * not in canonical form is parsed with {@link HeadSource#parse(String)}, which
 * means plain {@code "Senkex"} keeps working exactly as before.</p>
 *
 * <pre>{@code
 * SkinProvider provider = SourceSkinProvider.builder()
 *     .delegate(new MojangSkinProvider())
 *     .texture("minecraft:entity/player/wide/steve", steveSkin)
 *     .build();
 *
 * HeadRender.use(DefaultHeadRenderService.builder().provider(provider).build());
 * }</pre>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class SourceSkinProvider implements SkinProvider {

    private static final Pattern SKIN_URL_PATTERN =
            Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");

    /**
     * Default HTTP timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private final SkinProvider delegate;
    private final int timeoutMillis;
    private final Map<String, BufferedImage> textures;

    private SourceSkinProvider(final Builder builder) {
        this.delegate = builder.delegate != null
                ? builder.delegate
                : new FallbackSkinProvider(new MojangSkinProvider(), new MinotarSkinProvider());
        this.timeoutMillis = builder.timeoutMillis;
        this.textures = new HashMap<>(builder.textures);
    }

    /**
     * Creates a new builder.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Wraps the given provider with default settings.
     *
     * @param delegate the provider handling player names and UUIDs
     * @return the decorated provider
     */
    public static SourceSkinProvider wrapping(final SkinProvider delegate) {
        return builder().delegate(delegate).build();
    }

    /**
     * Returns the provider handling {@link HeadSource.Type#PLAYER} and
     * {@link HeadSource.Type#UUID} targets.
     *
     * @return the wrapped provider
     */
    public SkinProvider delegate() {
        return delegate;
    }

    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) throws IOException {
        Objects.requireNonNull(target, "Target cannot be null");
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }

        final HeadSource source = HeadSource.parse(target);
        switch (source.type()) {
            case PLAYER:
            case UUID:
                return delegate.fetch(source.value(), size, includeHelmet);
            case BASE64:
                return SkinFaces.faceOf(fromBase64(source.value()), includeHelmet);
            case URL:
                return SkinFaces.faceOf(HttpImages.download(source.value(), timeoutMillis), includeHelmet);
            case TEXTURE:
                return SkinFaces.faceOf(fromTextureKey(source.value()), includeHelmet);
            default:
                throw new IOException("Unsupported head source type: " + source.type());
        }
    }

    private BufferedImage fromBase64(final String encoded) throws IOException {
        final String json;
        try {
            json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException exception) {
            throw new IOException("Head source is not valid base64", exception);
        }

        final Matcher matcher = SKIN_URL_PATTERN.matcher(json);
        if (!matcher.find()) {
            throw new IOException("Decoded textures property carries no skin URL");
        }
        return HttpImages.download(matcher.group(1), timeoutMillis);
    }

    private BufferedImage fromTextureKey(final String key) throws IOException {
        final BufferedImage registered = textures.get(normalizeKey(key));
        if (registered == null) {
            throw new IOException("No texture registered for key '" + key + "'. "
                    + "HeadRender ships no vanilla assets; register it with "
                    + "SourceSkinProvider.builder().texture(key, image).");
        }
        return registered;
    }

    private static String normalizeKey(final String key) {
        final String lower = key.toLowerCase(Locale.ROOT);
        return lower.startsWith("minecraft:") ? lower.substring("minecraft:".length()) : lower;
    }

    /**
     * Builder for {@link SourceSkinProvider}.
     *
     * <p>Developed by <b>Senkex</b></p>
     */
    public static final class Builder {

        private SkinProvider delegate;
        private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private final Map<String, BufferedImage> textures = new HashMap<>();

        private Builder() {
        }

        /**
         * Sets the provider handling player names and UUIDs.
         *
         * <p>Defaults to Mojang with a Minotar fallback, matching
         * {@link com.github.senkex.headrender.DefaultHeadRenderService}.</p>
         *
         * @param delegate the provider to wrap, must not be {@code null}
         * @return this builder
         */
        public Builder delegate(final SkinProvider delegate) {
            this.delegate = Objects.requireNonNull(delegate, "Delegate cannot be null");
            return this;
        }

        /**
         * Sets the HTTP timeout used for base64 and URL sources.
         *
         * @param timeoutMillis the timeout in milliseconds, must be positive
         * @return this builder
         */
        public Builder timeout(final int timeoutMillis) {
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("Timeout must be greater than zero");
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Registers the image backing a vanilla texture key.
         *
         * <p>Lets {@code <head:entity/player/wide/steve>} resolve the same way it
         * does in Adventure, using an image you supply. The {@code minecraft:}
         * namespace is optional and the key is matched case-insensitively.</p>
         *
         * @param key the texture key, e.g. {@code entity/player/wide/steve}
         * @param image the full skin sheet or cropped face
         * @return this builder
         */
        public Builder texture(final String key, final BufferedImage image) {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(image, "Image cannot be null");
            this.textures.put(normalizeKey(key), image);
            return this;
        }

        /**
         * Builds the configured provider.
         *
         * @return a new {@link SourceSkinProvider}
         */
        public SourceSkinProvider build() {
            return new SourceSkinProvider(this);
        }
    }
}
