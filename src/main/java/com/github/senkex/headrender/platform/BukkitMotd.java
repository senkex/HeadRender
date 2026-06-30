package com.github.senkex.headrender.platform;

import org.bukkit.event.server.ServerListPingEvent;

import java.util.List;
import java.util.Objects;

/**
 * Applies rendered head lines to a Bukkit/Spigot MOTD.
 *
 * <p>The server list MOTD has exactly <b>two</b> lines, so render with
 * {@code size 2} (e.g. {@code RenderOptions.of(2)}) and pass the result here.
 * Extra lines are ignored; missing lines are padded with blanks.</p>
 *
 * <p>This class touches the Spigot API, which is a {@code compileOnly}
 * dependency — the core library never loads it. Use it only inside a Bukkit
 * plugin (e.g. from a {@code ServerListPingEvent} handler).</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class BukkitMotd {

    private BukkitMotd() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds a two-line MOTD string from the given rendered lines.
     *
     * @param lines the rendered head lines, must not be {@code null}
     * @return a {@code line1 \n line2} MOTD string
     */
    public static String motd(final List<String> lines) {
        Objects.requireNonNull(lines, "Lines cannot be null");
        final String first = lines.size() > 0 ? lines.get(0) : "";
        final String second = lines.size() > 1 ? lines.get(1) : "";
        return first + "\n" + second;
    }

    /**
     * Applies the given rendered lines to the MOTD of a ping event.
     *
     * @param event the server list ping event, must not be {@code null}
     * @param lines the rendered head lines, must not be {@code null}
     */
    public static void apply(final ServerListPingEvent event, final List<String> lines) {
        Objects.requireNonNull(event, "Event cannot be null");
        event.setMotd(motd(lines));
    }
}
