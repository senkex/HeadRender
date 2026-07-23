package com.github.senkex.headrender;

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

    /**
     * Default chat half-width in pixels (320 px chat / 2), used as the target
     * when {@link Builder#centered(boolean) centering} a head.
     */
    public static final int DEFAULT_CENTER_PX = 154;

    /**
     * Pixel width of the default {@code █} pixel character (excluding spacing).
     * Kept in sync with CenterMessage's {@code FontInfo.BLOCK_WIDTH} so both
     * libraries measure a head to the same width.
     */
    public static final int BLOCK_WIDTH = 5;

    /**
     * Extra pixel inserted between two adjacent glyphs in the vanilla font.
     */
    public static final int CHAR_SPACING = 1;

    /**
     * Pixel width of a space character (excluding spacing).
     */
    public static final int SPACE_WIDTH = 3;

    /**
     * Combined advance of one head pixel: {@link #BLOCK_WIDTH} + {@link #CHAR_SPACING}.
     */
    public static final int BLOCK_ADVANCE = BLOCK_WIDTH + CHAR_SPACING;

    /**
     * Combined advance of one space: {@link #SPACE_WIDTH} + {@link #CHAR_SPACING}.
     * A head can only be shifted sideways in whole multiples of this amount.
     */
    public static final int SPACE_ADVANCE = SPACE_WIDTH + CHAR_SPACING;

    private final int size;
    private final String character;
    private final boolean helmetLayer;
    private final boolean useCache;
    private final int alphaThreshold;
    private final int position;
    private final boolean centered;
    private final int centerPx;

    private RenderOptions(final Builder builder) {
        this.size = builder.size;
        this.character = builder.character;
        this.helmetLayer = builder.helmetLayer;
        this.useCache = builder.useCache;
        this.alphaThreshold = builder.alphaThreshold;
        this.position = builder.position;
        this.centered = builder.centered;
        this.centerPx = builder.centerPx;
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
     * Returns the horizontal offset applied to every rendered line, in pixels.
     *
     * <p>The head is shifted right by prepending spaces; because a space is
     * {@link #SPACE_ADVANCE} pixels wide the effective shift is rounded down to
     * the nearest multiple of it. Ignored when {@link #isCentered() centered}.</p>
     *
     * @return the left offset in pixels ({@code 0} means flush left)
     */
    public int getPosition() {
        return position;
    }

    /**
     * Returns whether the head is centered in chat.
     *
     * @return {@code true} if the head block is centered around {@link #getCenterPx()}
     */
    public boolean isCentered() {
        return centered;
    }

    /**
     * Returns the half-width in pixels used as the target when centering.
     *
     * @return the centering half-width (defaults to {@link #DEFAULT_CENTER_PX})
     */
    public int getCenterPx() {
        return centerPx;
    }

    /**
     * Computes how many leading spaces this configuration prepends to a head of
     * the given pixel size, honoring {@link #isCentered() centering} first and
     * {@link #getPosition() position} otherwise.
     *
     * @param size the rendered head size in pixels
     * @return the number of leading spaces, never negative
     */
    public int leadingSpaces(final int size) {
        final int offsetPx;
        if (centered) {
            final int headWidth = size * BLOCK_ADVANCE;
            offsetPx = centerPx - (headWidth / 2);
        } else {
            offsetPx = position;
        }
        if (offsetPx <= 0) {
            return 0;
        }
        return offsetPx / SPACE_ADVANCE;
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
                .position(position)
                .centered(centered)
                .centerPx(centerPx);
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
        private int position = 0;
        private boolean centered = false;
        private int centerPx = DEFAULT_CENTER_PX;

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
         * Shifts the whole head to the right by the given number of pixels,
         * mirroring how CenterMessage offsets text.
         *
         * <p>The offset is realized with leading spaces, so it snaps to the
         * nearest {@link RenderOptions#SPACE_ADVANCE}-pixel step. A value of
         * {@code 0} leaves the head flush left. Ignored while
         * {@link #centered(boolean) centering} is enabled.</p>
         *
         * <pre>{@code
         * RenderOptions.builder().position(100).build(); // ~25 spaces of offset
         * }</pre>
         *
         * @param px the left offset in pixels, must not be negative
         * @return this builder
         */
        public Builder position(final int px) {
            if (px < 0) {
                throw new IllegalArgumentException("Position must not be negative");
            }
            this.position = px;
            return this;
        }

        /**
         * Centers the head in chat around {@link #centerPx(int)}.
         *
         * <p>When enabled, every rendered row receives the same leading offset so
         * the head block sits centered without its columns drifting. This is the
         * "head included in the centering" look. Takes precedence over
         * {@link #position(int)}.</p>
         *
         * @param centered {@code true} to center the head
         * @return this builder
         */
        public Builder centered(final boolean centered) {
            this.centered = centered;
            return this;
        }

        /**
         * Sets the half-width in pixels used as the centering target.
         *
         * <p>Defaults to {@link RenderOptions#DEFAULT_CENTER_PX} (chat). Only
         * takes effect while {@link #centered(boolean) centering} is enabled.</p>
         *
         * @param px the half-width in pixels, must be positive
         * @return this builder
         */
        public Builder centerPx(final int px) {
            if (px <= 0) {
                throw new IllegalArgumentException("Center pixels must be greater than zero");
            }
            this.centerPx = px;
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
