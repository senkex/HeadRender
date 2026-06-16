package com.github.senkex.headrender.effect;

import java.awt.image.BufferedImage;

/**
 * A transformation applied to a head image before it is rendered into lines.
 *
 * <p>Effects operate purely on the {@link BufferedImage} (no resource pack
 * involved) and are composable: the render pipeline applies every configured
 * effect in order. Ready-made effects live in {@link HeadEffects}.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
@FunctionalInterface
public interface HeadEffect {

    /**
     * Applies this effect to the given image.
     *
     * @param source the input image, must not be {@code null}
     * @return the transformed image (may be the same instance or a new one)
     */
    BufferedImage apply(BufferedImage source);

    /**
     * Returns a composed effect that applies this effect first, then
     * {@code next}.
     *
     * @param next the effect to apply after this one, must not be {@code null}
     * @return the composed effect
     */
    default HeadEffect andThen(final HeadEffect next) {
        return source -> next.apply(this.apply(source));
    }
}
