package com.github.senkex.headrender.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a Minecraft resource pack that bakes player faces (or any head image)
 * as <b>bitmap font glyphs</b>, so the whole head renders inline as a
 * <b>single character on one line</b> — the one thing the no-pack
 * {@link HexPixelRenderer} cannot do (vanilla chat has no per-character
 * background, so a multicolor cell needs a pack).
 *
 * <p>Each registered image is assigned a codepoint in the Unicode Private Use
 * Area ({@code U+E000+}) and emitted as a {@code bitmap} provider in a custom
 * font. Send the returned glyph with that font applied
 * ({@link #fontKey() namespace:font}) and the client draws the head:</p>
 *
 * <pre>{@code
 * ResourcePackGenerator pack = new ResourcePackGenerator();
 * FontHeadRenderer renderer = new FontHeadRenderer(pack);
 *
 * HeadRenderService service = DefaultHeadRenderService.builder()
 *         .renderer(renderer)
 *         .build();
 * HeadRender.use(service);
 *
 * // After rendering the names you want baked, write & host the pack:
 * pack.writeZip(new File("plugins/MyPlugin/heads.zip"));
 * // pack.fontKey() -> "headrender:heads" ; apply it to the glyph component.
 * }</pre>
 *
 * <p>The registry is incremental and thread-safe: render first (which registers
 * the glyphs through {@link FontHeadRenderer}), then write the pack once every
 * needed head is known, and push it to clients.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class ResourcePackGenerator {

    /**
     * First codepoint used for generated glyphs (Unicode Private Use Area).
     */
    public static final int FIRST_CODEPOINT = 0xE000;

    /**
     * Default {@code pack_format}. Override with {@link #packFormat(int)} to
     * match your server's Minecraft version.
     */
    public static final int DEFAULT_PACK_FORMAT = 34;

    /**
     * Default glyph height in pixels (one chat line is ~8px tall).
     */
    public static final int DEFAULT_GLYPH_HEIGHT = 8;

    private final String namespace;
    private final String font;
    private final int glyphHeight;
    private final int ascent;

    private volatile int packFormat = DEFAULT_PACK_FORMAT;
    private volatile String description = "HeadRender generated heads";

    private final AtomicInteger nextCodepoint = new AtomicInteger(FIRST_CODEPOINT);
    private final Map<String, Integer> keyToCodepoint = new LinkedHashMap<>();
    private final Map<Integer, BufferedImage> glyphs = new LinkedHashMap<>();

    /**
     * Creates a generator with namespace {@code headrender}, font {@code heads}
     * and the default glyph height.
     */
    public ResourcePackGenerator() {
        this("headrender", "heads", DEFAULT_GLYPH_HEIGHT);
    }

    /**
     * Creates a generator with a custom namespace, font name and glyph height.
     *
     * @param namespace the resource pack namespace (lowercase {@code [a-z0-9_]}), must not be {@code null}
     * @param font the font name within the namespace, must not be {@code null}
     * @param glyphHeight the in-game glyph height in pixels, must be positive
     */
    public ResourcePackGenerator(final String namespace, final String font, final int glyphHeight) {
        this.namespace = Objects.requireNonNull(namespace, "Namespace cannot be null");
        this.font = Objects.requireNonNull(font, "Font cannot be null");
        if (glyphHeight <= 0) {
            throw new IllegalArgumentException("Glyph height must be greater than zero");
        }
        this.glyphHeight = glyphHeight;
        this.ascent = glyphHeight - 1;
    }

    /**
     * Sets the {@code pack_format} written to {@code pack.mcmeta}.
     *
     * @param packFormat the pack format for the target Minecraft version
     * @return this generator
     */
    public ResourcePackGenerator packFormat(final int packFormat) {
        this.packFormat = packFormat;
        return this;
    }

    /**
     * Sets the resource pack description.
     *
     * @param description the human-readable description, must not be {@code null}
     * @return this generator
     */
    public ResourcePackGenerator description(final String description) {
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        return this;
    }

    /**
     * Registers (or returns the existing) glyph for the given key, baking the
     * image into the pack.
     *
     * @param key a stable identifier for the glyph (e.g. the player name)
     * @param image the head image to bake, must not be {@code null}
     * @return the assigned codepoint
     */
    public synchronized int register(final String key, final BufferedImage image) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(image, "Image cannot be null");
        final Integer existing = keyToCodepoint.get(key);
        if (existing != null) {
            glyphs.put(existing, image);
            return existing;
        }
        final int codepoint = nextCodepoint.getAndIncrement();
        keyToCodepoint.put(key, codepoint);
        glyphs.put(codepoint, image);
        return codepoint;
    }

    /**
     * Returns the glyph string (the single character) for the given key,
     * registering the image if needed.
     *
     * @param key a stable identifier for the glyph
     * @param image the head image to bake
     * @return the one-character string to send with {@link #fontKey()} applied
     */
    public String glyph(final String key, final BufferedImage image) {
        return new String(Character.toChars(register(key, image)));
    }

    /**
     * Returns the font key ({@code namespace:font}) to apply to glyph
     * components so the client uses this pack's font.
     *
     * @return the font key
     */
    public String fontKey() {
        return namespace + ':' + font;
    }

    /**
     * Returns how many glyphs are currently registered.
     *
     * @return the glyph count
     */
    public synchronized int size() {
        return glyphs.size();
    }

    /**
     * Writes the resource pack as a directory tree rooted at {@code root}.
     *
     * @param root the destination directory (created if missing), must not be {@code null}
     * @throws IOException if writing fails
     */
    public synchronized void writeDirectory(final File root) throws IOException {
        Objects.requireNonNull(root, "Root cannot be null");
        final File fontDir = new File(root, "assets/" + namespace + "/textures/font");
        final File fontJsonDir = new File(root, "assets/" + namespace + "/font");
        if (!fontDir.isDirectory() && !fontDir.mkdirs()) {
            throw new IOException("Could not create " + fontDir);
        }
        if (!fontJsonDir.isDirectory() && !fontJsonDir.mkdirs()) {
            throw new IOException("Could not create " + fontJsonDir);
        }

        Files.write(new File(root, "pack.mcmeta").toPath(), packMeta().getBytes(StandardCharsets.UTF_8));
        for (final Map.Entry<Integer, BufferedImage> entry : glyphs.entrySet()) {
            ImageIO.write(entry.getValue(), "png", new File(fontDir, glyphFile(entry.getKey())));
        }
        Files.write(new File(fontJsonDir, font + ".json").toPath(),
                fontJson().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes the resource pack as a {@code .zip} archive (ready to host and
     * serve via {@code resource-pack} URL).
     *
     * @param zip the destination zip file, must not be {@code null}
     * @throws IOException if writing fails
     */
    public synchronized void writeZip(final File zip) throws IOException {
        Objects.requireNonNull(zip, "Zip cannot be null");
        final File parent = zip.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        try (OutputStream out = new FileOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            putEntry(zos, "pack.mcmeta", packMeta().getBytes(StandardCharsets.UTF_8));
            for (final Map.Entry<Integer, BufferedImage> entry : glyphs.entrySet()) {
                final ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(entry.getValue(), "png", png);
                putEntry(zos, "assets/" + namespace + "/textures/font/" + glyphFile(entry.getKey()),
                        png.toByteArray());
            }
            putEntry(zos, "assets/" + namespace + "/font/" + font + ".json",
                    fontJson().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void putEntry(final ZipOutputStream zos, final String name, final byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private String packMeta() {
        return "{\n  \"pack\": {\n    \"pack_format\": " + packFormat
                + ",\n    \"description\": \"" + escape(description) + "\"\n  }\n}\n";
    }

    private String fontJson() {
        final StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"providers\": [\n");
        boolean first = true;
        for (final Integer codepoint : glyphs.keySet()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("    {\"type\": \"bitmap\", \"file\": \"")
                    .append(namespace).append(":font/").append(glyphFile(codepoint))
                    .append("\", \"ascent\": ").append(ascent)
                    .append(", \"height\": ").append(glyphHeight)
                    .append(", \"chars\": [\"\\u").append(String.format("%04x", codepoint))
                    .append("\"]}");
        }
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    private static String glyphFile(final int codepoint) {
        return "glyph_" + String.format("%04x", codepoint) + ".png";
    }

    private static String escape(final String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
