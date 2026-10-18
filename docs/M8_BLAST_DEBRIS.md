# M8: surface fragments, dust and smoke breakup

Enhanced dry impacts now pair their existing smoke with a small ground response. Bombs throw
short-lived block-textured clods or chips among the dust; missile/rocket and penetrator hits get
a smaller pair of ground accents. Cannon impacts retain their existing path. The main smoke
allocation and familiar small-weapon puffs remain in place.

## Ground response

One loaded surface-column search supplies both dust and fragments. Sand/gravel gets wider,
faster dust, soil darker brown dust, snow a lighter, slower cloud, hard surfaces less dust, and
wood a warmer tint. Grass/mycelium/podzol fragments use dirt textures. Loose and layered snow
use the visible snow surface and white snow-block fragments. Water, leaves, invisible surfaces
and high airbursts reject these ground effects.

A typical power-12 Balanced bomb requests four dust wisps and two fragments, replacing part
of the previous six-dust burst. Balanced missile hits request one smaller dust wisp and one
smaller fragment. Emission admission may reduce those numbers. The fragments spin in three
dimensions and follow short arcs. Only some hard-surface fragments from strong incendiary
weapons start hot; soil, sand, snow, wood and ordinary high-explosive surface fragments stay
ambient-lit. Hot chips cool within eight ticks.

Fragments draw three camera-visible faces using the struck block's existing terrain-particle
texture and vanilla atlas pipeline. Geometry uses reused quaternions/vectors during extraction.
Dust uses one new original 128×128 sheet with four 64-pixel frames, generated offline by
`scripts/generate-effect-textures.py`. Existing effect sheets are unchanged. No new shaders,
persistent entities, gameplay debris or network payloads are added.

## Bounds and tradeoffs

- Primary explosion counts stay unchanged. Broad ground-accent requests remain capped at
  six Balanced / ten High per impact; small hits request two / three. Low and adaptive pressure
  >=2 omit ground accents. Requests are divided by two at pressure 1.
- Fragments have additional global caps: four / six emissions per world tick and 24 / 48
  tracked active slots for Balanced / High. Pressure 1 halves the tick cap. Weak references
  prevent the tracker from retaining particles or a departed world.
- Fragments share the existing smoke live-count limit. Their admission cost charges three
  visible faces and their full bounding radius against the shared weighted per-tick budget.
  They do not have a reserved pool that can bypass that budget.
- Each surface search reads at most 13 loaded block states. All ground-accent searches together
  may read at most 96 Balanced / 192 High block states per world tick. No chunks are loaded.
- Fragment half-width is at most 0.24 blocks (smaller for missiles and snow). Life is capped at
  40 ticks, with a shrinking final six ticks. A descending fragment disappears upon crossing
  the sampled source surface height. There are no collision scans or simulated bounces;
  uneven terrain can occlude these purely cosmetic pieces.
- Light sampling is cached once per moved block/tick, or every four stationary ticks. Light
  reads are limited by simulation ticks rather than render-frame count. Fragment geometry
  performs no collision queries or entity searches.

Three cube faces add vertex work compared with one dust quad, while the small fragments cover
far fewer pixels. The extra small-weapon ground accents and material-specific dust sizes also
change cost. The limits bound work; they do not establish a zero-cost improvement or 80 FPS.

## Smoke breakup

Each large-bomb billow now has its own 24–47-tick fade window and a small independent drift
in the final 40% of its life. Low base smoke receives a quarter of the upper-cloud drift.
This opens uneven gaps as the cloud dissolves, without spawning more particles, growing past
the admitted radius or extending the 128-tick lifetime cap. Earlier smoke-led small impacts
retain their existing puff behavior.

## Validation

The opt-in `BlastGroundProbe` covers isolated sand, grass/soil, snow and stone responses; a
full missile impact; high-airburst, water and Low rejection; and empty/model-only/fixed/adaptive
controls. A synthetic 100-impact burst checks the global fragment and terrain-read bounds.
Direct particle checks cover the shared smoke limit, 40-tick lifetime, final shrink, sampled
floor crossing, loaded dust sprite, grass classification and visible thin/powder snow heights.

The mixed scene holds two stock AH-64 helicopters and two F-35A jets above the ground, with
actual engine exhaust and helicopter rotor-wash hooks. Twelve stationary flare entities emit
their normal trails and render their glows. Scripted bomb/missile impacts repeat every 25 ticks,
with a short missile-trail fixture and muzzle flashes. These are held client fixtures and VFX
calls, not a flight simulation, weapon-damage benchmark or a complete real battle.

