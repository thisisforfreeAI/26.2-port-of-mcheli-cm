# M8 readable tracer rounds

Selected tracer rounds now have a narrow bright burning core, a tapered warm tail and a faint
colour edge. Ordinary bullet models remain ambient-lit. Stable UUID sampling retains roughly
one tracer per four rounds; it is cosmetic sampling, not a simulation of a loaded ammunition belt.
Explicit red/green/other weapon colours and definition opacity remain supported. Neutral rounds
use warm amber. Classic rendering, missile exhaust and countermeasure flare glow are unchanged.

The previous ribbon disappeared when looking straight along its motion: its cross product with
the camera direction had no width. That angle now draws a tiny camera-facing burning base.
It is brightest from behind the projectile, readable from the side and dimmer head-on. This
models the visibility of burning tracer composition without making the entire bullet glow.
There is no spherical halo, bloom pass, world light, persistent trail or extra particle.

| Cosmetic bound | Previous | Updated |
| --- | --- | --- |
| Selected rounds | About 1 in 4 | About 1 in 4 |
| Exposure length | 22% of tick motion, max 2 blocks | 35%, max 3 blocks |
| Streak width | 0.045 blocks | 0.06–0.14 blocks with bounded distance compensation |
| Definition-opacity multiplier | 0.30 day / 0.46 night | 0.95 day / 0.75 night, further scaled by view angle |
| Triangles per selected moving round | 4 | 4 |
| Geometry submissions per tracer | 1 | 1 |

Distance compensation makes distant tracers less likely to become subpixel slivers; the hard
width/length caps prevent broad beams. Brighter daylight rendering improves contrast, while
night opacity is restrained. The streak uses the existing additive, depth-tested entity pipeline.
Its core fades into the tail rather than lighting surrounding smoke or terrain. These dimensions
are presentation choices in game blocks, not literal projectile calibre or a ballistic prediction.

## Validation

`TracerLookTest` covers stable sparse selection, finite motion/width limits, zero motion,
definition transparency, view-angle behaviour, explicit colour preservation and neutral defaults.

Prepare the disposable `M6Smoke` world using
[the preview setup](M8_PIXEL_EFFECTS.md#repeating-the-preview), then run:

```sh
./gradlew runClient -Dmcheli.probeTracers=true -I scripts/visual-probe.init.gradle --no-configuration-cache
```

Add `-Dmcheli.probeBackend=OPENGL` for the compatibility pass. Seven 1080p scenes cover moving
stock M134 models from the side by day/night, from behind by day/night, head-on, at about eighty
blocks and in Classic. The fixture emits eight controlled client bullets per volley, asserts
two selected tracers (zero in Classic), caps the scene at sixteen live bullets and verifies
drainage. Controlled motion is slower than the motion vector supplied for exposure testing;
this is a renderer fixture, not weapon physics. Captures at ages 28/48 show the moving volleys.

The scene modifies only the disposable world's time/weather/player mode and the test client's
effect settings. Normal builds exclude its driver. No content definitions, entity motion,
damage, networking or server code change. Target-PC 80 FPS acceptance remains separate.

Vulkan/lavapipe and OpenGL/llvmpipe (Mesa 25.0.7 / LLVM 19.1.7) pass all seven scenes at
1920×1080. Each Enhanced volley selects two of eight rounds, Classic selects none, each scene
peaks at sixteen live fixture bullets and drains completely. Native captures confirm thin side
streaks and compact rear-view points. These are visual/behaviour checks on software renderers,
not an 80 FPS certification or comparison of hardware frame times.
The integrated build passes 219 JUnit tests. Optional JEI is excluded locally through an
external init script because its Maven host is blocked; repository dependency/JEI files are
unchanged. The normal release jar excludes all opt-in probe drivers.
