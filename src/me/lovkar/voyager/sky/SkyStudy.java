package me.lovkar.voyager.sky;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One study in the Observatory's own book of work.
 *
 * <p>This is not a MineColonies research and does not live in its tree. The University buys time
 * with a researcher standing in a room; an Observatory buys it with <b>nights</b> - the astronomer
 * has to actually watch the sky for as many nights as the study costs, and a clouded night buys
 * nothing. That is the whole reason the Observatory has a system of its own: the currency is the
 * thing the building already produces.</p>
 *
 * <p>Read from {@code data/voyager/sky_study/&lt;id&gt;.json} by {@link SkyStudies}, the same
 * datapack-first way the sky itself is read - so a pack can add, retune or remove studies without
 * touching a class.</p>
 */
public record SkyStudy(ResourceLocation id, String branch, int sortOrder,
                       @Nullable ResourceLocation parent, int tier, int nights,
                       List<ItemStack> costs, String effect, double level, ItemStack icon) {

    public String nameKey() {
        return "com.voyager.study." + id.getPath() + ".name";
    }

    public String subtitleKey() {
        return "com.voyager.study." + id.getPath() + ".subtitle";
    }

    public String branchKey() {
        return "com.voyager.study.branch." + branch;
    }

    /** The effect id this study grants, in the same shape the rest of the mod reads. */
    public ResourceLocation effectId() {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "effects/" + effect);
    }

    // ------------------------------------------------------------------ json

    public static @Nullable SkyStudy from(final ResourceLocation id, final JsonObject json) {
        try {
            final List<ItemStack> costs = new ArrayList<>();
            final JsonArray array = json.has("costs") ? json.getAsJsonArray("costs") : new JsonArray();
            for (final JsonElement element : array) {
                final JsonObject cost = element.getAsJsonObject();
                final ItemStack stack = stack(cost.get("item").getAsString(),
                        cost.has("count") ? cost.get("count").getAsInt() : 1);
                if (!stack.isEmpty()) {
                    costs.add(stack);
                }
            }
            return new SkyStudy(id,
                    json.has("branch") ? json.get("branch").getAsString() : "sky",
                    json.has("sortOrder") ? json.get("sortOrder").getAsInt() : 0,
                    json.has("parent") ? ResourceLocation.parse(json.get("parent").getAsString()) : null,
                    Math.max(1, json.get("tier").getAsInt()),
                    Math.max(1, json.get("nights").getAsInt()),
                    costs,
                    json.get("effect").getAsString(),
                    json.has("level") ? json.get("level").getAsDouble() : 1.0,
                    json.has("icon") ? stack(json.get("icon").getAsString(), 1) : ItemStack.EMPTY);
        } catch (final RuntimeException unreadable) {
            SkyData.LOG.warn("[study] {} is not a study we can read: {}", id, unreadable.toString());
            return null;
        }
    }

    /**
     * An item by id, or nothing. A study that costs an item from a mod this pack does not have is
     * a study that simply cannot be bought - which is the right answer, not a crash.
     */
    private static ItemStack stack(final String id, final int count) {
        final ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        final Item item = BuiltInRegistries.ITEM.get(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    // ------------------------------------------------------------------ network

    public void write(final RegistryFriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeUtf(branch);
        buf.writeVarInt(sortOrder);
        buf.writeBoolean(parent != null);
        if (parent != null) {
            buf.writeResourceLocation(parent);
        }
        buf.writeVarInt(tier);
        buf.writeVarInt(nights);
        buf.writeVarInt(costs.size());
        for (final ItemStack cost : costs) {
            ItemStack.STREAM_CODEC.encode(buf, cost);
        }
        buf.writeUtf(effect);
        buf.writeDouble(level);
        ItemStack.STREAM_CODEC.encode(buf, icon);
    }

    public static SkyStudy read(final RegistryFriendlyByteBuf buf) {
        final ResourceLocation id = buf.readResourceLocation();
        final String branch = buf.readUtf();
        final int sortOrder = buf.readVarInt();
        final ResourceLocation parent = buf.readBoolean() ? buf.readResourceLocation() : null;
        final int tier = buf.readVarInt();
        final int nights = buf.readVarInt();
        final int costCount = buf.readVarInt();
        final List<ItemStack> costs = new ArrayList<>(costCount);
        for (int i = 0; i < costCount; i++) {
            costs.add(ItemStack.STREAM_CODEC.decode(buf));
        }
        return new SkyStudy(id, branch, sortOrder, parent, tier, nights, costs,
                buf.readUtf(), buf.readDouble(), ItemStack.STREAM_CODEC.decode(buf));
    }
}
