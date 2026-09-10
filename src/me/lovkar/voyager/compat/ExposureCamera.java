package me.lovkar.voyager.compat;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;

import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.ExposureServer;
import io.github.mortuusars.exposure.data.ColorPalettes;
import io.github.mortuusars.exposure.util.ExtraData;
import io.github.mortuusars.exposure.world.camera.ExposureType;
import io.github.mortuusars.exposure.world.camera.component.FlashMode;
import io.github.mortuusars.exposure.world.camera.component.ShutterSpeed;
import io.github.mortuusars.exposure.world.camera.frame.EntityInFrame;
import io.github.mortuusars.exposure.world.item.FilmRollItem;
import io.github.mortuusars.exposure.world.item.component.StoredItemStack;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.camera.frame.Photographer;
import io.github.mortuusars.exposure.world.level.storage.ExposureData;
import io.github.mortuusars.exposure.world.level.storage.ExposureIdentifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
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
 * hit, how far away it was and how much light falls on it, and writes the packed palette index
 * straight into the byte array. The result is a real photograph of whatever the colonist was
 * pointing at - taken on the server, in Exposure's own palette, indistinguishable in kind from one
 * a player took.</p>
 *
 * <p><b>People are in the picture.</b> A block raycast alone would leave the sitter out of their
 * own portrait, so every ray is also tested against the entities standing in front of the camera
 * when the shutter opened - frozen where they stood, so a subject that fidgets between bands does
 * not tear the image - and a nearer entity wins over the wall behind it. They are painted in two
 * tones, head and body, by what kind of creature they are; a silhouette in the right colours reads
 * as a person at the resolution a map colour allows.</p>
 *
 * <p>It is deliberately drawn <b>a band at a time</b>. Ten thousand raycasts do not belong in one
 * tick, and a photograph that takes several seconds while the photographer stands still holding
 * the camera up is a better thing to watch than one that appears instantly.</p>
 */
public final class ExposureCamera {

    /** Pixels on a side. Exposure's own frames are square and small; this matches their look. */
    public static final int SIZE = 96;
    /** How far the camera can see. Beyond this is sky. */
    private static final double RANGE = 80.0;
    /** A 70 degree field of view, the same as the game's default. */
    private static final double TAN_HALF_FOV = Math.tan(Math.toRadians(35.0));

    private ExposureCamera() {
    }

    /** A creature, frozen where it stood when the shutter opened. */
    private record Sitter(Entity entity, AABB box, MapColor head, MapColor body) {
    }

    /**
     * What is fitted to the camera, read off the camera item itself.
     *
     * <p>Every attachment Exposure and Exposure: Expanded sell changes the picture the colonist
     * takes, because the colonist's picture is ours to draw:</p>
     * <ul>
     *   <li><b>film</b> - black-and-white film prints in greys; high-sensitivity film sees in the
     *       dark; no film, or a full roll, and there is no picture at all;</li>
     *   <li><b>lens</b> - a telescopic lens narrows the field of view, a panoramic one widens it;</li>
     *   <li><b>filter</b> - flip, desaturate, blur, sobel, outline and pencil are drawn for real
     *       on the pixels; a stained-glass pane or a colour filter tints the whole frame;</li>
     *   <li><b>flash</b> - lets a night shot come out lit, and goes off visibly;</li>
     *   <li><b>shutter speed</b> - a slow shutter brightens the exposure, a fast one darkens it.</li>
     * </ul>
     */
    public record Fittings(boolean blackAndWhite, boolean sensitive, double fovScale,
                           Set<String> filters, boolean flash, int stops) {

        public static final Fittings PLAIN = new Fittings(false, false, 1.0, Set.of(), false, 0);

        /** The same camera pointed through a different field of view - the lens is overruled. */
        public Fittings withFov(final double scale) {
            return new Fittings(blackAndWhite, sensitive, scale, filters, flash, stops);
        }
    }

