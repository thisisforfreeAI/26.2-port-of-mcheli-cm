"""Original wreck textures: charred skin, ember glow and ground scars. Standard library only, no source images.

m8_wreck_char.png    64x64 tileable soot and blistered paint; transparent pits let surviving paint show.
m8_wreck_embers.png  64x64 tileable glowing cracks and spots on transparent black (drawn full bright).
m8_wreck_ground.png  192x64 atlas: burn scar, skid groove (tileable along its length), fuel pool.
"""
from pathlib import Path
import math
import random
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mcheli_ce_port/textures/effect'


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)


def write(name, width, height, pixel):
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            r, g, b, a = (max(0, min(255, int(round(c)))) for c in pixel(x, y))
            rows.extend((r, g, b, a))
    (ROOT / name).write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', width, height, 8, 6, 0, 0, 0))
                              + chunk(b'IDAT', zlib.compress(bytes(rows), 9)) + chunk(b'IEND', b''))


def lattice(seed, period):
    rnd = random.Random(seed)
    return [[rnd.random() for _ in range(period)] for _ in range(period)]


def smooth(t):
    return t * t * (3 - 2 * t)


def value(grid, x, y, period):
    """Tileable value noise sampled on a period x period lattice, x and y in lattice units."""
    x0, y0 = int(math.floor(x)) % period, int(math.floor(y)) % period
    x1, y1 = (x0 + 1) % period, (y0 + 1) % period
    fx, fy = smooth(x - math.floor(x)), smooth(y - math.floor(y))
    top = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
    bottom = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
    return top * (1 - fy) + bottom * fy


def fractal(seed, x, y, size, octaves=4):
    """Tileable fractal noise in 0..1 for a size x size texture."""
    total, weight, amp = 0.0, 0.0, 1.0
    for o in range(octaves):
        period = 4 << o
        grid = GRIDS.setdefault((seed, period), lattice(seed * 31 + period, period))
        total += value(grid, x * period / size, y * period / size, period) * amp
        weight += amp
        amp *= 0.5
    return total / weight


GRIDS = {}


def char(x, y):
    n = fractal(1, x, y, 64)
    blister = fractal(2, x, y, 64, 2)
    grain = fractal(3, x, y, 64, 5)
    # Pits where burnt paint flaked away and the paint below survives: about a tenth of the tile.
    if n < 0.31:
        return 0, 0, 0, 0
    edge = min(1.0, (n - 0.31) / 0.05)
    shade = 14 + 34 * grain + (22 if blister > 0.62 else 0)
    ash = grain > 0.76
    if ash:
        return 62 + 30 * grain, 59 + 28 * grain, 55 + 25 * grain, 235 * edge
    # Brown heat tint around blisters, nearly black elsewhere.
    warm = max(0.0, blister - 0.5) * 2
    return shade + 24 * warm, shade + 10 * warm, shade * 0.85, (205 + 50 * n) * edge


def embers(x, y):
    crack = fractal(4, x, y, 64, 3)
    spot = fractal(5, x, y, 64, 4)
    # Thin glowing seams where the crack noise crosses its middle, and a few hot spots.
    seam = max(0.0, 1 - abs(crack - 0.5) / 0.035)
    hot = max(0.0, (spot - 0.72) / 0.12)
    glow = min(1.0, seam * 0.9 + hot)
    if glow <= 0.02:
        return 0, 0, 0, 0
    return 255, 90 + 140 * glow * glow, 20 + 60 * glow ** 3, 255 * min(1.0, glow * 1.4)


def ground(x, y):
    tile, u, v = x // 64, (x % 64 + 0.5) / 64, (y + 0.5) / 64
    if tile == 0:
        # Radial burn scar: black centre, brown rim, ragged ash-speckled edge.
        r = math.hypot(u - 0.5, v - 0.5) * 2
        ragged = 0.78 + 0.22 * fractal(6, x, y, 64, 4)
        if r > ragged:
            return 0, 0, 0, 0
        t = r / ragged
        n = fractal(7, x, y, 64, 5)
        shade = 10 + 40 * t + 20 * n
        alpha = 245 * min(1.0, (1 - t) / 0.25)
        if n > 0.7 and t > 0.3:
            return 110 + 40 * n, 104 + 36 * n, 96 + 30 * n, alpha * 0.8
        return shade + 30 * t * t, shade + 16 * t * t, shade * 0.8, alpha
    if tile == 1:
        # Skid groove, tileable along v: a dark gouged furrow, churned lighter banks, streaks along it.
        across = abs(u - 0.5) * 2
        streak = fractal(8, x * 6, y * 0.25, 384, 4)
        churn = fractal(9, x, y, 64, 5)
        bank = 0.5 + 0.3 * (fractal(10, x * 0.25, y, 64, 3) - 0.5)
        if across > 0.97:
            return 0, 0, 0, 0
        if across < bank:
            # Gouge lines scraped by the airframe, darker towards the middle of the furrow.
            line = 1 if streak > 0.56 else 0
            shade = 10 + 22 * across / bank + 36 * line * streak + 12 * churn
            return shade * 1.08, shade * 0.95, shade * 0.78, 245
        t = (across - bank) / (0.97 - bank)
        clod = churn > 0.6
        shade = (60 if clod else 95) + 70 * churn
        return shade * 1.02, shade * 0.9, shade * 0.74, (230 if clod else 170) * (1 - t * t)
    # Fuel pool: smooth dark pool with a blotchy edge and a faint oily sheen.
    r = math.hypot(u - 0.5, v - 0.5) * 2
    blot = 0.7 + 0.3 * fractal(11, x, y, 64, 3)
    if r > blot:
        return 0, 0, 0, 0
    t = r / blot
    sheen = fractal(12, x, y, 64, 2)
    shade = 8 + 10 * t + (26 if 0.47 < sheen < 0.53 else 0)
    return shade, shade * 0.95 + 2, shade * 1.05 + 3, 235 * min(1.0, (1 - t) / 0.15)


write('m8_wreck_char.png', 64, 64, char)
write('m8_wreck_embers.png', 64, 64, embers)
write('m8_wreck_ground.png', 192, 64, ground)
