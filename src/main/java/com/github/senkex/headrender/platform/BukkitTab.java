package com.github.senkex.headrender.platform;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Pushes rendered head lines into a player's tab list header/footer.
 *
 * <p>The tab header and footer are multi-line, so a head stacks cleanly there
 * (a {@code size} of {@code 2}–{@code 4} is a good fit). Lines are joined with
 * newlines.</p>
 *
 * <p>This class touches the Spigot API, a {@code compileOnly} dependency — use
 * it only inside a Bukkit plugin.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class BukkitTab {

    private BukkitTab() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Joins rendered lines into a single newline-separated string.
     *
     * @param lines the lines to join, must not be {@code null}
     * @return the joined text
     */
    public static String join(final List<String> lines) {
        Objects.requireNonNull(lines, "Lines cannot be null");
        return String.join("\n", lines);
    }

    /**
     * Sets the player's tab header from the given rendered lines.
     *
     * @param player the target player, must not be {@code null}
     * @param lines the rendered head lines, must not be {@code null}
     */
    public static void header(final Player player, final List<String> lines) {
        Objects.requireNonNull(player, "Player cannot be null");
        player.setPlayerListHeader(join(lines));
    }

    /**
     * Sets the player's tab footer from the given rendered lines.
     *
     * @param player the target player, must not be {@code null}
     * @param lines the rendered head lines, must not be {@code null}
     */
    public static void footer(final Player player, final List<String> lines) {
        Objects.requireNonNull(player, "Player cannot be null");
        player.setPlayerListFooter(join(lines));
    }

    /**
     * Sets both the header and footer at once.
     *
     * @param player the target player, must not be {@code null}
     * @param header the header lines, must not be {@code null}
     * @param footer the footer lines, must not be {@code null}
     */
    public static void headerFooter(final Player player, final List<String> header, final List<String> footer) {
        Objects.requireNonNull(player, "Player cannot be null");
        player.setPlayerListHeaderFooter(join(header), join(footer));
    }
}
