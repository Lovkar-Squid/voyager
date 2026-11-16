package me.lovkar.voyagerexpedition;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.operations.PlaceStructureOperation;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.CreativeStructureHandler;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityGrave;
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule;
import com.minecolonies.core.colony.buildings.modules.ExpeditionLogModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.research.GlobalResearchEffect;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.VoyagerConfig;
import me.lovkar.voyager.ai.EntityAIWorkVoyager;
import me.lovkar.voyager.ai.ExpeditionRules;
import me.lovkar.voyager.colony.BuildingVoyager;
import me.lovkar.voyager.colony.JobVoyager;
import me.lovkar.voyager.colony.VoyagerModules;
import me.lovkar.voyager.colony.VoyagerResearch;
import me.lovkar.voyager.trophy.EndPlace;
import me.lovkar.voyager.trophy.TripStory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A real expedition on the dedicated test server: a colony on the flat world, a level-5 Launchpad pasted
 * from the bundled pack and registered, a Voyager hired, gear, rations and the plan's supplies in the hut,
 * the research effects of the mode applied - and then the Voyager's AI runs for real. The probe only
 * watches: the job, the building, the citizen, the world and the server's log, and checks what it sees.
 *
 * <p>The mode word comes from {@code probe-mode.txt} in the server's working directory (the rig writes
 * it), and the rig seeds the server config from {@code rigconfig-<mode>/}:</p>
 * <ul>
 *   <li>{@code loop} - one complete Launchpad trip: boarding, departure, the rocket stowed and back, away
 *       and back, the haul unloaded, the trip counted, Rapid Refit's time, the three trophies in the hut
 *       with their story, the job and building NBT, and the absorb fallback of a refused blow;</li>
 *   <li>{@code dragon} - Dragon Hunt with the dragon certain: either a win (head, breath, the Dragon's
 *       Wake souvenir) or a loss (then the lost checks); back in the world at half health, the
 *       Voyager must eat a ration before the first fight;</li>
 *   <li>{@code lost} - an unarmed, unarmoured Voyager without rations meets the End: Return to Sender,
 *       the grave, the rocket coming home alone, nothing left on the job;</li>
 *   <li>{@code wounded} - a deadly trip (no rations, half health, the dragon at the end) with permanent
 *       death off: driven back wounded, home with the gear, no grave, no death, empty-handed; the
 *       entity is also taken away after the retreat to prove a respawn on the way home keeps the
 *       wound.</li>
 * </ul>
 *
 * <p>Research is injected as effects (the University is not part of the test). Checks fire on edges -
 * the moment a flag of the job or the building changes - not on per-tick world scans. Prints
 * "[expedition] CHECK ..." lines and "[expedition] RESULT expedition: PASS|FAIL", then halts. Nothing
 * here logs above INFO, and no line carries the words the rig's log scan treats as a failure.</p>
 */
@Mod("voyagerexpedition")
public class Expedition {
    private static final Logger LOGGER = LoggerFactory.getLogger("voyagerexpedition");

    private enum Mode { LOOP, DRAGON, LOST, WOUNDED }

    private enum Phase { BOOT, PASTE, PLAN, WAIT_DEPART, AWAY, WAIT_HOME, WAIT_UNLOAD, WAIT_LOST, ABSORB, VERIFY, DONE }

    // ---------------------------------------------------------------- the seeds (rigconfig-<mode>/voyager-server.toml)

    private static final int SEED_TRIPS = 2;
    private static final int SEED_RATIONS = 4;
    private static final double SEED_SUPPLY = 1.0;
    private static final double SEED_LENGTH = 0.25;
    private static final double SEED_TROPHY_CHANCE = 1.0;
    private static final double RAPID_REFIT = 0.25;

    // ---------------------------------------------------------------- budgets, in game ticks

    private static final int START_TICK = 60;
    private static final int HARD_CAP = 24000;
    private static final int STATUS_EVERY = 200;
    private static final int BUDGET_PASTE = 1200;
    private static final int BUDGET_PLAN = 1200;
    private static final int BUDGET_DEPART = 6000;
    /**
     * After the travel time: the respawn, the fights and digs (all in one AI call), then a pause as long
     * as the LAST find took - 40 ticks a block, so up to 64 x 40 for a stack of end stone - before the
     * trophies are rolled. The trip length multiplier does not shorten that pause.
     */
    private static final int AWAY_SLACK = 1500 + 64 * 40;
    private static final int BUDGET_HOME = 3000;
    private static final int BUDGET_UNLOAD = 2400;
    private static final int BUDGET_ROCKET_AFTER_DEATH = 1500;
    private static final int LINE_GRACE = 200;
    private static final int GOLD = 5;

