"""Synthesise the Voyager's own sound effects (no stock Minecraft sounds) and write sounds.json.

Six mono OGG files under resources/assets/voyager/sounds/ - Minecraft only attenuates mono
sounds by distance, so everything here is mono:

  rocket_ignition   engines light under the skirt, building rumble and crackle   (~2.0 s)
  rocket_launch     lift-off: blast, roar fading upwards                         (~5.0 s)
  rocket_landing    roar approaching from above, braking                         (~3.2 s)
  rocket_touchdown  thud, clank and a hiss of steam                              (~1.8 s)
  gate_charge       the ring hums and shimmers, gathering End light              (~2.2 s)
  gate_warp         the flash: crack, zap and a ringing tail                     (~1.6 s)

Timings match fx/Effects.java: ROCKET_LIFTOFF_TICK=30 (1.5 s), ROCKET_TOUCHDOWN_TICK=60 (3.0 s),
GATE_VANISH_TICK=40 (2.0 s), GATE_APPEAR_TICK=30 (1.5 s, gate_charge is played pitched up there).
"""
import json
import os
import subprocess

import numpy as np
from scipy import signal

SR = 44100
import paths

OUT = paths.res("assets", "voyager", "sounds")
RNG = np.random.default_rng(20260902)


def t(dur):
    return np.arange(int(dur * SR)) / SR


def white(dur):
    return RNG.standard_normal(int(dur * SR))


def brown(dur):
    x = np.cumsum(white(dur))
    x -= np.linspace(x[0], x[-1], len(x))          # remove drift
    x = highpass(x, 35, order=2)                   # no inaudible sub-bass eating the headroom
    return x / (np.max(np.abs(x)) + 1e-9)


def lowpass(x, cutoff, order=2):
    sos = signal.butter(order, cutoff / (SR / 2), btype="low", output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, cutoff, order=2):
    sos = signal.butter(order, cutoff / (SR / 2), btype="high", output="sos")
    return signal.sosfilt(sos, x)


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band", output="sos")
    return signal.sosfilt(sos, x)


def varying_lowpass(x, cutoffs):
    """One-pole low-pass whose cutoff (Hz, one per sample) changes over time."""
    alpha = 1.0 - np.exp(-2.0 * np.pi * np.asarray(cutoffs) / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += alpha[i] * (x[i] - acc)
        y[i] = acc
    return y


def sweep(f0, f1, dur, exp=True):
    """Sine whose frequency glides from f0 to f1."""
    tt = t(dur)
    if exp:
        f = f0 * (f1 / f0) ** (tt / dur)
    else:
        f = f0 + (f1 - f0) * tt / dur
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase), f


def env(points, dur):
    """Piecewise-linear envelope from (time, value) points."""
    tt = t(dur)
    xs, ys = zip(*points)
    return np.interp(tt, xs, ys)


def crackle(dur, density_curve, lo=800, hi=5000):
    """Random pops, band-limited: rocket exhaust crackle. density_curve: pops per second per sample."""
    n = int(dur * SR)
    out = np.zeros(n)
    dens = np.asarray(density_curve)
    hits = RNG.random(n) < dens / SR
    idx = np.nonzero(hits)[0]
    for i in idx:
        length = RNG.integers(20, 90)
        pop = RNG.standard_normal(length) * np.exp(-np.arange(length) / (length / 3))
        end = min(n, i + length)
        out[i:end] += pop[: end - i] * RNG.uniform(0.4, 1.0)
    return bandpass(out, lo, hi)


def fade(x, ms_in=5, ms_out=30):
    n_in = int(SR * ms_in / 1000)
    n_out = int(SR * ms_out / 1000)
    x = x.copy()
    x[:n_in] *= np.linspace(0, 1, n_in)
    x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def normalize(x, peak=0.89):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def soft(x, drive=1.0):
    return np.tanh(x * drive) / np.tanh(drive)


# ------------------------------------------------------------------ the sounds

