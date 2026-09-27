package me.lovkar.voyager.photo;

import java.util.ArrayList;
import java.util.List;

import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import me.lovkar.voyager.Voyager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The Photo Booth's own settings tab: which film the photographer loads and which filter he fits
 * to the camera before every picture - so the colony's photographs look the way you want them to.
 *
 * <p>Everything here is a real item. The film is a roll off the shelf (or out of his pack, where a
 * delivery lands); the filter is a stained glass pane or one of Exposure: Expanded's filters, fitted
 * into the camera's own filter slot, where a player who takes the camera off the shelf will find it.
 * What he does not have he asks the colony for. The renderer ({@code ExposureCamera}) already draws
 * whatever is fitted; these settings only decide what gets fitted.</p>
 */
public final class StudioSettings {

    public static final ISettingKey<StringSetting> FILM =
            new SettingKey<>(StringSetting.class, ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "film"));
    public static final ISettingKey<StringSetting> FILTER =
            new SettingKey<>(StringSetting.class, ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "filter"));

    // The values are lang keys: MineColonies shows a StringSetting's value translated.
    public static final String FILM_ANY = "com.voyager.setting.film.any";
    public static final String FILM_BW = "com.voyager.setting.film.bw";
    public static final String FILM_COLOUR = "com.voyager.setting.film.colour";
    public static final String FILM_BW_FAST = "com.voyager.setting.film.bw_fast";
    public static final String FILM_COLOUR_FAST = "com.voyager.setting.film.colour_fast";
    public static final String FILM_BW_HIRES = "com.voyager.setting.film.bw_hires";
    public static final String FILM_COLOUR_HIRES = "com.voyager.setting.film.colour_hires";

    public static final String FILTER_CAMERA = "com.voyager.setting.filter.camera";
    public static final String FILTER_NONE = "com.voyager.setting.filter.none";
    public static final String FILTER_RED = "com.voyager.setting.filter.red";
    public static final String FILTER_ORANGE = "com.voyager.setting.filter.orange";
    public static final String FILTER_YELLOW = "com.voyager.setting.filter.yellow";
    public static final String FILTER_GREEN = "com.voyager.setting.filter.green";
    public static final String FILTER_BLUE = "com.voyager.setting.filter.blue";
    public static final String FILTER_SEPIA = "com.voyager.setting.filter.sepia";
    public static final String FILTER_PENCIL = "com.voyager.setting.filter.pencil";
    public static final String FILTER_OUTLINE = "com.voyager.setting.filter.outline";
    public static final String FILTER_SOFT = "com.voyager.setting.filter.soft";
    public static final String FILTER_MIRROR = "com.voyager.setting.filter.mirror";
    public static final String FILTER_FADED = "com.voyager.setting.filter.faded";
    public static final String FILTER_VIVID = "com.voyager.setting.filter.vivid";

    private StudioSettings() {
    }

    /**
     * The film setting, "whatever is on the shelf" first - what the booth has always done. The
     * high-resolution rolls are Exposure: Expanded's, so they are offered only when it is there.
     * (The choice is saved by name, see {@code PhotoBoothSettingsModule}, so the list may grow.)
     */
    public static StringSetting filmSetting() {
        final List<String> values = new ArrayList<>(List.of(FILM_ANY, FILM_BW, FILM_COLOUR, FILM_BW_FAST, FILM_COLOUR_FAST));
        if (expanded()) {
            values.addAll(List.of(FILM_BW_HIRES, FILM_COLOUR_HIRES));
        }
        return new StringSetting(values, 0);
    }

    /**
     * The filter setting, "as fitted in the camera" first - so nothing changes until somebody
     * picks a filter. The effect filters are offered only when Exposure: Expanded is there to make them.
     */
    public static StringSetting filterSetting() {
        final List<String> values = new ArrayList<>(List.of(FILTER_CAMERA, FILTER_NONE, FILTER_RED, FILTER_ORANGE,
                FILTER_YELLOW, FILTER_GREEN, FILTER_BLUE, FILTER_SEPIA));
        if (expanded()) {
            values.addAll(List.of(FILTER_PENCIL, FILTER_OUTLINE, FILTER_SOFT, FILTER_MIRROR, FILTER_FADED, FILTER_VIVID));
        }
        return new StringSetting(values, 0);
    }

    private static boolean expanded() {
        try {
            return net.neoforged.fml.ModList.get().isLoaded("exposure_expanded");
        } catch (final Throwable noModList) {
            return false;
        }
    }

    /**
     * Does a roll of this kind ({@link FilmTraits}, from {@code ColonyCamera.filmTraits}) do for the
     * film setting? The settings choose a kind, not an item: "black and white" is any black-and-white
     * roll that is not a fast one - Exposure's own, or Expanded's high-capacity and high-resolution
     * rolls. Expanded's vanity films (Game Boy, NES, C64, CGA) are only ever used on "whatever is on
     * the shelf": the colonist's picture is drawn in the map's colours, not in their palettes.
     */
    public static boolean filmMatches(final String choice, final int traits) {
        if (!FilmTraits.isFilm(traits)) {
            return false;
        }
        final boolean bw = FilmTraits.blackAndWhite(traits);
        final boolean fast = FilmTraits.sensitive(traits);
        final boolean hires = FilmTraits.highResolution(traits);
        final boolean vanity = FilmTraits.vanity(traits);
        return switch (choice == null ? FILM_ANY : choice) {
            case FILM_BW -> bw && !fast && !vanity;
            case FILM_COLOUR -> !bw && !fast && !vanity;
            case FILM_BW_FAST -> bw && fast && !vanity;
            case FILM_COLOUR_FAST -> !bw && fast && !vanity;
            case FILM_BW_HIRES -> bw && hires && !vanity;
            case FILM_COLOUR_HIRES -> !bw && hires && !vanity;
            default -> true;
        };
    }

    /**
     * Among the rolls that do, which to spend first - lowest first: the plainest roll that does the
     * job, so a high-resolution, fast or vanity roll is only used up when the setting asks for it
     * or nothing else is left.
     */
    public static int filmCost(final String choice, final int traits) {
        final String c = choice == null ? FILM_ANY : choice;
        int cost = 0;
        if (FilmTraits.highResolution(traits) && !FILM_BW_HIRES.equals(c) && !FILM_COLOUR_HIRES.equals(c)) {
            cost += 2;
        }
        if (FilmTraits.sensitive(traits) && !FILM_BW_FAST.equals(c) && !FILM_COLOUR_FAST.equals(c)) {
            cost += 1;
        }
        if (FilmTraits.vanity(traits)) {
            cost += 4;
        }
        return cost;
    }

    /** The roll to ask the colony for when there is none: one of the chosen kind, or plain black and white. */
    public static ResourceLocation filmToRequest(final String choice) {
        return switch (choice == null ? FILM_ANY : choice) {
            case FILM_COLOUR -> id("exposure", "color_film");
            case FILM_BW_FAST -> id("exposure", "high_sensitivity_black_and_white_film");
            case FILM_COLOUR_FAST -> id("exposure", "high_sensitivity_color_film");
            case FILM_BW_HIRES -> id("exposure_expanded", "hires_black_and_white_film");
            case FILM_COLOUR_HIRES -> id("exposure_expanded", "hires_color_film");
            default -> id("exposure", "black_and_white_film");
        };
    }

    /** The item a filter setting fits into the camera; null for "as fitted" and for "no filter". */
    public static @Nullable ResourceLocation filterItem(final String choice) {
        return switch (choice == null ? FILTER_CAMERA : choice) {
            case FILTER_RED -> id("minecraft", "red_stained_glass_pane");
            case FILTER_ORANGE -> id("minecraft", "orange_stained_glass_pane");
            case FILTER_YELLOW -> id("minecraft", "yellow_stained_glass_pane");
            case FILTER_GREEN -> id("minecraft", "green_stained_glass_pane");
            case FILTER_BLUE -> id("minecraft", "blue_stained_glass_pane");
            case FILTER_SEPIA -> id("minecraft", "brown_stained_glass_pane");
            case FILTER_PENCIL -> id("exposure_expanded", "pencil_filter");
            case FILTER_OUTLINE -> id("exposure_expanded", "sobel_filter");
            case FILTER_SOFT -> id("exposure_expanded", "blur_filter");
            case FILTER_MIRROR -> id("exposure_expanded", "flip_filter");
            case FILTER_FADED -> id("exposure_expanded", "desaturate_filter");
            case FILTER_VIVID -> id("exposure_expanded", "color_convolve_filter");
            default -> null;
        };
    }

    private static ResourceLocation id(final String ns, final String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }
}
