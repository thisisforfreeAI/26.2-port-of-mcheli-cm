# M8 combined combat acceptance scene

The opt-in `BlastGroundProbe` combat mode exercises the current effects together with
bounded, moving stock bullet models. It supplies repeatable empty/model controls and fixed/
adaptive effects runs, followed by a cooldown without clearing particles. Production rendering,
weapon damage and physics are unchanged; the probe is excluded from normal builds.

## Reproduce

Use a disposable, cheats-enabled flat world named `M6Smoke` in
`.run/m8-preview/saves/M6Smoke`, as described in [the preview setup](M8_PIXEL_EFFECTS.md#repeating-the-preview).
The scene removes non-player entities and changes terrain, weather, time and client effect
settings in that test instance. The Vulkan setup requires `earlyWindowControl = false`.

```sh
./gradlew runClient -Dmcheli.probeCombatSoak=true -I scripts/visual-probe.init.gradle --no-configuration-cache
python scripts/summarize-m8-combat.py .run/m8-preview
```

For OpenGL add `-Dmcheli.probeBackend=OPENGL`. Copy `m8-combat-*.csv` out of the run directory
before switching backends; the next run overwrites them. Run each backend separately, without
another benchmark or build using the same output directory.

The driver fixes the window at 1920×1080, disables VSync and caps at 120 FPS. Each of four
phases lasts 600 client ticks, after initial loading/warmup:

| Phase | Models | Effects |
| --- | --- | --- |
| empty | None | None |
| models | Two stock AH-64s and two F-35As | Engines disabled |
| fixed | Same aircraft | Balanced, adaptive disabled |
| adaptive | Same aircraft | Balanced, adaptive enabled |

Effects run from tick 30 through 399: four M134 bullet fixtures per tick (16-tick lifetime),
three bursts every 25 ticks (power-12 bomb, power-4 rocket, power-10 general missile matching
the AGM-158), twelve flares at ticks 40/140/240/340, engine exhaust,
helicopter rotor wash, muzzle flashes and a scripted smoke trail. Client-only flare fixtures
receive explicit removal at tick 400 because they have no authoritative server removal packet.
The resulting particles continue ticking normally. No particle clearing occurs during cooldown.

The camera and aircraft positions are fixed; the projectile fixtures move across the scene.
This exercises stock renderers and effect entry points, not actual flight, ballistics, damage,
network latency or an addon compatibility matrix. Emitters use their usual random variation.

## Measurements and checks

Raw frame intervals are sampled at render completion during ticks 80–379. Screenshots occur
at tick 60 and cooldown tick 420, outside that window. Four `m8-combat-SCENE.csv` files record
the intervals. `m8-combat-ticks.csv` records all 2,400 phase ticks, including live particles,
bullets/fragments, terrain fitting/maintenance, stored surfaces, engine/wash emissions and
adaptive pressure. The summary reports nearest-rank p95/p99 and the fraction of frames at or
below the 80 FPS target's 12.5 ms budget. It rejects incomplete traces and bounded-work failures.
Fragment telemetry records occupied weak tracker slots (at most 24). Dead or rejected/evicted
particles may leave slots until the next emission or garbage collection; slot occupancy is not
a live-particle count. Cooldown uses the particle engine's actual total, without forcing GC.
Exhaust/wash counters are snapshots of the last eligible emission tick,
not rates or totals; the driver records zero after stopping those sources at tick 400.

Runtime assertions check existing Balanced limits: 64 bullet fixtures, 24 tracked fragments,
256 terrain fitter reads, 32 rays, 40 maintenance visits, 16 pending/two active projections,
384 cached terrain faces, 16 exhaust emissions per world tick, bounded rotor sampling/cache,
64 ground-flash faces per view and flare geometry admission. Direct terrain reads exclude
the internal lookups performed by rays/light sampling, as documented in
[terrain fitting](M8_TERRAIN_BUDGET.md). Transient particles, bullets and terrain
jobs must drain by tick 580. Scorch marks intentionally remain for their normal lifetime.

Frame intervals measure the complete client frame and host scheduling, rather than isolated
GPU time. Empty/model controls provide context; subtracting percentiles does not isolate an
effect's cost. The summary's 12.5 ms column is diagnostic, not an automatic acceptance verdict.

## Native software results

Vulkan/lavapipe and OpenGL/llvmpipe (Mesa 25.0.7 / LLVM 19.1.7), 1920×1080 under Xvfb:

| Backend | Scene | Frames | Median ms | p95 ms | p99 ms |
| --- | --- | ---: | ---: | ---: | ---: |
| vulkan | empty | 134 | 83.66 | 219.30 | 392.23 |
| vulkan | models | 81 | 152.42 | 432.11 | 1245.41 |
| vulkan | fixed | 114 | 108.74 | 246.28 | 360.75 |
| vulkan | adaptive | 148 | 90.84 | 165.34 | 260.74 |
| opengl | empty | 171 | 80.01 | 131.23 | 264.64 |
| opengl | models | 179 | 75.02 | 146.79 | 177.99 |
| opengl | fixed | 149 | 95.40 | 147.87 | 198.84 |
| opengl | adaptive | 142 | 88.68 | 183.84 | 347.40 |

Both backends pass all four scenes and cooldown checks. Each mixed scene reaches 64 bullet
fixtures, at most two active/three pending projections and 244 cached terrain faces. Observed
fitter maxima stay within 193 direct reads, 32 rays and 40 maintenance visits per world tick.
The fixed run reaches 24 flare admission units per view; adaptive pressure reaches level 3.
Particles and terrain jobs drain without clearing them; persistent scorch marks remain.

All sampled software frame intervals exceed 12.5 ms. Host variability is substantial (including
slower model-only intervals than effect intervals); these runs establish no rendering speedup
or hardware FPS result. Raw samples: [Vulkan ticks](performance/m8-combat/vulkan/m8-combat-ticks.csv),
[OpenGL ticks](performance/m8-combat/opengl/m8-combat-ticks.csv); each directory also contains
all four raw frame CSVs. Native runs use the updated graphics on base `40ad679`; the final
build additionally integrates main `aaeebb0` (command/chunk-loading work). The controlled
bullet fixtures skip gameplay ticking and do not exercise that chunk-loading implementation.
JEI is excluded only in local validation through an external init script because its Maven host
is blocked. Repository dependency/JEI files are unchanged. Audio is unavailable headlessly.

## Remaining target-PC acceptance

Run both backends on RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p and retain raw CSV, driver/game
versions, settings, view distance and screenshots. Then verify actual flight and sustained combat,
including the intended addon packs: smoke should retain its shape, flares should remain readable,
bullet bodies should use ambient lighting, and terrain marks should fit destroyed/stepped ground.
Hardware acceptance and final in-battle visual approval remain open until those runs exist.