    private static final ResourceLocation REACH_FOR_THE_STARS = ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "effects/blockhutvoyager");
    private static final ResourceKey<net.minecraft.world.damagesource.DamageType> END_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "the_end"));
    private static final String TRIP5 = "voyager:recipes/voyager/trip5";
    private static final String DRAGON = "ender_dragon";

    private static final Pattern DEPARTS = Pattern.compile(
            "departs from the (.+?) at .+ on expedition (\\d+): expedition roll (.*) \\((\\d+) finds, about (\\d+) s away\\)");
    private static final Pattern LIFTED = Pattern.compile("Rocket at .+ lifted off: (\\d+) blocks stowed");
    private static final Pattern LANDED = Pattern.compile("Rocket at .+ landed: (\\d+) blocks restored");
    private static final Pattern BEAT = Pattern.compile("beat a (\\S+) in (\\d+) blows");
    private static final Pattern LOST_IN_END = Pattern.compile("was lost in the End fighting a (\\S+) \\(after (\\d+) blows\\)");
    private static final Pattern DRIVEN_BACK = Pattern.compile("was driven back by a (\\S+) and comes home wounded, empty-handed");
    private static final Pattern TURNS_BACK = Pattern.compile("turns back from a (\\S+) at ([\\d.]+) of ([\\d.]+) hp after (\\d+) blows: the next blow could take ([\\d.]+) hp");
    private static final Pattern ATE = Pattern.compile("ate (\\d+) ration\\(s\\)");
    private static final Pattern HOME_WOUNDED = Pattern.compile("is home, wounded \\((\\d+) hp\\) and empty-handed");
    private static final Pattern ROCKET_ALONE = Pattern.compile("Rocket at .+ is coming back without its crew");

    // ---------------------------------------------------------------- state

    private final String modeWord = readMode();
    private final Mode mode = parseMode(modeWord);
    private final LogTail logs = new LogTail(Path.of("logs", "latest.log"));

    private int tick = 0;
    private int setupTick = -1;
    private Phase phase = Phase.BOOT;
    private int phaseStart = 0;
    private boolean failed = false;
    private boolean finished = false;

    private MinecraftServer server;
    private ServerLevel level;
    private IColony colony;
    private BlockPos center;
    private BlockPos padPos;
    private Blueprint blueprint;
    private BuildingVoyager building;
    private ICitizenData citizen;
    private JobVoyager job;
    private IRecipeStorage plan;
    private final Map<BlockPos, BlockState> rocketCells = new LinkedHashMap<>();
    /** The rocket as it was stowed at lift-off (a door the Voyager opened on the way in stays open). */
    private final Map<BlockPos, BlockState> stowedCells = new LinkedHashMap<>();
    private int rocketSolid = 0;
    private final Set<BlockPos> gravesBefore = new HashSet<>();
    private final List<ItemStack> gearGiven = new ArrayList<>();

    // the observer: last tick's flags
    private boolean wasAway, wasTravelling, wasRocketAway, wasDrivenBack, wasTrophiesRolled;
    private int lastEntityId = -1;
    // what was seen
    private boolean sawBoarding, departed, dead, home, liftedOff, landed, sawEntityWhileTravelling, sawInvisibleAway;
    private boolean travelStarted, travelEnded, healthSetAfterTravel, discarded, respawnWanted;
    private int departTick = -1, travelEndTick = -1, liftOffTick = -1, landingTick = -1, deathTick = -1, homeTick = -1;
    private int drivenBackTick = -1, discardTick = -1, discardedId = -1, respawnedId = -1, travelEntityId = -1;
    private int findsAtDepart = -1, expeditionAtDepart = -1, soFarAtDepart = -1;
    private int expectedTicksAway = -1;
    private Map<String, Integer> departCounts = Map.of();
    private Map<String, Integer> tallyAtRoll = null;
    private Float healthAtHomeEdge = null;
    private float maxHealthAtHome = -1f;
    private final List<Pending> pending = new ArrayList<>();
    private boolean departureLineSeen = false;

    /** A log line the probe expects: looked for on every tick until it shows up or the deadline passes. */
    private record Pending(String what, Pattern pattern, int from, int deadline, Consumer<Matcher> then) {
    }

    public Expedition() {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private static String readMode() {
        try {
            return Files.readString(Path.of("probe-mode.txt")).trim().toLowerCase(Locale.ROOT);
        } catch (final Exception e) {
            return "";
        }
    }

    private static Mode parseMode(final String word) {
        return switch (word) {
            case "loop" -> Mode.LOOP;
            case "dragon" -> Mode.DRAGON;
            case "lost" -> Mode.LOST;
            case "wounded" -> Mode.WOUNDED;
            default -> null;
        };
    }

    // ---------------------------------------------------------------- output

    /**
     * Every line of the probe goes through here. The rig fails a run on any line that mentions the probe
     * or Voyager and contains the two words of a stack trace or an error level (in any case), so those
     * words never leave the probe, whatever a detail quotes.
     */
    private static String clean(final String text) {
        return text == null ? "null" : text.replaceAll("(?i)exception", "exc").replaceAll("(?i)error", "fault");
    }

    private void say(final String text) {
        LOGGER.info("[expedition] {}", clean(text));
    }

    private void check(final String what, final boolean ok, final String detail) {
        say("CHECK " + what + ": " + (ok ? "OK" : "FAIL") + " " + detail);
        failed |= !ok;
    }

    private void checkEq(final String what, final Object got, final Object want) {
        check(what, Objects.equals(got, want), "got " + got + ", want " + want);
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        phase = Phase.DONE;
        say("RESULT expedition: " + (failed ? "FAIL" : "PASS"));
        if (server != null) {
            server.halt(false);
        }
    }

    private void enter(final Phase next) {
        say("phase " + phase + " -> " + next + " at t=" + tick);
        phase = next;
        phaseStart = tick;
    }

    private boolean overBudget(final int budget) {
        if (tick - phaseStart <= budget) {
            return false;
        }
        check("phase " + phase, false, "no progress within " + budget + " ticks: " + diagnosis());
        finish();
        return true;
    }

    private String diagnosis() {
        final StringBuilder sb = new StringBuilder();
        sb.append("job ").append(job == null ? "-" : job.getStatus() + " '" + job.getStatusLine() + "'");
        final Optional<AbstractEntityCitizen> entity = liveEntity();
        sb.append(", entity ").append(entity.isEmpty() ? "absent"
                : (entity.get().isInvisible() ? "present invisible" : "present visible") + " hp " + entity.get().getHealth()
                + " at " + entity.get().blockPosition().toShortString());
        sb.append(", travelling ").append(citizen != null && colony.getTravellingManager().isTravelling(citizen));
        sb.append(", rocket away ").append(building != null && building.isRocketAway());
        if (job != null) {
            sb.append(", away ").append(job.isAway()).append(", driven back ").append(job.isDrivenBack())
                    .append(", trophies rolled ").append(job.isTrophiesRolled());
        }
        if (phase == Phase.WAIT_UNLOAD) {
            sb.append(", pack ").append(counts(packStacks())).append(", gained and still in the pack ").append(gainedStillInPack());
        }
        return sb.toString();
    }

    private static String simpleName(final Throwable t) {
        return t.getClass().getSimpleName().replace("Exception", "");
    }

    // ---------------------------------------------------------------- the tick

    private void onTick(final ServerTickEvent.Post event) {
        if (finished) {
            return;
        }
        tick++;
        try {
            step(event.getServer());
        } catch (final Throwable t) {
            check("tick", false, simpleName(t) + ": " + t.getMessage() + " in phase " + phase);
            int shown = 0;
            for (final StackTraceElement frame : t.getStackTrace()) {
                say("  at " + frame);
                if (++shown >= 12) {
                    break;
                }
            }
            finish();
        }
    }

    private void step(final MinecraftServer srv) {
        if (tick < START_TICK) {
            return;
        }
        if (phase == Phase.BOOT) {
            server = srv;
            if (mode == null) {
                check("mode", false, "probe-mode.txt says '" + modeWord + "', want loop, dragon, lost or wounded");
                finish();
                return;
            }
            check("mode", true, modeWord);
            setUp();
            return;
        }
        if (tick - setupTick > HARD_CAP) {
            check("phase " + phase, false, "hard cap of " + HARD_CAP + " ticks after setup: " + diagnosis());
            finish();
            return;
        }
        if (!pending.isEmpty() || tick % 20 == 0) {
            logs.poll();
            resolvePending();
        }
        if (tick % STATUS_EVERY == 0) {
            status();
        }
        switch (phase) {
            case PASTE -> {
                if (tick % 20 == 0 && level.getBlockEntity(padPos) instanceof AbstractTileEntityColonyBuilding) {
                    register();
                } else {
                    overBudget(BUDGET_PASTE);
                }
            }
            case PLAN -> {
                if (tick % 20 == 0) {
                    waitForPlan();
                }
                if (phase == Phase.PLAN) {
                    overBudget(BUDGET_PLAN);
                }
            }
            case WAIT_DEPART, AWAY, WAIT_HOME, WAIT_UNLOAD, WAIT_LOST -> {
                observe();
                if (!finished) {
                    progress();
                }
            }
            case ABSORB -> absorbStep();
            case VERIFY -> {
                logs.poll();
                resolvePending();
                verify();
                finish();
            }
            default -> {
            }
        }
    }

    private void status() {
        if (job == null) {
            say("t=" + tick + " phase " + phase + (colony == null ? "" : ", colony active " + colony.isActive() + ", close subscribers "
                    + colony.getPackageManager().getCloseSubscribers().size() + ", centre in the colony " + colony.isCoordInColony(level, center)
                    + ", pad in the colony " + colony.isCoordInColony(level, padPos)));
            return;
        }
        say("t=" + tick + " phase " + phase + " day time " + (level.getDayTime() % 24000L) + ", colony active " + colony.isActive() + ": " + diagnosis());
    }

    // ---------------------------------------------------------------- setup

    private void setUp() {
        setupTick = tick;
        level = server.overworld();
        // not 0,0,0: MineColonies takes BlockPos.ZERO for "no building" (this flat world's surface is at y=0)
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 4, 4);
        center = new BlockPos(4, y, 4);
        padPos = center.offset(36, 0, 0);
        // the colony and the pad loaded for good, before the colony exists: MineColonies only runs a colony
        // (and its buildings' colony ticks) while more than 40 of its claimed chunks are loaded
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        // daylight held, no mobs, no rain: citizens stop working in the rain and go to bed at night
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(1000L);
        level.setWeatherParameters(12000, 0, false, false);
        say("surface at y=" + y + ", pad at " + padPos.toShortString() + ", chunks forced, day held, clear weather");

        final ServerPlayer owner = FakePlayerFactory.getMinecraft(level);
        owner.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        colony = IColonyManager.getInstance().createColony(level, center, owner, "Expedition Test", "Medieval Oak");
        check("colony", colony != null, colony == null ? "createColony returned nothing" : "colony " + colony.getID() + " at " + center.toShortString());
        if (colony == null) {
            finish();
            return;
        }
        final Block townHall = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecolonies", "blockhuttownhall"));
        level.setBlock(center, townHall.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(center) instanceof AbstractTileEntityColonyBuilding hall) {
            final IBuilding th = colony.getServerBuildingManager().addNewBuilding(hall, level);
            say("town hall registered: " + (th != null));
        }
        // MineColonies only runs a colony (its buildings' ticks, travel, requests) while somebody is close.
        // A server with nobody on it has the fake owner: MineColonies 1.1.1420 refuses it as a subscriber
        // through addCloseSubscriber, so it goes straight into the live set (where it stays while it
        // stands on a chunk of the colony).
        colony.getPackageManager().getCloseSubscribers().add(owner);
        gravesBefore.addAll(colony.getGraveManager().getGraves().keySet());

        blueprint = StructurePacks.getBlueprint("Voyager", "expedition/launchpad5.blueprint", level.registryAccess());
        check("blueprint", blueprint != null, "Voyager pack, expedition/launchpad5.blueprint");
        if (blueprint == null) {
            finish();
            return;
        }
        blueprint.setRotationMirror(RotationMirror.NONE, level);
        // the hut's block entity gets its blueprint path from the file name, and MineColonies reads the
        // blueprint back through it when the hut is registered: with the extension, or the read fails
        if (!blueprint.getFileName().endsWith(".blueprint")) {
            blueprint.setFileName(blueprint.getFileName() + ".blueprint");
        }
        final CreativeStructureHandler handler = new CreativeStructureHandler(level, padPos, blueprint, RotationMirror.NONE, true);
        Manager.addToQueue(new PlaceStructureOperation(new StructurePlacer(handler), owner));
        say("placing the level-5 Launchpad at " + padPos.toShortString());
        enter(Phase.PASTE);
    }

    private void register() {
        final AbstractTileEntityColonyBuilding hut = (AbstractTileEntityColonyBuilding) level.getBlockEntity(padPos);
        IBuilding b = colony.getServerBuildingManager().getBuilding(padPos);
        if (b == null) {
            b = colony.getServerBuildingManager().addNewBuilding(hut, level);
        }
        if (b != null && b.getBuildingLevel() < 5) {
            b.upgradeBuildingLevelToSchematicData();
        }
        if (b != null && b.getBuildingLevel() < 5) {
            say("the schematic data gave level " + b.getBuildingLevel() + ": setting level 5 by hand");
            b.setBuildingLevel(5);
            b.onUpgradeComplete(blueprint, 5);
        }
        check("building", b instanceof BuildingVoyager && b.getBuildingLevel() == 5,
                b == null ? "not registered" : b.getClass().getSimpleName() + " level " + b.getBuildingLevel() + " schematic " + b.getSchematicName());
        if (!(b instanceof BuildingVoyager bv) || b.getBuildingLevel() != 5) {
            finish();
            return;
        }
        building = bv;
        final List<BlockPos> departure = building.getLocationsFromTag(BuildingVoyager.TAG_DEPARTURE);
        check("launchpad look", !building.isEndGate(), "schematic " + building.getSchematicName() + ", departure tag at " + departure);
        check("departure tag", !departure.isEmpty(), "departure position " + building.getDeparturePosition().toShortString());
        for (final BlockPos pos : building.getLocationsFromTag(BuildingVoyager.TAG_ROCKET)) {
            final BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                rocketCells.put(pos.immutable(), state);
            }
        }
        rocketSolid = rocketCells.size();
        check("rocket", rocketSolid > 0, rocketSolid + " non-air rocket-tagged cells of "
                + building.getLocationsFromTag(BuildingVoyager.TAG_ROCKET).size());

        for (final Map.Entry<ResourceLocation, Double> effect : research().entrySet()) {
            final double s = effect.getValue();
            colony.getResearchManager().getResearchEffects().applyEffect(new GlobalResearchEffect(effect.getKey(), "", "", s, s));
            checkEq("research " + effect.getKey().getPath(), VoyagerResearch.strength(colony, effect.getKey()), s);
        }
        building.getModule(VoyagerModules.MENU).addMenuItem(new ItemStack(Items.COOKED_BEEF));
        check("rations on the menu", building.getModule(VoyagerModules.MENU).getMenu().contains(new ItemStorage(new ItemStack(Items.COOKED_BEEF))), "cooked beef");
        enter(Phase.PLAN);
    }

    private void waitForPlan() {
        plan = building.getFirstModuleOccurance(BuildingVoyager.CraftingModule.class).getPlan(VoyagerConfig.supplyMultiplier());
        if (plan == null) {
            return;
        }
        final String table = plan.getLootTable() == null ? "none" : plan.getLootTable().location().toString();
        check("plan", TRIP5.equals(table), "loot table " + table + ", supplies " + describeInputs(plan));
        if (!TRIP5.equals(table)) {
            finish();
            return;
        }
        stock();
        hire();
        if (finished) {
            return;
        }
        checkConfig();
        enter(Phase.WAIT_DEPART);
    }

    private static String describeInputs(final IRecipeStorage recipe) {
        final StringBuilder sb = new StringBuilder();
        for (final ItemStorage input : recipe.getInput()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(input.getAmount()).append("x ").append(BuiltInRegistries.ITEM.getKey(input.getItem()));
        }
        return sb.toString();
    }

    /** Exactly the plan's supplies and 16 rations: enough for one trip and not for a second. */
    private void stock() {
        boolean all = true;
        for (final ItemStorage input : plan.getInput()) {
            int left = input.getAmount();
            while (left > 0) {
                final ItemStack stack = input.getItemStack().copy();
                final int n = Math.min(left, stack.getMaxStackSize());
                stack.setCount(n);
                all &= InventoryUtils.addItemStackToProvider(building, stack);
                left -= n;
            }
        }
        all &= InventoryUtils.addItemStackToProvider(building, new ItemStack(Items.COOKED_BEEF, 16));
        check("stocked", all, "the plan's supplies and 16 cooked beef in the hut: " + counts(hutStacks()));
    }

    private void hire() {
        final ICitizenData data = colony.getCitizenManager().createAndRegisterCivilianData();
        BlockPos at = EntityUtils.getSpawnPoint(level, padPos);
        if (at == null) {
            at = padPos.offset(0, 1, 3);
        }
        colony.getCitizenManager().spawnOrCreateCitizen(data, level, at);
        final WorkerBuildingModule work = building.getFirstModuleOccurance(WorkerBuildingModule.class);
        final boolean assigned = work.assignCitizen(data);
        check("hired", assigned && data.getJob() instanceof JobVoyager,
                data.getName() + " assigned " + assigned + ", job " + (data.getJob() == null ? "none" : data.getJob().getClass().getSimpleName())
                        + ", entity " + data.getEntity().isPresent() + " at " + at.toShortString());
        if (!(data.getJob() instanceof JobVoyager j)) {
            finish();
            return;
        }
        citizen = data;
        job = j;
        gearGiven.addAll(gear());
        boolean all = true;
        for (final ItemStack stack : gearGiven) {
            all &= InventoryUtils.addItemStackToItemHandler(citizen.getInventory(), stack.copy());
        }
        check("gear", all, counts(gearGiven).toString());
    }

    private void checkConfig() {
        final String got = String.format(Locale.ROOT, "trips %d rations %d supply %.2f length %.2f dragon %.2f death %s souvenir %.2f book %.2f map %.2f books %d",
                VoyagerConfig.tripsPerLaunchWindow(), VoyagerConfig.rationsPerVoyager(), VoyagerConfig.supplyMultiplier(),
                VoyagerConfig.tripLengthMultiplier(), VoyagerConfig.dragonChance(), VoyagerConfig.permanentDeath(),
                VoyagerConfig.souvenirChance(), VoyagerConfig.endCityBookChance(), VoyagerConfig.endMapChance(),
                VoyagerConfig.bookEnchantments().size());
        final String want = String.format(Locale.ROOT, "trips %d rations %d supply %.2f length %.2f dragon %.2f death %s souvenir %.2f book %.2f map %.2f books %d",
                SEED_TRIPS, SEED_RATIONS, SEED_SUPPLY, SEED_LENGTH, mode == Mode.LOOP ? 0.0 : 1.0, mode != Mode.WOUNDED,
                SEED_TROPHY_CHANCE, SEED_TROPHY_CHANCE, SEED_TROPHY_CHANCE, VoyagerConfig.DEFAULT_BOOK_ENCHANTMENTS.size());
        check("config", got.equals(want) && VoyagerConfig.bookEnchantments().equals(VoyagerConfig.DEFAULT_BOOK_ENCHANTMENTS),
                "got " + got + "; want " + want + " (the default book list)");
    }

    // ---------------------------------------------------------------- the mode's setup

    private Map<ResourceLocation, Double> research() {
        final Map<ResourceLocation, Double> r = new LinkedHashMap<>();
        r.put(REACH_FOR_THE_STARS, 5.0);
        r.put(VoyagerResearch.LONG_RANGE_COMMS, 1.0);
        switch (mode) {
            case LOOP -> r.put(VoyagerResearch.RAPID_REFIT, RAPID_REFIT);
            case DRAGON -> {
                r.put(VoyagerResearch.DRAGON_HUNT, 1.0);
                r.put(VoyagerResearch.RETURN_TO_SENDER, 1.0);
                r.put(VoyagerResearch.VOID_INSURANCE, 0.5);
            }
            case LOST, WOUNDED -> {
                r.put(VoyagerResearch.DRAGON_HUNT, 1.0);
                r.put(VoyagerResearch.RETURN_TO_SENDER, 1.0);
            }
        }
        return r;
    }

    private List<ItemStack> gear() {
        final Holder<Enchantment> protection = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        final List<ItemStack> out = new ArrayList<>();
        switch (mode) {
            case LOOP -> {
                out.add(new ItemStack(Items.DIAMOND_SWORD));
                out.add(new ItemStack(Items.DIAMOND_PICKAXE));
                out.add(new ItemStack(Items.DIAMOND_HELMET));
                final ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
                chest.enchant(protection, 4);
                out.add(chest);
                out.add(new ItemStack(Items.DIAMOND_LEGGINGS));
                out.add(new ItemStack(Items.DIAMOND_BOOTS));
            }
            case DRAGON -> {
                out.add(new ItemStack(Items.NETHERITE_SWORD));
                out.add(new ItemStack(Items.DIAMOND_PICKAXE));
                for (final Item piece : new Item[] {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}) {
                    final ItemStack stack = new ItemStack(piece);
                    stack.enchant(protection, 4);
                    out.add(stack);
                }
            }
            case LOST, WOUNDED -> {
                out.add(new ItemStack(Items.DIAMOND_SWORD));
                out.add(new ItemStack(Items.DIAMOND_PICKAXE));
                out.add(new ItemStack(Items.IRON_HELMET));
                out.add(new ItemStack(Items.IRON_CHESTPLATE));
                out.add(new ItemStack(Items.IRON_LEGGINGS));
                out.add(new ItemStack(Items.IRON_BOOTS));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- inventories

    private static String idOf(final ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static Map<String, Integer> counts(final List<ItemStack> stacks) {
        final Map<String, Integer> out = new TreeMap<>();
        for (final ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                out.merge(idOf(stack), stack.getCount(), Integer::sum);
            }
        }
        return out;
    }

    private static int count(final Map<String, Integer> counts, final String id) {
        return counts.getOrDefault(id, 0);
    }

    private static List<ItemStack> stacksOf(final IItemHandler handler) {
        final List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    private List<ItemStack> hutStacks() {
        final List<ItemStack> out = new ArrayList<>();
        for (final Map.Entry<IItemHandler, List<Integer>> entry : InventoryUtils.findAllSlotsInProviderWith(building, stack -> !stack.isEmpty()).entrySet()) {
            for (final Integer slot : entry.getValue()) {
                final ItemStack stack = entry.getKey().getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
        return out;
    }

    /** The backpack and what is worn. */
    private List<ItemStack> packStacks() {
        final InventoryCitizen inv = citizen.getInventory();
        final List<ItemStack> out = stacksOf(inv);
        for (final EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            final ItemStack worn = inv.getArmorInSlot(slot);
            if (!worn.isEmpty()) {
                out.add(worn);
            }
        }
        return out;
    }

    private List<ItemStack> hutAndPack() {
        final List<ItemStack> out = new ArrayList<>(hutStacks());
        out.addAll(packStacks());
        return out;
    }

    private List<ItemStack> graveStacks() {
        final List<ItemStack> out = new ArrayList<>();
        for (final BlockPos pos : newGraves()) {
            if (level.getBlockEntity(pos) instanceof AbstractTileEntityGrave grave) {
                out.addAll(stacksOf(grave.getInventory()));
            }
        }
        return out;
    }

    private List<ItemStack> looseStacks() {
        final List<ItemStack> out = new ArrayList<>();
        for (final ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(building.getPosition()).inflate(16.0))) {
            if (item.isAlive()) {
                out.add(item.getItem());
            }
        }
        return out;
    }

    private Set<BlockPos> newGraves() {
        final Set<BlockPos> out = new HashSet<>(colony.getGraveManager().getGraves().keySet());
        out.removeAll(gravesBefore);
        return out;
    }

    /** A Departure Point object that is not in the colony, to read a building tag into (as a reload would). */
    private BuildingVoyager blankCopy() {
        final BuildingVoyager copy = new BuildingVoyager(colony, building.getPosition());
        copy.setBuildingType(building.getBuildingType());
        return copy;
    }

    private Optional<AbstractEntityCitizen> liveEntity() {
        if (citizen == null) {
            return Optional.empty();
        }
        final Optional<AbstractEntityCitizen> entity = citizen.getEntity();
        return entity.filter(e -> e.isAlive() && !e.isRemoved());
    }

    private int stat(final String name) {
        final BuildingStatisticsModule stats = building.getFirstModuleOccurance(BuildingStatisticsModule.class);
        return stats == null ? -1 : stats.getBuildingStatisticsManager().getStatTotal(name);
    }

    private boolean isTrophy(final ItemStack stack) {
        return stack.is(Voyager.SOUVENIR_ITEM.get()) || stack.is(Voyager.END_MAP.get()) || stack.is(Items.ENCHANTED_BOOK);
    }

    private DamageSource endDamage() {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(END_DAMAGE));
    }

    // ---------------------------------------------------------------- log expectations

    private void expect(final String what, final Pattern pattern, final int from, final Consumer<Matcher> then) {
        pending.add(new Pending(what, pattern, from, tick + LINE_GRACE, then));
    }

    private void resolvePending() {
        if (pending.isEmpty()) {
            return;
        }
        for (final Pending p : new ArrayList<>(pending)) {
            final Matcher m = logs.find(p.pattern(), p.from());
            if (m != null) {
                pending.remove(p);
                p.then().accept(m);
            } else if (tick > p.deadline()) {
                pending.remove(p);
                check(p.what(), false, "the line /" + p.pattern().pattern() + "/ did not show up within " + LINE_GRACE + " ticks");
            }
        }
    }

    // ---------------------------------------------------------------- the observer

    private void observe() {
        final boolean alive = colony.getCitizenManager().getCivilian(citizen.getId()) != null;
        final boolean away = job.isAway();
        final boolean travelling = colony.getTravellingManager().isTravelling(citizen);
        final Optional<AbstractEntityCitizen> entity = liveEntity();
        final boolean present = entity.isPresent();
        final int entityId = present ? entity.get().getId() : -1;
        final boolean rocketAway = building.isRocketAway();
        final boolean drivenBack = job.isDrivenBack();
        final boolean trophiesRolled = job.isTrophiesRolled();

        if (job.getStatus() == JobVoyager.Status.BOARDING) {
            sawBoarding = true;
        }
        if (!alive && !dead) {
            onDeath();
        }
        if (away && !wasAway && !departed) {
            onDepart();
        }
        if (travelling && !wasTravelling) {
            onTravelStart();
        }
        if (!travelling && wasTravelling) {
            onTravelEnd();
        }
        if (travelling && present) {
            sawEntityWhileTravelling = true;
        }
        if (present && entityId != lastEntityId) {
            onNewEntity(entity.get());
        }
        if (away && present && !travelling && entity.get().isInvisible()) {
            sawInvisibleAway = true;
        }
        if (rocketAway && !wasRocketAway) {
            onLiftOff();
        }
        if (!rocketAway && wasRocketAway) {
            onLanding();
        }
        if (trophiesRolled && !wasTrophiesRolled && away && !drivenBack && alive) {
            onTrophiesRolled();
        }
        if (drivenBack && !wasDrivenBack) {
            onDrivenBack(entity);
        }
        if (respawnWanted && !present && tick > discardTick) {
            respawnWanted = false;
            citizen.updateEntityIfNecessary();
            say("asked for the Voyager's entity back after taking it away: present " + liveEntity().isPresent());
        }
        if (!drivenBack && wasDrivenBack) {
            onDrivenBackCleared(entity);
        }
        if (!away && wasAway && alive) {
            onHome(entity);
        }

        wasAway = away;
        wasTravelling = travelling;
        wasRocketAway = rocketAway;
        wasDrivenBack = drivenBack;
        wasTrophiesRolled = trophiesRolled;
        if (present) {
            lastEntityId = entityId;
        }
    }

    private void progress() {
        switch (phase) {
            case WAIT_DEPART -> {
                if (departed) {
                    enter(Phase.AWAY);
                } else {
                    overBudget(BUDGET_DEPART);
                }
            }
            case AWAY -> {
                if (dead) {
                    enter(Phase.WAIT_LOST);
                } else if (home) {
                    enter(Phase.WAIT_UNLOAD);
                } else if (job.isDrivenBack() || job.isTrophiesRolled()) {
                    enter(Phase.WAIT_HOME);
                } else {
                    overBudget((expectedTicksAway > 0 ? expectedTicksAway : BUDGET_DEPART) + AWAY_SLACK);
                }
            }
            case WAIT_HOME -> {
                if (dead) {
                    enter(Phase.WAIT_LOST);
                } else if (home) {
                    enter(Phase.WAIT_UNLOAD);
                } else {
                    overBudget(BUDGET_HOME);
                }
            }
            case WAIT_UNLOAD -> {
                if (tick - phaseStart >= 100 && (mode == Mode.WOUNDED ? tick - phaseStart >= 200 : nothingLeftToUnload())) {
                    enter(mode == Mode.LOOP ? Phase.ABSORB : Phase.VERIFY);
                } else {
                    overBudget(BUDGET_UNLOAD);
                }
            }
            case WAIT_LOST -> {
                if (landed && landingTick > deathTick && tick - deathTick >= 100 && pending.isEmpty()) {
                    enter(Phase.VERIFY);
                } else {
                    overBudget(BUDGET_ROCKET_AFTER_DEATH);
                }
            }
            default -> {
            }
        }
    }

    /** The ids the hut keeps in a Voyager's pack on purpose: the plan's supplies (see BuildingVoyager). */
    private Set<String> planInputs() {
        final Set<String> out = new TreeSet<>();
        for (final ItemStorage input : plan.getInput()) {
            out.add(BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
        }
        return out;
    }

    /**
     * Food a worker may keep in the pack while unloading: MineColonies keeps some food (building level x 2
     * items) in every worker's inventory, so a chorus fruit from the End can stay there.
     */
    private static boolean isFood(final String id) {
        final Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return new ItemStack(item).getFoodProperties(null) != null;
    }

    /** What the trip added that is still in the pack (the plan's supplies and food may stay there). */
    private Map<String, Integer> gainedStillInPack() {
        final Map<String, Integer> now = counts(hutAndPack());
        final Map<String, Integer> pack = counts(packStacks());
        final Set<String> inputs = planInputs();
        final Map<String, Integer> out = new TreeMap<>();
        for (final Map.Entry<String, Integer> e : now.entrySet()) {
            if (e.getValue() > count(departCounts, e.getKey()) && !inputs.contains(e.getKey()) && !isFood(e.getKey())
                    && count(pack, e.getKey()) > 0) {
                out.put(e.getKey(), count(pack, e.getKey()));
            }
        }
        return out;
    }

    private boolean nothingLeftToUnload() {
        return gainedStillInPack().isEmpty();
    }

    // ---------------------------------------------------------------- edges

    private void onDepart() {
        departed = true;
        departTick = tick;
        findsAtDepart = job.getFinds().size();
        expeditionAtDepart = job.getExpedition();
        soFarAtDepart = building.expeditionsSoFar();
        departCounts = counts(hutAndPack());
        say("departed at t=" + tick + ": " + findsAtDepart + " finds on the itinerary, expedition " + expeditionAtDepart
                + ", hut and pack " + departCounts);
        check("boarding", sawBoarding, "status BOARDING seen before the departure");
        for (final Map.Entry<ResourceLocation, Double> effect : research().entrySet()) {
            checkEq("research at departure " + effect.getKey().getPath(), VoyagerResearch.strength(colony, effect.getKey()), effect.getValue());
        }
        checkEq("expedition number on the job", expeditionAtDepart, 1);
        checkEq("expeditions so far on the building", soFarAtDepart, 1);
        logs.poll();
        expect("boarding line", Pattern.compile(Pattern.quote("all set - heading for the Launchpad")), 0, m -> check("boarding line", true, m.group()));
        expect("departure line", DEPARTS, 0, this::onDepartureLine);
    }

    private void onDepartureLine(final Matcher m) {
        departureLineSeen = true;
        final String look = m.group(1);
        final int expedition = Integer.parseInt(m.group(2));
        final String roll = m.group(3);
        final int finds = Integer.parseInt(m.group(4));
        final int seconds = Integer.parseInt(m.group(5));
        checkEq("departure line: look", look, "Launchpad");
        checkEq("departure line: expedition", expedition, 1);
        checkEq("departure line: finds match the itinerary", finds, Math.max(1, findsAtDepart));
        final double refit = VoyagerResearch.strength(colony, VoyagerResearch.RAPID_REFIT);
        expectedTicksAway = ExpeditionRules.ticksAway(finds, refit, VoyagerConfig.tripLengthMultiplier());
        checkEq("departure line: time away", seconds, expectedTicksAway / 20);
        if (mode == Mode.LOOP) {
            final int want = ExpeditionRules.ticksAway(finds, RAPID_REFIT, SEED_LENGTH) / 20;
            final int without = ExpeditionRules.ticksAway(finds, 0.0, SEED_LENGTH) / 20;
            check("rapid refit", finds >= 2 && seconds == want && seconds < without,
                    finds < 2 ? "only " + finds + " find(s): the floor hides the refit" :
                            seconds + " s with Rapid Refit " + RAPID_REFIT + ", formula " + want + " s, " + without + " s without it");
        }
        if (mode != Mode.LOOP) {
            check("dragon on the itinerary", roll.contains("fight vs " + DRAGON), "expedition roll: " + roll);
        }
    }

    private void onTravelStart() {
        travelStarted = true;
        say("travelling from t=" + tick);
        if (mode == Mode.LOST) {
            // the deadly setup: nothing to fight with, nothing to wear, nothing to eat - and a marker
            final InventoryCitizen inv = citizen.getInventory();
            final List<String> removed = new ArrayList<>();
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                final ItemStack stack = inv.getStackInSlot(slot);
                if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof ArmorItem || stack.getFoodProperties(null) != null) {
                    removed.add(stack.getCount() + "x " + idOf(stack));
                    inv.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
            for (final EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                final ItemStack worn = inv.getArmorInSlot(slot);
                if (!worn.isEmpty()) {
                    removed.add(idOf(worn) + " (worn)");
                    inv.forceClearArmorInSlot(slot, worn);
                }
            }
            final boolean gold = InventoryUtils.addItemStackToItemHandler(inv, new ItemStack(Items.GOLD_INGOT, GOLD));
            final Map<String, Integer> left = counts(packStacks());
            final boolean clean = packStacks().stream().noneMatch(s -> s.getItem() instanceof SwordItem || s.getItem() instanceof ArmorItem
                    || s.getFoodProperties(null) != null);
            check("stripped before the respawn", clean && gold, "removed " + removed + ", pack now " + left);
        } else if (mode == Mode.WOUNDED) {
            // deadly enough with the gear still on: no rations, and half health once back in the world.
            // (With rations a Voyager in iron armour patches up between fights and beats even the dragon.)
            final InventoryCitizen inv = citizen.getInventory();
            int food = 0;
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                final ItemStack stack = inv.getStackInSlot(slot);
                if (stack.getFoodProperties(null) != null) {
                    food += stack.getCount();
                    inv.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
            final boolean clean = packStacks().stream().noneMatch(st -> st.getFoodProperties(null) != null);
            check("rations taken before the respawn", clean, food + " ration(s) taken, the gear stays: " + counts(packStacks()));
        } else if (mode == Mode.DRAGON) {
            final boolean gold = InventoryUtils.addItemStackToItemHandler(citizen.getInventory(), new ItemStack(Items.GOLD_INGOT, GOLD));
            check("gold marker", gold, GOLD + " gold ingots in the pack while travelling");
        }
    }

    private void onTravelEnd() {
        travelEnded = true;
        travelEndTick = tick;
        final int took = tick - departTick;
        say("travel time up at t=" + tick + " (" + took + " ticks after the departure)");
        if (expectedTicksAway > 0) {
            check("not back early", took >= expectedTicksAway - 20, took + " ticks away, the trip takes " + expectedTicksAway);
        } else {
            check("not back early", false, "the departure line was not read before the travel ended");
        }
    }

    private void onNewEntity(final AbstractEntityCitizen entity) {
        say("entity " + entity.getId() + " (was " + lastEntityId + ") at t=" + tick + ": " + (entity.isInvisible() ? "invisible" : "visible")
                + ", " + entity.getHealth() + "/" + entity.getMaxHealth() + " hp, away " + job.isAway());
        if (departed && travelEnded && travelEntityId < 0 && job.isAway()) {
            travelEntityId = entity.getId();
            if ((mode == Mode.WOUNDED || mode == Mode.DRAGON) && !healthSetAfterTravel) {
                // half health before the first fight: in the dragon mode the rations have to be eaten to
                // patch up; in the wounded mode there are none left, which makes the trip a deadly one
                healthSetAfterTravel = true;
                entity.setHealth(entity.getMaxHealth() / 2.0f);
                say("respawned after the travel: health set to " + entity.getHealth() + " of " + entity.getMaxHealth());
            }
        }
        if (discarded && entity.getId() != discardedId && respawnedId < 0) {
            respawnedId = entity.getId();
            say("the Voyager is back as entity " + respawnedId + " (" + entity.getHealth() + "/" + entity.getMaxHealth() + " hp)");
        }
    }

    private void onLiftOff() {
        liftedOff = true;
        liftOffTick = tick;
        int air = 0;
        for (final BlockPos pos : rocketCells.keySet()) {
            if (level.getBlockState(pos).isAir()) {
                air++;
            }
        }
        check("lift-off: rocket cells empty", air == rocketSolid, air + " of " + rocketSolid + " rocket cells are air at t=" + tick);
        final ListTag stash = building.serializeNBT(level.registryAccess()).getList("rocket_stash", Tag.TAG_COMPOUND);
        stowedCells.clear();
        for (int i = 0; i < stash.size(); i++) {
            final CompoundTag entry = stash.getCompound(i);
            stowedCells.put(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")),
                    NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("state")));
        }
        check("lift-off: stash in the building tag", stash.size() == rocketSolid && stowedCells.keySet().equals(rocketCells.keySet()),
                stash.size() + " stowed blocks, the rocket's own cells " + stowedCells.keySet().equals(rocketCells.keySet()));
        // a reload while the rocket is away: the stash comes back from the tag
        try {
            final BuildingVoyager copy = blankCopy();
            copy.deserializeNBT(level.registryAccess(), building.serializeNBT(level.registryAccess()));
            check("lift-off: stash through NBT", copy.isRocketAway()
                            && copy.serializeNBT(level.registryAccess()).getList("rocket_stash", Tag.TAG_COMPOUND).equals(stash),
                    "rocket away after the round trip " + copy.isRocketAway());
        } catch (final Throwable t) {
            check("lift-off: stash through NBT", false, simpleName(t) + ": " + t.getMessage());
        }
        expect("lift-off line", LIFTED, 0, m -> checkEq("lift-off line: blocks stowed", Integer.parseInt(m.group(1)), rocketSolid));
    }

    private void onLanding() {
        landed = true;
        landingTick = tick;
        int same = 0;
        final List<String> wrong = new ArrayList<>();
        final Map<BlockPos, BlockState> wanted = stowedCells.isEmpty() ? rocketCells : stowedCells;
        for (final Map.Entry<BlockPos, BlockState> cell : wanted.entrySet()) {
            if (level.getBlockState(cell.getKey()) == cell.getValue()) {
                same++;
            } else if (wrong.size() < 5) {
                wrong.add(cell.getKey().toShortString() + " " + level.getBlockState(cell.getKey()));
            }
        }
        check("landing: the rocket is back as it was at lift-off", same == rocketSolid && !building.isRocketAway(),
                same + " of " + rocketSolid + " cells as before at t=" + tick + (wrong.isEmpty() ? "" : ", differ: " + wrong));
        expect("landing line", LANDED, 0, m -> checkEq("landing line: blocks restored", Integer.parseInt(m.group(1)), rocketSolid));
    }

    private void onTrophiesRolled() {
        logs.poll();
        // the fights of this trip as the log tells them, and as the job tallied them
        final Map<String, Integer> beaten = new TreeMap<>();
        for (final Matcher m : logs.findAll(BEAT)) {
            beaten.merge(m.group(1), 1, Integer::sum);
        }
        tallyAtRoll = tally();
        checkEq("tally matches the fights in the log", tallyAtRoll, beaten);
        jobRoundTrip("away");
        say("trophies rolled at t=" + tick + "; the trip is a success, tally " + tallyAtRoll);
    }

    private Map<String, Integer> tally() {
        final Map<String, Integer> out = new TreeMap<>();
        for (final TripStory.Fight fight : job.getTally().fights()) {
            out.put(fight.mob().getPath(), fight.count());
        }
        return out;
    }

    /** The job through its NBT and back: every field 0.4.0 added must come back the same. */
    private void jobRoundTrip(final String when) {
        final HolderLookup.Provider provider = level.registryAccess();
        final CompoundTag tag = job.serializeNBT(provider);
        // a job without a citizen: the copy must not open a request-system store of its own
        final JobVoyager copy = new JobVoyager(null);
        copy.deserializeNBT(provider, tag);
        final String got = copy.isAway() + "/" + copy.isDrivenBack() + "/" + copy.getExpedition() + "/" + copy.getTally().save()
                + "/" + copy.isTrophiesRolled() + "/" + copy.getFinds().size() + "/" + copy.getHaul().size();
        final String want = job.isAway() + "/" + job.isDrivenBack() + "/" + job.getExpedition() + "/" + job.getTally().save()
                + "/" + job.isTrophiesRolled() + "/" + job.getFinds().size() + "/" + job.getHaul().size();
        check("job NBT round trip (" + when + ")", got.equals(want), "away/drivenBack/expedition/tally/trophiesRolled/finds/haul " + got);
    }

    private void onDrivenBack(final Optional<AbstractEntityCitizen> entity) {
        drivenBackTick = tick;
        say("driven back at t=" + tick + (entity.isPresent() ? ", " + entity.get().getHealth() + "/" + entity.get().getMaxHealth() + " hp" : ""));
        if (mode != Mode.WOUNDED) {
            check("driven back", false, "only the wounded mode turns permanent death off");
            return;
        }
        jobRoundTrip("driven back");
        if (entity.isPresent()) {
            final float wounded = ExpeditionRules.woundedHealth(entity.get().getMaxHealth());
            check("driven back: wounded", Math.abs(entity.get().getHealth() - wounded) < 0.01f,
                    entity.get().getHealth() + " of " + entity.get().getMaxHealth() + " hp, the wounded value is " + wounded);
        }
        check("driven back: job state", job.isDrivenBack() && job.isTrophiesRolled() && job.getFinds().isEmpty() && job.getHaul().isEmpty(),
                "drivenBack " + job.isDrivenBack() + ", trophiesRolled " + job.isTrophiesRolled() + ", finds " + job.getFinds().size()
                        + ", haul " + job.getHaul().size());
        if (entity.isPresent()) {
            // a reload on the way home: the entity goes, and comes back at full health
            discarded = true;
            discardTick = tick;
            discardedId = entity.get().getId();
            entity.get().remove(Entity.RemovalReason.DISCARDED);
            respawnWanted = true;
            say("took entity " + discardedId + " away after the retreat, as a reload would");
        }
    }

    private void onDrivenBackCleared(final Optional<AbstractEntityCitizen> entity) {
        if (entity.isEmpty()) {
            check("home wounded: health", false, "no entity when the retreat was over");
            return;
        }
        final float h = entity.get().getHealth();
        final float max = entity.get().getMaxHealth();
        final float wounded = ExpeditionRules.woundedHealth(max);
        healthAtHomeEdge = h;
        maxHealthAtHome = max;
        // natural regeneration works on the way home (about 1 hp in 100 ticks), as for any hurt citizen
        check("home wounded: health", h >= wounded && h < max,
                h + " of " + max + " hp, wounded value " + wounded + ", " + (tick - drivenBackTick) + " ticks after the retreat");
    }

    private void onHome(final Optional<AbstractEntityCitizen> entity) {
        home = true;
        homeTick = tick;
        final double dist = entity.isPresent() ? Math.sqrt(entity.get().blockPosition().distSqr(building.getPosition())) : -1;
        check("home: back visible by the hut", entity.isPresent() && !entity.get().isInvisible() && dist <= 16.0,
                entity.isEmpty() ? "no entity" : (entity.get().isInvisible() ? "invisible" : "visible") + ", " + (int) dist + " blocks from the hut at t=" + tick);
        checkEq("home: trips completed", stat(BuildingVoyager.STAT_TRIPS_COMPLETED), mode == Mode.WOUNDED ? 0 : 1);
    }

    private void onDeath() {
        dead = true;
        deathTick = tick;
        say("the Voyager is no longer a citizen of the colony at t=" + tick);
        if (mode == Mode.LOOP || mode == Mode.WOUNDED) {
            check("survived", false, "the Voyager died in mode " + modeWord);
        }
    }

    // ---------------------------------------------------------------- the absorb fallback (loop)

    private int absorbWait = -1;

    /**
     * A blow hurt() refused is worked out by hand: armour, then the protection enchantments. MineColonies'
     * own helper does that through NeoForge's damage container, which only exists inside hurt(); with a
     * Protection chestplate worn it peeks an empty stack. The chestplate is worn for a few ticks (the armour
     * attribute follows the slot on the entity's tick), then both are asked about the same blow.
     */
    private void absorbStep() {
        final Optional<AbstractEntityCitizen> entity = liveEntity();
        if (entity.isEmpty()) {
            check("absorb fallback", false, "no entity");
            enter(Phase.VERIFY);
            return;
        }
        final InventoryCitizen inv = citizen.getInventory();
        if (absorbWait < 0) {
            int slot = -1;
            for (int i = 0; i < inv.getSlots(); i++) {
                if (inv.getStackInSlot(i).is(Items.DIAMOND_CHESTPLATE)) {
                    slot = i;
                    break;
                }
            }
            if (slot >= 0) {
                inv.transferArmorToSlot(EquipmentSlot.CHEST, slot);
            }
            absorbWait = tick;
            say("absorb check: chestplate " + (slot >= 0 ? "worn from slot " + slot : "not in the pack") + ", now " + idOf(inv.getArmorInSlot(EquipmentSlot.CHEST)));
            return;
        }
        if (tick - absorbWait < 10) {
            return;
        }
        final AbstractEntityCitizen worker = entity.get();
        final DamageSource source = endDamage();
        final float blow = 10.0f;
        final float protection = EnchantmentHelper.getDamageProtection(level, worker, source);
        String own;
        try {
            own = "gives " + worker.calculateDamageAfterAbsorbs(source, blow);
        } catch (final Throwable t) {
            own = "throws " + simpleName(t);
        }
        say("absorb check: MineColonies' calculateDamageAfterAbsorbs outside hurt(), Protection " + protection + " worn: " + own);
        final float armourOnly = CombatRules.getDamageAfterAbsorb(worker, blow, source, worker.getArmorValue(),
                (float) worker.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        final float want = CombatRules.getDamageAfterMagicAbsorb(armourOnly, protection);
        try {
            final float got = EntityAIWorkVoyager.afterAbsorbs(worker, source, blow);
            check("absorb fallback", protection > 0.0f && Math.abs(got - want) < 1.0e-4f && got < armourOnly,
                    "a " + blow + " hp blow: " + got + " hp (armour " + worker.getArmorValue() + " alone " + armourOnly + ", with protection " + want + ")");
        } catch (final Throwable t) {
            check("absorb fallback", false, "afterAbsorbs throws " + simpleName(t) + ": " + t.getMessage());
        }
        inv.moveArmorToInventory(EquipmentSlot.CHEST);
        enter(Phase.VERIFY);
    }

    // ---------------------------------------------------------------- verify

    private void verify() {
        say("verify at t=" + tick + ", hut " + counts(hutStacks()) + ", pack " + counts(packStacks()));
        check("departure line read", departureLineSeen, "");
        switch (mode) {
            case LOOP -> verifyLoop();
            case DRAGON -> verifyDragon();
            case LOST -> verifyLost();
            case WOUNDED -> verifyWounded();
        }
        if (!pending.isEmpty()) {
            for (final Pending p : pending) {
                check(p.what(), false, "the line /" + p.pattern().pattern() + "/ never showed up");
            }
            pending.clear();
        }
    }

    private void verifyAwayAndBack() {
        check("away: no entity while travelling", travelStarted && !sawEntityWhileTravelling, "travelling seen " + travelStarted);
        check("away: back in the world out of sight", sawInvisibleAway, "present and invisible while away after the travel");
        check("rocket lifted off and landed", liftedOff && landed && landingTick > liftOffTick, "lift-off t=" + liftOffTick + ", landing t=" + landingTick);
    }

    private void verifyUnloaded() {
        final Map<String, Integer> now = counts(hutAndPack());
        final Map<String, Integer> hut = counts(hutStacks());
        final Set<String> inputs = planInputs();
        final List<String> missing = new ArrayList<>();
        final Map<String, Integer> gained = new TreeMap<>();
        for (final Map.Entry<String, Integer> e : now.entrySet()) {
            final int more = e.getValue() - count(departCounts, e.getKey());
            if (more > 0) {
                gained.put(e.getKey(), more);
                if (count(hut, e.getKey()) == 0 && !inputs.contains(e.getKey()) && !isFood(e.getKey())) {
                    missing.add(e.getKey());
                }
            }
        }
        final Map<String, Integer> inPack = gainedStillInPack();
        check("unloaded into the hut", !gained.isEmpty() && missing.isEmpty() && inPack.isEmpty(),
                "gained " + gained + (missing.isEmpty() ? "" : ", not in the hut " + missing)
                        + (inPack.isEmpty() ? "" : ", still in the pack " + inPack) + " (the plan's supplies " + planInputs() + " and food may stay in the pack)");
    }

    private void verifyLoop() {
        verifyAwayAndBack();
        check("home", home, "home at t=" + homeTick);
        verifyUnloaded();
        checkEq("trips completed", stat(BuildingVoyager.STAT_TRIPS_COMPLETED), 1);
        checkEq("deaths", stat("voyager_deaths"), 0);

        final List<ItemStack> hut = hutStacks();
        final Map<String, Integer> tallyNow = tally();
        final List<ItemStack> souvenirs = hut.stream().filter(s -> s.is(Voyager.SOUVENIR_ITEM.get())).toList();
        final List<ItemStack> books = hut.stream().filter(s -> s.is(Items.ENCHANTED_BOOK)).toList();
        final List<ItemStack> maps = hut.stream().filter(s -> s.is(Voyager.END_MAP.get())).toList();
        checkEq("one souvenir in the hut", souvenirs.size(), 1);
        if (souvenirs.size() == 1) {
            final ItemStack souvenir = souvenirs.get(0);
            final TripStory story = souvenir.get(Voyager.TRIP_STORY.get());
            check("souvenir story", story != null && story.voyager().equals(citizen.getName()) && story.expedition() == 1
                            && TripStory.LOOK_LAUNCHPAD.equals(story.look()) && !EndPlace.DRAGONS_WAKE.id().equals(story.place())
                            && EndPlace.byId(story.place()) != null && fights(story).equals(tallyNow),
                    story == null ? "no story" : "place " + story.place() + ", by " + story.voyager() + ", expedition " + story.expedition()
                            + ", look " + story.look() + ", day " + story.day() + ", fights " + fights(story) + " (tally " + tallyNow + ")");
            check("souvenir name", souvenir.has(DataComponents.ITEM_NAME), souvenir.getHoverName().getString());
        }
        checkEq("one enchanted book in the hut", books.size(), 1);
        if (books.size() == 1) {
            final ItemEnchantments stored = books.get(0).getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            final List<String> ids = new ArrayList<>();
            stored.keySet().forEach(h -> ids.add(h.unwrapKey().map(k -> k.location().toString()).orElse("?")));
            check("book from the list, never Mending", stored.size() == 1 && VoyagerConfig.bookEnchantments().containsAll(ids)
                    && !ids.contains("minecraft:mending"), "stored " + ids);
        }
        checkEq("one map of the End in the hut", maps.size(), 1);
        if (maps.size() == 1) {
            final ItemStack map = maps.get(0);
            final TripStory story = map.get(Voyager.TRIP_STORY.get());
            check("map of the End", story != null && story.expedition() == 1 && !map.has(DataComponents.MAP_ID),
                    story == null ? "no story" : "story place " + story.place() + ", map id " + map.has(DataComponents.MAP_ID));
        }
        expeditionLogTrophies(1, 1, 1);
        if (tallyAtRoll != null) {
            checkEq("tally kept until home", tallyNow, tallyAtRoll);
        }

        jobRoundTrip("home");
        final CompoundTag tag = building.serializeNBT(level.registryAccess());
        check("building tag after home", tag.getInt("expeditions") == 1 && tag.getList("rocket_stash", Tag.TAG_COMPOUND).isEmpty(),
                "expeditions " + tag.getInt("expeditions") + ", rocket stash " + tag.getList("rocket_stash", Tag.TAG_COMPOUND).size());
        // the counter through the tag, on a copy (a live building cannot read a tag twice: MineColonies'
        // Expedition Log keeps its gear list immutable once written)
        try {
            final BuildingVoyager copy = blankCopy();
            copy.deserializeNBT(level.registryAccess(), tag);
            final int readBack = copy.expeditionsSoFar();
            final int next = copy.nextExpeditionNumber();
            final BuildingVoyager again = blankCopy();
            again.deserializeNBT(level.registryAccess(), copy.serializeNBT(level.registryAccess()));
            check("building NBT round trip: expedition counter", readBack == 1 && next == 2 && again.expeditionsSoFar() == 2,
                    "read back " + readBack + ", next number " + next + ", after a second round trip " + again.expeditionsSoFar());
        } catch (final Throwable t) {
            check("building NBT round trip: expedition counter", false, simpleName(t) + ": " + t.getMessage());
        }
    }

    private Map<String, Integer> fights(final TripStory story) {
        final Map<String, Integer> out = new TreeMap<>();
        for (final TripStory.Fight fight : story.fights()) {
            out.put(fight.mob().getPath(), fight.count());
        }
        return out;
    }

    /** The Expedition Log lists exactly the trophies that came home. */
    private void expeditionLogTrophies(final int souvenirs, final int books, final int maps) {
        int s = 0, b = 0, m = 0;
        for (final ItemStorage loot : building.getFirstModuleOccurance(ExpeditionLogModule.class).getLog().getLoot()) {
            final ItemStack stack = loot.getItemStack();
            if (stack.is(Voyager.SOUVENIR_ITEM.get())) {
                s += loot.getAmount();
            } else if (stack.is(Items.ENCHANTED_BOOK)) {
                b += loot.getAmount();
            } else if (stack.is(Voyager.END_MAP.get())) {
                m += loot.getAmount();
            }
        }
        checkEq("expedition log lists the trophies (souvenir/book/map)", s + "/" + b + "/" + m, souvenirs + "/" + books + "/" + maps);
    }

    /** Rations: back in the world at half health, the Voyager eats from the pack before the first fight. */
    private void verifyRationsEaten() {
        final int firstAte = logs.indexOf("ration(s) and patched up", 0);
        int firstFight = Integer.MAX_VALUE;
        for (final String needle : new String[] {"beat a ", "turns back from a ", "was driven back by a ", "was lost in the End"}) {
            final int at = logs.indexOf(needle, 0);
            if (at >= 0) {
                firstFight = Math.min(firstFight, at);
            }
        }
        check("rations eaten before the first fight", healthSetAfterTravel && firstAte >= 0 && firstAte < firstFight,
                firstAte < 0 ? "no 'ate N ration(s)' line" : logs.lines().get(firstAte));
    }

    private void verifyDragon() {
        verifyRationsEaten();
        check("dragon line", logs.any("has the dragon on the itinerary"), "has the dragon on the itinerary");
        check("dragon fight line", logs.any("faces the dragon!"), "faces the dragon!");
        final boolean won = logs.any("beat a " + DRAGON + " in");
        if (dead || !won) {
            say("dragon branch: lost (dead " + dead + ", won line " + won + ")");
            check("dragon branch", dead, dead ? "lost" : "neither a win nor a loss");
            if (dead) {
                verifyLostChecks();
            }
            check("branch", dead, "lost");
            return;
        }
        verifyAwayAndBack();
        check("home", home, "home at t=" + homeTick);
        verifyUnloaded();
        final Map<String, Integer> gained = new TreeMap<>();
        final Map<String, Integer> now = counts(hutAndPack());
        for (final String id : List.of("minecraft:dragon_head", "minecraft:dragon_breath")) {
            gained.put(id, count(now, id) - count(departCounts, id));
        }
        check("dragon's hoard in the hut", gained.get("minecraft:dragon_head") >= 1 && gained.get("minecraft:dragon_breath") >= 3, "gained " + gained);
        final Map<String, Integer> tallyNow = tally();
        check("tally has the dragon", tallyNow.containsKey(DRAGON), "tally " + tallyNow);
        final List<ItemStack> souvenirs = hutStacks().stream().filter(s -> s.is(Voyager.SOUVENIR_ITEM.get())).toList();
        final TripStory story = souvenirs.size() == 1 ? souvenirs.get(0).get(Voyager.TRIP_STORY.get()) : null;
        check("Dragon's Wake souvenir", story != null && EndPlace.DRAGONS_WAKE.id().equals(story.place()) && fights(story).containsKey(DRAGON),
                souvenirs.size() + " souvenir(s)" + (story == null ? "" : ", place " + story.place() + ", fights " + fights(story)
                        + ", named " + souvenirs.get(0).getHoverName().getString()));
        checkEq("trips completed", stat(BuildingVoyager.STAT_TRIPS_COMPLETED), 1);
        expeditionLogTrophies(1, 1, 1);
        check("branch", true, "win");
    }

    private void verifyLost() {
        check("dragon on the itinerary line", logs.any("has the dragon on the itinerary"), "");
        verifyLostChecks();
    }

    /** A Voyager lost in the End: Return to Sender, a grave, the rocket alone, nothing left on the job. */
    private void verifyLostChecks() {
        final Matcher lost = logs.find(LOST_IN_END, 0);
        check("lost line", lost != null && dead, lost == null ? "no 'was lost in the End' line" : lost.group());
        checkEq("stats deaths/retreats/trips", stat("voyager_deaths") + "/" + stat("voyager_retreats") + "/" + stat(BuildingVoyager.STAT_TRIPS_COMPLETED), "1/0/0");
        final Map<String, Integer> hut = counts(hutStacks());
        final Map<String, Integer> pack = counts(packStacks());
        check("return to sender", logs.any("were returned to the") && count(hut, "minecraft:gold_ingot") == GOLD
                        && count(hut, "minecraft:diamond_pickaxe") >= 1 && pack.isEmpty(),
                "line " + logs.any("were returned to the") + ", hut gold " + count(hut, "minecraft:gold_ingot") + ", pickaxe "
                        + count(hut, "minecraft:diamond_pickaxe") + ", pack " + pack);
        check("nothing left on the job", job.getFinds().isEmpty() && job.getHaul().isEmpty(),
                "finds " + job.getFinds().size() + ", haul " + job.getHaul().size());
        final Set<BlockPos> graves = newGraves();
        final boolean noRoom = logs.any("No room for a grave");
        check("grave", graves.size() == 1 || noRoom, graves.size() + " new grave(s) " + graves + ", no-room line " + noRoom);
        for (final BlockPos pos : graves) {
            if (level.getBlockEntity(pos) instanceof AbstractTileEntityGrave grave && grave.getGraveData() != null) {
                // MineColonies takes the job off a citizen who dies (AbstractJob.onRemoval), so the record
                // normally holds none; if it holds one, the trip in it must be over
                final CompoundTag record = grave.getGraveData().getCitizenDataNBT();
                final CompoundTag jobTag = findCompoundWith(record, "away");
                check("grave keeps no trip in progress", record != null && (jobTag == null || !jobTag.getBoolean("away")
                                && jobTag.getList("finds", Tag.TAG_COMPOUND).isEmpty() && jobTag.getList("haul", Tag.TAG_COMPOUND).isEmpty()
                                && jobTag.getBoolean("trophies_rolled") && !jobTag.getBoolean("driven_back")),
                        record == null ? "no citizen record" : jobTag == null ? "the citizen record (" + record.getAllKeys().size()
                                + " keys) holds no job: MineColonies took it on the death" : "a job, away " + jobTag.getBoolean("away") + ", finds "
                                + jobTag.getList("finds", Tag.TAG_COMPOUND).size() + ", haul " + jobTag.getList("haul", Tag.TAG_COMPOUND).size()
                                + ", trophies_rolled " + jobTag.getBoolean("trophies_rolled") + ", driven_back " + jobTag.getBoolean("driven_back"));
            } else {
                check("grave keeps no trip in progress", false, "no grave data at " + pos.toShortString());
            }
        }
        final int gold = count(hut, "minecraft:gold_ingot") + count(counts(graveStacks()), "minecraft:gold_ingot")
                + count(counts(looseStacks()), "minecraft:gold_ingot");
        checkEq("gold: hut + grave + loose", gold, GOLD);
        check("rocket home alone", landed && landingTick > deathTick && landingTick - deathTick <= BUDGET_ROCKET_AFTER_DEATH && !building.isRocketAway()
                        && logs.find(ROCKET_ALONE, 0) != null,
                "death t=" + deathTick + ", landing t=" + landingTick + ", line " + (logs.find(ROCKET_ALONE, 0) != null));
        final List<ItemStack> everywhere = new ArrayList<>(hutStacks());
        everywhere.addAll(packStacks());
        everywhere.addAll(graveStacks());
        everywhere.addAll(looseStacks());
        final long trophies = everywhere.stream().filter(this::isTrophy).count();
        checkEq("no trophies for a lost Voyager", trophies, 0L);
        checkEq("expedition log: no trophies", logs.count("brings something home to remember"), 0);
    }

    private static CompoundTag findCompoundWith(final CompoundTag tag, final String key) {
        if (tag == null) {
            return null;
        }
        if (tag.contains(key)) {
            return tag;
        }
        for (final String name : tag.getAllKeys()) {
            final Tag child = tag.get(name);
            if (child instanceof CompoundTag compound) {
                final CompoundTag found = findCompoundWith(compound, key);
                if (found != null) {
                    return found;
                }
            } else if (child instanceof ListTag list) {
                for (final Tag element : list) {
                    if (element instanceof CompoundTag compound) {
                        final CompoundTag found = findCompoundWith(compound, key);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void verifyWounded() {
        final Matcher driven = logs.find(DRIVEN_BACK, 0);
        check("driven back line", driven != null, driven == null ? "none" : driven.group());
        final int respawnLine = logs.indexOf("was respawned at full health on the way home", 0);
        check("respawn on the way home keeps the wound", discarded && respawnedId >= 0 && respawnedId != discardedId && respawnLine >= 0,
                "entity " + discardedId + " taken away, back as " + respawnedId + (respawnLine >= 0 ? ": " + logs.lines().get(respawnLine) : ", no re-wound line"));
        final Matcher homeLine = logs.find(HOME_WOUNDED, 0);
        check("home wounded line", homeLine != null, homeLine == null ? "none" : homeLine.group());
        check("home wounded: health measured", healthAtHomeEdge != null, healthAtHomeEdge == null ? "the retreat never ended"
                : healthAtHomeEdge + " of " + maxHealthAtHome + " hp");
        checkEq("stats retreats/deaths/trips", stat("voyager_retreats") + "/" + stat("voyager_deaths") + "/" + stat(BuildingVoyager.STAT_TRIPS_COMPLETED), "1/0/0");
        checkEq("no grave", newGraves().size(), 0);
        check("nobody lost, nothing sent back", !logs.any("was lost in the End") && !logs.any("were returned to the"), "");
        final Map<String, Integer> now = counts(hutAndPack());
        final List<String> gearMissing = new ArrayList<>();
        for (final ItemStack given : gearGiven) {
            if (count(now, idOf(given)) < 1) {
                gearMissing.add(idOf(given));
            }
        }
        check("gear kept", gearMissing.isEmpty(), gearMissing.isEmpty() ? "every piece in the pack, worn or in the hut" : "missing " + gearMissing);
        final Map<String, Integer> more = new TreeMap<>();
        for (final Map.Entry<String, Integer> e : now.entrySet()) {
            if (e.getValue() > count(departCounts, e.getKey())) {
                more.put(e.getKey(), e.getValue() - count(departCounts, e.getKey()));
            }
        }
        check("empty-handed", more.isEmpty() && job.getHaul().isEmpty(), "gained " + more + ", haul " + job.getHaul().size());
        final long trophies = hutAndPack().stream().filter(this::isTrophy).count();
        checkEq("no trophies for a retreat", trophies, 0L);
        check("rocket back", landed && !building.isRocketAway(), "landing t=" + landingTick);
        check("still the Voyager", building.getAllAssignedCitizen().contains(citizen) && citizen.getJob() == job, "");
        final Matcher turn = logs.find(TURNS_BACK, 0);
        if (turn != null) {
            say("retreat report: turned back from a " + turn.group(1) + " at " + turn.group(2) + " of " + turn.group(3) + " hp after "
                    + turn.group(4) + " blows; the worst-blow estimate was " + turn.group(5) + " hp (MineColonies caps one blow at "
                    + (Float.parseFloat(turn.group(3)) / 5.0f) + " hp before armour)");
        }
        check("retreat line", turn != null, turn == null ? "no 'turns back from' line" : turn.group());
    }
}
