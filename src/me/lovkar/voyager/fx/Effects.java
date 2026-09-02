package me.lovkar.voyager.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/**
 * Send-off and homecoming choreography, ticked on the server: ignition, lift-off and the
 * climbing exhaust column of a Launchpad; the tightening vortex and flash of an End Gate.
 * Every show is a list of per-tick steps plus a callback at the key moment (lift-off,
 * touchdown, vanish, appear) so the AI and the building act exactly when the picture does.
 */
public final class Effects {

    private static final List<Show> SHOWS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    /** Gates that already have their ambient shimmer running, by ring centre. */
    private static final Set<BlockPos> AMBIENT = new HashSet<>();

    /** Ticks after which the rocket leaves the pad / the gate swallows the Voyager. */
    public static final int ROCKET_LIFTOFF_TICK = 30;
    public static final int ROCKET_TOUCHDOWN_TICK = 60;
    public static final int GATE_VANISH_TICK = 40;
    public static final int GATE_APPEAR_TICK = 30;

    /** The transport beam: how high it reaches, how fast the front travels (blocks per tick) and how long it stands. */
    private static final int BEAM_HEIGHT = 96;
    private static final int BEAM_SPEED = 3;
    private static final int BEAM_HOLD = 20;
    private static final int BEAM_FADE = 24;
    private static final DustParticleOptions BEAM_PURPLE = new DustParticleOptions(new Vector3f(0.58f, 0.18f, 0.96f), 2.4f);
    private static final DustParticleOptions BEAM_LILAC = new DustParticleOptions(new Vector3f(0.86f, 0.58f, 1.0f), 1.5f);
    /** Players this far from a beam or a climbing rocket still get its particles (vanilla stops at 32 blocks). */
    private static final double FAR = 192.0;

