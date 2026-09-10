package me.lovkar.voyager;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import com.minecolonies.core.colony.jobs.views.CrafterJobView;
import me.lovkar.voyager.ai.EntityAIWorkVoyager;
import me.lovkar.voyager.block.BlockHutObservatory;
import me.lovkar.voyager.block.BlockHutVoyager;
import me.lovkar.voyager.block.VoyagerTileEntity;
import me.lovkar.voyager.client.VoyagerClient;
import me.lovkar.voyager.ai.EntityAIWorkAstronomer;
import me.lovkar.voyager.colony.BuildingObservatory;
import me.lovkar.voyager.colony.BuildingVoyager;
import me.lovkar.voyager.colony.JobAstronomer;
import me.lovkar.voyager.colony.ObservatoryModules;
import me.lovkar.voyager.colony.JobVoyager;
import me.lovkar.voyager.colony.VoyagerModules;
import me.lovkar.voyager.fx.Effects;
import me.lovkar.voyager.item.SkyPlate;
import me.lovkar.voyager.fx.VoyagerSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Voyager - a MineColonies profession that explores the End.
 *
 * One building ("Departure Point", registry name voyager:voyager) with two looks in the
 * bundled "Voyager" structure pack: the Launchpad and the End Gate. The worker leaves from
 * the block tagged "departure", is away for a while and comes back with End loot - or not.
 *
 * Registration follows the MC Trade Post pattern: our own block-entity type for the hut
 * (MineColonies' own type only accepts MineColonies hut blocks), a hut block that reports
 * its registry name in our namespace, job and building entries through DeferredRegisters
 * on MineColonies' custom registries.
 */
@Mod(Voyager.MODID)
public class Voyager {
    public static final String MODID = "voyager";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final String HUT_NAME = "blockhutvoyager";
    public static final String OBSERVATORY_HUT_NAME = "blockhutobservatory";
    public static final String PHOTOBOOTH_HUT_NAME = "blockhutphotobooth";
    public static final ResourceLocation JOB_ID = ResourceLocation.fromNamespaceAndPath(MODID, "voyager");
    public static final ResourceLocation BUILDING_ID = ResourceLocation.fromNamespaceAndPath(MODID, "voyager");
    /** Citizen model type; the suit textures live under MineColonies' namespace (see VoyagerClient). */
    public static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(MODID, "voyager");
    public static final ResourceLocation ASTRONOMER_JOB_ID = ResourceLocation.fromNamespaceAndPath(MODID, "astronomer");
    public static final ResourceLocation OBSERVATORY_ID = ResourceLocation.fromNamespaceAndPath(MODID, "observatory");
    public static final ResourceLocation PHOTOGRAPHER_JOB_ID = ResourceLocation.fromNamespaceAndPath(MODID, "photographer");
    public static final ResourceLocation PHOTOBOOTH_ID = ResourceLocation.fromNamespaceAndPath(MODID, "photobooth");
    /** The astronomer's own model: a hood, a scarf, a cloak and a satchel of plates. */
    public static final ResourceLocation ASTRONOMER_MODEL_ID =
            ResourceLocation.fromNamespaceAndPath(MODID, "astronomer");
    /** The photographer's model: a cap, a vest, a strap, and the viewfinder pose. */
    public static final ResourceLocation PHOTOGRAPHER_MODEL_ID =
            ResourceLocation.fromNamespaceAndPath(MODID, "photographer");

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<JobEntry> JOBS = DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS, MODID);
    public static final DeferredRegister<BuildingEntry> BUILDINGS =
            DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, MODID);

    public static final DeferredBlock<BlockHutVoyager> BLOCK_HUT = BLOCKS.register(HUT_NAME, BlockHutVoyager::new);
    public static final DeferredItem<Item> ITEM_HUT =
            ITEMS.register(HUT_NAME, () -> new ItemBlockHut(BLOCK_HUT.get(), new Item.Properties()));
    public static final DeferredBlock<BlockHutObservatory> BLOCK_HUT_OBSERVATORY =
            BLOCKS.register(OBSERVATORY_HUT_NAME, BlockHutObservatory::new);
    public static final DeferredItem<Item> ITEM_HUT_OBSERVATORY =
            ITEMS.register(OBSERVATORY_HUT_NAME, () -> new ItemBlockHut(BLOCK_HUT_OBSERVATORY.get(), new Item.Properties()));

    public static final DeferredBlock<me.lovkar.voyager.block.BlockHutPhotoBooth> BLOCK_HUT_PHOTOBOOTH =
            BLOCKS.register(PHOTOBOOTH_HUT_NAME, me.lovkar.voyager.block.BlockHutPhotoBooth::new);
    public static final DeferredItem<Item> ITEM_HUT_PHOTOBOOTH =
            ITEMS.register(PHOTOBOOTH_HUT_NAME, () -> new ItemBlockHut(BLOCK_HUT_PHOTOBOOTH.get(), new Item.Properties()));

    /** What a night's work comes home as, and what the darkroom turns it into. */
    public static final DeferredItem<Item> EXPOSED_PLATE =
            ITEMS.register("exposed_plate", () -> new SkyPlate(new Item.Properties().stacksTo(16), false));
    public static final DeferredItem<Item> STAR_PLATE =
            ITEMS.register("star_plate", () -> new SkyPlate(new Item.Properties().stacksTo(16), true));
    /** One block-entity type for both huts: the blueprints of either building carry the same id. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoyagerTileEntity>> BUILDING_BE =
            BLOCK_ENTITIES.register("colonybuilding",
                    () -> BlockEntityType.Builder.of(VoyagerTileEntity::new,
                            BLOCK_HUT.get(), BLOCK_HUT_OBSERVATORY.get(),
                            BLOCK_HUT_PHOTOBOOTH.get()).build(null));

    public static final DeferredHolder<JobEntry, JobEntry> JOB = JOBS.register(JOB_ID.getPath(),
            () -> new JobEntry.Builder()
                    .setJobProducer(JobVoyager::new)
                    .setJobViewProducer(() -> CrafterJobView::new)
                    .setRegistryName(JOB_ID)
                    .createJobEntry());

    public static final DeferredHolder<BuildingEntry, BuildingEntry> BUILDING = BUILDINGS.register(BUILDING_ID.getPath(),
            () -> new BuildingEntry.Builder()
                    .setBuildingBlock(BLOCK_HUT.get())
                    .setBuildingProducer(BuildingVoyager::new)
                    .setBuildingViewProducer(() -> EmptyView::new)
                    .setRegistryName(BUILDING_ID)
                    .addBuildingModuleProducer(VoyagerModules.WORK)
                    .addBuildingModuleProducer(VoyagerModules.CRAFT)
                    .addBuildingModuleProducer(VoyagerModules.EXPEDITION)
                    .addBuildingModuleProducer(BuildingModules.SETTINGS_CRAFTER_RECIPE)
                    .addBuildingModuleProducer(VoyagerModules.MENU)
                    .addBuildingModuleProducer(BuildingModules.CRAFT_TASK_VIEW)
                    .addBuildingModuleProducer(BuildingModules.MIN_STOCK)
                    .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
                    .createBuildingEntry());

    public static final DeferredHolder<JobEntry, JobEntry> ASTRONOMER_JOB = JOBS.register(ASTRONOMER_JOB_ID.getPath(),
            () -> new JobEntry.Builder()
                    .setJobProducer(JobAstronomer::new)
                    .setJobViewProducer(() -> com.minecolonies.core.colony.jobs.views.DefaultJobView::new)
                    .setRegistryName(ASTRONOMER_JOB_ID)
                    .createJobEntry());

    public static final DeferredHolder<BuildingEntry, BuildingEntry> OBSERVATORY =
            BUILDINGS.register(OBSERVATORY_ID.getPath(),
                    () -> new BuildingEntry.Builder()
                            .setBuildingBlock(BLOCK_HUT_OBSERVATORY.get())
                            .setBuildingProducer(BuildingObservatory::new)
                            .setBuildingViewProducer(() -> EmptyView::new)
                            .setRegistryName(OBSERVATORY_ID)
                            .addBuildingModuleProducer(ObservatoryModules.WORK)
                            // The Observatory's own study of the sky - its research, paid for in
                            // nights of watching. Nothing to do with the University's tree.
                            .addBuildingModuleProducer(ObservatoryModules.STUDY)
                            // The lookout and the night escort.
                            .addBuildingModuleProducer(ObservatoryModules.SETTINGS)
                            // The beds in the study belong to the astronomers who work here.
                            .addBuildingModuleProducer(BuildingModules.BED)
                            .addBuildingModuleProducer(BuildingModules.MIN_STOCK)
                            .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
                            .createBuildingEntry());

    public static final DeferredHolder<JobEntry, JobEntry> PHOTOGRAPHER_JOB =
            JOBS.register(PHOTOGRAPHER_JOB_ID.getPath(),
                    () -> new JobEntry.Builder()
                            .setJobProducer(me.lovkar.voyager.colony.JobPhotographer::new)
                            .setJobViewProducer(() -> CrafterJobView::new)
                            .setRegistryName(PHOTOGRAPHER_JOB_ID)
                            .createJobEntry());

    /**
     * The Photo Booth. A crafter building through and through: the work module, the recipe list,
     * the recipe settings and the task view are the same four every MineColonies crafter has, and
     * they are what let the colony be taught to make its own film, frames and albums.
     */
    public static final DeferredHolder<BuildingEntry, BuildingEntry> PHOTOBOOTH =
            BUILDINGS.register(PHOTOBOOTH_ID.getPath(),
                    () -> new BuildingEntry.Builder()
                            .setBuildingBlock(BLOCK_HUT_PHOTOBOOTH.get())
                            .setBuildingProducer(me.lovkar.voyager.colony.BuildingPhotoBooth::new)
                            .setBuildingViewProducer(() -> EmptyView::new)
                            .setRegistryName(PHOTOBOOTH_ID)
                            .addBuildingModuleProducer(me.lovkar.voyager.colony.PhotoBoothModules.WORK)
                            .addBuildingModuleProducer(me.lovkar.voyager.colony.PhotoBoothModules.CRAFT)
                            .addBuildingModuleProducer(BuildingModules.SETTINGS_CRAFTER_RECIPE)
                            .addBuildingModuleProducer(BuildingModules.CRAFT_TASK_VIEW)
                            .addBuildingModuleProducer(BuildingModules.BED)
                            .addBuildingModuleProducer(BuildingModules.MIN_STOCK)
                            .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
                            .createBuildingEntry());

    /** MineColonies' creative tab that lists every hut block. */
    private static final ResourceKey<CreativeModeTab> HUTS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("minecolonies", "mchuts"));

    public Voyager(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        JOBS.register(modEventBus);
        BUILDINGS.register(modEventBus);
        VoyagerSounds.SOUNDS.register(modEventBus);
        Effects.init();
        modEventBus.addListener(EventPriority.HIGH, Voyager::registerCapabilities);
        modEventBus.addListener(Voyager::addToCreativeTab);
        modEventBus.addListener(Voyager::registerPayloads);
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            lendVoiceLines();
            registerInteractions();
            // The chronicle listens for finished buildings on MineColonies' own event bus.
            me.lovkar.voyager.colony.ChronicleHook.install();
        }));
        // The night sky, read out of datapacks rather than out of anybody's classes. Registered on
        // the GAME bus, not the mod bus: reload listeners and commands are server-side events.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Voyager::addSkyReload);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Voyager::skyLoaded);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Voyager::registerSkyCommand);
        if (FMLEnvironment.dist.isClient()) {
            VoyagerClient.init(modEventBus);
        }
        LOGGER.info("Voyager 0.3.0-alpha.19 loaded - the Departure Point, the Observatory and the Photo Booth are ready");
    }

    /**
     * The two datapack readers, added to the server's reload.
     *
     * <p>Vanilla walks {@code data/<any namespace>/cosmic_object/} and {@code .../cosmic_event/} for
     * us, so the Observatory ends up knowing every object any datapack in the pack declares -
     * Exposure: Space's, ours, and anybody else's - without a line of their code being linked.</p>
     */
    private static void addSkyReload(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        for (me.lovkar.voyager.sky.SkyData d : me.lovkar.voyager.sky.SkyData.listeners()) {
            event.addListener(d);
        }
        event.addListener(me.lovkar.voyager.sky.SkyStudies.listener());
    }

    /** Once the server is up, say what the sky came to. One line, and it is the whole diagnosis. */
    private static void skyLoaded(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (me.lovkar.voyager.sky.SkyData.ready()) {
            LOGGER.info("[sky] {}", me.lovkar.voyager.sky.SkyData.summary());
        } else {
            // Not a crash and not a silence: Exposure: Space (or any datapack of cosmic objects)
            // is missing, so the Observatory still runs and every plate comes home blank.
            LOGGER.warn("[sky] {}", me.lovkar.voyager.sky.SkyData.summary());
            LOGGER.warn("[sky] the Observatory will still work, but every plate will be blank until"
                    + " a datapack of cosmic objects is present (Exposure: Space is the usual one)");
        }
        LOGGER.info("[study] {}", me.lovkar.voyager.sky.SkyStudies.summary());
        me.lovkar.voyager.sky.SkyEvent tonight =
                me.lovkar.voyager.sky.SkyData.tonight(event.getServer().overworld());
        LOGGER.info("[sky] tonight: {}", tonight == null ? "an ordinary night" : tonight.id());
    }

    /**
     * {@code /voyagersky} - the same summary on demand, plus what a given lens could reach.
     *
     * <p>Kept because "restart the server to see the number again" is a bad way to work, and because
     * it is how the sky gets checked against a modpack that changed under us.</p>
     */
    private static void registerSkyCommand(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("voyagersky")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                            me.lovkar.voyager.sky.SkyData.summary()), false);
                    me.lovkar.voyager.sky.SkyEvent t =
                            me.lovkar.voyager.sky.SkyData.tonight(ctx.getSource().getLevel());
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                            "tonight: " + (t == null ? "an ordinary night" : t.id()
                                    + "  luck x" + t.luck() + "  analysis x" + t.analysisSpeed()
                                    + "  exclusive " + t.exclusive().size())), false);
                    return me.lovkar.voyager.sky.SkyData.all().size();
                })
                .then(net.minecraft.commands.Commands.argument("lens",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        String want = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "lens");
                        me.lovkar.voyager.sky.SkyObject.LensTier tier =
                                me.lovkar.voyager.sky.SkyObject.LensTier.of(want);
                        java.util.List<me.lovkar.voyager.sky.SkyObject> reach =
                                me.lovkar.voyager.sky.SkyData.reachableBy(tier);
                        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                "a " + tier.name().toLowerCase() + " lens reaches " + reach.size()
                                        + " of " + me.lovkar.voyager.sky.SkyData.all().size()), false);
                        return reach.size();
                    }))
                .then(net.minecraft.commands.Commands.literal("catalogue")
                    .executes(ctx -> {
                        final com.minecolonies.api.colony.IColony colony =
                                com.minecolonies.api.colony.IColonyManager.getInstance()
                                        .getColonyByPosFromWorld(ctx.getSource().getLevel(),
                                                net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
                        if (colony == null) {
                            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                    "stand in a colony to read its catalogue"));
                            return 0;
                        }
                        final java.util.Map<ResourceLocation, me.lovkar.voyager.sky.SkyCatalogue.Entry> book =
                                me.lovkar.voyager.sky.SkyCatalogue.of(colony);
                        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                colony.getName() + ": " + book.size() + " object(s) on record, "
                                        + me.lovkar.voyager.sky.SkyCatalogue.plates(colony) + " plate(s), from "
                                        + me.lovkar.voyager.sky.SkyCatalogue.observatories(colony).size()
                                        + " Observatory/ies"), false);
                        for (final me.lovkar.voyager.sky.SkyCatalogue.Entry e : book.values()) {
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    "  " + e.id() + "  x" + e.plates() + "  " + e.best()
                                            + "  nights " + e.firstNight() + "-" + e.lastNight()
                                            + (e.combined() ? "  (combined)" : "")), false);
                        }
                        return book.size();
                    })));
    }

    /**
     * Our own packets. Only one so far: "begin this study at that Observatory".
     *
     * <p>MineColonies' research packet is not usable here - it casts the building it came from to
     * the University - and the Observatory's studies are not in its tree anyway, so this is a
     * plain NeoForge payload of our own, re-checked end to end on the server.</p>
     */
    private static void registerPayloads(final net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                me.lovkar.voyager.network.StartStudyMessage.TYPE,
                me.lovkar.voyager.network.StartStudyMessage.STREAM_CODEC,
                me.lovkar.voyager.network.StartStudyMessage::handle);
    }

    /** Racks/chests inside the hut: MineColonies reads the hut inventory through this capability. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BUILDING_BE.get(),
                (be, side) -> be.getItemHandlerCap(side));
    }

    /**
     * MineColonies looks citizen voice lines up by job path and crashes the server tick for a job
     * it has no entry for. Voyagers speak with the Nether Miner's lines (they are cut from the
     * same cloth), falling back to the unemployed citizen's if that map is ever missing.
     */
    private static void lendVoiceLines() {
        final Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> voices = ModSoundEvents.CITIZEN_SOUND_EVENTS;
        final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> lines =
                voices.containsKey("netherworker") ? voices.get("netherworker") : voices.get("unemployed");
        if (lines == null) {
            LOGGER.warn("No citizen voice lines found to lend to the Voyager");
            return;
        }
        voices.put(JOB_ID.getPath(), lines);
        // A crafter's voice for a crafter. Without an entry MineColonies NPEs on the first line.
        final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> benchLines =
                voices.containsKey("stonemason") ? voices.get("stonemason") : lines;
        voices.put(PHOTOGRAPHER_JOB_ID.getPath(), benchLines);
        // The astronomer needs a map of their own or MineColonies NPEs on the first voice line -
        // that was the 0.1.0 crash. The Researcher is the closest thing the game has to somebody
        // who works nights and talks to nobody.
        final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> nightLines =
                voices.containsKey("researcher") ? voices.get("researcher") : lines;
        voices.put(ASTRONOMER_JOB_ID.getPath(), nightLines);
        LOGGER.info("Voyagers speak with the Nether Miner's voice lines, astronomers with the Researcher's");
    }

    /** The "I can't reach the departure point" speech bubble stays up only while that is true. */
    private static void registerInteractions() {
        InteractionValidatorRegistry.registerStandardPredicate(
                Component.translatableEscape(EntityAIWorkVoyager.CHAT_UNREACHABLE),
                citizen -> citizen.getJob() instanceof JobVoyager job && job.isDepartureBlocked());
        InteractionValidatorRegistry.registerStandardPredicate(
                Component.translatableEscape(EntityAIWorkAstronomer.CHAT_UNREACHABLE),
                citizen -> citizen.getJob() instanceof JobAstronomer job && job.isBlocked());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HUTS_TAB)) {
            event.accept(ITEM_HUT.get());
            event.accept(ITEM_HUT_OBSERVATORY.get());
            event.accept(ITEM_HUT_PHOTOBOOTH.get());
        }
        // the plates go in with the hut blocks: they are the Observatory's output, and that is
        // where a player looking for this mod's things will look
        if (event.getTabKey().equals(HUTS_TAB)) {
            event.accept(EXPOSED_PLATE.get());
            event.accept(STAR_PLATE.get());
        }
    }
}
