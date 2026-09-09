package me.lovkar.voyager.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * A night the sky does something: a meteor shower, an aurora, a deep-sky night.
 *
 * <p>Read the same way as {@link SkyObject} and for the same reason - it is datapack JSON, so it is
 * ours to read and nobody's to break. An event makes objects more likely, makes the analysis
 * faster, and lets a handful of objects appear that exist on no other night.</p>
 *
 * <p>These are what the colony's <i>calendar</i> is made of: the astronomer works harder on an event
 * night, the Ephemeris research announces one a day early, and the town holds a star party.</p>
 */
public record SkyEvent(
        ResourceLocation id,
        String nameKey,
        String descriptionKey,
        int weight,
        double luck,
        double analysisSpeed,
        Set<ResourceLocation> exclusive) {

    /** Is this object one of the ones that only exist while this event runs? */
    public boolean unlocks(ResourceLocation object) {
        return exclusive.contains(object);
    }

    public static SkyEvent from(ResourceLocation id, JsonObject o) {
        if (o == null) return null;
        Set<ResourceLocation> excl = new HashSet<>();
        if (o.has("exclusive_objects") && o.get("exclusive_objects").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("exclusive_objects");
            for (int i = 0; i < arr.size(); i++) {
                try {
                    ResourceLocation r = ResourceLocation.parse(arr.get(i).getAsString());
                    if (r != null) excl.add(r);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return new SkyEvent(
                id,
                str(o, "name_key", id.getPath()),
                str(o, "description_key", ""),
                Math.max(0, num(o, "weight", 1).intValue()),
                Math.max(0.0, num(o, "luck_multiplier", 1.0).doubleValue()),
                Math.max(0.05, num(o, "analysis_speed_multiplier", 1.0).doubleValue()),
                Set.copyOf(excl));
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static Number num(JsonObject o, String key, Number fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsNumber() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
