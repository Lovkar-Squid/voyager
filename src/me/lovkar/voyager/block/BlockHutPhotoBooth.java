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

/** The Photo Booth - the hut block of the photographer, shared by all five looks in the pack. */
public class BlockHutPhotoBooth extends AbstractBlockHut<BlockHutPhotoBooth> {

    @Override
    public String getHutName() {
        return Voyager.PHOTOBOOTH_HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return Voyager.PHOTOBOOTH.get();
    }

    @Override
    public @NotNull ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath(Voyager.MODID, Voyager.PHOTOBOOTH_HUT_NAME);
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
