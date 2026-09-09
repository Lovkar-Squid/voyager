package me.lovkar.voyager.sky;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import me.lovkar.voyager.colony.BuildingObservatory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * What this colony has actually photographed.
 *
 * <p>The plates are the colony's memory, and until now nothing remembered them: two researches
 * (Second Exposure and Comparative Astronomy) were data waiting on a record that did not exist.
 * This is that record - one line per object, with how many plates of it the colony has taken, the
 * best night it ever got, and whether the two best have already been combined.</p>
 *
 * <p>It is stored on each Observatory and read across all of them, so a colony with a second
 * Observatory does not start a second catalogue: the entries are merged on the way out. That is
 * deliberate - an addon has no clean place to hang per-colony data without a mixin, and a building
 * already serializes itself.</p>
 *
 * <p>Nothing here looks an object up. An id whose datapack has since been removed stays in the
 * book as an id, which is what a real observatory's records would do too.</p>
 */
public final class SkyCatalogue {

    /**
     * One object's line in the book.
     *
     * @param plates    how many plates of it the colony has developed
     * @param best      the best band it was ever caught in
     * @param firstNight the night it was first caught
     * @param lastNight  the night it was last caught
     * @param combined  Comparative Astronomy has already combined two plates of it
     */
    public record Entry(ResourceLocation id, int plates, SkyRoll.Band best,
                        long firstNight, long lastNight, boolean combined) {

        public Entry merge(final Entry other) {
            if (other == null) {
                return this;
            }
            return new Entry(id, plates + other.plates,
                    best.ordinal() >= other.best.ordinal() ? best : other.best,
                    Math.min(firstNight, other.firstNight), Math.max(lastNight, other.lastNight),
                    combined || other.combined);
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("id", id.toString());
            tag.putInt("plates", plates);
            tag.putString("best", best.name());
            tag.putLong("first", firstNight);
            tag.putLong("last", lastNight);
            tag.putBoolean("combined", combined);
            return tag;
        }

        public static Entry load(final CompoundTag tag) {
            try {
                return new Entry(ResourceLocation.parse(tag.getString("id")),
                        Math.max(0, tag.getInt("plates")),
                        SkyRoll.Band.valueOf(tag.getString("best")),
                        tag.getLong("first"), tag.getLong("last"), tag.getBoolean("combined"));
            } catch (final RuntimeException unreadable) {
                return null;                       // a line we can no longer parse is simply dropped
            }
        }
    }

    private SkyCatalogue() {
    }

    /**
     * Every Observatory this colony has, in no particular order.
     *
     * <p>The book is server-side data - a colony view has no buildings to walk - so a client asking
     * gets an empty list rather than an exception. Everything that writes to the catalogue runs in
     * the AI, which is server-side by definition.</p>
     */
    public static List<BuildingObservatory> observatories(final IColony colony) {
        final List<BuildingObservatory> found = new ArrayList<>();
        if (colony == null) {
            return found;
        }
        try {
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
                if (building instanceof BuildingObservatory observatory) {
                    found.add(observatory);
                }
            }
        } catch (final RuntimeException notOnThisSide) {
            return found;
        }
        return found;
    }

    /** The whole book, merged across every Observatory in the colony. */
    public static Map<ResourceLocation, Entry> of(final IColony colony) {
        final Map<ResourceLocation, Entry> merged = new LinkedHashMap<>();
        for (final BuildingObservatory observatory : observatories(colony)) {
            for (final Entry entry : observatory.catalogue().values()) {
                merged.merge(entry.id(), entry, Entry::merge);
            }
        }
        return merged;
    }

    /** This object's line, or null if the colony has never caught it. */
    public static Entry find(final IColony colony, final ResourceLocation id) {
        if (id == null) {
            return null;                           // a blank plate is not an entry
        }
        Entry found = null;
        for (final BuildingObservatory observatory : observatories(colony)) {
            final Entry entry = observatory.catalogue().get(id);
            if (entry != null) {
                found = found == null ? entry : found.merge(entry);
            }
        }
        return found;
    }

    /** Has the colony got a plate of this already? */
    public static boolean known(final IColony colony, final ResourceLocation id) {
        final Entry entry = find(colony, id);
        return entry != null && entry.plates() > 0;
    }

    /** How many different objects the colony has on record. */
    public static int distinct(final IColony colony) {
        return of(colony).size();
    }

    /** How many plates in total. */
    public static int plates(final IColony colony) {
        int total = 0;
        for (final Entry entry : of(colony).values()) {
            total += entry.plates();
        }
        return total;
    }

    /** Comparative Astronomy has used up this object's two best nights, wherever they are filed. */
    public static void markCombined(final IColony colony, final ResourceLocation id) {
        if (id == null) {
            return;
        }
        for (final BuildingObservatory observatory : observatories(colony)) {
            observatory.markCombined(id);
        }
    }

    /** One band better, or the same if there is no better one. */
    public static SkyRoll.Band brighter(final SkyRoll.Band band) {
        final SkyRoll.Band[] all = SkyRoll.Band.values();
        return band == null ? SkyRoll.Band.COMMON : all[Math.min(all.length - 1, band.ordinal() + 1)];
    }

    // ------------------------------------------------------------------ NBT, for the building

    public static ListTag save(final Map<ResourceLocation, Entry> book) {
        final ListTag list = new ListTag();
        for (final Entry entry : book.values()) {
            list.add(entry.save());
        }
        return list;
    }

    public static Map<ResourceLocation, Entry> load(final ListTag list) {
        final Map<ResourceLocation, Entry> book = new LinkedHashMap<>();
        for (final Tag tag : list) {
            if (tag instanceof CompoundTag compound) {
                final Entry entry = Entry.load(compound);
                if (entry != null) {
                    book.put(entry.id(), entry);
                }
            }
        }
        return book;
    }
}
