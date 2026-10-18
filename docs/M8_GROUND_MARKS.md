# M8 weapon-specific ground marks

Explosion marks change the visible colour and pixel texture of surviving ground: compact cannon
pockmarks, disturbed soil/stone around ordinary blasts, broader heavy-bomb footprints and sooty
incendiary patches. They follow terrain left by the existing explosion damage. This feature does
not replace blocks, change blast damage, dig additional craters or alter saved terrain.

## Weapon and material response

The server derives a cosmetic family from the projectile's actual weapon definition. The existing
explosion-effect packet carries that enum to clients. Explosion size scales the footprint within
quality caps; these are game-scale visual profiles, not a separate real-world ballistics model.

| Weapon definition | Ground appearance |
|---|---|
| `machinegun1`, `machinegun2`, `railgun` | Compact dark pockmark and chipped/disturbed rim |
| Rockets, general explosives, fallback events | Medium irregular blast scorch and scattered soil/grit |
| `bomb`, `cas` | Wider disturbed ground and darker broken centre |
| `atmissile` | Concentrated penetration/scorch footprint |
| Flaming, fuel-air, fire-container or NTM-fire definitions | Broad black-brown char with ash speckling; takes precedence |

Soil, grass and wood receive an earthy tint; stone stays neutral grey, sand gets a warmer dusty
tint, and snow/ice use pale dirty ash. Three original grayscale RGBA textures provide broken
edges, pitting, grit and scorch while preserving some of the base block texture. The generator
is `python3 scripts/generate-blast-textures.py`, using the standard library and no source images.

## Terrain fitting and lifetime

Projection waits two client ticks for explosion block updates. Each candidate column samples
loaded block shapes beneath the blast, fits clipped texture pieces to their upward surfaces,
and checks visibility from the explosion. Airbursts outside ground reach, water, leaf surfaces,
occluded surfaces and unsupported thin geometry are rejected. Slabs and ordinary stepped
terrain use their actual surface heights. It does not load chunks.

Marks last 60/120/180 seconds on Low/Balanced/High and fade over the final ten seconds. They are
client-local and are cleared on world changes/disconnect; reconnecting does not recreate old
marks. Nearby repeated hits replace overlapping marks. A periodic check removes pieces whose
blocks changed or became covered and refreshes their ambient light. Terrain edits therefore do
not leave floating textures.

Classic presentation hides/clears these enhanced marks. `effects.blastMarks = false` disables
them independently. The underlying explosion damage settings retain control of actual craters.

## Graphics budget

| Quality | Maximum radius | Live marks | Total surface quads | Draw range |
|---|---:|---:|---:|---:|
| Low | 2.5 blocks | 8 | 128 | 64 blocks |
| Balanced | 4 blocks | 24 | 384 | 96 blocks |
| High | 5 blocks | 48 | 768 | 128 blocks |

The pending queue holds at most sixteen events; at most two are projected per tick, each capped
at 256 surface pieces and a sixteen-block downward search. Old marks are evicted as complete
footprints to stay within both live limits. Adaptive pressure 2–3 declines new ground marks.
Surface checks run every ten ticks; rendering performs no world/block queries. Up to three
geometry submissions per frame share existing vanilla entity shaders and light maps. Vertices
are emitted relative to an integer camera origin to preserve precision far from world zero.
There are no extra scene copies, dynamic lights or full-screen passes.

This is part of the 80 FPS goal for RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p. The limits bound
M8's work; they do not certify total FPS. Target-PC combat measurements remain required.

## Protocol and validation

Explosion effects now include a cosmetic family enum. Network version is **21** (previously 20),
so clients and servers must use matching builds. Damage calculation, existing explosion block
selection/destruction and other gameplay parameters are unchanged. The old Java effect-call
and payload-construction overloads default to the general blast profile.

Use the disposable flat-world setup in [M8_PIXEL_EFFECTS.md](M8_PIXEL_EFFECTS.md), then run:

```sh
./gradlew runClient -Dmcheli.probeGround=true \
  -I scripts/visual-probe.init.gradle --no-configuration-cache
```

Add `-Dmcheli.probeBackend=OPENGL` for the other backend. This opt-in 1920×1080 scene shows weapon
families, grass/stone/sand/snow, stepped terrain, dry airbursts and water, a repeated-hit budget
stress, terrain removal, and an empty/marked frame-time comparison. It checks live limits,
rejects marks under unreachable airbursts/water, verifies that removed surfaces lose their
marks, and verifies that Classic clears them. The marked frame-time phase holds a dense set of
marks, with adaptive detail disabled to measure the explicit ceiling. Screenshots precede the
performance phases. Raw frame intervals can be summarized using
`scripts/summarize-effect-frames.py`. Development probes remain excluded from normal builds.

The Vulkan 1920×1080 scene passes its projection, material, step, airburst/water rejection,
terrain invalidation, Classic reset and budget assertions under Mesa 25.0.7 lavapipe / LLVM 19.1.7.
The repeated-hit scene retains nine marks / 354 quads; the steady marked phase retains ten marks /
360 quads against an explicit ceiling of 24 / 384. It saves and stops cleanly.

The isolated Vulkan frame sample compares an empty scene with that 360-quad marked scene,
adaptive detail off, at 1080p / four chunks / VSync off / 120 FPS cap. No build/test work ran
concurrently. Committed [raw intervals and summary](performance/m8-ground-lavapipe/summary.json):

| Scene | Median ms | p95 ms | p99 ms |
|---|---:|---:|---:|
| Empty | 59.26 | 98.29 | 152.76 |
| 360 surface quads | 60.15 | 76.48 | 96.50 |

The median differs by about 0.9 ms in this short software run. Tail variation prevents treating
these samples as an isolated cost estimate or evidence of improved FPS. Both miss 12.5 ms before
representative hardware testing. The OpenGL scene also passes at 1920×1080 on Mesa llvmpipe (OpenGL 4.5), including the same
354/360-quad ceilings and terrain/Classic clearing. Native captures:
[weapon families](screens/m8-ground-opengl-families.png),
[grass, stone, sand and snow](screens/m8-ground-opengl-materials.png),
[stepped terrain](screens/m8-ground-opengl-steps.png).
Its [raw frame summary](performance/m8-ground-llvmpipe/summary.json) reports median/p95
63.32/88.15 ms empty versus 69.30/93.24 ms marked. This software run shows additional rendering
cost and still does not meet 80 FPS; hardware combat profiling remains open.

The combined `build runGameTestServer` passes **180 JUnit tests and 209 required GameTests**
with zero failures. The normal jar includes all three textures and excludes development probes;
the texture generator reproduces the committed assets byte for byte. Local builds use the
documented external optional-JEI exclusion because its Maven host is blocked here. The concept preview is
an illustration of the intended style; native renderer captures are identified separately.
