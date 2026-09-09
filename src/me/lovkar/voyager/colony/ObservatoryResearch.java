package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.IColony;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.sky.SkyCatalogue;
import net.minecraft.resources.ResourceLocation;

/**
 * What the Observatory has learned, read by the rest of the mod.
 *
 * <p>These are not MineColonies research effects any more. They are granted by the Observatory's
 * own studies ({@link SkyStudyModule}), which are bought with nights of watching rather than with
 * hours of a timer, and they are read straight out of the buildings - merged across every
 * Observatory in the colony, so a second one shares what the first learned.</p>
 *
 * <p>The ids are kept in {@code voyager:effects/&lt;name&gt;} shape because that is what every
 * call site already says and because a datapack writing a study names the same bare {@code name}.</p>
 */
public final class ObservatoryResearch {

    /** Extra lens tiers on top of what the building's level gives (1, then 2). */
    public static final ResourceLocation LENS_GRINDING = effect("lens_grinding");
    /** The colony stays up on an event night, and nobody is the worse for it in the morning. */
    public static final ResourceLocation STAR_PARTY = effect("star_party");
    /** Tomorrow night's event is announced a day early. */
    public static final ResourceLocation EPHEMERIS = effect("ephemeris");
    /** Fraction of the developing time that is saved. */
    public static final ResourceLocation DARKROOM_DISCIPLINE = effect("darkroom_discipline");
    /** A plate of an object the colony already has is still worth something. */
    public static final ResourceLocation SECOND_EXPOSURE = effect("second_exposure");
    /** Two plates of one object combine into a better print. */
    public static final ResourceLocation COMPARATIVE_ASTRONOMY = effect("comparative_astronomy");
    /** One more astronomer per Observatory. */
    public static final ResourceLocation APPRENTICE = effect("apprentice");

    // Bought at the Observatory, spent at the Departure Point. The astronomer's book is the only
    // place these three are sold, which is the point of building one.

    /** Fraction of the time away the Observatory's charts save, on top of Rapid Refit. */
    public static final ResourceLocation STAR_CHARTS = effect("star_charts");
    /** One more find per expedition: the Observatory said where to look. */
    public static final ResourceLocation DEEP_FIELD = effect("deep_field");
    /** One more expedition per launch window: the almanac says go tonight. */
    public static final ResourceLocation LAUNCH_WINDOW = effect("launch_window");

    private ObservatoryResearch() {
    }

    private static ResourceLocation effect(final String name) {
        return ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "effects/" + name);
    }

    /** The best any Observatory in the colony has reached for this effect. */
    public static double strength(final IColony colony, final ResourceLocation effect) {
        if (colony == null || effect == null) {
            return 0.0;
        }
        final String name = effect.getPath().startsWith("effects/")
                ? effect.getPath().substring("effects/".length()) : effect.getPath();
        double best = 0.0;
        for (final BuildingObservatory observatory : SkyCatalogue.observatories(colony)) {
            final SkyStudyModule module = observatory.getModule(ObservatoryModules.STUDY);
            if (module != null) {
                best = Math.max(best, module.strength(name));
            }
        }
        return best;
    }

    public static boolean has(final IColony colony, final ResourceLocation effect) {
        return strength(colony, effect) > 0.0;
    }
}
