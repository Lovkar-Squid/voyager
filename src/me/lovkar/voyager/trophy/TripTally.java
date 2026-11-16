package me.lovkar.voyager.trophy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What happened on the trip so far, kept by the job while the Voyager is away: the fights won, by
 * mob, in the order they were first met. It is what a souvenir's "Fought 2 endermen and a shulker"
 * is made of, and whether the Dragon's Wake is on offer.
 *
 * <p>It lives on the job, not in the AI, and is saved with it: the AI is rebuilt after a reload (and
 * reset for sleep, rain and raids), and a story half-forgotten on the way home would be a wrong one.
 * Only fights won are counted - a fight lost ends the trip, and with it any souvenir.</p>
 */
public final class TripTally {

    private static final String TAG_MOB = "mob";
    private static final String TAG_COUNT = "count";
    private static final ResourceLocation DRAGON = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ENDER_DRAGON);

    private final Map<ResourceLocation, Integer> wins = new LinkedHashMap<>();

    /** A new trip: nothing has happened yet. */
    public void clear() {
        wins.clear();
    }

    /** One more fight won against this kind of mob. */
    public void recordWin(final ResourceLocation mob) {
        wins.merge(mob, 1, Integer::sum);
    }

    public boolean beatTheDragon() {
        return wins.containsKey(DRAGON);
    }

    /** The fights won, in the order the story tells them. */
    public List<TripStory.Fight> fights() {
        final List<TripStory.Fight> out = new ArrayList<>();
        wins.forEach((mob, count) -> out.add(new TripStory.Fight(mob, count)));
        return out;
    }

    public ListTag save() {
        final ListTag list = new ListTag();
        wins.forEach((mob, count) -> {
            final CompoundTag entry = new CompoundTag();
            entry.putString(TAG_MOB, mob.toString());
            entry.putInt(TAG_COUNT, count);
            list.add(entry);
        });
        return list;
    }

    /** Reads what {@link #save()} wrote; an entry with a broken id is dropped rather than failing the job. */
    public void load(final ListTag list) {
        wins.clear();
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            final ResourceLocation mob = ResourceLocation.tryParse(entry.getString(TAG_MOB));
            final int count = entry.getInt(TAG_COUNT);
            if (mob != null && count > 0) {
                wins.merge(mob, count, Integer::sum);
            }
        }
    }

    /** The list tag type {@link #save()} writes, for the caller's {@code getList}. */
    public static int listType() {
        return Tag.TAG_COMPOUND;
    }
}
