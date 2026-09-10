package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.WorkAtHomeBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import me.lovkar.voyager.Voyager;

/** Building modules of the Observatory. */
public final class ObservatoryModules {

    /**
     * One astronomer to a building, two once the colony has trained an apprentice at a level 4
     * Observatory. Knowledge decides what they can identify, Focus how long they hold a watch.
     *
     * <p>A <b>work-at-home</b> module, not a plain worker one: the astronomer lives at the
     * Observatory. That was Marko's rule from the start and it is the only arrangement that makes
     * sense - somebody whose whole job is the dark should not be walking home across a sleeping
     * town at dawn and taking up a bed in a house they are never in at night. Assigning the job
     * assigns the home with it, and the bed in the study is theirs.</p>
     */
    public static final BuildingEntry.ModuleProducer<WorkAtHomeBuildingModule, WorkerBuildingModuleView> WORK =
            new BuildingEntry.ModuleProducer<>("observatory_work",
                    () -> new WorkAtHomeBuildingModule(Voyager.ASTRONOMER_JOB.get(),
                            Skill.Knowledge, Skill.Focus, false, BuildingObservatory::crewSize),
                    () -> WorkerBuildingModuleView::new);

    /**
     * The Observatory's own study of the sky - its research tab, and its book.
     *
     * <p>Not MineColonies' research: see {@link SkyStudyModule} for why a building that pays for
     * knowledge in nights cannot use a system that pays for it in hours.</p>
     */
    public static final BuildingEntry.ModuleProducer<SkyStudyModule, SkyStudyModuleView> STUDY =
            new BuildingEntry.ModuleProducer<>("observatory_study",
                    SkyStudyModule::new, () -> SkyStudyModuleView::new);

    /**
     * The two switches the colony has over the night: whether the astronomer keeps the watch from
     * the lookout when there is one, and how many guards walk out with them - one by default,
     * because a colonist alone on a hill at night is a colonist the zombies get to first.
     */
    public static final BuildingEntry.ModuleProducer<SettingsModule, SettingsModuleView> SETTINGS =
            new BuildingEntry.ModuleProducer<>("observatory_settings",
                    () -> (SettingsModule) new SettingsModule()
                            .with(BuildingObservatory.LOOKOUT, new BoolSetting(true))
                            .with(BuildingObservatory.ESCORT, new StringSetting(
                                    BuildingObservatory.ESCORT_ONE, BuildingObservatory.ESCORT_TWO,
                                    BuildingObservatory.ESCORT_OFF)),
                    () -> SettingsModuleView::new);

    private ObservatoryModules() {
    }
}
