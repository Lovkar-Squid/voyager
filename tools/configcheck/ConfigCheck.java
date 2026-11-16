package me.lovkar.voyagerconfigcheck;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.core.colony.crafting.CustomRecipe;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.VoyagerConfig;
import me.lovkar.voyager.ai.ExpeditionRules;
import me.lovkar.voyager.colony.TripPlans;
import me.lovkar.voyager.item.ObservatoryHutItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Headless check of Voyager's server config and the rules it feeds, on the dedicated test server.
 *
 * <p>Two runs: without a mode the rig seeds this probe's {@code rigconfig/voyager-server.toml}
 * (a non-default value for every key) and the getters must return those values; with
 * {@code -Mode defaults} nothing is seeded ({@code rigconfig-defaults/} holds no .toml), NeoForge
 * writes the file from the mod's defaults, and the getters must return the defaults of the 0.4.0
 * plan - written out here by hand, so a wrong default in the mod cannot agree with itself. Both runs
 * also check the version, the Observatory's tooltip, the supply multiplier on a sample plan and on
 * the real trip1 plan, the trip length and trips-per-window arithmetic and the death-off decision.
 * Prints "[configcheck] CHECK ..." lines and "[configcheck] RESULT configcheck: PASS|FAIL", then halts.</p>
 */
