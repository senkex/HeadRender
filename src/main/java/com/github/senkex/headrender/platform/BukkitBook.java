package com.github.senkex.headrender.platform;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a written book whose pages contain rendered heads.
 *
 * <p>A book page is narrow (~19 characters and ~14 lines in the default font),
 * which comfortably fits a head up to {@code size 8}. Each rendered head goes
 * on its own page, with the pixel lines joined by newlines.</p>
 *
 * <p>This class touches the Spigot API, a {@code compileOnly} dependency — use
 * it only inside a Bukkit plugin.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class BukkitBook {

    private BukkitBook() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds a single-page written book from the given rendered lines.
     *
     * @param title the book title, must not be {@code null}
     * @param author the book author, must not be {@code null}
     * @param lines the rendered head lines (one page), must not be {@code null}
     * @return the written book item
     */
    public static ItemStack singlePage(final String title, final String author, final List<String> lines) {
        Objects.requireNonNull(lines, "Lines cannot be null");
        final List<List<String>> pages = new ArrayList<>(1);
        pages.add(lines);
        return book(title, author, pages);
    }

    /**
     * Builds a written book with one page per rendered head.
     *
     * @param title the book title, must not be {@code null}
     * @param author the book author, must not be {@code null}
     * @param pages one list of rendered lines per page, must not be {@code null}
     * @return the written book item
     */
    public static ItemStack book(final String title, final String author, final List<List<String>> pages) {
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(author, "Author cannot be null");
        Objects.requireNonNull(pages, "Pages cannot be null");

        final ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        final BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("WRITTEN_BOOK has no BookMeta");
        }
        meta.setTitle(title);
        meta.setAuthor(author);
        final List<String> rendered = new ArrayList<>(pages.size());
        for (final List<String> page : pages) {
            rendered.add(String.join("\n", page));
        }
        meta.setPages(rendered);
        item.setItemMeta(meta);
        return item;
    }
}
