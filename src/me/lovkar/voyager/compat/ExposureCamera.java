package me.lovkar.voyager.compat;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A camera a colonist can actually use.
 *
 * <p><b>The problem.</b> Exposure photographs the world by rendering it from the camera's
 * viewpoint on the <i>client</i> and uploading the pixels. A colonist lives on the server, where
 * there is no renderer, so the received wisdom is that an NPC cannot take a photograph at all.</p>
 *
 * <p><b>What we do instead: render it ourselves.</b> An exposure is nothing but
 * {@code width, height, byte[] pixels} and a palette id, and Exposure ships a palette that is
 * Minecraft's own map colours. Minecraft has drawn maps on the server since 2011 by taking a block
 * and asking it for its {@link MapColor}. So this casts one ray per pixel out of the citizen's eye
 * through a pinhole camera, asks whatever it hits for its map colour, shades it by which face was
 * hit and how far away it was, and writes the packed palette index straight into the byte array.
 * The result is a real photograph of whatever the colonist was pointing at - taken on the server,
 * in Exposure's own palette, indistinguishable in kind from one a player took.</p>
 *
 * <p>It is deliberately drawn <b>a band at a time</b>. Ten thousand raycasts do not belong in one
 * tick, and a photograph that takes several seconds while the photographer stands still holding
 * the camera up is a better thing to watch than one that appears instantly.</p>
 */
public final class ExposureCamera {

    /** Pixels on a side. Exposure's own frames are square and small; this matches their look. */
    public static final int SIZE = 96;
    /** How far the camera can see. Beyond this is sky. */
    private static final double RANGE = 96.0;
    /** A 70 degree field of view, the same as the game's default. */
    private static final double TAN_HALF_FOV = Math.tan(Math.toRadians(35.0));

    private ExposureCamera() {
    }

    /**
     * Draw rows {@code [from, from + rows)} of the picture.
     *
     * @param pixels the whole image, written in place
     */
    public static void renderBand(final ServerLevel level, final Entity eye, final byte[] pixels,
                                  final int from, final int rows) {
        final Vec3 origin = eye.getEyePosition();
        final float yaw = eye.getYRot();
        final float pitch = eye.getXRot();

        // The camera's own axes, from where the citizen is looking.
        final Vec3 forward = facing(yaw, pitch);
        final Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        final Vec3 up = right.cross(forward).normalize();

        for (int py = from; py < Math.min(SIZE, from + rows); py++) {
            final double ndcY = 1.0 - ((py + 0.5) / SIZE) * 2.0;
            for (int px = 0; px < SIZE; px++) {
                final double ndcX = ((px + 0.5) / SIZE) * 2.0 - 1.0;
                final Vec3 dir = forward
                        .add(right.scale(ndcX * TAN_HALF_FOV))
                        .add(up.scale(ndcY * TAN_HALF_FOV))
                        .normalize();
                pixels[py * SIZE + px] = sample(level, eye, origin, dir, px, py);
            }
        }
    }

    /** One ray: what is out there, and what colour is it. */
    private static byte sample(final ServerLevel level, final Entity eye, final Vec3 origin,
                               final Vec3 dir, final int px, final int py) {
        final Vec3 end = origin.add(dir.scale(RANGE));
        final BlockHitResult hit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, eye));
        if (hit.getType() == HitResult.Type.MISS) {
            return sky(level, dir, px, py);
        }
        final BlockPos pos = hit.getBlockPos();
        final BlockState state = level.getBlockState(pos);
        final MapColor colour = state.getMapColor(level, pos);
        if (colour == MapColor.NONE) {
            return sky(level, dir, px, py);
        }
        return (byte) colour.getPackedId(shade(hit.getDirection(), origin.distanceTo(hit.getLocation())));
    }

    /**
     * How bright a face is: the map's own four steps, from the direction it faces and how far away
     * it is. Up is bright, the sides are middling, the underside is dark, and everything fades with
     * distance - which is all a photograph of a Minecraft world really needs to read as depth.
     */
    private static MapColor.Brightness shade(final Direction face, final double distance) {
        final int base = switch (face) {
            case UP -> 3;
            case NORTH, SOUTH -> 2;
            case EAST, WEST -> 1;
            case DOWN -> 0;
        };
        final int fade = distance > 64.0 ? 2 : distance > 32.0 ? 1 : 0;
        return switch (Math.max(0, base - fade)) {
            case 3 -> MapColor.Brightness.HIGH;
            case 2 -> MapColor.Brightness.NORMAL;
            case 1 -> MapColor.Brightness.LOW;
            default -> MapColor.Brightness.LOWEST;
        };
    }

    /**
     * The sky. Blue by day and near-black by night, with a scattering of stars after dark - the
     * pixel's own coordinates seed them, so a star stays where it was between bands.
     */
    private static byte sky(final ServerLevel level, final Vec3 dir, final int px, final int py) {
        final long t = level.getDayTime() % 24000L;
        final boolean night = t >= 13000L && t <= 23000L;
        if (!night) {
            final MapColor.Brightness high = dir.y > 0.3 ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL;
            return (byte) MapColor.COLOR_LIGHT_BLUE.getPackedId(high);
        }
        if (dir.y > 0.0 && ((px * 73856093) ^ (py * 19349663)) % 97 == 0) {
            return (byte) MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH);
        }
        return (byte) MapColor.COLOR_BLUE.getPackedId(MapColor.Brightness.LOWEST);
    }

    /** Minecraft's own yaw/pitch to a unit vector. */
    private static Vec3 facing(final float yaw, final float pitch) {
        final double y = Math.toRadians(yaw);
        final double p = Math.toRadians(pitch);
        final double cosPitch = Math.cos(p);
        return new Vec3(-Math.sin(y) * cosPitch, -Math.sin(p), Math.cos(y) * cosPitch).normalize();
    }

    /**
     * Store the finished picture and hand back the photograph.
     *
     * @param id the exposure's id in the world's own store - make it unique
     */
    public static ItemStack develop(final ServerLevel level, final byte[] pixels, final String id,
                                    final String photographer) {
        final ExposureData data = new ExposureData(SIZE, SIZE, pixels,
                ColorPalettes.MAP_COLORS.location(),
                new ExposureData.Tag(ExposureType.COLOR, photographer,
                        System.currentTimeMillis() / 1000L, false, false));
        ExposureServer.exposureRepository().save(id, data);

        final Item photograph = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("exposure", "photograph"));
        if (photograph == null) {
            return ItemStack.EMPTY;
        }
        final ExtraData extra = new ExtraData();
        extra.put(Frame.TIMESTAMP, System.currentTimeMillis() / 1000L);
        extra.put(Frame.DAY_TIME, (int) (level.getDayTime() % 24000L));
        final Frame frame = new Frame(ExposureIdentifier.id(id), ExposureType.COLOR,
                Photographer.EMPTY, List.of(), extra);
        final ItemStack stack = new ItemStack(photograph);
        stack.set(Exposure.DataComponents.PHOTOGRAPH_FRAME, frame);
        return stack;
    }

    /** A blank picture to draw into. */
    public static byte[] blank() {
        return new byte[SIZE * SIZE];
    }
}
