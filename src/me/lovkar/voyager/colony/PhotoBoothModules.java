package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import me.lovkar.voyager.Voyager;

/** Building modules of the Photo Booth. */
public final class PhotoBoothModules {

    /**
     * One photographer, two from level 4. Creativity decides what they can be taught, Dexterity
     * how steady the hand is at the enlarger.
     */
    public static final BuildingEntry.ModuleProducer<CraftingWorkerBuildingModule, WorkerBuildingModuleView> WORK =
            new BuildingEntry.ModuleProducer<>("photobooth_work",
                    () -> new CraftingWorkerBuildingModule(Voyager.PHOTOGRAPHER_JOB.get(),
                            Skill.Creativity, Skill.Dexterity, false, BuildingPhotoBooth::crewSize),
                    () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<BuildingPhotoBooth.CraftingModule, CraftingModuleView> CRAFT =
            new BuildingEntry.ModuleProducer<>("photobooth_craft",
                    () -> new BuildingPhotoBooth.CraftingModule(Voyager.PHOTOGRAPHER_JOB.get()),
                    () -> CraftingModuleView::new);

    private PhotoBoothModules() {
    }
}