    /** Read the fittings off a camera item; a plain camera if it is not one. */
    public static Fittings fittingsOf(final ItemStack camera) {
        if (camera.isEmpty()) {
            return Fittings.PLAIN;
        }
        final ItemStack film = attachment(camera, Exposure.DataComponents.FILM);
        final String filmId = idOf(film);
        final boolean bw = filmId.contains("black_and_white") || filmId.contains("gameboy");
        final boolean sensitive = filmId.contains("high_sensitivity") || filmId.contains("hisen");

        final String lens = idOf(attachment(camera, Exposure.DataComponents.LENS));
        double fov = 1.0;
        if (lens.contains("telescopic")) {
            fov = lens.contains("sculk") ? 0.25 : lens.contains("excellent") ? 0.33
                    : lens.contains("good") ? 0.4 : 0.5;
        } else if (lens.contains("panoramic")) {
            fov = 1.6;
        }
        final Float zoom = camera.get(Exposure.DataComponents.ZOOM);
        if (zoom != null && zoom > 0.0f && zoom <= 1.0f && lens.isEmpty()) {
            fov = 1.0 - 0.6 * zoom;                       // the camera's own zoom dial
        }

        final Set<String> filters = new LinkedHashSet<>();
        final String filter = idOf(attachment(camera, Exposure.DataComponents.FILTER));
        if (!filter.isEmpty()) {
            filters.add(filter.substring(filter.indexOf(':') + 1));
        }

        final ItemStack flash = attachment(camera, Exposure.DataComponents.FLASH);
        final FlashMode mode = camera.get(Exposure.DataComponents.FLASH_MODE);
        final boolean flashOn = !flash.isEmpty() && mode != FlashMode.OFF;

        int stops = 0;
        final ShutterSpeed speed = camera.get(Exposure.DataComponents.SHUTTER_SPEED);
        if (speed != null) {
            stops = Math.round(speed.getStopsDifference(ShutterSpeed.DEFAULT));
        }
        return new Fittings(bw, sensitive, fov, filters, flashOn, stops);
    }

    private static ItemStack attachment(final ItemStack camera,
                                        final net.minecraft.core.component.DataComponentType<StoredItemStack> slot) {
        final StoredItemStack stored = camera.get(slot);
        return stored == null ? ItemStack.EMPTY : stored.getForReading();
    }

