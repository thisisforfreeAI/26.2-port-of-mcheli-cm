# M8 pixel graphics and particle budgets

This document records the first independent pixel-effects pass and its later integration with
Claude's M8 slice 1 (PR #36). The initial screenshots and timing table below predate that integration. It does not declare M6, M6b, M7 or M8 complete. Gameplay, network payloads and content
definitions stay with the port work; the files here concern client presentation only.

## Visual map

Preserve the simple, pixelated silhouettes in the reference screenshot. Add depth through
small shaded sprites and animation, rather than more particles or full-screen effects.

| Effect | First increment | Cost control |
|---|---|---|
| Smoke, missile trails, damage smoke, rotor dust | Shaded 32-pixel puffs, interpolated growth and a gentle fade | Existing single quad, shared atlas, no extra layers |
| Explosions/fire | Brief warm tint inside fresh explosion smoke; one expanding flash | Short light pulse, capped flash admission |
| Muzzle flash | Warm pixel animation, correct rotation units and fading | Existing single quad |
| Flares | Full-bright core with a small camera-facing halo | One extra quad per visible flare; none for chaff or Low quality |
| Water splashes | Separate pale, broken droplet sprite | Existing particle, same motion |
| Tracers and missile exhaust | Preserve the content models/exhaust in this increment | No new trail history or geometry |

Use vanilla particle and entity pipelines on both Vulkan and OpenGL. No bloom, scene copies,
dynamic world lights, volumetrics, or shader dependencies. Stock/addon sprite overrides remain
available through Classic style; Polished uses separate port-owned sprites. Particle colours,
emitter positions and motion remain content-controlled.

## Performance and validation

Default to Polished/Balanced. Low/Balanced/High scale smoke draw distance, emission per tick and
live particle limits. Respect Minecraft's decreased/minimal particle setting. Reserve a separate
budget for short flashes so smoke cannot exhaust it. Cull before allocating custom particles.
Keep the existing vanilla explosion option working. Large explosion block scanning and terrain
effects remain existing port costs and must not be represented as fixed by particle limits.

Verify budget reset, distance/density boundaries and animation endpoints with focused tests.
Run the build and repeatable scenes on Vulkan and OpenGL, capture both styles, and record actual
backend, scene settings and limitations. Mesa software rendering can check correctness and
relative cost; it cannot establish a hardware FPS guarantee. M8 remains open until representative
combat-load frame times and the remaining effects have been reviewed.

## Settings and integration

In `config/mcheli_ce_port-client.toml`, `[effects] style = "POLISHED"` enables this pass;
`"CLASSIC"` retains the upstream sprites, colour and animation. Existing particles finish in
the style they started with. Both styles share the new budgets. The existing
`defaultExplosionParticle` switch still selects vanilla explosions.

| `quality` | Smoke range | Smoke per tick / live | Flash per tick / live | Flare halo |
|---|---:|---:|---:|---|
| `LOW` | 96 blocks | 48 / 384 | 24 / 48 | Off |
| `BALANCED` (default) | 192 blocks | 96 / 768 | 48 / 96 | On |
| `HIGH` | 256 blocks | 192 / 1536 | 96 / 192 | On |

Smoke density halves beyond 64 blocks and quarters beyond 128. Minecraft's Decreased and
Minimal settings further halve/quarter smoke density; short flashes retain a separate bounded
budget for weapon readability. This does not cap terrain debris, vanilla particles or flare
entities, and it does not eliminate the existing explosion block ray march.

Textures are original procedural artwork, reproduced with
`python3 scripts/generate-effect-textures.py` (standard library only). Compact 4×2 sheets avoid
a silent dropped-sprite case in the pinned Minecraft 26.2 stitcher when a wide strip makes it
expand on an axis that cannot accommodate the sprite. `EffectTextureTest` exercises the real
sheet dimensions through that stitcher. The opt-in visual probe also rejects missing sprites.

The implementation is restricted to `client/particle`, `client/render/FlareRenderer`, the two
client config entries, new textures, focused tests and these graphics scripts/docs. No vehicle
physics, weapons, networking, common effects payloads or content definitions changed. Tracer
models and missile exhaust keep their existing rendering in this increment.

## Repeating the preview

Use an isolated copy of a flat test world named `M6Smoke` in
`.run/m8-preview/saves/M6Smoke`. The probe **removes non-player entities** and changes blocks,
time and weather inside that copy. Do not use a player world. Copy the usual test client's
options and `config/fml.toml`; Vulkan needs `earlyWindowControl = false` with this NeoForge build.

Run `./gradlew runClient -I scripts/visual-probe.init.gradle --no-configuration-cache` with a
working display and Vulkan driver. For the compatibility pass, add
`-Dmcheli.probeBackend=OPENGL`. The extra probe Java source directory is included only by this
init script; normal builds do not contain the probe or its event hooks.

