package com.github.senkex.headrender.item;

import com.github.senkex.headrender.skin.provider.MojangSkinProvider;
import com.github.senkex.headrender.skin.TextureProperty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.SkullType;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates player-head items (and skull blocks) from a name, UUID, skin URL or
 * base64 texture — the "apply a skin" side of HeadRender, versus the "draw a
 * skin as chat pixels" side of {@code HeadRender}.
 *
 * <p><b>Works from 1.8 to the latest release</b> without any NMS. Two paths are
 * used depending on the running server, picked automatically:</p>
 * <ul>
 *   <li><b>1.18.1+</b> — Bukkit's official {@link PlayerProfile} API
 *   ({@link SkullMeta#setOwnerProfile(PlayerProfile)}). This avoids the
 *   {@code com.mojang.authlib} breakage on 1.20.5+ where {@code Property}
 *   became a record and the old reflection trick stops working.</li>
 *   <li><b>1.8 – 1.18</b> — the classic reflection route: {@code setProfile}
 *   on the meta, falling back to the private {@code profile} field, with a
 *   {@code GameProfile} carrying the base64 texture.</li>
 * </ul>
 *
 * <p>The head {@link Material} is resolved the same way: {@code PLAYER_HEAD} on
 * modern servers, {@code SKULL_ITEM} with data value {@code 3} on legacy ones.</p>
 *
 * <p>This class touches the Spigot API, a {@code compileOnly} dependency — use
 * it only inside a Bukkit plugin.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HeadItem {

    private HeadItem() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Matches the {@code SKIN} url inside a decoded textures value (no JSON dep).
     */
    private static final Pattern SKIN_URL = Pattern.compile("\"SKIN\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Whether the server exposes the modern {@link PlayerProfile} API
     * ({@code SkullMeta#setOwnerProfile}, added in 1.18.1). Detected without
     * loading the class on servers that don't have it.
     */
    private static final boolean MODERN_PROFILE = detectModernProfile();

    /**
     * Reflection handle for the legacy {@code setProfile} meta method,
     * resolved lazily on 1.8 - 1.18 servers.
     */
    private static Method metaSetProfileMethod;

    /**
     * Reflection handle for the legacy private {@code profile} meta field,
     * resolved lazily on 1.8 - 1.18 servers.
     */
    private static Field metaProfileField;

    /**
     * Reflection handle for the legacy private {@code profile} block field,
     * resolved lazily on 1.8 - 1.18 servers.
     */
    private static Field blockProfileField;

    /**
     * Cached {@code com.mojang.authlib.GameProfile} class, resolved lazily.
     */
    private static Class<?> gameProfileClass;

    /**
     * Lazily-created Mojang provider shared by {@link #fromPlayer(String)}.
     */
    private static volatile MojangSkinProvider sharedProvider;

    /**
     * Creates an empty player-head item, version-safe.
     *
     * @return a blank player head ({@code PLAYER_HEAD} or legacy {@code SKULL_ITEM:3})
     */
    public static ItemStack create() {
        try {
            return new ItemStack(Material.valueOf("PLAYER_HEAD"));
        } catch (final IllegalArgumentException legacy) {
            return new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (byte) 3);
        }
    }

    /**
     * Creates a head with the skin encoded in the given base64 textures value.
     *
     * @param base64 the base64 {@code textures} value, must not be {@code null}
     * @return the head item
     */
    public static ItemStack fromBase64(final String base64) {
        return withBase64(create(), base64);
    }

    /**
     * Creates a head with the skin at the given {@code textures.minecraft.net} URL.
     *
     * @param url the skin URL, must not be {@code null}
     * @return the head item
     */
    public static ItemStack fromUrl(final String url) {
        return withUrl(create(), url);
    }

    /**
     * Creates a head owned by the given UUID (skin resolved by the client/server).
     *
     * @param id the owner UUID, must not be {@code null}
     * @return the head item
     */
    public static ItemStack fromUuid(final UUID id) {
        return withUuid(create(), id);
    }

    /**
     * Creates a head owned by the given player name.
     *
     * @param name the owner name, must not be {@code null}
     * @return the head item
     * @deprecated names don't make good identifiers; prefer {@link #fromUuid(UUID)}
     *             or {@link #fromBase64(String)}
     */
    @Deprecated
    public static ItemStack fromName(final String name) {
        return withName(create(), name);
    }

    /**
     * Creates a head from a resolved {@link TextureProperty} — the bridge from
     * HeadRender's skin resolution ({@code MojangSkinProvider#fetchTextures})
     * to a wearable/placeable item.
     *
     * @param textures the resolved texture property, must not be {@code null}
     * @return the head item
     */
    public static ItemStack fromTextures(final TextureProperty textures) {
        Objects.requireNonNull(textures, "textures");
        return fromBase64(textures.value());
    }

    /**
     * Resolves a player's real skin from Mojang (by name or UUID, working even
     * in offline mode, no proxy) and builds a head item from it.
     *
     * <p>The network lookup runs off the main thread; the returned future
     * completes with the finished item. The item is assembled off-thread too —
     * safe because it touches no world state — so you can use it directly, e.g.
     * on the main thread inside {@code thenAccept} when adding it to an
     * inventory.</p>
     *
     * @param target the player name or trimmed/dashed UUID, must not be {@code null}
     * @return a future completed with the head item
     */
    public static CompletableFuture<ItemStack> fromPlayer(final String target) {
        Objects.requireNonNull(target, "target");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fromTextures(sharedProvider().fetchTextures(target));
            } catch (final Exception exception) {
                throw new IllegalStateException("Cannot resolve head for " + target, exception);
            }
        });
    }

    /**
     * Applies the skin encoded in the base64 textures value to an existing head.
     *
     * @param item the head item to modify, must not be {@code null}
     * @param base64 the base64 {@code textures} value, must not be {@code null}
     * @return the same item, for chaining
     */
    public static ItemStack withBase64(final ItemStack item, final String base64) {
        notNull(item, "item");
        notNull(base64, "base64");
        if (!(item.getItemMeta() instanceof SkullMeta)) {
            throw new IllegalArgumentException("Item is not a player head");
        }
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyBase64(meta, base64);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Applies the skin at the given URL to an existing head.
     *
     * @param item the head item to modify, must not be {@code null}
     * @param url the skin URL, must not be {@code null}
     * @return the same item, for chaining
     */
    public static ItemStack withUrl(final ItemStack item, final String url) {
        notNull(item, "item");
        notNull(url, "url");
        return withBase64(item, urlToBase64(url));
    }

    /**
     * Sets the head's owner by UUID.
     *
     * @param item the head item to modify, must not be {@code null}
     * @param id the owner UUID, must not be {@code null}
     * @return the same item, for chaining
     */
    public static ItemStack withUuid(final ItemStack item, final UUID id) {
        notNull(item, "item");
        notNull(id, "id");
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        setOwner(meta, Bukkit.getOfflinePlayer(id));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Sets the head's owner by name.
     *
     * @param item the head item to modify, must not be {@code null}
     * @param name the owner name, must not be {@code null}
     * @return the same item, for chaining
     * @deprecated names don't make good identifiers; prefer {@link #withUuid(ItemStack, UUID)}
     */
    @Deprecated
    public static ItemStack withName(final ItemStack item, final String name) {
        notNull(item, "item");
        notNull(name, "name");
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        setOwner(meta, Bukkit.getOfflinePlayer(name));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Places a skull block carrying the skin encoded in the base64 textures value.
     *
     * @param block the target block, must not be {@code null}
     * @param base64 the base64 {@code textures} value, must not be {@code null}
     */
    public static void blockWithBase64(final Block block, final String base64) {
        notNull(block, "block");
        notNull(base64, "base64");
        setToSkull(block);
        final Skull state = (Skull) block.getState();
        applyBase64(state, base64);
        state.update(false, false);
    }

    /**
     * Places a skull block carrying the skin at the given URL.
     *
     * @param block the target block, must not be {@code null}
     * @param url the skin URL, must not be {@code null}
     */
    public static void blockWithUrl(final Block block, final String url) {
        blockWithBase64(block, urlToBase64(url));
    }

    /**
     * Places a skull block owned by the given UUID.
     *
     * @param block the target block, must not be {@code null}
     * @param id the owner UUID, must not be {@code null}
     */
    public static void blockWithUuid(final Block block, final UUID id) {
        notNull(block, "block");
        notNull(id, "id");
        setToSkull(block);
        final Skull state = (Skull) block.getState();
        setBlockOwner(state, Bukkit.getOfflinePlayer(id));
        state.update(false, false);
    }

    /**
     * Applies a base64 texture to skull meta, choosing the modern or legacy
     * route for the running server.
     *
     * @param meta the skull meta to mutate
     * @param base64 the base64 {@code textures} value
     */
    private static void applyBase64(final SkullMeta meta, final String base64) {
        if (MODERN_PROFILE) {
            applyModernProfile(meta, base64);
        } else {
            applyLegacyMetaProfile(meta, base64);
        }
    }

    /**
     * Applies a base64 texture to a skull block state, choosing the modern or
     * legacy route for the running server.
     *
     * @param state the skull block state to mutate
     * @param base64 the base64 {@code textures} value
     */
    private static void applyBase64(final Skull state, final String base64) {
        if (MODERN_PROFILE) {
            applyModernBlockProfile(state, base64);
        } else {
            applyLegacyBlockProfile(state, base64);
        }
    }

    /**
     * Modern (1.18.1+) route: build a {@link PlayerProfile} and let Bukkit
     * handle the internals.
     *
     * @param meta the skull meta to mutate
     * @param base64 the base64 {@code textures} value
     */
    private static void applyModernProfile(final SkullMeta meta, final String base64) {
        meta.setOwnerProfile(profileFromBase64(base64));
    }

    /**
     * Modern (1.18.1+) route for a placed skull block.
     *
     * @param state the skull block state to mutate
     * @param base64 the base64 {@code textures} value
     */
    private static void applyModernBlockProfile(final Skull state, final String base64) {
        state.setOwnerProfile(profileFromBase64(base64));
    }

    /**
     * Builds a {@link PlayerProfile} carrying the skin URL decoded from the
     * base64 textures value.
     *
     * @param base64 the base64 {@code textures} value
     * @return the populated player profile
     */
    private static PlayerProfile profileFromBase64(final String base64) {
        final String url = extractSkinUrl(base64);
        final PlayerProfile profile = Bukkit.createPlayerProfile(deterministicUuid(base64));
        final PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(url));
        } catch (final Exception exception) {
            throw new IllegalArgumentException("Invalid skin URL in texture: " + url, exception);
        }
        profile.setTextures(textures);
        return profile;
    }

    /**
     * Legacy (1.8 - 1.18) route: reflect {@code setProfile}, else set the
     * private {@code profile} field.
     *
     * @param meta the skull meta to mutate
     * @param base64 the base64 {@code textures} value
     */
    private static void applyLegacyMetaProfile(final SkullMeta meta, final String base64) {
        final Object profile = gameProfileFromBase64(base64);
        try {
            if (metaSetProfileMethod == null) {
                metaSetProfileMethod = meta.getClass().getDeclaredMethod("setProfile", gameProfileClass());
                metaSetProfileMethod.setAccessible(true);
            }
            metaSetProfileMethod.invoke(meta, profile);
        } catch (final ReflectiveOperationException noMethod) {
            try {
                if (metaProfileField == null) {
                    metaProfileField = meta.getClass().getDeclaredField("profile");
                    metaProfileField.setAccessible(true);
                }
                metaProfileField.set(meta, profile);
            } catch (final ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot set skull profile on this server", failure);
            }
        }
    }

    /**
     * Legacy (1.8 - 1.18) route: set the private {@code profile} field on the
     * block state.
     *
     * @param state the skull block state to mutate
     * @param base64 the base64 {@code textures} value
     */
    private static void applyLegacyBlockProfile(final Skull state, final String base64) {
        try {
            if (blockProfileField == null) {
                blockProfileField = state.getClass().getDeclaredField("profile");
                blockProfileField.setAccessible(true);
            }
            blockProfileField.set(state, gameProfileFromBase64(base64));
        } catch (final ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot set skull profile on this server", failure);
        }
    }

    /**
     * Builds a {@code com.mojang.authlib.GameProfile} with the base64 texture,
     * entirely by reflection so the library keeps zero compile dependencies and
     * never touches the {@code Property} record change on newer authlib.
     *
     * @param base64 the base64 {@code textures} value
     * @return the populated game profile
     */
    private static Object gameProfileFromBase64(final String base64) {
        try {
            final Class<?> profileClass = gameProfileClass();
            final Object profile = profileClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(deterministicUuid(base64), "");

            final Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            final Object property = newProperty(propertyClass, base64);

            final Object properties = profileClass.getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(properties, "textures", property);
            return profile;
        } catch (final ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot build a skull profile on this server", failure);
        }
    }

    /**
     * Builds a {@code com.mojang.authlib.properties.Property}, handling the
     * two-arg constructor pre-1.20.5 and the three-arg one after.
     *
     * @param propertyClass the resolved {@code Property} class
     * @param base64 the base64 {@code textures} value
     * @return the property instance
     * @throws ReflectiveOperationException if no known constructor is present
     */
    private static Object newProperty(final Class<?> propertyClass, final String base64)
            throws ReflectiveOperationException {
        try {
            return propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", base64);
        } catch (final NoSuchMethodException newerAuthlib) {
            return propertyClass.getConstructor(String.class, String.class, String.class)
                    .newInstance("textures", base64, null);
        }
    }

    /**
     * Resolves and caches the {@code com.mojang.authlib.GameProfile} class.
     *
     * @return the game profile class
     * @throws ClassNotFoundException if authlib is absent from the classpath
     */
    private static Class<?> gameProfileClass() throws ClassNotFoundException {
        if (gameProfileClass == null) {
            gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        }
        return gameProfileClass;
    }

    /**
     * Sets the skull owner from an offline player, version-safe. Uses
     * {@code setOwningPlayer} (1.12.1+) and falls back to the deprecated
     * {@code setOwner(name)} on 1.7 - 1.11 where the former does not exist.
     *
     * @param meta the skull meta to mutate
     * @param owner the owning player
     */
    @SuppressWarnings("deprecation")
    private static void setOwner(final SkullMeta meta, final OfflinePlayer owner) {
        try {
            meta.setOwningPlayer(owner);
        } catch (final NoSuchMethodError legacy) {
            meta.setOwner(owner.getName());
        }
    }

    /**
     * Sets a skull block's owner from an offline player, version-safe (same
     * {@code setOwningPlayer} / {@code setOwner} fallback as the item variant).
     *
     * @param state the skull block state to mutate
     * @param owner the owning player
     */
    @SuppressWarnings("deprecation")
    private static void setBlockOwner(final Skull state, final OfflinePlayer owner) {
        try {
            state.setOwningPlayer(owner);
        } catch (final NoSuchMethodError legacy) {
            state.setOwner(owner.getName());
        }
    }

    /**
     * Turns a block into a player-skull, version-safe.
     *
     * @param block the target block
     */
    private static void setToSkull(final Block block) {
        try {
            block.setType(Material.valueOf("PLAYER_HEAD"), false);
        } catch (final IllegalArgumentException legacy) {
            block.setType(Material.valueOf("SKULL"), false);
            final Skull state = (Skull) block.getState();
            state.setSkullType(SkullType.PLAYER);
            state.update(false, false);
        }
    }

    /**
     * Encodes a skin URL into a base64 {@code textures} value.
     *
     * @param url the skin URL
     * @return the base64 {@code textures} value
     */
    private static String urlToBase64(final String url) {
        final URI actual;
        try {
            actual = new URI(url);
        } catch (final Exception exception) {
            throw new IllegalArgumentException("Invalid skin URL: " + url, exception);
        }
        final String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + actual + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a base64 {@code textures} value and extracts its {@code SKIN} url.
     *
     * @param base64 the base64 {@code textures} value
     * @return the skin URL
     */
    private static String extractSkinUrl(final String base64) {
        final String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        final Matcher matcher = SKIN_URL.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Texture value has no SKIN url");
        }
        return matcher.group(1);
    }

    /**
     * Derives a stable UUID from the texture, so identical heads stack.
     *
     * @param base64 the base64 {@code textures} value
     * @return the deterministic UUID
     */
    private static UUID deterministicUuid(final String base64) {
        final int len = base64.length();
        final String tail = len >= 20 ? base64.substring(len - 20) : base64;
        final String half = len >= 10 ? base64.substring(len - 10) : base64;
        return new UUID(tail.hashCode(), half.hashCode());
    }

    /**
     * Returns the shared Mojang provider, creating it on first use.
     *
     * @return the shared provider
     */
    private static MojangSkinProvider sharedProvider() {
        MojangSkinProvider provider = sharedProvider;
        if (provider == null) {
            synchronized (HeadItem.class) {
                provider = sharedProvider;
                if (provider == null) {
                    provider = new MojangSkinProvider();
                    sharedProvider = provider;
                }
            }
        }
        return provider;
    }

    /**
     * Throws {@link NullPointerException} with a named message when the value
     * is {@code null}.
     *
     * @param value the value to check
     * @param name the argument name for the message
     */
    private static void notNull(final Object value, final String name) {
        if (value == null) {
            throw new NullPointerException(name + " cannot be null");
        }
    }

    /**
     * Detects whether the server exposes the modern {@link PlayerProfile} API
     * ({@code SkullMeta#setOwnerProfile}, added in 1.18.1), without loading the
     * class on servers that don't have it.
     *
     * @return {@code true} when the modern profile API is available
     */
    private static boolean detectModernProfile() {
        try {
            Class.forName("org.bukkit.profile.PlayerProfile");
            Class.forName("org.bukkit.inventory.meta.SkullMeta")
                    .getMethod("setOwnerProfile", Class.forName("org.bukkit.profile.PlayerProfile"));
            return true;
        } catch (final ReflectiveOperationException legacy) {
            return false;
        }
    }
}
