package com.github.senkex.headrender.render;

/**
 * Which part of a player skin a render targets.
 *
 * <p>{@link #FACE} is the classic {@code 8x8} head front (square) that every
 * {@link com.github.senkex.headrender.api.SkinProvider} can serve. {@link #BODY}
 * is the full front silhouette (head + torso + arms + legs, {@code 16x32}),
 * which requires a provider able to expose the <b>full skin</b> sheet — see
 * {@link com.github.senkex.headrender.api.SkinProvider#fetchSkin(String)}.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public enum RenderPart {

    /**
     * The {@code 8x8} square head front.
     */
    FACE(8, 8),

    /**
     * The full front body silhouette ({@code 16x32}, a {@code 1:2} ratio).
     */
    BODY(16, 32);

    private final int baseWidth;
    private final int baseHeight;

    RenderPart(final int baseWidth, final int baseHeight) {
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
    }

    /**
     * Returns the native pixel width of this part.
     *
     * @return the base width in skin pixels
     */
    public int baseWidth() {
        return baseWidth;
    }

    /**
     * Returns the native pixel height of this part.
     *
     * @return the base height in skin pixels
     */
    public int baseHeight() {
        return baseHeight;
    }

    /**
     * Returns whether this part needs the full skin sheet (rather than a
     * pre-cropped face) to be rendered.
     *
     * @return {@code true} when a full-skin provider is required
     */
    public boolean requiresFullSkin() {
        return this != FACE;
    }

    /**
     * Computes the target render height for a given requested {@code size}
     * (which always maps to the width), preserving this part's aspect ratio.
     *
     * @param size the requested width in pixels, must be positive
     * @return the matching height in pixels
     */
    public int heightFor(final int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        return Math.max(1, Math.round((float) size * baseHeight / baseWidth));
    }
}
