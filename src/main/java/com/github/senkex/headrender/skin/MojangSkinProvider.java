package com.github.senkex.headrender.skin;

import com.github.senkex.headrender.api.SkinProvider;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link SkinProvider} that resolves skins <b>directly from Mojang's official
 * API</b>, with no third-party proxy (Minotar/Crafatar/SkinMC) in between.
 *
 * <p>This is the mechanism that lets you show a player's <i>online-mode</i> head
 * while the server runs in <i>offline mode</i>: the skin is looked up by name
 * against Mojang, regardless of how the player authenticated against your
 * server. The flow is:</p>
 *
 * <ol>
 *   <li>{@code name} &rarr; {@code api.mojang.com/users/profiles/minecraft/<name>}
 *       to obtain the premium {@code UUID};</li>
 *   <li>{@code UUID} &rarr; {@code sessionserver.mojang.com/session/minecraft/profile/<uuid>}
 *       to obtain the base64 {@code textures} property;</li>
 *   <li>decode the base64, extract the {@code textures.minecraft.net} skin URL,
 *       download it and crop the {@code 8x8} face (helmet optional) via
 *       {@link SkinFaces}.</li>
 * </ol>
 *
 * <p>A trimmed or dashed {@code UUID} can be passed as the target directly, in
 * which case the name lookup (step 1) is skipped. Resolved skin URLs are cached
 * in-memory with a short TTL to respect Mojang's rate limits.</p>
 *
 * <p>If the name is not a premium account, or the profile carries no skin, the
 * fetch fails with {@link IOException}. Combine it with
 * {@link FallbackSkinProvider} and a {@link StaticSkinProvider} Steve/Alex if you
 * want a render that never fails outright.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class MojangSkinProvider implements SkinProvider {

    private static final String NAME_TO_UUID_URL =
            "https://api.mojang.com/users/profiles/minecraft/%s";
    private static final String PROFILE_URL =
            "https://sessionserver.mojang.com/session/minecraft/profile/%s";
    private static final String PROFILE_URL_SIGNED =
            "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";

    private static final Pattern UUID_PATTERN =
            Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern TEXTURES_VALUE_PATTERN =
            Pattern.compile("\"value\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
    private static final Pattern SIGNATURE_PATTERN =
            Pattern.compile("\"signature\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
    private static final Pattern SKIN_URL_PATTERN =
            Pattern.compile("\"url\"\\s*:\\s*\"(https?://textures\\.minecraft\\.net/texture/[^\"]+)\"");
    private static final Pattern TRIMMED_UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{32}");

    private static final String USER_AGENT = "HeadRender";

    /**
     * Default HTTP timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    /**
     * Default time-to-live for cached skin URLs, in milliseconds.
     */
    public static final long DEFAULT_CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final int timeoutMillis;
    private final long cacheTtlMillis;
    private final ConcurrentHashMap<String, CachedUrl> urlCache = new ConcurrentHashMap<>();

    /**
     * Creates a provider with the default timeout and cache TTL.
     */
    public MojangSkinProvider() {
        this(DEFAULT_TIMEOUT_MILLIS, DEFAULT_CACHE_TTL_MILLIS);
    }

    /**
     * Creates a provider with the given timeout and the default cache TTL.
     *
     * @param timeoutMillis the HTTP timeout in milliseconds, must be positive
     */
    public MojangSkinProvider(final int timeoutMillis) {
        this(timeoutMillis, DEFAULT_CACHE_TTL_MILLIS);
    }

    /**
     * Creates a provider with the given timeout and cache TTL.
     *
     * @param timeoutMillis the HTTP timeout in milliseconds, must be positive
     * @param cacheTtlMillis the skin-URL cache TTL in milliseconds; {@code 0}
     *                       disables the URL cache
     */
    public MojangSkinProvider(final int timeoutMillis, final long cacheTtlMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be greater than zero");
        }
        if (cacheTtlMillis < 0) {
            throw new IllegalArgumentException("Cache TTL cannot be negative");
        }
        this.timeoutMillis = timeoutMillis;
        this.cacheTtlMillis = cacheTtlMillis;
    }

    @Override
    public BufferedImage fetch(final String target, final int size, final boolean includeHelmet) throws IOException {
        Objects.requireNonNull(target, "Target cannot be null");
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Target cannot be empty");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }

        return SkinFaces.faceOf(fetchSkin(target), includeHelmet);
    }

    private BufferedImage fetchSkin(final String target) throws IOException {
        final String skinUrl = resolveSkinUrl(target);
        return HttpImages.download(skinUrl, timeoutMillis);
    }

    /**
     * Resolves the {@code textures.minecraft.net} skin URL for a name or UUID,
     * consulting (and populating) the in-memory URL cache.
     *
     * @param target the player name or trimmed/dashed UUID
     * @return the absolute skin texture URL
     * @throws IOException if the player or skin cannot be resolved
     */
    public String resolveSkinUrl(final String target) throws IOException {
        final String key = target.toLowerCase();

        if (cacheTtlMillis > 0) {
            final CachedUrl cached = urlCache.get(key);
            if (cached != null && !cached.isExpired()) {
                return cached.url;
            }
        }

        final String uuid = isUuid(target) ? trimUuid(target) : lookupUuid(target);
        final String skinUrl = lookupSkinUrl(uuid);

        if (cacheTtlMillis > 0) {
            urlCache.put(key, new CachedUrl(skinUrl, System.currentTimeMillis() + cacheTtlMillis));
        }
        return skinUrl;
    }

    /**
     * Resolves the Mojang UUID (32-char, no dashes) for a name, or returns the
     * trimmed UUID unchanged when the target already is one.
     *
     * @param target the player name or trimmed/dashed UUID
     * @return the trimmed lowercase UUID
     * @throws IOException if the name cannot be resolved
     */
    public String resolveUuid(final String target) throws IOException {
        Objects.requireNonNull(target, "Target cannot be null");
        return isUuid(target) ? trimUuid(target) : lookupUuid(target);
    }

    /**
     * Fetches the signed {@code textures} property (value + signature) for a
     * name or UUID, straight from Mojang's session server.
     *
     * <p>Use this to apply a skin elsewhere — native tablist head icons,
     * player-head items, NPC skins — rather than just rendering it. The
     * signature is included so the client accepts the property where it
     * validates it.</p>
     *
     * @param target the player name or trimmed/dashed UUID
     * @return the texture property, never {@code null}
     * @throws IOException if the player or texture cannot be resolved
     */
    public TextureProperty fetchTextures(final String target) throws IOException {
        Objects.requireNonNull(target, "Target cannot be null");
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Target cannot be empty");
        }
        final String uuid = resolveUuid(target);
        final String json = getJson(String.format(PROFILE_URL_SIGNED, uuid));
        if (json.isEmpty()) {
            throw new IOException("No profile found for UUID: " + uuid);
        }
        final Matcher value = TEXTURES_VALUE_PATTERN.matcher(json);
        if (!value.find()) {
            throw new IOException("Profile has no textures property: " + uuid);
        }
        final Matcher signature = SIGNATURE_PATTERN.matcher(json);
        final String sig = signature.find() ? signature.group(1) : null;
        return new TextureProperty(value.group(1), sig);
    }

    /**
     * Clears the resolved skin-URL cache.
     */
    public void clearCache() {
        urlCache.clear();
    }

    private String lookupUuid(final String name) throws IOException {
        final String json = getJson(String.format(NAME_TO_UUID_URL, encode(name)));
        if (json.isEmpty()) {
            throw new IOException("No premium account found for name: " + name);
        }
        final Matcher matcher = UUID_PATTERN.matcher(json);
        if (!matcher.find()) {
            throw new IOException("Could not parse UUID for name: " + name);
        }
        return matcher.group(1);
    }

    private String lookupSkinUrl(final String uuid) throws IOException {
        final String json = getJson(String.format(PROFILE_URL, uuid));
        if (json.isEmpty()) {
            throw new IOException("No profile found for UUID: " + uuid);
        }

        final Matcher value = TEXTURES_VALUE_PATTERN.matcher(json);
        if (!value.find()) {
            throw new IOException("Profile has no textures property: " + uuid);
        }

        final String decoded = new String(
                Base64.getDecoder().decode(value.group(1)), StandardCharsets.UTF_8);
        final Matcher url = SKIN_URL_PATTERN.matcher(decoded);
        if (!url.find()) {
            throw new IOException("Profile carries no skin texture: " + uuid);
        }
        return url.group(1);
    }

    private String getJson(final String urlString) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");

        try {
            final int status = connection.getResponseCode();
            // 204 (name not found) and 404 are expected "no data" answers.
            if (status == HttpURLConnection.HTTP_NO_CONTENT
                    || status == HttpURLConnection.HTTP_NOT_FOUND) {
                return "";
            }
            if (status == 429) {
                throw new IOException("Rate limited by Mojang (HTTP 429) for " + urlString);
            }
            if (status / 100 != 2) {
                throw new IOException("Mojang returned HTTP " + status + " for " + urlString);
            }
            try (InputStream stream = connection.getInputStream()) {
                return readAll(stream);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(final InputStream stream) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static boolean isUuid(final String target) {
        return TRIMMED_UUID_PATTERN.matcher(target.replace("-", "")).matches();
    }

    private static String trimUuid(final String target) {
        return target.replace("-", "").toLowerCase();
    }

    private static String encode(final String name) {
        // Player names are [A-Za-z0-9_], so no percent-encoding is required;
        // guard against accidental spaces only.
        return name.trim();
    }

    private static final class CachedUrl {
        private final String url;
        private final long expiresAt;

        private CachedUrl(final String url, final long expiresAt) {
            this.url = url;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
