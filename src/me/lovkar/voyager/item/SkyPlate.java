package me.lovkar.voyager.item;

import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.sky.SkyData;
import me.lovkar.voyager.sky.SkyObject;
import me.lovkar.voyager.sky.SkyRoll;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A photographic plate of the night sky.
 *
 * <p>Two of these exist: an <b>exposed plate</b>, which is what the astronomer carries in at dawn
 * and which shows nothing until it has been through the darkroom, and a <b>star plate</b>, the
 * developed print, which names what was caught and how rare it was.</p>
 *
 * <p>What it records lives in vanilla's {@code minecraft:custom_data} rather than in a component
 * type of our own: the plate has to survive being read by anything that handles item NBT, and the
 * object it names is a {@link ResourceLocation} that may belong to Exposure: Space, to us, or to a
 * datapack nobody has written yet. The item never looks anything up until a tooltip asks - so a
 * plate of an object whose datapack has since been removed is a plate of "something", not a crash.</p>
 */
public class SkyPlate extends Item {

    public static final String TAG_OBJECT = "sky_object";
    public static final String TAG_NIGHT = "sky_night";
    public static final String TAG_BAND = "sky_band";
    public static final String TAG_LOOK = "sky_look";
    public static final String TAG_MARK = "sky_mark";

    /**
     * What the darkroom made of it. A colony that has never seen the object gets a plain print; a
     * colony that has gets a duplicate, unless Comparative Astronomy put the two nights together.
     */
    public enum Mark {
        FIRST, DUPLICATE, COMPOSITE;

        public String key() {
            return "com.voyager.plate.mark." + name().toLowerCase();
        }
    }

    private final boolean developed;

    public SkyPlate(final Properties properties, final boolean developed) {
        super(properties);
        this.developed = developed;
    }

    /** Stamp a plate with what it caught. */
    public static ItemStack record(final ItemStack stack, final ResourceLocation object,
                                   final long night, final SkyRoll.Band band, final String look) {
        final CompoundTag tag = new CompoundTag();
        if (object != null) {
            tag.putString(TAG_OBJECT, object.toString());
        }
        tag.putLong(TAG_NIGHT, night);
        tag.putString(TAG_BAND, band == null ? SkyRoll.Band.COMMON.name() : band.name());
        if (look != null) {
            tag.putString(TAG_LOOK, look);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Say whether this print is the colony's first of its object, a duplicate, or a composite. */
    public static ItemStack mark(final ItemStack stack, final Mark mark) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        final CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putString(TAG_MARK, (mark == null ? Mark.FIRST : mark).name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static Mark markOf(final ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Mark.FIRST;
        }
        try {
            return Mark.valueOf(data.copyTag().getString(TAG_MARK));
        } catch (RuntimeException e) {
            return Mark.FIRST;
        }
    }

    /** What this plate caught, or null if it is blank or the datapack has gone. */
    public static ResourceLocation objectOf(final ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        final String id = data.copyTag().getString(TAG_OBJECT);
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return ResourceLocation.parse(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static SkyRoll.Band bandOf(final ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return SkyRoll.Band.COMMON;
        }
        try {
            return SkyRoll.Band.valueOf(data.copyTag().getString(TAG_BAND));
        } catch (RuntimeException e) {
            return SkyRoll.Band.COMMON;
        }
    }

    public static long nightOf(final ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0L : data.copyTag().getLong(TAG_NIGHT);
    }

    /** The colour a band is written in: the same ladder the game uses for rarity. */
    private static ChatFormatting colourOf(final SkyRoll.Band band) {
        return switch (band) {
            case COMMON -> ChatFormatting.GRAY;
            case NOTABLE -> ChatFormatting.WHITE;
            case RARE -> ChatFormatting.AQUA;
            case REMARKABLE -> ChatFormatting.BLUE;
            case EXTRAORDINARY -> ChatFormatting.LIGHT_PURPLE;
            case ONCE_IN_A_LIFETIME -> ChatFormatting.GOLD;
        };
    }

    @Override
    public void appendHoverText(final @NotNull ItemStack stack, final @NotNull TooltipContext context,
                                final @NotNull List<Component> lines, final @NotNull TooltipFlag flag) {
        final ResourceLocation id = objectOf(stack);
        if (!developed) {
            lines.add(Component.translatable("com.voyager.plate.undeveloped").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (id == null) {
            lines.add(Component.translatable("com.voyager.plate.blank").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        final SkyObject object = SkyData.byId(id);
        // The name key is theirs; if their datapack is gone we still have the id, and an id is a
        // better tooltip than an exception.
        lines.add(object != null
                ? Component.translatable(object.nameKey()).withStyle(ChatFormatting.WHITE)
                : Component.literal(id.getPath()).withStyle(ChatFormatting.WHITE));
        if (object != null) {
            lines.add(Component.translatable(object.typeKey()).withStyle(ChatFormatting.DARK_GRAY));
        }
        final SkyRoll.Band band = bandOf(stack);
        lines.add(Component.translatable(band.key()).withStyle(colourOf(band)));
        final Mark mark = markOf(stack);
        if (mark == Mark.COMPOSITE) {
            lines.add(Component.translatable(mark.key()).withStyle(ChatFormatting.GREEN));
        } else if (mark == Mark.DUPLICATE) {
            lines.add(Component.translatable(mark.key()).withStyle(ChatFormatting.DARK_GRAY));
        }
        final long night = nightOf(stack);
        if (night > 0) {
            lines.add(Component.translatable("com.voyager.plate.night", night).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** A developed plate of something once-in-a-lifetime should look like it in the inventory. */
    public boolean isDeveloped() {
        return developed;
    }

    static {
        Voyager.LOGGER.debug("Sky plates ready");
    }
}
