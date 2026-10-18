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

The production build excludes development scenes. The local build uses an external init script
excluding optional JEI because its Maven host is blocked; repository JEI sources/configuration
remain unchanged. Native checks here use software Mesa, so **80 FPS on the RTX 3060 8 GB /
7800X3D at 1080p remains a hardware acceptance check**.
