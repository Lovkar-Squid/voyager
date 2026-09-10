package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.ai.EntityAIWorkAstronomer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The Astronomer. Works at night, sleeps by day, and is the only colonist in the town whose
 * shift is the opposite of everybody else's.
 *
 * <p>The status field is here for the same reason JobVoyager has one: Colonist Errands reads it,
 * and "what is this colonist doing right now" is worth answering in one word.</p>
 */
public class JobAstronomer extends AbstractJob<EntityAIWorkAstronomer, JobAstronomer> {

    /** What the astronomer is up to, in one word. */
    public enum Status {
        /** Nothing to do - daytime, or between decisions. */
        IDLE,
        /** On the way to the instrument. */
        WALKING,
        /** At the instrument, watching. */
        OBSERVING,
        /** The sky is closed: rain, snow, or a roof over the instrument. */
        CLOUDED,
        /** Cannot reach the instrument at all. */
        BLOCKED
    }

    private static final String NBT_NIGHT_KEPT = "night_kept";
    private static final String NBT_NIGHT_CREDITED = "night_credited";

    private Status status = Status.IDLE;
    /** What they are doing, in a sentence, for anyone who asks (Colonist Errands does). */
    private String statusLine = "";
    /** The last night this astronomer actually kept the watch, by the world's day count. */
    private long nightKept = -1L;
    /** The night whose plate is already in the book, so a restart mid-night does not pay twice. */
    private long nightCredited = -1L;

    public JobAstronomer(final ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public EntityAIWorkAstronomer generateAI() {
        return new EntityAIWorkAstronomer(this);
    }

    @Override
    public @NotNull ResourceLocation getModel() {
        return Voyager.ASTRONOMER_MODEL_ID;
    }

    /**
     * The astronomer works nights. MineColonies asks this to decide whether the citizen should be
     * at work, and the honest answer for this job is "when everybody else is in bed".
     */
    @Override
    public boolean canAIBeInterrupted() {
        return status != Status.OBSERVING;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    /** The status and, in plain English, what it means tonight - "keeping the watch from the lookout". */
    public void setStatus(final Status status, final String line) {
        this.status = status == null ? Status.IDLE : status;
        this.statusLine = line == null ? "" : line;
    }

    /**
     * The last line the AI set for this astronomer, e.g. "keeping the watch at the instrument" or
     * "caught the Crab Nebula tonight - a first for the colony". English; read by Colonist Errands
     * through reflection, the way it reads {@link JobVoyager#getStatusLine()}.
     */
    public String getStatusLine() {
        return statusLine;
    }

    /** True while the astronomer is standing at the instrument and should not be disturbed. */
    public boolean isObserving() {
        return status == Status.OBSERVING;
    }

    /** True while the astronomer has given up walking to the instrument (shown as an interaction). */
    public boolean isBlocked() {
        return status == Status.BLOCKED;
    }

    // ------------------------------------------------------------------ the night's work

    /** Tonight is in the book: the astronomer may go to bed. */
    public void keptWatch(final long night) {
        nightKept = night;
    }

    /**
     * Has tonight's watch already been kept?
     *
     * <p>Read by the night-shift mixin as well as by the AI: once the plate is exposed there is
     * nothing left to look at, so the astronomer is allowed to sleep the rest of the night rather
     * than stand on the roof until dawn.</p>
     */
    public boolean keptWatchTonight(final Level level) {
        return level != null && nightKept == level.getGameTime() / 24000L;
    }

    /** Tonight's plate is taken (the astronomer may still be on the way home). */
    public void creditedNight(final long night) {
        nightCredited = night;
    }

    public boolean creditedTonight(final Level level) {
        return level != null && nightCredited == level.getGameTime() / 24000L;
    }

    public long creditedNight() {
        return nightCredited;
    }

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        nightKept = tag.contains(NBT_NIGHT_KEPT) ? tag.getLong(NBT_NIGHT_KEPT) : -1L;
        nightCredited = tag.contains(NBT_NIGHT_CREDITED) ? tag.getLong(NBT_NIGHT_CREDITED) : -1L;
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag tag = super.serializeNBT(provider);
        tag.putLong(NBT_NIGHT_KEPT, nightKept);
        tag.putLong(NBT_NIGHT_CREDITED, nightCredited);
        return tag;
    }
}
