package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.ai.EntityAIWorkVoyager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

/**
 * The Voyager job. Remembers whether the citizen is away in the End and what the
 * expedition turned up: "finds" (raw results of the trip recipe - adventure tokens,
 * blocks to mine, items) and "haul" (what survived processing and goes home).
 */
public class JobVoyager extends AbstractJobCrafter<EntityAIWorkVoyager, JobVoyager> {

    private static final String TAG_AWAY = "away";
    private static final String TAG_FINDS = "finds";
    private static final String TAG_HAUL = "haul";

    private boolean away = false;
    /** Transient: the send-off / homecoming show is running and the AI waits for its key moment. */
    private boolean countingDown = false;
    /** Transient: the Voyager gave up walking to the departure point (shown as an interaction). */
    private boolean departureBlocked = false;
    private final Queue<ItemStack> finds = new LinkedList<>();
    private final Queue<ItemStack> haul = new LinkedList<>();
    /** Transient: what the Voyager is up to right now, for other mods (Colonist Errands reads it). */
    private Status status = Status.IDLE;
    private String statusLine = "";

    /** The Voyager's current situation in plain words; {@link #getStatusLine()} has the details. */
    public enum Status {
        /** Nothing to do yet (just hired, or between decisions). */
        IDLE,
        /** Fetching rations from the hut for the next trip. */
        PACKING,
        /** The expedition supplies (cobblestone, pearls, torches) are not in the hut yet. */
        WAITING_SUPPLIES,
        /** Missing a pickaxe or a sword of an allowed tier. */
        WAITING_TOOLS,
        /** Supplies are in the colony but no expedition plan can be fulfilled yet. */
        WAITING_PLAN,
        /** All set, but the launch window is closed until the next one opens. */
        WAITING_WINDOW,
        /** Launchpad only: the rocket is out with the other crew member. */
        WAITING_ROCKET,
        /** On the way to the rocket or the dais; the send-off may be running. */
        BOARDING,
        /** Out in the End. */
        AWAY,
        /** Back on the pad, unloading the haul. */
        RETURNING
    }

    public JobVoyager(final ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public EntityAIWorkVoyager generateAI() {
        return new EntityAIWorkVoyager(this);
    }

    @Override
    public @NotNull ResourceLocation getModel() {
        return Voyager.MODEL_ID;
    }

    /** No colds while off-world. */
    @Override
    public double getDiseaseModifier() {
        if (getCitizen().getEntity().isPresent() && getCitizen().getEntity().get().isInvisible()) {
            return 0.0;
        }
        return super.getDiseaseModifier();
    }

    /** Waiting for the next launch window is normal, not a reason to nag. */
    @Override
    public int getIdleSeverity(final boolean isDemand) {
        return isDemand ? super.getIdleSeverity(true) : 4;
    }

    public boolean isAway() {
        return away;
    }

    public Status getStatus() {
        return status;
    }

    /** The last status line the AI logged for this Voyager (English, e.g. "waiting for expedition supplies"). */
    public String getStatusLine() {
        return statusLine;
    }

    public void setStatus(final Status status, final String line) {
        this.status = status == null ? Status.IDLE : status;
        this.statusLine = line == null ? "" : line;
    }

    public void setAway(final boolean away) {
        this.away = away;
    }

    public boolean isCountingDown() {
        return countingDown;
    }

    public void setCountingDown(final boolean countingDown) {
        this.countingDown = countingDown;
    }

    public boolean isDepartureBlocked() {
        return departureBlocked;
    }

    public void setDepartureBlocked(final boolean departureBlocked) {
        this.departureBlocked = departureBlocked;
    }

    public Queue<ItemStack> getFinds() {
        return finds;
    }

    public void addFinds(final Collection<ItemStack> stacks) {
        finds.addAll(stacks);
    }

    public Queue<ItemStack> getHaul() {
        return haul;
    }

    public void addHaul(final Collection<ItemStack> stacks) {
        haul.addAll(stacks);
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag compound = super.serializeNBT(provider);
        compound.putBoolean(TAG_AWAY, away);
        compound.put(TAG_FINDS, writeStacks(finds, provider));
        compound.put(TAG_HAUL, writeStacks(haul, provider));
        return compound;
    }

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag compound) {
        super.deserializeNBT(provider, compound);
        away = compound.getBoolean(TAG_AWAY);
        readStacks(compound.getList(TAG_FINDS, Tag.TAG_COMPOUND), finds, provider);
        readStacks(compound.getList(TAG_HAUL, Tag.TAG_COMPOUND), haul, provider);
    }

    private static ListTag writeStacks(final Queue<ItemStack> stacks, final HolderLookup.Provider provider) {
        final ListTag list = new ListTag();
        for (final ItemStack stack : stacks) {
            list.add(stack.saveOptional(provider));
        }
        return list;
    }

    private static void readStacks(final ListTag list, final Queue<ItemStack> into, final HolderLookup.Provider provider) {
        into.clear();
        for (int i = 0; i < list.size(); i++) {
            into.add(ItemStack.parseOptional(provider, list.getCompound(i)));
        }
    }
}
