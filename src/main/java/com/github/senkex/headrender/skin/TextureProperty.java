package com.github.senkex.headrender.skin;

import java.util.Objects;

/**
 * A Mojang {@code textures} profile property: the base64 {@code value} and its
 * optional {@code signature}.
 *
 * <p>This is the raw material for anything that needs to <i>apply</i> a skin
 * rather than just draw it: native tablist head icons (fake-player profiles),
 * player-head items / skulls, NPC skins, etc. The {@code value} is a base64
 * JSON blob pointing at the {@code textures.minecraft.net} skin; the
 * {@code signature} is Mojang's signature over it (only present when fetched
 * signed) and is required when the client validates the property.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class TextureProperty {

    private final String value;
    private final String signature;

    /**
     * Creates a texture property.
     *
     * @param value the base64 textures value, must not be {@code null}
     * @param signature the base64 signature, or {@code null} if unsigned
     */
    public TextureProperty(final String value, final String signature) {
        this.value = Objects.requireNonNull(value, "Value cannot be null");
        this.signature = signature;
    }

    /**
     * Returns the base64 {@code textures} value.
     *
     * @return the value, never {@code null}
     */
    public String value() {
        return value;
    }

    /**
     * Returns the base64 signature.
     *
     * @return the signature, or {@code null} when unsigned
     */
    public String signature() {
        return signature;
    }

    /**
     * Returns whether this property carries a signature.
     *
     * @return {@code true} if signed
     */
    public boolean isSigned() {
        return signature != null;
    }
}
