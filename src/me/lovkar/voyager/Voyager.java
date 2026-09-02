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
import me.lovkar.voyager.block.BlockHutVoyager;
import me.lovkar.voyager.block.VoyagerTileEntity;
import me.lovkar.voyager.client.VoyagerClient;
import me.lovkar.voyager.colony.BuildingVoyager;
import me.lovkar.voyager.colony.JobVoyager;
import me.lovkar.voyager.colony.VoyagerModules;
import me.lovkar.voyager.fx.Effects;
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
    public static final ResourceLocation JOB_ID = ResourceLocation.fromNamespaceAndPath(MODID, "voyager");
    public static final ResourceLocation BUILDING_ID = ResourceLocation.fromNamespaceAndPath(MODID, "voyager");
    /** Citizen model type; the suit textures live under MineColonies' namespace (see VoyagerClient). */
    public static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(MODID, "voyager");

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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoyagerTileEntity>> BUILDING_BE =
            BLOCK_ENTITIES.register("colonybuilding",
                    () -> BlockEntityType.Builder.of(VoyagerTileEntity::new, BLOCK_HUT.get()).build(null));

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
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            lendVoiceLines();
            registerInteractions();
        }));
        if (FMLEnvironment.dist.isClient()) {
            VoyagerClient.init(modEventBus);
        }
        LOGGER.info("Voyager loaded - the Departure Point is ready for the End");
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
        LOGGER.info("Voyagers speak with the Nether Miner's voice lines");
    }

    /** The "I can't reach the departure point" speech bubble stays up only while that is true. */
    private static void registerInteractions() {
        InteractionValidatorRegistry.registerStandardPredicate(
                Component.translatableEscape(EntityAIWorkVoyager.CHAT_UNREACHABLE),
                citizen -> citizen.getJob() instanceof JobVoyager job && job.isDepartureBlocked());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HUTS_TAB)) {
            event.accept(ITEM_HUT.get());
        }
    }
}
