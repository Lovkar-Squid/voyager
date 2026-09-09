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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * The Photographer at work.
 *
 * <p>Everything a crafter does, MineColonies already does - take a request, gather the
 * ingredients, stand at the bench, hand the result over - so most of this profession is its
 * recipes and its building rather than its state machine.</p>
 *
 * <p>What is here is the part everybody says is impossible: <b>the photographer actually takes
 * photographs.</b> When there is nothing on the bench they pick up the colony's camera, walk into
 * the studio, find something worth pointing it at, hold it up, and expose a picture - drawn ray by
 * ray on the server out of the world's own map colours (see
 * {@link me.lovkar.voyager.compat.ExposureCamera}). It takes a few seconds and you can watch them
 * do it, which is the whole point.</p>
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

    private byte[] film = new byte[0];
    private int rowsDone;
    private long lastShot = -BETWEEN_SHOOTS;
    private int walkAttempts;
    private ItemStack camera = ItemStack.EMPTY;

    public EntityAIWorkPhotographer(final @NotNull JobPhotographer job) {
        super(job);
        super.registerTargets(
                new AITarget<IAIState>(AIWorkerState.IDLE, this::wantsToShoot,
                        () -> Shoot.WALK_TO_STUDIO, 60),
                new AITarget<IAIState>(Shoot.WALK_TO_STUDIO, this::goToStudio, TICK_DELAY),
                new AITarget<IAIState>(Shoot.AIM, this::aim, TICK_DELAY),
                new AITarget<IAIState>(Shoot.EXPOSE, this::expose, TICK_DELAY),
                new AITarget<IAIState>(Shoot.FILE_PHOTOGRAPH, this::filePhotograph, TICK_DELAY));
    }

    @Override
    public @NotNull Class<BuildingPhotoBooth> getExpectedBuildingClass() {
        return BuildingPhotoBooth.class;
    }

    // ------------------------------------------------------------------ the shoot

    /** Idle hands, a camera on the shelf, daylight, and long enough since the last one. */
    private boolean wantsToShoot() {
        if (building == null || building.getBuildingLevel() < 1 || !ColonyCamera.available()) {
            return false;
        }
        if (world.getGameTime() - lastShot < BETWEEN_SHOOTS) {
            return false;
        }
        return !cameraFromStock().isEmpty();
    }

    /** The colony's camera, if it has one. The photographer crafts it themselves if it has not. */
    private ItemStack cameraFromStock() {
        final Item cameraItem = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("exposure", "camera"));
        if (cameraItem == null) {
            return ItemStack.EMPTY;
        }
        final ItemStack want = new ItemStack(cameraItem);
        final int have = InventoryUtils.getItemCountInProvider(building,
                stack -> stack.getItem() == cameraItem);
        if (have <= 0) {
            // Not a stall: the request goes out and the bench keeps working meanwhile.
            checkIfRequestForItemExistOrCreateAsync(want);
            return ItemStack.EMPTY;
        }
        return want;
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
     * Find something worth photographing and point the camera at it.
     *
     * <p>A citizen if one is near - a portrait is the thing a colony actually wants - and the way
     * the photographer is standing otherwise. The camera goes into the hand here, so it is up and
     * visible for the whole exposure.</p>
     */
    private IAIState aim() {
        camera = cameraFromStock();
        if (camera.isEmpty()) {
            return AIWorkerState.START_WORKING;
        }
        worker.setItemSlot(EquipmentSlot.MAINHAND, camera);
        worker.setRenderMetadata(RENDER_META_WORKING);

        final LivingEntity subject = nearestSitter();
        if (subject != null) {
            worker.getLookControl().setLookAt(subject, 60.0f, 30.0f);
            worker.lookAt(subject, 60.0f, 30.0f);
        }
        worker.startUsingItem(InteractionHand.MAIN_HAND);   // the arm comes up with the camera
        worker.swing(InteractionHand.MAIN_HAND);
        film = ColonyCamera.blank();
        rowsDone = 0;
        if (film.length == 0) {
            stopHolding();
            lastShot = world.getGameTime();
            return AIWorkerState.START_WORKING;
        }
        return Shoot.EXPOSE;
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
            stopHolding();
            return AIWorkerState.START_WORKING;
        }
        if (!ColonyCamera.renderBand(level, worker, film, rowsDone, ROWS_PER_STEP)) {
            stopHolding();
            lastShot = world.getGameTime();
            return AIWorkerState.START_WORKING;
        }
        rowsDone += ROWS_PER_STEP;
        level.sendParticles(ParticleTypes.END_ROD, worker.getX(), worker.getEyeY(), worker.getZ(),
                1, 0.12, 0.12, 0.12, 0.0);
        if (rowsDone < ColonyCamera.size()) {
            return Shoot.EXPOSE;
        }
        // The shutter. Exposure's own sound, by id, so we borrow nothing but a name.
        level.playSound(null, worker.blockPosition(),
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath("exposure", "item.camera.shutter_open")),
                SoundSource.NEUTRAL, 0.7f, 1.0f);
        return Shoot.FILE_PHOTOGRAPH;
    }

    /** Develop it, put it on the shelf, and give the camera back. */
    private IAIState filePhotograph() {
        if (!(world instanceof ServerLevel level)) {
            stopHolding();
            return AIWorkerState.START_WORKING;
        }
        final String name = worker.getCitizenData().getName();
        final String id = "voyager_" + building.getColony().getID() + "_"
                + world.getGameTime() + "_" + worker.getCivilianID();
        final ItemStack photograph = ColonyCamera.develop(level, film, id, name);
        film = new byte[0];
        lastShot = world.getGameTime();
        stopHolding();
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

    private void stopHolding() {
        worker.stopUsingItem();
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        worker.setRenderMetadata("");
        camera = ItemStack.EMPTY;
    }
}
