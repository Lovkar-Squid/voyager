package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.ai.EntityAIWorkPhotographer;
import net.minecraft.resources.ResourceLocation;
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

    /**
     * Render metadata while the camera is up. Deliberately not "working", which the crafting AI
     * sets at the bench: the viewfinder pose is for the shoot alone.
     */
    public static final String META_CAMERA = "camera";

    /** What the photographer is up to, in one word; {@link #getStatusLine()} has the details. */
    public enum Status {
        /** Nothing pressing: minding the studio between pictures. */
        IDLE,
        /** At the bench: film, frames, albums, cameras for the colony. */
        CRAFTING,
        /** A colonist is sitting for a portrait in the studio. */
        PORTRAIT,
        /** A visitor is sitting for a paid portrait. */
        SITTING,
        /** Out at a building site, photographing it for the colony chronicle. */
        CHRONICLE,
        /** Filing a photograph into the chronicle album. */
        FILING
    }

    private Status status = Status.IDLE;
    private String statusLine = "";

    public JobPhotographer(final ICitizenData citizen) {
        super(citizen);
    }

    public Status getStatus() {
        return status;
    }

    /** The last line the AI set, e.g. "photographing Anna in the studio". English; read by Colonist Errands. */
    public String getStatusLine() {
        return statusLine;
    }

    public void setStatus(final Status status, final String line) {
        this.status = status == null ? Status.IDLE : status;
        this.statusLine = line == null ? "" : line;
    }

    @Override
    public @NotNull EntityAIWorkPhotographer generateAI() {
        return new EntityAIWorkPhotographer(this);
    }

    @Override
    public @NotNull ResourceLocation getModel() {
        return Voyager.PHOTOGRAPHER_MODEL_ID;
    }
}