    private static String idOf(final ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ------------------------------------------------------------------ film

    /** Is there film in the camera with room for one more frame? */
    public static boolean hasFreeFrame(final ItemStack camera) {
        final ItemStack film = attachment(camera, Exposure.DataComponents.FILM);
        return film.getItem() instanceof FilmRollItem roll && roll.canAddFrame(film);
    }

    /** Is there film in the camera at all? */
    public static boolean hasFilm(final ItemStack camera) {
        return !attachment(camera, Exposure.DataComponents.FILM).isEmpty();
    }

    /** Put a roll into the camera. Returns what was in there before, if anything. */
    public static ItemStack loadFilm(final ItemStack camera, final ItemStack roll) {
        final ItemStack previous = attachment(camera, Exposure.DataComponents.FILM).copy();
        camera.set(Exposure.DataComponents.FILM, new StoredItemStack(roll.copy()));
        return previous;
    }

    /** Take the roll out of the camera - a full one, ready for the darkroom. */
    public static ItemStack ejectFilm(final ItemStack camera) {
        final ItemStack film = attachment(camera, Exposure.DataComponents.FILM).copy();
        camera.remove(Exposure.DataComponents.FILM);
        return film;
    }

    /** Is this item a roll of film a camera could take? */
    public static boolean isFilm(final ItemStack stack) {
        return stack.getItem() instanceof FilmRollItem;
    }

    /** A roll of film with at least one frame left on it - the only kind worth loading. */
    public static boolean isFilmWithRoom(final ItemStack stack) {
        return stack.getItem() instanceof FilmRollItem roll && roll.canAddFrame(stack);
    }

    /**
     * Expose the frame onto the roll in the camera: the negative stays on the film, the way it
     * does for a player, so a full roll can go to the darkroom later.
     */
    private static void exposeOntoFilm(final ItemStack camera, final Frame frame) {
        final StoredItemStack stored = camera.get(Exposure.DataComponents.FILM);
        if (stored == null) {
            return;
        }
        final ItemStack film = stored.getCopy();
        if (film.getItem() instanceof FilmRollItem roll && roll.canAddFrame(film)) {
            roll.addFrame(film, frame);
            camera.set(Exposure.DataComponents.FILM, new StoredItemStack(film));
        }
    }

    /**
     * One exposure: where the camera is, which way it looks, and who was standing in front of it.
     * Made once when the shutter opens and handed to every band, so the picture is of one moment.
     */
    public static final class Shot {
        final Vec3 origin;
        final Vec3 forward;
        final Vec3 right;
        final Vec3 up;
        final List<Sitter> sitters = new ArrayList<>();
        final Set<Entity> seen = new LinkedHashSet<>();
        final long dayTime;
        final boolean raining;
        final Fittings fittings;
        final double tanHalfFov;
        /** Where the moon is tonight, and how much of it is lit (0 full .. 4 new .. 7). */
        final Vec3 moonDir;
        final int moonPhase;

        Shot(final ServerLevel level, final Entity eye, final Fittings fittings) {
            this.fittings = fittings;
            this.tanHalfFov = TAN_HALF_FOV * fittings.fovScale();
            origin = eye.getEyePosition();
            forward = facing(eye.getYRot(), eye.getXRot());
            right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            up = right.cross(forward).normalize();
            dayTime = level.getDayTime() % 24000L;
            raining = level.isRaining();
            // The sun rises in the east (+x) at 0, stands overhead at 6000 and sets in the west at
            // 12000; the moon is always opposite it.
            final double theta = (dayTime / 24000.0) * Math.PI * 2.0;
            moonDir = new Vec3(-Math.cos(theta), -Math.sin(theta), 0.0).normalize();
            moonPhase = level.getMoonPhase();
            final AABB reach = eye.getBoundingBox().inflate(RANGE);
            for (final Entity other : level.getEntities(eye, reach, e -> e.isAlive() && e != eye)) {
                final Vec3 to = other.getBoundingBox().getCenter().subtract(origin);
                if (to.dot(forward) <= 0.0) {
                    continue;                                   // behind the camera
                }
                if (isCameraStand(other)) {
                    continue;                                   // the tripod is not in the picture
                }
                final MapColor[] tones = tones(other);
                sitters.add(new Sitter(other, other.getBoundingBox(), tones[0], tones[1]));
            }
        }

        /** Everybody who actually ended up in the picture, nearest first. */
        public List<Entity> inFrame() {
            return new ArrayList<>(seen);
        }
    }

    /** Exposure's camera stand - the studio's own tripod, which stands between sitter and lens. */
    private static boolean isCameraStand(final Entity entity) {
        final ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return type != null && type.getPath().contains("camera_stand");
    }

    public static Shot open(final ServerLevel level, final Entity eye, final ItemStack camera) {
        return new Shot(level, eye, fittingsOf(camera));
    }

    /** As above, with the field of view set by hand when {@code fovScale} is positive. */
    public static Shot open(final ServerLevel level, final Entity eye, final ItemStack camera,
                            final double fovScale) {
        final Fittings fittings = fittingsOf(camera);
        return new Shot(level, eye, fovScale > 0.0 ? fittings.withFov(fovScale) : fittings);
    }

    /**
     * Draw rows {@code [from, from + rows)} of the picture.
     *
     * @param pixels the whole image, written in place
     */
    public static void renderBand(final ServerLevel level, final Entity eye, final Shot shot,
                                  final byte[] pixels, final int from, final int rows) {
        for (int py = from; py < Math.min(SIZE, from + rows); py++) {
            final double ndcY = 1.0 - ((py + 0.5) / SIZE) * 2.0;
            for (int px = 0; px < SIZE; px++) {
                final double ndcX = ((px + 0.5) / SIZE) * 2.0 - 1.0;
                final Vec3 dir = shot.forward
                        .add(shot.right.scale(ndcX * shot.tanHalfFov))
                        .add(shot.up.scale(ndcY * shot.tanHalfFov))
                        .normalize();
                pixels[py * SIZE + px] = sample(level, eye, shot, dir, px, py);
            }
        }
    }

    /** One ray: what is out there, and what colour is it. */
    private static byte sample(final ServerLevel level, final Entity eye, final Shot shot,
                               final Vec3 dir, final int px, final int py) {
        final Vec3 end = shot.origin.add(dir.scale(RANGE));

        // The nearest block along the ray, if any.
        final BlockHitResult hit = level.clip(new ClipContext(shot.origin, end,
                ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, eye));
        double blockDistance = Double.MAX_VALUE;
        if (hit.getType() != HitResult.Type.MISS) {
            blockDistance = shot.origin.distanceTo(hit.getLocation());
        }

        // The nearest creature along the ray, if any - and whether it is in front of the block.
        Sitter nearest = null;
        double sitterDistance = Double.MAX_VALUE;
        Vec3 where = null;
        for (final Sitter sitter : shot.sitters) {
            final Optional<Vec3> point = sitter.box().clip(shot.origin, end);
            if (point.isPresent()) {
                final double d = shot.origin.distanceTo(point.get());
                if (d < sitterDistance) {
                    sitterDistance = d;
                    nearest = sitter;
                    where = point.get();
                }
            }
        }
        if (nearest != null && sitterDistance < blockDistance) {
            shot.seen.add(nearest.entity());
            // Head or body: the top quarter of the box is the head, which is where a face goes.
            final double height = nearest.box().getYsize();
            final boolean head = height > 0.0 && (where.y - nearest.box().minY) / height > 0.72;
            final MapColor tone = head ? nearest.head() : nearest.body();
            final MapColor.Brightness light = dim(shot, brightnessOf(2 + shot.fittings.stops(), sitterDistance),
                    level.getMaxLocalRawBrightness(BlockPos.containing(where)), sitterDistance);
            return (byte) tone.getPackedId(light);
        }

        if (hit.getType() == HitResult.Type.MISS) {
            return sky(shot, dir, px, py);
        }
        final BlockPos pos = hit.getBlockPos();
        final BlockState state = level.getBlockState(pos);
        final MapColor colour = state.getMapColor(level, pos);
        if (colour == MapColor.NONE) {
            return sky(shot, dir, px, py);
        }
        // Light falls on the face that was hit, so that is the square whose light we read.
        final int light = level.getMaxLocalRawBrightness(pos.relative(hit.getDirection()));
        return (byte) colour.getPackedId(dim(shot,
                brightnessOf(faceStep(hit.getDirection()) + shot.fittings.stops(), blockDistance), light, blockDistance));
    }

    /** Up is bright, the sides are middling, the underside is dark. */
    private static int faceStep(final Direction face) {
        return switch (face) {
            case UP -> 3;
            case NORTH, SOUTH -> 2;
            case EAST, WEST -> 1;
            case DOWN -> 0;
        };
    }

    /** Everything fades a step with distance - which is all a Minecraft world needs to read as depth. */
    private static MapColor.Brightness brightnessOf(final int step, final double distance) {
        final int fade = distance > 56.0 ? 2 : distance > 28.0 ? 1 : 0;
        return level(step - fade);
    }

    /**
     * Light matters: a dark room photographs dark, and a night shot is a night shot - unless the
     * flash went off, which lights everything within its reach, or the film is fast enough to see
     * in the dark on its own.
     */
    private static MapColor.Brightness dim(final Shot shot, final MapColor.Brightness lit, final int light,
                                           final double distance) {
        final int threshold = shot.fittings.sensitive() ? 4 : 9;
        if (light >= threshold || (shot.fittings.flash() && distance <= FLASH_REACH)) {
            return lit;
        }
        final int step = lit == MapColor.Brightness.HIGH ? 3 : lit == MapColor.Brightness.NORMAL ? 2
                : lit == MapColor.Brightness.LOW ? 1 : 0;
        return level(step - (light >= threshold / 2 ? 1 : 2));
    }

    /** How far the flash reaches, in blocks. */
    public static final double FLASH_REACH = 14.0;

    private static MapColor.Brightness level(final int step) {
        return switch (Math.max(0, Math.min(3, step))) {
            case 3 -> MapColor.Brightness.HIGH;
            case 2 -> MapColor.Brightness.NORMAL;
            case 1 -> MapColor.Brightness.LOW;
            default -> MapColor.Brightness.LOWEST;
        };
    }

    /**
     * The sky, by the hour: blue by day, orange along the horizon at dawn and dusk, near-black
     * with a scattering of stars at night, and grey when it rains. The pixel's own coordinates
     * seed the stars, so a star stays where it was between bands.
     */
    private static byte sky(final Shot shot, final Vec3 dir, final int px, final int py) {
        if (shot.raining) {
            return (byte) MapColor.COLOR_LIGHT_GRAY.getPackedId(
                    dir.y > 0.3 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW);
        }
        final long t = shot.dayTime;
        final boolean night = t >= 13000L && t <= 23000L;
        final boolean golden = (t >= 11000L && t < 13000L) || t > 23000L;
        if (night) {
            if (shot.moonDir.y > 0.05 && moonlit(shot, dir)) {
                return (byte) MapColor.QUARTZ.getPackedId(MapColor.Brightness.HIGH);
            }
            if (dir.y > 0.0 && ((px * 73856093) ^ (py * 19349663)) % 97 == 0) {
                return (byte) MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH);
            }
            return (byte) MapColor.COLOR_BLUE.getPackedId(MapColor.Brightness.LOWEST);
        }
        if (golden) {
            if (dir.y < 0.25) {
                return (byte) MapColor.COLOR_ORANGE.getPackedId(MapColor.Brightness.NORMAL);
            }
            return (byte) MapColor.COLOR_PURPLE.getPackedId(MapColor.Brightness.LOW);
        }
        return (byte) MapColor.COLOR_LIGHT_BLUE.getPackedId(
                dir.y > 0.3 ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL);
    }

