# M8: bound terrain fitting during overlapping explosions

Scorch marks and the short ground flash previously completed as many as two full terrain
projections in one client tick. Their draw count was bounded, but each projection could scan
hundreds of columns, and periodic maintenance checked every cached tile at once.

The fitter now resumes across ticks, visiting the impact center before the outer footprint.
It publishes the central surfaces while it works outward. The original radius, UV continuity,
stepped surfaces, material tints, occlusion checks and cached rendering remain in use.
No terrain queries run during geometry submission.

## Shared work limits

All impacts share these allowances per actual client world tick, including resumed jobs:

| Work | Low | Balanced | High |
|---|---:|---:|---:|
| Direct column/state-validation reads | 128 | 256 | 384 |
| Occlusion rays | 16 | 32 | 48 |
| New columns | 24 | 48 | 64 |
| Cached tile maintenance visits | 16 | 40 | 80 |

Direct reads count the fitter's explicit block-state samples, including revalidation of a shape
held across ticks. Rays and light samples have their own internal lookups; they are not included
in that direct-read count. Rays still have a maximum 16-block blast reach. Each admitted new
surface gets one cached light sample; maintenance refreshes light only on eligible valid tiles.

There are at most two active jobs and sixteen pending impacts. Unfinished jobs rotate so a deep
column cannot monopolize fitting. Pending same-family hits within half a block coalesce when
the newer hit is at least as powerful. This avoids repeatedly fitting obsolete copies while
preserving distinct or stronger footprints. Work older than forty ticks expires under overload;
some older cosmetic marks can remain partial or be omitted. Particle cues and gameplay continue.

The center-first cursor shares one precomputed offset table. It retains every column of the
original square footprint, including fractional and negative impact positions. Global cached
quad/mark limits and per-mark shape limits still apply. An unfinished fit reserves its flat
footprint capacity, so staging cannot crowd additional partial patches into the draw cache.
Completed or abandoned fits pay for their actual surviving faces. Cached tiles are checked gradually;
removal of a changed/covered/unloaded surface clears its associated flash geometry.

Flash age still begins at the explosion. A busy queue cannot make a delayed flash replay later.
Expired flash-only work is discarded, and disabling scorch marks stops persistent fitting once
the brief flash has ended. World unload and Classic quality clear all queued/active work.

## Validation and reproduction

`TerrainBudgetProbe` is an opt-in development driver:

```sh
./gradlew runClient -Dmcheli.probeTerrainBudget=true -I scripts/visual-probe.init.gradle
```

Add `-Dmcheli.probeBackend=OPENGL` for the second backend. The scene exercises flat ground,
nested crater steps, slabs/blocks, high airbursts, repeated overlapping impacts, scorch-disabled
flashes, and moving stock M134 projectile models amid repeated bomb/missile effects. Projectile
motion is a controlled client render fixture, not a gameplay ballistics benchmark.

A power-12 Balanced blast keeps the original completed 12×12-block surface patch (144 full
flat tiles). Boundary columns with zero clipped width add no face. The probe verifies completion,
removal after the source ground changes, drained airburst/flash-only queues, and fixed work,
storage and flash-face ceilings. Unit tests cover exact shared allowances, repeated calls in
one world tick, world/clock resets, and complete center-first footprint traversal.

The comparison baseline instruments the previous synchronous fitter with the same driver.
Its flag `mcheli.probeTerrainBaseline` only disables new-budget assertions and labels its CSV;
it does not restore the old implementation. Replay at `fcd9eb73bd44e054b0487063127b13ca22151e10` in a disposable checkout.
Copy the current `scripts/visual-probe.init.gradle` and `TerrainBudgetProbe.java` into that
checkout at the same paths, prepare the disposable `M6Smoke` world, then run
`python scripts/instrument-terrain-baseline.py /path/to/baseline-checkout` from the current
checkout. The script refuses a modified or different baseline source. Run the baseline driver
with `-Dmcheli.probeTerrainBaseline=true`. Raw tick samples and native results are recorded with this follow-up.

The build passes 212 JUnit tests. Vulkan/lavapipe and OpenGL/llvmpipe run at 1920×1080
with Mesa 25.0.7 / LLVM 19.1.7. Seven scenes check the completed flat footprint, crater/slab
fitting, source-block removal, airburst/expired-flash queue drainage, storage/work ceilings and
moving stock projectiles during repeated blasts. The moving fixture peaks at 32 live rounds.

Observed Vulkan peaks per client tick:

| Work / result | Synchronous baseline | Resumable fitter |
|---|---:|---:|
| Overlap direct fitter reads | 1,352 | 193 |
| Overlap occlusion rays | 288 | 32 |
| Overlap cached maintenance visits | 288 | 40 |
| Overlap cached surface faces | 288 | 287 |
| Airburst direct fitter reads | 3,380 | 256 |
| Completed flat-bomb surface faces | 144 | 144 |

The draw cache stays within its existing 384-face Balanced ceiling. The extra reservation
prevents partial fitting from increasing the crowded flat scene's draw footprint. These are
work-count comparisons, not an FPS claim. Raw samples: [Vulkan before](performance/m8-terrain/vulkan-before.csv),
[Vulkan after](performance/m8-terrain/vulkan-after.csv) and
[OpenGL after](performance/m8-terrain/opengl-after.csv). CPU timings include first-use costs
and shared-host variability; no timing speedup or target-PC frame rate is inferred from them.
The release jar excludes the probe and baseline instrumenter.

Software-renderer CPU samples are diagnostic. They do not establish 80 FPS on the requested
RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p. JEI compatibility is excluded locally with the external
init script because its dependency host is blocked; repository JEI/build files are unchanged.
There are no new textures, shaders, network packets, configuration options or gameplay changes.
