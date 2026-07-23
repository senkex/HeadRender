package com.github.senkex.headrender;

import com.github.senkex.headrender.api.HeadRenderService;
import com.github.senkex.headrender.skin.HeadSource;
import com.github.senkex.headrender.skin.provider.SourceSkinProvider;
import com.github.senkex.headrender.skin.provider.StaticSkinProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * End-to-end pipeline coverage with <b>no network access</b>.
 *
 * <p>A {@link StaticSkinProvider} stands in for Mojang, so these run offline, in
 * milliseconds, and never depend on a third party being up or on rate limits.</p>
 */
@DisplayName("render pipeline (offline)")
class RenderPipelineTest {

    private HeadRenderService service;

    private static BufferedImage syntheticSkin() {
        final BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                skin.setRGB(x, y, Color.RED.getRGB());
            }
        }
        for (int y = 8; y < 16; y++) {
            for (int x = 40; x < 48; x++) {
                skin.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        return skin;
    }

    @BeforeEach
    void setUp() {
        service = DefaultHeadRenderService.builder()
                .provider(new StaticSkinProvider(syntheticSkin()))
                .build();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private List<String> render(final String target, final RenderOptions options) {
        return assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                () -> service.render(target, options).get(10, TimeUnit.SECONDS));
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        @Test
        @DisplayName("produces one line per pixel row")
        void oneLinePerRow() {
            assertEquals(8, render("Ssenkex", RenderOptions.defaults()).size());
            assertEquals(16, render("Ssenkex", RenderOptions.of(16)).size());
        }

        @Test
        void everyLineCarriesHexColour() {
            for (final String line : render("Ssenkex", RenderOptions.defaults())) {
                assertTrue(line.contains("§x"), "expected a §x hex sequence in: " + line);
            }
        }

        @Test
        @DisplayName("the helmet layer changes the output")
        void helmetLayerMatters() {
            final List<String> withHat = render("Ssenkex",
                    RenderOptions.builder().helmetLayer(true).build());
            final List<String> withoutHat = render("Ssenkex",
                    RenderOptions.builder().helmetLayer(false).build());
            assertNotEquals(withHat, withoutHat);
        }

        @Test
        @DisplayName("the helmet suffix on the source overrides the options")
        void sourceHelmetOverridesOptions() {
            final RenderOptions helmetOn = RenderOptions.builder().helmetLayer(true).build();

            final List<String> forcedOff = render("Ssenkex:false", helmetOn);
            final List<String> plainOff = render("Ssenkex",
                    RenderOptions.builder().helmetLayer(false).build());

            assertEquals(plainOff, forcedOff);
        }
    }

    @Nested
    @DisplayName("head sources reach the provider")
    class Sources {

        @Test
        @DisplayName("the service wraps a plain provider so every source type resolves")
        void providerIsAutoWrapped() {
            assertInstanceOf(SourceSkinProvider.class, service.getProvider());
            assertInstanceOf(StaticSkinProvider.class,
                    ((SourceSkinProvider) service.getProvider()).delegate());
        }

        @Test
        void anAlreadyWrappedProviderIsNotDoubleWrapped() {
            final SourceSkinProvider wrapped =
                    SourceSkinProvider.wrapping(new StaticSkinProvider(syntheticSkin()));
            final HeadRenderService custom =
                    DefaultHeadRenderService.builder().provider(wrapped).build();
            try {
                assertSame(wrapped, custom.getProvider());
            } finally {
                custom.shutdown();
            }
        }

        @Test
        void renderingBySourceMatchesRenderingByString() {
            assertEquals(render("Ssenkex", RenderOptions.defaults()),
                    assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                            () -> service.render(HeadSource.parse("player:Ssenkex"),
                                    RenderOptions.defaults()).get(10, TimeUnit.SECONDS)));
        }
    }

    @Nested
    @DisplayName("tag parsing")
    class Tags {

        private List<String> parseTags(final String text) {
            return assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                    () -> service.parseTags(text).get(10, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("surrounding text sits on the centre row, padded elsewhere")
        void textIsVerticallyCentred() {
            final List<String> lines = parseTags("Hola <head:Ssenkex>!");
            assertEquals(8, lines.size());
            assertTrue(lines.get(4).contains("Hola"), "expected text on the centre row");
            assertFalse(lines.get(0).contains("Hola"));
        }

        @Test
        @DisplayName("two tags for the same source render once and appear twice")
        void repeatedSourcesAreDeduplicated() {
            final List<String> lines = parseTags("<head:Ssenkex><head:Ssenkex>");
            assertEquals(8, lines.size());
            // Both heads are on every row, so each row is twice a single head.
            final String row = lines.get(0);
            assertEquals(row.substring(0, row.length() / 2), row.substring(row.length() / 2));
        }

        @Test
        void newlinesProduceIndependentBlocks() {
            assertEquals(16, parseTags("<head:Ssenkex>\n<head:Notch>").size());
        }

        @Test
        void textWithoutTagsPassesThrough() {
            assertEquals(List.of("sin tags"), parseTags("sin tags"));
        }

        @Test
        void typedPlaceholdersWorkToo() {
            final List<String> lines = assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                    () -> service.parseTyped("x %head:player:Ssenkex% y").get(10, TimeUnit.SECONDS));
            assertEquals(8, lines.size());
        }
    }

    @Nested
    @DisplayName("caching")
    class Caching {

        @Test
        void secondRenderHitsTheCache() {
            render("Ssenkex", RenderOptions.defaults());
            final int afterFirst = service.getCache().size();
            render("Ssenkex", RenderOptions.defaults());
            assertEquals(afterFirst, service.getCache().size());
        }

        @Test
        @DisplayName("the helmet flag is part of the cache key")
        void helmetIsKeyed() {
            service.getCache().clear();
            render("Ssenkex", RenderOptions.builder().helmetLayer(true).build());
            render("Ssenkex", RenderOptions.builder().helmetLayer(false).build());
            assertEquals(2, service.getCache().size());
        }

        @Test
        void cacheCanBeBypassed() {
            service.getCache().clear();
            render("Ssenkex", RenderOptions.builder().useCache(false).build());
            assertEquals(0, service.getCache().size());
        }
    }
}