    /** A four-degree moon, with its phase cut out of it the way a moon's phase looks: a dark disc offset. */
    private static boolean moonlit(final Shot shot, final Vec3 dir) {
        final double cosR = Math.cos(Math.toRadians(4.0));
        if (dir.dot(shot.moonDir) < cosR) {
            return false;
        }
        if (shot.moonPhase == 0) {
            return true;                                // full
        }
        if (shot.moonPhase == 4) {
            return false;                               // new
        }
        // The shadow disc slides across from one side to the other over the month.
        final double[] offsets = {0.0, 0.55, 1.0, 1.5, 9.0, -1.5, -1.0, -0.55};
        final Vec3 side = shot.moonDir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        final double r = Math.toRadians(4.0);
        final Vec3 shadowCentre = shot.moonDir.add(side.scale(offsets[shot.moonPhase] * r)).normalize();
        return dir.dot(shadowCentre) < Math.cos(r * 1.05);
    }

    // ------------------------------------------------------------------ the sky's own pictures

    private static final Map<ResourceLocation, Optional<BufferedImage>> TEXTURES = new HashMap<>();

    /**
     * Paint a cosmic object into the frame: its catalogue picture, scaled to how big it would look
     * through this lens, centred where the astronomer pointed the camera.
     *
     * <p>Exposure: Space ships a 64x64 picture of every object it puts in the sky; on the server we
     * read it straight out of the mod's jar and press it into map colours. Through a telescopic
     * lens the object fills much of the frame; through a plain one it is a smudge of the right
     * colours a few pixels wide - which is what a plain camera pointed at a nebula would get.</p>
     *
     * @param sizePx the width the object should be painted at, in pixels of the frame
     * @return true if something was painted
     */
    public static boolean paintObject(final byte[] pixels, final ResourceLocation texture, final int sizePx) {
        if (pixels.length != SIZE * SIZE || texture == null || sizePx <= 0) {
            return false;
        }
        final BufferedImage image = catalogue(texture);
        return image != null && paste(pixels, image, sizePx, SIZE / 2, SIZE / 2, SIZE);
    }

