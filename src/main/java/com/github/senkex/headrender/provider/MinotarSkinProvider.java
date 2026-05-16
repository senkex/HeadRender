package com.github.senkex.headrender.provider;

import com.github.senkex.headrender.api.SkinProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

/**
 * Default {@link SkinProvider} implementation backed by the
 * <a href="https://minotar.net">Minotar</a> service.
 *
 * <p>Both player names and trimmed UUIDs (no dashes) are accepted.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class MinotarSkinProvider implements SkinProvider {

    private static final String AVATAR_URL = "https://minotar.net/avatar/%s/%d.png";
    private static final String HELM_URL = "https://minotar.net/helm/%s/%d.png";

    /**
     * Default HTTP timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private static final String USER_AGENT = "HeadRender";

    private volatile int timeoutMillis;

    /**
     * Creates a provider with the default timeout.
     */
    public MinotarSkinProvider() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Creates a provider with the given timeout.
     *
     * @param timeoutMillis the HTTP timeout in milliseconds, must be positive
     */
    public MinotarSkinProvider(final int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Fetches the player's head image from the Minotar service.
     *
     * @param target the player name or trimmed UUID
     * @param size the desired image size in pixels
     * @param includeHelmet {@code true} to include the helmet/hat overlay
     * @return the fetched image, never {@code null}
     * @throws IOException if the request fails
     */
    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) throws IOException {
        Objects.requireNonNull(target, "Target cannot be null");
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Target cannot be empty");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        final String url = String.format(includeHelmet ? HELM_URL : AVATAR_URL, target, size);
        return download(url);
    }

    /**
     * Updates the HTTP timeout used for subsequent requests.
     *
     * @param millis the timeout in milliseconds, must be positive
     */
    public void setTimeout(final int millis) {
        if (millis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }
        this.timeoutMillis = millis;
    }

    private BufferedImage download(final String urlString) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setUseCaches(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "image/png");

        try (InputStream stream = connection.getInputStream()) {
            final BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Unable to decode image returned by " + urlString);
            }
            return image;
        } finally {
            connection.disconnect();
        }
    }
}
