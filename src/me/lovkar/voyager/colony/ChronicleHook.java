package me.lovkar.voyager.colony;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import me.lovkar.voyager.Voyager;

/**
 * Where the colony chronicle learns that something was built.
 *
 * <p>MineColonies posts {@link BuildingConstructionModEvent} on its own event bus when a builder
 * finishes a work order. For a build or an upgrade, every Photo Booth in that colony is told, and
 * its photographer goes out to photograph the new building for the album. Repairs and removals are
 * not history worth a page.</p>
 */
public final class ChronicleHook {

    private static boolean installed;

    private ChronicleHook() {
    }

    /** Subscribe once. Called from common setup, when MineColonies' API is up. */
    public static void install() {
        if (installed) {
            return;
        }
        try {
            IMinecoloniesAPI.getInstance().getEventBus().subscribe(BuildingConstructionModEvent.class,
                    ChronicleHook::onConstruction);
            installed = true;
        } catch (final Throwable t) {
            Voyager.LOGGER.warn("[chronicle] could not subscribe to MineColonies' construction events ({});"
                    + " the chronicle will stay empty", t.toString());
        }
    }

    private static void onConstruction(final BuildingConstructionModEvent event) {
        final IBuilding built = event.getBuilding();
        if (built == null || event.getWorkOrder() == null) {
            return;
        }
        final WorkOrderType type = event.getWorkOrder().getWorkOrderType();
        if (type != WorkOrderType.BUILD && type != WorkOrderType.UPGRADE) {
            return;
        }
        final IColony colony = built.getColony();
        if (colony == null) {
            return;
        }
        final int level = event.getWorkOrder().getTargetLevel();
        for (final IBuilding candidate : colony.getServerBuildingManager().getBuildings().values()) {
            if (candidate instanceof BuildingPhotoBooth booth && booth.getBuildingLevel() >= 1) {
                booth.chronicle(built, level);
                Voyager.LOGGER.info("[chronicle] {} level {} is owed a photograph", built.getBuildingDisplayName(), level);
            }
        }
    }
}
