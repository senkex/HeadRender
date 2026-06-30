package com.github.senkex.headrender.api;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.UUID;

/**
 * Contract for retrieving Minecraft player skin images.
 *
 * <p>Implementations are responsible for fetching the raw head image
 * for a given target. They may delegate to any remote service or
 * local source.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public interface SkinProvider {

    /**
     * Fetches the player's head image.
     *
     * @param target the player name or trimmed UUID
     * @param size the desired image size in pixels
     * @param includeHelmet {@code true} to include the helmet/hat overlay
     * @return the fetched image, never {@code null}
     * @throws IOException if the request fails
     */
    BufferedImage fetch(String target, int size, boolean includeHelmet) throws IOException;

    /**
     * Fetches the player's head image using a {@link UUID}.
     *
     * @param uuid the player UUID
     * @param size the desired image size in pixels
     * @param includeHelmet {@code true} to include the helmet/hat overlay
     * @return the fetched image, never {@code null}
     * @throws IOException if the request fails
     */
    default BufferedImage fetch(final UUID uuid, final int size, final boolean includeHelmet) throws IOException {
        return fetch(uuid.toString().replace("-", ""), size, includeHelmet);
    }

    /**
     * Fetches the player's <b>full skin sheet</b> ({@code 64x64} or legacy
     * {@code 64x32}), uncropped.
     *
     * <p>Required for body/part rendering. Providers that only expose
     * pre-cropped faces (avatar proxies) leave this unimplemented and report
     * {@link #supportsFullSkin()} as {@code false}.</p>
     *
     * @param target the player name or trimmed UUID
     * @return the full skin sheet, never {@code null}
     * @throws IOException if the request fails
     * @throws UnsupportedOperationException if this provider cannot expose full skins
     */
    default BufferedImage fetchSkin(final String target) throws IOException {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not expose full skin sheets");
    }

    /**
     * Returns whether this provider can serve full skin sheets via
     * {@link #fetchSkin(String)} (and therefore body/part rendering).
     *
     * @return {@code true} when full skins are available
     */
    default boolean supportsFullSkin() {
        return false;
    }
}
