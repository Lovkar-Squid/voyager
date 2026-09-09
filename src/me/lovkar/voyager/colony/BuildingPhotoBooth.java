package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * The Photo Booth: a studio with a camera stand in it and a darkroom behind it.
 *
 * <p>The studio is for the player - it holds the colony's camera stand, so somebody can walk in,
 * sit, and take their own portrait. The darkroom is for the Photographer, and it is where the
 * colony's film becomes photographs.</p>
 */
public class BuildingPhotoBooth extends AbstractBuilding {

    public static final int MAX_LEVEL = 5;
    /** The blueprint tags the Lightroom: where film becomes a photograph. */
    public static final String TAG_DARKROOM = "darkroom";
    /** The blueprint tags the studio floor, where the camera stand waits for a sitter. */
    public static final String TAG_STUDIO = "studio";

    public BuildingPhotoBooth(final IColony colony, final BlockPos pos) {
        super(colony, pos);
    }

    /** Which of the five looks was built, taken from the blueprint path. */
    @Override
    public @NotNull String getSchematicName() {
        return "photobooth";
    }

    @Override
    public int getMaxBuildingLevel() {
        return MAX_LEVEL;
    }

    /** Two photographers from level 4: one at the bench, one in the dark. */
    public static int crewSize(final IBuilding building) {
        return building != null && building.getBuildingLevel() >= 4 ? 2 : 1;
    }

    /** The studio floor, where the camera stand waits and the photographer stands to shoot. */
    public BlockPos getStudioPosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_STUDIO);
        return tagged != null ? tagged : getPosition();
    }

    public BlockPos getDarkroomPosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_DARKROOM);
        return tagged != null ? tagged : getPosition();
    }

    /** The Photographer's recipes: everything Exposure makes, plus what the darkroom teaches. */
    public static class CraftingModule extends AbstractCraftingBuildingModule.Crafting {
        public CraftingModule(final JobEntry jobEntry) {
            super(jobEntry);
        }
    }
}
