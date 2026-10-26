"""Author the small deterministic pixel atlas used by vehicle impact overlays (stdlib only)."""
import math
from pathlib import Path
import struct
import zlib

SIZE = 32
width, height = SIZE * 4, SIZE * 2
pixels = bytearray(width * height * 4)
for tile in range(8):
    for y in range(SIZE):
        for x in range(SIZE):
            px, py = (x + .5 - 16) / 15, (y + .5 - 16) / 15
            r, angle = math.hypot(px, py), math.atan2(py, px)
            noise = ((x * 13 + y * 31 + tile * 17) % 23) / 23
            rgba = (0, 0, 0, 0)
            if tile < 4:
                edge = .47 + .12 * math.sin(angle * 5 + tile) + .08 * math.cos(angle * 9 - tile)
                shape = math.hypot(px, py * 1.4)
                if shape < edge:
                    gray = int(135 + noise * 40) if shape > edge - .15 and py > -.3 else int(38 + noise * 22)
                    rgba = (gray, gray, gray, 230)
                elif shape < edge + .18:
                    rgba = (28, 27, 25, int(95 * (1 - (shape - edge) / .18)))
            elif tile < 6:
                edge = .73 + .12 * math.sin(angle * 7 + tile) + .08 * math.cos(angle * 11)
                if r < edge:
                    alpha = min(210, int(250 * (edge - r) / .35))
                    gray = int(25 + noise * 16)
                    if r < .23 + .06 * math.sin(angle * 6) and py > 0:
                        gray, alpha = int(115 + noise * 35), 225
                    rgba = (gray, int(gray * .94), int(gray * .84), alpha)
            else:
                # Fine branching cracks, leaving most of the glass clear.
                for branch in range(7 if tile == 6 else 13):
                    a = branch * math.tau / (7 if branch < 7 else 13) + .18 * math.sin(branch * 4 + 6)
                    along, across = px * math.cos(a) + py * math.sin(a), -px * math.sin(a) + py * math.cos(a)
                    bend = .035 * math.sin(along * 16 + branch)
                    if 0 < along < .48 + .3 * ((branch * 7 + 6) % 5) / 4 and abs(across - bend) < .025:
                        rgba = (170, 190, 198, 190)
                    if .25 < along < .55 and abs(across - bend - (along - .25) * .42) < .018:
                        rgba = (130, 155, 165, 150)
                if r < .09:
                    rgba = (55, 65, 70, 160)
            at = ((tile // 4 * SIZE + y) * width + tile % 4 * SIZE + x) * 4
            pixels[at:at + 4] = bytes(rgba)

def chunk(kind, data):
    return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data))

raw = b''.join(b'\0' + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', width, height, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
target = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mcheli_ce_port/textures/effect/vehicle_impacts.png'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(png)
