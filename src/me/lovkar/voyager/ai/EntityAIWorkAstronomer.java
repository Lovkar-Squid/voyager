package me.lovkar.voyager.ai;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.HappinessConstants;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import me.lovkar.voyager.sky.SkyStudies;
import me.lovkar.voyager.sky.SkyStudy;
import net.neoforged.neoforge.items.IItemHandler;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.colony.BuildingObservatory;
import me.lovkar.voyager.colony.JobAstronomer;
import me.lovkar.voyager.colony.ObservatoryModules;
import me.lovkar.voyager.colony.ObservatoryResearch;
import me.lovkar.voyager.colony.SkyStudyModule;
import me.lovkar.voyager.item.SkyPlate;
import me.lovkar.voyager.sky.SkyCatalogue;
import me.lovkar.voyager.sky.SkyData;
import me.lovkar.voyager.sky.SkyEvent;
import me.lovkar.voyager.sky.SkyObject;
import me.lovkar.voyager.sky.SkyPhotograph;
import me.lovkar.voyager.sky.SkyRoll;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.NotNull;

/**
 * The astronomer's night.
 *
 * <p>Everybody else in the colony works the day; this one works the dark. The shift is the whole
 * character of the job: at dusk the astronomer walks out to the instrument - a telescope, an
 * armillary sphere, a stone gnomon or a ring, depending which of the five looks the colony built -
 * stands at it while the sky is clear, and comes back in at dawn with a plate.</p>
 *
 * <p>What is in the sky comes from {@link SkyData}, which reads Exposure: Space's cosmic objects
 * (and anybody else's) straight out of the datapacks. Not one line of their code is linked - the
 * arrangement their author asked for, and the reason this building can ship at all.</p>
 */
public class EntityAIWorkAstronomer extends AbstractEntityAIInteract<JobAstronomer, BuildingObservatory> {

    /** Ticks between AI steps. Nothing here is urgent. */
    private static final int TICK_DELAY = 20;
    /** Steps spent at the instrument for one night's work: about a minute and a half of watching. */
    private static final int WATCH_STEPS = 5;
    /** Steps the astronomer may spend trying to reach the instrument before giving up for the night. */
    private static final int WALK_ATTEMPTS = 20;
    /** Below this sky light the night has properly started. */
    private static final int DARK_ENOUGH = 8;
    /** Interaction shown on the citizen when the instrument cannot be reached. */
    public static final String CHAT_UNREACHABLE = "com.voyager.chat.observatory.unreachable";
    private static final String STAT_NIGHTS = "nights_observed";

    /** Our own states. IAIState is an interface, so an addon can have its own rather than
     *  borrowing ids that mean something else (the Voyager had to borrow the Nether Miner's). */
    public enum Watch implements IAIState {
        /** Walking out to the instrument. */
        WALK_TO_SCOPE(true),
        /** Standing at it, watching. */
        OBSERVE(false),
        /** Carrying the night's plate back inside. */
        FILE_PLATE(true),
        /** Walking to the darkroom with plates to develop. */
        WALK_TO_DARKROOM(true),
        /** In the darkroom, turning an exposed plate into a print. */
        DEVELOP(true);

        private final boolean eat;

        Watch(final boolean eat) {
            this.eat = eat;
        }

        @Override
        public boolean isOkayToEat() {
            return eat;
        }
    }

    /** The last day the colony was told what was in the sky, so it is told once a night. */
    private long lastAnnounced = -1;
    /** Guard so Darkroom Discipline develops one extra plate a step, not the whole rack. */
    private boolean secondPlate = false;
    private int watched = 0;
    private int walkAttempts = 0;
    /** The game day the last night's work was credited to, so one night pays once. */
    private long lastNight = -1;
    /** Said once: this pack has no cosmic objects, so every plate will be blank. */
    private boolean saidSkyIsEmpty = false;

