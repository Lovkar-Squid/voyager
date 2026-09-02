package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.EquipmentLevelConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.ai.EntityAIWorkVoyager;
import me.lovkar.voyager.fx.Effects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Departure Point. Whether it was built as a Launchpad or an End Gate only changes the
 * look (and the send-off effects); the work is the same: one expedition per launch window.
 */
public class BuildingVoyager extends AbstractBuilding {

    public static final String TAG_DEPARTURE = "departure";
    /** Every block of the rocket carries this tag; they leave with the Voyager and come back with them. */
    public static final String TAG_ROCKET = "rocket";
    /** Heart of the End Gate's ring, where the vortex spins. */
    public static final String TAG_GATE = "gate";
    /** Every pane of the End Gate's portal film. */
    public static final String TAG_PORTAL = "portal";
    /** Vanilla's invisible light source, full strength: what makes the portal glow. */
    private static final BlockState GATE_LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    private static final String TAG_TRIPS = "trips";
    private static final String TAG_PERIOD_DAY = "period_day";
    private static final String TAG_STASH = "rocket_stash";
    private static final int MAX_LEVEL = 5;
    private static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private int periodDay = 0;
    private int trips = 0;
    private long snapTime = 0L;
    /** Transient: the empty rocket's landing is already playing. */
    private boolean returningEmpty = false;
    /** The rocket's blocks while it is away, in the order they were taken. */
    private final List<StashedBlock> stash = new ArrayList<>();

    private record StashedBlock(BlockPos pos, BlockState state) {
    }

    public BuildingVoyager(final @NotNull IColony colony, final BlockPos pos) {
        super(colony, pos);
        keepX.put(stack -> ItemStackUtils.hasEquipmentLevel(stack, ModEquipmentTypes.sword.get(), 0, getMaxEquipmentLevel()), new Tuple<>(1, true));
        keepX.put(stack -> ItemStackUtils.hasEquipmentLevel(stack, ModEquipmentTypes.pickaxe.get(), 0, getMaxEquipmentLevel()), new Tuple<>(1, true));
        for (final EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            keepX.put(stack -> !ItemStackUtils.isEmpty(stack) && stack.getItem() instanceof ArmorItem armor
                    && armor.getEquipmentSlot() == slot, new Tuple<>(1, true));
        }
    }

    /** Both looks live in the same pack; the gate tag (or the blueprint path) tells them apart. */
    public boolean isEndGate() {
        if (getFirstLocationFromTag(TAG_GATE) != null) {
            return true;
        }
        final String path = getBlueprintPath();
        return path != null && path.contains("endgate");
    }

    /** Centre of the ring for an End Gate, the cabin for a Launchpad: where the effects play. */
    public BlockPos getEffectCentre() {
        final BlockPos gate = getFirstLocationFromTag(TAG_GATE);
        return gate != null ? gate : getDeparturePosition();
    }

    // ------------------------------------------------------------------ the gate glows

    /**
     * An invisible light block in front of or behind every portal pane (alternating, so each
     * pane has one right next to it): the film shines at night with no lamp in sight. Light
     * blocks only exist as a creative item, so the builder never asks for them - the building
     * places them itself once it stands and puts them back after every upgrade.
     */
    public void lightGate() {
        if (!isEndGate() || isPendingConstruction()) {
            return;
        }
        final Level level = colony.getWorld();
        if (level == null) {
            return;
        }
        int placed = 0;
        for (final BlockPos at : gateLightPositions()) {
            if (level.getBlockState(at).isAir()) {
                level.setBlock(at, GATE_LIGHT, Block.UPDATE_ALL);
                placed++;
            }
        }
        if (placed > 0) {
            Voyager.LOGGER.info("[Voyager] The gate at {} glows: {} lights lit", getFirstLocationFromTag(TAG_GATE), placed);
        }
    }

