package me.lovkar.voyager.item;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.items.ItemBlockHut;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The Observatory's hut item: MineColonies' hut item with one more tooltip line.
 *
 * <p>Without Exposure: Space nothing is in the sky (it is what supplies the cosmic objects), so the
 * astronomer keeps the watch and every plate comes home blank. The building stays registered and
 * buildable all the same - hiding it would strand the Observatories already standing in a world
 * whose pack lost the mod - but whoever picks the hut up should know before building it. The check
 * is the mod list, never a class of theirs, as everywhere else in this mod.</p>
 */
public class ObservatoryHutItem extends ItemBlockHut {

    /** Mod id of Exposure: Space. */
    public static final String EXPOSURE_SPACE = "exposure_space";
    /** Lang key of the tooltip line shown while Exposure: Space is missing. */
    public static final String TOOLTIP_NEEDS_SPACE = "com.voyager.observatory.needs_space";

    public ObservatoryHutItem(final AbstractBlockHut<?> block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(final @NotNull ItemStack stack, final @NotNull TooltipContext context,
                                final @NotNull List<Component> lines, final @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        if (!ModList.get().isLoaded(EXPOSURE_SPACE)) {
            lines.add(Component.translatable(TOOLTIP_NEEDS_SPACE).withStyle(ChatFormatting.GRAY));
        }
    }
}
