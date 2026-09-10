package me.lovkar.voyager.compat;

import java.util.List;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.InventoryUtils;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * The frames on the walls of a hut, and what hangs in them.
 *
 * <p>Every Observatory and Photo Booth blueprint has photograph frames on its walls - Exposure's
 * own hanging frames, small, medium and large - and until now they hung empty, because the
 * pictures the two workers made went on the shelf. Marko: "make it so both can put pictures into
 * the frames in their huts." So a finished picture goes on the wall first: into an empty frame if
 * there is one, and when every frame is full, into the frame with the oldest picture, whose print
 * comes down and goes on the shelf. The walls always show the newest work and nothing is thrown
 * away. The frames that count as the hut's are the ones inside its blueprint's box.</p>
 *
 * <p>Only reached through {@link me.lovkar.voyager.photo.ColonyCamera#hang} after the check that
 * Exposure is loaded, like the rest of this package.</p>
 */
public final class ExposureFrames {

    private ExposureFrames() {
    }

    /**
     * Hang a photograph in one of the building's frames.
     *
     * @return true if the picture is on a wall now - the caller's stack has been used up; false
     *         if there is no frame for it, in which case the caller keeps the stack
     */
    public static boolean hang(final ServerLevel level, final IBuilding building, final ItemStack photograph) {
        if (level == null || building == null || photograph == null || photograph.isEmpty()
                || !(photograph.getItem() instanceof PhotographItem)) {
            return false;
        }
        final List<PhotographFrameEntity> frames = framesOf(level, building);
        if (frames.isEmpty()) {
            return false;
        }
        PhotographFrameEntity chosen = null;
        long oldest = Long.MAX_VALUE;
        for (final PhotographFrameEntity frame : frames) {
            final ItemStack hanging = frame.getItem();
            if (hanging.isEmpty()) {
                chosen = frame;                          // an empty frame beats everything
                break;
            }
            final long taken = takenAt(hanging);
            if (taken < oldest) {
                oldest = taken;
                chosen = frame;
            }
        }
        if (chosen == null) {
            return false;
        }
        final ItemStack displaced = chosen.getItem();
        if (!displaced.isEmpty()) {
            // The old print comes down onto the shelf; if the shelf is full it stays on the wall.
            if (!InventoryUtils.addItemStackToProvider(building, displaced.copy())) {
                return false;
            }
        }
        chosen.setItem(photograph.copyWithCount(1));
        return true;
    }

    /** How many of the building's frames are on its walls, and how many of them hold a picture. */
    public static int[] count(final ServerLevel level, final IBuilding building) {
        final List<PhotographFrameEntity> frames = framesOf(level, building);
        int filled = 0;
        for (final PhotographFrameEntity frame : frames) {
            if (!frame.getItem().isEmpty()) {
                filled++;
            }
        }
        return new int[] {frames.size(), filled};
    }

    private static List<PhotographFrameEntity> framesOf(final ServerLevel level, final IBuilding building) {
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        if (corners == null || corners.getA() == null || corners.getB() == null) {
            return List.of();
        }
        final BlockPos a = corners.getA();
        final BlockPos b = corners.getB();
        final AABB box = new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1.0, Math.max(a.getY(), b.getY()) + 1.0, Math.max(a.getZ(), b.getZ()) + 1.0)
                .inflate(1.0);
        return level.getEntitiesOfClass(PhotographFrameEntity.class, box, frame -> frame.isAlive());
    }

    /** When the picture in a stack was taken, by its own frame data; a picture without a date counts as oldest. */
    private static long takenAt(final ItemStack photograph) {
        try {
            final Frame frame = photograph.get(Exposure.DataComponents.PHOTOGRAPH_FRAME);
            if (frame == null) {
                return Long.MIN_VALUE;
            }
            return frame.extraData().get(Frame.TIMESTAMP).orElse(Long.MIN_VALUE);
        } catch (final Throwable exposureChanged) {
            return Long.MIN_VALUE;
        }
    }
}
