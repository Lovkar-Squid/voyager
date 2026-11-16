package me.lovkar.voyager.trophy;

import me.lovkar.voyager.Voyager;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a completed expedition may bring home besides its haul - a souvenir from a named place, and
 * on a level-5 trip an End-city enchanted book and a hand-drawn map of the End - and the factories
 * that make them.
 *
 * <p>Everything here is plain: a random source and numbers in, item stacks out, no colony and no
 * citizen. The AI rolls with exactly these methods, and the rig's trophy probe calls the same ones,
 * so what the probe checked is what a Voyager brings home.</p>
 */
public final class Trophies {

    /** End cities only appear on the outer islands, and only a level-5 Departure Point reaches them. */
    public static final int END_CITY_LEVEL = 5;

    public static final String KEY_MAP_CHART = "com.voyager.end_map.chart";
    /** Long-range Comms: "%1$s brought home %2$s", the trophies as one list. */
    public static final String KEY_COMMS = "com.voyager.comms.trophies";
    public static final String KEY_TROPHY_SOUVENIR = "com.voyager.trophy.souvenir";
    public static final String KEY_TROPHY_BOOK = "com.voyager.trophy.book";
    public static final String KEY_TROPHY_MAP = "com.voyager.trophy.map";
    /** "%1$s and %2$s": the last two items of a list. */
    public static final String KEY_LIST_TWO = "com.voyager.list.two";
    /** "%1$s, %2$s": every earlier item of a list. */
    public static final String KEY_LIST_MORE = "com.voyager.list.more";

    /** Unknown book enchantment ids already warned about: one warning per id, not one per trip. */
    private static final Set<String> WARNED = new HashSet<>();

    private Trophies() {
    }

    /** Which trophies a completed trip brings home. */
    public record Roll(boolean souvenir, boolean book, boolean map) {
        public boolean any() {
            return souvenir || book || map;
        }
    }

    /**
     * The roll for one completed trip, with the server config's chances: a souvenir at any level, the
     * book and the map only from a level-5 Departure Point. Each is its own draw.
     */
    public static Roll decide(final int buildingLevel, final double souvenirChance, final double bookChance,
                              final double mapChance, final RandomSource random) {
        final boolean souvenir = random.nextDouble() < souvenirChance;
        final boolean endCity = buildingLevel >= END_CITY_LEVEL;
        final boolean book = endCity && random.nextDouble() < bookChance;
        final boolean map = endCity && random.nextDouble() < mapChance;
        return new Roll(souvenir, book, map);
    }

    /**
     * Where the souvenir is from: the Dragon's Wake only - and always - after the Voyager beat the
     * dragon on this trip, otherwise one of the fifteen other places.
     */
    public static EndPlace place(final boolean beatTheDragon, final RandomSource random) {
        if (beatTheDragon) {
            return EndPlace.DRAGONS_WAKE;
        }
        final List<EndPlace> ordinary = EndPlace.ordinary();
        return ordinary.get(random.nextInt(ordinary.size()));
    }

    /**
     * A souvenir with this story, named "Shard from the Hollow Spires" through the {@code item_name}
     * component - not {@code custom_name}, so an anvil can still rename it and the name stays a
     * translation, not frozen English.
     */
    public static ItemStack souvenir(final TripStory story) {
        final ItemStack stack = new ItemStack(Voyager.SOUVENIR_ITEM.get());
        stack.set(Voyager.TRIP_STORY.get(), story);
        final EndPlace place = EndPlace.byId(story.place());
        if (place != null) {
            stack.set(DataComponents.ITEM_NAME, place.souvenirName());
        }
        return stack;
    }

    /** A map of the End with this story: a lore item, never a vanilla map - no map data, no chunks. */
    public static ItemStack endMap(final TripStory story) {
        final ItemStack stack = new ItemStack(Voyager.END_MAP.get());
        stack.set(Voyager.TRIP_STORY.get(), story);
        return stack;
    }

    /**
     * The enchantments a book can carry: the configured ids that name an enchantment in this world.
     * An id that does not (a typo, a mod that is gone) is left out with one warning in the log.
     */
    public static List<Holder<Enchantment>> bookPool(final HolderLookup.Provider registries, final List<String> ids) {
        final HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        final List<Holder<Enchantment>> pool = new ArrayList<>();
        for (final String id : ids) {
            final ResourceLocation rl = ResourceLocation.tryParse(id);
            final Optional<? extends Holder<Enchantment>> holder = rl == null ? Optional.empty()
                    : lookup.get(ResourceKey.create(Registries.ENCHANTMENT, rl));
            if (holder.isPresent()) {
                if (!pool.contains(holder.get())) {
                    pool.add(holder.get());
                }
            } else {
                warnOnce(id);
            }
        }
        return pool;
    }

    private static void warnOnce(final String id) {
        synchronized (WARNED) {
            if (WARNED.add(id)) {
                Voyager.LOGGER.warn("[Voyager] rewards.bookEnchantments names '{}', which is not an enchantment in this world - left out of the End-city books", id);
            }
        }
    }

    /**
     * An End-city enchanted book: one enchantment from the list, at a random level from 1 to its
     * maximum. Empty when no id of the list names an enchantment.
     */
    public static ItemStack book(final HolderLookup.Provider registries, final RandomSource random, final List<String> ids) {
        final List<Holder<Enchantment>> pool = bookPool(registries, ids);
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final Holder<Enchantment> enchantment = pool.get(random.nextInt(pool.size()));
        final int level = 1 + random.nextInt(Math.max(1, enchantment.value().getMaxLevel()));
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
    }

    /**
     * A list read the way a sentence reads it: "a", "a and b", "a, b and c" - with the two joining
     * words in the lang file, so a translation joins its own way.
     */
    public static Component join(final List<Component> parts) {
        if (parts.isEmpty()) {
            return Component.empty();
        }
        Component head = parts.get(0);
        for (int i = 1; i < parts.size() - 1; i++) {
            head = Component.translatable(KEY_LIST_MORE, head, parts.get(i));
        }
        return parts.size() == 1 ? head : Component.translatable(KEY_LIST_TWO, head, parts.get(parts.size() - 1));
    }

    /**
     * One trophy as the comms line and the log name it: "a Shard from the Hollow Spires", "an
     * enchanted book (Sharpness III)", "a map of the End".
     */
    public static Component describe(final ItemStack trophy) {
        if (trophy.is(Voyager.SOUVENIR_ITEM.get())) {
            return Component.translatable(KEY_TROPHY_SOUVENIR, trophy.getHoverName());
        }
        if (trophy.is(Voyager.END_MAP.get())) {
            return Component.translatable(KEY_TROPHY_MAP);
        }
        if (trophy.is(Items.ENCHANTED_BOOK)) {
            final ItemEnchantments stored = trophy.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            final Optional<Component> first = stored.entrySet().stream().findFirst()
                    .map(entry -> enchantmentName(entry.getKey(), entry.getIntValue()));
            if (first.isPresent()) {
                return Component.translatable(KEY_TROPHY_BOOK, first.get());
            }
        }
        return trophy.getHoverName();
    }

    /** "Sharpness III" without vanilla's tooltip colour: in a chat line the grey would look like a mistake. */
    private static Component enchantmentName(final Holder<Enchantment> enchantment, final int level) {
        final Component name = enchantment.value().description();
        if (enchantment.value().getMaxLevel() == 1) {
            return name;
        }
        return Component.empty().append(name).append(" ").append(Component.translatable("enchantment.level." + level));
    }
}
