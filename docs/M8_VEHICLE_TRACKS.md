# M8 cosmetic vehicle tracks

Enhanced effects now leave broken tread imprints and narrow paired tire grooves. Implemented
in the requested order: terrain fitting and limits, surface appearance, then vehicle paths.
`effects.vehicleTracks` defaults to true; Classic mode or disabling it clears the marks.
These are temporary client geometry: no block edits, traction changes or server simulation.

## Contacts and appearance

Stock tracked tanks sample two grounded road-wheel anchors, one per side. Wheeled tanks sample
up to eight contact lanes, four per eligible vehicle tick. Contacts come from the existing
collision probes **before** their synthetic suspension support flags are applied. Other movable
vehicles require explicit wheel data and an on-ground hull; placed weapons without wheels
do not receive guessed contacts. Width derives from the configured wheel size.

Each lane follows its own displacement through turns and reversing. Distance spacing is
0.32 blocks for treads and 0.35 for tires. Stationary contacts do not stamp repeatedly;
airborne contacts, teleports, large height jumps and long sampling gaps reset interpolation.
Only the newest two samples per contact/update survive fast travel. Turns strengthen the
scuffs; reversing alone does not count as a skid.

Soil uses brown disturbed patches; sand/gravel use muted ochre, snow pale compressed marks,
wood restrained brown wear and hard surfaces faint dark scuffs. Two geometric pieces form
each stamp. Existing vanilla white-concrete grain supplies the pixel texture: no new artwork,
atlas expansion, shader, world light or scene-copy pass. A separate vanilla texture binding
and one translucent custom-geometry submission still have a small rendering cost.

Footprints clip to collision-top rectangles, including slab and stair steps, without bridging
unsupported columns. Thin snow uses its visible top. Fluids and leaves reject fitting;
powder snow provides no collision top. Unusual shapes with more than four boxes are skipped.
Cached lighting and geometry keep terrain queries out of drawing. Block changes invalidate
all cached pieces on the detected block; validation is staggered, so removal is not immediate.

## Shared work limits

| Limit | Low | Balanced | High |
| --- | ---: | ---: | ---: |
| Stored/drawn quads | 128 | 512 | 1024 |
| Direct block-state reads/world tick | 32 | 64 | 96 |
| Newly fitted pieces/light samples/world tick | 12 | 32 | 64 |
| Maintenance visits/tick, at most | 4 | 8 | 12 |
| Range, blocks | 32 | 64 | 96 |
| Lifetime, seconds at 20 TPS | 20 | 45 | 60 |
| Source requests/world tick | 8 | 16 | 24 |

All vehicles share these limits. Pending requests cap at 16 and expire after eight world
ticks; vehicle state caps at 64, with at most eight vehicles processed per world tick.
Maintenance shares the read allowance. Fitting reserves a whole column's maximum eight
pieces before starting it, so actual work can be below the nominal cap. Shape and light
helpers have their own internal lookups; the direct-read counter does not count those.
Maintenance lighting refreshes are additionally bounded by the visit cap.

Adaptive pressure divides read, fitting, source and drawing allowances, with minimums of
16 reads, eight fits, four requests and 16 drawn quads. Old marks are evicted first; under
load, some contacts intentionally leave fewer marks. The last five seconds fade out;
world changes and mode changes clear the cache. No per-frame entity-list scan is added.

## Verification

The opt-in `TrackProbe` uses a **disposable** `M6Smoke` preview world; it edits its blocks and
removes its non-player entities. Never point this fixture at a world you want to preserve.
With the preview-world setup used by the other visual probes:

```sh
./gradlew runClient -Dmcheli.probeTracks=true -I scripts/visual-probe.init.gradle --no-configuration-cache
# Add -Dmcheli.probeBackend=OPENGL for OpenGL.
```

Ten scenes cover materials, slab/step fitting, water, air, changed surfaces, actual M1A2 and
Growler contact hooks, disabled effects, combined helicopter wash/exhaust and blasts, Low,
and adaptive pressure three. The fixture controls vehicle motion but calls the real terrain
probe and real entity-tick contact hook; it does not certify player-driven vehicle physics.
It asserts queue, cache, source, read, fitting and draw limits, removal/unsupported rejection
and nonzero stock-vehicle emission. Raw per-tick counters are in
[Vulkan](performance/m8-tracks/vulkan.csv) and [OpenGL](performance/m8-tracks/opengl.csv).
JUnit tests cover clipping at large/negative coordinates, cache eviction/fading, material
contrast and contact interpolation across turns, reversing, air gaps and teleports.

Native checks use Mesa software Vulkan/OpenGL. Target-hardware performance and final driving,
sloped-terrain and addon visual acceptance remain open: RTX 3060 8 GB, Ryzen 7 7800X3D,
1080p, 80 FPS / 12.5 ms. Work bounds alone cannot certify that frame target.

Both backends completed all ten scenes (1,800 counter rows each). The final Vulkan fixture
also counted each vehicle separately: 138 tread requests and 253 tire requests during the
stock-contact scene. At pressure three, the recorded draw maximum was 128 quads. These are
work counters, not GPU timings. The local release build excludes optional JEI integration
because its Maven host is unavailable in this environment; other production sources and
the JUnit suite are included. A normal release build excludes all opt-in visual probes.