    /** A catalogue picture out of its mod's jar, cached; null if nobody ships it. */
    static BufferedImage catalogue(final ResourceLocation texture) {
        return TEXTURES.computeIfAbsent(texture, ExposureCamera::load).orElse(null);
    }

    /**
     * Press a picture into the frame, {@code sizePx} wide, centred on ({@code cx}, {@code cy}),
     * never below row {@code yMax}. Its transparent and black pixels are left out: they are the
     * picture's own sky, and the frame has a better one.
     */
    static boolean paste(final byte[] pixels, final BufferedImage image, final int sizePx,
                         final int cx, final int cy, final int yMax) {
        final int size = Math.max(1, Math.min(SIZE, sizePx));
        final int x0 = cx - size / 2;
        final int y0 = cy - size / 2;
        boolean painted = false;
        for (int y = 0; y < size; y++) {
            final int py = y0 + y;
            if (py < 0 || py >= Math.min(SIZE, yMax)) {
                continue;
            }
            for (int x = 0; x < size; x++) {
                final int pxx = x0 + x;
                if (pxx < 0 || pxx >= SIZE) {
                    continue;
                }
                final int sx = Math.min(image.getWidth() - 1, x * image.getWidth() / size);
                final int sy = Math.min(image.getHeight() - 1, y * image.getHeight() / size);
                final int argb = image.getRGB(sx, sy);
                final int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 96) {
                    continue;
                }
                final int r = (argb >> 16) & 0xFF;
                final int g = (argb >> 8) & 0xFF;
                final int b = argb & 0xFF;
                if (r + g + b < 60) {
                    continue;                           // the picture's own black background is sky
                }
                pixels[py * SIZE + pxx] = Filters.nearest((r << 16) | (g << 8) | b);
                painted = true;
            }
        }
        return painted;
    }

    /** Read a texture out of whichever mod jar owns its namespace; empty if nobody does. */
    private static Optional<BufferedImage> load(final ResourceLocation texture) {
        final String path = "assets/" + texture.getNamespace() + "/" + texture.getPath();
        try {
            final var mod = net.neoforged.fml.ModList.get().getModFileById(texture.getNamespace());
            if (mod != null) {
                final Path file = mod.getFile().findResource(path.split("/"));
                if (file != null && Files.exists(file)) {
                    try (InputStream in = Files.newInputStream(file)) {
                        return Optional.ofNullable(ImageIO.read(in));
                    }
                }
            }
            try (InputStream in = ExposureCamera.class.getClassLoader().getResourceAsStream(path)) {
                return in == null ? Optional.empty() : Optional.ofNullable(ImageIO.read(in));
            }
        } catch (final Throwable t) {
            me.lovkar.voyager.Voyager.LOGGER.info("[Observatory] could not read {} ({})", texture, t.toString());
            return Optional.empty();
        }
    }

    /**
     * What a creature is painted in: head, then body. Coarse on purpose - at map-colour resolution
     * a pink blob with a pink head is a pig and a tan head over blue is a colonist, and that is
     * exactly what the picture needs to say.
     */
    private static MapColor[] tones(final Entity entity) {
        final EntityType<?> type = entity.getType();
        if (entity instanceof Player || entity instanceof com.minecolonies.api.entity.citizen.AbstractEntityCitizen
                || type == EntityType.VILLAGER) {
            return new MapColor[] {MapColor.TERRACOTTA_ORANGE, MapColor.COLOR_BLUE};
        }
        if (type == EntityType.COW || type == EntityType.HORSE || type == EntityType.DONKEY
                || type == EntityType.MULE) {
            return new MapColor[] {MapColor.COLOR_BROWN, MapColor.COLOR_BROWN};
        }
        if (type == EntityType.SHEEP || type == EntityType.CHICKEN || type == EntityType.POLAR_BEAR
                || type == EntityType.GOAT) {
            return new MapColor[] {MapColor.SNOW, MapColor.WOOL};
        }
        if (type == EntityType.PIG) {
            return new MapColor[] {MapColor.COLOR_PINK, MapColor.COLOR_PINK};
        }
        if (type == EntityType.WOLF) {
            return new MapColor[] {MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_LIGHT_GRAY};
        }
        if (type == EntityType.CAT || type == EntityType.FOX || type == EntityType.OCELOT) {
            return new MapColor[] {MapColor.COLOR_ORANGE, MapColor.COLOR_ORANGE};
        }
        if (type == EntityType.ZOMBIE || type == EntityType.CREEPER || type == EntityType.ZOMBIE_VILLAGER) {
            return new MapColor[] {MapColor.COLOR_GREEN, MapColor.COLOR_GREEN};
        }
        if (type == EntityType.SKELETON || type == EntityType.STRAY) {
            return new MapColor[] {MapColor.SNOW, MapColor.COLOR_LIGHT_GRAY};
        }
        if (type == EntityType.ENDERMAN) {
            return new MapColor[] {MapColor.COLOR_BLACK, MapColor.COLOR_BLACK};
        }
        if (entity instanceof LivingEntity) {
            return new MapColor[] {MapColor.COLOR_GRAY, MapColor.COLOR_GRAY};
        }
        return new MapColor[] {MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_LIGHT_GRAY};
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
     * @param id    the exposure's id in the world's own store - make it unique
     * @param title what the photograph is called in the inventory; null for Exposure's default
     */
    public static ItemStack develop(final ServerLevel level, final Entity eye, final Shot shot,
                                    final byte[] pixels, final String id, final String photographer,
                                    final Component title, final ItemStack camera) {
        byte[] picture = pixels;
        for (final String filter : shot.fittings.filters()) {
            picture = Filters.apply(filter, picture);
        }
        final ExposureType type = shot.fittings.blackAndWhite() ? ExposureType.BLACK_AND_WHITE : ExposureType.COLOR;
        if (shot.fittings.blackAndWhite()) {
            picture = Filters.greys(picture);
        }
        final ExposureData data = new ExposureData(SIZE, SIZE, picture,
                ColorPalettes.MAP_COLORS.location(),
                new ExposureData.Tag(type, photographer, System.currentTimeMillis() / 1000L, false, false));
        ExposureServer.exposureRepository().save(id, data);

        final Item photograph = BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("exposure", "photograph")).orElse(null);
        if (photograph == null) {
            return ItemStack.EMPTY;
        }
        final ExtraData extra = new ExtraData();
        extra.put(Frame.TIMESTAMP, System.currentTimeMillis() / 1000L);
        extra.put(Frame.DAY_TIME, (int) (level.getDayTime() % 24000L));
        extra.put(Frame.POSITION, eye.position());
        extra.put(Frame.DIMENSION, level.dimension().location());
        // Whoever ended up in the picture, so Exposure's own tooltip can say so.
        final List<EntityInFrame> people = new ArrayList<>();
        for (final Entity seen : shot.inFrame()) {
            people.add(EntityInFrame.of(eye, seen));
            if (people.size() >= 4) {
                break;
            }
        }
        if (shot.fittings.flash()) {
            extra.put(Frame.FLASH, true);
        }
        final Frame frame = new Frame(ExposureIdentifier.id(id), type, Photographer.EMPTY, people, extra);
        // The negative stays on the roll, the print goes on the shelf - the way a real camera does it.
        exposeOntoFilm(camera, frame);
        final ItemStack stack = new ItemStack(photograph);
        stack.set(Exposure.DataComponents.PHOTOGRAPH_FRAME, frame);
        if (title != null) {
            stack.set(DataComponents.CUSTOM_NAME, title);
        }
        return stack;
    }

    // ------------------------------------------------------------------ the darkroom's tricks

    /**
     * Filters, drawn for real on the pixels.
     *
     * <p>Exposure: Expanded's filters are client-side shaders and so are not available to us - but
     * the ones that are simple image operations can be done on the byte array before it is saved,
     * and then the colonist's picture honestly shows the filter that was on the colonist's camera.
     * Anything we do not know how to draw is left alone rather than faked.</p>
     */
    static final class Filters {

        /** Every packed map colour as RGB, so the maths can be done once and the result snapped back. */
        private static int[] palette;

        private Filters() {
        }

        private static int[] palette() {
            if (palette == null) {
                final int[] table = new int[256];
                for (int i = 0; i < 256; i++) {
                    table[i] = rgbOf((byte) i);
                }
                palette = table;
            }
            return palette;
        }

        static int rgbOf(final byte packed) {
            final int id = (packed & 0xFF) >> 2;
            final MapColor colour = MapColor.byId(id);
            if (colour == null || colour == MapColor.NONE) {
                return 0;
            }
            final int modifier = MapColor.Brightness.byId(packed & 3).modifier;
            final int r = ((colour.col >> 16) & 0xFF) * modifier / 255;
            final int g = ((colour.col >> 8) & 0xFF) * modifier / 255;
            final int b = (colour.col & 0xFF) * modifier / 255;
            return (r << 16) | (g << 8) | b;
        }

        /** The packed map colour closest to an RGB value. */
        static byte nearest(final int rgb) {
            final int[] table = palette();
            final int r = (rgb >> 16) & 0xFF;
            final int g = (rgb >> 8) & 0xFF;
            final int b = rgb & 0xFF;
            int best = 0;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 4; i < 256; i++) {                   // skip MapColor.NONE's four slots
                final int c = table[i];
                if (c == 0 && i >= 4) {
                    continue;
                }
                final int dr = r - ((c >> 16) & 0xFF);
                final int dg = g - ((c >> 8) & 0xFF);
                final int db = b - (c & 0xFF);
                final long d = (long) dr * dr * 3 + (long) dg * dg * 4 + (long) db * db * 2;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = i;
                }
            }
            return (byte) best;
        }

        static int luminance(final int rgb) {
            return (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        }

        /** Black-and-white film: every pixel to the nearest grey. */
        static byte[] greys(final byte[] in) {
            final byte[] out = new byte[in.length];
            for (int i = 0; i < in.length; i++) {
                final int l = luminance(rgbOf(in[i]));
                out[i] = nearest((l << 16) | (l << 8) | l);
            }
            return out;
        }

        static byte[] apply(final String filter, final byte[] in) {
            if (filter.contains("flip")) {
                return flip(in);
            }
            if (filter.contains("desaturate")) {
                return desaturate(in, 0.5);
            }
            if (filter.contains("blur") || filter.contains("antialias") || filter.contains("blobs")
                    || filter.contains("art")) {
                return blur(in);
            }
            if (filter.contains("sobel") || filter.contains("outline")) {
                return edges(in, false);
            }
            if (filter.contains("pencil")) {
                return edges(in, true);
            }
            if (filter.contains("red") || filter.contains("green") || filter.contains("blue")
                    || filter.contains("yellow") || filter.contains("cyan") || filter.contains("magenta")
                    || filter.contains("orange") || filter.contains("purple") || filter.contains("pink")) {
                return tint(in, filter);
            }
            if (filter.contains("color_convolve")) {
                return desaturate(in, -0.4);                  // the opposite: push the colours
            }
            return in;                                        // a filter we cannot draw: left honest
        }

        static byte[] flip(final byte[] in) {
            final byte[] out = new byte[in.length];
            for (int y = 0; y < SIZE; y++) {
                System.arraycopy(in, y * SIZE, out, (SIZE - 1 - y) * SIZE, SIZE);
            }
            return out;
        }

        static byte[] desaturate(final byte[] in, final double amount) {
            final byte[] out = new byte[in.length];
            for (int i = 0; i < in.length; i++) {
                final int rgb = rgbOf(in[i]);
                final int l = luminance(rgb);
                final int r = clamp((int) (((rgb >> 16) & 0xFF) * (1 - amount) + l * amount));
                final int g = clamp((int) (((rgb >> 8) & 0xFF) * (1 - amount) + l * amount));
                final int b = clamp((int) ((rgb & 0xFF) * (1 - amount) + l * amount));
                out[i] = nearest((r << 16) | (g << 8) | b);
            }
            return out;
        }

        static byte[] blur(final byte[] in) {
            final byte[] out = new byte[in.length];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int r = 0, g = 0, b = 0, n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            final int xx = x + dx, yy = y + dy;
                            if (xx < 0 || yy < 0 || xx >= SIZE || yy >= SIZE) {
                                continue;
                            }
                            final int rgb = rgbOf(in[yy * SIZE + xx]);
                            r += (rgb >> 16) & 0xFF;
                            g += (rgb >> 8) & 0xFF;
                            b += rgb & 0xFF;
                            n++;
                        }
                    }
                    out[y * SIZE + x] = nearest(((r / n) << 16) | ((g / n) << 8) | (b / n));
                }
            }
            return out;
        }

        /** Sobel edges: bright lines on black, or - for the pencil - dark lines on white. */
        static byte[] edges(final byte[] in, final boolean pencil) {
            final int[] lum = new int[in.length];
            for (int i = 0; i < in.length; i++) {
                lum[i] = luminance(rgbOf(in[i]));
            }
            final byte[] out = new byte[in.length];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    final int gx = at(lum, x + 1, y - 1) + 2 * at(lum, x + 1, y) + at(lum, x + 1, y + 1)
                            - at(lum, x - 1, y - 1) - 2 * at(lum, x - 1, y) - at(lum, x - 1, y + 1);
                    final int gy = at(lum, x - 1, y + 1) + 2 * at(lum, x, y + 1) + at(lum, x + 1, y + 1)
                            - at(lum, x - 1, y - 1) - 2 * at(lum, x, y - 1) - at(lum, x + 1, y - 1);
                    final int edge = clamp((int) Math.sqrt((double) gx * gx + (double) gy * gy) / 2);
                    final int v = pencil ? 255 - edge : edge;
                    out[y * SIZE + x] = nearest((v << 16) | (v << 8) | v);
                }
            }
            return out;
        }

        private static int at(final int[] lum, final int x, final int y) {
            final int xx = Math.max(0, Math.min(SIZE - 1, x));
            final int yy = Math.max(0, Math.min(SIZE - 1, y));
            return lum[yy * SIZE + xx];
        }

        /** A coloured pane or filter over the lens: the whole frame leans its way. */
        static byte[] tint(final byte[] in, final String filter) {
            final int tintRgb = filter.contains("red") ? 0xFF4040 : filter.contains("green") ? 0x40FF40
                    : filter.contains("blue") ? 0x4060FF : filter.contains("yellow") ? 0xFFE040
                    : filter.contains("cyan") ? 0x40E0FF : filter.contains("magenta") ? 0xFF40E0
                    : filter.contains("orange") ? 0xFF9020 : filter.contains("purple") ? 0xA040FF : 0xFF80C0;
            final byte[] out = new byte[in.length];
            for (int i = 0; i < in.length; i++) {
                final int rgb = rgbOf(in[i]);
                final int r = clamp((((rgb >> 16) & 0xFF) * 2 + ((tintRgb >> 16) & 0xFF)) / 3);
                final int g = clamp((((rgb >> 8) & 0xFF) * 2 + ((tintRgb >> 8) & 0xFF)) / 3);
                final int b = clamp(((rgb & 0xFF) * 2 + (tintRgb & 0xFF)) / 3);
                out[i] = nearest((r << 16) | (g << 8) | b);
            }
            return out;
        }

        private static int clamp(final int v) {
            return Math.max(0, Math.min(255, v));
        }
    }

    /** A blank picture to draw into. */
    public static byte[] blank() {
        return new byte[SIZE * SIZE];
    }
}
