package me.lovkar.voyager;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * The server config, {@code voyager-server.toml}: what a server owner can tune about expeditions.
 *
 * <p>Every default is what 0.3.9 did with its constants, so a world that never touches the file
 * plays exactly as before. It is a SERVER config on purpose: the numbers decide what happens in
 * the world (how often Voyagers fly, what a trip costs, whether a Voyager can be lost), so they
 * belong to the world's owner and NeoForge sends them to every client that joins.</p>
 *
 * <p>The getters are safe to call at any time. A SERVER config only exists while a world is
 * running; before that (the title screen, a client tooltip, a probe at the wrong moment) NeoForge
 * would throw, and an expedition rule is better served by its default than by a crash.</p>
 *
 * <p>The supply LIST of an expedition is not here: it is the datapack recipes
 * {@code data/voyager/crafterrecipes/voyager/trip1..5.json}, and a datapack replaces them. This
 * file only scales their amounts.</p>
 */
public final class VoyagerConfig {

    /** The defaults, named once so the README, the spec and the probe cannot drift apart. */
    public static final int DEFAULT_TRIPS_PER_WINDOW = 1;
    public static final int DEFAULT_RATIONS_PER_VOYAGER = 16;
    public static final double DEFAULT_SUPPLY_MULTIPLIER = 1.0;
    public static final double DEFAULT_TRIP_LENGTH_MULTIPLIER = 1.0;
    public static final double DEFAULT_DRAGON_CHANCE = 0.10;
    public static final boolean DEFAULT_PERMANENT_DEATH = true;
    public static final double DEFAULT_SOUVENIR_CHANCE = 0.10;
    public static final double DEFAULT_END_CITY_BOOK_CHANCE = 0.05;
    public static final double DEFAULT_END_MAP_CHANCE = 0.03;
    /**
     * What an End city's library might hold: useful, ordinary enchantments. Never Mending, no other
     * treasure enchantment and no curse - a Voyager's book should be a nice find, not the best item
     * in the game every few weeks.
     */
    public static final List<String> DEFAULT_BOOK_ENCHANTMENTS = List.of(
            "minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection",
            "minecraft:projectile_protection", "minecraft:feather_falling", "minecraft:respiration",
            "minecraft:depth_strider", "minecraft:unbreaking", "minecraft:efficiency", "minecraft:fortune",
            "minecraft:silk_touch", "minecraft:sharpness", "minecraft:smite", "minecraft:looting",
            "minecraft:power", "minecraft:punch");

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue TRIPS_PER_WINDOW;
    private static final ModConfigSpec.IntValue RATIONS_PER_VOYAGER;
    private static final ModConfigSpec.DoubleValue SUPPLY_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue TRIP_LENGTH_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue DRAGON_CHANCE;
    private static final ModConfigSpec.BooleanValue PERMANENT_DEATH;
    private static final ModConfigSpec.DoubleValue SOUVENIR_CHANCE;
    private static final ModConfigSpec.DoubleValue END_CITY_BOOK_CHANCE;
    private static final ModConfigSpec.DoubleValue END_MAP_CHANCE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BOOK_ENCHANTMENTS;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("How Voyagers travel. Every default is how Voyager 0.3.9 played.").push("expeditions");
        TRIPS_PER_WINDOW = builder
                .comment("Expeditions each Voyager may make per launch window. Starlight Navigation and the",
                        "Observatory's Launch Window study still add one each on top.")
                .defineInRange("tripsPerLaunchWindow", DEFAULT_TRIPS_PER_WINDOW, 1, 10);
        RATIONS_PER_VOYAGER = builder
                .comment("Rations each Voyager packs for a trip; the hut asks for food while it and its crew",
                        "hold fewer than this many per Voyager.")
                .defineInRange("rationsPerVoyager", DEFAULT_RATIONS_PER_VOYAGER, 1, 64);
        SUPPLY_MULTIPLIER = builder
                .comment("Scales every supply amount of the expedition plans (rounded up, at least 1): what the",
                        "hut requests, what must be in stock before a launch and what a launch uses up.",
                        "The supply list itself is the datapack recipes data/voyager/crafterrecipes/voyager/trip1..5.json.")
                .defineInRange("supplyMultiplier", DEFAULT_SUPPLY_MULTIPLIER, 0.25, 4.0);
        TRIP_LENGTH_MULTIPLIER = builder
                .comment("Scales how long a Voyager is away. Rapid Refit and Star Charts still shorten the trip.")
                .defineInRange("tripLengthMultiplier", DEFAULT_TRIP_LENGTH_MULTIPLIER, 0.1, 10.0);
        DRAGON_CHANCE = builder
                .comment("Chance per level-5 expedition, with Dragon Hunt researched, that the dragon is on the itinerary.")
                .defineInRange("dragonChance", DEFAULT_DRAGON_CHANCE, 0.0, 1.0);
        PERMANENT_DEATH = builder
                .comment("true: a Voyager can be lost in the End (a grave by the hut, Return to Sender brings the gear back).",
                        "false: a blow that would kill them drives them back instead - they come home wounded and",
                        "empty-handed, with their gear, and nobody dies.")
                .define("permanentDeath", DEFAULT_PERMANENT_DEATH);
        builder.pop();

