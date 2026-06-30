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
never block the main thread. The default provider goes **straight to Mojang** (online skins in
offline mode, no proxy) and falls back to [Minotar](https://minotar.net) if Mojang is down or
rate-limits, with an in-memory LRU cache with a 10 minute TTL.

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
HeadRender.parse("Hi <face>Senkex</face>!", RenderOptions.defaults(), "face")
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
HeadRender.parsePlaceholders("Welcome %head-Senkex% to the server!")
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
| Action bar / single tab name | use `FontHeadRenderer` | A head on **one** line needs a resource pack — see [Single-line heads](#single-line-heads-resource-pack) |

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

`FontHeadRenderer` (tiny single-line inline heads, resource-pack backed) plugs
into this same interface — see [Single-line heads](#single-line-heads-resource-pack).

### Body & Full-Skin Parts

Render the whole player, not just the face. `RenderOptions.part(...)` chooses
between `RenderPart.FACE` (the classic `8×8` square) and `RenderPart.BODY` (the
full front silhouette: head + torso + arms + legs, a `16×32` image scaled to
your `size`):

```java
HeadRender.render("Senkex", RenderOptions.body(8))
        .thenAccept(lines -> lines.forEach(player::sendMessage)); // 16 lines tall
```

Body rendering needs a **full skin** sheet, so it only works with full-skin
providers (`MojangSkinProvider`, `UrlSkinProvider`, `LocalFileSkinProvider`,
`StaticSkinProvider`). The default provider is Mojang-first, so it works out of
the box; avatar-only proxies (Minotar/Crafatar) throw a clear error for body
parts. The `1:2` ratio is preserved automatically and overlay layers (hat,
jacket, sleeves, trousers) are composited when the helmet layer is on.

### Single-line heads (resource pack)

A multicolor head on **one** line is impossible in vanilla chat (no
per-character background), so this is the one feature that needs a resource
pack. `ResourcePackGenerator` bakes each head into a **bitmap font glyph** and
`FontHeadRenderer` emits it as a single character:

```java
ResourcePackGenerator pack = new ResourcePackGenerator().packFormat(34); // match your MC version
FontHeadRenderer renderer = new FontHeadRenderer(pack);

HeadRender.use(DefaultHeadRenderService.builder().renderer(renderer).build());

// Render the names you want baked (registers their glyphs)...
List<String> oneLine = HeadRender.render("Senkex").join(); // a single-character line

// ...then write the pack once and host it (server resource-pack URL):
pack.writeZip(new File("plugins/MyPlugin/heads.zip"));
```

The glyph only draws the head when the client has the pack **and** the text uses
the pack's font (`pack.fontKey()`, e.g. `headrender:heads`). Apply it with
Kyori Adventure:

```java
String glyph = oneLine.get(0);
Component head = Component.text(glyph).font(Key.key(pack.fontKey()));
player.sendMessage(head); // inline head in a tab name, scoreboard, action bar...
```

`writeDirectory(File)` writes the unzipped tree instead, and `packFormat(int)`
must match your server's Minecraft version.

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
| `MojangSkinProvider` | name or UUID | **direct from Mojang, no proxy** — online skins in offline mode |
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

#### Online heads in offline mode (no proxy, no pack, no mod)

`MojangSkinProvider` resolves skins straight from Mojang's official API, with no
third-party proxy in the middle. This is what lets you show a player's
**online-mode head while the server runs in offline mode**: the skin is looked
up by **name** against Mojang, independent of how the player authenticated on
your server (same approach BungeeTabListPlus uses for `%head-<player>`).

```
name → api.mojang.com/users/profiles/minecraft/<name>            → UUID
UUID → sessionserver.mojang.com/session/minecraft/profile/<uuid> → textures (base64)
base64 → textures.minecraft.net skin URL → download → crop 8×8 face
```

It needs no JSON dependency (the lib stays zero-deps) and caches resolved skin
URLs in-memory with a short TTL to respect Mojang's rate limits.

```java
SkinProvider source = new FallbackSkinProvider(
        new MojangSkinProvider(),               // official source, no proxy
        new MinotarSkinProvider(),              // proxy fallback if Mojang is down/limited
        new StaticSkinProvider(steveImage));    // last-resort Steve, never fails

HeadRender.use(DefaultHeadRenderService.builder().provider(source).build());
```

> [!NOTE]
> A name only resolves if it is a **premium account** on Mojang. Offline players
> whose name isn't a real premium account have no skin to fetch — chain a
> `StaticSkinProvider` Steve/Alex as the final fallback for those.

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

### Platform Helpers (Bukkit)

Thin, optional helpers in the `platform` package push rendered lines into common
surfaces. They touch the Spigot API, which is **`compileOnly`** — the core
library never loads it, so non-Bukkit consumers are unaffected. Use them only
inside a plugin.

```java
// MOTD — render at size 2 (it only has two lines)
HeadRender.render("Senkex", RenderOptions.of(2))
        .thenAccept(lines -> BukkitMotd.apply(event, lines)); // ServerListPingEvent

// Tab header / footer
HeadRender.render("Senkex", RenderOptions.of(3))
        .thenAccept(lines -> BukkitTab.header(player, lines));

// Written book, one head per page
HeadRender.render("Senkex").thenAccept(lines -> {
    ItemStack book = BukkitBook.singlePage("Profile", "Server", lines);
    player.getInventory().addItem(book);
});
```

These stay deliberately platform-light: anything else (scoreboards via FastBoard,
holograms, NPC plugins, …) consumes the same `List<String>` / `Component`
output, so you wire it to whatever API you already use.

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
