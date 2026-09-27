package me.lovkar.voyager.photo;

/**
 * What kind of roll of film something is, as bits - read off the roll by
 * {@code compat.ExposureCamera.filmTraits} and handed around as a plain int, so nothing outside the
 * compat package has to name one of Exposure's classes.
 */
public final class FilmTraits {

    /** It is a roll of film at all. */
    public static final int FILM = 1;
    /** Black and white (Exposure's own film type, or a Game Boy roll). */
    public static final int BLACK_AND_WHITE = 2;
    /** High-sensitivity: sees in the dark. */
    public static final int SENSITIVE = 4;
    /** A larger frame than the default - Exposure: Expanded's high-resolution rolls. */
    public static final int HIGH_RESOLUTION = 8;
    /** One of Expanded's vanity films with a palette of its own (Game Boy, NES, C64, CGA). */
    public static final int VANITY = 16;

    private FilmTraits() {
    }

    public static boolean isFilm(final int traits) {
        return (traits & FILM) != 0;
    }

    public static boolean blackAndWhite(final int traits) {
        return (traits & BLACK_AND_WHITE) != 0;
    }

    public static boolean sensitive(final int traits) {
        return (traits & SENSITIVE) != 0;
    }

    public static boolean highResolution(final int traits) {
        return (traits & HIGH_RESOLUTION) != 0;
    }

    public static boolean vanity(final int traits) {
        return (traits & VANITY) != 0;
    }
}
