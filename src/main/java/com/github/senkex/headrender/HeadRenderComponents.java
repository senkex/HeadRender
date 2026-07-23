package com.github.senkex.headrender;

import com.github.senkex.headrender.text.adventure.AdventureTextSerializer;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Adventure-flavored mirror of {@link HeadRender}.
 *
 * <p>Every method delegates to the same shared {@link com.github.senkex.headrender.api.HeadRenderService}
 * used by {@link HeadRender} and maps the legacy output into Kyori Adventure
 * {@link Component}s through {@link AdventureTextSerializer}. Use this facade
 * on Paper/Velocity or whenever you need hover/click decoration, MiniMessage
 * interop, or component-based APIs.</p>
 *
 * <p>Requires the optional Adventure dependencies on the classpath. The plain
 * {@link HeadRender} facade has no such requirement.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class HeadRenderComponents {

    private HeadRenderComponents() {
        throw new UnsupportedOperationException("Facade class");
    }

    /**
     * Renders the given player as one component per pixel row.
     *
     * @param target the player name or trimmed UUID
     * @return a future completed with one component per row
     */
    public static CompletableFuture<List<Component>> render(final String target) {
        return HeadRender.render(target).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Renders the given player with custom options as components.
     *
     * @param target the player name or trimmed UUID
     * @param options the render configuration
     * @return a future completed with one component per row
     */
    public static CompletableFuture<List<Component>> render(final String target, final RenderOptions options) {
        return HeadRender.render(target, options).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Renders the given player UUID as components.
     *
     * @param uuid the player UUID
     * @return a future completed with one component per row
     */
    public static CompletableFuture<List<Component>> render(final UUID uuid) {
        return HeadRender.render(uuid).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Renders the given player UUID with custom options as components.
     *
     * @param uuid the player UUID
     * @param options the render configuration
     * @return a future completed with one component per row
     */
    public static CompletableFuture<List<Component>> render(final UUID uuid, final RenderOptions options) {
        return HeadRender.render(uuid, options).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Renders the given player into a single newline-joined component.
     *
     * @param target the player name or trimmed UUID
     * @return a future completed with one multi-line component
     */
    public static CompletableFuture<Component> renderMultiline(final String target) {
        return HeadRender.render(target).thenApply(AdventureTextSerializer::toMultilineComponent);
    }

    /**
     * Renders the given player into a single newline-joined component using
     * custom options.
     *
     * @param target the player name or trimmed UUID
     * @param options the render configuration
     * @return a future completed with one multi-line component
     */
    public static CompletableFuture<Component> renderMultiline(final String target, final RenderOptions options) {
        return HeadRender.render(target, options).thenApply(AdventureTextSerializer::toMultilineComponent);
    }

    /**
     * Parses {@code <head>NAME</head>} tags and returns components.
     *
     * @param text the text to parse
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parse(final String text) {
        return HeadRender.parse(text).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses {@code <head>NAME</head>} tags with custom options as components.
     *
     * @param text the text to parse
     * @param options the render configuration
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parse(final String text, final RenderOptions options) {
        return HeadRender.parse(text, options).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses {@code %headrender:NAME%} placeholders and returns components.
     *
     * @param text the text to parse
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parseNamespaced(final String text) {
        return HeadRender.parseNamespaced(text).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses {@code %headrender:NAME%} placeholders with custom options.
     *
     * @param text the text to parse
     * @param options the render configuration
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parseNamespaced(final String text, final RenderOptions options) {
        return HeadRender.parseNamespaced(text, options).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses {@code %head-NAME%} / {@code %head_NAME%} placeholders and
     * returns components.
     *
     * @param text the text to parse
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parsePlaceholders(final String text) {
        return HeadRender.parsePlaceholders(text).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses {@code %head-NAME%} / {@code %head_NAME%} placeholders with
     * custom options.
     *
     * @param text the text to parse
     * @param options the render configuration
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parsePlaceholders(final String text, final RenderOptions options) {
        return HeadRender.parsePlaceholders(text, options).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses Adventure-style {@code <head:VALUE>} tags and returns components.
     *
     * @param text the text to parse
     * @return a future completed with the resulting components
     * @see com.github.senkex.headrender.text.adventure.HeadRenderTags
     */
    public static CompletableFuture<List<Component>> parseTags(final String text) {
        return HeadRender.parseTags(text).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses {@code <head:VALUE>} tags with custom options as components.
     *
     * @param text the text to parse
     * @param options the render configuration
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parseTags(final String text, final RenderOptions options) {
        return HeadRender.parseTags(text, options).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses typed {@code %head:VALUE%} placeholders and returns components.
     *
     * @param text the text to parse
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parseTyped(final String text) {
        return HeadRender.parseTyped(text).thenApply(AdventureTextSerializer::toComponents);
    }

    /**
     * Parses {@code %head:VALUE%} placeholders with custom options.
     *
     * @param text the text to parse
     * @param options the render configuration
     * @return a future completed with the resulting components
     */
    public static CompletableFuture<List<Component>> parseTyped(final String text, final RenderOptions options) {
        return HeadRender.parseTyped(text, options).thenApply(AdventureTextSerializer::toComponents);
    }
}
