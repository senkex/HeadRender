package com.github.senkex.headrender.skin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the SSRF boundary.
 *
 * <p>{@code url:} and {@code base64:} make the server perform an outbound
 * request to an address the tag author chose. If head tags can arrive from chat,
 * signs, books or nicknames, every case below is an attack someone will try.
 * These tests are the reason {@link HeadSource#parse(String)} defaults to
 * {@link HeadSource.Policy#SAFE}.</p>
 */
@DisplayName("HeadSource.Policy")
class HeadSourcePolicyTest {

    @Nested
    @DisplayName("SAFE (the default) blocks")
    class SafeBlocks {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "url:http://localhost:8123/admin",
                "url:http://127.0.0.1:25575/",
                "url:https://127.0.0.1/skin.png",
                "url:http://[::1]/skin.png",
                "url:http://192.168.1.1/",
                "url:http://10.0.0.1/",
        })
        @DisplayName("internal and loopback addresses")
        void internalAddresses(final String raw) {
            assertThrows(IllegalArgumentException.class, () -> HeadSource.parse(raw));
        }

        @Test
        @DisplayName("cloud instance metadata, the classic SSRF prize")
        void cloudMetadata() {
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse("url:http://169.254.169.254/latest/meta-data/"));
        }

        @Test
        @DisplayName("a host that merely ends with an allowed one")
        void suffixSpoofing() {
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse("url:https://textures.minecraft.net.evil.com/x.png"));
        }

        @Test
        @DisplayName("an allowed host smuggled into the fragment")
        void fragmentSpoofing() {
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse("url:https://evil.com#textures.minecraft.net"));
        }

        @Test
        @DisplayName("credentials in the authority, which make the host look allowed")
        void userInfoSpoofing() {
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse("url:https://user:pass@textures.minecraft.net/x.png"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "url:file:///etc/passwd",
                "url:jar:file:///tmp/x.jar!/y",
                "url:ftp://textures.minecraft.net/x.png",
        })
        @DisplayName("non-http schemes")
        void badSchemes(final String raw) {
            assertThrows(IllegalArgumentException.class, () -> HeadSource.parse(raw));
        }

        @Test
        @DisplayName("plain http, even on an allowed host")
        void requiresHttps() {
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse("url:http://textures.minecraft.net/x.png"));
        }

        @Test
        @DisplayName("an implicitly detected URL, not just an explicit url: prefix")
        void appliesToDetectedUrls() {
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse("https://internal.corp/skin.png"));
        }
    }

    @Nested
    @DisplayName("SAFE still allows")
    class SafeAllows {

        @ParameterizedTest
        @ValueSource(strings = {
                "url:https://textures.minecraft.net/texture/abc123",
                "https://minotar.net/skin/Ssenkex",
                "https://crafatar.com/skins/1f085b2d95484159a8c7f3ccdf0c2054",
        })
        void knownSkinHosts(final String raw) {
            assertDoesNotThrow(() -> HeadSource.parse(raw));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Ssenkex",
                "1f085b2d-9548-4159-a8c7-f3ccdf0c2054",
                "entity/player/wide/steve",
        })
        @DisplayName("types that never name their own fetch address")
        void nonFetchTypes(final String raw) {
            assertDoesNotThrow(() -> HeadSource.parse(raw));
        }

        @Test
        void hostMatchingIsCaseInsensitive() {
            assertDoesNotThrow(() -> HeadSource.parse("url:https://TEXTURES.MINECRAFT.NET/texture/a"));
        }
    }

    @Nested
    @DisplayName("NAMES_ONLY")
    class NamesOnly {

        @ParameterizedTest
        @ValueSource(strings = {
                "url:https://textures.minecraft.net/texture/a",
                "base64:eyJ0aW1lc3RhbXAiOjE2ODAwMDAwMDAwMDAsInByb2ZpbGVJZCI6ImFiY2RlZjAxMjM0NTY3ODkifQ==",
                "entity/player/wide/steve",
        })
        @DisplayName("refuses everything that is not a name or UUID")
        void refusesOtherTypes(final String raw) {
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse(raw, HeadSource.Policy.NAMES_ONLY));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Ssenkex", "1f085b2d-9548-4159-a8c7-f3ccdf0c2054"})
        void allowsNamesAndUuids(final String raw) {
            assertDoesNotThrow(() -> HeadSource.parse(raw, HeadSource.Policy.NAMES_ONLY));
        }

        @Test
        void reportsWhatItAllows() {
            assertTrue(HeadSource.Policy.NAMES_ONLY.allows(HeadSource.Type.PLAYER));
            assertTrue(HeadSource.Policy.NAMES_ONLY.allows(HeadSource.Type.UUID));
            assertFalse(HeadSource.Policy.NAMES_ONLY.allows(HeadSource.Type.URL));
            assertFalse(HeadSource.Policy.NAMES_ONLY.allows(HeadSource.Type.BASE64));
        }
    }

    @Nested
    @DisplayName("TRUSTED")
    class Trusted {

        @Test
        @DisplayName("allows what SAFE refuses — for your own config only")
        void allowsAnything() {
            assertDoesNotThrow(() -> HeadSource.parse("url:http://localhost:8080/skin.png",
                    HeadSource.Policy.TRUSTED));
            assertDoesNotThrow(() -> HeadSource.parse("url:http://10.0.0.5/skin.png",
                    HeadSource.Policy.TRUSTED));
        }

        @Test
        @DisplayName("still enforces the length ceiling")
        void keepsResourceLimits() {
            final StringBuilder huge = new StringBuilder();
            for (int i = 0; i < HeadSource.MAX_LENGTH + 1; i++) {
                huge.append('A');
            }
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse(huge.toString(), HeadSource.Policy.TRUSTED));
        }
    }

    @Nested
    @DisplayName("custom policies")
    class Custom {

        @Test
        void allowingRestrictsTypes() {
            final HeadSource.Policy onlyPlayers = HeadSource.Policy.allowing(HeadSource.Type.PLAYER);
            assertDoesNotThrow(() -> HeadSource.parse("Ssenkex", onlyPlayers));
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse("1f085b2d-9548-4159-a8c7-f3ccdf0c2054", onlyPlayers));
        }

        @Test
        void withHostsReplacesTheAllowlist() {
            final HeadSource.Policy cdn = HeadSource.Policy.SAFE.withHosts("cdn.myserver.net");
            assertDoesNotThrow(() -> HeadSource.parse("url:https://cdn.myserver.net/a.png", cdn));
            // The defaults are replaced, not extended.
            assertThrows(IllegalArgumentException.class,
                    () -> HeadSource.parse("url:https://textures.minecraft.net/texture/a", cdn));
        }

        @Test
        void allowingPlainHttpRelaxesTheScheme() {
            final HeadSource.Policy lax =
                    HeadSource.Policy.SAFE.withHosts("cdn.myserver.net").allowingPlainHttp();
            assertDoesNotThrow(() -> HeadSource.parse("url:http://cdn.myserver.net/a.png", lax));
        }

        @Test
        @DisplayName("the type flags the danger so callers can reason about it")
        void authorControlledFetchIsMarked() {
            assertTrue(HeadSource.Type.URL.isAuthorControlledFetch());
            assertTrue(HeadSource.Type.BASE64.isAuthorControlledFetch());
            assertFalse(HeadSource.Type.PLAYER.isAuthorControlledFetch());
            assertFalse(HeadSource.Type.UUID.isAuthorControlledFetch());
            assertFalse(HeadSource.Type.TEXTURE.isAuthorControlledFetch());
        }

        @Test
        void defaultHostsAreImmutable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> HeadSource.Policy.DEFAULT_HOSTS.add("evil.com"));
        }

        @Test
        void defaultHostsIncludeMojang() {
            assertTrue(HeadSource.Policy.DEFAULT_HOSTS.contains("textures.minecraft.net"));
            assertEquals(HeadSource.Policy.DEFAULT_HOSTS, HeadSource.Policy.DEFAULT_HOSTS);
        }
    }
}
