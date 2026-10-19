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


def noise2(x, y, seed=7):
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    def value(a, b):
        v = ((a * 374761393 + b * 668265263 + seed * 1274126177) & 0xffffffff)
        v = ((v ^ (v >> 13)) * 1274126177) & 0xffffffff
        return ((v ^ (v >> 16)) & 65535) / 65535
    a = value(ix, iy) * (1 - fx) + value(ix + 1, iy) * fx
    b = value(ix, iy + 1) * (1 - fx) + value(ix + 1, iy + 1) * fx
    return a * (1 - fy) + b * fy


def blast_cloud(x, y):
    # Twelve evolving fire frames, then four neutral smoke frames, all in one 256px sheet.
    cell = 64
    frame = x // cell + y // cell * 4
    px, py = (x % cell - 31.5) / 31, (y % cell - 31.5) / 31
    t = min(1, frame / 11)
    coarse = noise2(px * 3.1 + 5 - t * .65, py * 3.1 + 7 + t * .9)
    detail = noise2(px * 8 + 7 + t * 1.3, py * 8 + 4 + t * 2, 23)
    depth = -1
    # Billows separate and stretch as hot gas rolls outward; the outline is never a round disc.
    for i, (cx, cy, rx, ry) in enumerate([(-.30, -.16, .51, .55), (.28, -.22, .48, .50),
                                         (-.30, .32, .42, .38), (.30, .28, .47, .42), (0, 0, .57, .55)]):
        cx += math.sin(i * 2.1 + t * 2.2) * .09 * t
        cy -= t * .07 * (i % 2)
        depth = max(depth, 1 - ((px - cx) / rx) ** 2 - ((py - cy) / ry) ** 2)
    depth += (coarse - .5) * .48 + (detail - .5) * .16
    if depth <= 0:
        return (0, 0, 0, 0)
    alpha = min(240, round(min(1, depth * 5) * 15) * 16)
    shade = max(0, min(1, .48 - py * .20 - px * .12 + (coarse - .5) * .55 + (detail - .5) * .25))
    value = min(200, round((30 + shade * 125 + max(0, frame - 12) * 12) / 8) * 8)
    if frame >= 12:
        return (value, value, value, alpha)
    # Coherent hot pockets split around rolling charcoal lobes. Late frames leave only amber windows.
    warp_x = px + (coarse - .5) * .28
    warp_y = py + (detail - .5) * .22
    hot = 0
    for i, (cx, cy, rx, ry) in enumerate([(-.30, .12, .34, .42), (.19, -.21, .34, .33),
                                         (.35, .27, .27, .30), (-.12, -.42, .23, .26)]):
        cx += math.sin(i * 2.3 + t * 2.7) * t * .18
        cy -= t * .12
        rx *= 1 + t * .25
        ry *= 1 - t * .25
        pocket = 1 - ((warp_x - cx) / rx) ** 2 - ((warp_y - cy) / ry) ** 2
        hot = max(hot, pocket)
    occlusion = noise2(px * 5 + 2 - t * .5, py * 5 + 3 + t * 1.7, 43)
    hot = max(0, min(1, hot * .85 + detail * .45 - t * .42 - occlusion * (.15 + t * .7)))
    temperature = min(1, hot * 1.55)
    palette = [(88, 40, 24), (181, 55, 13), (255, 128, 19), (255, 209, 82), (255, 253, 224)]
    f = temperature * (len(palette) - 1)
    i = min(len(palette) - 2, int(f)); blend = f - i
    colour = tuple(palette[i][c] * (1 - blend) + palette[i + 1][c] * blend for c in range(3))
    exposure = min(1, hot * 5)
    return (*(round(value * (1 - exposure) + c * exposure) for c in colour), alpha)


def blast_flash(x, y):
    frame = x // SIZE + y // SIZE * 4
    px, py = (x % SIZE - 15.5) / 15, (y % SIZE - 15.5) / 15
    angle = math.atan2(py, px)
    edge = .72 + .12 * math.sin(angle * 5 + .7) + .06 * math.sin(angle * 11 + frame * .1)
    depth = 1 - math.hypot(px, py) / edge
    if depth <= 0:
        return (0, 0, 0, 0)
    # Uneven soft orange edge and a compact white-hot core, not a circular halo.
    core = max(0, min(1, depth * 2.5 - frame * .08))
    return (255, round(150 + core * 105), round(40 + core * 205),
            round(min(1, depth * 4) * (1 - frame / 10) * 255))


def ground_dust(x, y):
    # Four compact 64px cells: broad ragged billows, brighter than carbon smoke for tinting.
    cell = 64
    frame = x // cell + y // cell * 2
    px, py = (x % cell - 31.5) / 31, (y % cell - 31.5) / 31
    depth = -1
    for i, (cx, cy, rx, ry) in enumerate([(-.56, .08, .34, .30), (-.18, -.05, .42, .40),
                                         (.22, .02, .42, .34), (.59, .11, .31, .24),
                                         (.06, -.30, .25, .23)]):
        cx += math.sin(i * 2.1 + frame * .5) * .035
        cy += math.cos(i * 1.7 + frame * .6) * .025
        depth = max(depth, 1 - ((px - cx) / rx) ** 2 - ((py - cy) / ry) ** 2)
    coarse = noise2(px * 4 + 5 - frame * .12, py * 4 + 6, 53)
    detail = noise2(px * 12 + 2, py * 12 + 3 - frame * .16, 71)
    depth += (coarse - .5) * .35 + (detail - .5) * .18
    if depth <= 0:
        return (0, 0, 0, 0)
    alpha = min(192, round(min(1, depth * 3) * (.7 + detail * .3) * 12) * 16)
    shade = max(0, min(1, .6 - py * .3 + (coarse - .5) * .4 + (detail - .5) * .2))
    value = min(255, round((190 + shade * 65) / 8) * 8)
    return (value, value, value, alpha)


def dirt_jet(x, y):
    cell = 64
    frame = x // cell + y // cell * 2
    px, py = (x % cell - 31.5) / 31, (y % cell - 31.5) / 31
    t = frame / 3
    noise = noise2(px * 16 + 4, py * 12 + 3 - t, 83)
    center = math.sin(py * 5 + t * .7) * .04
    width = .04 + (1 - py) * .045 + t * .035
    depth = 1 - abs(px - center) / width
    end = max(0, min(1, (1 - abs(py) / .90) * 5))
    alpha = max(0, min(1, depth * 2)) * end * (.55 + noise * .45)
    shade = round((195 + noise * 60) / 8) * 8
    return (min(255, shade),) * 3 + (min(224, round(alpha * 14) * 16),)


if __name__ == '__main__':
    # Compact sheets avoid a dropped slot for wide strips in the pinned 26.2 stitcher.
    png('m8_smoke.png', SIZE * 4, SIZE * 2, smoke)
    png('m8_splash.png', SIZE * 4, SIZE * 2, splash)
    png('m8_flash.png', SIZE * 4, SIZE * 2, flash)
    png('m8_flare_halo.png', SIZE, SIZE, halo)
    png('m8_wash.png', SIZE * 2, SIZE * 2, wash)
    png('m8_wash_ripple.png', SIZE, SIZE, ripple)
    png('m8_dirt_jet.png', 128, 128, dirt_jet)
    png('m8_ground_dust.png', 128, 128, ground_dust)
    png('m8_blast_cloud.png', 64 * 4, 64 * 4, blast_cloud)
    png('m8_blast_flash.png', SIZE * 4, SIZE * 2, blast_flash)
