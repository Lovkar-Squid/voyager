package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkAtHomeBuildingModule;

import java.util.function.Function;

/**
 * A crafter's work module whose worker lives at the building - the Photographer's.
 *
 * <p>MineColonies has {@link WorkAtHomeBuildingModule} for a worker who lives where they work
 * (the astronomer uses it), and {@link CraftingWorkerBuildingModule} for a worker who crafts -
 * the crafting AI and the request resolvers cast the job's module to the latter, so a crafter
 * cannot simply use the former. This is the crafting module with the work-at-home behaviour
 * added, the same two steps {@code WorkAtHomeBuildingModule} takes: being hired here makes this
 * building the citizen's home (and frees the bed they held in a house), being fired or losing
 * the building makes them homeless again so a house takes them back. The bed in the studio is
 * theirs through {@code BuildingModules.BED}, the way the astronomer's is.</p>
 *
 * <p>Marko's rule, the same as the Observatory's: the photographer lives at the Photo Booth.
 * The first alpha hired them as an ordinary crafter and they walked home to a house at night.</p>
 */
public class WorkAtHomeCraftingModule extends CraftingWorkerBuildingModule {

    public WorkAtHomeCraftingModule(final JobEntry entry, final Skill primary, final Skill secondary,
                                    final boolean canWorkingDuringRain,
                                    final Function<IBuilding, Integer> sizeLimit) {
        super(entry, primary, secondary, canWorkingDuringRain, sizeLimit);
    }

    @Override
    public boolean assignCitizen(final ICitizenData citizen) {
        if (!super.assignCitizen(citizen)) {
            return false;
        }
        if (citizen != null) {
            final IBuilding home = citizen.getHomeBuilding();
            if (home == null || !home.getID().equals(building.getID())) {
                // setHomeBuilding takes them out of their old house's living module itself
                citizen.setHomeBuilding(building);
            }
        }
        return true;
    }

    /**
     * A photographer hired before this module existed (alpha.18 and earlier saves) is still a
     * commuter: they are on the roster but their home is a house in town. Once a colony tick,
     * anyone on the roster who does not live here yet moves in - so an old save needs no
     * re-hiring.
     */
    @Override
    public void onColonyTick(final IColony colony) {
        super.onColonyTick(colony);
        for (final ICitizenData citizen : getAssignedCitizen()) {
            final IBuilding home = citizen.getHomeBuilding();
            if (home == null || !home.getID().equals(building.getID())) {
                citizen.setHomeBuilding(building);
            }
        }
    }

    @Override
    public boolean removeCitizen(final ICitizenData citizen) {
        final boolean removed = super.removeCitizen(citizen);
        if (removed && citizen != null) {
            final IBuilding home = citizen.getHomeBuilding();
            if (home != null && home.getID().equals(building.getID())) {
                citizen.setHomeBuilding(null);
            }
        }
        return removed;
    }
}
