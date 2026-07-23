package com.github.senkex.headrender.text;

import com.github.senkex.headrender.skin.HeadSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HeadTagParser")
class HeadTagParserTest {

    private static final String BASE64 =
            "eyJ0aW1lc3RhbXAiOjE2ODAwMDAwMDAwMDAsInByb2ZpbGVJZCI6ImFiY2RlZjAxMjM0NTY3ODkiLCJ0ZXh0dXJlcyI6e319";

    private static List<HeadTagParser.Segment> parse(final String text, final Pattern pattern) {
        return HeadTagParser.parse(text, pattern);
    }

    @Nested
    @DisplayName("segmentation")
    class Segmentation {

        @Test
        void splitsTextAndHeadsInOrder() {
            final List<HeadTagParser.Segment> segments =
                    parse("Hola <head:Ssenkex> chau", HeadTagParser.SEQUENTIAL);

            assertEquals(3, segments.size());
            assertFalse(segments.get(0).isHead());
            assertEquals("Hola ", segments.get(0).value());
            assertTrue(segments.get(1).isHead());
            assertFalse(segments.get(2).isHead());
            assertEquals(" chau", segments.get(2).value());
        }

        @Test
        void handlesAdjacentTags() {
            final List<HeadTagParser.Segment> segments =
                    parse("<head:Ssenkex><head:Notch>", HeadTagParser.SEQUENTIAL);
            assertEquals(2, segments.size());
            assertTrue(segments.get(0).isHead());
            assertTrue(segments.get(1).isHead());
        }

        @Test
        void emptyTextYieldsNoSegments() {
            assertTrue(parse("", HeadTagParser.SEQUENTIAL).isEmpty());
        }

        @Test
        void textWithoutTagsIsOneSegment() {
            final List<HeadTagParser.Segment> segments = parse("nada aca", HeadTagParser.SEQUENTIAL);
            assertEquals(1, segments.size());
            assertFalse(segments.get(0).isHead());
        }
    }

    @Nested
    @DisplayName("SEQUENTIAL — <head:VALUE>")
    class Sequential {

        @ParameterizedTest
        @ValueSource(strings = {
                "<head:Ssenkex>",
                "<HEAD:Ssenkex>",
                "<Head:Ssenkex>",
        })
        @DisplayName("matches case-insensitively")
        void caseInsensitive(final String text) {
            assertTrue(HeadTagParser.containsTag(text, HeadTagParser.SEQUENTIAL));
        }

        @Test
        @DisplayName("captures a base64 blob whole, colons and all")
        void capturesBase64() {
            final List<HeadTagParser.Segment> segments =
                    parse("<head:base64:" + BASE64 + ">", HeadTagParser.SEQUENTIAL);
            assertEquals(1, segments.size());
            assertEquals(HeadSource.Type.BASE64, segments.get(0).source().type());
            assertEquals(BASE64, segments.get(0).source().value());
        }

        @Test
        void capturesHelmetSuffix() {
            final HeadSource source =
                    parse("<head:Ssenkex:false>", HeadTagParser.SEQUENTIAL).get(0).source();
            assertEquals("Ssenkex", source.value());
            assertEquals(false, source.helmet());
        }

        @Test
        @DisplayName("does not match the paired <head>NAME</head> form")
        void doesNotMatchPairedTag() {
            assertFalse(HeadTagParser.containsTag("<head>Ssenkex</head>", HeadTagParser.SEQUENTIAL));
        }
    }

    @Nested
    @DisplayName("TYPED_PLACEHOLDER — %head:VALUE%")
    class TypedPlaceholder {

        @Test
        void matchesTypedForm() {
            final List<HeadTagParser.Segment> segments =
                    parse("Top1: %head:player:Ssenkex%", HeadTagParser.TYPED_PLACEHOLDER);
            assertEquals(2, segments.size());
            assertEquals(HeadSource.Type.PLAYER, segments.get(1).source().type());
            assertEquals("Ssenkex", segments.get(1).source().value());
        }

        @Test
        void doesNotMatchTheDashedForm() {
            assertFalse(HeadTagParser.containsTag("%head-Ssenkex%", HeadTagParser.TYPED_PLACEHOLDER));
        }
    }

    @Nested
    @DisplayName("legacy patterns keep working")
    class Legacy {

        @Test
        void pairedTag() {
            final List<HeadTagParser.Segment> segments =
                    parse("a <head>Ssenkex</head> b", HeadTagParser.PATTERN);
            assertEquals(3, segments.size());
            assertEquals(HeadSource.Type.PLAYER, segments.get(1).source().type());
        }

        @ParameterizedTest
        @ValueSource(strings = {"%head-Ssenkex%", "%head_Ssenkex%"})
        void placeholderForms(final String text) {
            assertTrue(HeadTagParser.containsTag(text, HeadTagParser.PLACEHOLDER));
        }

        @Test
        void namespacedForm() {
            final List<HeadTagParser.Segment> segments =
                    parse("%headrender:Ssenkex%", HeadTagParser.NAMESPACED);
            assertEquals(1, segments.size());
            assertEquals("Ssenkex", segments.get(0).source().value());
        }

        @Test
        @DisplayName("legacy patterns now accept the new source types too")
        void legacyAcceptsNewSources() {
            final HeadSource source =
                    parse("%headrender:base64:" + BASE64 + "%", HeadTagParser.NAMESPACED)
                            .get(0).source();
            assertEquals(HeadSource.Type.BASE64, source.type());
        }
    }

    @Nested
    @DisplayName("Segment.source()")
    class Source {

        @Test
        @DisplayName("is cached, so repeated reads do not re-parse")
        void isCached() {
            final HeadTagParser.Segment segment =
                    parse("<head:Ssenkex>", HeadTagParser.SEQUENTIAL).get(0);
            assertSame(segment.source(), segment.source());
        }

        @Test
        void throwsOnTextSegments() {
            final HeadTagParser.Segment text =
                    parse("solo texto", HeadTagParser.SEQUENTIAL).get(0);
            assertThrows(IllegalStateException.class, text::source);
        }
    }

    @Nested
    @DisplayName("pattern factories")
    class Factories {

        @Test
        void customTagName() {
            assertTrue(HeadTagParser.containsTag("<face:Ssenkex>",
                    HeadTagParser.sequentialFor("face")));
        }

        @Test
        void customPlaceholderPrefix() {
            assertTrue(HeadTagParser.containsTag("%face:Ssenkex%",
                    HeadTagParser.typedPlaceholderFor("face")));
        }

        @ParameterizedTest
        @ValueSource(strings = {""})
        void rejectEmptyNames(final String empty) {
            assertThrows(IllegalArgumentException.class, () -> HeadTagParser.sequentialFor(empty));
            assertThrows(IllegalArgumentException.class, () -> HeadTagParser.typedPlaceholderFor(empty));
            assertThrows(IllegalArgumentException.class, () -> HeadTagParser.patternFor(empty));
        }

        @Test
        @DisplayName("a tag name with regex metacharacters is quoted, not interpreted")
        void quotesMetacharacters() {
            assertTrue(HeadTagParser.containsTag("<h.d:Ssenkex>", HeadTagParser.sequentialFor("h.d")));
            assertFalse(HeadTagParser.containsTag("<hXd:Ssenkex>", HeadTagParser.sequentialFor("h.d")));
        }
    }
}
