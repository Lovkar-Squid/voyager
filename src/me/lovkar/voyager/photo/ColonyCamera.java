package me.lovkar.voyager.photo;

import me.lovkar.voyager.Voyager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * The safe side of the colony's camera.
 *
 * <p>Exposure is an optional dependency, so every line that touches its classes lives in
 * {@code compat.ExposureCamera} and is only ever reached through here - after a check that the mod
 * is loaded, and inside a catch that turns any surprise into "no photograph today" instead of a
 * crash. A pack without Exposure gets a Photographer who crafts and develops nothing and says so
 * once in the log; a pack whose Exposure moved underneath us gets the same.</p>
 */
public final class ColonyCamera {

    private static Boolean present;
    private static boolean warned;

    private ColonyCamera() {
    }

    public static boolean available() {
        if (present == null) {
            present = ModList.get().isLoaded("exposure");
        }
        return present;
    }

    /** Pixels on a side of a colony photograph. */
    public static int size() {
        return available() ? tryPixels() : 0;
    }

    private static int tryPixels() {
        try {
            return me.lovkar.voyager.compat.ExposureCamera.SIZE;
        } catch (final Throwable exposureChanged) {
            return 0;
        }
    }

    public static byte[] blank() {
        try {
            return available() ? me.lovkar.voyager.compat.ExposureCamera.blank() : new byte[0];
        } catch (final Throwable exposureChanged) {
            return new byte[0];
        }
    }

    /** Draw part of the picture. Returns false if this build of Exposure cannot be drawn into. */
    public static boolean renderBand(final ServerLevel level, final Entity eye, final byte[] pixels,
                                     final int from, final int rows) {
        if (!available() || pixels.length == 0) {
            return false;
        }
        try {
            me.lovkar.voyager.compat.ExposureCamera.renderBand(level, eye, pixels, from, rows);
            return true;
        } catch (final Throwable exposureChanged) {
            complain(exposureChanged);
            return false;
        }
    }

    /** Store the picture and hand back the photograph, or an empty stack. */
    public static ItemStack develop(final ServerLevel level, final byte[] pixels, final String id,
                                    final String photographer) {
        if (!available() || pixels.length == 0) {
            return ItemStack.EMPTY;
        }
        try {
            return me.lovkar.voyager.compat.ExposureCamera.develop(level, pixels, id, photographer);
        } catch (final Throwable exposureChanged) {
            complain(exposureChanged);
            return ItemStack.EMPTY;
        }
    }

    private static void complain(final Throwable why) {
        if (!warned) {
            warned = true;
            Voyager.LOGGER.warn("[Photo Booth] cannot take photographs with this build of Exposure ({});"
                    + " the photographer will craft and develop, but not shoot", why.toString());
        }
    }
}