    public EntityAIWorkAstronomer(final @NotNull JobAstronomer job) {
        super(job);
        super.registerTargets(
                new AITarget<IAIState>(AIWorkerState.IDLE, () -> AIWorkerState.START_WORKING, 20),
                new AITarget<IAIState>(AIWorkerState.START_WORKING, this::decide, TICK_DELAY),
                new AITarget<IAIState>(Watch.WALK_TO_SCOPE, this::goToScope, TICK_DELAY),
                new AITarget<IAIState>(Watch.OBSERVE, this::observe, TICK_DELAY),
                new AITarget<IAIState>(Watch.FILE_PLATE, this::filePlate, TICK_DELAY),
                new AITarget<IAIState>(Watch.WALK_TO_DARKROOM, this::goToDarkroom, TICK_DELAY),
                new AITarget<IAIState>(Watch.DEVELOP, this::develop, TICK_DELAY));
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingObservatory> getExpectedBuildingClass() {
        return BuildingObservatory.class;
    }

    /** A night's watch is not interrupted for a chat. */
    @Override
    public boolean canBeInterrupted() {
        return !job.isObserving();
    }

    @Override
    public IAIState getStateAfterPickUp() {
        return AIWorkerState.START_WORKING;
    }

    // ------------------------------------------------------------------ deciding

    /** Night and a clear sky send the astronomer out; anything else keeps them in. */
    private IAIState decide() {
        if (building.getBuildingLevel() < 1) {
            job.setStatus(JobAstronomer.Status.IDLE);
            return AIWorkerState.IDLE;
        }
        stockTheStudy();
        if (!isNight()) {
            // Daylight is the darkroom's shift: no plate develops itself, and an astronomer with
            // nothing to develop has genuinely nothing to do until dusk.
            if (exposedPlateSlot() >= 0) {
                job.setStatus(JobAstronomer.Status.WALKING);
                walkAttempts = 0;
                return Watch.WALK_TO_DARKROOM;
            }
            job.setStatus(JobAstronomer.Status.IDLE);
            walkToBuilding();
            return AIWorkerState.START_WORKING;
        }
        if (world.isRaining() || world.isThundering()) {
            job.setStatus(JobAstronomer.Status.CLOUDED);
            walkToBuilding();
            return AIWorkerState.START_WORKING;
        }
        if (world.getGameTime() / 24000L == lastNight) {
            job.setStatus(JobAstronomer.Status.IDLE);          // this night is already in the book
            walkToBuilding();
            return AIWorkerState.START_WORKING;
        }
        watched = 0;
        walkAttempts = 0;
        job.setStatus(JobAstronomer.Status.WALKING);
        return Watch.WALK_TO_SCOPE;
    }

    /** Night by the world's own clock, not by the light where the astronomer happens to stand. */
    private boolean isNight() {
        return isNightAt(world);
    }

    /**
     * The astronomer's working hours, as everything else in the mod should read them.
     *
     * <p>Public and static because the night shift is enforced in two places: here, where the
     * watch is kept, and in the mixin that stops MineColonies putting the astronomer to bed at
     * exactly the hour they are supposed to be on the roof. The two must agree or the citizen
     * ping-pongs between working and sleeping.</p>
     */
    public static boolean isNightAt(final net.minecraft.world.level.Level level) {
        if (level == null) {
            return false;
        }
        final long t = level.getDayTime() % 24000L;
        return t >= 12800L && t <= 23200L;
    }

    /**
     * Buy the study the colony has chosen, out of the Observatory's own racks.
     *
     * <p>The colony buys its own knowledge. The player picks a study in the GUI and the astronomer
     * does the rest: looks on the shelf, and if what it costs is not there, puts in a request so a
     * courier brings it. Making the player carry a telescopic lens up the hill by hand was exactly
     * the opposite of what a colony is for.</p>
     *
     * <p>Run once per decision tick, which is often enough for something measured in nights and
     * rare enough to cost nothing.</p>
     */
    private void stockTheStudy() {
        final SkyStudyModule studies = building.getModule(ObservatoryModules.STUDY);
        if (studies == null || studies.current() != null) {
            return;
        }
        final SkyStudy wanted = SkyStudies.byId(studies.wanted());
        if (wanted == null) {
            studies.forgetWanted();               // it left the datapack while we were shopping
            return;
        }
        // What is still missing from the building's own stock?
        final List<ItemStack> missing = new ArrayList<>();
        for (final ItemStack cost : wanted.costs()) {
            final int have = InventoryUtils.getItemCountInProvider(building,
                    stack -> ItemStack.isSameItem(stack, cost));
            if (have < cost.getCount()) {
                final ItemStack want = cost.copy();
                want.setCount(cost.getCount() - have);
                missing.add(want);
            }
        }
        if (!missing.isEmpty()) {
            // Not a stall: the request goes out and the astronomer carries on watching meanwhile.
            checkIfRequestForItemExistOrCreateAsync(missing);
            return;
        }
        final List<ItemStack> taken = new ArrayList<>();
        for (final ItemStack cost : wanted.costs()) {
            int left = cost.getCount();
            for (final IItemHandler handler : InventoryUtils.getItemHandlersFromProvider(building)) {
                if (left <= 0) {
                    break;
                }
                final int here = InventoryUtils.getItemCountInItemHandler(handler,
                        stack -> ItemStack.isSameItem(stack, cost));
                final int take = Math.min(left, here);
                if (take > 0 && InventoryUtils.attemptReduceStackInItemHandler(handler, cost, take)) {
                    left -= take;
                }
            }
            taken.add(cost.copy());
        }
        studies.beginIfPaid(taken);
        MessageUtils.format(Component.translatable("com.voyager.study.begun",
                        Component.translatable(wanted.nameKey())))
                .sendTo(building.getColony()).forAllPlayers();
    }

    // ------------------------------------------------------------------ the walk out

    private IAIState goToScope() {
        final BlockPos scope = building.getScopePosition();
        if (walkToSafePos(scope)) {
            job.setStatus(JobAstronomer.Status.OBSERVING);
            return Watch.OBSERVE;
        }
        if (++walkAttempts > WALK_ATTEMPTS) {
            job.setStatus(JobAstronomer.Status.BLOCKED);
            Voyager.LOGGER.info("[Observatory] {} cannot reach the instrument at {}",
                    worker.getCitizenData().getName(), scope);
            return AIWorkerState.START_WORKING;
        }
        return Watch.WALK_TO_SCOPE;
    }

    // ------------------------------------------------------------------ the watch

    /**
     * Standing at the instrument. The astronomer looks up, the sky shows what it shows, and after
     * a few steps the night is in the book and a plate goes on the rack.
     */
    private IAIState observe() {
        final BlockPos scope = building.getScopePosition();
        if (scope.distSqr(worker.blockPosition()) > 9 * 9) {
            return Watch.WALK_TO_SCOPE;                        // pushed away; walk back
        }
        if (!isNight()) {
            job.setStatus(JobAstronomer.Status.IDLE);
            return AIWorkerState.START_WORKING;                // dawn caught them mid-watch
        }
        worker.getLookControl().setLookAt(scope.getX() + 0.5, scope.getY() + 8.0, scope.getZ() - 6.0);
        worker.setRenderMetadata(RENDER_META_WORKING);
        if (world instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD,
                    scope.getX() + 0.5, scope.getY() + 1.6, scope.getZ() + 0.5, 2, 0.25, 0.35, 0.25, 0.0);
        }
        if (++watched < WATCH_STEPS) {
            return Watch.OBSERVE;
        }
        creditTheNight();
        return Watch.FILE_PLATE;
    }

