package com.github.senkex.headrender.skin;

import com.github.senkex.headrender.api.SkinProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * {@link SkinProvider} that loads skins from a local directory.
 *
 * <p>The target is resolved to {@code <directory>/<target>.png}. The loaded
 * image may be a full skin (cropped via {@link SkinFaces}) or an already
 * cropped face. Useful for offline servers or curated skin packs.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class LocalFileSkinProvider implements SkinProvider {

    private final File directory;

    /**
     * Creates a provider rooted at the given directory.
     *
     * @param directory the directory holding the {@code .png} skins, must not be {@code null}
     */
    public LocalFileSkinProvider(final File directory) {
        this.directory = Objects.requireNonNull(directory, "Directory cannot be null");
    }

    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) throws IOException {
        Objects.requireNonNull(target, "Target cannot be null");
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Target cannot be empty");
        }
        final String fileName = target.toLowerCase(Locale.ROOT).endsWith(".png") ? target : target + ".png";
        final File file = new File(directory, fileName);
        if (!file.isFile()) {
            throw new IOException("Skin file not found: " + file.getAbsolutePath());
        }
        final BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Unable to decode skin file: " + file.getAbsolutePath());
        }
        return SkinFaces.faceOf(image, includeHelmet);
    }
}
