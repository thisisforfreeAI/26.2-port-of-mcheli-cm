#!/usr/bin/env python3
"""Generate original GPLv3 pixel effects. Python standard library only; no input artwork.

The small, shaded lobes are baked once, not evaluated in a shader or during play.
Run from any directory to reproduce the checked-in PNGs byte for byte.
"""
import math
from pathlib import Path
import struct
import zlib

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mcheli_ce_port/textures/particle'
SIZE = 32


def png(name, width, height, sample):
    def chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data))
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend(sample(x, y))
    header = struct.pack('!2I5B', width, height, 8, 6, 0, 0, 0)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header)
                            + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def smoke(x, y):
    frame = x // SIZE + y // SIZE * 4
    x, y = x % SIZE, y % SIZE
    t = frame / 7
    px, py = (x - 15.5) / 14, (y - 15.5) / 14
    # Overlapping rounded lobes with coherent evolution between frames.
    lobes = [(-.28, -.31, .59), (.26, -.21, .57), (-.37, .22, .47),
             (.26, .34, .51), (.0, .04, .65), (.06, -.61, .32)]
    depth, light = -1, 0
    for i, (cx, cy, radius) in enumerate(lobes):
        cx += math.sin(i * 2.1 + t * 1.4) * .07 * t
        cy -= .07 * t
        radius *= 1 - .10 * t
        nx, ny = (px - cx) / radius, (py - cy) / radius
        d = 1 - nx * nx - ny * ny
        if d > depth:
            depth = d
            light = .57 + .27 * max(0, d) ** .5 - .15 * nx - .20 * ny
    if depth <= 0:
        return (0, 0, 0, 0)
    # Six neutral tones retain content tint and read as chunky shaded smoke.
    value = max(92, min(245, round(light * 7) * 32))
    alpha = 112 if depth < .12 else 184 if depth < .3 else 226
    return value, value, value, alpha


def splash(x, y):
    frame = x // SIZE + y // SIZE * 4
    x, y = x % SIZE, y % SIZE
    t = frame / 7
    best = 0
    for i in range(7):
        cx = 16 + math.sin(i * 2.39) * (4 + i * 1.25)
        cy = 15 + math.cos(i * 1.7) * 9 + (t - .5) * (i - 3)
        d = 1 - ((x - cx) / (1.5 + i % 2)) ** 2 - ((y - cy) / (3.0 - t)) ** 2
        best = max(best, d)
    if best <= 0:
        return (0, 0, 0, 0)
    return (220, 239, 255, 216) if best > .4 else (154, 192, 223, 144)


def flash(x, y):
    frame = x // SIZE + y // SIZE * 4
    x, y = (x % SIZE - 15.5) / 14, (y % SIZE - 15.5) / 14
    t = frame / 7
    radius = math.hypot(x, y)
    angle = math.atan2(y, x)
    edge = .62 + .12 * math.cos(5 * angle + t) + .06 * math.sin(9 * angle - t)
    if radius > edge:
        return (0, 0, 0, 0)
    f = radius / edge
    if f < .27 * (1 - .5 * t):
        return (255, 252, 218, 255)
    if f < .52:
        return (255, 219 - int(60 * t), 106 - int(50 * t), 250)
    if f < .79:
        return (255, 147 - int(50 * t), 40, 224)
    return (215, 80, 28, 140)


def halo(x, y):
    x, y = (x - 15.5) / 15, (y - 15.5) / 15
    r = math.hypot(x, y)
    if r > .95:
        return (0, 0, 0, 0)
    ray = max(0, 1 - min(abs(x), abs(y)) * 12) * max(0, 1 - r)
    a = min(1, max(0, 1 - r * 1.4) ** 3 + ray * .24)
    # Bright warm core and a quieter amber edge, still one tiny texture and one quad.
    inner = max(0, 1 - r * 4)
    return (255, round(185 + 60 * inner), round(65 + 140 * inner), round(a * 220))


def wash(x, y):
    frame = x // SIZE + y // SIZE * 2
    x, y = x % SIZE, y % SIZE
    px, py = (x - 15.5) / 15, (y - 15.5) / 15
    depth = -1
    for i in range(5):
        cx = (i - 2) * .3 + math.sin(frame + i * 1.7) * .07
        cy = math.sin(i * 2.3 + frame * .5) * .09
        q = 1 - ((px - cx) / .33) ** 2 - ((py - cy) / (.23 + (i % 2) * .07)) ** 2
        depth = max(depth, q)
    grain = (x * 17 + y * 29 + frame * 11) % 19
    if depth <= 0 or grain < 2 and depth < .5:
        return (0, 0, 0, 0)
    shade = 190 if py > .03 else 224 if depth < .6 else 242
    return (shade, shade, shade, 96 if depth < .25 else 160 if depth < .6 else 208)


def ripple(x, y):
    px, py = (x - 15.5) / 15, (y - 15.5) / 15
    angle = math.atan2(py, px)
    radius = math.hypot(px, py)
    edge = .7 + .035 * math.sin(angle * 7)
    if abs(radius - edge) > .055 or math.sin(angle * 5 + 1) < -.25:
        return (0, 0, 0, 0)
    return (225, 241, 248, 88 if abs(radius - edge) < .025 else 42)


if __name__ == '__main__':
    # Compact sheets avoid a dropped slot for wide strips in the pinned 26.2 stitcher.
    png('m8_smoke.png', SIZE * 4, SIZE * 2, smoke)
    png('m8_splash.png', SIZE * 4, SIZE * 2, splash)
    png('m8_flash.png', SIZE * 4, SIZE * 2, flash)
    png('m8_flare_halo.png', SIZE, SIZE, halo)
    png('m8_wash.png', SIZE * 2, SIZE * 2, wash)
    png('m8_wash_ripple.png', SIZE, SIZE, ripple)
