package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import java.util.HashSet;
import java.util.Set;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.compat.TradePostLedger;
import me.lovkar.voyager.photo.ColonyCamera;
import me.lovkar.voyager.photo.VisitorSitting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Photo Booth: a studio with a camera stand in it and a darkroom behind it.
 *
 * <p>Three things happen here. The Photographer crafts everything Exposure makes, so the colony
 * never hand-crafts film again. When the bench is quiet they photograph the colony - a colonist who
 * happens by, or, from level two, a <b>visitor who came in for a portrait</b> and pays for it in
 * Trade Post coins. And every time the builder finishes a building or an upgrade the photographer
 * goes out and photographs it for the <b>colony chronicle</b>, an Exposure album on the shelf that
 * fills up page by page with the colony's own history and is signed as a volume when it is full.</p>
 */
public class BuildingPhotoBooth extends AbstractBuilding {

    public static final int MAX_LEVEL = 5;
    /** Visitors start coming in for portraits at this level. */
    public static final int SITTINGS_LEVEL = 2;
    /** The blueprint tags the Lightroom: where film becomes a photograph. */
    public static final String TAG_DARKROOM = "darkroom";
    /** The blueprint tags the studio floor, where the camera stand waits for a sitter. */
    public static final String TAG_STUDIO = "studio";
    /** The blueprint tags the mark a sitter stands on, in front of the gallery wall. */
    public static final String TAG_SITTER = "sitter";
    /** The blueprint tags the square behind the tripod the photographer shoots from. */
    public static final String TAG_PHOTOGRAPHER = "photographer";
    /** A booking nobody showed up for is dropped after this long. */
    private static final long BOOKING_TIMEOUT = 3000L;
    /** Pages in an Exposure album; a full one is signed as a volume. */
    private static final int ALBUM_PAGES = 16;

    private static final String NBT_SOLD = "portraits_sold";
    private static final String NBT_EARNED = "earned";
    private static final String NBT_VOLUMES = "volumes";
    private static final String NBT_CHRONICLE = "chronicle";
    private static final String STAT_SOLD = "portraits_sold";
    private static final String STAT_CHRONICLE = "chronicle_photographs";

    /** The chronicle photographs a build twice: halfway up, and finished. */
    public static final String PHASE_DONE = "";
    public static final String PHASE_HALFWAY = "halfway";

    /** A building the chronicle still owes a photograph. */
    public record ChronicleJob(BlockPos pos, int level, int day, String phase) {
        public boolean halfway() {
            return PHASE_HALFWAY.equals(phase);
        }

        CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            tag.putInt("level", level);
            tag.putInt("day", day);
            tag.putString("phase", phase == null ? PHASE_DONE : phase);
            return tag;
        }

