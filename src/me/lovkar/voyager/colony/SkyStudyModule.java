package me.lovkar.voyager.colony;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.util.MessageUtils;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.sky.SkyStudies;
import me.lovkar.voyager.sky.SkyStudy;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Observatory's own study of the sky - its research, and nothing to do with the University's.
 *
 * <p>Why a system of its own rather than a branch of MineColonies' tree: the University pays for
 * research with <b>time</b>, and a researcher standing in a room is enough. An Observatory should
 * pay with <b>nights</b>. A study here advances once per night the astronomer actually keeps the
 * watch - a clouded night buys nothing, a colony with nobody on the roof buys nothing, and the
 * thing the building already produces is the currency. That is not something the University's
 * timer can express, and it is the reason the Observatory is worth building.</p>
 *
 * <p>One study at a time. The book is stored per building and read across the colony, the same
 * way the plate catalogue is, so a second Observatory shares what the first learned.</p>
 */
public class SkyStudyModule extends AbstractBuildingModule implements IPersistentModule {

    /** Where a study stands for this colony. */
    public enum Status {
        DONE, IN_PROGRESS, GATHERING, AVAILABLE, NEEDS_LEVEL, NEEDS_PARENT, BUSY;

        public boolean startable() {
            return this == AVAILABLE;
        }

        public String key() {
            return "com.voyager.study.status." + name().toLowerCase();
        }
    }

    private static final String NBT_DONE = "done";
    private static final String NBT_CURRENT = "current";
    private static final String NBT_WANTED = "wanted";
    private static final String NBT_NIGHTS = "nights_done";

    /** Studies finished here, and the level each granted. */
    private final Map<ResourceLocation, Double> done = new LinkedHashMap<>();
    private @Nullable ResourceLocation current;
    /** Chosen, not yet paid for: the astronomer is fetching what it costs. */
    private @Nullable ResourceLocation wanted;
    private int nightsDone;

    // ------------------------------------------------------------------ reading

    public Map<ResourceLocation, Double> finished() {
        return done;
    }

    public @Nullable ResourceLocation current() {
        return current;
    }

    /** The study the colony has chosen but not yet gathered the materials for. */
    public @Nullable ResourceLocation wanted() {
        return wanted;
    }

    public int nightsDone() {
        return nightsDone;
    }

    /** How far along the current study is, 0..1, or 0 if none is running. */
    public double progress() {
        final SkyStudy study = SkyStudies.byId(current);
        return study == null ? 0.0 : Math.min(1.0, nightsDone / (double) study.nights());
    }

