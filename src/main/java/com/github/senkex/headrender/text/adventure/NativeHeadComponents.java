package com.github.senkex.headrender.text.adventure;

import com.github.senkex.headrender.skin.HeadSource;
import com.github.senkex.headrender.text.HeadTagParser;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps a {@link HeadSource} onto Minecraft's <b>native player-head object
 * component</b>, added in {@code 1.21.9} and exposed by Adventure {@code 4.25.0}.
 *
 * <p>This is the zero-cost path. Nothing is downloaded, nothing is rendered,
 * nothing is cached and no thread is used: the component simply tells the client
 * which head to draw and the client draws it, at full colour, inline, with no
 * resource pack. Every method here is synchronous and allocation-light.</p>
 *
 * <p>The trade-off is reach. Object components only exist on {@code 1.21.9+}
 * clients; older ones render nothing useful. HeadRender's own glyph and pixel
 * renderers work from {@code 1.16}, so the two complement each other:</p>
 *
 * <pre>{@code
 * if (NativeHeadComponents.isSupported() && viewerIsOn1219Plus) {
 *     player.sendMessage(NativeHeadComponents.playerHead(source));   // free
 * } else {
 *     HeadRenderComponents.renderMultiline(source.value())           // fallback
 *             .thenAccept(player::sendMessage);
 * }
 * }</pre>
 *
 * <p>HeadRender deliberately does <b>not</b> detect the viewer's protocol
 * version for you: that needs ViaVersion or a platform-specific API, and pulling
 * either in would contradict the library staying dependency-free. Ask your
 * platform, then pick a branch.</p>
 *
 * <h2>Source coverage</h2>
 *
 * <table>
 *   <caption>How each source type maps to the vanilla component</caption>
 *   <tr><th>Type</th><th>Native field</th></tr>
 *   <tr><td>{@link HeadSource.Type#PLAYER}</td><td>{@code player} as a name; the client resolves the profile</td></tr>
 *   <tr><td>{@link HeadSource.Type#UUID}</td><td>{@code player.id}; the client resolves the profile</td></tr>
 *   <tr><td>{@link HeadSource.Type#BASE64}</td><td>{@code player.properties[textures]}, no lookup needed</td></tr>
 *   <tr><td>{@link HeadSource.Type#TEXTURE}</td><td>{@code player.texture}, a pack-relative key</td></tr>
 *   <tr><td>{@link HeadSource.Type#URL}</td><td><b>unsupported</b> — vanilla has no field for an arbitrary URL</td></tr>
 * </table>
 *
 * <p>Requires {@code net.kyori:adventure-api} {@code 4.25.0+} at runtime, and
 * nothing else — MiniMessage is deliberately not referenced here so this class
 * loads on any Adventure platform. The MiniMessage resolver backed by these
 * components lives on {@link HeadRenderTags#nativeResolver()}.</p>
 *
 * <p>Guard with {@link #isSupported()} before touching anything else in this
 * class if you also support older servers, since older Adventure builds lack
 * these types entirely.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class NativeHeadComponents {

    /**
     * The tag name this class backs, matching Adventure's own.
     */
    public static final String TAG = HeadRenderTags.TAG;

    private static final String PROBE_CLASS = "net.kyori.adventure.text.object.ObjectContents";
    private static final boolean SUPPORTED = probe();

    private NativeHeadComponents() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static boolean probe() {
        try {
            Class.forName(PROBE_CLASS, false, NativeHeadComponents.class.getClassLoader());
            return true;
        } catch (final Throwable ignored) {
            // Adventure older than 4.25.0, or no Adventure at all.
            return false;
        }
    }

    /**
     * Returns whether the running Adventure exposes object components.
     *
     * <p>Checked once at class-init by looking the type up reflectively, so
     * calling this never throws even on a server whose Adventure predates
     * {@code 4.25.0}. It reports the <b>server's</b> capability, not the
     * viewer's: a {@code 1.21.8} client connected to a {@code 1.21.9} server
     * still cannot draw the component.</p>
     *
     * @return {@code true} when native player heads can be built
     */
    public static boolean isSupported() {
        return SUPPORTED;
    }

    /**
     * Builds a native player-head component for the given source.
     *
     * @param source the skin origin, must not be {@code null}
     * @return the head component
     * @throws UnsupportedOperationException if the source is a
     *         {@link HeadSource.Type#URL}, which vanilla cannot express
     * @throws IllegalArgumentException if the source value is malformed for its type
     */
    public static Component playerHead(final HeadSource source) {
        return Component.object(contents(source));
    }

    /**
     * Builds the native head contents for the given source.
     *
     * <p>Useful when you want to decorate or embed the contents yourself rather
     * than take the ready-made component.</p>
     *
     * @param source the skin origin, must not be {@code null}
     * @return the player-head contents
     * @throws UnsupportedOperationException if the source is a
     *         {@link HeadSource.Type#URL}, which vanilla cannot express
     * @throws IllegalArgumentException if the source value is malformed for its type
     */
    public static PlayerHeadObjectContents contents(final HeadSource source) {
        Objects.requireNonNull(source, "Source cannot be null");

        final PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead();
        final String value = source.value();

        switch (source.type()) {
            case PLAYER:
                builder.name(value);
                break;
            case UUID:
                builder.id(toUuid(value));
                break;
            case BASE64:
                // A signed property is only required where the client validates
                // it; the head object accepts an unsigned textures value.
                builder.profileProperty(PlayerHeadObjectContents.property("textures", value));
                break;
            case TEXTURE:
                builder.texture(Key.key(value));
                break;
            case URL:
            default:
                throw new UnsupportedOperationException(
                        "Vanilla player-head components cannot reference an arbitrary URL. "
                                + "Resolve it to a textures property first, or fall back to "
                                + "HeadRender's own renderer for this source.");
        }

        final Boolean helmet = source.helmet();
        builder.hat(helmet != null ? helmet : PlayerHeadObjectContents.DEFAULT_HAT);
        return builder.build();
    }

    /**
     * Returns whether the given source can be expressed as a native component.
     *
     * @param source the skin origin, must not be {@code null}
     * @return {@code false} for {@link HeadSource.Type#URL}, {@code true} otherwise
     */
    public static boolean canRepresent(final HeadSource source) {
        Objects.requireNonNull(source, "Source cannot be null");
        return source.type() != HeadSource.Type.URL;
    }

    /**
     * Replaces every {@code <head:VALUE>} tag in the given text with a native
     * head component, returning one component for the whole string.
     *
     * <p>Fully synchronous — there is nothing to fetch. Sources that cannot be
     * represented natively are left as their literal tag text rather than
     * throwing, so one bad tag never costs you the whole message.</p>
     *
     * <pre>{@code
     * player.sendMessage(NativeHeadComponents.parseTags("Hola <head:Senkex>!"));
     * }</pre>
     *
     * @param text the text to parse, must not be {@code null}
     * @return the assembled component
     */
    public static Component parseTags(final String text) {
        Objects.requireNonNull(text, "Text cannot be null");

        final List<HeadTagParser.Segment> segments =
                HeadTagParser.parse(text, HeadTagParser.SEQUENTIAL);
        // No tags at all: hand back a plain text component rather than an empty
        // root wrapping a single child.
        if (segments.isEmpty() || (segments.size() == 1 && !segments.get(0).isHead())) {
            return Component.text(text);
        }

        final Component[] parts = new Component[segments.size()];
        for (int i = 0; i < parts.length; i++) {
            final HeadTagParser.Segment segment = segments.get(i);
            if (!segment.isHead()) {
                parts[i] = Component.text(segment.value());
                continue;
            }
            try {
                parts[i] = playerHead(segment.source());
            } catch (final RuntimeException exception) {
                parts[i] = Component.text("<" + TAG + ":" + segment.value() + ">");
            }
        }
        return Component.textOfChildren(parts);
    }

    private static UUID toUuid(final String value) {
        if (value.indexOf('-') >= 0) {
            return UUID.fromString(value);
        }
        if (value.length() != 32) {
            throw new IllegalArgumentException("Not a valid UUID: " + value);
        }
        return UUID.fromString(value.substring(0, 8) + '-'
                + value.substring(8, 12) + '-'
                + value.substring(12, 16) + '-'
                + value.substring(16, 20) + '-'
                + value.substring(20));
    }
}