    private Effects() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Effects::onTick);
    }

    private static void onTick(final ServerTickEvent.Post event) {
        if (SHOWS.isEmpty()) {
            return;
        }
        // a step may start another show (the gate's flash starts the beam), so tick a snapshot
        // and never touch SHOWS itself while walking it
        final List<Show> finished = new ArrayList<>();
        for (final Show show : new ArrayList<>(SHOWS)) {
            boolean done;
            try {
                done = !isLive(show.level) || !show.step.test(show.tick++);
            } catch (final Exception e) {
                done = true;
            }
            if (done) {
                finished.add(show);
            }
        }
        for (final Show show : finished) {
            SHOWS.remove(show);
            if (show.onEnd != null) {
                try {
                    show.onEnd.run();
                } catch (final Exception ignored) {
                    // an ambient show's bookkeeping must never take the server down
                }
            }
        }
    }

    /** A show whose world was unloaded (the player left mid-launch) is dropped, not played into the void. */
    private static boolean isLive(final ServerLevel level) {
        return level.getServer() != null && level.getServer().isRunning() && level.getServer().getLevel(level.dimension()) == level;
    }

    private static final class Show {
        final ServerLevel level;
        final IntPredicate step;   // returns false when finished
        final Runnable onEnd;
        int tick = 0;

        Show(final ServerLevel level, final IntPredicate step) {
            this(level, step, null);
        }

        Show(final ServerLevel level, final IntPredicate step, final Runnable onEnd) {
            this.level = level;
            this.step = step;
            this.onEnd = onEnd;
        }
    }

    // ------------------------------------------------------------------ ambience

    /**
     * The End Gate's idle shimmer, shaders or not: purple motes drifting onto the panes of the
     * film all day long, now and then a star rising through it - the same language as the End
     * portal's own particles. One per gate; it ends when the building is gone or unloaded.
     */
    public static void ambientGate(final ServerLevel level, final BlockPos gate, final List<BlockPos> panes, final BooleanSupplier alive) {
        if (panes.isEmpty() || !AMBIENT.add(gate)) {
            return;
        }
        final List<BlockPos> film = List.copyOf(panes);
        final int motes = Math.max(1, film.size() / 40);
        SHOWS.add(new Show(level, t -> {
            if (t % 100 == 0 && !alive.getAsBoolean()) {
                return false;
            }
            if (t % 3 == 0) {
                for (int i = 0; i < motes; i++) {
                    final BlockPos p = film.get(RANDOM.nextInt(film.size()));
                    spray(level, ParticleTypes.PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.6);
                }
            }
            if (t % 35 == 0) {
                final BlockPos p = film.get(RANDOM.nextInt(film.size()));
                dart(level, ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0.0, 0.03, 0.0);
            }
            return true;
        }, () -> AMBIENT.remove(gate)));
    }

    private static void play(final ServerLevel level, final BlockPos at, final SoundEvent sound, final float volume, final float pitch) {
        level.playSound(null, at, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    private static void spray(final ServerLevel level, final ParticleOptions type, final double x, final double y, final double z,
                              final int count, final double sx, final double sy, final double sz, final double speed) {
        level.sendParticles(type, x, y, z, count, sx, sy, sz, speed);
    }

    /** One particle with a velocity of its own (count 0 makes the offsets a velocity). */
    private static void dart(final ServerLevel level, final ParticleOptions type, final double x, final double y, final double z,
                             final double vx, final double vy, final double vz) {
        level.sendParticles(type, x, y, z, 0, vx, vy, vz, 1.0);
    }

    /**
     * Like spray, but reaches every player within FAR blocks: vanilla only sends particles to
     * players within 32 blocks of the spawn point, which would cut the top off a beam or an
     * exhaust column for anyone standing on the ground next to it.
     */
    private static void sprayFar(final ServerLevel level, final ParticleOptions type, final double x, final double y, final double z,
                                 final int count, final double sx, final double sy, final double sz, final double speed) {
        for (final ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < FAR * FAR) {
                level.sendParticles(player, type, true, x, y, z, count, sx, sy, sz, speed);
            }
        }
    }

    /** One slice of the beam: a white core with a purple sheath, around (x, y, z). */
    private static void beamSlice(final ServerLevel level, final double x, final double y, final double z, final int density) {
        sprayFar(level, BEAM_PURPLE, x, y, z, density, 0.35, 0.5, 0.35, 0.0);
        sprayFar(level, BEAM_LILAC, x, y, z, 1, 0.15, 0.5, 0.15, 0.0);
        if (RANDOM.nextInt(2) == 0) {
            sprayFar(level, ParticleTypes.END_ROD, x, y, z, 1, 0.1, 0.4, 0.1, 0.01);
        }
    }

    /**
     * The gate's transport beam: a column of purple light shoots from the dais into the sky
     * (or comes down from it when up is false), stands for a moment and fades out.
     */
    private static void beam(final ServerLevel level, final BlockPos stand, final boolean up) {
        final double x = stand.getX() + 0.5, z = stand.getZ() + 0.5, base = stand.getY() + 0.2;
        final int riseTicks = BEAM_HEIGHT / BEAM_SPEED;
        SHOWS.add(new Show(level, t -> {
            if (t < riseTicks) {
                // the front travels; the slice it just reached is drawn densely
                final int reached = t * BEAM_SPEED;
                for (int h = reached; h < reached + BEAM_SPEED; h++) {
                    final double y = up ? base + h : base + BEAM_HEIGHT - h;
                    beamSlice(level, x, y, z, 3);
                }
                // and the part already lit is kept alive with a sparse sprinkle
                if (t % 2 == 0) {
                    for (int h = 0; h < reached; h += 6) {
                        final double y = up ? base + h : base + BEAM_HEIGHT - h;
                        beamSlice(level, x, y, z, 1);
                    }
                }
                return true;
            }
            final int age = t - riseTicks;
            if (age < BEAM_HOLD) {
                for (int h = age % 3; h < BEAM_HEIGHT; h += 3) {
                    beamSlice(level, x, base + h, z, 1);
                }
                return true;
            }
            if (age < BEAM_HOLD + BEAM_FADE) {
                final int step = 4 + (age - BEAM_HOLD) / 3;
                for (int h = age % step; h < BEAM_HEIGHT; h += step) {
                    sprayFar(level, BEAM_LILAC, x, base + h, z, 1, 0.3, 0.5, 0.3, 0.0);
                }
                return true;
            }
            return false;
        }));
    }

    // ------------------------------------------------------------------ Launchpad

    /**
     * Ignition under the skirt, then the rocket leaves (callback) and an exhaust column climbs
     * sixty blocks while the pad disappears in smoke.
     */
    public static void rocketLaunch(final ServerLevel level, final BlockPos cabin, final Runnable liftoff) {
        final double x = cabin.getX() + 0.5, z = cabin.getZ() + 0.5;
        final double pad = cabin.getY() - 2.0;      // trench / mount level under the skirt
        final double flame = cabin.getY() - 1.6;
        SHOWS.add(new Show(level, t -> {
            if (t == 0) {
                play(level, cabin, VoyagerSounds.ROCKET_IGNITION.get(), 2.5f, 1.0f);
            }
            if (t < ROCKET_LIFTOFF_TICK) {
                final double grow = 1.5 + t * 0.12;
                spray(level, ParticleTypes.FLAME, x, flame, z, 6 + t / 4, 1.2, 0.2, 1.2, 0.04);
                spray(level, ParticleTypes.LARGE_SMOKE, x, pad + 0.5, z, 5, grow, 0.4, grow, 0.02);
                if (t % 5 == 0) {
                    spray(level, ParticleTypes.LAVA, x, flame, z, 3, 1.0, 0.2, 1.0, 0.0);
                }
                return true;
            }
            if (t == ROCKET_LIFTOFF_TICK) {
                liftoff.run();
                play(level, cabin, VoyagerSounds.ROCKET_LAUNCH.get(), 3.0f, 1.0f);
                spray(level, ParticleTypes.EXPLOSION, x, pad + 1.0, z, 3, 2.0, 0.5, 2.0, 0.0);
            }
            final int age = t - ROCKET_LIFTOFF_TICK;
            if (age > 100) {
                return false;
            }
            final double climb = age * age * 0.006 + age * 0.15;         // slow start, then away
            final double ry = cabin.getY() + climb;
            sprayFar(level, ParticleTypes.FLAME, x, ry - 1.5, z, 14, 0.9, 1.2, 0.9, 0.12);
            sprayFar(level, ParticleTypes.END_ROD, x, ry, z, 2, 0.6, 0.6, 0.6, 0.02);
            sprayFar(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, ry - 3.0, z, 3, 1.2, 1.5, 1.2, 0.01);
            if (age % 3 == 0) {
                sprayFar(level, ParticleTypes.FIREWORK, x, ry - 2.0, z, 2, 0.5, 0.5, 0.5, 0.08);
            }
            if (age < 45) {
                spray(level, ParticleTypes.LARGE_SMOKE, x, pad + 0.5, z, 12, 5.0, 1.0, 5.0, 0.03);
                spray(level, ParticleTypes.CLOUD, x, pad + 0.5, z, 4, 4.0, 0.5, 4.0, 0.05);
            }
            return true;
        }));
    }

    /** The exhaust column comes down, the rocket is back on the pad (callback) in a burst of smoke. */
    public static void rocketLanding(final ServerLevel level, final BlockPos cabin, final Runnable touchdown) {
        final double x = cabin.getX() + 0.5, z = cabin.getZ() + 0.5;
        final double pad = cabin.getY() - 2.0;
        SHOWS.add(new Show(level, t -> {
            if (t == 0) {
                play(level, cabin, VoyagerSounds.ROCKET_LANDING.get(), 2.5f, 1.0f);
            }
            if (t < ROCKET_TOUCHDOWN_TICK) {
                final double remaining = ROCKET_TOUCHDOWN_TICK - t;
                final double height = remaining * remaining * 0.012 + remaining * 0.1;   // fast, then braking
                final double ry = cabin.getY() + height;
                sprayFar(level, ParticleTypes.FLAME, x, ry - 1.5, z, 10, 0.8, 0.8, 0.8, 0.1);
                sprayFar(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, ry + 1.0, z, 3, 1.0, 1.0, 1.0, 0.01);
                if (t > 35) {
                    spray(level, ParticleTypes.LARGE_SMOKE, x, pad + 0.5, z, 6, 3.0 + (t - 35) * 0.1, 0.5, 3.0 + (t - 35) * 0.1, 0.03);
                }
                return true;
            }
            if (t == ROCKET_TOUCHDOWN_TICK) {
                touchdown.run();
                play(level, cabin, VoyagerSounds.ROCKET_TOUCHDOWN.get(), 2.5f, 1.0f);
                spray(level, ParticleTypes.LARGE_SMOKE, x, pad + 0.5, z, 80, 4.5, 0.6, 4.5, 0.04);
                spray(level, ParticleTypes.CLOUD, x, pad + 0.5, z, 40, 4.0, 0.4, 4.0, 0.06);
                spray(level, ParticleTypes.FLAME, x, cabin.getY() - 1.6, z, 30, 1.5, 0.3, 1.5, 0.05);
            }
            if (t < ROCKET_TOUCHDOWN_TICK + 30) {
                spray(level, ParticleTypes.SMOKE, x, pad + 0.5, z, 4, 3.0, 0.3, 3.0, 0.02);
                return true;
            }
            return false;
        }));
    }

    // ------------------------------------------------------------------ End Gate

    /**
     * A vortex tightens in the ring while End light gathers around the Voyager on the dais; at
     * the flash they are gone (callback).
     */
    public static void gateDeparture(final ServerLevel level, final BlockPos gate, final BlockPos stand, final Runnable vanish) {
        final double gx = gate.getX() + 0.5, gy = gate.getY() + 0.5, gz = gate.getZ() + 0.5;
        final double sx = stand.getX() + 0.5, sy = stand.getY(), sz = stand.getZ() + 0.5;
        SHOWS.add(new Show(level, t -> {
            if (t == 0) {
                play(level, gate, VoyagerSounds.GATE_CHARGE.get(), 2.5f, 1.0f);
            }
            if (t < GATE_VANISH_TICK) {
                vortex(level, gx, gy, gz, t, 4.5 - t * 0.085);
                spray(level, ParticleTypes.PORTAL, sx, sy + 1.0, sz, 6, 0.35, 0.9, 0.35, 0.02);
                if (t > 20) {
                    spray(level, ParticleTypes.DRAGON_BREATH, gx, gy, gz, 2, 0.6, 0.6, 0.3, 0.01);
                }
                return true;
            }
            if (t == GATE_VANISH_TICK) {
                vanish.run();
                play(level, gate, VoyagerSounds.GATE_WARP.get(), 3.0f, 1.0f);
                spray(level, ParticleTypes.FLASH, sx, sy + 1.0, sz, 1, 0.0, 0.0, 0.0, 0.0);
                spray(level, ParticleTypes.END_ROD, sx, sy + 1.0, sz, 60, 0.4, 0.8, 0.4, 0.18);
                spray(level, ParticleTypes.REVERSE_PORTAL, gx, gy, gz, 160, 1.0, 1.0, 0.4, 0.35);
                beam(level, stand, true);   // and up they go
            }
            if (t < GATE_VANISH_TICK + 25) {
                spray(level, ParticleTypes.REVERSE_PORTAL, gx, gy, gz, 8, 2.0, 2.0, 0.3, 0.05);
                return true;
            }
            return false;
        }));
    }

    /** The ring lights up again and the Voyager steps out of the flash (callback). */
    public static void gateArrival(final ServerLevel level, final BlockPos gate, final BlockPos stand, final Runnable appear) {
        final double gx = gate.getX() + 0.5, gy = gate.getY() + 0.5, gz = gate.getZ() + 0.5;
        final double sx = stand.getX() + 0.5, sy = stand.getY(), sz = stand.getZ() + 0.5;
        SHOWS.add(new Show(level, t -> {
            if (t == 0) {
                play(level, gate, VoyagerSounds.GATE_CHARGE.get(), 2.0f, 1.15f);
                beam(level, stand, false);   // the beam comes down first, the Voyager with it
            }
            if (t < GATE_APPEAR_TICK) {
                vortex(level, gx, gy, gz, t, 1.0 + t * 0.11);
                return true;
            }
            if (t == GATE_APPEAR_TICK) {
                appear.run();
                play(level, gate, VoyagerSounds.GATE_WARP.get(), 2.5f, 0.9f);
                spray(level, ParticleTypes.FLASH, sx, sy + 1.0, sz, 1, 0.0, 0.0, 0.0, 0.0);
                spray(level, ParticleTypes.PORTAL, sx, sy + 1.0, sz, 80, 0.5, 1.0, 0.5, 0.4);
                spray(level, ParticleTypes.END_ROD, sx, sy + 1.0, sz, 30, 0.5, 0.8, 0.5, 0.1);
            }
            if (t < GATE_APPEAR_TICK + 20) {
                spray(level, ParticleTypes.REVERSE_PORTAL, gx, gy, gz, 6, 2.0, 2.0, 0.3, 0.05);
                return true;
            }
            return false;
        }));
    }

    /** Six arms of End light spiralling in the vertical plane of the ring. */
    private static void vortex(final ServerLevel level, final double gx, final double gy, final double gz, final int t, final double radius) {
        for (int k = 0; k < 6; k++) {
            final double a = t * 0.32 + k * Math.PI / 3.0;
            final double px = gx + Math.cos(a) * radius;
            final double py = gy + Math.sin(a) * radius;
            dart(level, ParticleTypes.REVERSE_PORTAL, px, py, gz, -Math.cos(a) * 0.08, -Math.sin(a) * 0.08, 0.0);
            if (RANDOM.nextInt(3) == 0) {
                dart(level, ParticleTypes.END_ROD, px, py, gz + (RANDOM.nextDouble() - 0.5) * 0.6, -Math.sin(a) * 0.04, Math.cos(a) * 0.04, 0.0);
            }
        }
    }
}
