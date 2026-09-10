package me.lovkar.voyager.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.requestsystem.locations.StaticLocation;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.ai.EntityAIWorkAstronomer;
import me.lovkar.voyager.sky.SkyCatalogue;
import me.lovkar.voyager.sky.SkyRoll;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    /** A blueprint may name its own lookout; otherwise the building finds one in the landscape. */
    public static final String TAG_LOOKOUT = "lookout";

    /** Setting: keep the watch from the lookout when there is one, instead of the instrument. */
    public static final ISettingKey<BoolSetting> LOOKOUT =
            new SettingKey<>(BoolSetting.class, ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "lookout"));
    /** Setting: how many guards walk out with the astronomer and stand watch at the lookout. */
    public static final ISettingKey<StringSetting> ESCORT =
            new SettingKey<>(StringSetting.class, ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "escort"));
    public static final String ESCORT_OFF = "com.voyager.setting.escort.off";
    public static final String ESCORT_ONE = "com.voyager.setting.escort.one";
    public static final String ESCORT_TWO = "com.voyager.setting.escort.two";

    private static final String NBT_NIGHTS = "nights";
    private static final String NBT_PLATES = "plates";
    private static final String NBT_CATALOGUE = "catalogue";
    private static final String NBT_LOOKOUT = "lookout";
    private static final String NBT_LOOKOUT_DAY = "lookout_day";
    private static final String NBT_ESCORT = "escort_towers";

    /** How far from the hut a lookout may be. Far enough for a hill, near enough to walk home by dawn. */
    private static final int LOOKOUT_RADIUS = 40;
    private static final int LOOKOUT_STEP = 3;
    /** Days between looking for a better lookout - the land changes, slowly. */
    private static final int LOOKOUT_RECHECK_DAYS = 3;
    /** A lookout has to be at least this much higher than the instrument to be worth the walk. */
    private static final int LOOKOUT_MIN_RISE = 3;

    /** Nights the astronomer has actually worked here. Research and rarity will read this. */
    private int nights = 0;
    /** Exposed plates waiting for the darkroom. */
    private int plates = 0;
    /** What this Observatory has photographed. Read colony-wide through {@link SkyCatalogue}. */
    private Map<ResourceLocation, SkyCatalogue.Entry> catalogue = new LinkedHashMap<>();
    /** The best spot with a clear sky within reach, or null if the land around is flat. */
    private @Nullable BlockPos lookout;
    private int lookoutDay = Integer.MIN_VALUE;
    /** Guard towers whose guards are rallied to the lookout tonight; released at dawn. */
    private final List<BlockPos> escortTowers = new ArrayList<>();

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

    // ------------------------------------------------------------------ the lookout

    /** The setting: does the colony want the astronomer out on the hill at all? */
    public boolean lookoutWanted() {
        try {
            final BoolSetting setting = getSetting(LOOKOUT);
            return setting == null || setting.getValue();
        } catch (final Throwable noSettings) {
            return true;
        }
    }

    /** How many guards the colony sends along: 0, 1 or 2. */
    public int escortWanted() {
        try {
            final StringSetting setting = getSetting(ESCORT);
            if (setting == null) {
                return 1;
            }
            return switch (setting.getValue()) {
                case ESCORT_OFF -> 0;
                case ESCORT_TWO -> 2;
                default -> 1;
            };
        } catch (final Throwable noSettings) {
            return 1;
        }
    }

    /**
     * Where the astronomer keeps the watch when the sky is best seen from outside.
     *
     * <p>A blueprint may tag one. Otherwise the building looks for it: within forty blocks, inside
     * the colony, the highest open square that stands clear of its neighbours and of every other
     * building, at least a few blocks above the instrument - a hilltop, a ridge, a cliff edge. Flat
     * land has none, and then the astronomer stays at the instrument, which is the right answer.
     * Looked for again every few days, because the land around a colony changes.</p>
     */
    public @Nullable BlockPos getLookout(final ServerLevel level) {
        final BlockPos tagged = getFirstLocationFromTag(TAG_LOOKOUT);
        if (tagged != null) {
            return tagged;
        }
        final int today = colony.getDay();
        if (lookoutDay != Integer.MIN_VALUE && today - lookoutDay < LOOKOUT_RECHECK_DAYS) {
            return lookout;
        }
        lookoutDay = today;
        lookout = findLookout(level);
        markDirty();
        Voyager.LOGGER.info("[Observatory] {} lookout: {}", getPosition(),
                lookout == null ? "none - the land around is flat" : lookout);
        return lookout;
    }

    private @Nullable BlockPos findLookout(final ServerLevel level) {
        final BlockPos scope = getScopePosition();
        final int floor = scope.getY() + LOOKOUT_MIN_RISE;
        final List<IBuilding> buildings = new ArrayList<>(colony.getServerBuildingManager().getBuildings().values());
        BlockPos best = null;
        double bestScore = 0.0;
        for (int dx = -LOOKOUT_RADIUS; dx <= LOOKOUT_RADIUS; dx += LOOKOUT_STEP) {
            for (int dz = -LOOKOUT_RADIUS; dz <= LOOKOUT_RADIUS; dz += LOOKOUT_STEP) {
                final int x = scope.getX() + dx;
                final int z = scope.getZ() + dz;
                if (dx * dx + dz * dz > LOOKOUT_RADIUS * LOOKOUT_RADIUS) {
                    continue;
                }
                final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y < floor) {
                    continue;
                }
                final BlockPos feet = new BlockPos(x, y, z);
                if (!colony.isCoordInColony(level, feet) || !standable(level, feet) || !level.canSeeSky(feet)) {
                    continue;
                }
                boolean inside = false;
                for (final IBuilding other : buildings) {
                    if (other.isInBuilding(feet)) {
                        inside = true;             // somebody's roof is not a lookout
                        break;
                    }
                }
                if (inside) {
                    continue;
                }
                // Open to the sky all round: how many of the eight neighbours a few blocks out are
                // no higher than this square. A ridge scores eight, a pit scores nothing.
                int open = 0;
                for (int i = 0; i < 8; i++) {
                    final double a = i * Math.PI / 4.0;
                    final int nx = x + (int) Math.round(Math.cos(a) * 5.0);
                    final int nz = z + (int) Math.round(Math.sin(a) * 5.0);
                    if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) <= y + 1) {
                        open++;
                    }
                }
                if (open < 5) {
                    continue;
                }
                final double rise = y - scope.getY();
                final double walk = Math.sqrt(dx * dx + dz * dz);
                final double score = rise * 2.0 + open * 1.5 - walk * 0.15;
                if (score > bestScore) {
                    bestScore = score;
                    best = feet;
                }
            }
        }
        return best;
    }

    private static boolean standable(final ServerLevel level, final BlockPos feet) {
        final BlockState floor = level.getBlockState(feet.below());
        if (!floor.isSolid() || !floor.getFluidState().isEmpty()) {
            return false;
        }
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    // ------------------------------------------------------------------ the escort

    /**
     * Send for the escort: the nearest guard towers with guards in them are rallied to the lookout,
     * the way the rally banner does it - the guards walk there, glow, and fight anything that comes
     * within thirty blocks - until {@link #releaseEscort()}.
     *
     * @return how many towers answered
     */
    public int callEscort(final ServerLevel level, final BlockPos lookout) {
        final int wanted = escortWanted();
        if (wanted <= 0 || lookout == null) {
            return 0;
        }
        if (!escortTowers.isEmpty()) {
            return escortTowers.size();            // already out
        }
        final List<IGuardBuilding> towers = new ArrayList<>();
        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values()) {
            if (other instanceof IGuardBuilding guard && other.getBuildingLevel() >= 1
                    && !other.getAllAssignedCitizen().isEmpty() && guard.getRallyLocation() == null) {
                towers.add(guard);
            }
        }
        towers.sort(Comparator.comparingDouble(t -> t.getPosition().distSqr(lookout)));
        for (final IGuardBuilding tower : towers) {
            if (escortTowers.size() >= wanted) {
                break;
            }
            tower.setRallyLocation(new StaticLocation(lookout, level.dimension()));
            escortTowers.add(tower.getPosition());
        }
        if (!escortTowers.isEmpty()) {
            markDirty();
            Voyager.LOGGER.info("[Observatory] {} tower(s) sent a guard to the lookout at {}", escortTowers.size(), lookout);
        }
        return escortTowers.size();
    }

    /** The watch is over: the guards go back to their own duties. */
    public void releaseEscort() {
        if (escortTowers.isEmpty()) {
            return;
        }
        for (final BlockPos pos : escortTowers) {
            final IBuilding tower = colony.getServerBuildingManager().getBuilding(pos);
            if (tower instanceof IGuardBuilding guard) {
                guard.setRallyLocation(null);
            }
        }
        Voyager.LOGGER.info("[Observatory] the escort of {} tower(s) is released", escortTowers.size());
        escortTowers.clear();
        markDirty();
    }

    public boolean escortOut() {
        return !escortTowers.isEmpty();
    }

    /** Safety: no guard stands on a hill past dawn because something interrupted the astronomer. */
    @Override
    public void onColonyTick(final IColony colony) {
        super.onColonyTick(colony);
        if (!escortTowers.isEmpty() && colony.getWorld() != null
                && !EntityAIWorkAstronomer.isNightAt(colony.getWorld())) {
            releaseEscort();
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        nights = tag.getInt(NBT_NIGHTS);
        plates = tag.getInt(NBT_PLATES);
        catalogue = SkyCatalogue.load(tag.getList(NBT_CATALOGUE, Tag.TAG_COMPOUND));
        lookout = NbtUtils.readBlockPos(tag, NBT_LOOKOUT).orElse(null);
        lookoutDay = tag.contains(NBT_LOOKOUT_DAY) ? tag.getInt(NBT_LOOKOUT_DAY) : Integer.MIN_VALUE;
        escortTowers.clear();
        for (final Tag entry : tag.getList(NBT_ESCORT, Tag.TAG_COMPOUND)) {
            NbtUtils.readBlockPos((CompoundTag) entry, "pos").ifPresent(escortTowers::add);
        }
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag tag = super.serializeNBT(provider);
        tag.putInt(NBT_NIGHTS, nights);
        tag.putInt(NBT_PLATES, plates);
        tag.put(NBT_CATALOGUE, SkyCatalogue.save(catalogue));
        if (lookout != null) {
            tag.put(NBT_LOOKOUT, NbtUtils.writeBlockPos(lookout));
        }
        if (lookoutDay != Integer.MIN_VALUE) {
            tag.putInt(NBT_LOOKOUT_DAY, lookoutDay);
        }
        final ListTag towers = new ListTag();
        for (final BlockPos pos : escortTowers) {
            final CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(pos));
            towers.add(entry);
        }
        tag.put(NBT_ESCORT, towers);
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
