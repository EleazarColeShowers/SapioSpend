#!/usr/bin/env python3
"""Rasterise the SapioSpend launcher mark to PNG at every density we ship.

Pure stdlib: geometry is evaluated per sub-sample and averaged, so no imaging
library is needed. Coordinates are normalised to the visible tile [0,1]^2, which
maps to the centre 72dp of the 108dp adaptive-icon canvas.
"""

import math
import os
import struct
import sys
import zlib

# --- Palette (matches ui/theme/AppColors.kt) ---------------------------------
BG_TL = (0x23, 0x2A, 0x38)   # top-left of the diagonal gradient
BG_BR = (0x0F, 0x11, 0x15)   # bottom-right
GREEN = (0x16, 0xA3, 0x4A)   # AppColors.Success
WHITE = (0xFF, 0xFF, 0xFF)

# --- Mark geometry, in tile space -------------------------------------------
BAR_W = 0.135
GAP = 0.075
BASE_Y = 0.775
X0 = (1.0 - (3 * BAR_W + 2 * GAP)) / 2.0

# (top_y, colour, alpha) — ascending, with the tallest carrying the accent.
BARS = [
    (0.545, WHITE, 0.50),
    (0.415, WHITE, 0.78),
    (0.285, GREEN, 1.00),
]

CORNER_R = 0.2225  # legacy square-icon corner radius, in tile units


def bar_rects():
    for i, (top, colour, alpha) in enumerate(BARS):
        x = X0 + i * (BAR_W + GAP)
        yield (x, top, x + BAR_W, BASE_Y), colour, alpha


def inside_pill(px, py, rect):
    """Rounded rect whose radius is half its width, i.e. a pill."""
    x0, y0, x1, y1 = rect
    r = (x1 - x0) / 2.0
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    hx, hy = (x1 - x0) / 2.0 - r, (y1 - y0) / 2.0 - r
    dx = max(abs(px - cx) - hx, 0.0)
    dy = max(abs(py - cy) - hy, 0.0)
    return math.hypot(dx, dy) <= r


def inside_shape(px, py, shape):
    if shape == "full":
        return 0.0 <= px <= 1.0 and 0.0 <= py <= 1.0
    if shape == "circle":
        return math.hypot(px - 0.5, py - 0.5) <= 0.5
    # rounded square
    r = CORNER_R
    dx = max(abs(px - 0.5) - (0.5 - r), 0.0)
    dy = max(abs(py - 0.5) - (0.5 - r), 0.0)
    return math.hypot(dx, dy) <= r


def sample(cx_, cy_, tx, ty, shape):
    """`c*` are canvas coords (background + mask); `t*` are tile coords (the mark)."""
    if not inside_shape(cx_, cy_, shape):
        return (0, 0, 0, 0.0)
    t = min(max((cx_ + cy_) / 2.0, 0.0), 1.0)  # diagonal gradient
    r = BG_TL[0] + (BG_BR[0] - BG_TL[0]) * t
    g = BG_TL[1] + (BG_BR[1] - BG_TL[1]) * t
    b = BG_TL[2] + (BG_BR[2] - BG_TL[2]) * t
    for rect, colour, alpha in bar_rects():
        if inside_pill(tx, ty, rect):
            r = r + (colour[0] - r) * alpha
            g = g + (colour[1] - g) * alpha
            b = b + (colour[2] - b) * alpha
    return (r, g, b, 1.0)


def render(size, shape, ss, inset=0.0):
    """Return RGBA bytes. `inset` shrinks only the mark; the background is full-bleed."""
    rows = bytearray()
    span = 1.0 - 2 * inset
    for y in range(size):
        for x in range(size):
            ar = ag = ab = aa = 0.0
            for sy in range(ss):
                for sx in range(ss):
                    px = (x + (sx + 0.5) / ss) / size
                    py = (y + (sy + 0.5) / ss) / size
                    # canvas -> tile, for the mark only
                    tx = (px - inset) / span
                    ty = (py - inset) / span
                    r, g, b, a = sample(px, py, tx, ty, shape)
                    ar += r * a
                    ag += g * a
                    ab += b * a
                    aa += a
            n = ss * ss
            aa /= n
            if aa > 0:
                ar = ar / n / aa
                ag = ag / n / aa
                ab = ab / n / aa
            rows += bytes((
                max(0, min(255, int(round(ar)))),
                max(0, min(255, int(round(ag)))),
                max(0, min(255, int(round(ab)))),
                max(0, min(255, int(round(aa * 255)))),
            ))
    return bytes(rows)


def write_png(path, size, rgba, opaque=False):
    if opaque:
        # Play requires the 512 store icon to be fully opaque.
        out = bytearray()
        for i in range(0, len(rgba), 4):
            out += rgba[i:i + 3]
        raw = b"".join(
            b"\x00" + bytes(out[y * size * 3:(y + 1) * size * 3]) for y in range(size)
        )
        colour_type = 2
    else:
        raw = b"".join(
            b"\x00" + rgba[y * size * 4:(y + 1) * size * 4] for y in range(size)
        )
        colour_type = 6

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, colour_type, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as fh:
        fh.write(png)


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    res = sys.argv[1]
    store_dir = sys.argv[2]
    for name, size in DENSITIES.items():
        d = os.path.join(res, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        write_png(os.path.join(d, "ic_launcher.png"), size,
                  render(size, "rounded", 4))
        write_png(os.path.join(d, "ic_launcher_round.png"), size,
                  render(size, "circle", 4))
        print(f"  mipmap-{name}: {size}x{size}")

    os.makedirs(store_dir, exist_ok=True)
    # Full-bleed square, opaque: Play applies its own mask.
    write_png(os.path.join(store_dir, "play-store-icon-512.png"), 512,
              render(512, "full", 3), opaque=True)
    print("  play-store-icon-512.png: 512x512 (opaque)")


if __name__ == "__main__":
    main()
