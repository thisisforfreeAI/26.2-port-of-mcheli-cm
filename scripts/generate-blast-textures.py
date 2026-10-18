#!/usr/bin/env python3
"""Original pixel soot/disturbed-ground masks; standard library only, no source images."""
from pathlib import Path
import math
import random
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mcheli_ce_port/textures/effect'
ROOT.mkdir(parents=True, exist_ok=True)


def png(path, pixels, side):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    rows = b''.join(b'\0' + bytes(pixels[y*side*4:(y+1)*side*4]) for y in range(side))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', side, side, 8, 6, 0, 0, 0))
                    + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


for kind, seed in [('impact', 42), ('blast', 75), ('burn', 101)]:
    rng = random.Random(seed)
    side = 128
    noise = [[rng.random() for x in range(64)] for y in range(64)]
    pixels = []
    for y in range(side):
        for x in range(side):
            dx, dz = (x + .5 - side/2)/(side/2), (y + .5 - side/2)/(side/2)
            angle = math.atan2(dz, dx)
            radius = math.hypot(dx, dz)
            grain = noise[y//2][x//2]
            rim = .78 + .09*math.sin(angle*7+seed) + .05*math.sin(angle*13) + .08*(grain-.5)
            q = radius / rim
            if q >= 1:
                pixels.extend((0, 0, 0, 0)); continue
            edge = min(1, max(0, (1-q)/.22))
            core = max(0, 1-q/.64)
            if kind == 'burn':
                value = 16 + grain*32 + (1-core)*20
                if grain < .18 and q > .35: value = 135 + grain*160
                alpha = edge*(.82 + core*.15)
            else:
                # Broken black centre, dusty/pocked rim, narrow radiating fracture chips.
                crack = abs(math.sin(angle*(9 if kind == 'impact' else 13) + radius*2.7)) < .10 + grain*.06
                value = 24 + (1-core)*165 + (grain-.5)*65
                if crack and q < .78: value *= .28
                alpha = edge*(.76 + core*.20)
                if grain > .75 and q > .42: alpha *= .18
                if grain < .14 and q > .35: value = 218; alpha *= .9
            value = max(0, min(255, int(value)))
            pixels.extend((value, value, value, int(max(0, min(1, alpha))*255)))
    png(ROOT/f'm8_{kind}_mark.png', pixels, side)
