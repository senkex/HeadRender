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

#### Custom Pattern

For non-XML placeholder syntaxes (`{head:NAME}`, `%head_NAME%`, MiniMessage
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
| Text Display / Hologram | `6` – `8` | Lines are tight, so the head looks compact |
| MOTD | `2` | MOTD only has two lines available |
| Action bar / single line | not supported | Needs a custom font / resource pack |

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