def rocket_ignition():
    dur = 2.0
    tt = t(dur)
    rumble = varying_lowpass(brown(dur), 90 + 260 * (tt / dur) ** 2)
    rumble *= env([(0, 0.15), (0.3, 0.4), (1.2, 0.8), (2.0, 1.0)], dur)
    pops = crackle(dur, 8 + 110 * (tt / dur) ** 2) * env([(0, 0.1), (2.0, 0.7)], dur)
    whine, _ = sweep(55, 190, dur)
    whine *= 0.18 * env([(0, 0), (0.5, 0.2), (2.0, 1.0)], dur)
    thump = np.sin(2 * np.pi * 42 * tt) * np.exp(-tt * 9) * 0.9
    hiss = highpass(white(dur), 1800) * 0.06 * env([(0, 0), (0.4, 1), (2.0, 1)], dur)
    mix = rumble * 1.0 + pops * 0.8 + whine + thump + hiss
    return fade(normalize(soft(mix, 1.4)), 5, 40)


def rocket_launch():
    dur = 5.0
    tt = t(dur)
    # blast at t=0
    blast = white(dur) * np.exp(-tt * 14) * 1.2
    boom = np.sin(2 * np.pi * 38 * tt) * np.exp(-tt * 3.5) * 1.3
    # roar: bright at first, then muffled and quieter as it climbs away
    cutoff = 900 * np.exp(-tt * 0.55) + 120
    roar = varying_lowpass(brown(dur) * 0.6 + white(dur) * 0.4, cutoff)
    roar *= env([(0, 0.9), (0.9, 1.0), (1.8, 0.85), (3.2, 0.35), (5.0, 0.03)], dur)
    pops = crackle(dur, 140 * np.exp(-tt * 0.8) + 4) * env([(0, 0.8), (2.0, 0.5), (5.0, 0.05)], dur)
    # engine tone dropping (doppler as it recedes)
    tone, _ = sweep(115, 62, dur)
    tone *= 0.22 * env([(0, 0.6), (1.5, 1.0), (5.0, 0.0)], dur)
    mix = blast + boom + roar * 1.1 + pops * 0.7 + tone
    return fade(normalize(soft(mix, 1.6)), 3, 200)


def rocket_landing():
    dur = 3.2
    tt = t(dur)
    # from far above: quiet and muffled, then loud and bright; braking burn peaks at 3.0 s
    p = np.clip(tt / 3.0, 0, 1)
    cutoff = 150 + 850 * p ** 2
    roar = varying_lowpass(brown(dur) * 0.6 + white(dur) * 0.4, cutoff)
    roar *= env([(0, 0.04), (1.0, 0.18), (2.2, 0.6), (2.9, 1.0), (3.05, 0.9), (3.2, 0.25)], dur)
    pops = crackle(dur, 4 + 120 * p ** 2) * env([(0, 0.05), (3.0, 0.7), (3.2, 0.2)], dur)
    tone, _ = sweep(60, 118, dur)
    tone *= 0.2 * env([(0, 0.2), (2.8, 1.0), (3.2, 0.3)], dur)
    mix = roar * 1.1 + pops * 0.7 + tone
    return fade(normalize(soft(mix, 1.5)), 40, 80)


def rocket_touchdown():
    dur = 1.8
    tt = t(dur)
    thud = np.sin(2 * np.pi * 48 * tt) * np.exp(-tt * 7) * 1.4
    burst = lowpass(white(dur), 1500) * np.exp(-tt * 18) * 1.0
    # metal clank of the clamps
    clank = np.zeros_like(tt)
    for delay, freq, amp in ((0.05, 620, 0.5), (0.11, 930, 0.35), (0.19, 410, 0.3)):
        m = tt >= delay
        clank[m] += np.sin(2 * np.pi * freq * (tt[m] - delay)) * np.exp(-(tt[m] - delay) * 22) * amp
    hiss = highpass(white(dur), 2500) * env([(0, 0), (0.15, 0.55), (0.6, 0.35), (1.8, 0.0)], dur)
    mix = thud + burst + clank + hiss
    return fade(normalize(soft(mix, 1.3)), 2, 120)


