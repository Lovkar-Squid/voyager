package me.lovkar.voyager.ai;

import java.util.List;

import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.colony.BuildingPhotoBooth;
import me.lovkar.voyager.colony.BuildingPhotoBooth.ChronicleJob;
import me.lovkar.voyager.colony.JobPhotographer;
import me.lovkar.voyager.photo.ColonyCamera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Photographer at work.
 *
 * <p>Everything a crafter does, MineColonies already does - take a request, gather the
 * ingredients, stand at the bench, hand the result over - so most of this profession is its
 * recipes and its building rather than its state machine.</p>
 *
 * <p>What is here is the part everybody says is impossible: <b>the photographer actually takes
 * photographs.</b> They take the colony's camera off the shelf - the real one, with whatever film,
 * lens, filter and flash the colony fitted to it - load a fresh roll if it needs one, walk to where
 * the picture is, hold the camera up, and expose a picture drawn ray by ray on the server out of
 * the world's own map colours (see {@link me.lovkar.voyager.compat.ExposureCamera}). The negative
 * goes on the roll, a full roll goes on the shelf for the darkroom, and the camera goes back where
 * it was. It takes a few seconds and you can watch them do it.</p>
 *
 * <p>Three kinds of picture, in order of priority:</p>
 * <ol>
 *   <li><b>A sitting.</b> A visitor has walked in and is standing at the mark. Money is waiting;
 *       the bench can wait. The print leaves with the visitor and the colony is paid in Trade Post
 *       coins (see {@link BuildingPhotoBooth#completeSitting}).</li>
 *   <li><b>The chronicle.</b> The builder finished something. When the bench is quiet the
 *       photographer walks out to a spot with a view of the new building and photographs it for the
 *       colony's album.</li>
 *   <li><b>A portrait.</b> Nothing else to do, a colonist nearby, daylight: a picture for the
 *       shelf, now and then.</li>
 * </ol>
 */
public class EntityAIWorkPhotographer
        extends AbstractEntityAICrafting<JobPhotographer, BuildingPhotoBooth> {

    /** The photographer's own states, alongside the crafting ones. */
    public enum Shoot implements IAIState {
        WALK_TO_STUDIO(true), WALK_TO_VIEWPOINT(true), AIM(true), EXPOSE(false), FILE_PHOTOGRAPH(true);

        private final boolean okayToEat;

        Shoot(final boolean okayToEat) {
            this.okayToEat = okayToEat;
        }

        @Override
        public boolean isOkayToEat() {
            return okayToEat;
        }
    }

    /** What the current picture is of. */
    private enum Assignment {
        PORTRAIT, SITTING, CHRONICLE
    }

    private static final int TICK_DELAY = 10;
    /** Rows of the picture drawn per step: twelve steps to a photograph, about six seconds. */
    private static final int ROWS_PER_STEP = 8;
    /** Game ticks between idle portraits, so the colony gets a picture now and then, not a flood. */
    private static final long BETWEEN_SHOOTS = 6000L;
    /** Game ticks between chronicle outings - a building a minute at most. */
    private static final long BETWEEN_CHRONICLE = 1200L;
    private static final int WALK_ATTEMPTS = 12;
    private static final int WALK_ATTEMPTS_OUTSIDE = 40;
    /** How far from a building's edge the chronicle photograph is taken from. */
    private static final int VIEW_STANDOFF = 6;

    private static final ResourceLocation CAMERA = ResourceLocation.fromNamespaceAndPath("exposure", "camera");
    private static final ResourceLocation FILM = ResourceLocation.fromNamespaceAndPath("exposure", "black_and_white_film");
    private static final ResourceLocation ALBUM = ResourceLocation.fromNamespaceAndPath("exposure", "album");

    private byte[] film = new byte[0];
    private Object shot;
    private int rowsDone;
    private long lastShot = -BETWEEN_SHOOTS;
    private long lastChronicle = -BETWEEN_CHRONICLE;
    /** Game time before which the racks are not counted again for a camera. */
    private long nextStockCheck = 0L;
    /** How often the shelf is checked for a camera while idle: five seconds, not every tick. */
    private static final long STOCK_CHECK_EVERY = 100L;
    private int walkAttempts;
    /** The colony's camera while the photographer has it out of the rack. */
    private ItemStack camera = ItemStack.EMPTY;
    private LivingEntity subject;
    private Assignment assignment = Assignment.PORTRAIT;
    private @Nullable ChronicleJob chronicleJob;
    private @Nullable BlockPos viewpoint;
    private @Nullable Vec3 viewTarget;
    private double viewFov;

    public EntityAIWorkPhotographer(final @NotNull JobPhotographer job) {
        super(job);
        super.registerTargets(
                new AITarget<IAIState>(Shoot.WALK_TO_STUDIO, this::goToStudio, TICK_DELAY),
                new AITarget<IAIState>(Shoot.WALK_TO_VIEWPOINT, this::goToViewpoint, TICK_DELAY),
                new AITarget<IAIState>(Shoot.AIM, this::aim, TICK_DELAY),
                new AITarget<IAIState>(Shoot.EXPOSE, this::expose, TICK_DELAY),
                new AITarget<IAIState>(Shoot.FILE_PHOTOGRAPH, this::filePhotograph, TICK_DELAY));
    }

    @Override
    public @NotNull Class<BuildingPhotoBooth> getExpectedBuildingClass() {
        return BuildingPhotoBooth.class;
    }

    /**
     * A sitter at the mark outranks the bench - a paying customer is standing there. Otherwise the
     * bench first, and only when the crafting AI has nothing to do does the camera come off the
     * shelf: for the chronicle if the builder has been busy, for a portrait if not.
     */
    @Override
    protected IAIState decide() {
        if (building != null && building.hasSitterWaiting() && canShootNow(true)) {
            assignment = Assignment.SITTING;
            walkAttempts = 0;
            return Shoot.WALK_TO_STUDIO;
        }
        final IAIState next = super.decide();
        if (next != AIWorkerState.IDLE || building == null) {
            return next;
        }
        if (building.hasChronicleWork() && world.getGameTime() - lastChronicle >= BETWEEN_CHRONICLE
                && canShootNow(false)) {
            final ChronicleJob job = building.nextChronicle();
            final IBuilding about = building.chronicleSubject(job);
            if (about == null || about.getBuildingLevel() < 1) {
                building.chronicleDone(job);            // torn down, or not standing yet
                return next;
            }
            if (!frame(about)) {
                Voyager.LOGGER.info("[Photo Booth] no spot with a view of {} - the chronicle skips it",
                        about.getBuildingDisplayName());
                building.chronicleDone(job);
                return next;
            }
            assignment = Assignment.CHRONICLE;
            chronicleJob = job;
            walkAttempts = 0;
            return Shoot.WALK_TO_VIEWPOINT;
        }
        if (wantsToShoot()) {
            assignment = Assignment.PORTRAIT;
            walkAttempts = 0;
            return Shoot.WALK_TO_STUDIO;
        }
        return next;
    }

    // ------------------------------------------------------------------ deciding

    /** Idle hands, a camera on the shelf, light to shoot by, and long enough since the last one. */
    private boolean wantsToShoot() {
        if (world.getGameTime() - lastShot < BETWEEN_SHOOTS) {
            return false;
        }
        return canShootNow(false);
    }

    /**
     * Is a photograph possible right now: the building stands, Exposure is here, there is a camera
     * on the shelf (checked every few seconds, not every tick), and there is light - or a flash.
     *
     * @param urgent skip the stock-check throttle, because somebody is waiting
     */
    private boolean canShootNow(final boolean urgent) {
        if (building == null || building.getBuildingLevel() < 1 || !ColonyCamera.available()) {
            return false;
        }
        final long now = world.getGameTime();
        if (!urgent && now < nextStockCheck) {
            return false;
        }
        nextStockCheck = now + STOCK_CHECK_EVERY;
        final ItemStack stock = cameraInStock();
        building.noteCamera(!stock.isEmpty());
        if (stock.isEmpty()) {
            return false;
        }
        // Daylight, or a flash: a photographer without either has the sense to wait for morning.
        final long t = world.getDayTime() % 24000L;
        final boolean day = t < 12500L || t > 23500L;
        return day || ColonyCamera.hasFlash(stock);
    }

    /** The registered item, or null - the registry hands back air for a missing key, never null. */
    private static @Nullable Item itemOrNull(final ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /** The colony's camera as it sits on the shelf (not removed). Puts in a request if there is none. */
    private ItemStack cameraInStock() {
        final Item cameraItem = itemOrNull(CAMERA);
        if (cameraItem == null) {
            return ItemStack.EMPTY;
        }
        for (final IItemHandler handler : InventoryUtils.getItemHandlersFromProvider(building)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getItem() == cameraItem) {
                    return stack;
                }
            }
        }
        // Not a stall: the request goes out and the bench keeps working meanwhile.
        checkIfRequestForItemExistOrCreateAsync(new ItemStack(cameraItem));
        return ItemStack.EMPTY;
    }

    /** Take the camera out of the rack and into the hand - the real item, fittings and all. */
    private ItemStack takeCamera() {
        final Item cameraItem = itemOrNull(CAMERA);
        if (cameraItem == null) {
            return ItemStack.EMPTY;
        }
        for (final IItemHandler handler : InventoryUtils.getItemHandlersFromProvider(building)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getItem() == cameraItem) {
                    return handler.extractItem(slot, 1, false);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Make sure there is film in the camera with room on it. A full roll comes out and goes on the
     * shelf for the darkroom; a fresh roll comes off the shelf and goes in; no roll at all and the
     * photographer asks for one and puts the camera back.
     */
    private boolean loadFilmIfNeeded() {
        if (ColonyCamera.hasFreeFrame(camera)) {
            return true;
        }
        if (ColonyCamera.hasFilm(camera)) {
            final ItemStack full = ColonyCamera.ejectFilm(camera);
            if (!full.isEmpty()) {
                InventoryUtils.addItemStackToProvider(building, full);
                Voyager.LOGGER.info("[Photo Booth] {} put a full roll of film on the shelf",
                        worker.getCitizenData().getName());
            }
        }
        for (final IItemHandler handler : InventoryUtils.getItemHandlersFromProvider(building)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                final ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && ColonyCamera.isFilm(stack)) {
                    final ItemStack roll = handler.extractItem(slot, 1, false);
                    ColonyCamera.loadFilm(camera, roll);
                    return ColonyCamera.hasFreeFrame(camera);
                }
            }
        }
        final Item filmItem = itemOrNull(FILM);
        if (filmItem != null) {
            checkIfRequestForItemExistOrCreateAsync(new ItemStack(filmItem));
        }
        return false;
    }

    // ------------------------------------------------------------------ getting there

    private IAIState goToStudio() {
        final BlockPos target = building.getPhotographerPosition();
        if (walkToSafePos(target)) {
            walkAttempts = 0;
            return Shoot.AIM;
        }
        if (++walkAttempts > WALK_ATTEMPTS) {
            walkAttempts = 0;
            lastShot = world.getGameTime();          // try again later rather than stand here
            return giveUp();
        }
        return Shoot.WALK_TO_STUDIO;
    }

    private IAIState goToViewpoint() {
        if (viewpoint == null) {
            return giveUp();
        }
        if (walkToSafePos(viewpoint)) {
            walkAttempts = 0;
            return Shoot.AIM;
        }
        if (++walkAttempts > WALK_ATTEMPTS_OUTSIDE) {
            walkAttempts = 0;
            Voyager.LOGGER.info("[Photo Booth] {} could not reach the viewpoint at {}",
                    worker.getCitizenData().getName(), viewpoint);
            if (chronicleJob != null) {
                building.chronicleDone(chronicleJob);   // not reachable today; do not stall the album
            }
            return giveUp();
        }
        return Shoot.WALK_TO_VIEWPOINT;
    }

    /**
     * Pick the spot a building is photographed from: outside its corners, a few blocks back, on
     * standable ground near the building's own level, on whichever side is easiest to get to. Sets
     * {@link #viewpoint}, {@link #viewTarget} and the field of view that fits the building in.
     */
    private boolean frame(final IBuilding about) {
        final Tuple<BlockPos, BlockPos> corners = about.getCorners();
        if (corners == null || corners.getA() == null || corners.getB() == null) {
            return false;
        }
        final BlockPos a = corners.getA();
        final BlockPos b = corners.getB();
        final int minX = Math.min(a.getX(), b.getX());
        final int maxX = Math.max(a.getX(), b.getX());
        final int minZ = Math.min(a.getZ(), b.getZ());
        final int maxZ = Math.max(a.getZ(), b.getZ());
        final int minY = Math.min(a.getY(), b.getY());
        final int maxY = Math.max(a.getY(), b.getY());
        final double cx = (minX + maxX + 1) / 2.0;
        final double cz = (minZ + maxZ + 1) / 2.0;
        final int halfX = (maxX - minX) / 2 + 1;
        final int halfZ = (maxZ - minZ) / 2 + 1;
        final int height = Math.max(3, maxY - minY + 1);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        // The eight compass points, at a distance that takes the whole facade in.
        final int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (final int[] d : dirs) {
            final double len = Math.sqrt(d[0] * d[0] + d[1] * d[1]);
            final double ux = d[0] / len;
            final double uz = d[1] / len;
            final double extent = Math.abs(ux) * halfX + Math.abs(uz) * halfZ;
            final double dist = Math.min(28.0, extent + VIEW_STANDOFF + height * 0.6);
            final int x = (int) Math.round(cx + ux * dist);
            final int z = (int) Math.round(cz + uz * dist);
            final BlockPos stand = standable(x, z, minY);
            if (stand == null) {
                continue;
            }
            final double climb = Math.abs(stand.getY() - minY);
            final double walk = Math.sqrt(stand.distSqr(worker.blockPosition()));
            final double score = climb * 6.0 + walk;
            if (score < bestScore) {
                bestScore = score;
                best = stand;
            }
        }
        if (best == null) {
            return false;
        }
        viewpoint = best;
        viewTarget = new Vec3(cx, minY + height * 0.45, cz);
        // Wide enough to take the whole building in from where we stand, never narrower than plain.
        final double dist = Math.sqrt(Math.pow(cx - (best.getX() + 0.5), 2) + Math.pow(cz - (best.getZ() + 0.5), 2));
        final double need = (Math.max(halfX, halfZ) + 1.5) / Math.max(4.0, dist) / Math.tan(Math.toRadians(35.0));
        viewFov = Math.max(1.0, Math.min(1.8, need));
        return true;
    }

    /** The block to stand on at (x, z): the surface, if it is near the building's level and safe. */
    private @Nullable BlockPos standable(final int x, final int z, final int nearY) {
        final int surface = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(surface - nearY) > 8) {
            return null;
        }
        final BlockPos feet = new BlockPos(x, surface, z);
        final BlockState floor = world.getBlockState(feet.below());
        if (!floor.isSolid() || !floor.getFluidState().isEmpty()) {
            return null;
        }
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                || !world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty()) {
            return null;
        }
        return feet;
    }

    // ------------------------------------------------------------------ the shot

    /**
     * Take the camera out, load it, find what the picture is of and point at it.
     *
     * <p>The sitter at the mark, if this is a sitting; the building, if this is the chronicle; the
     * nearest colonist otherwise. The camera goes into the hand here, so it is up and visible for
     * the whole exposure.</p>
     */
    private IAIState aim() {
        camera = takeCamera();
        if (camera.isEmpty()) {
            lastShot = world.getGameTime();
            return giveUp();
        }
        if (!loadFilmIfNeeded()) {
            putCameraBack();
            lastShot = world.getGameTime();
            return giveUp();
        }
        worker.setItemSlot(EquipmentSlot.MAINHAND, camera);
        worker.setRenderMetadata(JobPhotographer.META_CAMERA);

        double fov = 0.0;
        switch (assignment) {
            case SITTING -> {
                subject = building.sitterEntity();
                if (subject == null || subject.distanceToSqr(Vec3.atCenterOf(building.getSitterPosition())) > 36.0) {
                    // The visitor wandered off. Somebody else, then, or nobody.
                    final IVisitorData gone = building.sitterData();
                    if (gone != null) {
                        building.cancelSitting(gone);
                    }
                    assignment = Assignment.PORTRAIT;
                    subject = nearestSitter();
                }
            }
            case CHRONICLE -> {
                subject = null;
                fov = viewFov;
            }
            default -> subject = nearestSitter();
        }
        if (subject != null) {
            face(subject.getEyePosition().subtract(0.0, 0.2, 0.0));
        } else if (assignment == Assignment.CHRONICLE && viewTarget != null) {
            face(viewTarget);
        }
        worker.swing(InteractionHand.MAIN_HAND);          // the camera comes up
        if (world instanceof ServerLevel level) {
            level.playSound(null, worker.blockPosition(), sound("item.camera.shutter_open"),
                    SoundSource.NEUTRAL, 0.7f, 1.0f);
            if (ColonyCamera.hasFlash(camera)) {
                level.playSound(null, worker.blockPosition(), sound("item.camera.flash"),
                        SoundSource.NEUTRAL, 0.8f, 1.0f);
                level.sendParticles(ParticleTypes.FLASH, worker.getX(), worker.getEyeY(), worker.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0);
            }
            shot = ColonyCamera.open(level, worker, camera, fov);
        }
        film = ColonyCamera.blank();
        rowsDone = 0;
        if (film.length == 0 || shot == null) {
            putCameraBack();
            lastShot = world.getGameTime();
            return giveUp();
        }
        return Shoot.EXPOSE;
    }

    /**
     * Turn to a point, body and head, exactly - so the ray through the lens goes where the eyes do.
     * MineColonies' look control turns gradually and only the head; a photograph needs the whole
     * citizen pointed the right way this tick.
     */
    private void face(final Vec3 target) {
        final Vec3 d = target.subtract(worker.getEyePosition());
        final double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        final float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        final float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
        worker.setYRot(yaw);
        worker.yRotO = yaw;
        worker.setYHeadRot(yaw);
        worker.yHeadRotO = yaw;
        worker.yBodyRot = yaw;
        worker.yBodyRotO = yaw;
        worker.setXRot(pitch);
        worker.xRotO = pitch;
        worker.getLookControl().setLookAt(target.x, target.y, target.z);
    }

    /** Any citizen but this one, standing close enough to be the subject. */
    private LivingEntity nearestSitter() {
        final AABB around = worker.getBoundingBox().inflate(8.0);
        final List<AbstractEntityCitizen> nearby = world.getEntitiesOfClass(AbstractEntityCitizen.class, around,
                other -> other != worker && other.isAlive());
        LivingEntity best = null;
        double closest = Double.MAX_VALUE;
        for (final AbstractEntityCitizen other : nearby) {
            final double d = other.distanceToSqr(worker);
            if (d < closest) {
                closest = d;
                best = other;
            }
        }
        return best;
    }

    /**
     * The exposure itself: a band of the picture per step, while the photographer stands still
     * with the camera up. Slow on purpose - a long exposure should look like one.
     */
    private IAIState expose() {
        if (!(world instanceof ServerLevel level)) {
            return abandon();
        }
        worker.setItemSlot(EquipmentSlot.MAINHAND, camera);   // MineColonies may have swapped tools
        if (!ColonyCamera.renderBand(level, worker, shot, film, rowsDone, ROWS_PER_STEP)) {
            return abandon();
        }
        rowsDone += ROWS_PER_STEP;
        level.sendParticles(ParticleTypes.END_ROD, worker.getX(), worker.getEyeY(), worker.getZ(),
                1, 0.12, 0.12, 0.12, 0.0);
        if (rowsDone < ColonyCamera.size()) {
            return Shoot.EXPOSE;
        }
        level.playSound(null, worker.blockPosition(), sound("item.camera.shutter_close"),
                SoundSource.NEUTRAL, 0.7f, 1.0f);
        return Shoot.FILE_PHOTOGRAPH;
    }

    /** Develop it, file it where it belongs, and give the camera back. */
    private IAIState filePhotograph() {
        if (!(world instanceof ServerLevel level)) {
            return abandon();
        }
        final String name = worker.getCitizenData().getName();
        final String id = "voyager_" + building.getColony().getID() + "_"
                + world.getGameTime() + "_" + worker.getCivilianID();
        final Component title = titleFor(shot);
        final boolean colour = !ColonyCamera.isBlackAndWhite(camera);
        final ItemStack photograph = ColonyCamera.develop(level, worker, shot, film, id, name, title, camera);
        film = new byte[0];
        shot = null;
        lastShot = world.getGameTime();
        final Assignment done = assignment;
        final ChronicleJob job = chronicleJob;
        putCameraBack();
        if (photograph.isEmpty()) {
            return giveUp();
        }
        switch (done) {
            case SITTING -> {
                final int paid = building.completeSitting(photograph, colour, worker.getCitizenData());
                worker.getCitizenExperienceHandler().addExperience(paid > 0 ? 6.0 : 2.0);
            }
            case CHRONICLE -> {
                lastChronicle = world.getGameTime();
                final IBuilding about = building.chronicleSubject(job);
                final String note = about == null ? title.getString()
                        : Component.translatable(about.getBuildingDisplayName()).getString()
                        + " - level " + (job == null ? about.getBuildingLevel() : job.level())
                        + ", day " + (job == null ? building.getColony().getDay() : job.day());
                if (!building.fileInAlbum(photograph, note, name)) {
                    // No album with room: the print goes on the shelf loose and an album is asked for.
                    if (!InventoryUtils.addItemStackToProvider(building, photograph)) {
                        InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), photograph);
                    }
                    final Item album = itemOrNull(ALBUM);
                    if (album != null) {
                        checkIfRequestForItemExistOrCreateAsync(new ItemStack(album));
                    }
                }
                if (job != null) {
                    building.chronicleDone(job);
                }
                worker.getCitizenExperienceHandler().addExperience(4.0);
                MessageUtils.format(Component.translatable("com.voyager.photo.chronicled", name,
                                about == null ? title : Component.translatable(about.getBuildingDisplayName())))
                        .sendTo(building.getColony()).forAllPlayers();
            }
            default -> {
                if (!InventoryUtils.addItemStackToProvider(building, photograph)) {
                    InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), photograph);
                }
                worker.getCitizenExperienceHandler().addExperience(2.0);
                MessageUtils.format(Component.translatable("com.voyager.photo.taken", name))
                        .sendTo(building.getColony()).forAllPlayers();
            }
        }
        incrementActionsDoneAndDecSaturation();
        Voyager.LOGGER.info("[Photo Booth] {} took a photograph ({}, {})", name, done, id);
        chronicleJob = null;
        viewpoint = null;
        viewTarget = null;
        assignment = Assignment.PORTRAIT;
        return AIWorkerState.START_WORKING;
    }

    /** "Portrait of X" if somebody is in it; the building, for the chronicle; otherwise the colony. */
    private Component titleFor(final Object finished) {
        if (assignment == Assignment.CHRONICLE) {
            final IBuilding about = building.chronicleSubject(chronicleJob);
            if (about != null) {
                final int level = chronicleJob == null ? about.getBuildingLevel() : chronicleJob.level();
                return Component.translatable("com.voyager.photo.chronicle",
                        Component.translatable(about.getBuildingDisplayName()), level,
                        chronicleJob == null ? building.getColony().getDay() : chronicleJob.day());
            }
        }
        if (assignment == Assignment.SITTING && subject instanceof AbstractEntityCitizen visitor
                && visitor.getCitizenData() != null) {
            return Component.translatable("com.voyager.photo.portrait", visitor.getCitizenData().getName());
        }
        for (final Entity seen : ColonyCamera.inFrame(finished)) {
            if (seen instanceof AbstractEntityCitizen citizen && citizen.getCitizenData() != null) {
                return Component.translatable("com.voyager.photo.portrait", citizen.getCitizenData().getName());
            }
        }
        return Component.translatable("com.voyager.photo.view", building.getColony().getName());
    }

    /** Something went wrong mid-exposure. Nothing is filed; the camera goes back. */
    private IAIState abandon() {
        film = new byte[0];
        shot = null;
        lastShot = world.getGameTime();
        putCameraBack();
        return giveUp();
    }

    /** Whatever this outing was, it is over; let a waiting sitter go rather than keep them standing. */
    private IAIState giveUp() {
        if (assignment == Assignment.SITTING) {
            final IVisitorData waiting = building.sitterData();
            if (waiting != null) {
                building.cancelSitting(waiting);
            }
        }
        if (assignment == Assignment.CHRONICLE) {
            lastChronicle = world.getGameTime();
        }
        chronicleJob = null;
        viewpoint = null;
        viewTarget = null;
        assignment = Assignment.PORTRAIT;
        return AIWorkerState.START_WORKING;
    }

    /** The camera goes back where it came from, film and all; the hand empties. */
    private void putCameraBack() {
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        worker.setRenderMetadata("");
        subject = null;
        if (!camera.isEmpty()) {
            if (!InventoryUtils.addItemStackToProvider(building, camera)) {
                InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), camera);
            }
            camera = ItemStack.EMPTY;
        }
    }

    private static SoundEvent sound(final String path) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("exposure", path));
    }
}
