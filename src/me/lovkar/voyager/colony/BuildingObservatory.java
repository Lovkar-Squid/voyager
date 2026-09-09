package me.lovkar.voyager.colony;

import java.util.LinkedHashMap;
import java.util.Map;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.sky.SkyCatalogue;
import me.lovkar.voyager.sky.SkyRoll;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * The Observatory. Five looks in the pack - a Copper Dome, a Stargazer's Keep, a Sand Court, a
 * Skyward Station and an Aperture Array - and the look only changes what the astronomer stands
 * next to, never the work.
 *
 * <p>The building remembers what the colony has looked at: the number of nights worked and the
 * plates waiting to be developed. It deliberately knows nothing about Exposure's classes - the
 * catalogue is read out of datapacks by {@link me.lovkar.voyager.sky.SkyData}, which is the
 * arrangement their author asked for.</p>
 *
 * <p>The Observatory studies the sky on its own terms: {@link SkyStudyModule} is its research,
 * paid for in nights of watching rather than in hours of a timer, and it owes nothing to the
 * University's tree.</p>
 */
public class BuildingObservatory extends AbstractBuilding {

    public static final int MAX_LEVEL = 5;
    /** The blueprint tags the instrument: the pier of a telescope, the plinth of an armillary,
     *  the foot of the gnomon, the mast of the aperture ring. The astronomer works beside it. */
    public static final String TAG_SCOPE = "scope";
    /** The blueprint tags the darkroom's lightroom, where plates are developed. Level 1 has none. */
    public static final String TAG_DARKROOM = "darkroom";

    private static final String NBT_NIGHTS = "nights";
    private static final String NBT_PLATES = "plates";
    private static final String NBT_CATALOGUE = "catalogue";

    /** Nights the astronomer has actually worked here. Research and rarity will read this. */
    private int nights = 0;
    /** Exposed plates waiting for the darkroom. */
    private int plates = 0;
    /** What this Observatory has photographed. Read colony-wide through {@link SkyCatalogue}. */
    private Map<ResourceLocation, SkyCatalogue.Entry> catalogue = new LinkedHashMap<>();

    public BuildingObservatory(final IColony colony, final BlockPos pos) {
        super(colony, pos);
    }

    /** Which of the five looks was built: taken from the blueprint path, like the Departure Point. */
    @Override
    public @NotNull String getSchematicName() {
        final String path = getBlueprintPath();
        if (path != null) {
            for (final String look : new String[] {"keep", "sandcourt", "station", "array"}) {
                if (path.contains(look)) {
                    return look;
                }
            }
        }
        return "observatory";
    }

    @Override
    public int getMaxBuildingLevel() {
        return MAX_LEVEL;
    }


    /**
     * Where the astronomer works. The blueprint tags it; if the tag is missing (an old blueprint,
     * or one somebody drew themselves) the hut block itself will do, so the AI never stalls.
     */
    public BlockPos getDarkroomPosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_DARKROOM);
        return tagged != null ? tagged : getPosition();
    }

    public BlockPos getScopePosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_SCOPE);
        return tagged != null ? tagged : getPosition();
    }

    /**
     * How far the astronomer can see from here.
     *
     * <p>The building's level gets you most of the way; Lens Grinding is what takes a colony past
     * what its walls alone would allow, and it is bought with Exposure: Space's own lenses.</p>
     */
    public int lensTier() {
        final int ground = (int) Math.round(ObservatoryResearch.strength(colony, ObservatoryResearch.LENS_GRINDING));
        return Math.max(0, getBuildingLevel() - 1 + ground);
    }

    /** Two astronomers once the colony has trained an apprentice, and only then. */
    public static int crewSize(final com.minecolonies.api.colony.buildings.IBuilding building) {
        if (building == null || building.getBuildingLevel() < 4) {
            return 1;
        }
        return ObservatoryResearch.has(building.getColony(), ObservatoryResearch.APPRENTICE) ? 2 : 1;
    }

    public int getNights() {
        return nights;
    }

    public int getPlates() {
        return plates;
    }

    /** This building's own lines. Ask {@link SkyCatalogue} for the colony's. */
    public Map<ResourceLocation, SkyCatalogue.Entry> catalogue() {
        return catalogue;
    }

    /**
     * Write a developed plate into the book: one more plate of this object, the best band it has
     * ever been caught in, and the nights it bookends.
     *
     * @return the line as it now stands
     */
    public SkyCatalogue.Entry catalogue(final ResourceLocation object, final SkyRoll.Band band, final long night) {
        if (object == null) {
            return null;                       // a blank plate is a result, not a discovery
        }
        final SkyCatalogue.Entry was = catalogue.get(object);
        final SkyCatalogue.Entry now = was == null
                ? new SkyCatalogue.Entry(object, 1, band, night, night, false)
                : new SkyCatalogue.Entry(object, was.plates() + 1,
                        was.best().ordinal() >= band.ordinal() ? was.best() : band,
                        Math.min(was.firstNight(), night), Math.max(was.lastNight(), night), was.combined());
        catalogue.put(object, now);
        markDirty();
        return now;
    }

    /** Comparative Astronomy has combined this object's two best plates; it may not do so twice. */
    public void markCombined(final ResourceLocation object) {
        final SkyCatalogue.Entry entry = catalogue.get(object);
        if (entry != null && !entry.combined()) {
            catalogue.put(object, new SkyCatalogue.Entry(object, entry.plates(), entry.best(),
                    entry.firstNight(), entry.lastNight(), true));
            markDirty();
        }
    }

    /** One night's work is done: the count goes up and a plate joins the rack. */
    public void nightWorked() {
        nights++;
        plates++;
        markDirty();
    }

    /** The darkroom took a plate. */
    public boolean takePlate() {
        if (plates <= 0) {
            return false;
        }
        plates--;
        markDirty();
        return true;
    }

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        nights = tag.getInt(NBT_NIGHTS);
        plates = tag.getInt(NBT_PLATES);
        catalogue = SkyCatalogue.load(tag.getList(NBT_CATALOGUE, Tag.TAG_COMPOUND));
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag tag = super.serializeNBT(provider);
        tag.putInt(NBT_NIGHTS, nights);
        tag.putInt(NBT_PLATES, plates);
        tag.put(NBT_CATALOGUE, SkyCatalogue.save(catalogue));
        return tag;
    }

    @Override
    public void onUpgradeComplete(final @org.jetbrains.annotations.Nullable com.ldtteam.structurize.blueprints.v1.Blueprint blueprint,
                                  final int newLevel) {
        super.onUpgradeComplete(blueprint, newLevel);
        Voyager.LOGGER.info("[Observatory] {} is now level {} ({} look), lens tier {}",
                getPosition(), newLevel, getSchematicName(), lensTier());
    }
}