        builder.comment("What a completed expedition may bring home besides its haul.").push("rewards");
        SOUVENIR_CHANCE = builder
                .comment("Chance per completed expedition of a souvenir from a named place in the End.")
                .defineInRange("souvenirChance", DEFAULT_SOUVENIR_CHANCE, 0.0, 1.0);
        END_CITY_BOOK_CHANCE = builder
                .comment("Chance per completed level-5 expedition of an enchanted book from an End city.")
                .defineInRange("endCityBookChance", DEFAULT_END_CITY_BOOK_CHANCE, 0.0, 1.0);
        END_MAP_CHANCE = builder
                .comment("Chance per completed level-5 expedition of a hand-drawn map of the End.")
                .defineInRange("endMapChance", DEFAULT_END_MAP_CHANCE, 0.0, 1.0);
        BOOK_ENCHANTMENTS = builder
                .comment("Enchantments an End-city book can carry (ids such as minecraft:sharpness), one picked",
                        "at random at a random level. Unknown ids are skipped.")
                .defineListAllowEmpty("bookEnchantments", () -> DEFAULT_BOOK_ENCHANTMENTS, () -> "minecraft:unbreaking",
                        VoyagerConfig::isEnchantmentId);
        builder.pop();

        SPEC = builder.build();
    }

    private VoyagerConfig() {
    }

    /** A list entry the file may hold: any well-formed id. Whether it names an enchantment is the reader's business. */
    private static boolean isEnchantmentId(final Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    /** The value while a world's config is loaded, the default otherwise (see the class comment). */
    private static <T> T read(final ModConfigSpec.ConfigValue<T> value) {
        if (!SPEC.isLoaded()) {
            return value.getDefault();
        }
        try {
            return value.get();
        } catch (final IllegalStateException notLoaded) {
            // unloaded between the check and the read (a world closing): the default will do
            return value.getDefault();
        }
    }

    /** Base expeditions per launch window per Voyager, before research (1-10). */
    public static int tripsPerLaunchWindow() {
        return read(TRIPS_PER_WINDOW);
    }

    /** Rations packed per trip per Voyager (1-64). */
    public static int rationsPerVoyager() {
        return read(RATIONS_PER_VOYAGER);
    }

    /** Factor on every supply amount of the expedition plans (0.25-4.0; exactly 1.0 leaves the plans untouched). */
    public static double supplyMultiplier() {
        return read(SUPPLY_MULTIPLIER);
    }

    /** Factor on the time a Voyager is away (0.1-10.0). */
    public static double tripLengthMultiplier() {
        return read(TRIP_LENGTH_MULTIPLIER);
    }

    /** Odds of the dragon per level-5 expedition with Dragon Hunt (0-1). */
    public static double dragonChance() {
        return read(DRAGON_CHANCE);
    }

    /** Whether a Voyager can be lost in the End. */
    public static boolean permanentDeath() {
        return read(PERMANENT_DEATH);
    }

    /** Odds of a souvenir per completed expedition (0-1). */
    public static double souvenirChance() {
        return read(SOUVENIR_CHANCE);
    }

    /** Odds of an End-city enchanted book per completed level-5 expedition (0-1). */
    public static double endCityBookChance() {
        return read(END_CITY_BOOK_CHANCE);
    }

    /** Odds of a map of the End per completed level-5 expedition (0-1). */
    public static double endMapChance() {
        return read(END_MAP_CHANCE);
    }

    /** The enchantment ids an End-city book may carry, as written in the file (a copy; may be empty). */
    public static List<String> bookEnchantments() {
        return new ArrayList<>(read(BOOK_ENCHANTMENTS));
    }
}