    /**
     * The lights are not part of the blueprint, so a builder at work would only mine them again
     * and again: while a work order is open they come down, and return once it is done.
     */
    private void unlightGate() {
        final Level level = colony.getWorld();
        if (level == null) {
            return;
        }
        int removed = 0;
        for (final BlockPos at : gateLightPositions()) {
            if (level.getBlockState(at).is(Blocks.LIGHT)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                removed++;
            }
        }
        if (removed > 0) {
            Voyager.LOGGER.info("[Voyager] Builder at work on the gate at {}: {} lights taken down for now", getPosition(), removed);
        }
    }

    /** One cell per pane, alternating in front of and behind the film along the ring's axis. */
    private List<BlockPos> gateLightPositions() {
        final List<BlockPos> cells = new ArrayList<>();
        final BlockPos gate = getFirstLocationFromTag(TAG_GATE);
        final BlockPos stand = getFirstLocationFromTag(TAG_DEPARTURE);
        if (gate == null || stand == null) {
            return cells;
        }
        // the ring's axis runs from its centre toward the dais, snapped to x or z (the blueprint may be rotated)
        final int dx = stand.getX() - gate.getX();
        final int dz = stand.getZ() - gate.getZ();
        final BlockPos normal = Math.abs(dx) >= Math.abs(dz)
                ? new BlockPos(Integer.signum(dx == 0 ? 1 : dx), 0, 0)
                : new BlockPos(0, 0, Integer.signum(dz));
        for (final BlockPos pane : getLocationsFromTag(TAG_PORTAL)) {
            final boolean front = ((pane.getX() + pane.getY() + pane.getZ()) & 1) == 0;
            cells.add(front ? pane.offset(normal) : pane.subtract(normal));
        }
        return cells;
    }

    // ------------------------------------------------------------------ the rocket leaves with the crew

    public boolean isRocketAway() {
        return !stash.isEmpty();
    }

