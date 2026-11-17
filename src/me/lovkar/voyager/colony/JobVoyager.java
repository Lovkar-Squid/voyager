package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.ai.EntityAIWorkVoyager;
import me.lovkar.voyager.trophy.TripTally;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
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
    private static final String TAG_DRIVEN_BACK = "driven_back";
    private static final String TAG_EXPEDITION = "expedition";
    private static final String TAG_TALLY = "trip_fights";
    private static final String TAG_TROPHIES_ROLLED = "trophies_rolled";
    private static final String TAG_HOMEWARD = "homeward";
    private static final String TAG_LANDED = "landed";

    private boolean away = false;
    /**
     * Permanent death off: this trip ended in a retreat and the Voyager is on the way home wounded.
     * Saved, so a reload on the way home keeps both the wound and the record straight.
     */
    private boolean drivenBack = false;
    /** Transient: the send-off / homecoming show is running and the AI waits for its key moment. */
    private boolean countingDown = false;
    /** Transient: the Voyager gave up walking to the departure point (shown as an interaction). */
    private boolean departureBlocked = false;
    /**
     * This trip has turned for home (finished, or driven back). Lives on the job, not the AI, because
     * the AI is made anew when it restarts on the way home; it keeps the "finished the expedition" log
     * line to one per trip. Saved, so a reload on the way home does not announce the trip again (an
     * older save has no key and reads as false: at worst the line comes once more).
     */
    private boolean homeward = false;
    /**
     * The Voyager has stepped out of the landing (or the gate) on this trip, and only the walk to the hut
     * is left. Set when they appear, cleared when they reach the hut and when a new trip starts. Saved:
     * an End Gate has nothing else to show for it (a Launchpad has its rocket back on the pad), and an AI
     * restarted on that walk - MineColonies' care for a hurt colonist below 6 hp does it to every
     * driven-back Voyager, a reload does it to anyone - must walk on, not go through the arrival again.
     */
    private boolean landed = false;
    private final Queue<ItemStack> finds = new LinkedList<>();
    /** A list, not just a queue: the trophies go in at the front, so they are packed first. */
    private final LinkedList<ItemStack> haul = new LinkedList<>();
    /** The Departure Point's number for the trip under way (0: a trip begun before 0.4.0 counted them). */
    private int expedition = 0;
    /** What happened on the trip so far (the fights won), for the trophies' story. Saved. */
    private final TripTally tally = new TripTally();
    /**
     * The trophies of this trip were rolled (or, after a retreat, never will be). Saved with the haul
     * they went into, so a reload can neither roll them twice nor lose the roll.
     */
    private boolean trophiesRolled = false;
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

    public boolean isDrivenBack() {
        return drivenBack;
    }

    public void setDrivenBack(final boolean drivenBack) {
        this.drivenBack = drivenBack;
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

    public boolean isHomeward() {
        return homeward;
    }

    public void setHomeward(final boolean homeward) {
        this.homeward = homeward;
    }

    public boolean isLanded() {
        return landed;
    }

    public void setLanded(final boolean landed) {
        this.landed = landed;
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

    /** Puts these at the front of the haul: packed before everything else, while the backpack has room. */
    public void addHaulFirst(final List<ItemStack> stacks) {
        haul.addAll(0, stacks);
    }

    /** A new expedition sets off: its number, nothing happened yet, no trophies yet, not homeward or landed yet. */
    public void startTrip(final int expedition) {
        this.expedition = expedition;
        tally.clear();
        trophiesRolled = false;
        homeward = false;
        landed = false;
    }

    public int getExpedition() {
        return expedition;
    }

    public void setExpedition(final int expedition) {
        this.expedition = expedition;
    }

    public TripTally getTally() {
        return tally;
    }

    public boolean isTrophiesRolled() {
        return trophiesRolled;
    }

    public void setTrophiesRolled(final boolean trophiesRolled) {
        this.trophiesRolled = trophiesRolled;
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag compound = super.serializeNBT(provider);
        compound.putBoolean(TAG_AWAY, away);
        compound.putBoolean(TAG_DRIVEN_BACK, drivenBack);
        compound.put(TAG_FINDS, writeStacks(finds, provider));
        compound.put(TAG_HAUL, writeStacks(haul, provider));
        compound.putInt(TAG_EXPEDITION, expedition);
        compound.put(TAG_TALLY, tally.save());
        compound.putBoolean(TAG_TROPHIES_ROLLED, trophiesRolled);
        compound.putBoolean(TAG_HOMEWARD, homeward);
        compound.putBoolean(TAG_LANDED, landed);
        return compound;
    }

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag compound) {
        super.deserializeNBT(provider, compound);
        away = compound.getBoolean(TAG_AWAY);
        drivenBack = compound.getBoolean(TAG_DRIVEN_BACK);
        readStacks(compound.getList(TAG_FINDS, Tag.TAG_COMPOUND), finds, provider);
        readStacks(compound.getList(TAG_HAUL, Tag.TAG_COMPOUND), haul, provider);
        expedition = compound.getInt(TAG_EXPEDITION);
        tally.load(compound.getList(TAG_TALLY, TripTally.listType()));
        trophiesRolled = compound.getBoolean(TAG_TROPHIES_ROLLED)
                || pastTheFightsBeforeTallies(compound.contains(TAG_TALLY, Tag.TAG_LIST), away, finds.size());
        homeward = compound.getBoolean(TAG_HOMEWARD);
        landed = compound.getBoolean(TAG_LANDED);
    }

    /**
     * A trip saved by 0.3.9 (no tally in the save at all - a 0.4.0 save always writes one, empty or
     * not) after its last find was lived through: the fights happened, but nobody counted them, and
     * trophies rolled now would tell a false story ("A quiet crossing", never the Dragon's Wake). Such
     * a trip comes home with its haul and no trophy, as it would have under 0.3.9. A 0.3.9 trip with
     * finds still ahead keeps its roll: the fights still to come are counted.
     */
    public static boolean pastTheFightsBeforeTallies(final boolean hasTally, final boolean away, final int findsLeft) {
        return !hasTally && away && findsLeft == 0;
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
