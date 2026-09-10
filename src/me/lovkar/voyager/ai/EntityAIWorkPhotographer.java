package me.lovkar.voyager.ai;

import java.util.List;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.colony.BuildingPhotoBooth;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
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
 * photographs.</b> When there is nothing on the bench they take the colony's camera off the shelf
 * - the real one, with whatever film, lens, filter and flash the colony fitted to it - load a
 * fresh roll if it needs one, walk into the studio, find something worth pointing it at, hold it
 * up, and expose a picture drawn ray by ray on the server out of the world's own map colours (see
 * {@link me.lovkar.voyager.compat.ExposureCamera}). The negative goes on the roll, the print goes
 * on the shelf, a full roll goes on the shelf for the darkroom, and the camera goes back where it
 * was. It takes a few seconds and you can watch them do it.</p>
 */
public class EntityAIWorkPhotographer
        extends AbstractEntityAICrafting<JobPhotographer, BuildingPhotoBooth> {

    /** The photographer's own states, alongside the crafting ones. */
    public enum Shoot implements IAIState {
        WALK_TO_STUDIO(true), AIM(true), EXPOSE(false), FILE_PHOTOGRAPH(true);

        private final boolean okayToEat;

        Shoot(final boolean okayToEat) {
            this.okayToEat = okayToEat;
        }

        @Override
        public boolean isOkayToEat() {
            return okayToEat;
        }
    }

    private static final int TICK_DELAY = 10;
    /** Rows of the picture drawn per step: twelve steps to a photograph, about six seconds. */
    private static final int ROWS_PER_STEP = 8;
    /** Game ticks between shoots, so the colony gets a picture now and then, not a flood. */
    private static final long BETWEEN_SHOOTS = 6000L;
    private static final int WALK_ATTEMPTS = 12;

    private static final ResourceLocation CAMERA = ResourceLocation.fromNamespaceAndPath("exposure", "camera");
    private static final ResourceLocation FILM = ResourceLocation.fromNamespaceAndPath("exposure", "black_and_white_film");

    private byte[] film = new byte[0];
    private Object shot;
    private int rowsDone;
    private long lastShot = -BETWEEN_SHOOTS;
    /** Game time before which the racks are not counted again for a camera. */
    private long nextStockCheck = 0L;
    /** How often the shelf is checked for a camera while idle: five seconds, not every tick. */
    private static final long STOCK_CHECK_EVERY = 100L;
    private int walkAttempts;
    /** The colony's camera while the photographer has it out of the rack. */
    private ItemStack camera = ItemStack.EMPTY;
    private LivingEntity subject;

    public EntityAIWorkPhotographer(final @NotNull JobPhotographer job) {
        super(job);
        super.registerTargets(
                new AITarget<IAIState>(Shoot.WALK_TO_STUDIO, this::goToStudio, TICK_DELAY),
                new AITarget<IAIState>(Shoot.AIM, this::aim, TICK_DELAY),
                new AITarget<IAIState>(Shoot.EXPOSE, this::expose, TICK_DELAY),
                new AITarget<IAIState>(Shoot.FILE_PHOTOGRAPH, this::filePhotograph, TICK_DELAY));
    }

    @Override
    public @NotNull Class<BuildingPhotoBooth> getExpectedBuildingClass() {
        return BuildingPhotoBooth.class;
    }

    /**
     * The bench first. Only when the crafting AI has nothing to do does the photographer reach
     * for the camera - a request for film outranks a picture of a cow.
     */
    @Override
    protected IAIState decide() {
        final IAIState next = super.decide();
        if (next == AIWorkerState.IDLE && wantsToShoot()) {
            walkAttempts = 0;
            return Shoot.WALK_TO_STUDIO;
        }
        return next;
    }

    // ------------------------------------------------------------------ the shoot

    /** Idle hands, a camera on the shelf, light to shoot by, and long enough since the last one. */
    private boolean wantsToShoot() {
        if (building == null || building.getBuildingLevel() < 1 || !ColonyCamera.available()) {
            return false;
        }
        final long now = world.getGameTime();
        if (now - lastShot < BETWEEN_SHOOTS || now < nextStockCheck) {
            return false;
        }
        nextStockCheck = now + STOCK_CHECK_EVERY;
        final ItemStack stock = cameraInStock();
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

    private IAIState goToStudio() {
        final BlockPos target = building.getStudioPosition();
        if (walkToSafePos(target)) {
            walkAttempts = 0;
            return Shoot.AIM;
        }
        if (++walkAttempts > WALK_ATTEMPTS) {
            walkAttempts = 0;
            lastShot = world.getGameTime();          // try again later rather than stand here
            return AIWorkerState.START_WORKING;
        }
        return Shoot.WALK_TO_STUDIO;
    }

    /**
     * Take the camera out, load it, find something worth photographing and point at it.
     *
     * <p>A citizen if one is near - a portrait is the thing a colony actually wants - and the way
     * the photographer is standing otherwise. The camera goes into the hand here, so it is up and
     * visible for the whole exposure.</p>
     */
    private IAIState aim() {
        camera = takeCamera();
        if (camera.isEmpty()) {
            lastShot = world.getGameTime();
            return AIWorkerState.START_WORKING;
        }
        if (!loadFilmIfNeeded()) {
            putCameraBack();
            lastShot = world.getGameTime();
            return AIWorkerState.START_WORKING;
        }
        worker.setItemSlot(EquipmentSlot.MAINHAND, camera);
        worker.setRenderMetadata(JobPhotographer.META_CAMERA);

        subject = nearestSitter();
        if (subject != null) {
            face(subject);
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
            shot = ColonyCamera.open(level, worker, camera);
        }
        film = ColonyCamera.blank();
        rowsDone = 0;
        if (film.length == 0 || shot == null) {
            putCameraBack();
            lastShot = world.getGameTime();
            return AIWorkerState.START_WORKING;
        }
        return Shoot.EXPOSE;
    }

    /** Turn to the subject, body and head, so the ray through the lens goes where the eyes do. */
    private void face(final LivingEntity who) {
        worker.getLookControl().setLookAt(who, 60.0f, 30.0f);
        worker.lookAt(who, 60.0f, 30.0f);
        worker.setYRot(worker.getYHeadRot());
        worker.yBodyRot = worker.getYHeadRot();
    }

    /** Any citizen but this one, standing close enough to be the subject. */
    private LivingEntity nearestSitter() {
        final AABB around = worker.getBoundingBox().inflate(8.0);
        final List<EntityCitizen> nearby = world.getEntitiesOfClass(EntityCitizen.class, around,
                other -> other != worker && other.isAlive());
        LivingEntity best = null;
        double closest = Double.MAX_VALUE;
        for (final EntityCitizen other : nearby) {
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

    /** Develop it, put it on the shelf, and give the camera back. */
    private IAIState filePhotograph() {
        if (!(world instanceof ServerLevel level)) {
            return abandon();
        }
        final String name = worker.getCitizenData().getName();
        final String id = "voyager_" + building.getColony().getID() + "_"
                + world.getGameTime() + "_" + worker.getCivilianID();
        final ItemStack photograph = ColonyCamera.develop(level, worker, shot, film, id, name,
                titleFor(shot), camera);
        film = new byte[0];
        shot = null;
        lastShot = world.getGameTime();
        putCameraBack();
        if (photograph.isEmpty()) {
            return AIWorkerState.START_WORKING;
        }
        if (!InventoryUtils.addItemStackToProvider(building, photograph)) {
            InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), photograph);
        }
        worker.getCitizenExperienceHandler().addExperience(2.0);
        incrementActionsDoneAndDecSaturation();
        Voyager.LOGGER.info("[Photo Booth] {} took a photograph ({})", name, id);
        MessageUtils.format(Component.translatable("com.voyager.photo.taken", name))
                .sendTo(building.getColony()).forAllPlayers();
        return AIWorkerState.START_WORKING;
    }

    /** "Portrait of X" if somebody is in it, otherwise a view of the colony. */
    private Component titleFor(final Object finished) {
        for (final Entity seen : ColonyCamera.inFrame(finished)) {
            if (seen instanceof EntityCitizen citizen && citizen.getCitizenData() != null) {
                return Component.translatable("com.voyager.photo.portrait", citizen.getCitizenData().getName());
            }
        }
        return Component.translatable("com.voyager.photo.view", building.getColony().getName());
    }

    private IAIState abandon() {
        film = new byte[0];
        shot = null;
        lastShot = world.getGameTime();
        putCameraBack();
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