The camera is fixed at `(0.5, -55, -9.5)`, yaw 0, pitch 8, at midnight, 1280×720, four chunks.
The probe emits a smoke column, an explosion-like burst, a muzzle flash, a flare with its stock
trail and water drops. It runs Classic and Polished with the same seeded emitter parameters,
then repeats with twelve emitters under each style and Low quality, and captures both styles
at noon. A final daylight capture also exercises the real client explosion-effect entry point.
Individual particle random
values and capture partial ticks vary. Captures are real engine output, not concept images.
`M8_PROBE result` lines record observed inter-frame median/p95 and live engine counts; this is
a synthetic stress scene, not a complete battle or GPU timer query.

## Observed graphics results (2026-10-18)

Both backends load all new sprites, render the scenes, save screenshots and shut down the
integrated server cleanly. Vulkan uses the explicit lavapipe ICD (Vulkan 1.4.305); OpenGL uses
llvmpipe (OpenGL 4.5), both Mesa 25.0.7 / LLVM 19.1.7 under Xvfb. Audio is unavailable in this
environment. Captures:

- Vulkan: [Classic night](screens/m8-vulkan-classic.png),
  [Polished night](screens/m8-vulkan-polished.png),
  [Classic daylight](screens/m8-vulkan-day-classic.png),
  [Polished daylight](screens/m8-vulkan-day-polished.png),
  [client explosion effects](screens/m8-vulkan-day-explosion.png).
- OpenGL: [Polished night](screens/m8-opengl-polished.png),
  [Polished daylight](screens/m8-opengl-day-polished.png).

The following inter-frame times came from isolated runs after the unit tests finished; the
earlier run overlapping test compilation is excluded. Both styles use Balanced budgets.
Warm-up is excluded from each sample. Figures are **median / p95 milliseconds**, not GPU timings:

| Backend / scene | Classic | Polished |
|---|---:|---:|
| Vulkan / one emitter set | 36.75 / 80.59 | 35.53 / 64.32 |
| Vulkan / twelve emitter sets | 36.60 / 55.58 | 36.79 / 75.25 |
| OpenGL / one emitter set | 32.30 / 45.71 | 31.27 / 45.02 |
| OpenGL / twelve emitter sets | 33.91 / 46.18 | 34.27 / 49.77 |

Low quality reduces the stress scene's live engine count from about 760 to about 384, including
a few non-smoke particles. The matching stress medians are close, but Polished has higher p95
times in these samples, especially on software Vulkan. This is not evidence of zero performance
cost or a hardware FPS guarantee. Hardware combat profiling and longer frame-pacing checks
remain open before declaring M8 complete.

The normal build is blocked by this environment's HTTP 403 for the optional JEI API at
`maven.blamejared.com`. Local validation uses an external Gradle init script that excludes only
`compat/jei/**` and removes the `mezz.jei` compile-only dependency. The repository build settings
and JEI adapter are unchanged. Such a local artifact is not the full JEI-enabled release build.
The final local `build runGameTestServer` succeeds: all 164 JUnit tests (zero failures, errors or
skips), including six new graphics regressions, and all 198 required GameTests pass. The dedicated
GameTest server saves and shuts down cleanly. All four texture PNGs reproduce byte for byte from
the generator. The local JAR contains the graphics classes and four new textures and contains
neither the development probe hooks nor the excluded optional JEI adapter. Full JEI-enabled
build validation remains blocked as described above.

## Integration with M8 slice 1

PR #36 reached main while this branch was being prepared. The combined implementation keeps
its `effects.effectQuality = CLASSIC/ENHANCED` switch, heat/fade curves, additive flash/glow
pipelines, tracer and missile effects, and downwash ground tint. New `effects.style` selects
the pixel pass (Polished) or upstream presentation (Classic); either Classic switch disables
enhanced presentation. `effects.quality = LOW/BALANCED/HIGH` remains an independent resource
budget. This separates the existing `EffectQuality` enum from the new `EffectBudget` enum.

Smoke uses one fade and one heat calculation from `EffectLook`, with the new 4×2 artwork; the
earlier six-tick heat pulse and duplicate fade implementation are removed. Explosions keep
slice 1's glow without adding a second flash. Optional muzzle/explosion glows share the smoke
budget and are disabled on Low; weapon flashes retain their own reserved budget. Flares keep
their full-bright core and flicker, using one small textured halo instead of two glow discs;
Low disables that halo. Tracer and missile rendering from main is preserved.

The preceding screenshots and timing samples describe the independent pass before integration;
they are not performance measurements of the combined renderer. Use
`-Dmcheli.probeQuick=true` for a shorter compatibility capture run (Classic/Polished, night/day,
including the client explosion entry point); omit it for the full stress sequence.

Integration checks pass with the same local JEI exclusion: `build runGameTestServer`, all
167 JUnit tests (zero failures/errors/skips) and all 198 required GameTests. The quick native
scene completes and saves/stops cleanly on Vulkan and OpenGL. Updated integrated captures:
[Vulkan night](screens/m8-integrated-vulkan-night.png),
[Vulkan day/explosion](screens/m8-integrated-vulkan-day.png),
[OpenGL night](screens/m8-integrated-opengl-night.png),
[OpenGL day/explosion](screens/m8-integrated-opengl-day.png).
These are compatibility captures, not a hardware performance validation.