    /** The best level any finished study grants for this effect - 0 if none does. */
    public double strength(final String effect) {
        double best = 0.0;
        for (final Map.Entry<ResourceLocation, Double> entry : done.entrySet()) {
            final SkyStudy study = SkyStudies.byId(entry.getKey());
            if (study != null && study.effect().equals(effect)) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best;
    }

    public Status statusOf(final SkyStudy study) {
        if (study == null) {
            return Status.NEEDS_LEVEL;
        }
        if (done.containsKey(study.id())) {
            return Status.DONE;
        }
        if (study.id().equals(current)) {
            return Status.IN_PROGRESS;
        }
        if (study.id().equals(wanted)) {
            return Status.GATHERING;
        }
        if (building == null || building.getBuildingLevel() < study.tier()) {
            return Status.NEEDS_LEVEL;
        }
        if (study.parent() != null && !done.containsKey(study.parent())) {
            return Status.NEEDS_PARENT;
        }
        return current != null || wanted != null ? Status.BUSY : Status.AVAILABLE;
    }

    // ------------------------------------------------------------------ writing

    /**
     * Begin a study. Server side only; the costs come out of the player's own pockets, because
     * somebody has to carry the lens up the hill.
     *
     * @return the reason it could not start, or null if it did
     */
    public @Nullable String start(final SkyStudy study, final ServerPlayer player) {
        final Status status = statusOf(study);
        if (!status.startable()) {
            return status.key();
        }
        wanted = study.id();
        nightsDone = 0;
        markDirty();
        Voyager.LOGGER.info("[Observatory] {} chose the study of {}; the astronomer will fetch what it costs",
                player.getGameProfile().getName(), study.id());
        return null;
    }

    /**
     * The materials are on the shelf: take them and start counting nights.
     *
     * <p>Called by the astronomer, not by the player. The colony buys its own studies - the costs
     * come out of the Observatory's own racks, and if they are not there the astronomer puts in a
     * request and a courier brings them. A player carrying a lens up the hill was the opposite of
     * what this building is for.</p>
     */
    public boolean beginIfPaid(final List<ItemStack> taken) {
        final SkyStudy study = SkyStudies.byId(wanted);
        if (study == null || current != null) {
            return false;
        }
        current = study.id();
        wanted = null;
        nightsDone = 0;
        markDirty();
        Voyager.LOGGER.info("[Observatory] the study of {} has begun ({} nights), paid with {}",
                study.id(), study.nights(), taken.size() + " stack(s)");
        return true;
    }

    /** The colony changed its mind, or the study went out of the datapack. */
    public void forgetWanted() {
        if (wanted != null) {
            wanted = null;
            markDirty();
        }
    }

    /**
     * One more night on the book. Called by the astronomer when a watch is credited - not by a
     * timer, which is the whole point.
     */
    public void nightWorked(final IColony colony) {
        final SkyStudy study = SkyStudies.byId(current);
        if (study == null) {
            if (current != null) {
                current = null;                  // the datapack dropped it out from under us
                markDirty();
            }
            return;
        }
        nightsDone++;
        if (nightsDone < study.nights()) {
            markDirty();
            return;
        }
        done.put(study.id(), study.level());
        current = null;
        nightsDone = 0;
        markDirty();
        Voyager.LOGGER.info("[Observatory] the study of {} is finished ({} at {})",
                study.id(), study.effect(), study.level());
        if (colony != null) {
            MessageUtils.format(Component.translatable("com.voyager.study.finished",
                            Component.translatable(study.nameKey())))
                    .sendTo(colony).forAllPlayers();
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag tag) {
        done.clear();
        final ListTag list = tag.getList(NBT_DONE, Tag.TAG_COMPOUND);
        for (final Tag entry : list) {
            final CompoundTag compound = (CompoundTag) entry;
            try {
                done.put(ResourceLocation.parse(compound.getString("id")), compound.getDouble("level"));
            } catch (final RuntimeException unreadable) {
                // a study whose id we can no longer parse is simply forgotten
            }
        }
        final String running = tag.getString(NBT_CURRENT);
        current = running == null || running.isEmpty() ? null : ResourceLocation.tryParse(running);
        final String chosen = tag.getString(NBT_WANTED);
        wanted = chosen == null || chosen.isEmpty() ? null : ResourceLocation.tryParse(chosen);
        nightsDone = tag.getInt(NBT_NIGHTS);
    }

    @Override
    public void serializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag tag) {
        final ListTag list = new ListTag();
        for (final Map.Entry<ResourceLocation, Double> entry : done.entrySet()) {
            final CompoundTag compound = new CompoundTag();
            compound.putString("id", entry.getKey().toString());
            compound.putDouble("level", entry.getValue());
            list.add(compound);
        }
        tag.put(NBT_DONE, list);
        tag.putString(NBT_CURRENT, current == null ? "" : current.toString());
        tag.putString(NBT_WANTED, wanted == null ? "" : wanted.toString());
        tag.putInt(NBT_NIGHTS, nightsDone);
    }

    /**
     * The client gets the whole book with the building.
     *
     * <p>Datapack contents do not reach a client on their own - the studies are read by a server
     * reload listener - so they travel here instead. It is a few hundred bytes and it means a
     * dedicated server's packs decide what the GUI shows, which is the right way round.</p>
     */
    @Override
    public void serializeToView(final @NotNull RegistryFriendlyByteBuf buf) {
        final List<SkyStudy> book = new ArrayList<>(SkyStudies.all().values());
        buf.writeVarInt(book.size());
        for (final SkyStudy study : book) {
            study.write(buf);
        }
        buf.writeVarInt(done.size());
        for (final Map.Entry<ResourceLocation, Double> entry : done.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeDouble(entry.getValue());
        }
        buf.writeBoolean(current != null);
        if (current != null) {
            buf.writeResourceLocation(current);
        }
        buf.writeBoolean(wanted != null);
        if (wanted != null) {
            buf.writeResourceLocation(wanted);
        }
        buf.writeVarInt(nightsDone);
        buf.writeVarInt(building == null ? 0 : building.getBuildingLevel());
    }
}
