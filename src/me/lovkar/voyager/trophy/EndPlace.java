package me.lovkar.voyager.trophy;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The named places of the outer End a souvenir can come from, and the word for what was picked up
 * there ("Shard from the Hollow Spires").
 *
 * <p>The names are invented - the End has no places of its own - and were approved by the owner for
 * 0.4.0; the list is closed on purpose, so a place in a tooltip is always one of these sixteen. The
 * Dragon's Wake is the odd one out: a Voyager only gets there by beating the dragon, so it is never
 * among the {@link #ordinary()} places a souvenir is drawn from.</p>
 *
 * <p>Both parts of the name are lang keys. A place key holds the place as it reads inside a sentence
 * ("the Hollow Spires", "Chorus Reach"): the name is only ever shown inside one, and a translator
 * then decides the article along with the rest of the sentence instead of the code lower-casing an
 * English "The".</p>
 */
public enum EndPlace {
    HOLLOW_SPIRES("hollow_spires", "shard"),
    CHORUS_REACH("chorus_reach", "sprig"),
    PALE_SHELF("pale_shelf", "pebble"),
    PURPUR_STEPS("purpur_steps", "fragment"),
    SHULKER_NARROWS("shulker_narrows", "shell"),
    QUIET_RIM("quiet_rim", "keepsake"),
    GATEWATCH_ISLE("gatewatch_isle", "stone"),
    LISTENING_PILLARS("listening_pillars", "shard"),
    LONG_DROP("long_drop", "pebble"),
    OBSIDIAN_HOOK("obsidian_hook", "splinter"),
    DRAGONS_WAKE("dragons_wake", "scale"),
    ELYTRA_POINT("elytra_point", "feather"),
    BROKEN_ARCHES("broken_arches", "fragment"),
    LANTERNLESS_BAY("lanternless_bay", "stone"),
    FAR_ISLES("far_isles", "keepsake"),
    DRIFTING_KEEL("drifting_keel", "splinter");

    /** Lang key prefix of a place; the key is the prefix plus {@link #id()}. */
    public static final String PLACE_PREFIX = "com.voyager.place.";
    /** Lang key prefix of a souvenir word; the key is the prefix plus {@link #word()}. */
    public static final String WORD_PREFIX = "com.voyager.souvenir.word.";
    /** "%1$s from %2$s": the word, then the place. */
    public static final String NAME_PATTERN = "com.voyager.souvenir.name";

    private final String id;
    private final String word;

    EndPlace(final String id, final String word) {
        this.id = id;
        this.word = word;
    }

    /** The id stored in a trip story (never changes once released: old souvenirs keep their place). */
    public String id() {
        return id;
    }

    /** The souvenir word's id ("shard"); several places share a word. */
    public String word() {
        return word;
    }

    public String placeKey() {
        return PLACE_PREFIX + id;
    }

    public String wordKey() {
        return WORD_PREFIX + word;
    }

    /** The souvenir's name from this place: "Shard from the Hollow Spires", from three lang keys. */
    public Component souvenirName() {
        return Component.translatable(NAME_PATTERN, Component.translatable(wordKey()), Component.translatable(placeKey()));
    }

    /** The place with this id, or null for one this version does not know. */
    public static @Nullable EndPlace byId(final String id) {
        for (final EndPlace place : values()) {
            if (place.id.equals(id)) {
                return place;
            }
        }
        return null;
    }

    /** The fifteen places a souvenir is drawn from when the dragon was not beaten. */
    public static List<EndPlace> ordinary() {
        final List<EndPlace> out = new ArrayList<>();
        for (final EndPlace place : values()) {
            if (place != DRAGONS_WAKE) {
                out.add(place);
            }
        }
        return out;
    }
}
