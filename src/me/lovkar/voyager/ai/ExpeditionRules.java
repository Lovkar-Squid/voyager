package me.lovkar.voyager.ai;

/**
 * The arithmetic of an expedition that the server config tunes, kept out of the AI.
 *
 * <p>Plain numbers in, plain numbers out: no colony, no world, no MineColonies class. That is the
 * point - the rig's config probe checks these on a server with no colony on it, and whatever the
 * AI decides with them it decides with exactly what was checked.</p>
 */
public final class ExpeditionRules {

    /** Ticks away per find in the expedition roll, before the trip length multiplier. */
    public static final int TICKS_PER_FIND = 400;
    /** A driven-back Voyager comes home with this share of max health (never less than {@link #MIN_WOUNDED_HEALTH}). */
    public static final float WOUNDED_SHARE = 0.25f;
    public static final float MIN_WOUNDED_HEALTH = 1.0f;
    /**
     * Below this distance from 1.0 a multiplier counts as 1.0: the config file stores a double, and
     * a value written as 1.0 must leave the plans exactly as they are.
     */
    private static final double SAME = 1.0e-9;

    private ExpeditionRules() {
    }

    /**
     * Expeditions a Departure Point may launch per launch window: the configured base plus the
     * research bonuses (Starlight Navigation, Launch Window), per Voyager, for a crew of at least one.
     */
    public static int tripsPerWindow(final int basePerVoyager, final int researchBonus, final int crew) {
        return (basePerVoyager + researchBonus) * Math.max(1, crew);
    }

    /**
     * Ticks a Voyager is away: one find's time per find, shortened by Rapid Refit and Star Charts
     * ({@code refit}, a share 0-0.8), then scaled by the trip length multiplier. The floor of one
     * find's time scales with the multiplier too, so a short-trips server really has short trips.
     * At a multiplier of 1.0 this is the 0.3.9 formula to the tick.
     */
    public static int ticksAway(final int findCount, final double refit, final double lengthMultiplier) {
        return (int) Math.max(TICKS_PER_FIND * lengthMultiplier,
                Math.max(1, findCount) * TICKS_PER_FIND * (1.0 - refit) * lengthMultiplier);
    }

    /** Whether a supply multiplier changes anything at all. */
    public static boolean scales(final double multiplier) {
        return Math.abs(multiplier - 1.0) > SAME;
    }

    /**
     * One supply amount of an expedition plan, scaled: rounded up, and never below one (a quarter
     * of 3 ender pearls is still a pearl). The tiny allowance keeps floating point from rounding
     * 10 x 0.3 up to 4.
     */
    public static int scaledAmount(final int amount, final double multiplier) {
        if (!scales(multiplier)) {
            return amount;
        }
        return Math.max(1, (int) Math.ceil(amount * multiplier - SAME));
    }

    /** Whether a blow that takes {@code blow} hp leaves a Voyager on {@code health} hp at zero or below. */
    public static boolean isFatalBlow(final float health, final float blow) {
        return health - blow <= 0.0f;
    }

    /**
     * Whether the Voyager is driven back instead of taking the blow: only with permanent death off,
     * and only when the blow would be fatal. With permanent death on nothing changes.
     */
    public static boolean drivenBack(final boolean permanentDeath, final float health, final float blow) {
        return !permanentDeath && isFatalBlow(health, blow);
    }

    /** The health a driven-back Voyager is left with: a quarter of max health, at least 1 hp. */
    public static float woundedHealth(final float maxHealth) {
        return Math.max(MIN_WOUNDED_HEALTH, maxHealth * WOUNDED_SHARE);
    }
}
