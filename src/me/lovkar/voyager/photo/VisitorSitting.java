package me.lovkar.voyager.photo;

import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.entity.ai.visitor.EntityAIVisitor;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import me.lovkar.voyager.colony.BuildingPhotoBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A visitor's trip to the Photo Booth.
 *
 * <p>MineColonies' visitors sit in the tavern, wander, and go home; their behaviour is a small
 * state machine on the entity, and MineColonies leaves it open - anybody can add transitions to
 * it, which is how Trade Post sends them shopping. We do the same: from IDLE or WANDERING, by day,
 * when a Photo Booth is open and nobody else is being photographed, a visitor may decide to have
 * their portrait taken. They walk to the sitter's mark in front of the gallery wall, stand there
 * looking at the photographer, and wait - a minute at most - for the shutter. Then they pay
 * (Trade Post coins, if there is a Trade Post) and take the print with them.</p>
 *
 * <p>One sitting per visit. A visitor who is kept waiting leaves, and the booth is free again.</p>
 */
public final class VisitorSitting {

    /** The visitor's extra states, alongside MineColonies' own. */
    public enum State implements IState {
        TO_STUDIO, POSING
    }

    /** How long a visitor waits at the mark before deciding the photographer is not coming. */
    private static final int PATIENCE = 1200;
    /** Ticks between the visitor wondering about a portrait, so they do not all rush at once. */
    private static final int THINK_EVERY = 300;
    /** Chance, per thought, that an idle visitor decides on a portrait (about one in ten). */
    private static final double WHIM = 0.1;

    private final IVisitorData visitor;
    private final BuildingPhotoBooth booth;
    private int patience;
    private boolean sat;
    private long nextThought;

    private VisitorSitting(final IVisitorData visitor, final BuildingPhotoBooth booth) {
        this.visitor = visitor;
        this.booth = booth;
    }

    /** Give a visitor the idea. Safe to call once per visitor entity; the booth remembers who has it. */
    public static void attach(final IVisitorData visitor, final BuildingPhotoBooth booth) {
        if (visitor.getEntity().isEmpty()
                || !(visitor.getEntity().get() instanceof AbstractEntityCitizen citizen)) {
            return;
        }
        final ITickRateStateMachine<IState> machine = citizen.getEntityStateController();
        final VisitorSitting sitting = new VisitorSitting(visitor, booth);
        sitting.nextThought = citizen.level().getGameTime() + citizen.getRandom().nextInt(THINK_EVERY);
        machine.addTransition(new TickingTransition<IState>(EntityAIVisitor.VisitorState.IDLE,
                sitting::wantsPortrait, () -> State.TO_STUDIO, 100));
        machine.addTransition(new TickingTransition<IState>(EntityAIVisitor.VisitorState.WANDERING,
                sitting::wantsPortrait, () -> State.TO_STUDIO, 100));
        machine.addTransition(new TickingTransition<IState>(State.TO_STUDIO, () -> true, sitting::toStudio, 20));
        machine.addTransition(new TickingTransition<IState>(State.POSING, () -> true, sitting::pose, 20));
    }

    private AbstractEntityCitizen entity() {
        final Entity e = visitor.getEntity().orElse(null);
        return e instanceof AbstractEntityCitizen citizen ? citizen : null;
    }

    /** Daylight, an open booth with nobody in the chair, and a whim. */
    private boolean wantsPortrait() {
        final AbstractEntityCitizen me = entity();
        if (sat || me == null || !WorldUtil.isDayTime(me.level())) {
            return false;
        }
        final long now = me.level().getGameTime();
        if (now < nextThought) {
            return false;
        }
        nextThought = now + THINK_EVERY;
        if (!booth.isOpenForSittings() || me.getRandom().nextDouble() > WHIM) {
            return false;
        }
        if (!booth.book(visitor)) {
            return false;                          // somebody else is being photographed
        }
        patience = PATIENCE;
        return true;
    }

    private IState toStudio() {
        final AbstractEntityCitizen me = entity();
        if (me == null || !booth.isBookedBy(visitor)) {
            return leave();
        }
        if ((patience -= 20) <= 0) {
            booth.cancelSitting(visitor);
            return leave();
        }
        final BlockPos mark = booth.getSitterPosition();
        if (EntityNavigationUtils.walkToPos(me, mark, 2, true)) {
            booth.sitterArrived(visitor);
            patience = PATIENCE;
            return State.POSING;
        }
        return State.TO_STUDIO;
    }

    /** Stand at the mark, look at the camera, wait for the shutter. */
    private IState pose() {
        final AbstractEntityCitizen me = entity();
        if (me == null) {
            booth.cancelSitting(visitor);
            return leave();
        }
        if (booth.isSittingDone(visitor)) {
            sat = true;
            booth.forgetSitting(visitor);
            return leave();
        }
        if (!booth.isBookedBy(visitor) || (patience -= 20) <= 0) {
            booth.cancelSitting(visitor);
            return leave();
        }
        final BlockPos mark = booth.getSitterPosition();
        if (me.blockPosition().distSqr(mark) > 4.0) {
            EntityNavigationUtils.walkToPos(me, mark, 2, true);     // drifted; back to the mark
        } else {
            final LivingEntity photographer = booth.photographerNear(me, 12.0);
            if (photographer != null) {
                me.getLookControl().setLookAt(photographer, 30.0f, 30.0f);
            }
        }
        return State.POSING;
    }

    private IState leave() {
        return EntityAIVisitor.VisitorState.WANDERING;
    }
}