@Mod("voyagerconfigcheck")
public class ConfigCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger("voyagerconfigcheck");
    private static final List<String> PLAN_BOOK_ENCHANTMENTS = List.of(
            "minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection",
            "minecraft:projectile_protection", "minecraft:feather_falling", "minecraft:respiration",
            "minecraft:depth_strider", "minecraft:unbreaking", "minecraft:efficiency", "minecraft:fortune",
            "minecraft:silk_touch", "minecraft:sharpness", "minecraft:smite", "minecraft:looting",
            "minecraft:power", "minecraft:punch");
    private int ticks = 0;
    private boolean failed = false;

    /** What the getters must return in one run. */
    private record Expected(int trips, int rations, double supply, double length, double dragon, boolean permanentDeath,
                            double souvenir, double book, double map, List<String> enchantments) {
    }

    /** The 0.4.0 plan's defaults: every one of them 0.3.9's behaviour, except the new reward chances. */
    private static final Expected DEFAULTS = new Expected(1, 16, 1.0, 1.0, 0.10, true, 0.10, 0.05, 0.03, PLAN_BOOK_ENCHANTMENTS);
    /** What rigconfig/voyager-server.toml seeds: a non-default value for every key. */
    private static final Expected SEEDED = new Expected(3, 24, 0.5, 2.5, 0.75, false, 0.5, 0.25, 0.2,
            List.of("minecraft:sharpness", "minecraft:unbreaking", "minecraft:silk_touch"));

    public ConfigCheck() {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void check(final String what, final boolean ok, final String detail) {
        LOGGER.info("[configcheck] CHECK {}: {} {}", what, ok ? "OK" : "FAIL", detail);
        failed |= !ok;
    }

    private void onTick(final ServerTickEvent.Post event) {
        if (++ticks != 100) {
            return;
        }
        final MinecraftServer server = event.getServer();
        try {
            final String mode = readMode();
            final boolean defaults = "defaults".equals(mode);
            LOGGER.info("[configcheck] mode '{}': expecting the {}", mode, defaults ? "mod defaults" : "seeded rigconfig values");
            checkConfig(defaults ? DEFAULTS : SEEDED, defaults);
            checkVersionAndLang();
            checkObservatoryTooltip();
            checkSupplies(defaults ? DEFAULTS : SEEDED);
            checkTripLength(defaults ? DEFAULTS : SEEDED);
            checkTripsPerWindow(defaults ? DEFAULTS : SEEDED);
            checkWound(defaults ? DEFAULTS : SEEDED);
        } catch (final Throwable t) {
            check("probe ran to the end", false, t.toString());
        }
        LOGGER.info("[configcheck] RESULT configcheck: {}", failed ? "FAIL" : "PASS");
        server.halt(false);
    }

    /** The mode word the rig wrote next to the server (empty without -Mode). */
    private static String readMode() throws Exception {
        final Path file = Path.of("probe-mode.txt");
        return Files.exists(file) ? Files.readString(file, StandardCharsets.US_ASCII).trim() : "";
    }

    // ---------------------------------------------------------------- the config itself

    private void checkConfig(final Expected want, final boolean defaults) {
        check("config loaded", VoyagerConfig.SPEC.isLoaded(), "voyager-server.toml is the live SERVER config");
        final Path file = Path.of("config", "voyager-server.toml");
        check("config file", Files.exists(file), file.toString());
        checkEq("tripsPerLaunchWindow", VoyagerConfig.tripsPerLaunchWindow(), want.trips());
        checkEq("rationsPerVoyager", VoyagerConfig.rationsPerVoyager(), want.rations());
        checkNear("supplyMultiplier", VoyagerConfig.supplyMultiplier(), want.supply());
        checkNear("tripLengthMultiplier", VoyagerConfig.tripLengthMultiplier(), want.length());
        checkNear("dragonChance", VoyagerConfig.dragonChance(), want.dragon());
        checkEq("permanentDeath", VoyagerConfig.permanentDeath(), want.permanentDeath());
        checkNear("souvenirChance", VoyagerConfig.souvenirChance(), want.souvenir());
        checkNear("endCityBookChance", VoyagerConfig.endCityBookChance(), want.book());
        checkNear("endMapChance", VoyagerConfig.endMapChance(), want.map());
        checkEq("bookEnchantments", VoyagerConfig.bookEnchantments(), want.enchantments());
        if (defaults) {
            final List<String> list = VoyagerConfig.bookEnchantments();
            check("default book list has no Mending", !list.contains("minecraft:mending"), list.size() + " enchantments");
            check("default book list has no curse", list.stream().noneMatch(id -> id.contains("curse")), "");
            // the class constants the README documents must be the same numbers
            check("documented defaults", VoyagerConfig.DEFAULT_TRIPS_PER_WINDOW == 1 && VoyagerConfig.DEFAULT_RATIONS_PER_VOYAGER == 16
                    && VoyagerConfig.DEFAULT_SUPPLY_MULTIPLIER == 1.0 && VoyagerConfig.DEFAULT_TRIP_LENGTH_MULTIPLIER == 1.0
                    && VoyagerConfig.DEFAULT_DRAGON_CHANCE == 0.10 && VoyagerConfig.DEFAULT_PERMANENT_DEATH
                    && VoyagerConfig.DEFAULT_SOUVENIR_CHANCE == 0.10 && VoyagerConfig.DEFAULT_END_CITY_BOOK_CHANCE == 0.05
                    && VoyagerConfig.DEFAULT_END_MAP_CHANCE == 0.03
                    && VoyagerConfig.DEFAULT_BOOK_ENCHANTMENTS.equals(PLAN_BOOK_ENCHANTMENTS), "VoyagerConfig.DEFAULT_*");
        }
    }

    // ---------------------------------------------------------------- version, lang, tooltip

    private void checkVersionAndLang() throws Exception {
        // getVersion() returns a Maven type the probe is not compiled against: ask it by name
        final IModInfo info = ModList.get().getModContainerById(Voyager.MODID).map(c -> c.getModInfo()).orElse(null);
        final String version = info == null ? "?" : String.valueOf(IModInfo.class.getMethod("getVersion").invoke(info));
        check("version", "0.4.0".equals(version), version);
        final Path lang = ModList.get().getModFileById(Voyager.MODID).getFile().findResource("assets", "voyager", "lang", "en_us.json");
        final JsonObject json;
        try (Reader reader = Files.newBufferedReader(lang, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        final String tooltip = json.has(ObservatoryHutItem.TOOLTIP_NEEDS_SPACE) ? json.get(ObservatoryHutItem.TOOLTIP_NEEDS_SPACE).getAsString() : "";
        check("lang Observatory tooltip", tooltip.contains("Exposure: Space"), ObservatoryHutItem.TOOLTIP_NEEDS_SPACE + " = " + tooltip);
        for (final String key : new String[] {"com.voyager.comms.driven_back", "com.minecolonies.coremod.gui.townhall.stats.voyager_retreats"}) {
            check("lang " + key, json.has(key), json.has(key) ? json.get(key).getAsString() : "missing");
        }
    }

    /** The rig has no Exposure: Space, so the hut item must carry the line (the item asks the mod list, nothing else). */
    private void checkObservatoryTooltip() {
        final boolean space = ModList.get().isLoaded(ObservatoryHutItem.EXPOSURE_SPACE);
        check("rig has no Exposure: Space", !space, "the tooltip case under test");
        final Item hut = Voyager.ITEM_HUT_OBSERVATORY.get();
        final List<Component> lines = new ArrayList<>();
        hut.appendHoverText(new ItemStack(hut), Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        final boolean shown = lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents t
                && ObservatoryHutItem.TOOLTIP_NEEDS_SPACE.equals(t.getKey()));
        check("Observatory hut tooltip", hut instanceof ObservatoryHutItem && shown == !space, lines.size() + " line(s), needs-space line " + shown);
        check("Observatory still registered", Voyager.OBSERVATORY.isBound() && Voyager.BLOCK_HUT_OBSERVATORY.isBound(), Voyager.OBSERVATORY_ID.toString());
    }

    // ---------------------------------------------------------------- supplies

    private static IRecipeStorage samplePlan() {
        return RecipeStorage.builder()
                .withInputs(List.of(
                        new ItemStorage(new ItemStack(Items.COBBLESTONE), 64),
                        new ItemStorage(new ItemStack(Items.ENDER_PEARL), 4),
                        new ItemStorage(new ItemStack(Items.TORCH), 16),
                        new ItemStorage(new ItemStack(Items.STRING), 1),
                        new ItemStorage(new ItemStack(Items.FEATHER), 3)))
                .withLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "recipes/voyager/trip1")))
                .build();
    }

    /** The sample's amounts in input order: cobblestone, pearls, torches, string, feathers. */
    private static List<Integer> amounts(final IRecipeStorage plan) {
        final List<Integer> out = new ArrayList<>();
        for (final net.minecraft.world.level.ItemLike item : new net.minecraft.world.level.ItemLike[] {
                Items.COBBLESTONE, Items.ENDER_PEARL, Items.TORCH, Items.STRING, Items.FEATHER}) {
            out.add(TripPlans.amountOf(plan, new ItemStorage(new ItemStack(item))));
        }
        return out;
    }

    private void checkSupplies(final Expected want) {
        final IRecipeStorage plan = samplePlan();
        check("plan x1.0 is the same storage", TripPlans.scaled(plan, 1.0) == plan, "unchanged at the default");
        final IRecipeStorage quarter = TripPlans.scaled(plan, 0.25);
        checkEq("plan x0.25 rounds up, at least 1", amounts(quarter), List.of(16, 1, 4, 1, 1));
        checkEq("plan x1.5 rounds up", amounts(TripPlans.scaled(plan, 1.5)), List.of(96, 6, 24, 2, 5));
        checkEq("plan x4.0", amounts(TripPlans.scaled(plan, 4.0)), List.of(256, 16, 64, 4, 12));
        checkEq("the plan itself is untouched", amounts(plan), List.of(64, 4, 16, 1, 3));
        check("scaled plan keeps its loot table", quarter != plan && Objects.equals(quarter.getLootTable(), plan.getLootTable()),
                String.valueOf(quarter.getLootTable()));
        checkEq("scaledAmount 10 x 0.3 (no float creep)", ExpeditionRules.scaledAmount(10, 0.3), 3);
        // the configured multiplier, as the AI applies it
        final IRecipeStorage configured = TripPlans.scaled(plan, VoyagerConfig.supplyMultiplier());
        if (want.supply() == 1.0) {
            check("configured multiplier leaves the plan as it is", configured == plan, "x" + VoyagerConfig.supplyMultiplier());
        } else {
            checkEq("configured multiplier x" + want.supply(), amounts(configured), List.of(32, 2, 8, 1, 2));
        }
        // the real first expedition plan from the datapack (64 cobblestone, 4 ender pearls, 16 torches)
        IRecipeStorage trip1 = null;
        for (final CustomRecipe recipe : CustomRecipeManager.getInstance().getRecipes("voyager_custom")) {
            if (recipe.getRecipeId().getPath().endsWith("trip1")) {
                trip1 = recipe.getRecipeStorage();
            }
        }
        check("trip1 plan loaded", trip1 != null, "crafter voyager_custom");
        if (trip1 != null) {
            final IRecipeStorage small = TripPlans.scaled(trip1, 0.25);
            final List<Integer> got = List.of(
                    TripPlans.amountOf(small, new ItemStorage(new ItemStack(Items.COBBLESTONE))),
                    TripPlans.amountOf(small, new ItemStorage(new ItemStack(Items.ENDER_PEARL))),
                    TripPlans.amountOf(small, new ItemStorage(new ItemStack(Items.TORCH))));
            checkEq("trip1 x0.25", got, List.of(16, 1, 4));
            final ResourceKey<LootTable> loot = trip1.getLootTable();
            check("trip1 x0.25 keeps the loot roll", Objects.equals(small.getLootTable(), loot), String.valueOf(loot));
        }
    }

    // ---------------------------------------------------------------- time away, trips per window

    private void checkTripLength(final Expected want) {
        checkEq("ticksAway 5 finds", ExpeditionRules.ticksAway(5, 0.0, 1.0), 2000);
        checkEq("ticksAway 5 finds, Rapid Refit", ExpeditionRules.ticksAway(5, 0.25, 1.0), 1500);
        checkEq("ticksAway floor of one find", ExpeditionRules.ticksAway(1, 0.8, 1.0), 400);
        checkEq("ticksAway floor scales x0.5", ExpeditionRules.ticksAway(1, 0.8, 0.5), 200);
        checkEq("ticksAway x2.0", ExpeditionRules.ticksAway(5, 0.0, 2.0), 4000);
        checkEq("ticksAway x0.1 with refit", ExpeditionRules.ticksAway(5, 0.25, 0.1), 150);
        // at 1.0 the 0.3.9 formula to the tick
        boolean same = true;
        for (int finds = 1; finds <= 12; finds++) {
            for (final double refit : new double[] {0.0, 0.25, 0.5, 0.75, 0.8}) {
                same &= ExpeditionRules.ticksAway(finds, refit, 1.0) == (int) Math.max(400, finds * 400 * (1.0 - refit));
            }
        }
        check("ticksAway x1.0 is 0.3.9's", same, "1-12 finds, refit 0-0.8");
        checkEq("ticksAway with the configured multiplier", ExpeditionRules.ticksAway(5, 0.0, VoyagerConfig.tripLengthMultiplier()),
                (int) Math.round(2000 * want.length()));
    }

    private void checkTripsPerWindow(final Expected want) {
        checkEq("trips per window, 0.3.9 base", ExpeditionRules.tripsPerWindow(1, 0, 1), 1);
        checkEq("trips per window, research and crew of 2", ExpeditionRules.tripsPerWindow(1, 2, 2), 6);
        checkEq("trips per window, nobody hired yet", ExpeditionRules.tripsPerWindow(1, 0, 0), 1);
        checkEq("trips per window, configured base + Starlight Navigation, crew of 2",
                ExpeditionRules.tripsPerWindow(VoyagerConfig.tripsPerLaunchWindow(), 1, 2), (want.trips() + 1) * 2);
    }

    // ---------------------------------------------------------------- permanent death off

    private void checkWound(final Expected want) {
        checkNear("wounded health of 20 max", ExpeditionRules.woundedHealth(20.0f), 5.0);
        checkNear("wounded health of 40 max", ExpeditionRules.woundedHealth(40.0f), 10.0);
        checkNear("wounded health of 6 max", ExpeditionRules.woundedHealth(6.0f), 1.5);
        checkNear("wounded health at least 1 hp", ExpeditionRules.woundedHealth(2.0f), 1.0);
        check("blow to exactly 0 hp is fatal", ExpeditionRules.isFatalBlow(5.0f, 5.0f), "5 hp, 5 damage");
        check("blow past 0 hp is fatal", ExpeditionRules.isFatalBlow(5.0f, 7.0f), "5 hp, 7 damage");
        check("blow that leaves a sliver is not fatal", !ExpeditionRules.isFatalBlow(5.0f, 4.9f), "5 hp, 4.9 damage");
        check("permanent death on: never driven back", !ExpeditionRules.drivenBack(true, 3.0f, 7.0f), "3 hp, 7 damage");
        check("permanent death off: a fatal blow drives back", ExpeditionRules.drivenBack(false, 3.0f, 7.0f), "3 hp, 7 damage");
        check("permanent death off: a survivable blow does not", !ExpeditionRules.drivenBack(false, 20.0f, 4.0f), "20 hp, 4 damage");
        check("configured permanentDeath decides", ExpeditionRules.drivenBack(VoyagerConfig.permanentDeath(), 3.0f, 7.0f) == !want.permanentDeath(),
                "permanentDeath = " + VoyagerConfig.permanentDeath());
    }

    // ---------------------------------------------------------------- helpers

    private void checkEq(final String what, final Object got, final Object want) {
        check(what, Objects.equals(got, want), "got " + got + ", want " + want);
    }

    private void checkNear(final String what, final double got, final double want) {
        check(what, Math.abs(got - want) < 1.0e-9, "got " + got + ", want " + want);
    }
}
