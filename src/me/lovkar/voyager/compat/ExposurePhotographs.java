package me.lovkar.voyager.compat;

import java.util.List;

import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.util.ExtraData;
import io.github.mortuusars.exposure.world.camera.ExposureType;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.camera.frame.Photographer;
import io.github.mortuusars.exposure.world.level.storage.ExposureIdentifier;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.sky.SkyObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A real Exposure photograph, printed by the colony.
 *
 * <p><b>The thing everybody assumes is impossible.</b> Exposure makes a photograph by rendering
 * the world from the camera's viewpoint <i>on the client</i> and uploading the pixels, so a
 * colonist cannot take one - there is no server-side renderer, and no amount of AI changes that.
 * But a photograph does not have to carry pixels. Its {@code photograph_frame} component holds an
 * {@link ExposureIdentifier}, and an identifier can be either a stored exposure <b>or a texture</b>
 * - which is how Exposure projects images that were never photographed.</p>
 *
 * <p>Every cosmic object in Exposure: Space ships its own {@code catalog_texture}. So when the
 * Observatory's darkroom develops a plate of the Crab Nebula, it can print a genuine Exposure
 * photograph pointing at Exposure: Space's own picture of the Crab Nebula: framable, album-able,
 * projectable, and correct. The colony is not faking a render - it is printing the picture the
 * object's own datapack ships, which is exactly what a plate of it should look like.</p>
 *
 * <p><b>This is the one class in the mod that touches Exposure's code</b>, and it is deliberately
 * the only one. Exposure is an optional dependency, so nothing may reference this class except
 * through {@link me.lovkar.voyager.sky.SkyPhotograph}, which checks the mod is loaded first and
 * swallows anything that goes wrong. If Exposure changes underneath us, one file breaks and the
 * Observatory keeps working.</p>
 */
public final class ExposurePhotographs {

    private ExposurePhotographs() {
    }

    /**
     * Print the colony's photograph of a cosmic object, or an empty stack if there is nothing to
     * point the picture at.
     */
    public static ItemStack print(final SkyObject object, final String photographer) {
        if (object == null || object.catalogTexture() == null || object.catalogTexture().isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ResourceLocation texture = ResourceLocation.tryParse(object.catalogTexture());
        if (texture == null) {
            return ItemStack.EMPTY;
        }
        final Item photograph = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("exposure", "photograph"));
        if (photograph == null) {
            return ItemStack.EMPTY;
        }
        final ExtraData extra = new ExtraData();
        extra.put(Frame.TIMESTAMP, System.currentTimeMillis() / 1000L);
        final Frame frame = new Frame(ExposureIdentifier.texture(texture), ExposureType.COLOR,
                Photographer.EMPTY, List.of(), extra);
        final ItemStack stack = new ItemStack(photograph);
        stack.set(Exposure.DataComponents.PHOTOGRAPH_FRAME, frame);
        Voyager.LOGGER.debug("[Observatory] printed a photograph of {} for {}", object.id(), photographer);
        return stack;
    }
}
