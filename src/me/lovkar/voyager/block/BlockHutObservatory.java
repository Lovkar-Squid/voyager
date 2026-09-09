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
 * The Observatory - the hut block of the astronomer, shared by all five looks in the pack.
 *
 * Same two overrides as the Departure Point's block and for the same reasons: AbstractColonyBlock
 * hardcodes the "minecolonies" namespace in getRegistryName(), and MineColonies' own block-entity
 * type only accepts MineColonies hut blocks. Ours is registered for both of our hut blocks, so the
 * blueprints of either building carry the same {@code voyager:colonybuilding} tile entity.
 */
public class BlockHutObservatory extends AbstractBlockHut<BlockHutObservatory> {

    @Override
    public String getHutName() {
        return Voyager.OBSERVATORY_HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return Voyager.OBSERVATORY.get();
    }

    @Override
    public @NotNull ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath(Voyager.MODID, Voyager.OBSERVATORY_HUT_NAME);
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
