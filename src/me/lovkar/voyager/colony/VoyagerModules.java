package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.ExpeditionLogModule;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ExpeditionLogModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RestaurantMenuModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import me.lovkar.voyager.Voyager;

/** Building modules of the Departure Point (same set the Nether Mine uses, minus the portal setting). */
public final class VoyagerModules {

    /** One Voyager per building (two with Buddy System); Adaptability shapes the finds, Agility the odds of coming home unhurt. */
    public static final BuildingEntry.ModuleProducer<CraftingWorkerBuildingModule, WorkerBuildingModuleView> WORK =
            new BuildingEntry.ModuleProducer<>("voyager_work",
                    () -> new CraftingWorkerBuildingModule(Voyager.JOB.get(), Skill.Adaptability, Skill.Agility, false, BuildingVoyager::crewSize),
                    () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<BuildingVoyager.CraftingModule, CraftingModuleView> CRAFT =
            new BuildingEntry.ModuleProducer<>("voyager_craft",
                    () -> new BuildingVoyager.CraftingModule(Voyager.JOB.get()),
                    () -> CraftingModuleView::new);

    /** The expedition log needs no research - every trip is worth reading about. */
    public static final BuildingEntry.ModuleProducer<ExpeditionLogModule, ExpeditionLogModuleView> EXPEDITION =
            new BuildingEntry.ModuleProducer<>("voyager_expedition",
                    () -> new ExpeditionLogModule(null),
                    () -> ExpeditionLogModuleView::new);

    /**
     * Rations: which food the Voyager packs for the trip. The stock module would keep a full
     * stack of every menu item in the racks at all times (and re-order the moment the Voyager
     * packs some); we only ask for food while there are not enough rations for the next trip,
     * counting what the Voyager already carries.
     */
    public static final BuildingEntry.ModuleProducer<RestaurantMenuModule, RestaurantMenuModuleView> MENU =
            new BuildingEntry.ModuleProducer<>("voyager_menu",
                    () -> new RestaurantMenuModule(false, BuildingVoyager::rationStockWanted),
                    () -> RestaurantMenuModuleView::new);

    private VoyagerModules() {
    }
}
