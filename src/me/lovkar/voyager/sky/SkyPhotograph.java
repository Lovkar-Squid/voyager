package me.lovkar.voyager.sky;

import me.lovkar.voyager.Voyager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * The safe side of the one wall this mod builds against Exposure's code.
 *
 * <p>Exposure is optional. Everything that touches its classes lives in
 * {@code compat.ExposurePhotographs}, and nothing may reach that class except through here: this
 * checks the mod is actually loaded before the JVM has any reason to resolve it, and treats a
 * failure as "no photograph today" rather than as a crash. A pack without Exposure gets plates and
 * no prints; a pack whose Exposure changed underneath us gets the same, plus one line in the log.</p>
 */
public final class SkyPhotograph {

    private static Boolean present;
    private static boolean warned;

    private SkyPhotograph() {
    }

    public static boolean available() {
        if (present == null) {
            present = ModList.get().isLoaded("exposure");
        }
        return present;
    }

    /** The colony's print of what it caught, or nothing at all. */
    public static ItemStack print(final SkyObject object, final String photographer) {
        if (!available() || object == null) {
            return ItemStack.EMPTY;
        }
        try {
            return me.lovkar.voyager.compat.ExposurePhotographs.print(object, photographer);
        } catch (final Throwable exposureChanged) {
            if (!warned) {
                warned = true;
                Voyager.LOGGER.warn("[Observatory] cannot print photographs with this build of Exposure ({});"
                        + " the darkroom will hand out plates only", exposureChanged.toString());
            }
            return ItemStack.EMPTY;
        }
    }
}
