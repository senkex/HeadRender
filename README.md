# HeadRender

[![Minecraft](https://img.shields.io/badge/Minecraft-1.16%2B-dark_green.svg)](https://shields.io/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://shields.io/)
[![JitPack](https://jitpack.io/v/senkex/HeadRender.svg)](https://jitpack.io/#senkex/HeadRender)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Small library to render a player's skin head directly into Minecraft chat as colored pixels.
It fetches the skin, downscales it and turns every pixel into a HEX-colored chat line, so you can
print a clean avatar next to whatever info you want to show (welcome messages, profiles, /seen, etc).

> [!IMPORTANT]
> Requires Minecraft **1.16 or newer** since it relies on HEX colors (`§x§r§r§g§g§b§b`).
> Anything below that won't display the colors correctly.

> [!CAUTION]
> Don't forget to [shade](#shading) the library if you plan to ship it inside your plugin,
> otherwise you'll run into class conflicts with anyone else using HeadRender.

The whole point of the library is to stay simple: one static facade, async by default and a builder
when you actually need to tweak something. No plugin instance, no `onEnable` call, no config files.

### Getting Started

The library targets **Java 17** and uses `CompletableFuture` for every IO operation, so HTTP calls
never block the main thread. The default provider uses [Minotar](https://minotar.net) and an
in-memory LRU cache with a 10 minute TTL.

You can drop it into your project with JitPack:

#### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.github.senkex</groupId>
    <artifactId>HeadRender</artifactId>
    <version>version</version>
</dependency>
```

#### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.senkex:HeadRender:version")
}
```

#### Gradle (Groovy)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.senkex:HeadRender:version'
}
```

### Usage

The simplest case is just a player name. The future resolves with one chat line per pixel row:

```java
HeadRender.render("Senkex").thenAccept(lines -> {
    for (String line : lines) {
        player.sendMessage(line);
    }
});
```

UUIDs work the same way:

```java
HeadRender.render(player.getUniqueId()).thenAccept(player::sendMessage);
```

If you need to tweak the output (size, character, helmet layer, transparency...) build a
`RenderOptions` and pass it along:

```java
RenderOptions options = RenderOptions.builder()
        .size(10)
        .character("⬛")
        .helmetLayer(true)
        .alphaThreshold(20)
        .build();

HeadRender.render("Senkex", options).thenAccept(lines -> lines.forEach(player::sendMessage));
```

### Inline Head Tags

You can also embed heads inside arbitrary text using `<head>NAME</head>` tags.
The library parses the input, renders every tag and gives you back chat-ready
lines you can send to a player, a hologram, a text display, an action bar
wrapper, an NPC plugin, or anything else that consumes multi-line text.
No texture pack required, no client mod, no custom font:

```java
HeadRender.parse("Welcome <head>Senkex</head> to the server!")
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

You can place multiple tags in the same string and mix them with newlines:

```java
String template = "Top players:\n"
        + "1. <head>Senkex</head> Senkex\n"
        + "2. <head>Notch</head> Notch";

HeadRender.parse(template).thenAccept(lines -> lines.forEach(player::sendMessage));
```

Custom options work the same way as `render`:

```java
RenderOptions options = RenderOptions.builder().size(6).helmetLayer(true).build();
HeadRender.parse("<head>Senkex</head> joined", options)
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

The tag is case-insensitive and accepts both player names and trimmed UUIDs.
Heads are rendered as `size` rows; the surrounding text sits on the vertical
center row and is padded with spaces on the other rows so the columns line up.

#### Custom Tag Name

Don't like `<head>`? Pass any tag name and the parser will match it:

```java
HeadRender.parse("Hola <face>Senkex</face>!", RenderOptions.defaults(), "face")
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

#### Placeholder Syntax (`%headrender:NAME%`, `%head-NAME%`)

If you prefer PlaceholderAPI-style placeholders over XML tags, there are
built-in shortcuts. The canonical one is namespaced:

```java
HeadRender.parseNamespaced("Top 1: %headrender:Senkex%")
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

The PlaceholderAPI-flavored `%head-NAME%` and `%head_NAME%` (either separator)
also work out of the box:

```java
HeadRender.parsePlaceholders("Bienvenido %head-Senkex% al server!")
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

With custom options:

```java
RenderOptions options = RenderOptions.builder().size(3).build();
HeadRender.parsePlaceholders("%head_Senkex% Senkex", options)
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

Need a different prefix (`%face-NAME%`, `%avatar_NAME%`...)? Build the pattern
with `HeadTagParser.placeholderFor("face")` and pass it to `parse`.

#### Custom Pattern

For non-XML placeholder syntaxes (`{head:NAME}`, MiniMessage
style, etc.) supply your own `Pattern`. The player name must be capture
group `1`:

```java
import java.util.regex.Pattern;

Pattern placeholder = Pattern.compile("\\{head:([^}\\s]+)}");

HeadRender.parse("Welcome {head:Senkex}!", RenderOptions.defaults(), placeholder)
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

#### Manual Usage (no parser)

If you want full control over where the lines go (mixing with your own
formatting engine, building MiniMessage components, etc.) skip `parse`
entirely and call `render` directly:

```java
HeadRender.render("Senkex", RenderOptions.of(2)).thenAccept(lines -> {
    // 2-line head fits in the two MOTD lines
    motd.setLine1(lines.get(0));
    motd.setLine2(lines.get(1));
});
```

#### Sizing Cheatsheet

| Context | Suggested `size` | Notes |
|---|---|---|
| Chat | `8` (default) | Looks crisp, takes 8 chat lines |
| Scoreboard | `2` – `4` | Use a no-limit board API (e.g. FastBoard); one head row per scoreboard line |
| Tab (header/footer) | `2` – `4` | Header and footer are multi-line, so the head stacks fine |
| Text Display / Hologram | `6` – `8` | Lines are tight, so the head looks compact |
| MOTD | `2` | MOTD only has two lines available |
| Action bar / single tab name | not supported | A head on **one** line needs a negative-space font (resource pack) |

### Adventure Components

Every `HeadRender` method has a mirror on `HeadRenderComponents` that returns
Kyori Adventure `Component`s instead of legacy strings. Use it on Paper /
Velocity, for hover/click decoration, or MiniMessage interop:

```java
HeadRenderComponents.render("Senkex")
        .thenAccept(lines -> lines.forEach(player::sendMessage)); // Audience#sendMessage

// Single newline-joined component (holograms, lore, ...)
HeadRenderComponents.renderMultiline("Senkex").thenAccept(player::sendMessage);

// Placeholders work too
HeadRenderComponents.parseNamespaced("Top 1: %headrender:Senkex%")
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

The Adventure dependency is **optional** (`compileOnly`): the plain
`HeadRender` facade never touches it, so legacy-only plugins don't have to ship
it. Add it yourself only if you call the component API:

```groovy
compileOnly 'net.kyori:adventure-api:4.17.0'
compileOnly 'net.kyori:adventure-text-serializer-legacy:4.17.0'
```

If you already have lines from the legacy API, convert them directly with
`AdventureTextSerializer.toComponents(lines)` / `toMultilineComponent(lines)`.

### Renderers

How pixels become text is pluggable through the `HeadRenderer` strategy. The
default is `HexPixelRenderer` (one colored character per pixel, no resource
pack). Swap it on the service builder:

```java
HeadRenderService service = DefaultHeadRenderService.builder()
        .renderer(HexPixelRenderer.INSTANCE)
        .build();

HeadRender.use(service);
```

A negative-space font renderer (tiny single-line inline heads, resource-pack
backed) plugs into this same interface.

### Effects

Transform the head image before it's rendered — no resource pack, no network.
Effects come from `HeadEffects` and run in the order you add them:

```java
RenderOptions options = RenderOptions.builder()
        .size(8)
        .effect(HeadEffects.grayscale())
        .effect(HeadEffects.hueShift(45))
        .build();

HeadRender.render("Senkex", options).thenAccept(lines -> lines.forEach(player::sendMessage));
```

Built-in effects: `grayscale()`, `invert()`, `hueShift(deg)`, `saturate(factor)`,
`brightness(factor)`, `sepia()`, `tint(color, strength)`, `flipHorizontal()`,
`flipVertical()`, `rotate180()`. Implement `HeadEffect` for your own, and chain
them with `effect.andThen(other)`.

### Skin Providers

The default source is Minotar (names and UUIDs). Swap or combine it on the
service builder. Built-in providers:

| Provider | Target | Notes |
|---|---|---|
| `MinotarSkinProvider` | name or UUID | default |
| `CrafatarSkinProvider` | UUID | `&overlay` for the helmet |
| `SkinMcSkinProvider` | name | SkinMC face endpoint |
| `UrlSkinProvider` | image URL | crops the face from a full skin automatically |
| `LocalFileSkinProvider` | file name | reads `<dir>/<name>.png` |
| `StaticSkinProvider` | (ignored) | always serves one image — great as a fallback |
| `FallbackSkinProvider` | — | tries a chain, first success wins |

```java
SkinProvider source = new FallbackSkinProvider(
        new MinotarSkinProvider(),
        new SkinMcSkinProvider(),
        new StaticSkinProvider(steveImage)); // never fails

HeadRender.use(DefaultHeadRenderService.builder().provider(source).build());
```

Full-skin sources (`UrlSkinProvider`, `LocalFileSkinProvider`, `StaticSkinProvider`)
crop the 8×8 face — and the helmet overlay when enabled — via `SkinFaces`.

### Custom Service

The static facade is just a wrapper around a `HeadRenderService` so you can replace the provider,
the cache, or both. Useful if you want a different skin source, a longer TTL or a bigger cache:

```java
HeadRenderService service = DefaultHeadRenderService.builder()
        .provider(new MinotarSkinProvider(3000))
        .cache(new InMemorySkinCache(512, TimeUnit.MINUTES.toMillis(30)))
        .build();

HeadRender.use(service);
```

You can implement your own `SkinProvider` if you'd rather pull skins from Mojang directly,
from a local file, or from any other source.

### Cache

The cache is shared across every call that uses the same service. A few helpers are exposed on the
facade so you don't have to grab it manually:

```java
HeadRender.cacheSize();
HeadRender.clearCache();
HeadRender.cache().invalidate("Senkex");
```

When you're done (plugin disable, server reload, etc.) call `HeadRender.shutdown()` so the service
releases its thread pool.

## Shading

If you're shipping HeadRender inside a plugin you **must** relocate it. Two plugins on the same
server depending on different versions of the same package will eventually break someone's day.

### Maven Shade Plugin

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <relocations>
            <relocation>
                <pattern>com.github.senkex.headrender</pattern>
                <!-- change this to a package inside your own plugin -->
                <shadedPattern>my.plugin.libs.headrender</shadedPattern>
            </relocation>
        </relocations>
    </configuration>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Gradle Shadow (Kotlin DSL)

```kotlin
plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

tasks {
    shadowJar {
        relocate("com.github.senkex.headrender", "my.plugin.libs.headrender")
    }
}
```

### Gradle Shadow (Groovy)

```groovy
plugins {
    id 'com.gradleup.shadow' version '8.3.5'
}

tasks {
    shadowJar {
        relocate 'com.github.senkex.headrender', 'my.plugin.libs.headrender'
    }
}
```

### Notes

- All HTTP fetches go through a small async pool, so calling `render` from the main thread is safe.
- The cache is keyed by lowercase target, so `"Senkex"` and `"senkex"` share the same entry.
- Skin source is Minotar by default. If you need higher availability swap the `SkinProvider`.

### License

Released under the MIT License. Do whatever you want with it, attribution is appreciated.
