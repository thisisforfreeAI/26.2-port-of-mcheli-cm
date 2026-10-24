# Larger explosions and crater-aware smoke

This pass deliberately raises particle allowances to give large explosions more volume.
**Preserve existing crater formation:** production changes are client presentation only.
Existing weapon damage, explosion block selection, destruction, ignition and terrain rules
continue to form the original craters; VFX adapt to their reported footprint.
The adaptive effects goal remains 60 FPS. Higher detail is not a guarantee of target-PC FPS.

| Allowance | Low | Balanced, before → after | High, before → after |
|---|---:|---:|---:|
| Shared live smoke/material particles | 384 | 768 → 1536 | 1536 → 3072 |
| Weighted smoke admission per tick, before pressure | 48 | 96 → 256 | 192 → 512 |
| Primary blast lobe ceiling | 6 | 12 → 18 | 18 → 28 |
| Broad ground dust/jet/chip requests | 0 | 6 → 10 | 10 → 16 |
| Fragment admissions per tick | 0 | 4 → 6 | 6 → 10 |
| Tracked fragments | 0 | 24 → 36 | 48 → 72 |
| Optional dust-front sectors | 0 | 10 → 14 | 14 → 20 |
| Shared dust-front admissions per tick | 0 | 12 → 20 | 20 → 28 |

Counts still scale with weapon power, so these are ceilings. Cannon primary limits stay at
four; the flash/tracer reserve, terrain read caps and draw distances are unchanged. Admission
still pays coverage/distance costs. Adaptive pressure lowers lobe counts, thins secondary
particles and drops optional dust fronts before primary blast smoke.

## Match the affected footprint

Enhanced explosions now use their already-received affected-block list. A half-block radial
histogram estimates the 90th percentile of horizontal extent, sampling at most 4096 entries
from even positions in the list. It performs no extra world reads and needs no packet change.
A few distant ray tips cannot dominate ordinary footprints. The list describes reported
explosion effects and may include positions whose blocks survive gameplay/protection rules;
this is an approximate footprint, not a guarantee of the final excavated crater boundary.

Dense cloud radius can grow to 85% of that footprint, with a maximum 2.5× profile multiplier
and ceilings of 16 blocks Balanced / 22 High. Low uses a 1.25× multiplier and 10-block ceiling.
Missing footprints retain nominal sizing; cannons retain their ordinary radius. Legacy
missile puffs scale their actual charged maximum size along with their birth positions.
Fire/flash size and brief timing retain the weapon profile, so larger smoke does not mean a
longer blinding flash. No damage, terrain destruction, projectile behavior or audio changes.

Dust fronts begin near the reported rim, sample their local height and material, and travel
outward toward a reach capped at 24 blocks. Those bounded column reads share the existing
96/192 terrain-read budgets, stop at water/unloaded terrain, and happen only at birth.
No per-particle terrain scan is added. This keeps rim dust from starting below the crater edge.
Large penetrator footprints (at least eight blocks radius) also qualify for rim dust; small
directional impacts keep their concentrated jets. Rim dust is requested after the core and
first support billows, so trailing smoke cannot consume its entire allowance first.
Particles still use the existing sprite atlas and vanilla Vulkan/OpenGL rendering paths.

## 3D explosion depth

- Selected broad, power >=8 fragment slots use chunks up to twice the old radius. They spin,
  bounce once against their already-sampled birth plane with damped velocity, then settle
  and fade. Larger chunks have a lower vertical impulse and 48–59 tick lives so a landing
  can be seen. Other fragments retain short lives. This is cosmetic plane contact, not
  arbitrary-terrain rigid-body physics; no collision/world search is added.
- Nearby dirt jets can use three tapering world-space segments at different orientations.
  These replace a jet request rather than spawning an unbounded trail emitter.
- At most two fire pockets per explosion can have a second expanding layer at an offset.
  At most four rolling-smoke billows can add two intersecting world-oriented cards.
- Alternate nearby dust-front sectors can be low, three-triangle wedge meshes. NeoForge
  registers a dedicated particle group; its state batches the existing atlas and vanilla
  particle pipeline. Geometry is actual tapered faces, not a wedge-shaped billboard.
- Extra geometry is admitted only at pressure zero, within 48 blocks Balanced / 64 High;
  Low uses simple rendering. Cloud/column extras fall back to one billboard when detail
  is shed; optional wedge sectors can disappear under load. Each extra face is charged
  at admission with an extent covering its offsets. Rejected multi-face requests can
  fall back to the existing single-card effect. Primary smoke remains available.

There are no new textures, scene copies, dynamic lights, deferred emitters or shader dependencies.
The mesh group adds a particle batch using the existing shader; it is not volumetric smoke.

## Validation

279 unit tests pass, including robust footprint sizing, empty/invalid metadata, extreme
list sampling bounds and finite quality limits. Native Vulkan/OpenGL 1080p each passed eight crater-fixture scenes (seven stock weapon
definitions plus a synthetic power-12 bomb). Additional Vulkan side and overhead
fixtures assert camera position/pitch/yaw before capture, and confirmed two crossed billows, one layered fire pocket, three columns and three/four
wedge sectors admitted together. Captures can coalesce under software rendering; the
[evidence index](performance/m8-realism/crater-depth.json) lists actual saved views/backend.
Bouncing and settled fragments were both observed. Vulkan mixed combat passed four phases /
2400 tick samples, all transient drains and shared bounds, with adaptive pressure reaching
three. [Raw telemetry](performance/m8-realism/crater-depth-combat.csv.gz) is preserved. Three additional tests verify geometry LOD/quantity
limits, charged extents and actual batched wedge vertices/triangle areas. Local builds exclude only the optional
uncached JEI API; repository dependencies are unchanged. RTX 3060 8 GB / Ryzen 7 7800X3D /
1080p 60 FPS acceptance remains a target-PC check.
