#!/usr/bin/env python3
"""Summarize opt-in M8 probe CSVs without third-party packages. No GPU FPS is inferred."""
import argparse
import csv
import json
import math
from pathlib import Path
import statistics


def summarize(path, target):
    with path.open(newline="") as source:
        values = [float(row["interval_ms"]) for row in csv.DictReader(source)]
    if not values or any(not math.isfinite(v) or v <= 0 for v in values):
        raise ValueError(f"Empty or invalid frame sample: {path}")
    values.sort()
    percentile = lambda p: values[math.ceil(len(values) * p) - 1]
    return {
        "scene": path.stem.removeprefix("m8-"),
        "frames": len(values),
        "mean_ms": round(statistics.mean(values), 3),
        "median_ms": round(statistics.median(values), 3),
        "p95_ms": round(percentile(.95), 3),
        "p99_ms": round(percentile(.99), 3),
        "average_fps": round(1000 / statistics.mean(values), 2),
        "within_budget_percent": round(100 * sum(v <= 1000 / target for v in values) / len(values), 2),
        "p95_within_target": percentile(.95) <= 1000 / target,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", type=Path, nargs="+")
    parser.add_argument("--target-fps", type=int, default=60)
    parser.add_argument("--require-target", action="store_true", help="Fail when any scene's p95 exceeds the frame budget")
    args = parser.parse_args()
    if args.target_fps <= 0:
        parser.error("target-fps must be positive")
    results = [summarize(path, args.target_fps) for path in args.files]
    print(json.dumps({"target_fps": args.target_fps, "frame_budget_ms": 1000 / args.target_fps, "results": results}, indent=2))
    if args.require_target and not all(row["p95_within_target"] for row in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
