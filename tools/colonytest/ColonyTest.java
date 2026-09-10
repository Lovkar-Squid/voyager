package me.lovkar.voyagercolonytest;

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
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import me.lovkar.voyager.colony.JobAstronomer;
import me.lovkar.voyager.colony.JobPhotographer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Headless colony test: a colony on a flat world with an Observatory and a Photo Booth, an
 * astronomer and a photographer hired into them, a camera and film on each shelf, a hill for the
 * lookout and a visitor for the studio. Then the two AIs run for real, driven by the console
 * (time set day / night), and the log says what they photographed and where it ended up.
 */
@Mod("voyagercolonytest")
public class ColonyTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("voyagercolonytest");
    private int tick = 0;
    private IColony colony;
    private BlockPos center, obsPos, boothPos;
    private ICitizenData astronomer, photographer, bystander;
    private IVisitorData visitor;
    private boolean pasted, registered;
    private final java.util.Map<BlockPos, Blueprint> blueprints = new java.util.HashMap<>();

    public ColonyTest(final IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void onTick(final ServerTickEvent.Post e) {
        tick++;
        final ServerLevel level = e.getServer().overworld();
        try {
            if (tick == 60) {
                setUp(level);
            } else if (tick == 400 && pasted) {
                register(level);
            } else if (registered && tick % 200 == 0) {
                report(level);
            }
        } catch (final Throwable t) {
            LOGGER.error("[colonytest] FAILED at tick {}", tick, t);
        }
    }

    private void setUp(final ServerLevel level) {
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        center = new BlockPos(0, y, 0);
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        LOGGER.info("[colonytest] surface at y={}, chunks forced", y);
        final Player owner = FakePlayerFactory.getMinecraft(level);
        owner.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        colony = IColonyManager.getInstance().createColony(level, center, owner, "Voyager Test", "Medieval Oak");
        LOGGER.info("[colonytest] colony {} created at {}", colony.getID(), center);
        // the town hall block, registered, so the colony has one
        try {
            final Block townHall = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecolonies", "blockhuttownhall"));
            level.setBlock(center, townHall.defaultBlockState(), 3);
            final BlockEntity te = level.getBlockEntity(center);
            if (te instanceof AbstractTileEntityColonyBuilding hut) {
                final IBuilding th = colony.getServerBuildingManager().addNewBuilding(hut, level);
                LOGGER.info("[colonytest] town hall registered: {}", th);
            }
        } catch (final Throwable t) {
            LOGGER.warn("[colonytest] town hall not registered: {}", t.toString());
        }
        colony.getPackageManager().addCloseSubscriber((net.minecraft.server.level.ServerPlayer) owner);
        obsPos = center.offset(26, 0, 0);
        boothPos = center.offset(-26, 0, 0);
        paste(level, "observatory/observatory1.blueprint", obsPos);
        paste(level, "photobooth/photobooth2.blueprint", boothPos);
        pasted = true;
    }

    private void paste(final ServerLevel level, final String path, final BlockPos pos) {
        final Blueprint bp = StructurePacks.getBlueprint("Voyager", path, level.registryAccess());
        if (bp == null) {
            LOGGER.error("[colonytest] NO BLUEPRINT {}", path);
            return;
        }
        bp.setRotationMirror(RotationMirror.NONE, level);
        blueprints.put(pos, bp);
        final CreativeStructureHandler handler = new CreativeStructureHandler(level, pos, bp, RotationMirror.NONE, true);
        Manager.addToQueue(new PlaceStructureOperation(new StructurePlacer(handler), FakePlayerFactory.getMinecraft(level)));
        LOGGER.info("[colonytest] placing {} at {}", path, pos);
    }

    private void register(final ServerLevel level) {
        final IBuilding obs = hut(level, obsPos, "observatory");
        final IBuilding booth = hut(level, boothPos, "photo booth");
        if (obs == null || booth == null) {
            return;
        }
        // a hill for the lookout: a stepped stone pyramid, its top 5 above the instrument
        final BlockPos scope = ((me.lovkar.voyager.colony.BuildingObservatory) obs).getScopePosition();
        final int top = scope.getY() + 5;
        final int height = top - center.getY();
        final BlockPos hill = obsPos.offset(0, 0, 32);
        for (int layer = 0; layer < height; layer++) {
            final int r = 3 + (height - 1 - layer);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    level.setBlock(new BlockPos(hill.getX() + dx, center.getY() + layer, hill.getZ() + dz), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        LOGGER.info("[colonytest] hill built at {} up to y={} (scope at {})", hill, center.getY() + height - 1, scope);
        // a camera with a roll of film, and a spare roll, on each shelf
        stock(obs);
        stock(booth);
        // the workers
        astronomer = hire(level, obs, obsPos.offset(0, 1, 3));
        photographer = hire(level, booth, boothPos.offset(0, 1, 3));
        bystander = spawn(level, boothPos.offset(3, 1, 4));
        try {
            final IVisitorData v = (IVisitorData) colony.getVisitorManager().createAndRegisterCivilianData();
            v.setSittingPosition(boothPos.offset(6, 0, 6));
            colony.getVisitorManager().spawnOrCreateCivilian(v, level, List.of(boothPos.offset(6, 1, 6)), true);
            visitor = v;
            LOGGER.info("[colonytest] visitor {} spawned", v.getName());
        } catch (final Throwable t) {
            LOGGER.warn("[colonytest] no visitor: {}", t.toString());
        }
        registered = true;
        LOGGER.info("[colonytest] READY - observatory level {}, booth level {}", obs.getBuildingLevel(), booth.getBuildingLevel());
    }

    private IBuilding hut(final ServerLevel level, final BlockPos pos, final String what) {
        final BlockEntity te = level.getBlockEntity(pos);
        if (!(te instanceof AbstractTileEntityColonyBuilding hut)) {
            LOGGER.error("[colonytest] no hut block entity at {} for the {} ({})", pos, what, te);
            return null;
        }
        IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        if (building == null) {
            building = colony.getServerBuildingManager().addNewBuilding(hut, level);
        }
        if (building == null) {
            LOGGER.error("[colonytest] could not register the {} at {}", what, pos);
            return null;
        }
        if (building.getBuildingLevel() < 1) {
            building.upgradeBuildingLevelToSchematicData();
        }
        if (building.getBuildingLevel() < 1) {
            final int wanted = what.equals("observatory") ? 1 : 2;
            building.setBuildingLevel(wanted);
            building.onUpgradeComplete(blueprints.get(pos), wanted);
        }
        LOGGER.info("[colonytest] {} registered at {}: {} level {} schematic {}", what, pos,
                building.getClass().getSimpleName(), building.getBuildingLevel(), building.getSchematicName());
        return building;
    }

    private void stock(final IBuilding building) {
        final Item camera = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("exposure", "camera"));
        final Item film = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("exposure", "black_and_white_film"));
        final boolean a = InventoryUtils.addItemStackToProvider(building, new ItemStack(camera));
        final boolean b = InventoryUtils.addItemStackToProvider(building, new ItemStack(film));
        final boolean c = InventoryUtils.addItemStackToProvider(building, new ItemStack(film));
        LOGGER.info("[colonytest] stocked {}: camera {} film {} {}", building.getSchematicName(), a, b, c);
    }

    private ICitizenData spawn(final ServerLevel level, final BlockPos at) {
        final ICitizenData data = colony.getCitizenManager().createAndRegisterCivilianData();
        colony.getCitizenManager().spawnOrCreateCitizen(data, level, at);
        LOGGER.info("[colonytest] citizen {} spawned at {} (entity {})", data.getName(), at, data.getEntity().isPresent());
        return data;
    }

    private ICitizenData hire(final ServerLevel level, final IBuilding building, final BlockPos at) {
        final ICitizenData data = spawn(level, at);
        final WorkerBuildingModule module = building.getFirstModuleOccurance(WorkerBuildingModule.class);
        final boolean ok = module.assignCitizen(data);
        LOGGER.info("[colonytest] {} hired into {}: {} -> job {}", data.getName(), building.getSchematicName(), ok,
                data.getJob() == null ? "none" : data.getJob().getClass().getSimpleName());
        return data;
    }

    private void report(final ServerLevel level) {
        final String a = astronomer == null || astronomer.getJob() == null ? "-"
                : astronomer.getJob() instanceof JobAstronomer j ? j.getStatus() + " | " + j.getStatusLine() : astronomer.getJob().getClass().getSimpleName();
        final String p = photographer == null || photographer.getJob() == null ? "-"
                : photographer.getJob() instanceof JobPhotographer j ? j.getStatus() + " | " + j.getStatusLine() : photographer.getJob().getClass().getSimpleName();
        final String where = (astronomer != null && astronomer.getEntity().isPresent() ? astronomer.getEntity().get().blockPosition().toShortString() : "?")
                + " / " + (photographer != null && photographer.getEntity().isPresent() ? photographer.getEntity().get().blockPosition().toShortString() : "?");
        LOGGER.info("[colonytest] t={} day={} colony {} | astronomer: {} | photographer: {} | at {}", tick,
                level.getDayTime() % 24000L, colony.isActive() ? "ACTIVE" : "inactive", a, p, where);
    }
}
