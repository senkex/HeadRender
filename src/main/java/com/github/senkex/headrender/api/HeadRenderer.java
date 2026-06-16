package com.github.senkex.headrender.api;

import com.github.senkex.headrender.RenderOptions;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Strategy that turns a (already scaled) head image into chat-ready lines.
 *
 * <p>Different implementations trade resolution, height and client
 * requirements. The two built-in strategies are
 * {@code HexPixelRenderer} (one text line per pixel row, no resource pack)
 * and {@code HalfBlockRenderer} (two pixel rows packed into one text line
 * with the {@code ▀}/{@code ▄} half blocks, still no resource pack).</p>
 *
 * <p>All output uses the {@code §x§r§r§g§g§b§b} HEX color format and is
 * therefore compatible with Minecraft 1.16+. A renderer must be stateless
 * and safe to share across threads.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public interface HeadRenderer {

    /**
     * Renders the given image into colored chat lines.
     *
     * @param image the scaled head image, must not be {@code null}
     * @param options the render configuration, must not be {@code null}
     * @return the ordered chat lines, top to bottom
     */
    List<String> render(BufferedImage image, RenderOptions options);

    /**
     * Returns how many text lines a head of the given pixel size produces
     * with this renderer.
     *
     * <p>Used by the parser to vertically align surrounding text. The
     * default assumes one line per pixel row.</p>
     *
     * @param size the render size in pixels
     * @return the number of text lines the rendered head occupies
     */
    default int rows(final int size) {
        return size;
    }
}
