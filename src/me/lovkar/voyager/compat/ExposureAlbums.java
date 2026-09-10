package me.lovkar.voyager.compat;

import io.github.mortuusars.exposure.world.item.AlbumItem;
import io.github.mortuusars.exposure.world.item.SignedAlbumItem;
import io.github.mortuusars.exposure.world.item.component.album.AlbumContent;
import io.github.mortuusars.exposure.world.item.component.album.AlbumPage;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * Exposure's albums, as the colony's chronicle uses them.
 *
 * <p>An album is sixteen pages, each a photograph and a hand-written note. The Photo Booth keeps
 * one open on its shelf and pastes every chronicle photograph into the first free page with a note
 * saying what it is; when the sixteenth page fills, the album is signed - title and photographer -
 * which turns it into Exposure's signed album, a finished volume nobody can scribble in.</p>
 *
 * <p>Touches Exposure's classes, so it is reached only through {@link me.lovkar.voyager.photo.ColonyCamera}.</p>
 */
public final class ExposureAlbums {

    private ExposureAlbums() {
    }

    /** An album that can still be written in - not a signed one. */
    public static boolean isOpenAlbum(final ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof AlbumItem
                && !(stack.getItem() instanceof SignedAlbumItem);
    }

    /** Index of the first page without a photograph, or -1 when the album is full. */
    public static int freePage(final ItemStack album) {
        if (!isOpenAlbum(album)) {
            return -1;
        }
        final AlbumContent content = ((AlbumItem) album.getItem()).getContent(album);
        for (int i = 0; i < AlbumContent.MAX_PAGES; i++) {
            final Optional<AlbumPage> page = content.getPage(i);
            if (page.isEmpty() || page.get().photograph().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static int photographs(final ItemStack album) {
        return isOpenAlbum(album) ? ((AlbumItem) album.getItem()).getPhotographsCount(album) : 0;
    }

    /**
     * Paste a photograph into the first free page.
     *
     * @return true if it went in; false if the album is full or not an album
     */
    public static boolean paste(final ItemStack album, final ItemStack photograph, final String note) {
        final int index = freePage(album);
        if (index < 0) {
            return false;
        }
        ((AlbumItem) album.getItem()).updatePage(album, index,
                existing -> new AlbumPage(photograph.copy(), note == null ? "" : note));
        return true;
    }

    /** Sign a full album: a finished volume with a title and the photographer's name on it. */
    public static ItemStack sign(final ItemStack album, final String title, final String author) {
        if (!isOpenAlbum(album)) {
            return album;
        }
        return ((AlbumItem) album.getItem()).sign(album, title, author);
    }
}
