#!/usr/bin/env python3
"""Summarize the opt-in combat scene's raw frame intervals and tick counters."""
import argparse
import csv
import math
from pathlib import Path
import statistics


def summarize(directory):
    print("| Scene | Frames | Median ms | p95 ms | p99 ms | Frames <=12.5 ms |")
    print("| --- | ---: | ---: | ---: | ---: | ---: |")
    for scene in ("empty", "models", "fixed", "adaptive"):
        path = directory / f"m8-combat-{scene}.csv"
        with path.open() as stream:
            values = sorted(float(row["interval_ms"]) for row in csv.DictReader(stream))
        if len(values) < 48 or any(not math.isfinite(v) or v <= 0 for v in values):
            raise ValueError(f"{path}: requires at least 48 finite positive frame intervals")
        percentile = lambda fraction: values[math.ceil(fraction * len(values)) - 1]
        within = sum(v <= 12.5 for v in values) / len(values) * 100
        print(f"| {scene} | {len(values)} | {statistics.median(values):.2f} | "
              f"{percentile(.95):.2f} | {percentile(.99):.2f} | {within:.1f}% |")
    with (directory / "m8-combat-ticks.csv").open() as stream:
        rows = list(csv.DictReader(stream))
    limits = {"bullets": 64, "fragment_slots": 24, "terrain_reads": 256, "terrain_rays": 32,
              "maintenance": 40, "pending": 16, "active": 2, "tiles": 384, "exhaust": 16}
    for scene in ("empty", "models", "fixed", "adaptive"):
        phase = [r for r in rows if r["phase"] == scene]
        if [int(r["tick"]) for r in phase] != list(range(600)):
            raise ValueError(f"{scene}: incomplete or repeated tick trace")
        peaks = {key: max(int(r[key]) for r in phase) for key in limits}
        if any(peaks[key] > limit for key, limit in limits.items()):
            raise ValueError(f"{scene}: budget exceeded: {peaks}")
        if scene in ("fixed", "adaptive") and (peaks["bullets"] != 64 or peaks["exhaust"] == 0):
            raise ValueError(f"{scene}: missing sustained bullet/exhaust fixture")
        if scene == "fixed" and peaks["fragment_slots"] == 0:
            raise ValueError("fixed: missing surface fragments")
        tail = phase[580:]
        transient = ("particles", "bullets", "pending", "active")
        if any(int(r[key]) for r in tail for key in transient):
            raise ValueError(f"{scene}: transient work did not drain")
        print(f"\n{scene}: peaks {peaks}; transient work drained by tick 580.")
    print("\nFrame intervals include CPU/GPU submission and scheduling. These scripted scenes "
          "do not certify sustained combat FPS or flight behavior on another machine.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="Directory containing m8-combat-*.csv")
    args = parser.parse_args()
    try:
        summarize(args.directory)
    except (OSError, KeyError, ValueError) as error:
        parser.exit(1, f"{error}\n")
