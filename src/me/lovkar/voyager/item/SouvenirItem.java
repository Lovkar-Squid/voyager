package me.lovkar.voyager.item;

import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.trophy.TripStory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A souvenir from an expedition: it places as the keepsake on its stand (see SouvenirBlock), and its
 * tooltip tells the trip - who brought it home, which expedition it was, what happened out there.
 * The name ("Shard from the Hollow Spires") is the stack's {@code item_name}, set when it was found.
 */
public class SouvenirItem extends BlockItem {

    public SouvenirItem(final Block block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(final @NotNull ItemStack stack, final @NotNull TooltipContext context,
                                final @NotNull List<Component> lines, final @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        final TripStory story = stack.get(Voyager.TRIP_STORY.get());
        if (story != null) {
            story.addTooltip(lines);
        }
    }
}
