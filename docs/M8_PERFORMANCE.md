# M8 performance target: 80 FPS

Target supplied by the user: **RTX 3060 8 GB, Ryzen 7 7800X3D, 1920×1080**. The frame
budget is **12.5 ms**. Hardware acceptance is still pending: this workspace has Mesa software
rendering, not an RTX 3060. Software measurements below must not be presented as that GPU's FPS.

## Runtime changes

`effects.adaptiveDetail = true` and `effects.targetFps = 80` are the defaults. The controller
observes complete frame intervals, including presentation and pacing, on either backend. It
uses the lower of the requested target, the user's FPS cap and the known VSync refresh rate.
It does not change Minecraft's settings or cap FPS itself.

After two seconds of warmup, a 0.75-second window averaging more than 108% of the target frame
time increases pressure by one step, up to three. Five seconds of windows within 102% of the
frame budget restores one step. This hysteresis limits visible changes near the target.
Menus, pause, focus loss and resource overlays are excluded. Invalid intervals and stalls over
250 ms are discarded; world changes reset the controller.

- Smoke and McHeli block dust share a bounded per-tick budget and native live-particle limit.
  Higher pressure increases emission stride and lowers the per-tick allowance. Existing
  particles finish normally; detail transitions do not clear clouds.
- Large nearby puffs and glows consume more spawn units, using their expected largest radius
  and camera distance as a cheap coverage estimate. This bounds repeated near-camera clouds
  more aggressively than a particle count alone. It is not an exact GPU overdraw measurement.
- Muzzle flashes retain an independent budget at every pressure level. Tracer streaks, missile
  flames, full-bright flare cores, shaded artwork, heat and fades remain available. Optional
  explosion/muzzle glows, flare halos, missile glow discs and tracer head halos are disabled at
  pressure 2–3 or on Low quality.
- McHeli splash/bubble secondary spray also passes admission, rather than multiplying an
  admitted water drop into an unbounded burst. This does not limit unrelated vanilla emitters.
- Explosions beyond the effect range plus their extent return before the client block scan.
  Visible explosions retain their block ray march; that CPU cost is still present.
- Flare cube vertices/UVs and glow-disc ring coordinates are reused, avoiding repeated mesh
  arrays and ring trigonometry during rendering.

Set `adaptiveDetail = false` for fixed detail, controlled by `effects.quality` and Minecraft's
particle setting. Coverage weighting and hard budgets remain active. This switch is useful for
comparisons, not a promise that fixed detail meets the performance target.

## Repeatable native benchmark

Use the isolated world setup from [M8_PIXEL_EFFECTS.md](M8_PIXEL_EFFECTS.md). The opt-in probe
removes entities and changes blocks/time in that test copy. It is excluded from ordinary builds.

```sh
./gradlew runClient -Dmcheli.probeBenchmark=true \
  -I scripts/visual-probe.init.gradle --no-configuration-cache
python3 scripts/summarize-effect-frames.py .run/m8-preview/m8-*.csv
```

Default window size is now 1920×1080; `mcheli.probeWidth`/`mcheli.probeHeight` can override it.
For OpenGL add `-Dmcheli.probeBackend=OPENGL`. The test world currently uses four render chunks,
VSync off and a 120 FPS cap. Check actual screenshot dimensions and backend in the log.

The benchmark runs an empty scene, then fixed/adaptive/fixed/adaptive detail, each for 360 ticks,
with twelve seeded smoke/flash/splash emitter sets under Polished/Balanced. It excludes the first
100 ticks from each sample. Screenshots happen before measurement starts. It writes per-frame
interval CSVs and reports median/p95/live count/pressure in `M8_PROBE result` lines. The summary
script also reports p99, average FPS and the percentage of frames within 12.5 ms. Use
`--require-target` to fail when any supplied scene has p95 above the target frame budget.
Comparisons share the same executable; fixed detail includes the new static optimizations.
The synthetic scene does not replace an actual battle or hardware GPU profiling.

## Hardware acceptance still required

On the target PC, record the GPU/driver, resolution, graphics settings, render/simulation
chunks and mod list. Run the native benchmark above, then a representative busy flight/battle
with several aircraft, missile trails, rotor wash and overlapping explosions. Compare fixed
and adaptive detail at identical settings after warmup. Record average, p95 and p99 frame times
and time above 12.5 ms; aim for at least 95% of sampled frames within the 80 FPS budget.

A slow empty scene identifies a base renderer/terrain bottleneck that particle detail cannot
solve. This patch targets M8's cosmetic workload; it cannot guarantee total FPS across terrain,
server load, content packs or other mods. Do not mark M8's 80 FPS hardware target achieved until
that machine's combat capture passes.

## Local validation

The isolated Vulkan run used Mesa 25.0.7 lavapipe / LLVM 19.1.7, Vulkan 1.4.305 under Xvfb,
1920×1080, four chunks, VSync off, 120 FPS cap. No test/build work ran concurrently. The probe
verified actual framebuffer dimensions. Runs use latest main `f5cdbb3` plus this performance pass.
Raw samples and the generated [summary](performance/m8-80fps-lavapipe/summary.json) are committed.
The table uses the summary script's nearest-rank percentile definition (now also used by the probe;
the original console output used a floor index).

| Scene | Median ms | p95 ms | p99 ms | Final live particles | Final pressure |
|---|---:|---:|---:|---:|---:|
| Empty | 62.88 | 106.28 | 160.91 | 0 | 0 |
| Fixed, first repeat | 71.23 | 122.60 | 160.51 | 754 | 0 |
| Adaptive, first repeat | 65.90 | 92.59 | 155.52 | 271 | 3 |
| Fixed, second repeat | 69.50 | 115.13 | 168.14 | 757 | 0 |
| Adaptive, second repeat | 65.70 | 96.68 | 216.77 | 271 | 3 |

Adaptive detail cuts the final live count by about 64% and lowers median/p95 in both repeats.
The second adaptive p99 is worse. These short software runs support reduced particle workload,
not a guarantee about every stall, hardware FPS or a completed 80 FPS target. All software scenes,
including the empty scene, miss 12.5 ms. Captures: [fixed detail](screens/m8-80fps-vulkan-fixed.png),
[adaptive detail](screens/m8-80fps-vulkan-adaptive.png).

The OpenGL compatibility scene also completes at verified 1920×1080 on Mesa llvmpipe (OpenGL
4.5), checks Classic/Polished by night and day, exercises the actual client explosion entry point,
saves captures and stops the integrated server cleanly. [OpenGL explosion capture](screens/m8-80fps-opengl-explosion.png).
The Vulkan benchmark likewise saves and shuts down cleanly. Audio is unavailable in this environment.

Local Gradle validation excludes
only the optional JEI adapter/API through the external `/tmp/m8-without-jei.init.gradle`, because
this environment cannot access its Maven host (HTTP 403). Repository build settings and the JEI
adapter are unchanged; the locally built JAR is not a full JEI-enabled release artifact.

Final validation against the updated main succeeds: `build runGameTestServer` with the above
JEI exclusion, **171 JUnit tests** (zero failures/errors/skips) and **all 201 required GameTests**.
The dedicated server saves and stops cleanly. Focused regressions cover overload/recovery,
loading spikes, user caps/VSync, world-reset behavior and weighted-budget boundaries. The summary
script also passes manual good/slow/invalid sample checks. The normal local JAR includes the
adaptive controller and excludes the opt-in development probe.
