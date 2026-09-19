package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.WorkAtHomeBuildingModule;

import java.util.List;
import java.util.function.Function;

/**
 * The Photo Booth's housing slot - a module that hires nobody and exists only to be counted.
 *
 * <p>MineColonies works out how many citizens a colony can hold in
 * {@code CitizenManager.calculateMaxCitizens()}, and a building where the worker sleeps is counted
 * by a rule of its own:</p>
 *
 * <pre>
 *   if (building.hasModule(BuildingModules.BED) &amp;&amp; building.hasModule(WorkAtHomeBuildingModule.class))
 *       max += building.getAllAssignedCitizen().size();
 *   else if (building.hasModule(LivingBuildingModule.class)) ...
 * </pre>
 *
 * <p>That is what makes a guard tower or the Observatory carry its own people: the worker gives up
 * their bed in a house, and the colony's ceiling is raised by one to make up for it. The Photo
 * Booth had the beds and the worker who sleeps in them, but not the class - its
 * {@link WorkAtHomeCraftingModule} has to extend {@code CraftingWorkerBuildingModule}, because the
 * crafting AI casts the job's module to exactly that. So the photographer moved out of their house
 * and nothing was put back: the colony kept the same ceiling with one bed standing empty, and
 * reported that there was no room for anybody new while a house plainly had space.</p>
 *
 * <p>Java lets a class have one parent, so the honest fix is a second module: this one. It is a
 * real {@code WorkAtHomeBuildingModule}, which is all the arithmetic above asks for, and it is
 * deliberately inert - nobody can be hired into it, it is always full, and it creates no request
 * resolvers, so the photographer's crafting requests stay with the one module that handles them.
 * The citizens it is credited with are the building's, counted by MineColonies itself.</p>
 */
public class PhotoBoothHomeModule extends WorkAtHomeBuildingModule {

    public PhotoBoothHomeModule(final JobEntry entry, final Skill primary, final Skill secondary,
                                final Function<IBuilding, Integer> sizeLimit) {
        super(entry, primary, secondary, false, sizeLimit);
    }

    /** Nobody is hired here. The photographers are hired by {@link WorkAtHomeCraftingModule}. */
    @Override
    public boolean assignCitizen(final ICitizenData citizen) {
        return false;
    }

    /** ...and the colony is never offered the empty seat, so it never tries. */
    @Override
    public boolean isFull() {
        return true;
    }

    /**
     * If a lookup for "the module of the photographer's job" lands on this one, it answers with
     * the photographer.
     *
     * <p>MineColonies finds a worker with
     * {@code getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == job)}, which
     * returns whichever matching module comes first - and this one carries the same job entry, so
     * it can be the one found. It holds no citizens of its own, so it hands back the building's,
     * which is the same answer the crafting module would give. The skills it was built with are
     * the crafting module's too, for the same reason.</p>
     */
    @Override
    public ICitizenData getFirstCitizen() {
        final ICitizenData mine = super.getFirstCitizen();
        if (mine != null) {
            return mine;
        }
        return building == null ? null : building.getAllAssignedCitizen().stream().findFirst().orElse(null);
    }

    /**
     * No resolvers. {@code WorkerBuildingModule} hands out three of them per module - a building
     * resolver and two private crafting resolvers keyed to the job - and a second set for the same
     * building and the same job would have them competing over the photographer's requests.
     */
    @Override
    public List<IRequestResolver<?>> createResolvers() {
        return List.of();
    }
}
