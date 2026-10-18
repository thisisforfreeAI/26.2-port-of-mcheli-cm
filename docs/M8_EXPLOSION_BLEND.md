# M8 combined explosion presentation

Missiles, rockets, cannon hits and bombs below power four use the earlier growing McHeli smoke
puffs, with a short irregular hot flash and a few sparks. These are the existing `SmokeParticle`
sprites, growth, drag and upward/outward ejection. They cool quickly, retaining the smoke-led
character rather than becoming miniature versions of a large bomb fireball.

Large bombs and incendiaries mix those familiar puffs with faster, uneven fire lobes and a dark
smoke column. Fire expands in roughly eight ticks; smoke grows more slowly, with different rise
speeds at the base, middle and crown so it stays connected to the impact. Individual lobes cool
at different times. Stronger early flash, visible hot fragments and fast flat material dust fans
give the initial blast more energy. Original 64-pixel cloud frames replace the previous simple
32-pixel fire shapes; all shading and turbulence are baked offline, without runtime noise or a
new shader.

## Limits

The same primary request caps remain: Low/Balanced/High 6/12/18, cannon at most four, with adaptive
pressure reducing requests. Large bombs replace every sixth fire request with an earlier smoke
puff, so the combination does not stack another complete emitter on top. Small smoke replaces
the primary fire/cloud emitter entirely. Every puff charges its final radius to shared weighted
smoke admission and the engine's live smoke limit. Muzzle flashes keep their separate reserve.

Broad base radius caps at eight blocks, with variation at most 1.2 times base; ordinary weapons
cap at 3.2. Earlier puffs retain the bounded nominal-size policy, a final radius of at most 5.6
on High, and a lifetime capped at 100 ticks. New smoke lives at most 111 ticks. Dust requests stay
at six/ten on Balanced/High, sparks at eight/twelve, and optional accents shed under pressure.
The existing one-column loaded-only dust lookup remains bounded to 13 blocks. Primary effects
need no terrain ray march, collisions, entity wind searches or delayed-emission queue.

Classic, vanilla explosions, underwater effects, server damage/destruction and the network
protocol are unchanged. This document supersedes the presentation/size descriptions in
M8_EXPLOSION_STRUCTURE.md; that file's captures and timings describe the earlier PR #48 pass.

## Validation

`ExplosionSequenceTest` checks weapon-family selection, compact small impacts, power/radius caps
and pressure limits. The atlas test packs the actual 256×128 cloud sheet alongside the original
wide flash and other effect sheets. `ExplosionStructureProbe` checks shared limits, expiry,
fast-fire/slow-smoke growth separation, and captures flash, fire and smoke at ages 1/4/10/20/40/60.
Its isolated explosions deliberately receive empty affected-block lists, followed by empty,
fixed-detail and adaptive overlap frame samples. `-Dmcheli.probeStructureQuick=true` runs a short
large-bomb visual iteration; full validation omits that flag.

The final build passes 194 JUnit tests and all 225 required GameTests on the integrated M6 base.
The production jar excludes development scenes; all eight effect textures reproduce byte-for-byte.
The local build uses an external init script
excluding optional JEI because its Maven host is blocked; repository JEI sources/configuration
remain unchanged. Native checks here use software Mesa, so **80 FPS on the RTX 3060 8 GB /
7800X3D at 1080p remains a hardware acceptance check**.

Native Vulkan/lavapipe and OpenGL/llvmpipe scenes completed, including empty affected lists,
expiry/shared-limit checks and adaptive overlap. Vulkan was repeated after integrating the
latest M6 port changes. Real captures: [large fire](screens/m8-blend-vulkan-fire.png),
[dark column](screens/m8-blend-vulkan-column.png), [night flash](screens/m8-blend-vulkan-night.png),
[earlier missile puffs](screens/m8-blend-opengl-missile.png). Small-impact captures use a closer
camera; all frame-sampling scenes use the same wide camera, 1080p, four render chunks, VSync off
and a runtime cap of 120 FPS. No builds/tests or other native scenes ran during these samples.

| Software renderer | Scene | Frames | Median ms | p95 ms |
|---|---|---:|---:|---:|
| Vulkan / lavapipe | Empty | 91 | 66.805 | 81.144 |
| Vulkan / lavapipe | Fixed | 73 | 82.011 | 127.761 |
| Vulkan / lavapipe | Adaptive | 89 | 69.572 | 80.217 |
| OpenGL / llvmpipe | Empty | 93 | 68.181 | 89.506 |
| OpenGL / llvmpipe | Fixed | 74 | 84.511 | 106.516 |
| OpenGL / llvmpipe | Adaptive | 87 | 71.998 | 87.941 |

Three overlapping impacts recur every 25 ticks. End-scene particle snapshots are 67/68 at fixed
detail and 2/4 at adaptive pressure three (Vulkan/OpenGL); these are not averages or universal
savings. All scenes miss 12.5 ms on software rendering. CSVs and summaries are saved under
`docs/performance/m8-energy-lavapipe` and `m8-energy-llvmpipe`.
