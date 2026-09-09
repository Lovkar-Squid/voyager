package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import me.lovkar.voyager.ai.EntityAIWorkPhotographer;
import org.jetbrains.annotations.NotNull;

/**
 * The Photographer. Runs the colony's darkroom and print shop.
 *
 * <p>A crafter, deliberately. A colonist cannot take a photograph - Exposure renders an image on
 * the client and uploads it, and no server-side worker can do that - so this profession is not the
 * one holding the camera. It is the one who develops the film the player brings home, prints it in
 * the Lightroom, ages it, copies it, frames it and binds it into albums, and who crafts every
 * camera, film, frame and album the colony needs so nobody has to hand-craft them.</p>
 */
public class JobPhotographer extends AbstractJobCrafter<EntityAIWorkPhotographer, JobPhotographer> {

    public JobPhotographer(final ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public @NotNull EntityAIWorkPhotographer generateAI() {
        return new EntityAIWorkPhotographer(this);
    }
}
