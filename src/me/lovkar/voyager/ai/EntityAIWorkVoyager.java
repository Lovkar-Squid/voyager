package me.lovkar.voyager.ai;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.compatibility.tinkers.TinkersToolHelper;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.workers.util.GuardGear;
import com.minecolonies.api.entity.ai.workers.util.GuardGearBuilder;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.items.component.AdventureData;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.util.DamageSourceKeys;
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.constant.GuardConstants;
import com.minecolonies.core.colony.buildings.modules.ExpeditionLogModule;
import com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.items.ItemAdventureToken;
import com.minecolonies.core.util.TeleportHelper;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.VoyagerConfig;
import me.lovkar.voyager.colony.BuildingVoyager;
import me.lovkar.voyager.colony.JobVoyager;
import me.lovkar.voyager.colony.VoyagerModules;
import me.lovkar.voyager.colony.ObservatoryResearch;
import me.lovkar.voyager.colony.VoyagerResearch;
import me.lovkar.voyager.fx.Effects;
import me.lovkar.voyager.trophy.TripStory;
import me.lovkar.voyager.trophy.Trophies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The Voyager's day: gear up, pack rations and supplies, walk to the departure point, leave
 * with a proper send-off, live through whatever the expedition roll turned up (fights, digging,
 * finds), come back with a landing / a flash at the gate and carry the haul into the hut.
 *
 * The three away-states borrow the Nether Miner's state ids (NETHER_LEAVE/AWAY/RETURN) because
 * the state enum belongs to MineColonies; the behaviour is our own. Everything the Voyager does
 * is logged with a [Voyager] prefix so an expedition can be read back from the log.
 */
public class EntityAIWorkVoyager extends AbstractEntityAICrafting<JobVoyager, BuildingVoyager> {

    private static final int TICK_DELAY = 40;
    /** Damage taken per fight is reduced by this much per point of the secondary skill. */
    private static final float SECONDARY_DAMAGE_REDUCTION = 0.005f;
    /** launch() calls (one per TICK_DELAY) the Voyager may spend walking to the departure point: about a minute. */
    private static final int WALK_ATTEMPTS = 30;
    /** When the last step is blocked, this close still counts as "on board". */
    private static final double CLOSE_ENOUGH = 12.0;
    private static final String STAT_TRIPS = BuildingVoyager.STAT_TRIPS_COMPLETED;
    private static final String STAT_DEATHS = "voyager_deaths";
    /** Permanent death off: a Voyager driven back from a fight they would not have survived. */
    private static final String STAT_RETREATS = "voyager_retreats";
    private static final String STAT_FOUND = "items_discovered";
    /** Interaction shown on the citizen when the departure point cannot be reached. */
    public static final String CHAT_UNREACHABLE = "com.voyager.chat.unreachable";
    /** Dragon Hunt: what the dragon hits like (the odds of meeting her are in the server config). */
    private static final float DRAGON_DAMAGE = 12.0f;
    private static final int DRAGON_XP = 250;
    /** Between fights the Voyager eats and patches up to this share of their health, at most this many rations at a time. */
    private static final float RECOVER_TO = 0.8f;
    private static final int RATIONS_PER_REST = 3;
    private static final float RATION_HEAL = 5.0f;

