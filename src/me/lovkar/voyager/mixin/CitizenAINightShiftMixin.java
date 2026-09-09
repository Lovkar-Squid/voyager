package me.lovkar.voyager.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.workers.CitizenAI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.lovkar.voyager.ai.EntityAIWorkAstronomer;
import me.lovkar.voyager.colony.JobAstronomer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The astronomer keeps the night watch instead of going to bed during it.
 *
 * <p><b>Why a mixin, when nothing else in this mod needs one.</b> MineColonies decides what a
 * citizen does next in {@code CitizenAI.calculateNextState}, and the decision to sleep is written
 * into that method with exactly one exemption: {@code job instanceof AbstractJobGuard}. Guards
 * work at night because the guard branch returns before the sleep logic is ever reached. There is
 * no hook on the job, none on the building, and none on the schedule - {@code canAIBeInterrupted}
 * is consulted for sickness and not for bedtime.</p>
 *
 * <p>So without this, the Observatory does not work at all: dusk falls, the astronomer walks to
 * a bed, and the one job in the mod whose whole point is the dark sleeps through it. Making
 * {@code JobAstronomer} extend the guard job would take the exemption, but it would also drag in
 * combat AI, guard-tower requirements and a citizen the colony counts as defence - a much bigger
 * lie than this.</p>
 *
 * <p>The injection is as narrow as it can be: it only ever <b>forces work</b>, only for an
 * astronomer, only at night, and it stands aside for everything MineColonies would rightly
 * interrupt work for - hunger, illness and a raid. In daylight the astronomer sleeps like anybody
 * else, which is the other half of the point.</p>
 */
@Mixin(CitizenAI.class)
public abstract class CitizenAINightShiftMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    @Inject(method = "calculateNextState", at = @At("HEAD"), cancellable = true)
    private void voyager$keepTheNightWatch(final CallbackInfoReturnable<IState> callback) {
        if (citizen == null || citizen.getCitizenData() == null) {
            return;
        }
        final IJob<?> job = citizen.getCitizenData().getJob();
        if (!(job instanceof JobAstronomer astronomer)) {
            return;
        }
        if (!EntityAIWorkAstronomer.isNightAt(citizen.level())) {
            return;                                  // by day they sleep, like everybody else
        }
        if (astronomer.keptWatchTonight(citizen.level())) {
            return;                                  // the plate is exposed; bed is allowed now
        }
        if (job.getWorkBuilding() == null) {
            return;                                  // no Observatory to work at
        }
        // Everything MineColonies would rightly stop work for still stops it.
        if (citizen.getCitizenData().getCitizenDiseaseHandler() != null
                && citizen.getCitizenData().getCitizenDiseaseHandler().isSick()) {
            return;
        }
        if (((CitizenAI) (Object) this).shouldEat()) {
            return;
        }
        final IColony colony = citizen.getCitizenColonyHandler() == null
                ? null : citizen.getCitizenColonyHandler().getColonyOrRegister();
        if (colony != null && colony.getRaiderManager() != null && colony.getRaiderManager().isRaided()) {
            return;                                  // a raid sends everyone indoors, astronomers too
        }
        if (citizen.getCitizenSleepHandler() != null && citizen.getCitizenSleepHandler().isAsleep()) {
            citizen.getCitizenSleepHandler().onWakeUp();
        }
        callback.setReturnValue(CitizenAIState.WORK);
    }
}
