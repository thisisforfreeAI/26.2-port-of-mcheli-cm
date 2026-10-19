# M8 impact smoke, ground dust fronts and exhaust heat

This follow-up implements the requested optional effects and fuller rocket/missile impact smoke.
Gameplay damage, destruction, weapon trajectories and engine simulation are unchanged.

## Fuller impact smoke

Small general and anti-tank missile impacts keep the older growing puff character, with more
primary requests inside the existing Low/Balanced/High caps of 6/12/18. Larger general missile
warheads spend three quarters of their primary requests on smoke while retaining rolling fire.
Fire still enters admission first. Ground dust and fragments follow the primary clouds.

| Stock profile | Power/family | Balanced primary requests, before → after |
| --- | --- | ---: |
| Hydra | 3 / BLAST | 6 → 10 |
| AGM-114 | 4 / PENETRATOR | 7 → 11 |
| AIM-9 | 5 / BLAST | 8 → 12 |
| AGM-158 | 10 / BLAST | 12 → 12; smoke allocation 8 → 9 |

Missile puffs start at 0.32 of nominal size instead of 0.2, grow to 30% larger radii on
Balanced/High (10% on Low), and last at least 44 + min(power,12) × 3 ticks, at most 100.
The directional spread is broader and the strong upward impulse is reduced, making a connected
cloud rather than a narrow stack of flying puffs. Admission charges the final radius, not the
small initial sprite. Pressure divides request
counts, rounding missile clouds upward within the same caps so moderate warheads retain two
smoke puffs alongside their fire pocket where admission permits. Shared live/coverage limits
can still reject requests. Cannon smoke, projectile trails and Classic emitters retain their
existing behavior; this changes impact clouds.

## Broken ground dust front

`effects.blastDustRing` defaults to true. Substantial general/bomb/incendiary impacts can
produce ten short, low ground wisps on Balanced or fourteen on High. Each uses existing pixel dust
art, cached local lighting and the shared smoke allowance. The wisps expand unevenly and
reach their outer radius over ten ticks and fade over 18 ticks; they suggest a broken pressure
front rather than an opaque perfect circle. Starts lie within two blocks of the impact and
radial travel caps at ten blocks, so the front can clear the developing fireball.
Sand, soil, snow and hard-ground tint/opacity follow the existing sampled ground response.

Low, pressure ≥1, cannon/penetrator profiles and powers below four omit the front. New rings
share a global cap of 12/20 particles per world tick on Balanced/High and the existing 96/192
direct ground-read allowance. Each sector checks its initial loaded support column; no
particle collision queries or continuous terrain search is added. Water/leaves/unsupported
starts reject sectors. Airbursts more than min(3,power×0.4) blocks above sampled ground do not
form the ring. Short billboards stay just above their initial ground plane as they move; terrain depth
hides intersections, so they do not conform continuously to slopes or follow crater walls.

## Additional calibrated exhausts

AH-1Z and CH-47 now declare paired turbine outlets in stock YAML. AH-1Z uses the angled engine
exit collars near (±0.85,2.23,-6.83); CH-47 uses the rear engine mouths near
(±1.30,3.75,-12.55). These coordinates come from their MQO geometry, before the existing
aircraft render offset. Unsupported/addon helicopters still need explicit outlet data as
documented in [M8_ENGINE_EXHAUST.md](M8_ENGINE_EXHAUST.md); rotor positions are not substitutes.
The parity baseline records these two intentional cosmetic additions; legacy fixtures stay pinned.
The bundled content revision advances to two so existing installations refresh the extracted
default definitions on startup. As with other bundled updates, custom overrides belong in
separate addon packs; edits inside the generated default folder are replaced by extraction.

## Local exhaust refraction

`effects.engineHeatHaze` defaults to true in Enhanced effects. A small GLSL 330 fragment shader
uses Minecraft's supported RenderPipeline/texture/buffer APIs on both graphics backends; no
raw GL is used. This is an explicit exception to the earlier M8 vanilla-shader/no-scene-copy
restriction, authorized by the request for engine heat distortion.

The pass runs after the level and before hands/HUD. Engine outlets feed a bounded 16-entry
cache; nearest in-view sources win up to four capsules on Balanced or eight on High, within
48 blocks. Throttle, fuel and outlet-fluid gates are shared with the plume emitter. Turbine
volumes are at most 1.8 blocks long and jet volumes four; projected radius caps at 48 pixels.
Animated refraction shifts samples at most two pixels per axis, retaining the pixel appearance.
A client-only projection tap reads the final world matrix, including view bob/hurt/nausea,
without modifying it, keeping the projected volumes aligned with the rendered engines.
Reversed scene depth hides heat behind nearer opaque geometry and rejects displaced samples
that would pull foreground edges into a plume. Translucent objects without depth writes are
not reliable occluders for this technique.

