# HeadRender

[![Minecraft](https://img.shields.io/badge/Minecraft-1.16%2B-dark_green.svg)](https://shields.io/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://shields.io/)
[![JitPack](https://jitpack.io/v/senkex/HeadRender.svg)](https://jitpack.io/#senkex/HeadRender)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Library for rendering Minecraft player heads. It resolves a skin, crops the `8×8` face
and turns it into chat text, an inline font glyph, a native `1.21.9` head component, or a
player-head item — one small dependency, no runtime dependencies of its own, async by
default.

> [!IMPORTANT]
> Chat rendering requires Minecraft **1.16+** (HEX colors). The head *item* API works from
> **1.8**. Native head components require a **1.21.9+** client.

> [!CAUTION]
> [Shade](#shading) the library when bundling it into a plugin, or two plugins on
> different versions will clash on the same package.

## Install

Via [JitPack](https://jitpack.io/#senkex/HeadRender).

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

### Adventure & MiniMessage

HeadRender declares Adventure, MiniMessage and Spigot as `compileOnly`. This is **not
transitive and never reaches your POM** — you inherit nothing and cannot end up with a
duplicate copy of Adventure fighting the server's own.

| Server | Declare |
|---|---|
| **Paper 1.18+** | nothing — Adventure and MiniMessage are already on the classpath |
| **Spigot** | nothing for the core API; the component/MiniMessage facades need Adventure yourself |

The plain `HeadRender` facade never touches Adventure, so the failure is lazy: only classes
that use components require it. On Spigot, add Adventure with `implementation` **plus a
relocation** — bundling it unrelocated is what produces `NoSuchMethodError`. See [Shading](#shading).

| Feature | Needs at runtime |
|---|---|
| `HeadRender`, `HeadItem`, providers, renderers | nothing |
| `HeadRenderComponents`, `AdventureTextSerializer` | `adventure-api`, `adventure-text-serializer-legacy` |
| `HeadRenderTags` (MiniMessage tag) | + `adventure-text-minimessage` |
| `NativeHeadComponents` (1.21.9 heads) | `adventure-api` **4.25.0+** |

## Rendering

Every call is asynchronous and resolves to one chat line per pixel row. Names and UUIDs
both work:

```java
HeadRender.render("Senkex").thenAccept(lines -> lines.forEach(player::sendMessage));
HeadRender.render(player.getUniqueId()).thenAccept(player::sendMessage);
```

Configure size, pixel character, helmet layer and transparency with `RenderOptions`:

```java
RenderOptions options = RenderOptions.builder()
        .size(10)
        .character("⬛")
        .helmetLayer(true)
        .alphaThreshold(20)
        .build();

HeadRender.render("Senkex", options).thenAccept(lines -> lines.forEach(player::sendMessage));
```

Output is a `List<String>`, usable on any surface that consumes multi-line text.

### Positioning

Shift the whole head sideways, the same way CenterMessage offsets text. `position`
is in **pixels** and is realized with leading spaces (a space is `4` px, so it
snaps to the nearest step). `centered` centers the head block in chat and takes
precedence over `position`:

```java
RenderOptions.builder().position(100).build();   // ~25 spaces to the right
RenderOptions.builder().centered(true).build();   // centered in chat (154 px)
RenderOptions.builder().centered(true).centerPx(90).build(); // centered in a narrower area
```

For centering a head **together with text beside it**, keep the head flush left
here and hand the rendered lines to CenterMessage's `CenterHead.card(...)`
(everything centered) or `CenterHead.beside(...)` (head fixed, text centered).

## Head sources (`<head:...>`)

`parseTags` matches the Adventure/MiniMessage self-closing tag, extended with source types
vanilla does not have:

```java
HeadRender.parseTags("Steve <head:entity/player/wide/steve> vs <head:Senkex:false>")
        .thenAccept(lines -> lines.forEach(player::sendMessage));
```

The value is resolved by `HeadSource`. Write the type explicitly, or let it be detected:

| Written | Type | Detected because |
|---|---|---|
| `<head:Senkex>` | player name | nothing else matched |
| `<head:1f085b2d-9548-…>` | UUID | parses as a UUID, dashed or trimmed |
| `<head:entity/player/wide/steve>` | vanilla texture key | contains `/` |
| `<head:eyJ0aW1lc3RhbXAi…>` | Mojang base64 textures | long base64 blob |
| `<head:https://textures.minecraft.net/…>` | direct URL | `http://` or `https://` |

Prefix to be explicit — `player:` / `name:`, `uuid:` / `id:`, `base64:` / `value:` /
`textures:`, `url:`, `texture:` / `key:` — and suffix `:true` / `:false` to force the
helmet layer for one head:

```
<head:player:Senkex>   <head:base64:eyJ0…>   <head:Senkex:false>
```

`%head:VALUE%` is the placeholder form, via `parseTyped`. The older forms
(`<head>NAME</head>`, `%head-NAME%`, `%headrender:NAME%`) accept these sources too.

| Method | Matches |
|---|---|
| `render(target)` / `render(uuid)` | a single head |
| `parseTags(text)` | `<head:VALUE>` |
| `parseTyped(text)` | `%head:VALUE%` |
| `parse(text)` | `<head>NAME</head>` |
| `parseNamespaced(text)` | `%headrender:NAME%` |
| `parsePlaceholders(text)` | `%head-NAME%` / `%head_NAME%` |
| `parse(text, options, pattern)` | your own `Pattern` (value in group `1`) |

## Untrusted input

`url:` and `base64:` make the server perform an outbound request to an address the tag
author chose. If head tags can arrive from chat, signs, books or nicknames, that is a
server-side request forgery vector.

`HeadSource.parse(raw)` applies `Policy.SAFE` by default: `url:` sources must use `https`
and target a known skin host, so `<head:url:http://localhost:8123/>` is rejected. Host
matching is exact — no wildcards, no credentials in the authority — and sources are capped
at 8 KB.

```java
HeadSource.parse(raw);                                // SAFE — the default
HeadSource.parse(raw, HeadSource.Policy.NAMES_ONLY);  // only name/UUID
HeadSource.parse(raw, HeadSource.Policy.TRUSTED);     // anything — your config only
```

| Policy | Allows | Use for |
|---|---|---|
| `SAFE` *(default)* | all types; URLs restricted to known hosts over https | any input, including player text |
| `NAMES_ONLY` | only `player` and `uuid` | public chat |
| `TRUSTED` | everything, any host, plain http | strings **you** wrote |

Tune the allowlist with `Policy.allowing(...).withHosts("cdn.myserver.net")`.

> [!CAUTION]
> Never apply `TRUSTED` to text a player can influence.

## Inline heads on one line

A multicolor head in a single character cell is impossible in vanilla chat — one character
is one color. Two renderers solve it differently.

### Resource pack glyph (1.16+)

`FontHeadRenderer` bakes each head into a bitmap-font glyph in the Unicode Private Use Area.
The head renders as a single character once the client has the generated pack:

```java
ResourcePackGenerator pack = new ResourcePackGenerator().packFormat(34); // match your MC version
HeadRender.use(DefaultHeadRenderService.builder().renderer(new FontHeadRenderer(pack)).build());

String glyph = HeadRender.render("Senkex").join().get(0); // registers + returns the glyph
pack.writeZip(new File("plugins/MyPlugin/heads.zip"));     // host it as a server resource pack

Component head = Component.text(glyph).font(Key.key(pack.fontKey()));
```

`HeadRenderTags` exposes this as a MiniMessage `<head:...>` tag. Rendering is async and
MiniMessage is not, so tags read from a bounded cache you warm with `preload`:

```java
HeadRenderTags tags = HeadRenderTags.create(service, Key.key(pack.fontKey()));

tags.preload(raw).thenAccept(v -> {
    Component msg = MiniMessage.miniMessage().deserialize(raw, tags.resolver());
    player.sendMessage(msg);
});
```

### Native component (1.21.9+)

Minecraft `1.21.9` added a native player-head component. `NativeHeadComponents` maps a
`HeadSource` onto it — nothing is downloaded, rendered, cached or threaded; the client draws
the head:

```java
if (NativeHeadComponents.isSupported()) {
    player.sendMessage(NativeHeadComponents.parseTags("Hola <head:Senkex>!"));
}

Component msg = MiniMessage.miniMessage()
        .deserialize("<gray>Hola <head:Senkex>!", HeadRenderTags.nativeResolver());
```

| | Native component | Resource-pack glyph |
|---|---|---|
| Clients | 1.21.9+ | 1.16+ |
| Network, threads, cache | none | one bounded fetch, cached |
| Resource pack | not needed | required |
| Sources | name, uuid, base64, texture key | all of those **+ `url:`** |

`isSupported()` reports the *server's* Adventure version, not the viewer's — detecting the
client requires ViaVersion or a platform API. `url:` has no vanilla equivalent, so
`playerHead` refuses it and `parseTags` leaves such a tag as literal text.

## Head items & skull blocks

`HeadItem` builds the head as a player-head item or places a skull block, from a name, UUID,
skin URL or base64 texture. No NMS, works 1.8 → latest: it uses Bukkit's `PlayerProfile` API
on 1.18.1+ and reflection below that, selecting the right material automatically.

```java
ItemStack a = HeadItem.fromUrl("https://textures.minecraft.net/texture/…");
ItemStack b = HeadItem.fromUuid(uuid);
ItemStack c = HeadItem.fromBase64(base64);

HeadItem.fromPlayer("Senkex").thenAccept(head ->
        Bukkit.getScheduler().runTask(plugin, () -> player.getInventory().addItem(head)));

HeadItem.blockWithUrl(block, url);
```

`HeadItem` uses the Spigot API (`compileOnly`) — call it inside a plugin.

## Adventure components

Every `HeadRender` method has a mirror on `HeadRenderComponents` returning Kyori Adventure
`Component`s instead of legacy strings, for Paper/Velocity, hover/click decoration or
MiniMessage interop:

```java
HeadRenderComponents.render("Senkex").thenAccept(lines -> lines.forEach(player::sendMessage));
HeadRenderComponents.renderMultiline("Senkex").thenAccept(player::sendMessage);
```

Have legacy lines already? Convert them with `AdventureTextSerializer.toComponents(lines)`.

## Skin providers

The skin source is pluggable and combinable on the service builder:

| Provider | Target | Notes |
|---|---|---|
| `MojangSkinProvider` | name or UUID | direct from Mojang, no proxy — online skins in offline mode (default) |
| `MinotarSkinProvider` | name or UUID | proxy fallback |
| `CrafatarSkinProvider` | UUID only | adds the helmet overlay when enabled |
| `SkinMcSkinProvider` | name | SkinMC face endpoint |
| `UrlSkinProvider` | image URL | crops the face from a full skin |
| `LocalFileSkinProvider` | file name | reads `<dir>/<name>.png` |
| `StaticSkinProvider` | (ignored) | always serves one image |
| `SourceSkinProvider` | `HeadSource` | dispatches by type; wraps the chain automatically |
| `FallbackSkinProvider` | — | tries a chain, first success wins |

```java
SkinProvider source = new FallbackSkinProvider(
        new MojangSkinProvider(),            // official source, no proxy
        new MinotarSkinProvider(),           // proxy fallback
        new StaticSkinProvider(steveImage)); // last resort, never fails

HeadRender.use(DefaultHeadRenderService.builder().provider(source).build());
```

`MojangSkinProvider` shows a player's online-mode head while the server runs in offline
mode: the skin is looked up by name against Mojang, independent of how the player
authenticated. It needs no JSON dependency and caches resolved URLs with a short TTL.

> [!NOTE]
> A name only resolves if it is a **premium** account. For non-premium offline players,
> chain a `StaticSkinProvider` Steve/Alex as the final fallback.

## Custom service & cache

```java
HeadRenderService service = DefaultHeadRenderService.builder()
        .provider(new MinotarSkinProvider(3000))
        .cache(new InMemorySkinCache(512, TimeUnit.MINUTES.toMillis(30)))
        .build();

HeadRender.use(service);
```

```java
HeadRender.cacheSize();
HeadRender.clearCache();
HeadRender.cache().invalidate("Senkex");
HeadRender.shutdown(); // on plugin disable — releases the thread pool
```

## Footprint

Everything is bounded and idles at zero:

| | |
|---|---|
| **Threads** | `0` while idle, **max 2**, daemon, `MIN_PRIORITY`, 30 s keep-alive |
| **Scheduler tasks** | none — no Bukkit runnable, no listener, no tick hook |
| **Skin cache** | LRU, 256 entries, 10 min TTL |
| **Glyph cache** | LRU, 512 entries, with a 64-render in-flight cap |
| **Runtime dependencies** | none — only `java.*` plus the server's own Adventure |

Disabling the cache (`useCache(false)`) re-downloads the same skin on every call and walks
into Mojang's rate limit (`HTTP 429`). Leave it on unless you have a reason.

## Package layout

```
com.github.senkex.headrender
├── HeadRender              facade — legacy §x strings
├── HeadRenderComponents    facade — Adventure Components
├── DefaultHeadRenderService
├── RenderOptions
├── api/                    contracts: HeadRenderService, HeadRenderer, SkinProvider, SkinCache
├── render/                 image → text: HexPixelRenderer, FontHeadRenderer, ResourcePackGenerator
├── skin/                   HeadSource, SkinFaces, TextureProperty, InMemorySkinCache
│   └── provider/           every SkinProvider implementation
├── text/                   HeadTagParser, HeadRowMerger
│   └── adventure/          AdventureTextSerializer, HeadRenderTags, NativeHeadComponents
└── item/                   HeadItem
```

If a class lives in `text.adventure`, it needs Adventure at runtime; nothing outside it does.

## Tests

```bash
./gradlew test        # unit suite, offline, ~1s
./gradlew smokeJar    # build/libs/HeadRender-<version>-smoke.jar
java -jar build/libs/HeadRender-2.0.0-smoke.jar
```

`src/test/java` mirrors the main tree: `HeadSourceTest` and `HeadSourcePolicyTest` (parsing
and the SSRF boundary — loopback, cloud metadata, host spoofing), `HeadTagParserTest`,
`NativeHeadComponentsTest` (the native wire format, pinned against Adventure's fixtures) and
`RenderPipelineTest` (end to end). Nothing touches the network — a synthetic skin runs
through a `StaticSkinProvider`, so the suite runs offline in about a second.

The smoke jar runs the same suite from a plain `java -jar` on any JDK 17+, so it works when
the Gradle wrapper's JDK requirement doesn't match your default `java`. It exits non-zero on
failure.

## Shading

When you bundle HeadRender inside your plugin jar you **must** relocate its package. Two
plugins bundling different versions of `com.github.senkex.headrender` will otherwise load
whichever wins the classpath and break the other.

### Maven (Shade Plugin)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
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

### Gradle (Shadow)

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

Change `my.plugin.libs.headrender` to a package inside your own plugin, then build the
shaded jar — the one in `build/libs/` (or Maven's `target/`) is what you ship.

## License

Released under the [MIT License](LICENSE).
