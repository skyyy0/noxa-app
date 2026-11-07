#!/usr/bin/env python3
"""
Generates the ambience loops shipped with NOXA Music.

These are real, audible loops (not padding): each one is mixed under the music
by the Ambience player when the user arms the sleep timer, and they are stored
uncompressed in `res/raw` so playback starts instantly without decoding work.

Pure standard library on purpose — the build environment has no NumPy — and the
signal chain is deliberately simple: coloured noise, slow envelopes, and a few
transient layers (droplets, crackles, chirps) to keep a 32 second loop from
sounding like a hiss.

Usage:  python3 tools/make_ambience.py
"""

import math
import os
import random
import struct
import wave

RATE = 44100
SECONDS = 30
FRAMES = RATE * SECONDS
OUT_DIR = os.path.join("app", "src", "main", "res", "raw")
CROSSFADE = RATE // 2  # half a second, folded over the loop seam


def lowpass(samples, alpha):
    """One-pole low-pass; `alpha` close to 0 = darker."""
    out = [0.0] * len(samples)
    prev = 0.0
    for i, value in enumerate(samples):
        prev += alpha * (value - prev)
        out[i] = prev
    return out


def highpass(samples, alpha):
    out = [0.0] * len(samples)
    prev_in = 0.0
    prev_out = 0.0
    for i, value in enumerate(samples):
        prev_out = alpha * (prev_out + value - prev_in)
        prev_in = value
        out[i] = prev_out
    return out


def noise(count, seed):
    rnd = random.Random(seed)
    return [rnd.uniform(-1.0, 1.0) for _ in range(count)]


def normalise(samples, peak=0.62):
    top = max(1e-9, max(abs(s) for s in samples))
    gain = peak / top
    return [s * gain for s in samples]


def rain():
    """Steady rain bed plus sparse droplets."""
    body = lowpass(highpass(noise(FRAMES, 11), 0.55), 0.30)
    body = [s * 0.85 for s in body]
    rnd = random.Random(12)
    for _ in range(2200):
        start = rnd.randrange(0, FRAMES - 900)
        amp = rnd.uniform(0.05, 0.22)
        length = rnd.randrange(120, 700)
        for i in range(length):
            body[start + i] += amp * math.exp(-i / (length * 0.28)) * rnd.uniform(-1, 1)
    return body


def fire():
    """Low rumble with crackles on top."""
    rumble = lowpass(noise(FRAMES, 21), 0.02)
    rumble = [s * 2.6 for s in rumble]
    rnd = random.Random(22)
    for _ in range(900):
        start = rnd.randrange(0, FRAMES - 400)
        amp = rnd.uniform(0.15, 0.55)
        length = rnd.randrange(40, 320)
        for i in range(length):
            rumble[start + i] += amp * math.exp(-i / (length * 0.22)) * rnd.uniform(-1, 1)
    return rumble


def waves():
    """Noise swells: a slow envelope over a wet, low-passed bed."""
    bed = lowpass(noise(FRAMES, 31), 0.12)
    out = [0.0] * FRAMES
    for i in range(FRAMES):
        # Two beating swells so the loop never feels metronomic.
        phase = i / RATE
        swell = 0.55 + 0.45 * math.sin(2 * math.pi * 0.055 * phase)
        swell *= 0.80 + 0.20 * math.sin(2 * math.pi * 0.017 * phase + 1.1)
        out[i] = bed[i] * swell * 1.9
    return out


def night():
    """Crickets and a breath of wind."""
    wind = lowpass(noise(FRAMES, 41), 0.04)
    out = [s * 0.9 for s in wind]
    rnd = random.Random(42)
    # Cricket chirps: short 4 kHz bursts in trains of three.
    for _ in range(180):
        start = rnd.randrange(0, FRAMES - 12000)
        for chirp in range(rnd.randint(2, 4)):
            base = start + chirp * 1400
            for i in range(900):
                if base + i >= FRAMES:
                    break
                env = math.exp(-i / 240.0)
                carrier = math.sin(2 * math.pi * 4200 * i / RATE)
                out[base + i] += 0.16 * env * carrier
    return out


def fold(samples):
    """Crossfades the tail into the head so the loop seam is inaudible."""
    head = samples[:CROSSFADE]
    tail = samples[-CROSSFADE:]
    for i in range(CROSSFADE):
        w = i / CROSSFADE
        head[i] = tail[i] * (1 - w) + head[i] * w
    return samples[:FRAMES - CROSSFADE]


def write_wav(path, mono):
    """Writes a stereo 16-bit file; the right channel is slightly delayed."""
    delay = 37  # ~0.8 ms — width without an audible echo
    frames = len(mono)
    data = struct.pack("<%dh" % (frames * 2), *[
        value
        for i in range(frames)
        for value in (
            int(max(-1.0, min(1.0, mono[i])) * 32000),
            int(max(-1.0, min(1.0, mono[i - delay] if i >= delay else 0.0)) * 32000),
        )
    ])
    with wave.open(path, "wb") as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(RATE)
        handle.writeframes(data)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    recipes = {
        "amb_rain": rain,
        "amb_fire": fire,
        "amb_waves": waves,
        "amb_night": night,
    }
    for name, recipe in recipes.items():
        mono = normalise(recipe())
        mono = fold(mono)
        path = os.path.join(OUT_DIR, name + ".wav")
        write_wav(path, mono)
        print(name, os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    main()
