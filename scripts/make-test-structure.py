#!/usr/bin/env python3
"""Writes the GameTest structure mcheli_ce_port:flight_area (docs/M3_PLAN.md section 8.3).

A 24 x 48 x 24 space with a stone floor at y 0 and air above. The tests add their own sponge
pads and water. Run from the repository root: python3 scripts/make-test-structure.py
"""
import gzip
import struct

SIZE_X, SIZE_Y, SIZE_Z = 24, 48, 24
DATA_VERSION = 4903  # Minecraft 26.2
OUT = "src/main/resources/data/mcheli_ce_port/structure/flight_area.nbt"


def name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag_int(key, v):
    return b"\x03" + name(key) + struct.pack(">i", v)


def tag_string(key, v):
    return b"\x08" + name(key) + name(v)


def tag_int_list(key, values):
    return b"\x09" + name(key) + b"\x03" + struct.pack(">i", len(values)) + b"".join(
        struct.pack(">i", v) for v in values)


def tag_compound_list(key, compounds):
    return b"\x09" + name(key) + (b"\x0a" if compounds else b"\x00") + struct.pack(">i", len(compounds)) + b"".join(
        c + b"\x00" for c in compounds)


palette = [tag_string("Name", "minecraft:stone")]
blocks = [tag_int_list("pos", [x, 0, z]) + tag_int("state", 0) for x in range(SIZE_X) for z in range(SIZE_Z)]
root = (tag_int("DataVersion", DATA_VERSION)
        + tag_int_list("size", [SIZE_X, SIZE_Y, SIZE_Z])
        + tag_compound_list("palette", palette)
        + tag_compound_list("blocks", blocks)
        + tag_compound_list("entities", []))
data = b"\x0a" + name("") + root + b"\x00"
with gzip.GzipFile(OUT, "wb", mtime=0) as f:
    f.write(data)
print("wrote", OUT)