        static @Nullable ChronicleJob load(final CompoundTag tag) {
            return NbtUtils.readBlockPos(tag, "pos")
                    .map(p -> new ChronicleJob(p, tag.getInt("level"), tag.getInt("day"), tag.getString("phase")))
                    .orElse(null);
        }
    }

    // sittings
    private @Nullable Integer sitterId;
    private long bookedAt;
    private boolean sitterAtMark;
    private boolean sittingDone;
    private final Map<Integer, WeakReference<Entity>> invited = new HashMap<>();
    /** Set by the photographer's AI: the last time it looked, there was a camera on the shelf. */
    private boolean cameraSeen;
    private int portraitsSold;
    private long earned;

    // the chronicle
    private final List<ChronicleJob> chronicle = new ArrayList<>();
    private int volumes;
    /** Work orders already photographed halfway, so each build gets one progress picture. */
    private final Set<Integer> halfwayShot = new HashSet<>();
    private static final String NBT_HALFWAY = "halfway_shot";

    public BuildingPhotoBooth(final IColony colony, final BlockPos pos) {
        super(colony, pos);
    }

    /** Which of the five looks was built, taken from the blueprint path, like the Observatory. */
    @Override
    public @NotNull String getSchematicName() {
        final String path = getBlueprintPath();
        if (path != null) {
            for (final String look : new String[] {"keep", "sandcourt", "station", "array"}) {
                if (path.contains(look)) {
                    return look;
                }
            }
        }
        return "photobooth";
    }

    @Override
    public int getMaxBuildingLevel() {
        return MAX_LEVEL;
    }

    /** Two photographers from level 4: one at the bench, one in the dark. */
    public static int crewSize(final IBuilding building) {
        return building != null && building.getBuildingLevel() >= 4 ? 2 : 1;
    }

    /** The studio floor, where the camera stand waits and the photographer stands to shoot. */
    public BlockPos getStudioPosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_STUDIO);
        return tagged != null ? tagged : getPosition();
    }

    /** Where a sitter stands: the tagged mark, or failing that the studio floor itself. */
    public BlockPos getSitterPosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_SITTER);
        return tagged != null ? tagged : getStudioPosition();
    }

    /** Where the photographer stands to shoot: behind the tripod, or failing that beside it. */
    public BlockPos getPhotographerPosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_PHOTOGRAPHER);
        return tagged != null ? tagged : getStudioPosition();
    }

    public BlockPos getDarkroomPosition() {
        final BlockPos tagged = getFirstLocationFromTag(TAG_DARKROOM);
        return tagged != null ? tagged : getPosition();
    }

    // ------------------------------------------------------------------ the colony tick

    /**
     * Every colony tick (about twenty-five seconds): put the idea of a portrait into any visitor
     * who does not have it yet, and drop a booking nobody turned up for.
     */
    @Override
    public void onColonyTick(final IColony colony) {
        super.onColonyTick(colony);
        if (colony.getWorld() == null) {
            return;
        }
        final long now = colony.getWorld().getGameTime();
        if (sitterId != null && !sittingDone && now - bookedAt > BOOKING_TIMEOUT) {
            Voyager.LOGGER.debug("[Photo Booth] booking by visitor {} expired", sitterId);
            clearSitting();
        }
        if (getBuildingLevel() >= 1 && ColonyCamera.available()) {
            watchTheBuilders(colony);
        }
        if (getBuildingLevel() < SITTINGS_LEVEL || !ColonyCamera.available()) {
            return;
        }
        final Map<Integer, ICivilianData> visitors = colony.getVisitorManager().getCivilianDataMap();
        for (final ICivilianData data : visitors.values()) {
            if (!(data instanceof IVisitorData visitor) || visitor.getEntity().isEmpty()) {
                continue;
            }
            final Entity entity = visitor.getEntity().get();
            final WeakReference<Entity> known = invited.get(visitor.getId());
            if (known != null && known.get() == entity) {
                continue;                              // this entity already has the idea
            }
            VisitorSitting.attach(visitor, this);
            invited.put(visitor.getId(), new WeakReference<>(entity));
        }
        final Iterator<Integer> gone = invited.keySet().iterator();
        while (gone.hasNext()) {
            if (!visitors.containsKey(gone.next())) {
                gone.remove();                         // the visitor left the colony
            }
        }
    }

    // ------------------------------------------------------------------ sittings

    /** Level two, a photographer on the books, and a camera seen on the shelf. */
    public boolean isOpenForSittings() {
        return getBuildingLevel() >= SITTINGS_LEVEL && cameraSeen && ColonyCamera.available()
                && !getAllAssignedCitizen().isEmpty();
    }

    /** The photographer's AI reports whether the shelf had a camera, so visitors know to come. */
    public void noteCamera(final boolean present) {
        cameraSeen = present;
    }

    /** Reserve the chair. One sitter at a time. */
    public boolean book(final IVisitorData visitor) {
        if (sitterId != null || visitor == null || getColony().getWorld() == null) {
            return false;
        }
        sitterId = visitor.getId();
        bookedAt = getColony().getWorld().getGameTime();
        sitterAtMark = false;
        sittingDone = false;
        return true;
    }

    public boolean isBookedBy(final IVisitorData visitor) {
        return sitterId != null && visitor != null && sitterId == visitor.getId();
    }

    public void sitterArrived(final IVisitorData visitor) {
        if (isBookedBy(visitor)) {
            sitterAtMark = true;
            bookedAt = getColony().getWorld().getGameTime();
        }
    }

    /** Is somebody standing at the mark waiting to be photographed? */
    public boolean hasSitterWaiting() {
        return sitterId != null && sitterAtMark && !sittingDone;
    }

    /** The visitor at the mark, if they are there in the flesh. */
    public @Nullable LivingEntity sitterEntity() {
        if (sitterId == null) {
            return null;
        }
        final IVisitorData visitor = getColony().getVisitorManager().getVisitor(sitterId);
        if (visitor == null || visitor.getEntity().isEmpty()) {
            return null;
        }
        return visitor.getEntity().get() instanceof LivingEntity living ? living : null;
    }

    public @Nullable IVisitorData sitterData() {
        return sitterId == null ? null : getColony().getVisitorManager().getVisitor(sitterId);
    }

    public boolean isSittingDone(final IVisitorData visitor) {
        return isBookedBy(visitor) && sittingDone;
    }

    /** The visitor gave up, or left, or vanished. The chair is free. */
    public void cancelSitting(final IVisitorData visitor) {
        if (isBookedBy(visitor)) {
            clearSitting();
        }
    }

    /** The visitor has seen that their portrait was taken and is leaving with it. */
    public void forgetSitting(final IVisitorData visitor) {
        if (isBookedBy(visitor)) {
            clearSitting();
        }
    }

    private void clearSitting() {
        sitterId = null;
        sitterAtMark = false;
        sittingDone = false;
    }

    /**
     * The portrait is taken: the visitor pays for it and takes the print.
     *
     * <p>With Trade Post the colony is paid in its balance - the same number the Marketplace
     * fills - and the print leaves with the visitor (the negative stays on the roll, so the darkroom
     * can make another). Without an economy there is nothing to pay with, so the print goes on the
     * shelf like any other.</p>
     *
     * @param colour whether the print is in colour, which is worth more
     * @return the amount paid in balance units, or 0 when nothing was
     */
    public int completeSitting(final ItemStack print, final boolean colour, final ICitizenData photographer) {
        final IVisitorData visitor = sitterData();
        sittingDone = true;
        if (visitor == null) {
            return 0;
        }
        final int price = portraitPrice(colour);
        final boolean paid = TradePostLedger.credit(getColony(), price);
        if (paid) {
            portraitsSold++;
            earned += price;
            StatsUtil.trackStat(this, STAT_SOLD, 1);
            markDirty();
            MessageUtils.format(Component.translatable("com.voyager.photo.sold", visitor.getName(),
                            TradePostLedger.format(price), photographer == null ? "?" : photographer.getName()))
                    .sendTo(getColony()).forAllPlayers();
            Voyager.LOGGER.info("[Photo Booth] {} bought their portrait for {}", visitor.getName(),
                    TradePostLedger.format(price));
        } else {
            if (!InventoryUtils.addItemStackToProvider(this, print)) {
                Voyager.LOGGER.info("[Photo Booth] no room on the shelf for {}'s portrait", visitor.getName());
            }
            MessageUtils.format(Component.translatable("com.voyager.photo.sat", visitor.getName()))
                    .sendTo(getColony()).forAllPlayers();
        }
        return paid ? price : 0;
    }

    /**
     * What a portrait costs: half a coin plus a quarter a level, half again for colour. At level two
     * that is a coin for a black-and-white portrait; at level five, nearly two.
     */
    public int portraitPrice(final boolean colour) {
        final int coin = TradePostLedger.coinValue();
        double price = coin * (0.5 + 0.25 * getBuildingLevel());
        if (colour) {
            price *= 1.5;
        }
        return (int) Math.round(price);
    }

    public int portraitsSold() {
        return portraitsSold;
    }

    /**
     * The Photo Booth in one English paragraph, for whoever asks in words - Colonist Errands reads
     * this through reflection and gives it to the photographer's talking colonist.
     */
    public String describeForChat() {
        final StringBuilder sb = new StringBuilder();
        final String look = switch (getSchematicName()) {
            case "keep" -> "stone and spruce under a pitched roof";
            case "sandcourt" -> "sandstone and cool blue tile";
            case "station" -> "white quartz and glass";
            case "array" -> "deepslate and purpur";
            default -> "pale stone with copper trim";
        };
        sb.append("The Photo Booth is a studio built in ").append(look).append(", level ").append(getBuildingLevel())
                .append(" of 5: a gallery wall, a camera stand in the middle of the floor and a mark where the sitter stands");
        sb.append(getBuildingLevel() >= 2 ? ", with a darkroom wing where film is developed under a red lamp. " : "; the darkroom comes at level 2. ");
        if (getBuildingLevel() >= 2) {
            sb.append("Visitors to the colony can sit for a portrait and pay for it - a black-and-white one costs about ")
                    .append(coins(portraitPrice(false))).append(", colour about ").append(coins(portraitPrice(true))).append(". ");
        }
        if (portraitsSold > 0) {
            sb.append(portraitsSold).append(portraitsSold == 1 ? " portrait has" : " portraits have").append(" been sold so far, earning the colony about ")
                    .append(coins(earned)).append(". ");
        } else {
            sb.append("No portrait has been sold yet. ");
        }
        if (volumes > 0) {
            sb.append("The colony chronicle - photographs of every building as it goes up - runs to ")
                    .append(volumes).append(volumes == 1 ? " volume. " : " volumes. ");
        } else {
            sb.append("The colony chronicle (photographs of every building as it goes up) has not filled its first album yet. ");
        }
        if (hasSitterWaiting()) {
            sb.append("Somebody is waiting in the studio right now. ");
        }
        return sb.toString().trim();
    }

    /** An amount of colony money in coins, for chat: "1 coin", "about 2 coins", "half a coin". */
    private static String coins(final long amount) {
        final int coin = Math.max(1, TradePostLedger.coinValue());
        final double c = amount / (double) coin;
        if (c < 0.75) {
            return "half a coin";
        }
        final long whole = Math.round(c);
        return whole + (whole == 1 ? " coin" : " coins");
    }

    public long earned() {
        return earned;
    }

    /** The photographer, if they are within reach of somebody - for the sitter to look at. */
    public @Nullable LivingEntity photographerNear(final Entity who, final double range) {
        for (final ICitizenData citizen : getAllAssignedCitizen()) {
            if (citizen.getEntity().isPresent()) {
                final LivingEntity entity = citizen.getEntity().get();
                if (entity.distanceTo(who) <= range) {
                    return entity;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the chronicle

    /** The builder finished something: the chronicle owes it a photograph. */
    public void chronicle(final IBuilding built, final int level) {
        chronicle(built, level, PHASE_DONE);
    }

    public void chronicle(final IBuilding built, final int level, final String phase) {
        if (built == null) {
            return;
        }
        final String which = phase == null ? PHASE_DONE : phase;
        // A finished building supersedes its own halfway picture if that was never taken.
        chronicle.removeIf(job -> job.pos().equals(built.getPosition())
                && (job.phase().equals(which) || PHASE_DONE.equals(which)));
        chronicle.add(new ChronicleJob(built.getPosition(), level, getColony().getDay(), which));
        markDirty();
    }

    /**
     * Progress pictures: every build or upgrade in the colony is photographed once halfway up.
     *
     * <p>The builder's hut keeps its place in the blueprint (a blueprint-local position and a stage
     * - clear, solid blocks, non-solids, decoration...). Once the solid stage has climbed past half
     * the blueprint's height, or any later stage has begun, the walls are up and the roof is not,
     * which is the picture worth having; the chronicle queues it and remembers the work order so
     * it is taken once.</p>
     */
    private void watchTheBuilders(final IColony colony) {
        for (final IServerWorkOrder order : colony.getWorkManager().getWorkOrders().values()) {
            if (!(order instanceof WorkOrderBuilding build) || !build.isClaimed() || halfwayShot.contains(build.getID())) {
                continue;
            }
            if (build.getWorkOrderType() != WorkOrderType.BUILD && build.getWorkOrderType() != WorkOrderType.UPGRADE) {
                continue;
            }
            final IBuilding hut = colony.getServerBuildingManager().getBuilding(build.getClaimedBy());
            if (!(hut instanceof AbstractBuildingStructureBuilder builder)) {
                continue;
            }
            final com.minecolonies.api.util.Tuple<BlockPos, BuildingProgressStage> progress = builder.getProgress();
            if (progress == null || progress.getA() == null || progress.getB() == null || progress.getA().getY() < 0) {
                continue;
            }
            final BuildingProgressStage stage = progress.getB();
            boolean halfway = stage == BuildingProgressStage.CLEAR_NON_SOLIDS || stage == BuildingProgressStage.DECORATE
                    || stage == BuildingProgressStage.SPAWN;
            if (!halfway && stage == BuildingProgressStage.BUILD_SOLID) {
                int height = 0;
                try {
                    height = build.getBlueprint() == null ? 0 : build.getBlueprint().getSizeY();
                } catch (final Throwable notLoaded) {
                    height = 0;
                }
                halfway = height > 0 && progress.getA().getY() >= height / 2;
            }
            if (!halfway) {
                continue;
            }
            final IBuilding subject = colony.getServerBuildingManager().getBuilding(build.getLocation());
            if (subject == null) {
                continue;
            }
            halfwayShot.add(build.getID());
            chronicle(subject, build.getTargetLevel(), PHASE_HALFWAY);
            Voyager.LOGGER.info("[chronicle] {} level {} is halfway up - a progress photograph is owed",
                    subject.getBuildingDisplayName(), build.getTargetLevel());
        }
        // forget work orders that no longer exist
        halfwayShot.removeIf(id -> colony.getWorkManager().getWorkOrder(id) == null);
    }

    public @Nullable ChronicleJob nextChronicle() {
        return chronicle.isEmpty() ? null : chronicle.get(0);
    }

    public boolean hasChronicleWork() {
        return !chronicle.isEmpty();
    }

    public void chronicleDone(final ChronicleJob job) {
        if (chronicle.remove(job)) {
            StatsUtil.trackStat(this, STAT_CHRONICLE, 1);
            markDirty();
        }
    }

    /** The building a chronicle job is about, if it still stands. */
    public @Nullable IBuilding chronicleSubject(final ChronicleJob job) {
        return job == null ? null : getColony().getServerBuildingManager().getBuilding(job.pos());
    }

    /**
     * Paste a chronicle photograph into the album on the shelf.
     *
     * <p>The first open album with a free page takes it; when that fills the sixteenth page the
     * album is signed as the next volume of the colony's chronicle and left on the shelf finished.
     * No album, or none with room: the print goes on the shelf loose and false comes back, so the
     * photographer knows to ask for one.</p>
     */
    public boolean fileInAlbum(final ItemStack photograph, final String note, final String photographer) {
        for (final IItemHandler handler : InventoryUtils.getItemHandlersFromProvider(this)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (!ColonyCamera.isOpenAlbum(stack) || ColonyCamera.freeAlbumPage(stack) < 0) {
                    continue;
                }
                final ItemStack album = handler.extractItem(slot, 1, false);
                if (album.isEmpty() || !ColonyCamera.pasteInAlbum(album, photograph, note)) {
                    if (!album.isEmpty()) {
                        handler.insertItem(slot, album, false);
                    }
                    continue;
                }
                ItemStack back = album;
                if (ColonyCamera.freeAlbumPage(album) < 0) {
                    volumes++;
                    back = ColonyCamera.signAlbum(album, chronicleTitle(volumes), photographer);
                    MessageUtils.format(Component.translatable("com.voyager.photo.volume",
                                    chronicleTitle(volumes), photographer))
                            .sendTo(getColony()).forAllPlayers();
                    markDirty();
                }
                final ItemStack left = handler.insertItem(slot, back, false);
                if (!left.isEmpty()) {
                    InventoryUtils.addItemStackToProvider(this, left);   // a signed album is a new item
                }
                return true;
            }
        }
        return false;
    }

    /** "Chronicle of <colony>, vol. II". */
    public String chronicleTitle(final int volume) {
        return "Chronicle of " + getColony().getName() + ", vol. " + roman(volume);
    }

    private static String roman(final int n) {
        final int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] signs = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        final StringBuilder out = new StringBuilder();
        int left = Math.max(1, n);
        for (int i = 0; i < values.length; i++) {
            while (left >= values[i]) {
                out.append(signs[i]);
                left -= values[i];
            }
        }
        return out.toString();
    }

    public int volumes() {
        return volumes;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        portraitsSold = tag.getInt(NBT_SOLD);
        earned = tag.getLong(NBT_EARNED);
        volumes = tag.getInt(NBT_VOLUMES);
        chronicle.clear();
        for (final Tag entry : tag.getList(NBT_CHRONICLE, Tag.TAG_COMPOUND)) {
            final ChronicleJob job = ChronicleJob.load((CompoundTag) entry);
            if (job != null) {
                chronicle.add(job);
            }
        }
        halfwayShot.clear();
        for (final int id : tag.getIntArray(NBT_HALFWAY)) {
            halfwayShot.add(id);
        }
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag tag = super.serializeNBT(provider);
        tag.putInt(NBT_SOLD, portraitsSold);
        tag.putLong(NBT_EARNED, earned);
        tag.putInt(NBT_VOLUMES, volumes);
        final ListTag list = new ListTag();
        for (final ChronicleJob job : chronicle) {
            list.add(job.save());
        }
        tag.put(NBT_CHRONICLE, list);
        tag.putIntArray(NBT_HALFWAY, halfwayShot.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    /** The Photographer's recipes: everything Exposure makes, plus what the darkroom teaches. */
    public static class CraftingModule extends AbstractCraftingBuildingModule.Crafting {
        public CraftingModule(final JobEntry jobEntry) {
            super(jobEntry);
        }
    }
}
