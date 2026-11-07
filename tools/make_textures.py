#!/usr/bin/env python3
"""
Generates the Cover Studio texture overlays.

Four 1024x1024 RGBA tiles, drawn by hand into a PNG so the studio can lay one
over a generated cover and instantly give it a printed / filmic surface. They
are single-channel-friendly noise patterns, so a 1024 tile costs very little
and still tiles without visible seams.

Usage:  python3 tools/make_textures.py
"""

import os
import random
import struct
import zlib

SIZE = 1024
OUT_DIR = os.path.join("app", "src", "main", "res", "drawable-nodpi")


def png(path, pixels):
    """pixels: list of rows, each row a list of (r, g, b, a) tuples."""
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter: none
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, payload):
        return (
            struct.pack(">I", len(payload))
            + tag
            + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
        )

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    with open(path, "wb") as handle:
        handle.write(b"\x89PNG\r\n\x1a\n")
        handle.write(chunk(b"IHDR", header))
        handle.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        handle.write(chunk(b"IEND", b""))


def grain(seed):
    rnd = random.Random(seed)
    rows = []
    for _ in range(SIZE):
        rows.append([(255, 255, 255, rnd.randrange(0, 90)) for _ in range(SIZE)])
    return rows


def halftone():
    rows = []
    step = 8
    for y in range(SIZE):
        dy = (y % step) - step / 2
        row = []
        for x in range(SIZE):
            dx = (x % step) - step / 2
            dist = (dx * dx + dy * dy) ** 0.5
            alpha = max(0, int(150 - dist * 42))
            row.append((255, 255, 255, min(alpha, 150)))
        rows.append(row)
    return rows


def waves():
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            v = math_sin(x * 0.020 + y * 0.006) + math_sin(y * 0.017 - x * 0.004)
            alpha = int(max(0.0, v) * 55)
            row.append((255, 255, 255, min(alpha, 120)))
        rows.append(row)
    return rows


def scanline():
    rows = []
    for y in range(SIZE):
        alpha = 26 if y % 6 < 3 else 0
        rows.append([(255, 255, 255, alpha) for _ in range(SIZE)])
    return rows


def math_sin(x):
    import math

    return math.sin(x)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    makers = {
        "tex_grain": lambda: grain(7),
        "tex_halftone": halftone,
        "tex_waves": waves,
        "tex_scanline": scanline,
    }
    for name, maker in makers.items():
        path = os.path.join(OUT_DIR, name + ".png")
        png(path, maker())
        print(name, os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    main()
