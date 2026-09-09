package me.lovkar.voyager.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

/**
 * The Observatory's book of studies, read out of datapacks.
 *
 * <p>{@code data/&lt;namespace&gt;/sky_study/*.json} - ours by default, anybody's if they write
 * them. Same listener, same contract and same failure mode as {@link SkyData}: a file we cannot
 * read is skipped and counted, never thrown.</p>
 */
public final class SkyStudies extends SimpleJsonResourceReloadListener {

    public static final String DIR = "sky_study";
    private static final Gson GSON = new Gson();
    private static final SkyStudies INSTANCE = new SkyStudies();

    private static Map<ResourceLocation, SkyStudy> studies = Map.of();
    private static int skipped;

    private SkyStudies() {
        super(GSON, DIR);
    }

    public static SkyStudies listener() {
        return INSTANCE;
    }

    @Override
    protected void apply(final Map<ResourceLocation, JsonElement> files, final ResourceManager manager,
                         final ProfilerFiller profiler) {
        final Map<ResourceLocation, SkyStudy> read = new LinkedHashMap<>();
        int bad = 0;
        for (final Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            final SkyStudy study = entry.getValue() != null && entry.getValue().isJsonObject()
                    ? SkyStudy.from(entry.getKey(), (JsonObject) entry.getValue()) : null;
            if (study == null) {
                bad++;
            } else {
                read.put(study.id(), study);
            }
        }
        studies = read;
        skipped = bad;
        SkyData.LOG.info("[study] {} studies read{}", read.size(), bad > 0 ? ", " + bad + " unreadable" : "");
    }

    /** Everything, in the order the Observatory should show it: branch, then sort order, then tier. */
    public static List<SkyStudy> ordered() {
        final List<SkyStudy> all = new ArrayList<>(studies.values());
        all.sort(Comparator.comparing(SkyStudy::branch)
                .thenComparingInt(SkyStudy::sortOrder)
                .thenComparingInt(SkyStudy::tier)
                .thenComparing(s -> s.id().toString()));
        return all;
    }

    public static @Nullable SkyStudy byId(final ResourceLocation id) {
        return id == null ? null : studies.get(id);
    }

    public static Map<ResourceLocation, SkyStudy> all() {
        return studies;
    }

    public static boolean ready() {
        return !studies.isEmpty();
    }

    /** The client is told the whole book once per building packet; this replaces it. */
    public static void receive(final List<SkyStudy> read) {
        final Map<ResourceLocation, SkyStudy> map = new LinkedHashMap<>();
        for (final SkyStudy study : read) {
            map.put(study.id(), study);
        }
        studies = map;
    }

    public static String summary() {
        if (studies.isEmpty()) {
            return "no sky studies in any datapack - the Observatory has nothing to work towards"
                    + (skipped > 0 ? " (" + skipped + " file(s) unreadable)" : "");
        }
        final Map<String, Integer> byBranch = new LinkedHashMap<>();
        for (final SkyStudy study : studies.values()) {
            byBranch.merge(study.branch(), 1, Integer::sum);
        }
        return studies.size() + " sky study/ies " + byBranch + (skipped > 0 ? ", " + skipped + " unreadable" : "");
    }
}
