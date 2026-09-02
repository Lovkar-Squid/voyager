package me.lovkar.voyager.block;

import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import me.lovkar.voyager.Voyager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of the Departure Point. Behaves exactly like a MineColonies hut block entity;
 * it only exists so the hut block is a valid block for a block-entity type we own.
 */
public class VoyagerTileEntity extends TileEntityColonyBuilding {

    public VoyagerTileEntity(final BlockPos pos, final BlockState state) {
        super(Voyager.BUILDING_BE.get(), pos, state);
    }
}
