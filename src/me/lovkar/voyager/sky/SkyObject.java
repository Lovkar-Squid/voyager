package me.lovkar.voyager.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One thing in the night sky, as the datapack describes it.
 *
 * <p>This is a <b>reading</b> of somebody else's data, not a copy of their class. Exposure: Space
 * publishes its objects as ordinary datapack JSON and its author was explicit that this - datapacks
 * and configs - is the only surface he supports: there is no Java API and the internals may change
 * under us at any release. So we never link his code. We read the same files vanilla reads, with
 * vanilla's own {@code ResourceManager}, and we survive anything we do not recognise.</p>
 *
 * <p>That has a second benefit worth having on purpose: the Observatory sees <b>every</b> object any
 * datapack registers, whether it came from Exposure: Space, from us, or from a third party.</p>
 *
 * <p>Every field is optional as far as this class is concerned. All 256 of his objects happen to
 * carry all eleven, but a file that is missing something must degrade to a sensible default rather
 * than throw - one bad JSON file in a datapack cannot be allowed to take a colony down.</p>
 */
public record SkyObject(
        ResourceLocation id,
        String nameKey,
        String descriptionKey,
        String typeKey,
        LensTier tier,
        double appearanceChance,
        int size,
        int analysisTicks,
        String catalogTexture,
        List<Reward> rewards,
        float yaw,
        float pitch,
        float spread,
        String wikipedia) {

    /** One item the object pays out when it is first studied. */
    public record Reward(ResourceLocation item, int count) {
    }

    /** How good a lens has to be to see it. The order is the progression. */
    public enum LensTier {
        BAD, NORMAL, GOOD, EXCELLENT, SCULK;

        public static LensTier of(String s) {
            if (s == null) return BAD;
            for (LensTier t : values()) if (t.name().equalsIgnoreCase(s.trim())) return t;
            return BAD;
        }

        /** Can a telescope fitted with {@code fitted} resolve something needing this tier? */
        public boolean reachedBy(LensTier fitted) {
            return fitted != null && fitted.ordinal() >= this.ordinal();
        }
    }

    /** The bare type name, without its translation-key prefix: "nebula", "quasar", "star". */
    public String type() {
        int dot = typeKey == null ? -1 : typeKey.lastIndexOf('.');
        return dot < 0 ? "unknown" : typeKey.substring(dot + 1);
    }

    /**
     * Read one object out of its JSON.
     *
     * @return the object, or null if the file is not one we can use - the caller counts those and
     *         says so once rather than failing
     */
    public static SkyObject from(ResourceLocation id, JsonObject o) {
        if (o == null) return null;
        JsonObject sky = o.has("sky") && o.get("sky").isJsonObject() ? o.getAsJsonObject("sky") : null;
        List<Reward> rewards = new ArrayList<>();
        if (o.has("rewards") && o.get("rewards").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("rewards");
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isJsonObject()) continue;
                JsonObject r = arr.get(i).getAsJsonObject();
                ResourceLocation item = loc(str(r, "id", ""));
                if (item == null) continue;
                rewards.add(new Reward(item, Math.max(1, num(r, "count", 1).intValue())));
            }
        }
        return new SkyObject(
                id,
                str(o, "name_key", id.getPath()),
                str(o, "description_key", ""),
                str(o, "type_key", "type.unknown"),
                LensTier.of(str(o, "required_tier", "bad")),
                Math.max(0.0, num(o, "appearance_chance", 0.05).doubleValue()),
                Math.max(1, num(o, "size", 16).intValue()),
                Math.max(20, num(o, "base_analysis_ticks", 1200).intValue()),
                str(o, "catalog_texture", ""),
                List.copyOf(rewards),
                sky == null ? 0f : num(sky, "yaw", 0).floatValue(),
                sky == null ? 0f : num(sky, "pitch", 0).floatValue(),
                sky == null ? 8f : num(sky, "spread", 8).floatValue(),
                str(o, "wikipedia", ""));
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

    private static ResourceLocation loc(String s) {
        try {
            return s == null || s.isEmpty() ? null : ResourceLocation.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
