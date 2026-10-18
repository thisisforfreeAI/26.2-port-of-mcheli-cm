# M8 bomb/missile accents and flare glow

This follows the merged bomb-size correction in PR #45. It keeps the pixel artwork, existing
vanilla shader families and the 80 FPS / 12.5 ms target on RTX 3060 8 GB + Ryzen 7 7800X3D at
1920×1080. Hardware acceptance remains open.

## Explosion presentation

Dry enhanced explosions gain a small animated hot core using the existing flash sheet and
additive particle layer. The existing cosmetic weapon profile selects its shape/timing:

| Profile | Hot-core radius cap | Core lifetime | Fire cooling share of puff life |
|---|---:|---:|---:|
| General blast (includes rockets/general missiles) | 1.2 blocks | 4 ticks | 18% |
| Penetrator (AT missile) | 0.8 blocks | 4 ticks | 18% |
| Heavy bomb | 3 blocks | 7 ticks | 35% |
| Incendiary | 3 blocks | 7 ticks | 45% |
| Cannon | No additional core | — | 35% |

Radius scales with existing explosion power before the cap. Rocket/penetrator sparks last
5–7 ticks and have a tighter vertical spread; broad-blast sparks retain 7–11 ticks. The existing
soft glow lasts five ticks for general/penetrator impacts, while broad blasts retain their
longer glow. Smoke lifetime, emission timing, motion and damage remain controlled by the
existing emitter. The larger bomb smoke/ground footprint from PR #45 remains in place.

Bomb/incendiary explosions of power 3 or greater can throw six outward dust puffs on Balanced
or ten on High. Pressure 1 halves the count; Low and pressure 2–3 suppress them. Dust uses the
existing smoke sheet, sampled material tint and cached ambient light, with no heat. A single
loaded column is searched downward (at most thirteen blocks examined); water, leaves, surfaces
above the blast and airbursts beyond that search produce no dust. The puffs last 18–25 ticks,
reach at most a 0.8-block quad radius, and perform no collision or rotor-wind queries.

Core, dust and sparks share smoke admission and live limits. Coverage admission uses their
maximum radius, leaving the independent weapon-flash reserve available. The core is also shed
on Low and pressure 2–3; existing primary smoke/heat cues remain. New dust/spark placement uses
an independent cosmetic RNG. Scaling/timing choices add no world-RNG draws. Classic, water
explosions and the vanilla-explosion option skip the additional core/dust accents.

The general blast profile also covers fallback explosion calls: the existing packet does not
identify every missile subtype separately. No new protocol or gameplay behavior is introduced.

## Flare presentation and cost

Enhanced burning flares retain their quarter-block cube, now tinted warm white at full
brightness. The textured warm halo grows from half-size 0.65 to 1.25 blocks and has a brighter
baked core. A soft additive disc extends to radius 1.55. Both use restrained 0.88–1.0 flicker,
with a different phase per entity. No particle light source or dynamic world light is created.
Chaff retains its original unlit cube and receives no halo.

Entity halos have a separate per-view weighted allowance because particle live limits do not
apply to entity geometry:

| Quality | Weighted halo allowance | Pressure 1 | Pressure 2–3 | Range |
|---|---:|---:|---:|---:|
| Low | 0 | 0 | 0 | — |
| Balanced | 24 | 12 | 0 | 64 blocks |
| High | 48 | 24 | 0 | 96 blocks |

Each visible halo costs at least one unit and up to sixteen at close range, using the existing
coverage estimate. Thus at most 24/48 halos can be admitted in one extracted view. Each admitted halo
adds one four-vertex textured quad and one cached-rim 16-segment additive disc. No per-vertex
trigonometry or new pipeline is added. Rejected halos retain the bright physical flare cue;
Classic uses the original world-lit cube. Countermeasure movement, lifetime and targeting are
unchanged.

## Native validation scene

Use the disposable flat-world setup in [M8_PIXEL_EFFECTS.md](M8_PIXEL_EFFECTS.md). The opt-in
scene clears entities and changes blocks/time in that isolated world:

```sh
./gradlew runClient -Dmcheli.probeImpactFlare=true \
  -I scripts/visual-probe.init.gradle --no-configuration-cache
```

Set render distance to four chunks for the recorded workload. Add
`-Dmcheli.probeBackend=OPENGL` for the other backend. The probe requires 1920×1080 and checks
the core's shared smoke limit, additive layer and expiry. It captures bomb and general/AT
missile impacts, daytime/nighttime flares with chaff, Classic at night and Low under load.
Every rendered view checks halo allowances. Two-flare scenes must admit halos in enhanced mode;
Classic/Low must admit none. The stress scene holds 48 real client flare entities and repeats
three overlapping bomb/missile impacts every thirty ticks. These entities are stationary only
in the disposable probe; production physics is unchanged.

The last three phases compare empty, fixed-detail stress and adaptive stress. Frame intervals
are collected after warmup and without screenshots during those phases. Adaptive software
overload must reach pressure 2 or greater. Raw CSVs are summarized with
`scripts/summarize-effect-frames.py`; a software-renderer FPS result does not certify target-PC
combat performance. Development probe classes are excluded from normal builds.

## Recorded results

Both native 1920×1080 runs pass on Mesa 25.0.7 / LLVM 19.1.7: Vulkan lavapipe and OpenGL 4.5
llvmpipe. Day/night scenes admit two flare halos; Classic/Low admit zero. Both fixed stress
scenes reach a maximum of 24 halos, and both adaptive scenes reach pressure 3 while retaining
the per-view allowance checks. The core expires after seven ticks and uses the shared smoke
limit and additive layer. Each run saves and shuts down cleanly.

Native captures (actual renderer output):

- [Vulkan bomb hot core](screens/m8-impact-vulkan-bomb.png) and
  [outward dust / growing smoke](screens/m8-impact-vulkan-dust.png).
- [Vulkan night flares with unlit chaff between them](screens/m8-impact-vulkan-flare-night.png).
- [OpenGL daylight flares](screens/m8-impact-opengl-flare-day.png),
  [AT-missile impact](screens/m8-impact-opengl-penetrator.png), and
  [Classic night control](screens/m8-impact-opengl-classic-night.png).

The isolated profile captures are staggered in the current probe so one impact cannot spend
another's shared admission allowance. The stress phases intentionally overlap all three
profiles. Frame samples use four chunks, VSync off, a 120 FPS cap, and the same Balanced scene;
no build or test process ran concurrently. Raw data and summaries:
[Vulkan](performance/m8-impact-lavapipe/summary.json),
[OpenGL](performance/m8-impact-llvmpipe/summary.json).

| Software backend | Empty median / p95 ms | Fixed stress median / p95 ms | Adaptive stress median / p95 ms |
|---|---:|---:|---:|
| Vulkan lavapipe | 72.39 / 100.14 | 102.02 / 160.38 | 100.83 / 164.63 |
| OpenGL llvmpipe | 76.01 / 108.11 | 99.51 / 199.99 | 97.90 / 146.44 |

These short software samples miss the 12.5 ms target even when empty. Tail variation prevents
claiming a reliable FPS gain from these runs. They demonstrate the extra rendering cost and
that overload feedback works, not 80 FPS hardware acceptance. Target-PC combat/flight testing
remains required.

The normal `build` passes **187 JUnit tests** with zero failures, and its jar includes the new
effects while excluding all visual probes. The generator reproduces all four effect assets
byte for byte. As in earlier M8 checks, an external local init script excludes optional JEI API
sources/dependency because its Maven host is blocked here; no repository build configuration or
JEI source was changed. A full JEI-enabled release build remains unverified.