    /** Takes the rocket off the pad (every block tagged "rocket"), remembering it for the landing. */
    public void hideRocket() {
        if (!stash.isEmpty()) {
            return;
        }
        final Level level = colony.getWorld();
        if (level == null) {
            return;
        }
        final List<BlockPos> rocket = new ArrayList<>(getLocationsFromTag(TAG_ROCKET));
        // top down, so nothing falls or pops while the hull disappears
        rocket.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        for (final BlockPos pos : rocket) {
            final BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            stash.add(new StashedBlock(pos.immutable(), state));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT);
        }
        markDirty();
        Voyager.LOGGER.info("[Voyager] Rocket at {} lifted off: {} blocks stowed", getPosition(), stash.size());
    }

    /** Puts the rocket back exactly as it was. */
    public void revealRocket() {
        if (stash.isEmpty()) {
            return;
        }
        final Level level = colony.getWorld();
        if (level == null) {
            return;
        }
        // bottom up: doors and stairs want their neighbours in place
        final List<StashedBlock> blocks = new ArrayList<>(stash);
        blocks.sort((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY()));
        for (final StashedBlock block : blocks) {
            level.setBlock(block.pos(), block.state(), SILENT);
        }
        Voyager.LOGGER.info("[Voyager] Rocket at {} landed: {} blocks restored", getPosition(), stash.size());
        stash.clear();
        markDirty();
    }

    private boolean crewAway() {
        for (final ICitizenData citizen : getAllAssignedCitizen()) {
            if (citizen.getJob() instanceof JobVoyager job && job.isAway()) {
                return true;
            }
            if (colony.getTravellingManager().isTravelling(citizen)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every colony tick: a Voyager whose travel time is up is called back at once (MineColonies
     * itself only respawns missing citizens every five minutes or at dawn), and a rocket that is
     * away with nobody on an expedition comes back on its own.
     */
    @Override
    public void onColonyTick(final IColony colony) {
        super.onColonyTick(colony);
        for (final ICitizenData citizen : getAllAssignedCitizen()) {
            if (citizen.getJob() instanceof JobVoyager job && job.isAway()
                    && citizen.getEntity().isEmpty() && !colony.getTravellingManager().isTravelling(citizen)) {
                citizen.updateEntityIfNecessary();
                if (citizen.getEntity().isPresent()) {
                    Voyager.LOGGER.info("[Voyager] {}'s travel time is up - back in the world, out of sight", citizen.getName());
                }
            }
        }
        if (isRocketAway() && !crewAway() && !returningEmpty && colony.getWorld() instanceof ServerLevel level) {
            // the crew did not make it (or was dismissed mid-flight): the rocket comes home on its own
            returningEmpty = true;
            Voyager.LOGGER.info("[Voyager] Rocket at {} is coming back without its crew", getPosition());
            Effects.rocketLanding(level, getDeparturePosition(), () -> {
                revealRocket();
                returningEmpty = false;
            });
        }
        // colony ticks come every 25 s: cheap enough to keep the gate lit and shimmering each time
        if (getBuildingLevel() > 0 && isEndGate()) {
            if (isPendingConstruction()) {
                unlightGate();
            } else {
                lightGate();
                final BlockPos gate = getFirstLocationFromTag(TAG_GATE);
                if (gate != null && colony.getWorld() instanceof ServerLevel level) {
                    Effects.ambientGate(level, gate, getLocationsFromTag(TAG_PORTAL),
                            () -> IColonyManager.getInstance().getBuilding(level, getPosition()) == this
                                    && WorldUtil.isBlockLoaded(level, gate) && !isPendingConstruction());
                }
            }
        }
    }

    /** A new level means a new rocket from the blueprint; whatever we stowed is history. */
    @Override
    public void onUpgradeComplete(final @Nullable Blueprint blueprint, final int newLevel) {
        super.onUpgradeComplete(blueprint, newLevel);
        if (!stash.isEmpty()) {
            Voyager.LOGGER.info("[Voyager] Rocket stash dropped after the upgrade of {}", getPosition());
            stash.clear();
        }
        lightGate();
    }

    @Override
    public @NotNull String getSchematicName() {
        return isEndGate() ? "endgate" : "launchpad";
    }

    @Override
    public int getMaxBuildingLevel() {
        return MAX_LEVEL;
    }

    /**
     * Better gear with every level - the End is no place for a stone sword: iron at level 1,
     * diamond at 2, netherite at 3, anything (enchantments included) from level 4.
     */
    @Override
    public int getMaxEquipmentLevel() {
        final int level = getBuildingLevel();
        if (level >= 4) {
            return EquipmentLevelConstants.TOOL_LEVEL_MAXIMUM;
        }
        return Math.max(1, level + 1);
    }

    /**
     * Expected food stock for the menu module, in stacks per menu item: a stack while the racks
     * and the Voyager's backpack together hold fewer rations than one trip needs, nothing once
     * they do (which also cancels the open food requests).
     */
    public static int rationStockWanted(final IBuilding building) {
        if (!(building instanceof BuildingVoyager voyager)) {
            return 1;
        }
        final Set<ItemStorage> menu = voyager.getModule(VoyagerModules.MENU).getMenu();
        if (menu.isEmpty()) {
            return 0;
        }
        final Predicate<ItemStack> onMenu = stack -> !stack.isEmpty() && menu.contains(new ItemStorage(stack));
        int rations = 0;
        for (final ItemStorage item : menu) {
            rations += InventoryUtils.getCountFromBuilding(voyager, item);
        }
        for (final ICitizenData citizen : voyager.getAllAssignedCitizen()) {
            rations += InventoryUtils.getItemCountInItemHandler(citizen.getInventory(), onMenu);
        }
        return rations >= EntityAIWorkVoyager.RATIONS_TO_PACK * Math.max(1, voyager.getAllAssignedCitizen().size()) ? 0 : 1;
    }

    /** Days between launch windows: 3 at level 1-2, 2 at level 3-4, every day at level 5. */
    public int getPeriodDays() {
        final int level = getBuildingLevel();
        return level >= 5 ? 1 : level >= 3 ? 2 : 3;
    }

    /** Voyagers a Departure Point can house: one, one more with Buddy System. */
    public static int crewSize(final IBuilding building) {
        return 1 + (int) VoyagerResearch.strength(building.getColony(), VoyagerResearch.BUDDY_SYSTEM);
    }

    /** One expedition per launch window per Voyager, one more each with Starlight Navigation. */
    public int getMaxTripsPerPeriod() {
        final int perVoyager = 1 + (int) VoyagerResearch.strength(colony, VoyagerResearch.STARLIGHT_NAVIGATION);
        return perVoyager * Math.max(1, getAllAssignedCitizen().size());
    }

    @Override
    public void onWakeUp() {
        super.onWakeUp();
        snapTime = colony.getWorld().getDayTime();
        newDay();
    }

    /** A day has passed: after getPeriodDays() of them a new launch window opens (level 5: every morning). */
    private void newDay() {
        periodDay++;
        if (periodDay >= getPeriodDays()) {
            periodDay = 0;
            trips = 0;
            markDirty();
        }
    }

    public boolean isReadyForTrip() {
        if (snapTime == 0L) {
            snapTime = colony.getWorld().getDayTime();
        }
        if (Math.abs(colony.getWorld().getDayTime() - snapTime) >= 24000L) {
            // a day went by without a wake-up (nobody slept): count it anyway
            snapTime = colony.getWorld().getDayTime();
            newDay();
        }
        return trips < getMaxTripsPerPeriod();
    }

    public void recordTrip() {
        trips++;
    }

    /** The block tagged "departure" in the blueprint, or the hut itself for an untagged build. */
    public BlockPos getDeparturePosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_DEPARTURE);
        return tagged != null ? tagged : getPosition();
    }

    /** Keep the expedition supplies (the trip recipe's inputs) in the hut instead of shipping them off. */
    @Override
    public int buildingRequiresCertainAmountOfItem(final ItemStack stack, final List<ItemStorage> localAlreadyKept,
                                                   final boolean inventory, final JobEntry jobEntry) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (inventory && getFirstModuleOccurance(MinimumStockModule.class).isStocked(stack)) {
            return stack.getCount();
        }
        final IRecipeStorage recipe = getFirstModuleOccurance(CraftingModule.class).getFirstRecipe(ItemStack::isEmpty);
        if (recipe != null) {
            final ItemStorage kept = new ItemStorage(stack);
            final boolean needed = recipe.getInput().contains(kept);
            final int keptCount = localAlreadyKept.stream().filter(kept::equals).mapToInt(ItemStorage::getAmount).sum();
            if (needed && (keptCount < 64 || !inventory)) {
                if (localAlreadyKept.contains(kept)) {
                    kept.setAmount(localAlreadyKept.remove(localAlreadyKept.indexOf(kept)).getAmount());
                }
                localAlreadyKept.add(kept);
                return 0;
            }
        }
        return super.buildingRequiresCertainAmountOfItem(stack, localAlreadyKept, inventory, jobEntry);
    }

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag compound) {
        super.deserializeNBT(provider, compound);
        trips = compound.getInt(TAG_TRIPS);
        periodDay = compound.getInt(TAG_PERIOD_DAY);
        stash.clear();
        final ListTag list = compound.getList(TAG_STASH, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            final BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            final BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("state"));
            stash.add(new StashedBlock(pos, state));
        }
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag compound = super.serializeNBT(provider);
        compound.putInt(TAG_TRIPS, trips);
        compound.putInt(TAG_PERIOD_DAY, periodDay);
        final ListTag list = new ListTag();
        for (final StashedBlock block : stash) {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("x", block.pos().getX());
            entry.putInt("y", block.pos().getY());
            entry.putInt("z", block.pos().getZ());
            entry.put("state", NbtUtils.writeBlockState(block.state()));
            list.add(entry);
        }
        compound.put(TAG_STASH, list);
        return compound;
    }

    /** The "recipes" are expedition plans: supplies in, a loot-table roll of End finds out. */
    public static class CraftingModule extends AbstractCraftingBuildingModule.Custom {
        public CraftingModule(final JobEntry jobEntry) {
            super(jobEntry);
        }
    }
}
