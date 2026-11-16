package me.lovkar.voyager.item;

import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.trophy.TripStory;
import me.lovkar.voyager.trophy.Trophies;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A map of the End from a level-5 expedition: a lore item, a chart a Voyager drew of the outer
 * islands, with the trip's story in its tooltip.
 *
 * <p>Deliberately a plain item and not a vanilla map: a map item carries map data, and filling one
 * means generating and loading the End's chunks around it - for a keepsake on a shelf, on a server
 * that may never have had anybody in the End. Nothing here touches a level.</p>
 */
public class EndMapItem extends Item {

    public EndMapItem(final Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(final @NotNull ItemStack stack, final @NotNull TooltipContext context,
                                final @NotNull List<Component> lines, final @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(Trophies.KEY_MAP_CHART).withStyle(ChatFormatting.GRAY));
        final TripStory story = stack.get(Voyager.TRIP_STORY.get());
        if (story != null) {
            story.addTooltip(lines);
        }
    }
}
