package me.lovkar.voyager.trophy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * The story of one expedition, as a data component on what it brought home (a souvenir, a map of
 * the End): where, who, which expedition of the Departure Point, on which colony day, from which
 * look of the building and what happened out there.
 *
 * <p>Only facts are stored - ids and numbers, never finished English sentences - and the tooltip
 * is put together from lang keys when it is shown, so a souvenir reads in the player's language
 * and a fixed translation fixes every souvenir already made. The record is immutable, as a data
 * component value must be (vanilla shares one value between copies of a stack).</p>
 *
 * @param place      the {@link EndPlace} id
 * @param voyager    the Voyager's name
 * @param expedition the Departure Point's lifetime expedition number
 * @param day        the colony's day when the trip ended
 * @param look       {@link #LOOK_LAUNCHPAD} or {@link #LOOK_END_GATE}
 * @param fights     the fights won, by mob, in the order they were first met
 */
public record TripStory(String place, String voyager, int expedition, int day, String look, List<Fight> fights) {

    public static final String LOOK_LAUNCHPAD = "launchpad";
    public static final String LOOK_END_GATE = "end_gate";

    public static final String KEY_BROUGHT_BY = "com.voyager.story.brought_by";
    public static final String KEY_EXPEDITION = "com.voyager.story.expedition";
    /** Lang key prefix of a look's name; the key is the prefix plus {@link #look()}. */
    public static final String LOOK_PREFIX = "com.voyager.story.look.";
    public static final String KEY_QUIET = "com.voyager.story.quiet";
    public static final String KEY_DRAGON = "com.voyager.story.dragon";
    public static final String KEY_FOUGHT = "com.voyager.story.fought";
    /** "an enderman": lang key prefix for one of a mob the lang file has words for. */
    public static final String MOB_ONE_PREFIX = "com.voyager.story.mob.";
    /** "%1$s endermen": lang key prefix for several of a mob the lang file has words for. */
    public static final String MOB_MANY_PREFIX = "com.voyager.story.mobs.";
    /** Any other mob (a datapack's), named the way the game names it: one... */
    public static final String KEY_OTHER_MOB_ONE = "com.voyager.story.other_mob.one";
    /** ...and several. */
    public static final String KEY_OTHER_MOB_MANY = "com.voyager.story.other_mob.many";
    /**
     * The End's mobs that have proper words of their own in the lang file ("2 endermen", "a shulker").
     * The expedition plans name exactly these three; the dragon has a line of her own.
     */
    public static final List<String> NAMED_MOBS = List.of("enderman", "endermite", "shulker");

    private static final ResourceLocation DRAGON = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ENDER_DRAGON);

    /** One kind of mob beaten on the trip, and how many of them. */
    public record Fight(ResourceLocation mob, int count) {
        public static final Codec<Fight> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("mob").forGetter(Fight::mob),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Fight::count)
        ).apply(i, Fight::new));
        public static final StreamCodec<ByteBuf, Fight> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Fight::mob,
                ByteBufCodecs.VAR_INT, Fight::count,
                Fight::new);
    }

    public static final Codec<TripStory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("place").forGetter(TripStory::place),
            Codec.STRING.fieldOf("voyager").forGetter(TripStory::voyager),
            Codec.INT.fieldOf("expedition").forGetter(TripStory::expedition),
            Codec.INT.fieldOf("day").forGetter(TripStory::day),
            Codec.STRING.fieldOf("look").forGetter(TripStory::look),
            Fight.CODEC.listOf().optionalFieldOf("fights", List.of()).forGetter(TripStory::fights)
    ).apply(i, TripStory::new));

    /** What a client is sent: the same six facts, so the tooltip can be built over there. */
    public static final StreamCodec<ByteBuf, TripStory> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TripStory::place,
            ByteBufCodecs.STRING_UTF8, TripStory::voyager,
            ByteBufCodecs.VAR_INT, TripStory::expedition,
            ByteBufCodecs.VAR_INT, TripStory::day,
            ByteBufCodecs.STRING_UTF8, TripStory::look,
            Fight.STREAM_CODEC.apply(ByteBufCodecs.list()), TripStory::fights,
            TripStory::new);

    public TripStory {
        fights = List.copyOf(fights);
    }

    /** Whether the dragon is among the fights won - the only way to the Dragon's Wake. */
    public boolean beatTheDragon() {
        return fights.stream().anyMatch(f -> DRAGON.equals(f.mob()));
    }

    /** "Brought home by Mira Tallow" */
    public Component broughtBy() {
        return Component.translatable(KEY_BROUGHT_BY, voyager);
    }

    /** "Expedition 12 - Launchpad, day 34" */
    public Component expeditionLine() {
        return Component.translatable(KEY_EXPEDITION, expedition, Component.translatable(LOOK_PREFIX + look), day);
    }

    /**
     * What happened: "Met the dragon and lived" when she was beaten (that is the story, whatever
     * else came along), "Fought 2 endermen and a shulker" after fights, "A quiet crossing" without.
     */
    public Component eventLine() {
        if (beatTheDragon()) {
            return Component.translatable(KEY_DRAGON);
        }
        if (fights.isEmpty()) {
            return Component.translatable(KEY_QUIET);
        }
        final List<Component> parts = new ArrayList<>();
        for (final Fight fight : fights) {
            parts.add(mobs(fight));
        }
        return Component.translatable(KEY_FOUGHT, Trophies.join(parts));
    }

    /** "an enderman", "2 endermen" - or, for a mob without words of its own, "Phantom", "3 Phantom". */
    private static Component mobs(final Fight fight) {
        final ResourceLocation id = fight.mob();
        if (ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace()) && NAMED_MOBS.contains(id.getPath())) {
            return fight.count() == 1
                    ? Component.translatable(MOB_ONE_PREFIX + id.getPath())
                    : Component.translatable(MOB_MANY_PREFIX + id.getPath(), fight.count());
        }
        final Component name = BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .<Component>map(EntityType::getDescription)
                .orElse(Component.literal(id.getPath()));
        return fight.count() == 1
                ? Component.translatable(KEY_OTHER_MOB_ONE, name)
                : Component.translatable(KEY_OTHER_MOB_MANY, fight.count(), name);
    }

    /** The three tooltip lines, grey like every other line this mod adds to a tooltip. */
    public void addTooltip(final List<Component> lines) {
        lines.add(broughtBy().copy().withStyle(ChatFormatting.GRAY));
        lines.add(expeditionLine().copy().withStyle(ChatFormatting.GRAY));
        lines.add(eventLine().copy().withStyle(ChatFormatting.GRAY));
    }
}