    /**
     * Said once per Observatory: there is no sky in this pack.
     *
     * <p>Exposure: Space is optional, and without it (or any other datapack of cosmic objects) the
     * building still runs - the astronomer keeps the watch, the darkroom keeps working, and every
     * plate comes home blank. That is a coherent thing to happen, but a silent one, so the colony
     * gets told once rather than wondering why the rack is full of grey glass.</p>
     */
    private void sayIfTheSkyIsEmpty() {
        if (SkyData.ready() || saidSkyIsEmpty) {
            return;
        }
        saidSkyIsEmpty = true;
        MessageUtils.format(Component.translatable("com.voyager.observatory.shut"))
                .sendTo(building.getColony()).forManagers();
        Voyager.LOGGER.warn("[Observatory] no cosmic objects in any datapack - {} is working a blank sky",
                building.getPosition());
    }

    /** One night, one plate, and a line in the log saying what was up there. */
    private void creditTheNight() {
        sayIfTheSkyIsEmpty();
        final long night = world.getGameTime() / 24000L;
        lastNight = night;
        job.keptWatch(night);          // tonight is in the book; the astronomer may go to bed
        building.nightWorked();
        // The Observatory's own research is paid for in nights, and this is the night. A clouded
        // sky and an empty roof both buy nothing, which is the whole bargain.
        final SkyStudyModule study = building.getModule(ObservatoryModules.STUDY);
        if (study != null) {
            study.nightWorked(building.getColony());
        }
        keepTheNightWatch();
        worker.getCitizenExperienceHandler().addExperience(2.0);
        incrementActionsDoneAndDecSaturation();

        final SkyEvent tonight = SkyData.tonight(world);
        final SkyObject.LensTier lens = lensFitted();
        final SkyObject caught = SkyRoll.tonightsCatch(world, lens, worker.getCivilianID());
        final ItemStack plate = new ItemStack(Voyager.EXPOSED_PLATE.get());
        if (caught != null) {
            final SkyRoll.Band band = SkyRoll.bandOf(caught, SkyRoll.isExclusiveTonight(world, caught));
            SkyPlate.record(plate, caught.id(), night, band, building.getSchematicName());
        } else {
            // Nothing this lens could resolve. The plate still comes home - a blank plate is a
            // real result, and it is the colony's cue to grind a better lens.
            SkyPlate.record(plate, null, night, SkyRoll.Band.COMMON, building.getSchematicName());
        }
        if (!InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), plate)) {
            Voyager.LOGGER.info("[Observatory] {} had no room for tonight's plate", worker.getCitizenData().getName());
        }
        announce(caught);
        Voyager.LOGGER.info("[Observatory] {} worked night {} at a level {} {} - lens {}, sky {}, caught {}",
                worker.getCitizenData().getName(), building.getNights(), building.getBuildingLevel(),
                building.getSchematicName(), lens,
                tonight == null ? "ordinary" : tonight.id(),
                caught == null ? "nothing this lens can resolve" : caught.id());
    }

    /**
     * Nobody is punished for a night's work.
     *
     * <p>MineColonies drops a citizen's happiness for not sleeping - {@code EntityAISleep} clears
     * the "slepttonight" modifier and anybody who did not sleep keeps it. For a job whose whole
     * point is the dark that is simply wrong, so the astronomer clears it themselves at the end of
     * a watch. Marko's rule, and it applies whether or not the colony has researched anything.</p>
     *
     * <p>Star Party extends it to the whole colony on an event night: the town stays up to look,
     * and nobody is the worse for it in the morning.</p>
     */
    private void keepTheNightWatch() {
        clearSleepPenalty(worker.getCitizenData());
        if (SkyData.tonight(world) == null
                || !ObservatoryResearch.has(building.getColony(), ObservatoryResearch.STAR_PARTY)) {
            return;
        }
        for (final ICitizenData citizen : building.getColony().getCitizenManager().getCitizens()) {
            clearSleepPenalty(citizen);
        }
        Voyager.LOGGER.info("[Observatory] star party: the whole colony stayed up for {}",
                SkyData.tonight(world).id());
    }

    private static void clearSleepPenalty(final ICitizenData citizen) {
        if (citizen != null && citizen.getCitizenHappinessHandler() != null) {
            citizen.getCitizenHappinessHandler().resetModifier(HappinessConstants.SLEPTTONIGHT);
        }
    }

    /** Ephemeris: the colony hears what tomorrow night holds, tonight. */
    private void announce(final SkyObject caught) {
        final long day = world.getGameTime() / 24000L;
        if (day == lastAnnounced) {
            return;
        }
        lastAnnounced = day;
        final SkyEvent tonight = SkyData.tonight(world);
        if (tonight != null) {
            MessageUtils.format(Component.translatable("com.voyager.sky.event_tonight",
                            Component.translatable(tonight.nameKey())))
                    .sendTo(building.getColony()).forAllPlayers();
        }
        if (caught == null) {
            MessageUtils.format(Component.translatable("com.voyager.sky.blank",
                            worker.getCitizenData().getName()))
                    .sendTo(building.getColony()).forAllPlayers();
        }
    }

    /** The lens the colony has ground: level 1 is a bad one, level 5 the best there is. */
    private SkyObject.LensTier lensFitted() {
        final SkyObject.LensTier[] all = SkyObject.LensTier.values();
        return all[Math.max(0, Math.min(all.length - 1, building.lensTier()))];
    }

    // ------------------------------------------------------------------ the darkroom

    /** The first exposed plate the astronomer is carrying, or -1. */
    private int exposedPlateSlot() {
        return InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getItemHandlerCitizen(),
                stack -> stack.getItem() == Voyager.EXPOSED_PLATE.get());
    }

    private IAIState goToDarkroom() {
        final BlockPos dark = building.getDarkroomPosition();
        if (walkToSafePos(dark)) {
            return Watch.DEVELOP;
        }
        if (++walkAttempts > WALK_ATTEMPTS) {
            // No darkroom yet (level 1 has none) - the plates keep until there is one.
            job.setStatus(JobAstronomer.Status.IDLE);
            return AIWorkerState.START_WORKING;
        }
        return Watch.WALK_TO_DARKROOM;
    }

    /**
     * Developing: one plate a step. The exposed plate becomes a star plate carrying the same
     * record, so what the darkroom hands back is the same night, now readable.
     */
    private IAIState develop() {
        final int slot = exposedPlateSlot();
        if (slot < 0) {
            job.setStatus(JobAstronomer.Status.IDLE);
            return Watch.FILE_PLATE;
        }
        // Darkroom Discipline is a steadier hand, not a faster one: it gets a second plate through
        // in the same step rather than shortening the step, which is the same saving and reads
        // better than a number ticking down.
        final boolean steady = ObservatoryResearch.has(building.getColony(), ObservatoryResearch.DARKROOM_DISCIPLINE);
        final ItemStack exposed = worker.getInventoryCitizen().getStackInSlot(slot);
        final ItemStack print = new ItemStack(Voyager.STAR_PLATE.get());
        final ResourceLocation object = SkyPlate.objectOf(exposed);
        final long night = SkyPlate.nightOf(exposed);
        final Developed made = readTheBook(object, SkyPlate.bandOf(exposed));

        SkyPlate.record(print, object, night, made.band(), building.getSchematicName());
        SkyPlate.mark(print, made.mark());
        worker.getInventoryCitizen().extractItem(slot, 1, false);
        InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), print);
        building.takePlate();
        payTheSky(object, made.rewardShare());
        printThePhotograph(object, made.mark());
        building.catalogue(object, made.band(), night);
        if (made.mark() == SkyPlate.Mark.COMPOSITE) {
            SkyCatalogue.markCombined(building.getColony(), object);
            MessageUtils.format(Component.translatable("com.voyager.sky.combined",
                            worker.getCitizenData().getName(), nameOf(object),
                            Component.translatable(made.band().key())))
                    .sendTo(building.getColony()).forAllPlayers();
        }
        worker.getCitizenExperienceHandler().addExperience(made.xp());
        worker.setRenderMetadata(RENDER_META_WORKING);
        if (world instanceof ServerLevel level) {
            final BlockPos dark = building.getDarkroomPosition();
            level.playSound(null, dark, SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.4f,
                    made.mark() == SkyPlate.Mark.COMPOSITE ? 2.0f : 1.6f);
        }
        Voyager.LOGGER.info("[Observatory] {} developed a {} plate of {} ({}); the colony now has {} object(s) on record",
                worker.getCitizenData().getName(), made.mark().name().toLowerCase(),
                object == null ? "a blank sky" : object, made.band(),
                SkyCatalogue.distinct(building.getColony()));
        if (steady && !secondPlate) {
            secondPlate = true;                                   // exactly one extra, not the rack
            final IAIState next = develop();
            secondPlate = false;
            return next;
        }
        return Watch.DEVELOP;
    }

    /** What the darkroom is about to hand back, and what the colony is owed for it. */
    private record Developed(SkyRoll.Band band, SkyPlate.Mark mark, double xp, double rewardShare) {
    }

    /**
     * Nights before an object the colony already has is worth paying for again.
     *
     * <p>Exposure: Space makes the same bargain with a player - a first study pays in full, a
     * repeat pays a share once a cooldown has passed - and a colony should not get a better deal
     * than the person who wrote the sky.</p>
     */
    private static final long REWARD_COOLDOWN_NIGHTS = 8L;
    /** What a repeat study is worth, once Second Exposure has taught the colony to bother. */
    private static final double REPEAT_SHARE = 0.25;

    /**
     * Look the object up in the colony's book before printing, and decide what this plate is worth.
     *
     * <p>This is where Second Exposure and Comparative Astronomy stop being data and start being
     * mechanics. A colony that has never seen the object gets a plain print. A colony that has
     * gets a duplicate - and what a duplicate is worth is exactly what those two researches buy:</p>
     *
     * <ul>
     *   <li>with neither, half the practice and a plate that only says "another one";</li>
     *   <li>with <b>Second Exposure</b>, full value, and the print carries the better of the two
     *       nights rather than tonight's - a re-shot object is still worth looking at;</li>
     *   <li>with <b>Comparative Astronomy</b>, the two nights are put together: one composite print,
     *       a band brighter than either, and the book notes that this object's two best are spent,
     *       so it can never be farmed twice.</li>
     * </ul>
     */
    private Developed readTheBook(final ResourceLocation object, final SkyRoll.Band tonight) {
        final SkyCatalogue.Entry prior = SkyCatalogue.find(building.getColony(), object);
        if (object == null || prior == null || prior.plates() <= 0) {
            return new Developed(tonight, SkyPlate.Mark.FIRST, 1.0, 1.0);
        }
        final SkyRoll.Band better = prior.best().ordinal() >= tonight.ordinal() ? prior.best() : tonight;
        if (!prior.combined()
                && ObservatoryResearch.has(building.getColony(), ObservatoryResearch.COMPARATIVE_ASTRONOMY)) {
            // Two nights put together are a new result, and paid for like one.
            return new Developed(SkyCatalogue.brighter(better), SkyPlate.Mark.COMPOSITE, 3.0, 1.0);
        }
        if (ObservatoryResearch.has(building.getColony(), ObservatoryResearch.SECOND_EXPOSURE)) {
            final long rested = world.getGameTime() / 24000L - prior.lastNight();
            final double share = rested >= REWARD_COOLDOWN_NIGHTS ? REPEAT_SHARE : 0.0;
            return new Developed(better, SkyPlate.Mark.DUPLICATE, 1.0, share);
        }
        return new Developed(tonight, SkyPlate.Mark.DUPLICATE, 0.5, 0.0);
    }

    /**
     * What the sky pays for being looked at.
     *
     * <p>Every cosmic object carries its own {@code rewards} list - amethyst, diamonds, netherite
     * scrap, film, bottles of experience - and until now the Observatory read that list and threw
     * it away. This is the reason to build one: the colony that studies the sky is paid by it,
     * in the objects' own currency, out of their own datapack. Nothing here knows what an
     * "amethyst shard" is; it is an id in a file somebody else wrote.</p>
     */
    private void payTheSky(final ResourceLocation object, final double share) {
        final SkyObject studied = SkyData.byId(object);
        if (studied == null || share <= 0.0 || studied.rewards().isEmpty()) {
            return;
        }
        final List<ItemStack> paid = new ArrayList<>();
        for (final SkyObject.Reward reward : studied.rewards()) {
            if (!BuiltInRegistries.ITEM.containsKey(reward.item())) {
                continue;                         // an item from a mod this pack does not have
            }
            final int count = Math.max(1, (int) Math.round(reward.count() * share));
            final ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(reward.item()), count);
            if (InventoryUtils.addItemStackToProvider(building, stack)
                    || InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), stack)) {
                paid.add(stack);
            }
        }
        if (paid.isEmpty()) {
            return;
        }
        Voyager.LOGGER.info("[Observatory] {} paid {} for {} ({}% share)", object, describeReward(paid),
                worker.getCitizenData().getName(), (int) Math.round(share * 100));
        MessageUtils.format(Component.translatable("com.voyager.sky.reward",
                        nameOf(object), describeReward(paid)))
                .sendTo(building.getColony()).forAllPlayers();
    }

    private static Component describeReward(final List<ItemStack> paid) {
        Component line = Component.literal("");
        for (int i = 0; i < paid.size(); i++) {
            if (i > 0) {
                line = Component.literal("").append(line).append(Component.literal(", "));
            }
            line = Component.literal("").append(line)
                    .append(Component.literal(paid.get(i).getCount() + "x "))
                    .append(paid.get(i).getHoverName());
        }
        return line;
    }

    /**
     * The print worth hanging: a real Exposure photograph of what the plate caught.
     *
     * <p>Only for a first sighting or a composite - the two results the colony should want to
     * frame. A duplicate is a duplicate and gets the plate alone, or the wall fills with the same
     * nebula five times.</p>
     *
     * <p>See {@link me.lovkar.voyager.sky.SkyPhotograph}: this is genuinely one of Exposure's own
     * photographs, and it works in their frames, albums, lecterns and projector.</p>
     */
    private void printThePhotograph(final ResourceLocation object, final SkyPlate.Mark mark) {
        if (object == null || (mark != SkyPlate.Mark.FIRST && mark != SkyPlate.Mark.COMPOSITE)) {
            return;
        }
        final ItemStack print = SkyPhotograph.print(SkyData.byId(object),
                worker.getCitizenData().getName());
        if (print.isEmpty()) {
            return;
        }
        if (!InventoryUtils.addItemStackToProvider(building, print)) {
            InventoryUtils.addItemStackToItemHandler(worker.getItemHandlerCitizen(), print);
        }
        Voyager.LOGGER.info("[Observatory] {} printed a photograph of {}",
                worker.getCitizenData().getName(), object);
    }

    /** An object's name for chat: its own key if the datapack is here, its id if it is not. */
    private static Component nameOf(final ResourceLocation object) {
        if (object == null) {
            return Component.translatable("com.voyager.plate.blank");
        }
        final SkyObject known = SkyData.byId(object);
        return known != null ? Component.translatable(known.nameKey()) : Component.literal(object.getPath());
    }

    // ------------------------------------------------------------------ back inside

    private IAIState filePlate() {
        if (!walkToBuilding()) {
            return Watch.FILE_PLATE;
        }
        for (int slot = 0; slot < worker.getInventoryCitizen().getSlots(); slot++) {
            final ItemStack stack = worker.getInventoryCitizen().getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == Voyager.STAR_PLATE.get()) {
                InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, building);
            }
        }
        worker.setRenderMetadata("");
        job.setStatus(JobAstronomer.Status.IDLE);
        return AIWorkerState.START_WORKING;
    }

    /** The dark the astronomer needs: used only for the log line, never to gate the work. */
    @SuppressWarnings("unused")
    private int skyLight(final BlockPos pos) {
        return world.getBrightness(LightLayer.SKY, pos);
    }
}
