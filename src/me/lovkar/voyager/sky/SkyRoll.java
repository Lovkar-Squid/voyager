package me.lovkar.voyager.sky;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the astronomer caught tonight, and how rare it was.
 *
 * <p>The roll is weighted by the objects' own {@code appearance_chance} and gated by the lens the
 * colony has ground so far, which is exactly what their data is for. An event night raises the
 * odds by its own {@code luck} and opens its exclusive objects, which is the only way some of
 * them can ever be caught - so an event is worth staying up for.</p>
 *
 * <p>The six rarity bands Marko asked for are derived from the data rather than hand-sorted across
 * 256 objects: the tier you need to see it and how often it shows up. Event-exclusive is its own
 * top band, because "you had to be there" is the rarest thing a sky can offer.</p>
 */
public final class SkyRoll {

    /** The six bands, commonest first. */
    public enum Band {
        COMMON, NOTABLE, RARE, REMARKABLE, EXTRAORDINARY, ONCE_IN_A_LIFETIME;

        public String key() {
            return "com.voyager.band." + name().toLowerCase();
        }
    }

    private SkyRoll() {
    }

    /**
     * Roll one object for a night.
     *
     * @param fitted the lens the Observatory has ground (its level - 1)
     * @return the object caught, or null if there was nothing this lens could resolve
     */
    public static SkyObject tonightsCatch(final Level level, final SkyObject.LensTier fitted, final long salt) {
        final List<SkyObject> reach = SkyData.reachableBy(fitted);
        if (reach.isEmpty()) {
            return null;
        }
        final SkyEvent event = SkyData.tonight(level);
        final List<SkyObject> pool = new ArrayList<>(reach);
        if (event != null) {
            // the exclusives are not in anybody's reach on an ordinary night; tonight they are
            for (final ResourceLocation id : event.exclusive()) {
                final SkyObject o = SkyData.byId(id);
                if (o != null && !pool.contains(o)) {
                    pool.add(o);
                }
            }
        }
        final double luck = event == null ? 1.0 : Math.max(0.1, event.luck());
        double total = 0.0;
        for (final SkyObject o : pool) {
            total += weight(o, event, luck);
        }
        if (total <= 0.0) {
            return null;
        }
        final long seed = (level.getServer() == null ? 0L : level.getServer().overworld().getSeed())
                * 31L + (level.getDayTime() / 24000L) * 7919L + salt;
        double roll = new Random(seed).nextDouble() * total;
        for (final SkyObject o : pool) {
            roll -= weight(o, event, luck);
            if (roll <= 0.0) {
                return o;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * An object's share of the night. Rarer objects are rarer twice over - their own appearance
     * chance is lower and a better lens is needed - which is what makes a level-5 Observatory feel
     * different rather than just faster.
     */
    private static double weight(final SkyObject o, final SkyEvent event, final double luck) {
        double w = Math.max(0.0001, o.appearanceChance());
        if (event != null && event.unlocks(o.id())) {
            w *= luck * 2.0;               // tonight is the only night; make it count
        } else if (event != null) {
            w *= luck;
        }
        return w;
    }

    /** Which band an object falls in. Derived, never hand-sorted. */
    public static Band bandOf(final SkyObject o, final boolean exclusive) {
        if (o == null) {
            return Band.COMMON;
        }
        if (exclusive) {
            return Band.ONCE_IN_A_LIFETIME;
        }
        final double chance = o.appearanceChance();
        final int tier = o.tier().ordinal();          // BAD 0 .. SCULK 4
        // Two axes, one number: how good a lens it needs, and how shy it is once you have one.
        int score = tier * 2;
        if (chance <= 0.01) {
            score += 3;
        } else if (chance <= 0.03) {
            score += 2;
        } else if (chance <= 0.08) {
            score += 1;
        }
        if (score >= 9) return Band.EXTRAORDINARY;
        if (score >= 7) return Band.REMARKABLE;
        if (score >= 5) return Band.RARE;
        if (score >= 3) return Band.NOTABLE;
        return Band.COMMON;
    }

    /** Is this object one that only exists while tonight's event runs? */
    public static boolean isExclusiveTonight(final Level level, final SkyObject o) {
        final SkyEvent event = SkyData.tonight(level);
        return event != null && o != null && event.unlocks(o.id());
    }
}
