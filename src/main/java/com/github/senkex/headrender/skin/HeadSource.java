package com.github.senkex.headrender.skin;

import com.github.senkex.headrender.RenderOptions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Identifies <b>where</b> the skin behind a head comes from.
 *
 * <p>A source is a {@link Type} plus its raw value, optionally carrying an
 * explicit helmet (outer layer) override. It is what the {@code <head:...>} tag
 * and the {@code %head:...%} placeholder resolve to, and what
 * {@link SourceSkinProvider} knows how to fetch.</p>
 *
 * <h2>Syntax</h2>
 *
 * <p><b>Explicit</b> — prefix the value with the type:</p>
 * <pre>{@code
 * player:Senkex
 * uuid:1f085b2d-9548-4159-a8c7-f3ccdf0c2054
 * base64:eyJ0aW1lc3RhbXAiOjE2...
 * url:https://textures.minecraft.net/texture/abc123
 * texture:entity/player/wide/steve
 * }</pre>
 *
 * <p><b>Implicit</b> — omit the type and it is detected, using the same ordering
 * Kyori Adventure's {@code <head:...>} tag uses, extended with URL and base64:</p>
 * <ol>
 *   <li>starts with {@code http://} / {@code https://} &rarr; {@link Type#URL}</li>
 *   <li>parses as a dashed or trimmed UUID &rarr; {@link Type#UUID}</li>
 *   <li>long base64 blob &rarr; {@link Type#BASE64}</li>
 *   <li>contains {@code /} &rarr; {@link Type#TEXTURE} (vanilla texture key)</li>
 *   <li>otherwise &rarr; {@link Type#PLAYER}</li>
 * </ol>
 *
 * <p>Either form may be suffixed with {@code :true} / {@code :false} to force
 * the helmet layer for that head alone, e.g. {@code Senkex:false}.</p>
 *
 * <h2>Untrusted input</h2>
 *
 * <p>{@link #parse(String)} is <b>safe by default</b>: it applies
 * {@link Policy#SAFE}, which rejects URLs pointing anywhere other than the known
 * skin hosts. This matters because {@link Type#URL} and {@link Type#BASE64} make
 * the server perform an outbound request on behalf of whoever wrote the tag. If
 * head tags can reach you from chat, signs, books, nicknames or any other
 * player-controlled text, an unrestricted parse is a server-side request forgery
 * hole: {@code <head:url:http://localhost:8123/>} would have your server probe
 * its own internal services.</p>
 *
 * <p>Use {@link #parse(String, Policy)} with {@link Policy#TRUSTED} only for
 * strings you authored yourself, such as values read from your plugin's own
 * config file.</p>
 *
 * <p>This class is immutable and allocates nothing beyond the parse itself; it
 * holds no cache, no executor and no background state.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HeadSource {

    private static final Pattern TRIMMED_UUID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern BASE64_BLOB = Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}");

    /**
     * Longest accepted raw source string, in characters.
     *
     * <p>A Mojang {@code textures} blob sits comfortably under 1 KB. The ceiling
     * keeps a hostile or malformed tag from turning into a multi-megabyte string
     * that is decoded, hashed and used as a cache key.</p>
     */
    public static final int MAX_LENGTH = 8192;

    /**
     * Longest accepted player name, matching vanilla's own validation.
     */
    public static final int MAX_NAME_LENGTH = 16;

    /**
     * The kind of value a {@link HeadSource} carries.
     *
     * <p>Developed by <b>Senkex</b></p>
     */
    public enum Type {

        /**
         * A player name, resolved against the configured skin provider.
         *
         * <p>Aliases: {@code player}, {@code name}.</p>
         */
        PLAYER("player", "name"),

        /**
         * A player UUID, dashed or trimmed.
         *
         * <p>Aliases: {@code uuid}, {@code id}.</p>
         */
        UUID("uuid", "id"),

        /**
         * A Mojang {@code textures} profile property: the raw base64 JSON blob.
         *
         * <p>Aliases: {@code base64}, {@code value}, {@code textures}.</p>
         */
        BASE64("base64", "value", "textures"),

        /**
         * A direct URL to a skin sheet or an already-cropped face image.
         *
         * <p>Alias: {@code url}.</p>
         */
        URL("url"),

        /**
         * A vanilla namespaced texture key such as
         * {@code entity/player/wide/steve}.
         *
         * <p>This is the type Adventure's {@code <head:...>} tag produces for
         * slash-containing arguments. Rendering it requires the texture to be
         * registered on the {@link SourceSkinProvider}, since the library ships
         * no vanilla assets.</p>
         *
         * <p>Aliases: {@code texture}, {@code key}.</p>
         */
        TEXTURE("texture", "key");

        private final String canonical;
        private final String[] aliases;

        Type(final String canonical, final String... aliases) {
            this.canonical = canonical;
            this.aliases = aliases;
        }

        /**
         * Returns the canonical lowercase prefix for this type.
         *
         * @return the canonical prefix, e.g. {@code "player"}
         */
        public String canonical() {
            return canonical;
        }

        /**
         * Returns whether resolving this type performs an outbound HTTP request
         * to a location named by the source itself.
         *
         * <p>{@link #PLAYER} and {@link #UUID} also hit the network, but only at
         * addresses your own provider chain decides. These two let the tag
         * author choose the address, which is what makes them dangerous on
         * untrusted input.</p>
         *
         * @return {@code true} for {@link #URL} and {@link #BASE64}
         */
        public boolean isAuthorControlledFetch() {
            return this == URL || this == BASE64;
        }

        static Type byPrefix(final String prefix) {
            final String lower = prefix.toLowerCase(Locale.ROOT);
            for (final Type type : values()) {
                if (type.canonical.equals(lower)) {
                    return type;
                }
                for (final String alias : type.aliases) {
                    if (alias.equals(lower)) {
                        return type;
                    }
                }
            }
            return null;
        }
    }

    /**
     * Decides which sources a parse will accept.
     *
     * <p>Instances are immutable and cheap to share; build one per plugin and
     * reuse it. Policies are pure validation — they hold no state and touch no
     * network.</p>
     *
     * <p>Developed by <b>Senkex</b></p>
     */
    public static final class Policy {

        /**
         * Hosts trusted by {@link #SAFE}: Mojang plus the avatar proxies this
         * library ships providers for.
         */
        public static final Set<String> DEFAULT_HOSTS = Collections.unmodifiableSet(
                new LinkedHashSet<>(Arrays.asList(
                        "textures.minecraft.net",
                        "minotar.net",
                        "crafatar.com",
                        "mc-heads.net",
                        "api.mineatar.io",
                        "visage.surgeplay.com",
                        "skins.mcstatistics.org"
                )));

        /**
         * The default policy: every type is allowed, but {@link Type#URL} and
         * {@link Type#BASE64} may only reference {@link #DEFAULT_HOSTS} over
         * {@code https}.
         *
         * <p>Safe to run against player-controlled text.</p>
         */
        public static final Policy SAFE = new Policy(
                EnumSetOf(Type.values()), DEFAULT_HOSTS, true);

        /**
         * Allows anything, including arbitrary URLs and plain {@code http}.
         *
         * <p><b>Never apply this to text a player can influence.</b> Use it only
         * for strings you wrote yourself.</p>
         */
        public static final Policy TRUSTED = new Policy(
                EnumSetOf(Type.values()), null, false);

        /**
         * Allows only {@link Type#PLAYER} and {@link Type#UUID}, so no source
         * can ever name its own fetch address.
         *
         * <p>The tightest option, and a good fit for public chat.</p>
         */
        public static final Policy NAMES_ONLY = new Policy(
                EnumSetOf(Type.PLAYER, Type.UUID), Collections.emptySet(), true);

        private static Set<Type> EnumSetOf(final Type... types) {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(types)));
        }

        private final Set<Type> allowed;
        private final Set<String> hosts;
        private final boolean requireHttps;

        private Policy(final Set<Type> allowed, final Set<String> hosts, final boolean requireHttps) {
            this.allowed = allowed;
            this.hosts = hosts;
            this.requireHttps = requireHttps;
        }

        /**
         * Creates a policy allowing the given types, with the default host
         * allowlist and {@code https} required.
         *
         * @param types the permitted source types
         * @return the new policy
         */
        public static Policy allowing(final Type... types) {
            Objects.requireNonNull(types, "Types cannot be null");
            return new Policy(EnumSetOf(types), DEFAULT_HOSTS, true);
        }

        /**
         * Returns a copy of this policy with the given host allowlist.
         *
         * <p>Hosts are matched case-insensitively and exactly; no wildcard or
         * suffix matching is performed, because suffix matching on hostnames is
         * how allowlists get bypassed.</p>
         *
         * @param hosts the permitted hostnames
         * @return the derived policy
         */
        public Policy withHosts(final String... hosts) {
            Objects.requireNonNull(hosts, "Hosts cannot be null");
            final Set<String> set = new LinkedHashSet<>();
            for (final String host : hosts) {
                set.add(Objects.requireNonNull(host, "Host cannot be null").toLowerCase(Locale.ROOT));
            }
            return new Policy(allowed, Collections.unmodifiableSet(set), requireHttps);
        }

        /**
         * Returns a copy of this policy that also accepts plain {@code http}.
         *
         * @return the derived policy
         */
        public Policy allowingPlainHttp() {
            return new Policy(allowed, hosts, false);
        }

        /**
         * Returns whether the given type is permitted.
         *
         * @param type the type to test
         * @return {@code true} when permitted
         */
        public boolean allows(final Type type) {
            return allowed.contains(type);
        }

        void check(final Type type, final String value) {
            if (!allowed.contains(type)) {
                throw new IllegalArgumentException(
                        "Head source type " + type.canonical() + " is not allowed by this policy");
            }
            if (type == Type.PLAYER) {
                checkName(value);
            }
            if (type == Type.URL) {
                checkUrl(value);
            }
        }

        private void checkName(final String name) {
            if (name.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("Player name is too long: " + name);
            }
            // Vanilla's own rule: printable ASCII, no spaces.
            for (int i = 0; i < name.length(); i++) {
                final char c = name.charAt(i);
                if (c <= ' ' || c > '~') {
                    throw new IllegalArgumentException("Invalid character in player name: " + name);
                }
            }
        }

        private void checkUrl(final String value) {
            // hosts == null means "any host", used only by TRUSTED.
            if (hosts == null) {
                return;
            }

            final URI uri;
            try {
                uri = new URI(value);
            } catch (final URISyntaxException exception) {
                throw new IllegalArgumentException("Malformed head source URL: " + value);
            }

            final String scheme = uri.getScheme() == null
                    ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (requireHttps ? !scheme.equals("https") : !(scheme.equals("https") || scheme.equals("http"))) {
                throw new IllegalArgumentException(
                        "Head source URL must use " + (requireHttps ? "https" : "http or https") + ": " + value);
            }

            // Credentials in the authority are a classic way to make a URL look
            // like it points at an allowed host when it does not.
            if (uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Head source URL must not carry credentials");
            }

            final String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("Head source URL has no host: " + value);
            }
            if (!hosts.contains(host.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Head source host '" + host + "' is not allowed. Allowed: " + hosts);
            }
        }
    }

    private final Type type;
    private final String value;
    private final Boolean helmet;

    private HeadSource(final Type type, final String value, final Boolean helmet) {
        this.type = type;
        this.value = value;
        this.helmet = helmet;
    }

    /**
     * Creates a source of the given type without a helmet override.
     *
     * <p>Built directly, so no policy is applied: the caller is stating the type
     * rather than parsing untrusted text.</p>
     *
     * @param type the source type, must not be {@code null}
     * @param value the raw value, must not be {@code null} or empty
     * @return the new source
     */
    public static HeadSource of(final Type type, final String value) {
        return of(type, value, null);
    }

    /**
     * Creates a source of the given type with an explicit helmet override.
     *
     * @param type the source type, must not be {@code null}
     * @param value the raw value, must not be {@code null} or empty
     * @param helmet {@code true} or {@code false} to force the helmet layer, or
     *               {@code null} to inherit it from the render options
     * @return the new source
     */
    public static HeadSource of(final Type type, final String value, final Boolean helmet) {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Value cannot be empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Head source exceeds " + MAX_LENGTH + " characters");
        }
        return new HeadSource(type, value, helmet);
    }

    /**
     * Parses a tag argument into a source under {@link Policy#SAFE}.
     *
     * <p>This is the overload the tag parsers use, so text arriving from players
     * cannot point the server at an arbitrary address.</p>
     *
     * @param raw the raw argument, must not be {@code null}
     * @return the parsed source, never {@code null}
     * @throws IllegalArgumentException if the argument is empty, malformed,
     *                                  oversized, or rejected by the policy
     */
    public static HeadSource parse(final String raw) {
        return parse(raw, Policy.SAFE);
    }

    /**
     * Parses a tag argument into a source under the given policy.
     *
     * <p>Handles the explicit {@code type:value} form, the implicit form
     * resolved by {@link #detect(String)}, and the optional trailing
     * {@code :true} / {@code :false} helmet override.</p>
     *
     * @param raw the raw argument, must not be {@code null}
     * @param policy the policy to enforce, must not be {@code null}
     * @return the parsed source, never {@code null}
     * @throws IllegalArgumentException if the argument is empty, malformed,
     *                                  oversized, or rejected by the policy
     */
    public static HeadSource parse(final String raw, final Policy policy) {
        Objects.requireNonNull(raw, "Raw value cannot be null");
        Objects.requireNonNull(policy, "Policy cannot be null");

        if (raw.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Head source exceeds " + MAX_LENGTH + " characters");
        }

        String remaining = raw.trim();
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("Head source cannot be empty");
        }

        // A lone boolean is the helmet flag, matching how Adventure reads
        // <head:false>. Adventure can pair it with an empty profile; HeadRender
        // always needs something to draw, so this is an error here.
        if (remaining.equalsIgnoreCase("true") || remaining.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(
                    "Head source carries a helmet flag but no target: " + raw);
        }

        // Trailing helmet override: <head:Senkex:false>. Split from the right so
        // it never eats into a URL's scheme or an explicit type prefix.
        Boolean helmet = null;
        final int lastColon = remaining.lastIndexOf(':');
        if (lastColon > 0) {
            final String tail = remaining.substring(lastColon + 1);
            if (tail.equalsIgnoreCase("true")) {
                helmet = Boolean.TRUE;
                remaining = remaining.substring(0, lastColon);
            } else if (tail.equalsIgnoreCase("false")) {
                helmet = Boolean.FALSE;
                remaining = remaining.substring(0, lastColon);
            }
        }

        if (remaining.isEmpty()) {
            throw new IllegalArgumentException(
                    "Head source carries a helmet flag but no target: " + raw);
        }

        Type type = null;
        String value = remaining;

        final int firstColon = remaining.indexOf(':');
        if (firstColon > 0) {
            final Type explicit = Type.byPrefix(remaining.substring(0, firstColon));
            if (explicit != null) {
                type = explicit;
                value = remaining.substring(firstColon + 1);
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("Head source has a type but no value: " + raw);
                }
            }
        }

        if (type == null) {
            type = detect(remaining);
        }

        policy.check(type, value);
        return new HeadSource(type, value, helmet);
    }

    /**
     * Detects the type of a value written without an explicit prefix.
     *
     * <p>Pure classification: no policy is applied and nothing is rejected.</p>
     *
     * @param value the raw value, must not be {@code null}
     * @return the detected type, never {@code null}
     */
    public static Type detect(final String value) {
        Objects.requireNonNull(value, "Value cannot be null");

        final String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return Type.URL;
        }
        if (TRIMMED_UUID.matcher(value.replace("-", "")).matches()) {
            return Type.UUID;
        }
        // Mojang texture blobs run a few hundred base64 characters; the length
        // floor keeps 16-char player names out of this branch.
        if (BASE64_BLOB.matcher(value).matches()) {
            return Type.BASE64;
        }
        if (value.indexOf('/') >= 0) {
            return Type.TEXTURE;
        }
        return Type.PLAYER;
    }

    /**
     * Returns the type of this source.
     *
     * @return the source type, never {@code null}
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the raw value, stripped of its type prefix and helmet suffix.
     *
     * @return the value, never {@code null}
     */
    public String value() {
        return value;
    }

    /**
     * Returns the explicit helmet override carried by this source.
     *
     * @return {@link Boolean#TRUE} or {@link Boolean#FALSE} when the tag forced
     *         the helmet layer, or {@code null} to inherit from the options
     */
    public Boolean helmet() {
        return helmet;
    }

    /**
     * Returns the canonical {@code type:value} form of this source.
     *
     * <p>The helmet override is deliberately excluded: it belongs to the render
     * options, not to the identity of the skin. The result feeds back into
     * {@link #parse(String, Policy)} unchanged, which is what lets a source
     * travel through the plain-{@link String}
     * {@link com.github.senkex.headrender.api.SkinProvider} contract without
     * widening the interface.</p>
     *
     * @return the canonical form, e.g. {@code "player:Senkex"}
     */
    public String canonical() {
        return type.canonical() + ':' + value;
    }

    /**
     * Applies this source's helmet override to the given options.
     *
     * <p>Returns the argument itself when there is nothing to override, so the
     * common path allocates nothing.</p>
     *
     * @param options the base options, must not be {@code null}
     * @return the options unchanged when no override applies, otherwise a copy
     *         with the helmet layer forced
     */
    public RenderOptions applyTo(final RenderOptions options) {
        Objects.requireNonNull(options, "Options cannot be null");
        if (helmet == null || helmet == options.useHelmetLayer()) {
            return options;
        }
        return options.toBuilder().helmetLayer(helmet).build();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HeadSource)) {
            return false;
        }
        final HeadSource that = (HeadSource) other;
        return type == that.type
                && value.equals(that.value)
                && Objects.equals(helmet, that.helmet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, helmet);
    }

    @Override
    public String toString() {
        return helmet == null ? canonical() : canonical() + ':' + helmet;
    }
}
