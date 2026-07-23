package com.github.senkex.headrender.text.adventure;

import com.github.senkex.headrender.RenderOptions;
import com.github.senkex.headrender.api.HeadRenderService;
import com.github.senkex.headrender.skin.HeadSource;
import com.github.senkex.headrender.text.HeadTagParser;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

/**
 * A MiniMessage {@link TagResolver} for {@code <head:...>}, mirroring the tag
 * Kyori Adventure added in {@code 4.25.0} but rendering through HeadRender's
 * resource pack instead of the vanilla {@code object} component.
 *
 * <p>The difference matters: Adventure's own tag emits a native player-head
 * object component, which only the {@code 1.21.9+} client can draw. This one
 * emits a font glyph, so it works on <b>every version since 1.16</b> as long as
 * the client has the generated pack.</p>
 *
 * <p><b>Requires a single-row renderer.</b> A MiniMessage tag has to resolve to
 * one inline component, so the backing service must use
 * {@link com.github.senkex.headrender.render.FontHeadRenderer} (one glyph per
 * head). Constructing this class against the multi-row
 * {@link com.github.senkex.headrender.render.HexPixelRenderer} fails fast.</p>
 *
 * <p><b>Rendering is asynchronous, MiniMessage is not.</b> Tag resolution cannot
 * block the calling thread, so glyphs are served from an internal cache that you
 * fill with {@link #preload(String)} before deserializing:</p>
 *
 * <pre>{@code
 * HeadRenderTags tags = HeadRenderTags.create(service, Key.key("headrender:heads"));
 *
 * String raw = "<gray>Bienvenido <head:Senkex> <yellow>Senkex";
 * tags.preload(raw).thenAccept(ignored -> {
 *     Component message = MiniMessage.miniMessage().deserialize(raw, tags.resolver());
 *     player.sendMessage(message);
 * });
 * }</pre>
 *
 * <p>A tag whose head is not cached yet resolves to {@link #fallback()} and
 * schedules a background render, so the next pass through the same text draws
 * it. Preloading simply removes that first miss.</p>
 *
 * <p>Requires the optional {@code net.kyori:adventure-text-minimessage}
 * dependency on the classpath. It is already present on Paper 1.18+.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HeadRenderTags {

    /**
     * The tag name resolved by this resolver, matching Adventure's own.
     */
    public static final String TAG = "head";

    private final HeadRenderService service;
    private final Key font;
    private final RenderOptions options;
    private final Component fallback;
    /**
     * Most glyphs kept in memory at once.
     *
     * <p>Each entry is a {@link HeadSource} key and a one-character string, so
     * the whole map is a few kilobytes at capacity. The bound exists because
     * head tags can come from player-controlled text: without it, a player
     * cycling through names would grow this map forever.</p>
     */
    public static final int MAX_CACHED_GLYPHS = 512;

    /**
     * Most renders allowed to be in flight at once.
     *
     * <p>Caps how much work a single burst of uncached tags can queue onto the
     * service.</p>
     */
    public static final int MAX_PENDING_RENDERS = 64;

    private final Map<HeadSource, String> glyphs = Collections.synchronizedMap(
            new LinkedHashMap<HeadSource, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<HeadSource, String> eldest) {
                    return size() > MAX_CACHED_GLYPHS;
                }
            });
    private final Set<HeadSource> pending = ConcurrentHashMap.newKeySet();

    private HeadRenderTags(final HeadRenderService service,
                           final Key font,
                           final RenderOptions options,
                           final Component fallback) {
        this.service = service;
        this.font = font;
        this.options = options;
        this.fallback = fallback;

        if (service.getRenderer().rows(options.getSize()) != 1) {
            throw new IllegalArgumentException(
                    "MiniMessage head tags need a single-row renderer; "
                            + service.getRenderer().getClass().getSimpleName()
                            + " renders " + service.getRenderer().rows(options.getSize())
                            + " rows. Use FontHeadRenderer with a generated resource pack.");
        }
    }

    /**
     * Creates a resolver over the given service using default options and an
     * empty fallback.
     *
     * @param service the backing service, must use a single-row renderer
     * @param font the pack font key the glyphs live in
     * @return the new tag resolver holder
     */
    public static HeadRenderTags create(final HeadRenderService service, final Key font) {
        return create(service, font, RenderOptions.defaults(), Component.empty());
    }

    /**
     * Creates a resolver over the given service.
     *
     * @param service the backing service, must use a single-row renderer
     * @param font the pack font key the glyphs live in
     * @param options the render configuration applied to every head
     * @param fallback the component emitted while a head is not cached yet
     * @return the new tag resolver holder
     */
    public static HeadRenderTags create(final HeadRenderService service,
                                        final Key font,
                                        final RenderOptions options,
                                        final Component fallback) {
        Objects.requireNonNull(service, "Service cannot be null");
        Objects.requireNonNull(font, "Font cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");
        Objects.requireNonNull(fallback, "Fallback cannot be null");
        return new HeadRenderTags(service, font, options, fallback);
    }

    /**
     * Returns the MiniMessage resolver for the {@code <head:...>} tag.
     *
     * <p>Pass it to {@code MiniMessage#deserialize(String, TagResolver...)}, or
     * bake it into a {@code MiniMessage} instance with
     * {@code MiniMessage.builder().editTags(b -> b.resolver(tags.resolver()))}.</p>
     *
     * @return the resolver
     */
    public TagResolver resolver() {
        return TagResolver.resolver(TAG, this::resolve);
    }

    /**
     * Returns the component emitted for a head that is not cached yet.
     *
     * @return the fallback component
     */
    public Component fallback() {
        return fallback;
    }

    /**
     * Returns a resolver for {@code <head:...>} backed by Minecraft's
     * <b>native</b> player-head component instead of a rendered glyph.
     *
     * <p>Needs no service, no pack, no preloading and no cache: resolution is
     * immediate and free, because the client does the drawing. The catch is that
     * it only displays on {@code 1.21.9+} clients — see
     * {@link NativeHeadComponents} for the full trade-off.</p>
     *
     * <pre>{@code
     * Component msg = MiniMessage.miniMessage()
     *         .deserialize("<gray>Hola <head:Senkex>!", HeadRenderTags.nativeResolver());
     * }</pre>
     *
     * <p>It accepts HeadRender's extended syntax — explicit {@code type:value}
     * prefixes, {@code base64:}, the {@code :false} helmet suffix — which is a
     * superset of what Adventure's own tag understands.</p>
     *
     * @return the resolver
     * @throws IllegalStateException if the running Adventure predates {@code 4.25.0}
     */
    public static TagResolver nativeResolver() {
        if (!NativeHeadComponents.isSupported()) {
            throw new IllegalStateException(
                    "Native player-head components need Adventure 4.25.0+. "
                            + "Use HeadRenderTags.create(...) for the resource-pack path instead.");
        }
        return TagResolver.resolver(TAG, HeadRenderTags::resolveNative);
    }

    private static Tag resolveNative(final ArgumentQueue args, final Context ctx) throws ParsingException {
        final HeadSource source = readSource(args, ctx);
        try {
            return Tag.selfClosingInserting(NativeHeadComponents.playerHead(source));
        } catch (final UnsupportedOperationException exception) {
            throw ctx.newException(exception.getMessage());
        }
    }

    private static HeadSource readSource(final ArgumentQueue args, final Context ctx) throws ParsingException {
        if (!args.hasNext()) {
            throw ctx.newException("The head tag needs a name, uuid, base64, url or texture argument");
        }

        // MiniMessage splits on ':', so <head:base64:eyJ0...> and
        // <head:Senkex:false> arrive as separate arguments. Re-joining them
        // hands HeadSource the exact string the author wrote.
        final StringBuilder raw = new StringBuilder(args.pop().value());
        while (args.hasNext()) {
            raw.append(':').append(args.pop().value());
        }

        try {
            return HeadSource.parse(raw.toString());
        } catch (final IllegalArgumentException exception) {
            throw ctx.newException("Invalid head source: " + exception.getMessage());
        }
    }

    /**
     * Renders every {@code <head:...>} in the given text and caches the result.
     *
     * <p>Await the returned future before deserializing so no tag falls back.
     * Heads already cached are skipped, so calling this on every message is
     * cheap once the working set is warm.</p>
     *
     * @param text the MiniMessage input, must not be {@code null}
     * @return a future completed once every head in the text is cached
     */
    public CompletableFuture<Void> preload(final String text) {
        Objects.requireNonNull(text, "Text cannot be null");

        final Set<HeadSource> sources = new LinkedHashSet<>();
        final Matcher matcher = HeadTagParser.SEQUENTIAL.matcher(text);
        while (matcher.find()) {
            try {
                sources.add(HeadSource.parse(matcher.group(1)));
            } catch (final IllegalArgumentException ignored) {
                // Malformed tags are reported at deserialization time, where the
                // MiniMessage context can point at the offending column.
            }
        }

        final List<CompletableFuture<?>> futures = new ArrayList<>(sources.size());
        for (final HeadSource source : sources) {
            if (!glyphs.containsKey(source)) {
                futures.add(warm(source));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }

    /**
     * Drops every cached glyph.
     *
     * <p>Call after regenerating the backing pack, since glyph characters are
     * only meaningful for the pack that baked them.</p>
     */
    public void clear() {
        glyphs.clear();
        pending.clear();
    }

    private Tag resolve(final ArgumentQueue args, final Context ctx) throws ParsingException {
        final HeadSource source = readSource(args, ctx);

        final String glyph = glyphs.get(source);
        if (glyph == null) {
            warm(source);
            return Tag.selfClosingInserting(fallback);
        }
        return Tag.selfClosingInserting(Component.text(glyph).font(font));
    }

    private CompletableFuture<?> warm(final HeadSource source) {
        // Shed load rather than queueing unbounded work behind a tag burst.
        if (pending.size() >= MAX_PENDING_RENDERS) {
            return CompletableFuture.completedFuture(null);
        }
        // Guard against a burst of identical tags each firing its own render.
        if (!pending.add(source)) {
            return CompletableFuture.completedFuture(null);
        }
        return service.render(source, options)
                .thenAccept(lines -> {
                    if (!lines.isEmpty()) {
                        glyphs.put(source, lines.get(0));
                    }
                })
                .whenComplete((ignored, error) -> pending.remove(source));
    }
}
