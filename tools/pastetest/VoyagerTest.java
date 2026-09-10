package me.lovkar.voyagertest;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.operations.PlaceStructureOperation;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.CreativeStructureHandler;
import com.ldtteam.structurize.storage.StructurePacks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Headless test: paste every Voyager Observatory and Photo Booth blueprint into the world. */
@Mod("voyagertest")
public class VoyagerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("voyagertest");
    static final String[] PATHS = { "observatory/observatory1.blueprint", "observatory/observatory2.blueprint", "observatory/observatory3.blueprint", "observatory/observatory4.blueprint", "observatory/observatory5.blueprint", "observatory/keep1.blueprint", "observatory/keep2.blueprint", "observatory/keep3.blueprint", "observatory/keep4.blueprint", "observatory/keep5.blueprint", "observatory/sandcourt1.blueprint", "observatory/sandcourt2.blueprint", "observatory/sandcourt3.blueprint", "observatory/sandcourt4.blueprint", "observatory/sandcourt5.blueprint", "observatory/station1.blueprint", "observatory/station2.blueprint", "observatory/station3.blueprint", "observatory/station4.blueprint", "observatory/station5.blueprint", "observatory/array1.blueprint", "observatory/array2.blueprint", "observatory/array3.blueprint", "observatory/array4.blueprint", "observatory/array5.blueprint", "photobooth/photobooth1.blueprint", "photobooth/photobooth2.blueprint", "photobooth/photobooth3.blueprint", "photobooth/photobooth4.blueprint", "photobooth/photobooth5.blueprint", "photobooth/keep1.blueprint", "photobooth/keep2.blueprint", "photobooth/keep3.blueprint", "photobooth/keep4.blueprint", "photobooth/keep5.blueprint", "photobooth/sandcourt1.blueprint", "photobooth/sandcourt2.blueprint", "photobooth/sandcourt3.blueprint", "photobooth/sandcourt4.blueprint", "photobooth/sandcourt5.blueprint", "photobooth/station1.blueprint", "photobooth/station2.blueprint", "photobooth/station3.blueprint", "photobooth/station4.blueprint", "photobooth/station5.blueprint", "photobooth/array1.blueprint", "photobooth/array2.blueprint", "photobooth/array3.blueprint", "photobooth/array4.blueprint", "photobooth/array5.blueprint" };
    public static final int SPACING = 64;
    public static final int Y = 150;
    private int tick = 0;
    private int next = 0;

    public VoyagerTest(final IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    public static BlockPos slot(final int i) {
        return new BlockPos((i % 10) * SPACING, Y, (i / 10) * SPACING);
    }

    private void onTick(final ServerTickEvent.Post e) {
        tick++;
        if (tick < 100 || tick % 40 != 0 || next >= PATHS.length) {
            return;
        }
        final ServerLevel level = e.getServer().overworld();
        final String path = PATHS[next];
        final BlockPos pos = slot(next);
        next++;
        for (int cx = (pos.getX() - 32) >> 4; cx <= (pos.getX() + 32) >> 4; cx++) {
            for (int cz = (pos.getZ() - 32) >> 4; cz <= (pos.getZ() + 32) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        try {
            final Blueprint bp = StructurePacks.getBlueprint("Voyager", path, level.registryAccess());
            if (bp == null) {
                LOGGER.error("[voyagertest] NO BLUEPRINT {}", path);
                return;
            }
            bp.setRotationMirror(RotationMirror.NONE, level);
            final CreativeStructureHandler handler = new CreativeStructureHandler(level, pos, bp, RotationMirror.NONE, true);
            Manager.addToQueue(new PlaceStructureOperation(new StructurePlacer(handler), FakePlayerFactory.getMinecraft(level)));
            LOGGER.info("[voyagertest] placing {} ({}x{}x{}, {} entities) at {}", path, bp.getSizeX(), bp.getSizeY(), bp.getSizeZ(), bp.getEntities().length, pos);
        } catch (final Exception ex) {
            LOGGER.error("[voyagertest] FAILED {}", path, ex);
        }
    }
}
