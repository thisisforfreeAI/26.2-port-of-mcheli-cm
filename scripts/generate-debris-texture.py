"""Original neutral pixel fleck, tinted as grass, grit, snow, spray or coolant in the renderer."""
from pathlib import Path
import struct
import zlib

def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

rows = ['........', '...#....', '..###...', '..####..', '..###...', '...##...', '...#....', '........']
pixels = bytearray()
for y, row in enumerate(rows):
    pixels.append(0)
    for x, cell in enumerate(row):
        shade = 255 if x < 4 else 190
        pixels.extend((shade, shade, shade, 255 if cell == '#' else 0))
target = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mcheli_ce_port/textures/particle/m8_debris.png'
target.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', 8, 8, 8, 6, 0, 0, 0))
                   + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b''))