For a disposable flat `M6Smoke` world in `.run/m8-preview/saves`, use:

```text
./gradlew runClient -Dmcheli.probeBlastGround=true -I scripts/visual-probe.init.gradle
```

Add `-Dmcheli.probeBackend=OPENGL` for OpenGL. Add `-Dmcheli.probeGroundPerfOnly=true` to run only
the four mixed-scene controls. The driver runs at 1920×1080 with VSync off and a 120 FPS limit.
Mixed screenshots are taken before the frame-sampling window. A phase may extend its sample
window to gather at least 48 frames on a slow renderer. Scene drivers are excluded from normal
builds. Optional JEI integration is excluded locally through an external init script because
its Maven host is blocked by this environment's network policy; repository JEI files are unchanged.

The final release build passed all 204 JUnit tests with no failures/errors/skips. The JAR
contains the fragment class and exact generated dust sheet, and excludes all opt-in drivers.

Both full native material/mixed scenes passed on Vulkan/lavapipe and OpenGL/llvmpipe. Vulkan's
four performance controls were repeated after screenshots moved outside the sampling window;
only those repeated Vulkan samples are retained below. No builds or unit tests ran during the
recorded frame windows. The native particle atlas stayed 512×512 after adding the dust sheet.
All nine original-generator effect sheets regenerate byte-for-byte; the new PNG is 3,176 bytes.
The dedicated GameTest server also passed all 234 required tests on the integrated M6/main base.
Missing OpenAL hardware disabled audio; the passing runs had no renderer exceptions.

### Actual native captures

The closeups isolate ground accents to show their material response. Full bomb/missile effects
are present in the mixed scene. These are in-game screenshots, not concept art.

| Capture | Scene |
|---|---|
| [Vulkan soil](screens/m8-debris-vulkan-soil.png) | Brown dust and unheated dirt clods over grass |
| [Vulkan mixed scene](screens/m8-debris-vulkan-combat.png) | Aircraft, flare trails/halos, blasts and ground accents |
| [OpenGL snow](screens/m8-debris-opengl-snow.png) | Lighter dust and smaller white fragments |
| [OpenGL missile](screens/m8-debris-opengl-missile.png) | Familiar smoke with a small ground response |

### Software frame samples

Four render chunks, 1080p, VSync off, 120 FPS cap. The CSVs record frame intervals, not isolated
effect costs. [Vulkan raw data and summary](performance/m8-debris-lavapipe/summary.json) and
[OpenGL raw data and summary](performance/m8-debris-llvmpipe/summary.json) are retained for review.
The model-only control includes the same four aircraft with exhaust/wash off.

| Backend | Scene | Frames | Median ms | p95 ms |
|---|---|---:|---:|---:|

| Vulkan | empty | 72 | 79.072 | 136.061 |
| Vulkan | models | 77 | 81.689 | 111.655 |
| Vulkan | fixed | 63 | 99.691 | 115.968 |
| Vulkan | adaptive | 67 | 94.254 | 113.976 |
| OpenGL | empty | 91 | 71.677 | 95.748 |
| OpenGL | models | 69 | 83.151 | 157.372 |
| OpenGL | fixed | 68 | 92.617 | 143.999 |
| OpenGL | adaptive | 78 | 83.445 | 99.891 |

End-scene total particles were 752/247 fixed/adaptive on Vulkan and 749/237 on OpenGL. Peak
tracked fragment slots were 12/3 and 11/3, respectively; all were below the Balanced cap of 24.
Both fixed scenes drew flare halos and emitted exhaust and rotor wash, while bounds held for
fragments, terrain reads and per-view flare geometry. The synthetic burst reached four fragments
and 96 surface reads in a world tick without exceeding either cap.

Adaptive ended at pressure 3. Its phase peaks include initial warmup before optional details
were shed; they are not counts of fragments or halos retained at pressure 3. End-scene totals
are snapshots rather than averages. Host/software-renderer variability is substantial: some
control p95 values were worse than loaded scenes. These runs do not establish the isolated
cost of the change or a consistent FPS improvement.

All controls, including empty scenes, missed 12.5 ms. Hardware acceptance remains **80 FPS at
1080p on RTX 3060 8 GB / Ryzen 7 7800X3D**, including actual projectile flight, destruction,
fire blocks and real combat geometry. The held VFX fixtures cannot establish that target.
