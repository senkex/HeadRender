package com.github.senkex.headrender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the {@code position} / {@code centered} offset math on
 * {@link RenderOptions}.
 */
class RenderOptionsPositionTest {

    @Test
    void defaultsAddNoOffset() {
        assertEquals(0, RenderOptions.defaults().leadingSpaces(8));
    }

    @Test
    void positionConvertsPixelsToSpaces() {
        // 100 px / 4 px-per-space = 25 spaces.
        assertEquals(25, RenderOptions.builder().position(100).build().leadingSpaces(8));
    }

    @Test
    void positionRoundsDownToWholeSpaces() {
        // 10 px / 4 = 2 spaces (8 px), the remainder cannot be expressed.
        assertEquals(2, RenderOptions.builder().position(10).build().leadingSpaces(8));
    }

    @Test
    void centeredCentersAnEightPixelHead() {
        // headWidth = 8 * 6 = 48 px; offset = 154 - 24 = 130 px; 130 / 4 = 32.
        assertEquals(32, RenderOptions.builder().centered(true).build().leadingSpaces(8));
    }

    @Test
    void centeredTakesPrecedenceOverPosition() {
        final RenderOptions options = RenderOptions.builder()
                .centered(true)
                .position(4)
                .build();
        assertEquals(32, options.leadingSpaces(8));
    }

    @Test
    void centeredHonorsCustomCenterPx() {
        // centerPx = 24 -> offset = 24 - 24 = 0 -> no spaces.
        assertEquals(0, RenderOptions.builder().centered(true).centerPx(24).build().leadingSpaces(8));
    }

    @Test
    void offsetSurvivesToBuilderRoundTrip() {
        final RenderOptions options = RenderOptions.builder().position(100).build().toBuilder().build();
        assertEquals(25, options.leadingSpaces(8));
    }

    @Test
    void negativePositionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RenderOptions.builder().position(-1));
    }
}
