package me.lovkar.voyager.sky;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everything the colony knows about the night sky, read out of datapacks.
 *
 * <p><b>Why it is written this way.</b> Exposure: Space is where the sky comes from, and its author
 * told us plainly that the only surface he supports is the data: "it only lets you interact with
 * space objects - adding, removing, etc. - for datapacks and configs, and that's the extent of it."
 * His Java classes are internals that may change at any release. So this reads
 * {@code data/<namespace>/cosmic_object/*.json} and {@code .../cosmic_event/*.json} with vanilla's
 * own reload listener and never touches a class of his.</p>
 *
 * <p>Three things fall out of that, all of them good:</p>
 * <ul>
 *   <li>an update of his mod cannot break ours;</li>
 *   <li>the Observatory sees objects from <b>any</b> datapack - his, ours, or a third party's;</li>
 *   <li>with his mod absent the map is simply empty, and every feature that needs it turns itself
 *       off instead of crashing.</li>
 * </ul>
 *
 * <p><b>The one thing we cannot read is which event is running tonight.</b> That lives in his saved
 * data, and it is internal. So {@link #tonight} rolls its own, deterministically from the world seed
 * and the day, using the weights out of his event files. It will not always agree with what his
 * Night Analyzer says - a known divergence, written down rather than hidden, and the thing to ask
 * him to expose one day.</p>
 */
public final class SkyData extends SimpleJsonResourceReloadListener {
    public static final Logger LOG = LoggerFactory.getLogger("Voyager/Sky");

    private static final Gson GSON = new Gson();
    /** The datapack directories. These are his names, and they are the whole contract. */
    public static final String OBJECT_DIR = "cosmic_object";
    public static final String EVENT_DIR = "cosmic_event";

    private static final SkyData OBJECTS = new SkyData(OBJECT_DIR);
    private static final SkyData EVENTS = new SkyData(EVENT_DIR);

    private static Map<ResourceLocation, SkyObject> objects = Map.of();
    private static List<SkyEvent> events = List.of();
    /** Files we could not make sense of, counted so the log can say so once. */
    private static int skipped;

    private final String dir;

    private SkyData(String dir) {
        super(GSON, dir);
        this.dir = dir;
    }

    /** Both listeners, to hand to {@code AddReloadListenerEvent}. */
    public static List<SkyData> listeners() {
        return List.of(OBJECTS, EVENTS);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        if (OBJECT_DIR.equals(dir)) {
            Map<ResourceLocation, SkyObject> read = new LinkedHashMap<>();
            int bad = 0;
            for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
                SkyObject o = e.getValue() != null && e.getValue().isJsonObject()
                        ? SkyObject.from(e.getKey(), (JsonObject) e.getValue()) : null;
                if (o == null) bad++;
                else read.put(e.getKey(), o);
            }
            objects = Collections.unmodifiableMap(read);
            skipped = bad;
        } else {
            List<SkyEvent> read = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
                SkyEvent v = e.getValue() != null && e.getValue().isJsonObject()
                        ? SkyEvent.from(e.getKey(), (JsonObject) e.getValue()) : null;
                if (v != null) read.add(v);
            }
            read.sort((a, b) -> a.id().compareTo(b.id()));
            events = List.copyOf(read);
        }
    }

    // ---- what the Observatory asks -----------------------------------------------------------

    public static Map<ResourceLocation, SkyObject> all() {
        return objects;
    }

    public static SkyObject byId(ResourceLocation id) {
        return objects.get(id);
    }

    public static List<SkyEvent> events() {
        return events;
    }

    public static boolean ready() {
        return !objects.isEmpty();
    }

    /** Everything a telescope with this lens fitted could resolve, event-exclusives aside. */
    public static List<SkyObject> reachableBy(SkyObject.LensTier fitted) {
        List<SkyObject> out = new ArrayList<>();
        for (SkyObject o : objects.values()) if (o.tier().reachedBy(fitted)) out.add(o);
        return out;
    }

    /**
     * Which event, if any, is running on this night.
     *
     * <p>Deterministic from the world seed and the day number, so every part of the colony agrees
     * with itself and a reload does not reshuffle the sky. The weights are his; the roll is ours,
     * for the reason in the class comment.</p>
     *
     * @return the event, or null for an ordinary night
     */
    public static SkyEvent tonight(Level level) {
        if (events.isEmpty() || level == null) return null;
        long day = level.getDayTime() / 24000L;
        long seed = level.getServer() == null ? 0L : level.getServer().overworld().getSeed();
        Random r = new Random(seed * 31L + day * 1000003L);
        int total = 0;
        for (SkyEvent e : events) total += e.weight();
        // Most nights are ordinary. The events' own weights are small numbers against this floor,
        // so a sky event stays an occasion rather than a rota.
        int ordinary = Math.max(1, total * 3);
        int roll = r.nextInt(total + ordinary);
        if (roll >= total) return null;
        for (SkyEvent e : events) {
            roll -= e.weight();
            if (roll < 0) return e;
        }
        return null;
    }

    /** One line for the log, and the whole of the spike's answer. */
    public static String summary() {
        if (objects.isEmpty()) {
            return "no cosmic objects in any datapack - the Observatory stays shut"
                    + (skipped > 0 ? " (" + skipped + " file(s) unreadable)" : "");
        }
        Map<String, Integer> byTier = new LinkedHashMap<>();
        for (SkyObject.LensTier t : SkyObject.LensTier.values()) byTier.put(t.name().toLowerCase(), 0);
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byNamespace = new LinkedHashMap<>();
        for (SkyObject o : objects.values()) {
            byTier.merge(o.tier().name().toLowerCase(), 1, Integer::sum);
            byType.merge(o.type(), 1, Integer::sum);
            byNamespace.merge(o.id().getNamespace(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(objects.size()).append(" cosmic object(s) from ").append(byNamespace)
          .append(", ").append(events.size()).append(" event(s)");
        if (skipped > 0) sb.append(", ").append(skipped).append(" unreadable");
        sb.append("\n  by lens tier: ").append(byTier);
        sb.append("\n  by type: ").append(byType);
        return sb.toString();
    }
}
