package me.lovkar.voyager.photo;

import me.lovkar.voyager.Voyager;
import net.minecraft.network.chat.Component;
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
 *
 * <p>The shot in progress is handed around as an {@link Object} on purpose: naming its real type
 * here would make the JVM resolve Exposure's classes the moment this class loads.</p>
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

    /** Pixels on a side of a colony photograph, or 0 without Exposure. */
    public static int size() {
        try {
            return available() ? me.lovkar.voyager.compat.ExposureCamera.SIZE : 0;
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

    // ------------------------------------------------------------------ film

    public static boolean isFilm(final ItemStack stack) {
        try {
            return available() && me.lovkar.voyager.compat.ExposureCamera.isFilm(stack);
        } catch (final Throwable exposureChanged) {
            return false;
        }
    }

    public static boolean hasFilm(final ItemStack camera) {
        try {
            return available() && me.lovkar.voyager.compat.ExposureCamera.hasFilm(camera);
        } catch (final Throwable exposureChanged) {
            return false;
        }
    }

    public static boolean hasFreeFrame(final ItemStack camera) {
        try {
            return available() && me.lovkar.voyager.compat.ExposureCamera.hasFreeFrame(camera);
        } catch (final Throwable exposureChanged) {
            return false;
        }
    }

    /** Put a roll in; hands back whatever roll was there before (possibly a full one). */
    public static ItemStack loadFilm(final ItemStack camera, final ItemStack roll) {
        try {
            return available() ? me.lovkar.voyager.compat.ExposureCamera.loadFilm(camera, roll) : ItemStack.EMPTY;
        } catch (final Throwable exposureChanged) {
            complain(exposureChanged);
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack ejectFilm(final ItemStack camera) {
        try {
            return available() ? me.lovkar.voyager.compat.ExposureCamera.ejectFilm(camera) : ItemStack.EMPTY;
        } catch (final Throwable exposureChanged) {
            complain(exposureChanged);
            return ItemStack.EMPTY;
        }
    }

    /** Does the camera have a flash fitted and switched on? */
    public static boolean hasFlash(final ItemStack camera) {
        try {
            return available() && me.lovkar.voyager.compat.ExposureCamera.fittingsOf(camera).flash();
        } catch (final Throwable exposureChanged) {
            return false;
        }
    }

    // ------------------------------------------------------------------ the shot

    /** Open the shutter: where the camera is, what is fitted, who is in front of it. Null on failure. */
    public static Object open(final ServerLevel level, final Entity eye, final ItemStack camera) {
        if (!available()) {
            return null;
        }
        try {
            return me.lovkar.voyager.compat.ExposureCamera.open(level, eye, camera);
        } catch (final Throwable exposureChanged) {
            complain(exposureChanged);
            return null;
        }
    }

    /** Draw part of the picture. Returns false if this build of Exposure cannot be drawn into. */
    public static boolean renderBand(final ServerLevel level, final Entity eye, final Object shot,
                                     final byte[] pixels, final int from, final int rows) {
        if (!available() || pixels.length == 0 || shot == null) {
            return false;
        }
        try {
            me.lovkar.voyager.compat.ExposureCamera.renderBand(level, eye,
                    (me.lovkar.voyager.compat.ExposureCamera.Shot) shot, pixels, from, rows);
            return true;
        } catch (final Throwable exposureChanged) {
            complain(exposureChanged);
            return false;
        }
    }

    /** Store the picture and hand back the photograph, or an empty stack. */
    public static ItemStack develop(final ServerLevel level, final Entity eye, final Object shot,
                                    final byte[] pixels, final String id, final String photographer,
                                    final Component title, final ItemStack camera) {
        if (!available() || pixels.length == 0 || shot == null) {
            return ItemStack.EMPTY;
        }
        try {
            return me.lovkar.voyager.compat.ExposureCamera.develop(level, eye,
                    (me.lovkar.voyager.compat.ExposureCamera.Shot) shot, pixels, id, photographer, title, camera);
        } catch (final Throwable exposureChanged) {
            complain(exposureChanged);
            return ItemStack.EMPTY;
        }
    }

    /** Who ended up in the picture, nearest first; empty without Exposure. */
    public static java.util.List<Entity> inFrame(final Object shot) {
        try {
            return shot == null ? java.util.List.of()
                    : ((me.lovkar.voyager.compat.ExposureCamera.Shot) shot).inFrame();
        } catch (final Throwable exposureChanged) {
            return java.util.List.of();
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
