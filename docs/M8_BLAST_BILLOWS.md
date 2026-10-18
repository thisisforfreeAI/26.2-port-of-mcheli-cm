# M8: smoke volume and blast motion

Large enhanced dry explosions now build a connected cloud around the impact. At Balanced,
8 of the existing 12 primary requests form smoke: four low overlapping billows and four
staggered upper billows. Four faster fire pockets expand within that cloud, then cool into
smoke. The [previous blend](M8_EXPLOSION_BLEND.md) spent most requests on fire lobes that
separated from the impact.
Missiles, rockets, penetrators and bombs below power 4 retain the familiar growing smoke puffs.

## Presentation

- Base smoke expands over 18 ticks and settles into slow lateral curls close to the impact.
  Upper billows begin 3–10 ticks later, expand over 26 ticks, and rise at different speeds.
- Fire starts compact and expands over six ticks. Its shorter outward travel keeps it inside
  the developing cloud; staggered heat durations reveal and hide orange pockets. Cooled fire
  particles remain as smoke instead of vanishing with the flash. Fire retains full local heat
  through its initial hot phase, then cools to ambient-lit smoke; it does not emit world light.
- The additive flash expands from one-quarter to full size with a fast, decelerating curve.
- Ground dust uses the existing material-tinted wash sheet with a stronger impulse, greater
  width and rapid thinning. A separate particle keeps helicopter rotor wash unchanged.
- Existing curved ember arcs remain available under their existing optional-detail rules.

## Cost and boundaries

Primary caps remain Low 6 / Balanced 12 / High 18, divided by adaptive pressure. Smoke takes
roughly two-thirds of those requests, leaving at least one fire pocket. Smoke is admitted first
so a thinned bomb still has a supporting cloud. Smoke-led small weapons retain their emitter.

All particles, including delayed billows, are allocated and admitted at impact time against the
shared smoke live count and final-radius coverage budget. There is no deferred spawn queue.
Billow lifetimes, including delay, are capped at 128 ticks. Larger smoke and longer persistence
can increase fill cost and live counts; unchanged request caps do not imply unchanged GPU cost.
Adaptive detail, distance thinning, coverage admission and the existing optional-effect gates
remain active. Primary lobes no longer receive an additional pressure stride after their
request count has already been divided by pressure. Distance/vanilla particle thinning still
applies, and the shared per-tick weighted cap is still reduced under load. Secondary effects
retain the extra pressure stride. This prevents double thinning of the primary cloud without
granting it a separate reserve.

Ground dust retains the single loaded-column search of at most 13 block states, water/leaf
rejection, Low omission and pressure >=2 omission. Dust requests remain six at Balanced and ten
at High. No new textures, shaders, render pipelines, collision scans or entity searches are
added. Gameplay damage, destruction, Classic effects and underwater effects keep their paths.

## Validation

- Final normal release build: 201 JUnit tests passed, no failures/errors/skips. The production
  JAR contains the new blast dust class and excludes all opt-in scene drivers.
- Dedicated GameTest server: all 234 required tests passed on the integrated M6/main base.
- Full native Vulkan/lavapipe and OpenGL/llvmpipe scenes passed at 1920×1080: Classic control,
  power-12 bomb, power-2 bomb, power-4 missile, night incendiary, empty scene and overlapping
  fixed/adaptive explosions. The driver checks shared live limits, delayed motion, slower smoke
  growth, low-base retention and expiration at the hard lifetime cap.
- Final scenes include the heat curve and primary-admission correction. No concurrent builds
  or tests ran during the frame samples. Missing OpenAL hardware disables sound in this
  environment; no renderer exceptions occurred in either passing run.
- Builds use the external `/tmp/m8-without-jei.init.gradle` because this environment's network
  policy blocks the optional JEI Maven host. Repository JEI sources and dependencies are
  unchanged; full JEI compatibility is not established by these local checks.

Run the scene with `runClient -Dmcheli.probeExplosionStructure=true -I scripts/visual-probe.init.gradle`
(and `-Dmcheli.probeBackend=OPENGL` for OpenGL). The opt-in driver uses the disposable M6Smoke
world in `.run/m8-preview`; it is excluded from normal builds. The quicker preview flag covers
only a large bomb and is not used for the results below.

### Native captures

Actual in-game screenshots; random billow orientations differ between runs. These are not
concept images. The captures show the probe's approximate tick ages.

| Capture | Scene |
|---|---|
| [Vulkan fire](screens/m8-billows-vulkan-fire.png) | Large bomb, age 10 |
| [Vulkan smoke](screens/m8-billows-vulkan-smoke.png) | Large bomb, age 40 |
| [OpenGL night](screens/m8-billows-opengl-night.png) | Incendiary, age 10 |
| [OpenGL missile](screens/m8-billows-opengl-missile.png) | Earlier smoke-led missile, age 10 |

### Software frame samples

Mesa software rendering, four render chunks, 1080p, VSync off, 120 FPS cap. The fixed/adaptive
scenes emit three explosions every 25 ticks. These are frame-interval samples, not isolated
particle timings or measurements on the target GPU. Raw CSVs and nearest-rank summaries are
in [lavapipe](performance/m8-billows-lavapipe/summary.json) and
[llvmpipe](performance/m8-billows-llvmpipe/summary.json).

| Backend | Scene | Frames | Median ms | p95 ms |
|---|---|---:|---:|---:|
| Vulkan | empty | 88 | 68.935 | 93.728 |
| Vulkan | fixed | 71 | 90.044 | 100.715 |
| Vulkan | adaptive | 76 | 80.495 | 105.449 |
| OpenGL | empty | 82 | 72.999 | 125.990 |
| OpenGL | fixed | 63 | 88.019 | 161.775 |
| OpenGL | adaptive | 70 | 85.938 | 115.602 |

At the end-scene snapshot, fixed/adaptive live particles were 79/25 on Vulkan and 77/27 on
OpenGL, with adaptive pressure 3. The retained primary cloud is more substantial than the
previous doubly-thinned effect. These snapshots are not average counts. Vulkan's adaptive
p95 was slightly worse than fixed despite fewer particles; this run does not establish a
consistent frame-time improvement.

All software scenes, including empty controls, missed 12.5 ms. Hardware acceptance remains
**80 FPS at 1080p on RTX 3060 8 GB / Ryzen 7 7800X3D**. Larger smoke and longer persistence
need a real combat-scene check on that PC before the target can be considered met.
