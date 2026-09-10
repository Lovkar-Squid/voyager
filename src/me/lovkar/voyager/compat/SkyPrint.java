package me.lovkar.voyager.compat;

import java.awt.image.BufferedImage;
import java.util.List;

import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.ExposureServer;
import io.github.mortuusars.exposure.data.ColorPalettes;
import io.github.mortuusars.exposure.util.ExtraData;
import io.github.mortuusars.exposure.world.camera.ExposureType;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.camera.frame.Photographer;
import io.github.mortuusars.exposure.world.level.storage.ExposureData;
import io.github.mortuusars.exposure.world.level.storage.ExposureIdentifier;
import me.lovkar.voyager.sky.SkyObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The darkroom's print of a plate: a night sky, drawn, with the object large in the middle of it.
 *
 * <p>The first prints pointed the photograph straight at Exposure: Space's catalogue picture of
 * the object - correct, and dull: the picture has a transparent ground, so it hung on the wall as
 * a coloured icon on white paper. Marko's words: "a bit boring because you can't see the night
 * sky". This draws the sky the way the lookout photograph draws it - in Minecraft's own map
 * colours, the palette Exposure stores its pictures in - and presses the catalogue picture into
 * it as big as a telescope would make it: a deep blue that goes black at the zenith, a scatter
 * of stars with a few bright enough to cross, a band of haze slanting across, a glow round the
 * object, and along the bottom the hills, a few trees and the Observatory's own dome with a
 * lamp lit in it, in silhouette. The stars are seeded by the object and the night, so two prints
 * of the same plate agree and two nights differ.</p>
 *
 * <p>{@link #compose} is pure - no world, no Exposure - so it can be looked at outside the game;
 * {@link #print} wraps it in an Exposure photograph the way the camera's own exposures are.</p>
 */
public final class SkyPrint {

    private static final int SIZE = ExposureCamera.SIZE;

    // Map colours, packed the way MapColor.getPackedId packs them: colour id * 4 + brightness
    // (0 low, 1 normal, 2 high, 3 lowest). Names as in net.minecraft.world.level.material.MapColor.
    private static final byte ZENITH = packed(29, 3);        // black, lowest      (13, 13, 13)
    private static final byte SKY = packed(25, 3);           // blue, lowest       (27, 40, 94)
    private static final byte GLOW = packed(25, 0);          // blue, low          (36, 53, 125)
    private static final byte HAZE = packed(39, 3);          // terracotta light blue, lowest (59, 57, 73)
    private static final byte HAZE_CORE = packed(5, 3);      // ice, lowest        (84, 84, 135)
    private static final byte DUSK = packed(47, 3);          // terracotta purple, lowest (40, 32, 48)
    private static final byte STAR_FAINT = packed(9, 3);     // clay, lowest       (86, 88, 97)
    private static final byte STAR = packed(22, 1);          // light gray, normal (132, 132, 132)
    private static final byte STAR_BRIGHT = packed(8, 1);    // snow, normal       (220, 220, 220)
    private static final byte STAR_CORE = packed(8, 2);      // snow, high         (255, 255, 255)
    private static final byte GROUND = packed(29, 3);        // black, lowest
    private static final byte LAMP = packed(15, 1);          // orange, normal     (216, 127, 51)

    /** The ground line, before the hills: rows from here down are land. */
    private static final int HORIZON = 84;

    private SkyPrint() {
    }

    static byte packed(final int colourId, final int brightness) {
        return (byte) (colourId * 4 + brightness);
    }

    /** How big the object is painted, from the catalogue's own size (10..56): 26 to 62 pixels. */
    public static int paintedSize(final int objectSize) {
        return Math.max(26, Math.min(62, 20 + objectSize * 3 / 4));
    }

    /**
     * Draw the print.
     *
     * @param icon       the catalogue picture (64x64, transparent ground), or null for an empty sky
     * @param objectSize the object's size in the catalogue (SkyObject.size)
     * @param seed       what the stars are seeded by
     */
    public static byte[] compose(final BufferedImage icon, final int objectSize, final long seed) {
        final byte[] px = new byte[SIZE * SIZE];
        final int size = paintedSize(objectSize);
        final int cx = SIZE / 2;
        final int cy = SIZE / 2 - 5;                            // a little above centre: the land is below
        final double glowRadius = size * 0.62;

        // the sky: black at the zenith, blue below, a dusk-purple band just over the hills
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                final int h = hash(x, y, seed);
                byte c = SKY;
                // black at the zenith, thinning into the blue over a dozen rows; the dusk band
                // over the hills the same way - a hard edge reads as a bar, a dither as a sky
                final int zenith = (24 - y) * 100 / 18;                 // 100 at the top, 0 by row 24
                final int dusk = (y - (HORIZON - 12)) * 100 / 10;       // 0 twelve rows up, 100 at the hills
                if (zenith > 0 && h % 100 < zenith) {
                    c = ZENITH;
                } else if (dusk > 0 && (h / 3) % 100 < dusk) {
                    c = DUSK;
                }
                // a band of haze slanting up to the right: the galaxy's own light
                final double band = (y - 62.0) + (x - 0.0) * 0.55;         // 0 on the band's centre line
                final double weight = Math.max(0.0, 1.0 - Math.abs(band) / 15.0);
                if (c == SKY && weight > 0.0 && (h / 7) % 100 < weight * 42) {
                    c = (Math.abs(band) < 5.0 && (h / 700) % 3 == 0) ? HAZE_CORE : HAZE;
                }
                // the glow round the object
                final double d = Math.hypot(x - cx, y - cy);
                if ((c == SKY || c == HAZE) && d < glowRadius && (h / 11) % 100 < (1.0 - d / glowRadius) * 95) {
                    c = GLOW;
                }
                px[y * SIZE + x] = c;
            }
        }
        // the stars: about one pixel in thirty, a few of them bright enough to cross
        for (int y = 0; y < HORIZON - 6; y++) {
            for (int x = 0; x < SIZE; x++) {
                final int roll = hash(x + 1000, y, seed) % 1000;
                if (roll >= 32) {
                    continue;
                }
                final double d = Math.hypot(x - cx, y - cy);
                if (d < size * 0.45) {
                    continue;                                   // the object's own sky stays clean
                }
                final int i = y * SIZE + x;
                if (roll < 3) {
                    px[i] = STAR_CORE;
                    for (int[] arm : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        final int ax = x + arm[0];
                        final int ay = y + arm[1];
                        if (ax >= 0 && ax < SIZE && ay >= 0 && ay < HORIZON - 6) {
                            px[ay * SIZE + ax] = STAR;
                        }
                    }
                } else if (roll < 10) {
                    px[i] = STAR_BRIGHT;
                } else if (roll < 20) {
                    px[i] = STAR;
                } else {
                    px[i] = STAR_FAINT;
                }
            }
        }
        // the land: hills, a few trees, and the Observatory's dome with its lamp lit
        for (int x = 0; x < SIZE; x++) {
            final int ground = HORIZON + (int) Math.round(2.5 * Math.sin(x / 9.0) + 1.5 * Math.sin(x / 4.3 + 1.0));
            for (int y = Math.max(0, ground); y < SIZE; y++) {
                px[y * SIZE + x] = GROUND;
            }
            if (x % 13 == 5 && x < 44) {                        // a spruce, one pixel wide, then two
                for (int y = ground - 5; y < ground; y++) {
                    if (y >= 0) {
                        px[y * SIZE + x] = GROUND;
                        if (y >= ground - 2) {
                            px[y * SIZE + Math.min(SIZE - 1, x + 1)] = GROUND;
                        }
                    }
                }
            }
        }
        dome(px, 71, HORIZON + 1);
        // the object itself, over everything but the land
        if (icon != null) {
            ExposureCamera.paste(px, icon, size, cx, cy, HORIZON - 2);
        }
        return px;
    }

    /** The Observatory in silhouette: a drum, a dome, and one lit window. */
    private static void dome(final byte[] px, final int cx, final int base) {
        final int top = base - 6;                               // the drum
        for (int y = top; y < base; y++) {
            for (int x = cx - 5; x <= cx + 5; x++) {
                set(px, x, y, GROUND);
            }
        }
        for (int dy = 0; dy <= 5; dy++) {                       // the dome: a half disc on the drum
            final int half = (int) Math.round(Math.sqrt(Math.max(0.0, 5.0 * 5.0 - dy * dy)));
            for (int x = cx - half; x <= cx + half; x++) {
                set(px, x, top - dy, GROUND);
            }
        }
        set(px, cx, top - 6, GROUND);                           // the finial
        set(px, cx, top - 7, STAR);
        set(px, cx - 3, base - 3, LAMP);                        // somebody is still up
    }

    private static void set(final byte[] px, final int x, final int y, final byte c) {
        if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
            px[y * SIZE + x] = c;
        }
    }

    /** A cheap, deterministic hash of a pixel and the night. */
    private static int hash(final int x, final int y, final long seed) {
        long h = x * 73856093L ^ y * 19349663L ^ seed * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (int) (h & 0x7fffffffL);
    }

    /**
     * The colony's print of a plate as a real Exposure photograph: the sky above, stored in
     * Exposure's own repository, so it hangs, files and projects like any other picture.
     */
    public static ItemStack print(final SkyObject object, final String photographer, final long seed) {
        if (object == null || object.catalogTexture() == null || object.catalogTexture().isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ResourceLocation texture = ResourceLocation.tryParse(object.catalogTexture());
        final Item photograph = BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("exposure", "photograph")).orElse(null);
        if (texture == null || photograph == null) {
            return ItemStack.EMPTY;
        }
        final BufferedImage icon = ExposureCamera.catalogue(texture);
        if (icon == null) {
            return ItemStack.EMPTY;
        }
        final byte[] pixels = compose(icon, object.size(), seed);
        final String id = "voyager_plate_" + Long.toString(System.nanoTime(), 36) + "_" + object.id().getPath();
        final long now = System.currentTimeMillis() / 1000L;
        final ExposureData data = new ExposureData(SIZE, SIZE, pixels, ColorPalettes.MAP_COLORS.location(),
                new ExposureData.Tag(ExposureType.COLOR, photographer, now, false, false));
        ExposureServer.exposureRepository().save(id, data);
        final ExtraData extra = new ExtraData();
        extra.put(Frame.TIMESTAMP, now);
        final Frame frame = new Frame(ExposureIdentifier.id(id), ExposureType.COLOR, Photographer.EMPTY,
                List.of(), extra);
        final ItemStack stack = new ItemStack(photograph);
        stack.set(Exposure.DataComponents.PHOTOGRAPH_FRAME, frame);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(object.nameKey()));
        return stack;
    }
}
