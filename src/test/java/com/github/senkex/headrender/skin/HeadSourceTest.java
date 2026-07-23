package com.github.senkex.headrender.skin;

import com.github.senkex.headrender.RenderOptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HeadSource")
class HeadSourceTest {

    static final String BASE64 =
            "eyJ0aW1lc3RhbXAiOjE2ODAwMDAwMDAwMDAsInByb2ZpbGVJZCI6ImFiY2RlZjAxMjM0NTY3ODkiLCJ0ZXh0dXJlcyI6e319";

    @Nested
    @DisplayName("implicit type detection")
    class Detection {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "Ssenkex,                                    PLAYER",
                "_a_,                                        PLAYER",
                "1f085b2d-9548-4159-a8c7-f3ccdf0c2054,       UUID",
                "1f085b2d95484159a8c7f3ccdf0c2054,           UUID",
                "entity/player/wide/steve,                   TEXTURE",
                "minecraft:entity/player/slim/alex,          TEXTURE",
                "https://textures.minecraft.net/texture/ab,  URL",
        })
        void detectsType(final String raw, final HeadSource.Type expected) {
            assertEquals(expected, HeadSource.parse(raw).type());
        }

        @Test
        @DisplayName("a long base64 blob is not mistaken for a player name")
        void detectsBase64() {
            assertEquals(HeadSource.Type.BASE64, HeadSource.parse(BASE64).type());
        }

        @Test
        @DisplayName("a 16-char name stays a name even though it is valid base64")
        void shortBase64LikeNameStaysPlayer() {
            // The 40-char floor is what keeps names out of the BASE64 branch.
            assertEquals(HeadSource.Type.PLAYER, HeadSource.parse("abcdefghijklmnop").type());
        }
    }

    @Nested
    @DisplayName("explicit type prefixes")
    class Prefixes {

        @ParameterizedTest(name = "{0} -> {1} / {2}")
        @CsvSource({
                "player:Ssenkex,    PLAYER,  Ssenkex",
                "name:Ssenkex,      PLAYER,  Ssenkex",
                "uuid:1f085b2d95484159a8c7f3ccdf0c2054, UUID, 1f085b2d95484159a8c7f3ccdf0c2054",
                "id:1f085b2d95484159a8c7f3ccdf0c2054,   UUID, 1f085b2d95484159a8c7f3ccdf0c2054",
                "texture:entity/player/wide/steve, TEXTURE, entity/player/wide/steve",
                "key:entity/player/wide/steve,     TEXTURE, entity/player/wide/steve",
        })
        void readsPrefix(final String raw, final HeadSource.Type type, final String value) {
            final HeadSource source = HeadSource.parse(raw);
            assertEquals(type, source.type());
            assertEquals(value, source.value());
        }

        @ParameterizedTest
        @ValueSource(strings = {"base64:", "value:", "textures:"})
        void base64Aliases(final String prefix) {
            final HeadSource source = HeadSource.parse(prefix + BASE64);
            assertEquals(HeadSource.Type.BASE64, source.type());
            assertEquals(BASE64, source.value());
        }

        @Test
        @DisplayName("an explicit prefix overrides what detection would have guessed")
        void prefixWinsOverDetection() {
            // "steve" alone would detect as PLAYER; the prefix forces TEXTURE.
            assertEquals(HeadSource.Type.TEXTURE, HeadSource.parse("texture:steve").type());
        }

        @Test
        @DisplayName("an unknown prefix is treated as part of the value")
        void unknownPrefixIsNotAType() {
            assertEquals(HeadSource.Type.PLAYER, HeadSource.parse("bogus", HeadSource.Policy.TRUSTED).type());
        }
    }

    @Nested
    @DisplayName("helmet override")
    class Helmet {

        @Test
        void defaultsToNull() {
            assertNull(HeadSource.parse("Ssenkex").helmet());
        }

        @ParameterizedTest
        @CsvSource({"Ssenkex:true, true", "Ssenkex:false, false", "player:Ssenkex:false, false"})
        void readsSuffix(final String raw, final boolean expected) {
            final HeadSource source = HeadSource.parse(raw);
            assertEquals(expected, source.helmet());
            assertEquals("Ssenkex", source.value());
        }

        @Test
        @DisplayName("the suffix is split from the right so a URL scheme survives")
        void doesNotEatUrlScheme() {
            final HeadSource source =
                    HeadSource.parse("url:https://minotar.net/skin/Ssenkex:false");
            assertEquals(HeadSource.Type.URL, source.type());
            assertEquals("https://minotar.net/skin/Ssenkex", source.value());
            assertEquals(false, source.helmet());
        }

        @Test
        @DisplayName("applyTo returns the same instance when there is nothing to override")
        void applyToIsAllocationFreeWhenUnchanged() {
            final RenderOptions options = RenderOptions.defaults();
            assertSame(options, HeadSource.parse("Ssenkex").applyTo(options));
            assertSame(options, HeadSource.parse("Ssenkex:true").applyTo(options));
        }

        @Test
        void applyToForcesTheLayer() {
            final RenderOptions options = RenderOptions.defaults();
            assertTrue(options.useHelmetLayer());
            assertFalse(HeadSource.parse("Ssenkex:false").applyTo(options).useHelmetLayer());
        }
    }

    @Nested
    @DisplayName("canonical form")
    class Canonical {

        @ParameterizedTest
        @ValueSource(strings = {
                "Ssenkex",
                "player:Ssenkex:false",
                "1f085b2d-9548-4159-a8c7-f3ccdf0c2054",
                "entity/player/wide/steve",
                "url:https://minotar.net/skin/Ssenkex",
        })
        @DisplayName("re-parsing canonical() yields the same type and value")
        void roundTrips(final String raw) {
            final HeadSource first = HeadSource.parse(raw);
            final HeadSource second = HeadSource.parse(first.canonical());
            assertEquals(first.type(), second.type());
            assertEquals(first.value(), second.value());
            assertEquals(first.canonical(), second.canonical());
        }

        @Test
        void prefixesTheType() {
            assertEquals("player:Ssenkex", HeadSource.parse("Ssenkex").canonical());
            assertEquals("texture:entity/player/wide/steve",
                    HeadSource.parse("entity/player/wide/steve").canonical());
        }

        @Test
        @DisplayName("the helmet flag is excluded — it is not part of the skin's identity")
        void excludesHelmet() {
            assertEquals("player:Ssenkex", HeadSource.parse("Ssenkex:false").canonical());
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        void equalWhenTypeValueAndHelmetMatch() {
            assertEquals(HeadSource.parse("Ssenkex"), HeadSource.parse("player:Ssenkex"));
            assertEquals(HeadSource.parse("Ssenkex").hashCode(), HeadSource.parse("player:Ssenkex").hashCode());
        }

        @Test
        @DisplayName("the helmet flag participates, so the render cache keys them apart")
        void helmetDistinguishes() {
            assertNotEquals(HeadSource.parse("Ssenkex:false"), HeadSource.parse("Ssenkex:true"));
            assertNotEquals(HeadSource.parse("Ssenkex:false"), HeadSource.parse("Ssenkex"));
        }
    }

    @Nested
    @DisplayName("rejects")
    class Rejects {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "player:", "uuid:"})
        void emptyOrValueless(final String raw) {
            assertThrows(IllegalArgumentException.class, () -> HeadSource.parse(raw));
        }

        @ParameterizedTest
        @ValueSource(strings = {"true", "false", "TRUE", "False"})
        @DisplayName("a lone boolean is a helmet flag with no target, not a player named 'false'")
        void loneBoolean(final String raw) {
            assertThrows(IllegalArgumentException.class, () -> HeadSource.parse(raw));
        }

        @Test
        void overlongSource() {
            final StringBuilder huge = new StringBuilder();
            for (int i = 0; i < HeadSource.MAX_LENGTH + 1; i++) {
                huge.append('A');
            }
            assertThrows(IllegalArgumentException.class, () -> HeadSource.parse(huge.toString()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"player:ThisNameIsFarTooLong", "player:has space", "player:tab\tchar"})
        void invalidPlayerNames(final String raw) {
            assertThrows(IllegalArgumentException.class, () -> HeadSource.parse(raw));
        }

        @Test
        void nullInput() {
            assertThrows(NullPointerException.class, () -> HeadSource.parse(null));
        }
    }
}