def gate_charge():
    dur = 2.2
    tt = t(dur)
    base, f = sweep(48, 230, dur)
    phase = np.cumsum(f) / SR
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * tt)
    hum = np.zeros_like(tt)
    for k, a in ((1, 1.0), (2, 0.5), (3, 0.3), (5, 0.16), (7, 0.08)):
        hum += a * np.sin(2 * np.pi * k * phase * vib)
    hum *= env([(0, 0.15), (0.8, 0.5), (1.8, 0.95), (2.2, 1.0)], dur)
    shimmer = np.zeros_like(tt)
    for freq, rate, a in ((1480, 6.2, 0.35), (2210, 8.7, 0.3), (2960, 5.1, 0.22), (3720, 7.3, 0.16)):
        trem = 0.5 + 0.5 * np.sin(2 * np.pi * rate * tt + freq)
        shimmer += a * np.sin(2 * np.pi * freq * tt) * trem
    shimmer *= env([(0, 0.0), (0.6, 0.15), (2.2, 0.8)], dur)
    whoosh = bandpass(white(dur), 250, 2600)
    whoosh = varying_lowpass(whoosh, 300 + 2600 * (tt / dur) ** 2)
    whoosh *= env([(0, 0.0), (1.0, 0.2), (2.2, 0.9)], dur)
    mix = hum * 0.9 + shimmer * 0.5 + whoosh * 0.7
    return fade(normalize(soft(mix, 1.2)), 30, 20)


def gate_warp():
    dur = 1.6
    tt = t(dur)
    crack = white(dur) * np.exp(-tt * 60) * 1.5
    zap, _ = sweep(2600, 180, dur)
    zap = soft(zap * 2.5, 2.0) * np.exp(-tt * 7) * 0.9
    chirp, _ = sweep(380, 1400, dur)
    chirp *= np.exp(-tt * 5) * 0.35 * (tt > 0.04)
    thump = np.sin(2 * np.pi * 55 * tt) * np.exp(-tt * 6) * 0.9
    ring = np.zeros_like(tt)
    for freq, a, dec in ((660, 0.3, 3.2), (990, 0.22, 3.6), (1320, 0.16, 4.2), (1980, 0.1, 5.0)):
        ring += a * np.sin(2 * np.pi * freq * tt) * np.exp(-tt * dec)
    ring *= (tt > 0.06)
    sparkle = highpass(white(dur), 5000) * np.exp(-tt * 4) * 0.12
    mix = crack + zap + chirp + thump + ring + sparkle
    return fade(normalize(soft(mix, 1.4)), 1, 150)


SOUNDS = {
    "rocket_ignition": rocket_ignition,
    "rocket_launch": rocket_launch,
    "rocket_landing": rocket_landing,
    "rocket_touchdown": rocket_touchdown,
    "gate_charge": gate_charge,
    "gate_warp": gate_warp,
}


def write_ogg(name, samples):
    os.makedirs(OUT, exist_ok=True)
    wav = paths.out(f"{name}.wav")
    from scipy.io import wavfile
    wavfile.write(wav, SR, (np.clip(samples, -1, 1) * 32767).astype(np.int16))
    ogg = os.path.join(OUT, f"{name}.ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", ogg], check=True)
    return ogg


def main():
    for name, fn in SOUNDS.items():
        samples = fn()
        path = write_ogg(name, samples)
        print(f"{name}: {len(samples) / SR:.2f} s, peak {np.max(np.abs(samples)):.2f}, {os.path.getsize(path) // 1024} KB")
    sounds_json = {
        name: {"subtitle": f"subtitles.voyager.{name}", "sounds": [{"name": f"voyager:{name}", "stream": False}]}
        for name in SOUNDS
    }
    with open(paths.res("assets", "voyager", "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=2)
    print("sounds.json written")


if __name__ == "__main__":
    main()
