package com.github.senkex.headrender.text.adventure;

import com.github.senkex.headrender.skin.HeadSource;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the wire format of the native {@code 1.21.9} player-head component.
 *
 * <p>The expected JSON is taken from Adventure's own {@code ObjectComponentTest}
 * fixtures, so a drift here means we would be sending the client something it
 * cannot read.</p>
 */
@DisplayName("NativeHeadComponents")
class NativeHeadComponentsTest {

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final String BASE64 =
            "eyJ0aW1lc3RhbXAiOjE2ODAwMDAwMDAwMDAsInByb2ZpbGVJZCI6ImFiY2RlZjAxMjM0NTY3ODkiLCJ0ZXh0dXJlcyI6e319";

    private static String json(final String raw) {
        return GSON.serialize(NativeHeadComponents.playerHead(HeadSource.parse(raw)));
    }

    @Test
    @DisplayName("the test classpath has Adventure 4.25+, so the probe reports true")
    void isSupported() {
        assertTrue(NativeHeadComponents.isSupported());
    }

    @Nested
    @DisplayName("wire format")
    class WireFormat {

        @Test
        @DisplayName("a bare name uses the short string form")
        void playerName() {
            assertEquals("{\"hat\":true,\"player\":\"Ssenkex\"}", json("Ssenkex"));
        }

        @Test
        void helmetOff() {
            assertEquals("{\"hat\":false,\"player\":\"Ssenkex\"}", json("Ssenkex:false"));
        }

        @Test
        @DisplayName("a UUID serializes as vanilla's four-int array")
        void uuid() {
            final String out = json("1f085b2d-9548-4159-a8c7-f3ccdf0c2054");
            assertTrue(out.contains("\"id\":["), out);
            assertTrue(out.contains("520641325"), out);
        }

        @Test
        @DisplayName("a trimmed UUID produces the same component as the dashed one")
        void trimmedUuidMatchesDashed() {
            assertEquals(json("1f085b2d-9548-4159-a8c7-f3ccdf0c2054"),
                    json("1f085b2d95484159a8c7f3ccdf0c2054"));
        }

        @Test
        void textureKeyGetsTheMinecraftNamespace() {
            assertEquals(
                    "{\"hat\":true,\"player\":{\"texture\":\"minecraft:entity/player/wide/steve\"}}",
                    json("entity/player/wide/steve"));
        }

        @Test
        @DisplayName("base64 becomes an unsigned textures profile property")
        void base64BecomesProperty() {
            final String out = json("base64:" + BASE64);
            assertTrue(out.contains("\"properties\""), out);
            assertTrue(out.contains("\"name\":\"textures\""), out);
            assertTrue(out.contains(BASE64), out);
        }
    }

    @Nested
    @DisplayName("URL sources")
    class Urls {

        @Test
        @DisplayName("are refused — vanilla has no field for an arbitrary URL")
        void refused() {
            final HeadSource source = HeadSource.parse("url:https://minotar.net/skin/Ssenkex");
            assertThrows(UnsupportedOperationException.class,
                    () -> NativeHeadComponents.playerHead(source));
        }

        @Test
        void areReportedByCanRepresent() {
            assertFalse(NativeHeadComponents.canRepresent(
                    HeadSource.parse("url:https://minotar.net/skin/Ssenkex")));
            assertTrue(NativeHeadComponents.canRepresent(HeadSource.parse("Ssenkex")));
        }
    }

    @Nested
    @DisplayName("parseTags")
    class ParseTags {

        @Test
        void inlinesHeadsBetweenText() {
            final Component out = NativeHeadComponents.parseTags("Hola <head:Ssenkex>!");
            final String json = GSON.serialize(out);
            assertTrue(json.contains("\"Hola \""), json);
            assertTrue(json.contains("\"player\":\"Ssenkex\""), json);
            assertTrue(json.contains("\"!\""), json);
        }

        @Test
        @DisplayName("an unrepresentable tag degrades to literal text instead of throwing")
        void degradesUnrepresentableTags() {
            final String json = GSON.serialize(
                    NativeHeadComponents.parseTags("a <head:url:https://minotar.net/x.png> b"));
            assertTrue(json.contains("head:url"), json);
        }

        @Test
        @DisplayName("a malformed tag degrades too, so one bad tag never costs the message")
        void degradesMalformedTags() {
            final String json = GSON.serialize(NativeHeadComponents.parseTags("x <head:false> y"));
            assertTrue(json.contains("head:false"), json);
        }

        @Test
        void textWithoutTagsRoundTrips() {
            assertEquals("\"nada\"", GSON.serialize(NativeHeadComponents.parseTags("nada")));
        }
    }

    @Test
    @DisplayName("the native resolver is reachable without touching MiniMessage from this class")
    void nativeResolverIsOnHeadRenderTags() {
        // NativeHeadComponents deliberately does not reference MiniMessage, so it
        // loads on Adventure platforms that ship without it. The resolver lives
        // on HeadRenderTags, which already requires MiniMessage anyway.
        assertTrue(HeadRenderTags.nativeResolver() != null);
    }
}
