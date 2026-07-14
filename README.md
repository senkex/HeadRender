# HeadRender

[![Minecraft](https://img.shields.io/badge/Minecraft-1.16%2B-dark_green.svg)](https://shields.io/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://shields.io/)
[![JitPack](https://jitpack.io/v/senkex/HeadRender.svg)](https://jitpack.io/#senkex/HeadRender)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Small library focused on **one thing: player heads**. It fetches a skin, crops the
`8×8` face and turns every pixel into a HEX-colored chat line, so you can print a clean
avatar next to whatever info you want (welcome messages, profiles, `/seen`, …). It can
also build the **head as a real item / skull block** that works on every version.

No body, no full-skin parts — just the head, done well.

> [!IMPORTANT]
> Rendering to chat requires Minecraft **1.16+** for HEX colors (`§x§r§r§g§g§b§b`).
> The head *item* API works from **1.8** onward.

> [!CAUTION]
> [Shade](#shading) the library when shipping it inside a plugin, or two plugins on
> different versions will clash on the same package.

The whole point is to stay simple: one static facade, async by default, a builder when
you actually need to tweak something. No plugin instance, no `onEnable`, no config files.

## Getting Started

Targets **Java 17**, uses `CompletableFuture` for every IO call so HTTP never blocks the
main thread. The default skin source goes **straight to Mojang** (online skins in offline
mode, no proxy) and falls back to [Minotar](https://minotar.net) if Mojang is down, with
an in-memory LRU cache (10 min TTL).

Drop it in with JitPack:

#### Maven
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.senkex</groupId>
    <artifactId>HeadRender</artifactId>
    <version>version</version>
</dependency>
```

#### Gradle (Kotlin DSL)
```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.senkex:HeadRender:version' }
```

## Rendering a head to chat

The future resolves with one chat line per pixel row. Names and UUIDs both work:

```java
HeadRender.render("Senkex").thenAccept(lines -> lines.forEach(player::sendMessage));
HeadRender.render(player.getUniqueId()).thenAccept(player::sendMessage);
```

Tweak size, character, helmet layer or transparency with `RenderOptions`:

```java
RenderOptions options = RenderOptions.builder()
        .size(10)
        .character("⬛")
        .helmetLayer(true)
        .alphaThreshold(20)
        .build();

HeadRender.render("Senkex", options).thenAccept(lines -> lines.forEach(player::sendMessage));
```

The output is just a `List<String>`, so you send it wherever you want — chat, a hologram,
a text display, an action-bar wrapper, an NPC plugin, a scoreboard API, anything that
consumes multi-line text.

## Inline head tags

Embed heads inside arbitrary text with `<head>NAME</head>` tags. The library parses the
input, renders every tag and gives you back chat-ready lines. The head occupies `size`
rows; surrounding text sits on the center row, padded so columns line up. No pack, no mod:

```java
HeadRender.parse("Welcome <head>Senkex</head> to the server!")
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

Tags are case-insensitive, accept names or UUIDs, and you can mix several with newlines.
Pass a `RenderOptions` as the second argument to style them.

Prefer a different syntax? Every variant returns the same chat-ready lines:

| Call | Matches |
|---|---|
| `parse(text)` | `<head>NAME</head>` |
| `parse(text, options, "face")` | a custom tag, e.g. `<face>NAME</face>` |
| `parseNamespaced(text)` | `%headrender:NAME%` |
| `parsePlaceholders(text)` | `%head-NAME%` / `%head_NAME%` (PlaceholderAPI-style) |
| `parse(text, options, pattern)` | your own `Pattern` (name in capture group `1`) |

```java
// Custom pattern example: {head:NAME}
Pattern p = Pattern.compile("\\{head:([^}\\s]+)}");
HeadRender.parse("Hi {head:Senkex}!", RenderOptions.defaults(), p)
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

## Per-row heads (`<hd>` markers)

When you want the head on the left and one line of text per row (a stats panel, say), mark
each line with `<hd>` and `renderRows` splices one head row into each. The head is only
injected when the number of `<hd>` lines equals the head height (8 for a default head).

The text after the marker is **your own** — HeadRender doesn't depend on PlaceholderAPI and
doesn't translate `&` colors. It just splices the head; you resolve placeholders and colors
yourself first (the `<hd>` marker stays at the very start, so pre-processing leaves it
intact):

```java
List<String> template = List.of(
        "<hd>",
        "<hd>  &fName: &b%player_name%",
        "<hd>  &fRank: &e%vault_rank%",
        "<hd>",
        "<hd>  &fBalance: &a$%vault_eco_balance%",
        "<hd>",
        "<hd>  &fOnline: &a%server_online%&7/&a%server_max_players%",
        "<hd>");

// Resolve PlaceholderAPI + legacy colors before rendering
List<String> resolved = template.stream()
        .map(line -> PlaceholderAPI.setPlaceholders(player, line))
        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
        .collect(Collectors.toList());

HeadRender.renderRows("Senkex", resolved).thenAccept(lines -> lines.forEach(player::sendMessage));
```

The marker only matches at the very start of a line, so it never collides with HEX colors
or gradient tags. Pass a custom marker with `renderRows(target, template, options, marker)`,
or merge manually with `HeadRowMerger.merge(template, headRows)`.

## Head items & skull blocks

`HeadItem` builds the head as a real **player-head item** (or places a skull block) from a
name, UUID, skin URL or base64 texture. **No NMS, works 1.8 → latest**: it uses Bukkit's
`PlayerProfile` API on 1.18.1+ and the classic reflection route below that, picking the
right material (`PLAYER_HEAD`, or legacy `SKULL_ITEM:3`) automatically.

```java
ItemStack a = HeadItem.fromUrl("http://textures.minecraft.net/texture/…");
ItemStack b = HeadItem.fromUuid(uuid);
ItemStack c = HeadItem.fromBase64(base64);

// Resolve a player's real skin from Mojang (offline mode, no proxy) and build the item
HeadItem.fromPlayer("Senkex").thenAccept(head ->
        Bukkit.getScheduler().runTask(plugin, () -> player.getInventory().addItem(head)));

// Skull blocks
HeadItem.blockWithUrl(block, url);
HeadItem.blockWithUuid(block, uuid);
```

`HeadItem` touches the Spigot API (a `compileOnly` dependency) — use it inside a plugin.
It reuses HeadRender's own Mojang resolution: `fromTextures(provider.fetchTextures(name))`
turns a resolved skin straight into an item.

## Adventure components

Every `HeadRender` method has a mirror on `HeadRenderComponents` returning Kyori Adventure
`Component`s instead of legacy strings — for Paper/Velocity, hover/click decoration or
MiniMessage interop:

```java
HeadRenderComponents.render("Senkex").thenAccept(lines -> lines.forEach(player::sendMessage));
HeadRenderComponents.renderMultiline("Senkex").thenAccept(player::sendMessage); // single joined component
```

Adventure is **optional** (`compileOnly`): the plain facade never touches it. Add it only
if you call the component API. Already have legacy lines? Convert them with
`AdventureTextSerializer.toComponents(lines)` / `toMultilineComponent(lines)`.

## Renderers

How pixels become text is pluggable through the `HeadRenderer` strategy, swapped on the
service builder:

- **`HexPixelRenderer`** (default) — one colored character per pixel, no resource pack,
  `size` stacked lines.
- **`FontHeadRenderer`** — a single-character inline head for a tab name, action bar or
  scoreboard. A multicolor head on **one** line is impossible in vanilla chat, so this one
  bakes each head into a bitmap-font glyph via `ResourcePackGenerator`:

```java
ResourcePackGenerator pack = new ResourcePackGenerator().packFormat(34); // match your MC version
HeadRender.use(DefaultHeadRenderService.builder().renderer(new FontHeadRenderer(pack)).build());

String glyph = HeadRender.render("Senkex").join().get(0); // registers + returns the glyph
pack.writeZip(new File("plugins/MyPlugin/heads.zip"));     // host it as a server resource pack

// The glyph only draws with the pack + its font applied:
Component head = Component.text(glyph).font(Key.key(pack.fontKey()));
```

## Skin providers

The skin source is pluggable and combinable on the service builder:

| Provider | Target | Notes |
|---|---|---|
| `MojangSkinProvider` | name or UUID | **direct from Mojang, no proxy** — online skins in offline mode (default) |
| `MinotarSkinProvider` | name or UUID | proxy fallback |
| `CrafatarSkinProvider` | UUID only | adds the helmet overlay when enabled |
| `SkinMcSkinProvider` | name | SkinMC face endpoint |
| `UrlSkinProvider` | image URL | crops the face from a full skin |
| `LocalFileSkinProvider` | file name | reads `<dir>/<name>.png` |
| `StaticSkinProvider` | (ignored) | always serves one image — great as a fallback |
| `FallbackSkinProvider` | — | tries a chain, first success wins |

```java
SkinProvider source = new FallbackSkinProvider(
        new MojangSkinProvider(),            // official source, no proxy
        new MinotarSkinProvider(),           // proxy fallback if Mojang is down/limited
        new StaticSkinProvider(steveImage)); // last-resort Steve, never fails

HeadRender.use(DefaultHeadRenderService.builder().provider(source).build());
```

`MojangSkinProvider` is what shows a player's **online-mode head while your server runs in
offline mode**: the skin is looked up by name against Mojang, independent of how the player
authenticated (the same approach BungeeTabListPlus uses for `%head-<player>`). It needs no
JSON dependency and caches resolved URLs with a short TTL.

> [!NOTE]
> A name only resolves if it's a **premium** account. For offline players whose name isn't
> premium, chain a `StaticSkinProvider` Steve/Alex as the final fallback.

## Custom service & cache

The static facade wraps a `HeadRenderService`, so you can replace the provider, the cache,
or both — e.g. a longer TTL or a bigger cache:

```java
HeadRenderService service = DefaultHeadRenderService.builder()
        .provider(new MinotarSkinProvider(3000))
        .cache(new InMemorySkinCache(512, TimeUnit.MINUTES.toMillis(30)))
        .build();

HeadRender.use(service);
```

The cache is shared across calls on the same service (keyed by lowercase target, so
`"Senkex"` and `"senkex"` share an entry). A few helpers sit on the facade:

```java
HeadRender.cacheSize();
HeadRender.clearCache();
HeadRender.cache().invalidate("Senkex");
HeadRender.shutdown(); // on plugin disable — releases the thread pool
```

## Shading

When you ship HeadRender **inside** your plugin jar you must relocate its package. Two
plugins bundling different versions of `com.github.senkex.headrender` will otherwise load
whichever one wins the classpath and break the other. Relocation moves the classes under
your own package so each plugin carries its own private copy.

Change `my.plugin.libs.headrender` to a package inside your own plugin, then rebuild.

### Maven (Shade Plugin)

Add the plugin to your `<build><plugins>` and bind it to the `package` phase:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>com.github.senkex.headrender</pattern>
                        <shadedPattern>my.plugin.libs.headrender</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Build with `mvn package` — the shaded jar in `target/` is the one you ship.

### Gradle (Shadow)

Apply the Shadow plugin, relocate the package, and make `build` produce the shaded jar:

```kotlin
plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

tasks {
    shadowJar {
        relocate("com.github.senkex.headrender", "my.plugin.libs.headrender")
    }

    build {
        dependsOn(shadowJar)
    }
}
```

Groovy DSL:

```groovy
plugins {
    id 'com.gradleup.shadow' version '8.3.5'
}

tasks {
    shadowJar {
        relocate 'com.github.senkex.headrender', 'my.plugin.libs.headrender'
    }

    build {
        dependsOn shadowJar
    }
}
```

Build with `gradle shadowJar` (or `build`) — the shaded jar in `build/libs/` is the one
you ship.

## License

Released under the MIT License. Do whatever you want with it; attribution is appreciated.