All capsules share one color copy and one scissored triangle pass per active frame; there is
no extra depth copy. The fragment shader samples the original scene rather than its output.
A 1080p RGBA8 color target costs about 7.9 MiB while active, plus a 272-byte uniform buffer.
No entity-list scan or render-time terrain query is added. Sources expire after six world ticks;
empty/disabled/world-change paths release GPU resources. Framebuffer resizing recreates the
copy. In-view sources hidden behind geometry can still trigger the copy; depth masking happens
on the GPU. No source in view means no copy. Low, Classic, or **any** adaptive pressure disables
the effect entirely. Its toggle is independent of exhaust smoke and flare glow.

## Validation and remaining acceptance

`-Dmcheli.probeFollowups=true -I scripts/visual-probe.init.gradle` selects the disposable
`M6Smoke` fixture. It edits terrain and removes non-player entities; use only the preview copy.
`-Dmcheli.probeBackend=OPENGL` selects OpenGL, otherwise Vulkan is used. Scenes cover stock-sized
rocket/anti-tank/cruise impacts, dust fronts, water, isolated heat, opaque occlusion, real stock
Viper/Chinook/jet emitter calls, Low, disabled effects, adaptive pressure and cooldown.
The occlusion control compares 320,000 foreground pixels with heat enabled and disabled.
The driver and its screenshots/readbacks are excluded from normal builds.

Final Vulkan/lavapipe and OpenGL/llvmpipe runs passed all sixteen scenes (1,920 tick rows per
backend), including the isolated dust-front control and the final projection hook. The visible
heat control changed 1,186 background pixels on Vulkan and 1,123 on OpenGL; both opaque-wall
controls left all 320,000 sampled pixels identical. Viper/Chinook each supplied two real outlet
sources; the jet fallback supplied one. Balanced showed at most four heat capsules and one
scene copy; Low/disabled/cooldown had zero copies, and pressure three shed the pass.

Raw [Vulkan counters](performance/m8-followups/vulkan.csv) and
[OpenGL counters](performance/m8-followups/opengl.csv) include the vanilla particle debug
string and snapshots of work counters. Ring/read counters retain their last active tick's
value until the next emission or clear; they are not a per-tick emission rate.

Add `-Dmcheli.probeFollowupPerfOnly=true` for two 400-tick fixed-camera scenes using the same
background, with four in-view heat capsules enabled and then the effect disabled. Adaptive
detail is off for this comparison; captures/readbacks are suppressed, and the first 40 ticks
of each scene are excluded from frame sampling. No other native clients or builds ran during
these timing windows. These sequential software samples include scene/renderer noise and do
not establish a consistent target-GPU overhead or an FPS gain.

| Software backend | Heat | Frames | Median ms | p95 ms |
| --- | --- | ---: | ---: | ---: |
| Vulkan/lavapipe | On | 234 | 73.810 | 98.534 |
| Vulkan/lavapipe | Off | 252 | 70.323 | 85.923 |
| OpenGL/llvmpipe | On | 234 | 74.905 | 105.322 |
| OpenGL/llvmpipe | Off | 209 | 80.002 | 138.910 |

Raw [Vulkan frame intervals](performance/m8-followups/vulkan-heat-perf.csv) and
[OpenGL frame intervals](performance/m8-followups/opengl-heat-perf.csv) record exactly one copy
and four capsules in the enabled windows, zero copies/capsules in the disabled windows.
The corresponding 800-row counter traces are stored alongside them. These are whole-frame
wall-clock intervals, not GPU timestamps. The inconsistent ordering between backends is a
reason to retain the independent toggle and conservative load gates, not evidence of free
refraction.

Software-renderer counters and frame intervals are diagnostic only. The RTX 3060 8 GB /
Ryzen 7 7800X3D at 1080p still requires representative combat profiling against 80 FPS / 12.5 ms,
especially with the newly enabled scene copy. Hardware performance is not certified here.
Local verification excludes optional JEI integration because its Maven host is unavailable;
repository JEI configuration is unchanged.
The final release build passes all 235 JUnit tests, including stale bundled-pack refresh while
preserving separate overrides. Jar inspection confirms the shader/client projection mixin and
effects are present and all development visual probes are excluded.
