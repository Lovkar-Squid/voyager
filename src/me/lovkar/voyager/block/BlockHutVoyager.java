package me.lovkar.voyager.block;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import me.lovkar.voyager.Voyager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Departure Point - the hut block shared by the Launchpad and the End Gate.
 *
 * AbstractColonyBlock hardcodes the "minecolonies" namespace in getRegistryName() and
 * creates MineColonies' own block entity, whose type only accepts MineColonies huts;
 * both are overridden here (the MC Trade Post approach).
 */
public class BlockHutVoyager extends AbstractBlockHut<BlockHutVoyager> {

    @Override
    public String getHutName() {
        return Voyager.HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return Voyager.BUILDING.get();
    }

    @Override
    public @NotNull ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath(Voyager.MODID, Voyager.HUT_NAME);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        final TileEntityColonyBuilding te = Voyager.BUILDING_BE.get().create(pos, state);
        if (te != null) {
            te.registryName = getBuildingEntry().getRegistryName();
        }
        return te;
    }
}
