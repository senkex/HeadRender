package com.github.senkex.headrender.model;

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

    private RenderOptions(final Builder builder) {
        this.size = builder.size;
        this.character = builder.character;
        this.helmetLayer = builder.helmetLayer;
        this.useCache = builder.useCache;
        this.alphaThreshold = builder.alphaThreshold;
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
                .alphaThreshold(alphaThreshold);
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
         * Builds the configured immutable instance.
         *
         * @return a new {@link RenderOptions} instance
         */
        public RenderOptions build() {
            return new RenderOptions(this);
        }
    }
}
