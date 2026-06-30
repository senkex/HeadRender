package com.github.senkex.headrender;

import com.github.senkex.headrender.effect.HeadEffect;
import com.github.senkex.headrender.render.RenderPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable rendering configuration consumed by the head render service.
 *
 * <p>Defines how a player head should be rendered into chat pixels.
 * Instances are created through the static factories or with
 * {@link #builder()}.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class RenderOptions {

    /**
     * Default pixel character used for rendering.
     */
    public static final String DEFAULT_CHARACTER = "█";

    /**
     * Default render resolution in pixels.
     */
    public static final int DEFAULT_SIZE = 8;

    /**
     * Default alpha threshold for transparent pixels.
     */
    public static final int DEFAULT_ALPHA_THRESHOLD = 10;

    private final int size;
    private final String character;
    private final boolean helmetLayer;
    private final boolean useCache;
    private final int alphaThreshold;
    private final RenderPart part;
    private final List<HeadEffect> effects;

    private RenderOptions(final Builder builder) {
        this.size = builder.size;
        this.character = builder.character;
        this.helmetLayer = builder.helmetLayer;
        this.useCache = builder.useCache;
        this.alphaThreshold = builder.alphaThreshold;
        this.part = builder.part;
        this.effects = Collections.unmodifiableList(new ArrayList<>(builder.effects));
    }

    /**
     * Returns the render resolution.
     *
     * @return the render size in pixels
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the character used to represent each rendered pixel.
     *
     * @return the pixel character
     */
    public String getCharacter() {
        return character;
    }

    /**
     * Returns whether the helmet/hat overlay layer is rendered.
     *
     * @return {@code true} if the helmet layer is enabled
     */
    public boolean useHelmetLayer() {
        return helmetLayer;
    }

    /**
     * Returns whether the skin cache is used for this operation.
     *
     * @return {@code true} if cache reads and writes are enabled
     */
    public boolean useCache() {
        return useCache;
    }

    /**
     * Returns the alpha threshold below which a pixel is considered
     * transparent and rendered as a blank space.
     *
     * @return the alpha threshold in the range {@code [0, 255]}
     */
    public int getAlphaThreshold() {
        return alphaThreshold;
    }

    /**
     * Returns the skin part this configuration renders.
     *
     * @return the render part, never {@code null} (defaults to {@link RenderPart#FACE})
     */
    public RenderPart getPart() {
        return part;
    }

    /**
     * Returns the ordered, immutable list of effects applied to the head
     * image before rendering.
     *
     * @return the effects, never {@code null} (possibly empty)
     */
    public List<HeadEffect> getEffects() {
        return effects;
    }

    /**
     * Returns a builder pre-populated with this instance's values.
     *
     * @return a mutable copy of this configuration
     */
    public Builder toBuilder() {
        return new Builder()
                .size(size)
                .character(character)
                .helmetLayer(helmetLayer)
                .useCache(useCache)
                .alphaThreshold(alphaThreshold)
                .part(part)
                .effects(effects);
    }

    /**
     * Returns the default configuration.
     *
     * <p>Defaults: size {@code 8}, helmet on, cache on, alpha threshold
     * {@code 10}, character {@code █}.</p>
     *
     * @return the default options
     */
    public static RenderOptions defaults() {
        return builder().build();
    }

    /**
     * Returns a configuration with the given size and defaults elsewhere.
     *
     * @param size the render size in pixels
     * @return the new configuration
     */
    public static RenderOptions of(final int size) {
        return builder().size(size).build();
    }

    /**
     * Returns a full-body configuration of the given width (height follows the
     * {@code 1:2} body ratio). Requires a full-skin provider.
     *
     * @param size the render width in pixels
     * @return the new body configuration
     */
    public static RenderOptions body(final int size) {
        return builder().size(size).part(RenderPart.BODY).build();
    }

    /**
     * Returns a new builder.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link RenderOptions}.
     *
     * <p>Developed by <b>Senkex</b></p>
     */
    public static final class Builder {

        private int size = DEFAULT_SIZE;
        private String character = DEFAULT_CHARACTER;
        private boolean helmetLayer = true;
        private boolean useCache = true;
        private int alphaThreshold = DEFAULT_ALPHA_THRESHOLD;
        private RenderPart part = RenderPart.FACE;
        private final List<HeadEffect> effects = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the render resolution in pixels.
         *
         * @param size the render size, must be positive
         * @return this builder
         */
        public Builder size(final int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Size must be greater than zero");
            }
            this.size = size;
            return this;
        }

        /**
         * Sets the pixel character.
         *
         * @param character the character, must not be empty
         * @return this builder
         */
        public Builder character(final String character) {
            Objects.requireNonNull(character, "Character cannot be null");
            if (character.isEmpty()) {
                throw new IllegalArgumentException("Character cannot be empty");
            }
            this.character = character;
            return this;
        }

        /**
         * Toggles the helmet/hat overlay rendering.
         *
         * @param helmetLayer {@code true} to render the helmet layer
         * @return this builder
         */
        public Builder helmetLayer(final boolean helmetLayer) {
            this.helmetLayer = helmetLayer;
            return this;
        }

        /**
         * Toggles the internal cache usage.
         *
         * @param useCache {@code true} to enable cache reads and writes
         * @return this builder
         */
        public Builder useCache(final boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        /**
         * Sets the alpha threshold for transparent pixels.
         *
         * @param threshold the value in the range {@code [0, 255]}
         * @return this builder
         */
        public Builder alphaThreshold(final int threshold) {
            if (threshold < 0 || threshold > 255) {
                throw new IllegalArgumentException("Alpha threshold must be in [0, 255]");
            }
            this.alphaThreshold = threshold;
            return this;
        }

        /**
         * Sets the skin part to render ({@link RenderPart#FACE} or
         * {@link RenderPart#BODY}).
         *
         * <p>{@link RenderPart#BODY} requires a provider that exposes full
         * skins (e.g. {@code MojangSkinProvider}); avatar-proxy providers like
         * Minotar cannot serve it.</p>
         *
         * @param part the part to render, must not be {@code null}
         * @return this builder
         */
        public Builder part(final RenderPart part) {
            this.part = Objects.requireNonNull(part, "Part cannot be null");
            return this;
        }

        /**
         * Appends an effect applied to the head image before rendering.
         *
         * <p>Effects run in the order they are added. See
         * {@link com.github.senkex.headrender.effect.HeadEffects} for the
         * built-in ones.</p>
         *
         * @param effect the effect to append, must not be {@code null}
         * @return this builder
         */
        public Builder effect(final HeadEffect effect) {
            this.effects.add(Objects.requireNonNull(effect, "Effect cannot be null"));
            return this;
        }

        /**
         * Replaces the current effects with the given collection.
         *
         * @param effects the effects to apply, must not be {@code null}
         * @return this builder
         */
        public Builder effects(final Collection<? extends HeadEffect> effects) {
            Objects.requireNonNull(effects, "Effects cannot be null");
            this.effects.clear();
            for (final HeadEffect effect : effects) {
                this.effects.add(Objects.requireNonNull(effect, "Effect cannot be null"));
            }
            return this;
        }

        /**
         * Builds the configured immutable instance.
         *
         * @return a new {@link RenderOptions} instance
         */
        public RenderOptions build() {
            return new RenderOptions(this);
        }
    }
}
