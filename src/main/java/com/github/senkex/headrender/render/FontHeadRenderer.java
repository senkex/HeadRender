package com.github.senkex.headrender.render;

import com.github.senkex.headrender.RenderOptions;
import com.github.senkex.headrender.api.HeadRenderer;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@link HeadRenderer} that renders a head as a <b>single inline character</b>
 * backed by a generated resource pack (see {@link ResourcePackGenerator}).
 *
 * <p>This is the resource-pack counterpart to {@link HexPixelRenderer}: instead
 * of one chat line per pixel row, the entire head is one font glyph occupying a
 * single line — small enough to sit inline in a tab name, a scoreboard entry or
 * an action bar.</p>
 *
 * <p>Each rendered image is baked into the backing pack and assigned a glyph.
 * The returned string is the raw glyph character; it only draws the head once
 * the client has the pack and the text uses the pack's font
 * ({@link ResourcePackGenerator#fontKey()}). With Kyori Adventure:</p>
 *
 * <pre>{@code
 * Component head = Component.text(glyph).font(Key.key(pack.fontKey()));
 * }</pre>
 *
 * <p>Because {@code render} only receives the image, glyphs are keyed by image
 * content (identical faces share one glyph). Pass a stable key explicitly via
 * {@link ResourcePackGenerator#glyph(String, BufferedImage)} if you need
 * per-player glyphs regardless of pixel equality.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class FontHeadRenderer implements HeadRenderer {

    private final ResourcePackGenerator pack;

    /**
     * Creates a renderer backed by the given pack generator.
     *
     * @param pack the pack that bakes and serves the glyphs, must not be {@code null}
     */
    public FontHeadRenderer(final ResourcePackGenerator pack) {
        this.pack = Objects.requireNonNull(pack, "Pack cannot be null");
    }

    /**
     * Returns the backing resource pack generator.
     *
     * @return the pack generator
     */
    public ResourcePackGenerator pack() {
        return pack;
    }

    @Override
    public List<String> render(final BufferedImage image, final RenderOptions options) {
        Objects.requireNonNull(image, "Image cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");
        final String key = "head_" + Integer.toHexString(hash(image));
        return Collections.singletonList(pack.glyph(key, image));
    }

    @Override
    public int rows(final int size) {
        return 1;
    }

    private static int hash(final BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        int hash = 17;
        hash = 31 * hash + width;
        hash = 31 * hash + height;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                hash = 31 * hash + image.getRGB(x, y);
            }
        }
        return hash;
    }
}
