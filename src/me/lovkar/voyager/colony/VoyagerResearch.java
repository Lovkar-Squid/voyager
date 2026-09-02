package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.IColony;
import me.lovkar.voyager.Voyager;
import net.minecraft.resources.ResourceLocation;

/**
 * The Voyager's branch of the University's Technology tree (data/voyager/researches). Every
 * entry here is a research effect id; the JSON files under researches/effects define their
 * levels, the code asks the colony for the strength.
 */
public final class VoyagerResearch {

    /** Fraction of fight damage the Voyager does not take (0.25, then 0.5). */
    public static final ResourceLocation VOID_INSURANCE = effect("void_insurance");
    /** Extra expeditions per launch window. */
    public static final ResourceLocation STARLIGHT_NAVIGATION = effect("starlight_navigation");
    /** Fraction of the time away that is saved. */
    public static final ResourceLocation RAPID_REFIT = effect("rapid_refit");
    /** Extra shulker shells per shulker beaten. */
    public static final ResourceLocation SHULKER_WHISPERER = effect("shulker_whisperer");
    /** Extra ender pearls per enderman beaten. */
    public static final ResourceLocation ENDER_HARVEST = effect("ender_harvest");
    /** Level 5 Voyagers may run into the dragon. */
    public static final ResourceLocation DRAGON_HUNT = effect("dragon_hunt");
    /** Chat reports from the End while the Voyager is away. */
    public static final ResourceLocation LONG_RANGE_COMMS = effect("long_range_comms");
    /** A lost Voyager's gear comes back to the hut. */
    public static final ResourceLocation RETURN_TO_SENDER = effect("return_to_sender");
    /** Extra Voyagers per Departure Point. */
    public static final ResourceLocation BUDDY_SYSTEM = effect("buddy_system");

    private VoyagerResearch() {
    }

    private static ResourceLocation effect(final String name) {
        return ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "effects/" + name);
    }

    public static double strength(final IColony colony, final ResourceLocation effect) {
        if (colony == null) {
            return 0.0;
        }
        return colony.getResearchManager().getResearchEffects().getEffectStrength(effect);
    }

    public static boolean has(final IColony colony, final ResourceLocation effect) {
        return strength(colony, effect) > 0.0;
    }
}