    private static final ResourceKey<DamageType> END_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "the_end"));

    private final Map<EquipmentSlot, ItemStack> virtualEquipment = new HashMap<>();
    private final List<List<GuardGear>> gearNeeded = new ArrayList<>();
    private final List<ItemStack> rations;
    private int walkAttempts = 0;
    private String lastNote = "";
    /** Transient: the entity a driven-back Voyager's wound was last checked on (a respawn is a new entity). */
    private int woundSeenOn = -1;

    public EntityAIWorkVoyager(final @NotNull JobVoyager job) {
        super(job);
        super.registerTargets(
                new AITarget<IAIState>(AIWorkerState.NETHER_LEAVE, this::launch, TICK_DELAY),
                new AITarget<IAIState>(AIWorkerState.NETHER_AWAY, this::explore, TICK_DELAY),
                new AITarget<IAIState>(AIWorkerState.NETHER_RETURN, this::comeHome, TICK_DELAY));
        worker.setCanPickUpLoot(true);
        rations = IColonyManager.getInstance().getCompatibilityManager().getEdibles(building.getBuildingLevel() - 1)
                .stream().map(ItemStorage::getItemStack).collect(Collectors.toList());
        gearNeeded.add(GuardGearBuilder.buildGearForLevel(3, Integer.MAX_VALUE, GuardConstants.LEATHER_BUILDING_LEVEL_RANGE, GuardConstants.DIA_BUILDING_LEVEL_RANGE));
        gearNeeded.add(GuardGearBuilder.buildGearForLevel(2, 4, GuardConstants.LEATHER_BUILDING_LEVEL_RANGE, GuardConstants.DIA_BUILDING_LEVEL_RANGE));
        gearNeeded.add(GuardGearBuilder.buildGearForLevel(0, 3, GuardConstants.LEATHER_BUILDING_LEVEL_RANGE, GuardConstants.IRON_BUILDING_LEVEL_RANGE));
        gearNeeded.add(GuardGearBuilder.buildGearForLevel(0, 2, GuardConstants.LEATHER_BUILDING_LEVEL_RANGE, GuardConstants.CHAIN_BUILDING_LEVEL_RANGE));
        gearNeeded.add(GuardGearBuilder.buildGearForLevel(0, 1, GuardConstants.LEATHER_BUILDING_LEVEL_RANGE, GuardConstants.GOLD_BUILDING_LEVEL_RANGE));
    }

    @Override
    public Class<BuildingVoyager> getExpectedBuildingClass() {
        return BuildingVoyager.class;
    }

    /**
     * The crafting AI only leaves IDLE while there is work: an expedition in progress counts
     * (the Voyager comes back, lives through the finds and lands - all after the trip was
     * recorded and the launch window closed), not just the next launch window.
     */
    @Override
    public boolean hasWorkToDo() {
        return super.hasWorkToDo() || job.isAway() || job.isCountingDown() || colonyTravelling() || building.isReadyForTrip();
    }

    @Override
    public IAIState getStateAfterPickUp() {
        return AIWorkerState.START_WORKING;
    }

    /** Nobody interrupts an expedition, and nobody walks off in the middle of a send-off. */
    @Override
    public boolean canBeInterrupted() {
        return !worker.isInvisible() && !job.isCountingDown();
    }

    @Override
    protected void updateRenderMetaData() {
        final IAIState state = getState();
        final boolean busy = state == AIWorkerState.CRAFT || state == AIWorkerState.NETHER_LEAVE || state == AIWorkerState.NETHER_RETURN;
        final StringBuilder render = new StringBuilder(busy ? "working" : "");
        for (int slot = 0; slot < worker.getInventoryCitizen().getSlots(); slot++) {
            final ItemStack stack = worker.getInventoryCitizen().getStackInSlot(slot);
            if (stack.canPerformAction(ItemAbilities.PICKAXE_DIG) && render.indexOf("pickaxe") == -1) {
                render.append("pickaxe");
            }
        }
        worker.setRenderMetadata(render.toString());
    }

    // ------------------------------------------------------------------ logging helpers

    private String name() {
        return worker.getCitizenData().getName();
    }

    private String lookName() {
        return building.isEndGate() ? "End Gate" : "Launchpad";
    }

    /** Logs a status line once (repeating it every AI tick would drown the log) and publishes it on the job. */
    private void note(final JobVoyager.Status status, final String message) {
        job.setStatus(status, message);
        if (!message.equals(lastNote)) {
            Voyager.LOGGER.info("[Voyager] {}: {}", name(), message);
            lastNote = message;
        }
    }

    private static String describe(final List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return "nothing";
        }
        final StringBuilder sb = new StringBuilder();
        for (final ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            if (stack.getItem() instanceof ItemAdventureToken) {
                final AdventureData data = AdventureData.readFromItemStack(stack);
                sb.append("fight vs ").append(data == null ? "?" : EntityType.getKey(data.entityType()).getPath());
            } else {
                sb.append(stack.getCount()).append("x ").append(stack.getHoverName().getString());
            }
        }
        return sb.length() == 0 ? "nothing" : sb.toString();
    }

    // ------------------------------------------------------------------ deciding

    @Override
    protected IAIState decide() {
        if (colonyTravelling() || job.isAway()) {
            job.setStatus(JobVoyager.Status.AWAY, "out in the End");
            return AIWorkerState.NETHER_AWAY;
        }
        final Optional<BlockPos> target = worker.getCitizenData().getColony().getTravellingManager().getTravellingTargetFor(worker.getCitizenData());
        if (target.isPresent()) {
            worker.getCitizenData().setNextRespawnPosition(EntityUtils.getSpawnPoint(job.getColony().getWorld(), target.get()));
            worker.getCitizenData().updateEntityIfNecessary();
        }
        job.setAway(false);

        final IAIState crafterState = super.decide();
        if (crafterState != AIWorkerState.IDLE && crafterState != AIWorkerState.START_WORKING) {
            return crafterState;
        }

        checkAndRequestArmor();
        final IAIState foodState = checkAndRequestRations();
        if (foodState != getState()) {
            note(JobVoyager.Status.PACKING, "packing rations from the hut");
            return foodState;
        }

        // supplies for the next expedition are requested ahead of time, in the amounts the server's
        // supply multiplier asks for (at 1.0 that is the plan itself)
        final IRecipeStorage plan = craftingModule().getPlan(VoyagerConfig.supplyMultiplier());
        boolean suppliesAvailable = true;
        if (plan != null) {
            for (final ItemStorage item : plan.getInput()) {
                if (!checkIfRequestForItemExistOrCreateAsync(new ItemStack((ItemLike) item.getItem(), 1), item.getAmount(), item.getAmount())) {
                    suppliesAvailable = false;
                }
            }
        }
        if (!suppliesAvailable) {
            note(JobVoyager.Status.WAITING_SUPPLIES, "waiting for expedition supplies (see the hut's requests)");
            setDelay(60);
            return AIWorkerState.IDLE;
        }

        final boolean missingPick = checkForToolOrWeapon(ModEquipmentTypes.pickaxe.get());
        final boolean missingSword = checkForToolOrWeapon(ModEquipmentTypes.sword.get());
        if (missingPick || missingSword) {
            note(JobVoyager.Status.WAITING_TOOLS, "waiting for a " + (missingPick ? "pickaxe" : "sword") + " (up to tool level " + building.getMaxEquipmentLevel() + ")");
            worker.getCitizenData().setJobStatus(JobStatus.STUCK);
            setDelay(60);
            return AIWorkerState.IDLE;
        }

        if (currentRecipeStorage == null) {
            currentRecipeStorage = craftingModule().getFirstFulfillablePlan(VoyagerConfig.supplyMultiplier());
            if (currentRecipeStorage == null) {
                note(JobVoyager.Status.WAITING_PLAN, "no expedition plan can be fulfilled yet (supplies not in the hut?)");
                if (building.isReadyForTrip()) {
                    worker.getCitizenData().setJobStatus(JobStatus.STUCK);
                }
            }
            return getState();
        }

        if (!building.isReadyForTrip()) {
            note(JobVoyager.Status.WAITING_WINDOW, "launch window closed - waiting for the next one (every " + building.getPeriodDays() + " day(s))");
            worker.getCitizenData().setJobStatus(JobStatus.IDLE);
            setDelay(120);
            return AIWorkerState.IDLE;
        }
        if (rocketOut()) {
            note(JobVoyager.Status.WAITING_ROCKET, "the rocket is out with the other crew - waiting for it to come back");
            worker.getCitizenData().setJobStatus(JobStatus.IDLE);
            setDelay(120);
            return AIWorkerState.IDLE;
        }

        if (walkTo != null || !walkToBuilding()) {
            return getState();
        }
        if (!worker.getInventoryCitizen().hasSpace()) {
            return AIWorkerState.INVENTORY_FULL;
        }

        final IAIState check = checkForItems(currentRecipeStorage);
        if (check == AIWorkerState.GET_RECIPE) {
            currentRecipeStorage = null;
            worker.getCitizenData().setJobStatus(JobStatus.STUCK);
            setDelay(60);
            return AIWorkerState.IDLE;
        }
        if (check != AIWorkerState.CRAFT) {
            return check;
        }
        note(JobVoyager.Status.BOARDING, "all set - heading for the " + lookName());
        return AIWorkerState.NETHER_LEAVE;
    }

    /** A Launchpad has one rocket; while it is away (or landing) nobody else can board. */
    private boolean rocketOut() {
        return !building.isEndGate() && building.isRocketAway();
    }

    private boolean colonyTravelling() {
        return worker.getCitizenData().getColony().getTravellingManager().isTravelling(worker.getCitizenData());
    }

    private BuildingVoyager.CraftingModule craftingModule() {
        return building.getFirstModuleOccurance(BuildingVoyager.CraftingModule.class);
    }

    private ExpeditionLog log() {
        return building.getFirstModuleOccurance(ExpeditionLogModule.class).getLog();
    }

    // ------------------------------------------------------------------ leaving

    /**
     * Walk to the departure point (the rocket cabin / the gate dais), consume the supplies, roll
     * the expedition and go: a Launchpad crew is already out of sight inside the hull, so they
     * travel at once and the rocket follows at lift-off; at an End Gate the vortex builds up
     * around the visible Voyager and the flash takes them.
     */
    private IAIState launch() {
        if (job.isCountingDown()) {
            return getState();
        }
        if (job.isAway()) {
            // the show already took them (or a reload interrupted it): nothing left to do here
            return AIWorkerState.NETHER_AWAY;
        }
        if (!worker.getInventoryCitizen().hasSpace()) {
            return AIWorkerState.INVENTORY_FULL;
        }
        if (currentRecipeStorage == null) {
            worker.getCitizenData().setJobStatus(JobStatus.STUCK);
            return AIWorkerState.IDLE;
        }
        if (rocketOut()) {
            // one rocket per pad: the other Voyager has it
            return AIWorkerState.IDLE;
        }

        final BlockPos departure = building.getDeparturePosition();
        if (!walkToWorkPos(departure)) {
            walkAttempts++;
            if (walkAttempts < WALK_ATTEMPTS) {
                return getState();
            }
            walkAttempts = 0;
            final double dist = Math.sqrt(worker.blockPosition().distSqr(departure));
            if (dist > CLOSE_ENOUGH) {
                Voyager.LOGGER.warn("[Voyager] {} cannot reach the departure point {} of the {} (still {} blocks away at {}) - is the way blocked?",
                        name(), departure, lookName(), (int) dist, worker.blockPosition());
                job.setDepartureBlocked(true);
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatableEscape(CHAT_UNREACHABLE), ChatPriority.IMPORTANT));
                worker.getCitizenData().setJobStatus(JobStatus.STUCK);
                setDelay(200);
                return AIWorkerState.IDLE;
            }
            Voyager.LOGGER.warn("[Voyager] {} could not step onto the departure point {} but is {} blocks away - departing anyway",
                    name(), departure, (int) dist);
        }
        walkAttempts = 0;
        job.setDepartureBlocked(false);
        if (!(world instanceof ServerLevel level)) {
            return getState();
        }

        final ExpeditionLog log = log();
        log.reset();
        log.setStatus(ExpeditionLog.Status.STARTING);
        log.setCitizen(worker);
        building.recordTrip();
        job.startTrip(building.nextExpeditionNumber());
        job.setAway(true);
        job.setDrivenBack(false);
        log.setStatus(ExpeditionLog.Status.IN_PROGRESS);
        logAllEquipment(log, false);

        List<ItemStack> finds = currentRecipeStorage.fullfillRecipeAndCopy(getLootContext(),
                List.<IItemHandler>of(worker.getItemHandlerCitizen()), false);
        if (finds != null) {
            finds = new ArrayList<>(finds);
            Collections.shuffle(finds, worker.getCitizenData().getRandom());
            if (dragonSighted()) {
                // Dragon Hunt: the last thing on the itinerary is the dragon herself
                final ItemStack token = new ItemStack(ModItems.adventureToken);
                new AdventureData(EntityType.ENDER_DRAGON, DRAGON_DAMAGE, DRAGON_XP).writeToItemStack(token);
                finds.add(token);
                Voyager.LOGGER.info("[Voyager] {} has the dragon on the itinerary this time", name());
            }
            deepField(finds);
            job.addFinds(finds);
        }
        final int findCount = Math.max(1, job.getFinds().size());
        // Rapid Refit is bought at the University, Star Charts at the Observatory, and a colony
        // that has both gets both - a crossing is shorter for a better ship and for a better map.
        // The server config's trip length multiplier scales the whole crossing, floor included.
        final double refit = Math.min(0.8,
                VoyagerResearch.strength(job.getColony(), VoyagerResearch.RAPID_REFIT)
                        + ObservatoryResearch.strength(job.getColony(), ObservatoryResearch.STAR_CHARTS));
        final int ticksAway = ExpeditionRules.ticksAway(findCount, refit, VoyagerConfig.tripLengthMultiplier());
        worker.getCitizenData().setJobStatus(JobStatus.WORKING);
        lastNote = "";

        // where the (invisible) Voyager reappears when the travel time is up: a safe spot by the
        // hut - the cabin floor is gone with the rocket, and the trench under it is magma
        final BlockPos waiting = waitingSpot();
        Voyager.LOGGER.info("[Voyager] {} departs from the {} at {} on expedition {}: expedition roll {} ({} finds, about {} s away)",
                name(), lookName(), departure, job.getExpedition(), describe(finds), findCount, ticksAway / 20);
        comms("com.voyager.comms.departed", name(), findCount);

        if (building.isEndGate()) {
            job.setCountingDown(true);
            Effects.gateDeparture(level, building.getEffectCentre(), departure, () -> {
                job.setCountingDown(false);
                if (worker.isRemoved() || !worker.isAlive()) {
                    return;
                }
                Voyager.LOGGER.info("[Voyager] {} vanished in the gate's flash", name());
                travel(waiting, ticksAway);
            });
            return getState();
        }
        travel(waiting, ticksAway);
        Effects.rocketLaunch(level, departure, () -> {
            building.hideRocket();
            Voyager.LOGGER.info("[Voyager] lift-off from {}", departure);
        });
        return AIWorkerState.NETHER_AWAY;
    }

    private void travel(final BlockPos target, final int ticks) {
        worker.getCitizenData().getColony().getTravellingManager().startTravellingTo(worker.getCitizenData(), target, ticks);
        worker.remove(Entity.RemovalReason.DISCARDED);
    }

    /**
     * Deep Field: the Observatory spent its nights working out where to point, and the expedition
     * comes home with one thing more than the roll gave it. A second copy of something already on
     * the itinerary, never a second fight - the astronomer found a place, not a monster.
     */
    private void deepField(final List<ItemStack> finds) {
        if (!ObservatoryResearch.has(job.getColony(), ObservatoryResearch.DEEP_FIELD)) {
            return;
        }
        final List<ItemStack> quiet = new ArrayList<>();
        for (final ItemStack find : finds) {
            if (!(find.getItem() instanceof ItemAdventureToken) && !find.isEmpty()) {
                quiet.add(find);
            }
        }
        if (quiet.isEmpty()) {
            return;
        }
        final ItemStack extra = quiet.get(worker.getRandom().nextInt(quiet.size())).copy();
        finds.add(extra);
        Voyager.LOGGER.info("[Voyager] deep field: the charts add {}x {} to {}'s itinerary",
                extra.getCount(), extra.getHoverName().getString(), name());
    }

    /** Dragon Hunt researched, a level 5 Departure Point, and the server config's roll (one in ten by default). */
    private boolean dragonSighted() {
        return building.getBuildingLevel() >= 5
                && VoyagerResearch.has(job.getColony(), VoyagerResearch.DRAGON_HUNT)
                && worker.getRandom().nextDouble() < VoyagerConfig.dragonChance();
    }

    /** Long-range Comms: a line in the colony chat while the Voyager is out of sight. */
    private void comms(final String key, final Object... args) {
        if (VoyagerResearch.has(job.getColony(), VoyagerResearch.LONG_RANGE_COMMS)) {
            MessageUtils.format(key, args).sendTo(job.getColony()).forAllPlayers();
        }
    }

    private BlockPos waitingSpot() {
        final BlockPos spot = EntityUtils.getSpawnPoint(world, building.getPosition());
        return spot != null ? spot : building.getPosition().above();
    }

    // ------------------------------------------------------------------ away

    /**
     * Lives through the expedition roll, one find per visit: adventure tokens are fights
     * (armour and sword on, hits traded until one side drops), block items are dug up with the
     * best tool the Voyager carries, everything else is simply picked up. Then the haul goes
     * into the backpack and the Voyager heads home.
     */
    private IAIState explore() {
        if (!worker.isInvisible()) {
            // out in the End nobody sees them (a reload can bring them back visible)
            worker.setInvisible(true);
        }
        keepWound();
        job.setStatus(JobVoyager.Status.AWAY, "out in the End");
        final ExpeditionLog log = log();
        if (!job.getFinds().isEmpty()) {
            Voyager.LOGGER.info("[Voyager] {} is out in the End: {} finds to live through", name(), job.getFinds().size());
            for (final ItemStack find : job.getFinds()) {
                if (find.getItem() instanceof ItemAdventureToken) {
                    recover();
                    final IAIState after = fight(find, log);
                    if (after != null) {
                        return after;
                    }
                    continue;
                }
                if (find.isEmpty()) {
                    continue;
                }
                int delay;
                if (find.getItem() instanceof BlockItem blockItem) {
                    delay = dig(blockItem.getBlock(), find.getCount(), log);
                } else {
                    job.addHaul(Collections.singletonList(find));
                    log.addLoot(Collections.singletonList(find));
                    Voyager.LOGGER.info("[Voyager] {} picked up {}x {}", name(), find.getCount(), find.getHoverName().getString());
                    delay = TICK_DELAY * find.getCount();
                }
                setDelay(delay);
            }
            job.getFinds().clear();
            return getState();
        }

        // every find lived through and still standing: the trip is a success - roll its trophies, once
        List<ItemStack> trophies = List.of();
        if (!job.isTrophiesRolled() && !job.isDrivenBack() && !worker.isDeadOrDying()) {
            job.setTrophiesRolled(true);
            trophies = rollTrophies(log);
        }

        if (!job.getHaul().isEmpty()) {
            if (!worker.isDeadOrDying()) {
                log.setStatus(ExpeditionLog.Status.RETURNING_HOME);
                final List<ItemStack> packed = new ArrayList<>();
                for (final ItemStack stack : job.getHaul()) {
                    if (InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), stack)) {
                        worker.decreaseSaturationForContinuousAction();
                        worker.getCitizenExperienceHandler().addExperience(0.2);
                        StatsUtil.trackStatByName(building, STAT_FOUND, stack.getHoverName(), stack.getCount());
                        packed.add(stack);
                    } else {
                        Voyager.LOGGER.warn("[Voyager] {} had no room in the pack for {}x {} - it stays in the End",
                                name(), stack.getCount(), stack.getHoverName().getString());
                    }
                }
                Voyager.LOGGER.info("[Voyager] {} packed the haul: {}", name(), describe(packed));
                comms("com.voyager.comms.returning", name(), packed.stream().mapToInt(ItemStack::getCount).sum());
                announceTrophies(trophies, packed);
            }
            job.getHaul().clear();
            return getState();
        }

        log.setStatus(ExpeditionLog.Status.COMPLETED);
        Voyager.LOGGER.info("[Voyager] {} finished the expedition and is heading home", name());
        return AIWorkerState.NETHER_RETURN;
    }

    /**
     * The trophies of a completed trip, with the server config's chances (see Trophies): a souvenir
     * named after a place in the End, and from a level-5 Departure Point an End-city book and a map of
     * the End. They go to the front of the haul - packed first, carried home and unloaded into the hut
     * like every other find - and into the Expedition Log. Returns what was found (often nothing).
     */
    private List<ItemStack> rollTrophies(final ExpeditionLog log) {
        final Trophies.Roll roll = Trophies.decide(building.getBuildingLevel(), VoyagerConfig.souvenirChance(),
                VoyagerConfig.endCityBookChance(), VoyagerConfig.endMapChance(), worker.getRandom());
        if (!roll.any()) {
            return List.of();
        }
        if (job.getExpedition() <= 0) {
            // a trip that set off before 0.4.0 numbered them: it gets its number now
            job.setExpedition(building.nextExpeditionNumber());
        }
        final TripStory story = new TripStory(Trophies.place(job.getTally().beatTheDragon(), worker.getRandom()).id(),
                name(), job.getExpedition(), job.getColony().getDay(),
                building.isEndGate() ? TripStory.LOOK_END_GATE : TripStory.LOOK_LAUNCHPAD, job.getTally().fights());
        final List<ItemStack> trophies = new ArrayList<>();
        if (roll.souvenir()) {
            trophies.add(Trophies.souvenir(story));
        }
        if (roll.book()) {
            final ItemStack book = Trophies.book(world.registryAccess(), worker.getRandom(), VoyagerConfig.bookEnchantments());
            if (!book.isEmpty()) {
                trophies.add(book);
            }
        }
        if (roll.map()) {
            trophies.add(Trophies.endMap(story));
        }
        if (trophies.isEmpty()) {
            return trophies;
        }
        job.addHaulFirst(trophies);
        log.addLoot(trophies);
        Voyager.LOGGER.info("[Voyager] {} brings something home to remember expedition {} by: {}", name(), job.getExpedition(),
                trophies.stream().map(t -> Trophies.describe(t).getString()).collect(Collectors.joining(", ")));
        return trophies;
    }

    /** Long-range Comms: one line naming the trophies that made it into the pack. */
    private void announceTrophies(final List<ItemStack> trophies, final List<ItemStack> packed) {
        final List<Component> parts = new ArrayList<>();
        for (final ItemStack trophy : trophies) {
            if (packed.contains(trophy)) {
                parts.add(Trophies.describe(trophy));
            }
        }
        if (!parts.isEmpty()) {
            comms(Trophies.KEY_COMMS, name(), Trophies.join(parts));
        }
    }

    /**
     * One simulated fight. Returns a state to switch to when the Voyager did not survive it (or,
     * with permanent death off, was driven back), null otherwise.
     */
    private IAIState fight(final ItemStack token, final ExpeditionLog log) {
        final AdventureData data = AdventureData.readFromItemStack(token);
        if (data == null) {
            return null;
        }
        equipArmor(true);
        worker.setItemSlot(EquipmentSlot.MAINHAND, findTool(ModEquipmentTypes.sword.get()));
        final DamageSource source = new DamageSource(world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(END_DAMAGE));
        final DamageSource swordSource = new DamageSource(world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageSourceKeys.DEFAULT), worker);
        final EntityType<?> mobType = data.entityType();
        final String mobName = EntityType.getKey(mobType).getPath();
        final Entity created = mobType.create(world);
        if (!(created instanceof LivingEntity mob)) {
            return null;
        }
        final float healthBefore = worker.getHealth();
        float mobHealth = mob.getHealth();
        float incoming = data.damage();
        incoming -= incoming * (getSecondarySkillLevel() * SECONDARY_DAMAGE_REDUCTION);
        // Void Insurance pays part of every blow
        incoming *= (float) (1.0 - VoyagerResearch.strength(job.getColony(), VoyagerResearch.VOID_INSURANCE));
        if (mobType == EntityType.ENDER_DRAGON) {
            Voyager.LOGGER.info("[Voyager] {} faces the dragon!", name());
            comms("com.voyager.comms.dragon", name());
        }
        // permanent death off: the blow that would kill is never dealt - the Voyager turns back instead
        final boolean permanentDeath = VoyagerConfig.permanentDeath();
        boolean drivenBack = false;
        int hit = 0;
        while (mobHealth > 0.0f && !worker.isDeadOrDying()) {
            worker.hurtTime = 0;
            worker.invulnerableTime = 0;
            float damage = 3.0f;
            final boolean lands = worker.getRandom().nextBoolean();
            final boolean takes = worker.getRandom().nextBoolean();
            final ItemStack sword = worker.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!sword.isEmpty()) {
                if (sword.getItem() instanceof SwordItem swordItem) {
                    damage += swordItem.getDamage(sword);
                } else {
                    damage += (float) TinkersToolHelper.getDamage(sword);
                }
                damage += EnchantmentHelper.modifyDamage((ServerLevel) worker.level(), sword, mob, swordSource, 1.0f) / 2.5f;
                if (lands) {
                    sword.hurtAndBreak(1, (ServerLevel) worker.level(), worker,
                            item -> worker.setItemSlot(EquipmentSlot.MAINHAND, findTool(ModEquipmentTypes.sword.get())));
                }
            }
            if (lands) {
                mobHealth -= damage;
            }
            if (takes) {
                // decided before hurt(), which kills on its own: afterwards it would be too late
                if (ExpeditionRules.drivenBack(permanentDeath, worker.getHealth(), worstBlow(source, incoming))) {
                    drivenBack = true;
                    break;
                }
                if (!worker.hurt(source, incoming)) {
                    final float after = worker.calculateDamageAfterAbsorbs(source, incoming);
                    if (ExpeditionRules.drivenBack(permanentDeath, worker.getHealth(), after)) {
                        drivenBack = true;
                        break;
                    }
                    worker.setHealth(worker.getHealth() - after);
                }
            }
            if (hit % 2 == 0) {
                final float healed = regenerate();
                if (healed > 0.0f) {
                    worker.getCitizenData().decreaseSaturation(healed * 0.25f);
                }
            } else if (worker.getCitizenData().getSaturation() < 10.0) {
                eatRation();
            }
            hit++;
        }
        log.setCitizen(worker);
        logAllEquipment(log, true);
        if (drivenBack) {
            return driveBack(mobName, log);
        }
        if (worker.isDeadOrDying()) {
            Voyager.LOGGER.warn("[Voyager] {} was lost in the End fighting a {} (after {} blows)", name(), mobName, hit);
            comms("com.voyager.comms.lost", name(), mobName);
            log.setKilled();
            StatsUtil.trackStat(building, STAT_DEATHS, 1);
            if (VoyagerResearch.has(job.getColony(), VoyagerResearch.RETURN_TO_SENDER)) {
                returnPackToHut();
            }
            raiseGrave();
            InventoryUtils.clearItemHandler(worker.getItemHandlerCitizen());
            job.getFinds().clear();
            job.getHaul().clear();
            return AIWorkerState.IDLE;
        }
        final LootTable loot = world.getServer().reloadableRegistries().getLootTable(mob.getLootTable());
        final List<ItemStack> drops = new ArrayList<>(loot.getRandomItems(getLootContext()));
        drops.addAll(bonusDrops(mobType));
        job.addHaul(drops);
        job.getTally().recordWin(EntityType.getKey(mobType));
        log.addMob(mobType);
        log.addLoot(drops);
        Voyager.LOGGER.info("[Voyager] {} beat a {} in {} blows ({} -> {} hp), drops: {}", name(), mobName, hit,
                (int) healthBefore, (int) worker.getHealth(), describe(drops));
        comms("com.voyager.comms.fight", name(), mobName, (int) worker.getHealth(), (int) worker.getMaxHealth());
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        equipArmor(false);
        worker.getCitizenExperienceHandler().addExperience(CitizenItemUtils.applyMending(worker, data.xp()));
        return null;
    }

    /**
     * The most a blow can take through hurt(), worked out without dealing it. Armour softens it the
     * way vanilla does; what can only soften it further - MineColonies' cap on one blow against a
     * citizen, protection enchantments, resistance, absorption - is left out, so the estimate errs on
     * the side of turning back. Nothing is dealt or worn here; with permanent death on the answer
     * is simply not used.
     */
    private float worstBlow(final DamageSource source, final float incoming) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return incoming;
        }
        return CombatRules.getDamageAfterAbsorb(worker, incoming, source, worker.getArmorValue(),
                (float) worker.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    }

    /**
     * Permanent death is off and the next blow would have killed: the Voyager is driven back. Left
     * with a quarter of their health, they give up the rest of the itinerary and everything gathered
     * on this trip (the supplies are spent all the same) and come home through the normal landing,
     * gear and remaining rations still in the pack. No grave, no death on the record, no Return to
     * Sender, and the Expedition Log is not marked killed - nobody was lost. The wound is applied to
     * the live entity, after the travel respawn, like every blow of a fight; the job remembers the
     * retreat so a respawn on the way home cannot heal it (see keepWound).
     */
    private IAIState driveBack(final String mobName, final ExpeditionLog log) {
        worker.setHealth(ExpeditionRules.woundedHealth(worker.getMaxHealth()));
        woundSeenOn = worker.getId();
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        equipArmor(false);
        job.getFinds().clear();
        job.getHaul().clear();
        job.setDrivenBack(true);
        // nothing to remember this one by: no trophies for a retreat
        job.setTrophiesRolled(true);
        forgetLoot(log);
        log.setStatus(ExpeditionLog.Status.COMPLETED);
        StatsUtil.trackStat(building, STAT_RETREATS, 1);
        Voyager.LOGGER.info("[Voyager] {} was driven back by a {} and comes home wounded, empty-handed", name(), mobName);
        comms("com.voyager.comms.driven_back", name(), mobName);
        return AIWorkerState.NETHER_RETURN;
    }

    /** The log keeps the fights won and the gear, but no loot: none of it is coming home. */
    private void forgetLoot(final ExpeditionLog log) {
        final List<Tuple<EntityType<?>, Integer>> mobs = log.getMobs();
        final List<ItemStack> equipment = log.getEquipment();
        log.reset();
        log.setCitizen(worker);
        log.setEquipment(equipment);
        for (final Tuple<EntityType<?>, Integer> mob : mobs) {
            for (int i = 0; i < mob.getB(); i++) {
                log.addMob(mob.getA());
            }
        }
    }

    /**
     * A driven-back Voyager stays wounded until home. The wound lives on the entity, and a citizen
     * whose entity is respawned (by the travelling manager, or by MineColonies after a reload that
     * lost it) comes back at full health: once per entity, a full-health driven-back Voyager is
     * wounded again.
     */
    private void keepWound() {
        if (!job.isDrivenBack() || worker.getId() == woundSeenOn) {
            return;
        }
        woundSeenOn = worker.getId();
        if (worker.getHealth() >= worker.getMaxHealth()) {
            worker.setHealth(ExpeditionRules.woundedHealth(worker.getMaxHealth()));
            Voyager.LOGGER.info("[Voyager] {} was respawned at full health on the way home - still wounded from the retreat ({} hp)",
                    name(), (int) worker.getHealth());
        }
    }

    /** What the research adds on top of the mob's own loot table (the dragon has none of her own). */
    private List<ItemStack> bonusDrops(final EntityType<?> mobType) {
        final List<ItemStack> extra = new ArrayList<>();
        if (mobType == EntityType.SHULKER) {
            final int shells = (int) VoyagerResearch.strength(job.getColony(), VoyagerResearch.SHULKER_WHISPERER);
            if (shells > 0) {
                extra.add(new ItemStack(Items.SHULKER_SHELL, shells));
            }
        } else if (mobType == EntityType.ENDERMAN) {
            final int pearls = (int) VoyagerResearch.strength(job.getColony(), VoyagerResearch.ENDER_HARVEST);
            if (pearls > 0) {
                extra.add(new ItemStack(Items.ENDER_PEARL, pearls));
            }
        } else if (mobType == EntityType.ENDER_DRAGON) {
            extra.add(new ItemStack(Items.DRAGON_HEAD));
            extra.add(new ItemStack(Items.DRAGON_BREATH, worker.getRandom().nextInt(3, 6)));
        }
        return extra;
    }

    /**
     * MineColonies raises no grave for a citizen who dies out of sight, so the Voyager does it
     * here: a grave by the console with whatever they still carried (nothing, after Return to
     * Sender) and their record, so the Undertaker can bury them - and, with the Graveyard's
     * research, maybe bring them back.
     */
    private void raiseGrave() {
        final BlockPos at = job.getColony().getGraveManager().createCitizenGrave(world, building.getPosition(), worker.getCitizenData());
        if (at == null) {
            Voyager.LOGGER.warn("[Voyager] No room for a grave for {} by the {}", name(), lookName());
            return;
        }
        Voyager.LOGGER.info("[Voyager] A grave for {} was raised at {}", name(), at);
        MessageUtils.format("com.voyager.grave", name()).sendTo(job.getColony()).forManagers();
    }

    /** Return to Sender: armour off, and everything in the pack goes into the hut's racks. */
    private void returnPackToHut() {
        equipArmor(false);
        int returned = 0;
        for (int slot = 0; slot < worker.getInventoryCitizen().getSlots(); slot++) {
            final ItemStack stack = worker.getInventoryCitizen().getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, building)) {
                returned++;
            }
        }
        Voyager.LOGGER.info("[Voyager] {} stacks of {}'s gear were returned to the {}", returned, name(), lookName());
    }

    /**
     * Collects `count` of the block: with the best tool in the backpack when the block needs
     * one (a block that needs a better tool than the Voyager carries yields nothing, as in
     * vanilla), by hand when it does not (chorus flowers). Returns the ticks it took.
     */
    private int dig(final Block block, final int count, final ExpeditionLog log) {
        final BlockState state = block.defaultBlockState();
        final boolean needsTool = state.requiresCorrectToolForDrops();
        final String blockName = block.getName().getString();
        ItemStack tool = findTool(state, worker.blockPosition());
        if (needsTool && !(tool.getItem() instanceof TieredItem)) {
            Voyager.LOGGER.info("[Voyager] {} found {}x {} but has no tool for it", name(), count, blockName);
            return TICK_DELAY;
        }
        int delay = 0;
        int dug = 0;
        worker.setItemSlot(EquipmentSlot.MAINHAND, tool);
        for (int i = 0; i < count; i++) {
            if (needsTool) {
                if (tool.isEmpty()) {
                    break;
                }
                if (!tool.isCorrectToolForDrops(state)) {
                    delay += TICK_DELAY;
                    continue;
                }
            }
            final LootTable loot = world.getServer().reloadableRegistries().getLootTable(block.getLootTable());
            final List<ItemStack> drops = loot.getRandomItems(getLootContext());
            job.addHaul(drops);
            log.addLoot(drops);
            dug++;
            if (needsTool) {
                tool.hurtAndBreak(1, (ServerLevel) worker.level(), worker, item -> {});
                if (tool.isEmpty()) {
                    tool = findTool(state, worker.blockPosition());
                    worker.setItemSlot(EquipmentSlot.MAINHAND, tool);
                }
            }
            worker.getCitizenExperienceHandler().addExperience(CitizenItemUtils.applyMending(worker, xpFor(block)));
            delay += TICK_DELAY;
        }
        Voyager.LOGGER.info("[Voyager] {} dug {} of {}x {}{}", name(), dug, count, blockName,
                dug < count ? " (the tool was too weak or broke)" : "");
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        logAllEquipment(log, false);
        return delay;
    }

    private int xpFor(final Block block) {
        final ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        final String path = id.getPath();
        if (path.contains("ore")) {
            return worker.getRandom().nextInt(2, 5);
        }
        if (path.contains("purpur") || path.contains("chorus") || path.contains("obsidian")) {
            return worker.getRandom().nextInt(0, 2);
        }
        return 0;
    }

    // ------------------------------------------------------------------ home

    /**
     * Homecoming in two acts: the landing (rocket back on the pad) or the gate lighting up
     * again, with the Voyager stepping out at the key moment; then the walk to the hut with
     * the haul.
     */
    private IAIState comeHome() {
        job.setStatus(JobVoyager.Status.RETURNING, job.isDrivenBack()
                ? "driven back - coming home wounded, empty-handed" : "back from the End - unloading the haul");
        keepWound();
        if (worker.isInvisible()) {
            if (job.isCountingDown()) {
                return getState();
            }
            if (!(world instanceof ServerLevel level)) {
                return getState();
            }
            final BlockPos departure = building.getDeparturePosition();
            job.setCountingDown(true);
            final Runnable appear = () -> {
                job.setCountingDown(false);
                if (worker.isRemoved() || !worker.isAlive()) {
                    return;
                }
                TeleportHelper.teleportCitizen(worker, world, departure);
                worker.setInvisible(false);
                Voyager.LOGGER.info("[Voyager] {} is back at the {} ({})", name(), lookName(), departure);
                comms("com.voyager.comms.landed", name(), lookName());
            };
            if (building.isEndGate()) {
                Voyager.LOGGER.info("[Voyager] the gate at {} lights up for {}", building.getEffectCentre(), name());
                Effects.gateArrival(level, building.getEffectCentre(), departure, appear);
            } else {
                Voyager.LOGGER.info("[Voyager] rocket coming in to land at {} with {}", departure, name());
                Effects.rocketLanding(level, departure, () -> {
                    building.revealRocket();
                    appear.run();
                });
            }
            return getState();
        }
        if (job.isCountingDown()) {
            return getState();
        }
        if (!walkToBuilding()) {
            return getState();
        }
        worker.getCitizenData().setJobStatus(JobStatus.STUCK);
        job.setAway(false);
        currentRecipeStorage = null;
        if (job.isDrivenBack()) {
            // a retreat is counted as one (voyager_retreats), not as a completed trip
            job.setDrivenBack(false);
            Voyager.LOGGER.info("[Voyager] {} is home, wounded ({} hp) and empty-handed", name(), (int) worker.getHealth());
        } else {
            StatsUtil.trackStat(building, STAT_TRIPS, 1);
            Voyager.LOGGER.info("[Voyager] {} is home - unloading the haul into the hut", name());
        }
        return AIWorkerState.INVENTORY_FULL;
    }

    // ------------------------------------------------------------------ gear, rations, healing

    private ItemStack findItem(final @NotNull Predicate<ItemStack> predicate) {
        final int slot = InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getItemHandlerCitizen(), predicate);
        return slot < 0 ? ItemStack.EMPTY : worker.getInventoryCitizen().getStackInSlot(slot);
    }

    private ItemStack findTool(final @NotNull EquipmentTypeEntry tool) {
        return findItem(stack -> ItemStackUtils.hasEquipmentLevel(stack, tool, 0, building.getMaxEquipmentLevel()));
    }

    private ItemStack findTool(final @NotNull BlockState target, final BlockPos pos) {
        final int slot = getMostEfficientTool(target, pos);
        return slot < 0 ? ItemStack.EMPTY : worker.getInventoryCitizen().getStackInSlot(slot);
    }

    private void setEquipSlot(final EquipmentSlot slot, final boolean equip) {
        if (equip) {
            for (final List<GuardGear> list : gearNeeded) {
                for (final GuardGear gear : list) {
                    if (!gear.getType().equals(slot)
                            || building.getBuildingLevel() < gear.getMinBuildingLevelRequired()
                            || building.getBuildingLevel() > gear.getMaxBuildingLevelRequired()
                            || gear.test(worker.getInventoryCitizen().getArmorInSlot(gear.getType()))) {
                        continue;
                    }
                    final int from = InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getItemHandlerCitizen(), gear);
                    if (from <= -1) {
                        continue;
                    }
                    final ItemStack stack = worker.getInventoryCitizen().getStackInSlot(from);
                    worker.getInventoryCitizen().transferArmorToSlot(gear.getType(), from);
                    virtualEquipment.put(gear.getType(), stack);
                }
            }
        } else {
            worker.getInventoryCitizen().moveArmorToInventory(slot);
            virtualEquipment.put(slot, ItemStack.EMPTY);
        }
    }

    private void equipArmor(final boolean equip) {
        setEquipSlot(EquipmentSlot.HEAD, equip);
        setEquipSlot(EquipmentSlot.CHEST, equip);
        setEquipSlot(EquipmentSlot.LEGS, equip);
        setEquipSlot(EquipmentSlot.FEET, equip);
    }

    private void logAllEquipment(final @NotNull ExpeditionLog log, final boolean alreadyEquipped) {
        if (!alreadyEquipped) {
            equipArmor(true);
        }
        final StackList edible = new StackList(rationsList(), "Edible Food", 1);
        final List<ItemStack> equipment = new ArrayList<>();
        equipment.add(findTool(ModEquipmentTypes.sword.get()));
        equipment.add(worker.getInventoryCitizen().getArmorInSlot(EquipmentSlot.HEAD));
        equipment.add(worker.getInventoryCitizen().getArmorInSlot(EquipmentSlot.CHEST));
        equipment.add(worker.getInventoryCitizen().getArmorInSlot(EquipmentSlot.LEGS));
        equipment.add(worker.getInventoryCitizen().getArmorInSlot(EquipmentSlot.FEET));
        equipment.add(findTool(ModEquipmentTypes.pickaxe.get()));
        equipment.add(findItem(edible::matches));
        log.setEquipment(equipment);
        if (!alreadyEquipped) {
            equipArmor(false);
        }
    }

    private List<ItemStack> rationsList() {
        final Set<ItemStorage> allowed = building.getModule(VoyagerModules.MENU).getMenu();
        rations.removeIf(item -> allowed.contains(new ItemStorage(item)));
        return rations;
    }

    private boolean onMenu(final ItemStack stack) {
        return building.getModule(VoyagerModules.MENU).getMenu().contains(new ItemStorage(stack));
    }

    /**
     * A breather before the next fight: rations are eaten and wounds patched up, up to a share
     * of full health. Without food the Voyager goes in as they are - which is how they get lost.
     */
    private void recover() {
        final float before = worker.getHealth();
        int eaten = 0;
        while (worker.getHealth() < worker.getMaxHealth() * RECOVER_TO && eaten < RATIONS_PER_REST) {
            final StackList edible = new StackList(rationsList(), "Edible Food", 1);
            final int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(worker, edible::matches);
            if (slot < 0) {
                break;
            }
            ItemStackUtils.consumeFood(worker.getInventoryCitizen().getStackInSlot(slot), worker, null);
            worker.heal(RATION_HEAL);
            eaten++;
        }
        if (eaten > 0) {
            Voyager.LOGGER.info("[Voyager] {} ate {} ration(s) and patched up ({} -> {} hp)", name(), eaten, (int) before, (int) worker.getHealth());
        } else if (worker.getHealth() < worker.getMaxHealth() * RECOVER_TO) {
            Voyager.LOGGER.info("[Voyager] {} is hurt ({} hp) and has no rations left", name(), (int) worker.getHealth());
        }
    }

    private void eatRation() {
        final StackList edible = new StackList(rationsList(), "Edible Food", 1);
        final int slot = InventoryUtils.findFirstSlotInProviderNotEmptyWith(worker, edible::matches);
        if (slot > -1) {
            ItemStackUtils.consumeFood(worker.getInventoryCitizen().getStackInSlot(slot), worker, null);
        }
    }

    private void checkAndRequestArmor() {
        for (final List<GuardGear> list : gearNeeded) {
            for (final GuardGear gear : list) {
                if (building.getBuildingLevel() < gear.getMinBuildingLevelRequired()
                        || building.getBuildingLevel() > gear.getMaxBuildingLevelRequired()) {
                    continue;
                }
                int bestSlot = -1;
                int bestLevel = -1;
                IItemHandler bestHandler = null;
                if (virtualEquipment.containsKey(gear.getType()) && !ItemStackUtils.isEmpty(virtualEquipment.get(gear.getType()))) {
                    bestLevel = gear.getItemNeeded().getMiningLevel(virtualEquipment.get(gear.getType()));
                } else {
                    final ItemStack inBackpack = findItem(gear::test);
                    if (!inBackpack.isEmpty()) {
                        if (!virtualEquipment.containsKey(gear.getType()) || ItemStackUtils.isEmpty(virtualEquipment.get(gear.getType()))) {
                            virtualEquipment.put(gear.getType(), inBackpack);
                            bestLevel = gear.getItemNeeded().getMiningLevel(inBackpack);
                        }
                    } else {
                        virtualEquipment.put(gear.getType(), ItemStack.EMPTY);
                    }
                }
                final Map<IItemHandler, List<Integer>> inHut = InventoryUtils.findAllSlotsInProviderWith(building, gear::test);
                if (inHut.isEmpty()) {
                    if (ItemStackUtils.isEmpty(virtualEquipment.get(gear.getType()))) {
                        checkForToolOrWeaponAsync(gear.getItemNeeded(), gear.getMinArmorLevel(), gear.getMaxArmorLevel());
                    }
                } else {
                    for (final Map.Entry<IItemHandler, List<Integer>> entry : inHut.entrySet()) {
                        for (final Integer slot : entry.getValue()) {
                            final ItemStack stack = entry.getKey().getStackInSlot(slot);
                            if (ItemStackUtils.isEmpty(stack)) {
                                continue;
                            }
                            final int level = gear.getItemNeeded().getMiningLevel(stack);
                            if (level > bestLevel) {
                                bestLevel = level;
                                bestSlot = slot;
                                bestHandler = entry.getKey();
                            }
                        }
                    }
                }
                if (bestHandler == null) {
                    continue;
                }
                if (!ItemStackUtils.isEmpty(virtualEquipment.get(gear.getType()))) {
                    final int slot = InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getInventoryCitizen(),
                            stack -> stack == virtualEquipment.get(gear.getType()));
                    if (slot > -1) {
                        InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, building);
                    }
                }
                virtualEquipment.put(gear.getType(), bestHandler.getStackInSlot(bestSlot));
                InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(bestHandler, bestSlot, worker.getInventoryCitizen());
            }
        }
    }

    /** Packs the server config's rations per Voyager (16 by default) from the hut before a trip. */
    private IAIState checkAndRequestRations() {
        final int rationsToPack = VoyagerConfig.rationsPerVoyager();
        if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), this::onMenu) >= rationsToPack) {
            return getState();
        }
        if (InventoryUtils.hasBuildingEnoughElseCount(building, this::onMenu, 1) >= 1) {
            needsCurrently = new Tuple<>(this::onMenu, rationsToPack);
            return AIWorkerState.GATHERING_REQUIRED_MATERIALS;
        }
        return getState();
    }

    /** Natural regeneration between blows, paid for with saturation (the colony's research applies). */
    private float regenerate() {
        if (worker.getHealth() >= worker.getMaxHealth()) {
            return 0.0f;
        }
        final double saturation = worker.getCitizenData().getSaturation();
        final var effects = worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects();
        final double limitDecrease = effects.getEffectStrength(ResearchConstants.SATLIMIT);
        final double regenBonus = 1.0 + effects.getEffectStrength(ResearchConstants.REGENERATION);
        double heal;
        if (saturation >= 60.0 + limitDecrease) {
            heal = 2.0 * regenBonus;
        } else if (saturation < 6.0) {
            return 0.0f;
        } else {
            heal = 1.0 * regenBonus;
        }
        worker.heal((float) heal);
        return (float) heal;
    }
}
