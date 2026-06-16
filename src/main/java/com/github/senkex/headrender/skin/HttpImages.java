package com.github.senkex.headrender.skin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

/**
 * Small helper for downloading and decoding remote PNG images.
 *
 * <p>Shared by the HTTP-backed {@link com.github.senkex.headrender.api.SkinProvider}
 * implementations so the connection setup lives in one place.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HttpImages {

    private static final String USER_AGENT = "HeadRender";

    private HttpImages() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Downloads and decodes the image at the given URL.
     *
     * @param urlString the absolute URL, must not be {@code null}
     * @param timeoutMillis the connect/read timeout in milliseconds, must be positive
     * @return the decoded image, never {@code null}
     * @throws IOException if the request fails or the body is not an image
     */
    public static BufferedImage download(final String urlString, final int timeoutMillis) throws IOException {
        Objects.requireNonNull(urlString, "URL cannot be null");
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }

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
