package com.github.senkex.headrender.api;

import com.github.senkex.headrender.RenderOptions;
import com.github.senkex.headrender.skin.HeadSource;
import com.github.senkex.headrender.text.HeadTagParser;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Contract for the head rendering pipeline.
 *
 * <p>An implementation is responsible for fetching the skin, scaling
 * it down and converting every pixel into a colored chat line. All
 * operations are asynchronous and return a {@link CompletableFuture}
 * resolved with one chat line per pixel row.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public interface HeadRenderService {

    /**
     * Renders the given player using default options.
     *
     * @param target the player name or trimmed UUID
     * @return a future completed with one chat line per pixel row
     */
    default CompletableFuture<List<String>> render(final String target) {
        return render(target, RenderOptions.defaults());
    }

    /**
     * Renders the given player using custom options.
     *
     * @param target the player name or trimmed UUID
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    CompletableFuture<List<String>> render(String target, RenderOptions options);

    /**
     * Renders the given player UUID using default options.
     *
     * @param uuid the player UUID
     * @return a future completed with one chat line per pixel row
     */
    default CompletableFuture<List<String>> render(final UUID uuid) {
        return render(uuid, RenderOptions.defaults());
    }

    /**
     * Renders the given player UUID using custom options.
     *
     * @param uuid the player UUID
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    CompletableFuture<List<String>> render(UUID uuid, RenderOptions options);

    /**
     * Renders the given resolved skin origin using default options.
     *
     * @param source the skin origin
     * @return a future completed with one chat line per pixel row
     */
    default CompletableFuture<List<String>> render(final HeadSource source) {
        return render(source, RenderOptions.defaults());
    }

    /**
     * Renders the given resolved skin origin using custom options.
     *
     * <p>The source's helmet override, when present, takes precedence over the
     * one carried by {@code options}.</p>
     *
     * @param source the skin origin
     * @param options the render configuration
     * @return a future completed with one chat line per pixel row
     */
    default CompletableFuture<List<String>> render(final HeadSource source, final RenderOptions options) {
        Objects.requireNonNull(source, "Source cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");
        return render(source.canonical(), source.applyTo(options));
    }

    /**
     * Parses the given text and replaces every {@code <head>NAME</head>}
     * tag with the rendered head of {@code NAME}.
     *
     * <p>Output is a list of chat-ready lines. Heads occupy as many rows
     * as the configured render size; surrounding text is placed on the
     * vertical center row and padded with spaces on the other rows.
     * Newlines in the input produce independent blocks in the output.</p>
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parse(final String text) {
        return parse(text, RenderOptions.defaults());
    }

    /**
     * Parses the given text with custom render options.
     *
     * @param text the text to parse
     * @param options the render configuration applied to every head tag
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parse(final String text, final RenderOptions options) {
        return parse(text, options, HeadTagParser.PATTERN);
    }

    /**
     * Parses the given text using a custom tag name.
     *
     * <p>For example, calling with {@code tagName = "face"} will match
     * {@code <face>NAME</face>} instead of the default {@code <head>}.</p>
     *
     * @param text the text to parse
     * @param options the render configuration applied to every matching tag
     * @param tagName the tag name to match (case-insensitive)
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parse(final String text, final RenderOptions options, final String tagName) {
        return parse(text, options, HeadTagParser.patternFor(tagName));
    }

    /**
     * Parses the given text using a fully custom pattern.
     *
     * <p>The pattern must expose the head name as capture group {@code 1}.
     * Useful for placeholder formats like {@code {head:NAME}},
     * {@code %head_NAME%}, or anything else.</p>
     *
     * @param text the text to parse
     * @param options the render configuration applied to every match
     * @param pattern the pattern used to locate head tags
     * @return a future completed with the resulting chat lines
     */
    CompletableFuture<List<String>> parse(String text, RenderOptions options, Pattern pattern);

    /**
     * Parses the given text replacing PlaceholderAPI-style
     * {@code %head-NAME%} and {@code %head_NAME%} placeholders with the
     * rendered head of {@code NAME}.
     *
     * <p>Convenience overload over {@link #parse(String, RenderOptions, Pattern)}
     * using {@link HeadTagParser#PLACEHOLDER}. Handy when your text comes
     * from configs written with {@code %} placeholders instead of XML tags.</p>
     *
     * @param text the text to parse
     * @param options the render configuration applied to every placeholder
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parsePlaceholders(final String text, final RenderOptions options) {
        return parse(text, options, HeadTagParser.PLACEHOLDER);
    }

    /**
     * Parses the given text replacing {@code %head-NAME%} / {@code %head_NAME%}
     * placeholders using default options.
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parsePlaceholders(final String text) {
        return parsePlaceholders(text, RenderOptions.defaults());
    }

    /**
     * Parses the given text replacing namespaced {@code %headrender:NAME%}
     * placeholders with the rendered head of {@code NAME}.
     *
     * <p>Convenience overload over {@link #parse(String, RenderOptions, Pattern)}
     * using {@link HeadTagParser#NAMESPACED}.</p>
     *
     * @param text the text to parse
     * @param options the render configuration applied to every placeholder
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parseNamespaced(final String text, final RenderOptions options) {
        return parse(text, options, HeadTagParser.NAMESPACED);
    }

    /**
     * Parses the given text replacing {@code %headrender:NAME%} placeholders
     * using default options.
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parseNamespaced(final String text) {
        return parseNamespaced(text, RenderOptions.defaults());
    }

    /**
     * Parses the given text replacing Adventure-style {@code <head:VALUE>} tags
     * with the rendered head.
     *
     * <p>This is the MiniMessage-compatible flavour. {@code VALUE} accepts an
     * explicit {@code type:value} pair, a bare value whose type is detected, and
     * an optional trailing {@code :true} / {@code :false} helmet override — see
     * {@link HeadSource}.</p>
     *
     * <pre>{@code
     * service.parseTags("Hola <head:Senkex>, mira <head:base64:eyJ0...>");
     * }</pre>
     *
     * @param text the text to parse
     * @param options the render configuration applied to every tag
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parseTags(final String text, final RenderOptions options) {
        return parse(text, options, HeadTagParser.SEQUENTIAL);
    }

    /**
     * Parses the given text replacing {@code <head:VALUE>} tags using default
     * options.
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parseTags(final String text) {
        return parseTags(text, RenderOptions.defaults());
    }

    /**
     * Parses the given text replacing typed {@code %head:VALUE%} placeholders
     * with the rendered head.
     *
     * <p>The placeholder counterpart of {@link #parseTags(String, RenderOptions)},
     * for configs written with {@code %} placeholders instead of tags.</p>
     *
     * @param text the text to parse
     * @param options the render configuration applied to every placeholder
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parseTyped(final String text, final RenderOptions options) {
        return parse(text, options, HeadTagParser.TYPED_PLACEHOLDER);
    }

    /**
     * Parses the given text replacing {@code %head:VALUE%} placeholders using
     * default options.
     *
     * @param text the text to parse
     * @return a future completed with the resulting chat lines
     */
    default CompletableFuture<List<String>> parseTyped(final String text) {
        return parseTyped(text, RenderOptions.defaults());
    }

    /**
     * Returns the cache used by this service.
     *
     * @return the underlying skin cache
     */
    SkinCache getCache();

    /**
     * Returns the provider used to fetch skin images.
     *
     * @return the underlying skin provider
     */
    SkinProvider getProvider();

    /**
     * Returns the renderer strategy used to turn head images into lines.
     *
     * @return the underlying head renderer
     */
    HeadRenderer getRenderer();

    /**
     * Releases any resource owned by this service (thread pools, etc.).
     */
    void shutdown();
}
