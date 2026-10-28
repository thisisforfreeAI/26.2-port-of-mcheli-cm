# Airborne wing and tail breakup

Planes can lose an outer left wing, outer right wing or rear airframe section while they still
have hull HP. Each section is driven by its own server-authoritative component health, rather
than a generic low-hull-health threshold. This adds visual structural failure and moving mesh
debris; existing flight handling, lift, collision boxes, weapon operation and explosion craters
remain unchanged.

## Damage and persistence

`WING` (indices 0/1) and `AIRFRAME_TAIL` zones append after a plane's existing engine, rotor and
fuel slots. Their state uses the same packed component long, save data, vehicle-item component
and multiplayer synchronization as existing M9 components. No packet layout or entity-data
field is added. Old engine/rotor/fuel indices stay unchanged; previously unused intact slots
start the new components intact. Vehicles with fewer than three spare slots retain their
existing component layout. Helicopters, tanks, ships and other vehicle layouts are unchanged.

Wing zones prefer authored left/right default bounding boxes; tail zones prefer the rearmost
one. Content lacking these uses bounded outer-hull/rear-hull estimates. Direct projectile hits
still select the first zone intersected; identical-entry ties prefer the smaller zone so a broad
fallback engine volume does not swallow a specific wing/tail hit. Explosion falloff follows the
existing M9 rules. Fire, melee and generic low hull HP do not independently sever wings.

Each new structural component has the existing 35%-of-max-hull-HP component health budget and
1.0 damage share. A part separates at zero component health. The plane need not be destroyed.
Repairs restore component health through the existing repair rules and restore the section when
it is no longer disabled; full repairs restore everything. Structural damage does not change
engine thrust or the flight model in this pass. The component HUD shows **WL**, **WR** and **T**.

## Presentation

The existing Enhanced effects, vehicle damage details and destruction-sequences settings control
this feature. Supported body meshes are split into disjoint left/right/tail regions, using the
original textures, UVs and normals. A wing cut removes the outer 45% of that side's span; the rear
cut uses the existing 27%-of-body-length wreck profile. The retained fuselage exposes small metal
spars. Associated animated parts beyond a missing section are hidden in their posed coordinates.

- Left, right and tail loss combine independently. The other wing stays intact after a one-sided hit.
- Live fragments inherit the aircraft's full movement and pose (using observed travel for remote aircraft), then tumble and fall. They use
  cosmetic geometry, not gameplay entities, drops or additional explosions.
- The existing global debris limits remain eight pieces, two births per tick, 24,000 total vertices
  and two collision rays per tick. Live airframe fragments last at most 200 ticks and disappear
  when the corresponding component is repaired or destruction sequences are disabled.
- Model clipping, surface shading and impact BVH preparation happen on the bounded background
  loader. Each requested mask is cached; source meshes over 60,000 vertices or sections over 9,000
  vertices keep their original surface. Wing clipping requires a body span above five blocks,
  and rear clipping requires a body length above six blocks, avoiding cuts through tiny bodies. No per-frame clipping or dynamic-light work is added.
- A first view of an already damaged plane shows its missing sections without replaying fresh
  debris. The damaged silhouette is also applied outside the nearby debris range.
- A later wreck retains every section already lost, then adds the existing staged cover/right-wing/
  tail breakup. Cuts do not overlap or duplicate removed surfaces. Repairs clear the wreck and live
  debris through their existing bounded cleanup.
- When the visible rear section is gone, nozzle flames and engine plumes stop, and damage smoke/
  wreck effects use the exposed cut instead of an absent engine outlet.

The hardware goal remains 60 FPS at 1080p on RTX 3060 8 GB / Ryzen 7 7800X3D. Software Vulkan
previews validate rendering and behavior; they do not measure performance on that PC.

## Checks

`ComponentDamageTest` verifies separate left/right ray hits and appended slot layout on F-35A,
F-15E and A-10. `BreakupMeshesTest` verifies surface conservation for all eight combinations,
retention of the opposite wing and source immutability. Three required `LiveAirframeGameTests`
exercise real projectile damage while airborne, tail blast damage, survival, saving/loading and
full repair. Existing engine/rotor/fuel tests remain required.

The non-shipping `LiveAirframePreviewProbe`, enabled with `-Dmcheli.probeLiveAirframe=true` and
`scripts/visual-probe.init.gradle`, renders controlled airborne damage states on three stock
planes. Native captures are labeled previews, not combat captures; actual damage and persistence
are checked separately by the server GameTests.


Validation result: **374 unit tests, 271 normal required GameTests and 279 with NTM Next passed**.
The full three-plane Vulkan run passed nine live failure checks, six repairs, three toggle
cleanups and three wreck-preservation checks. [Twelve native screenshots](M8_LIVE_AIRFRAME_GALLERY.md)
and [machine-readable evidence](performance/m8-realism/live-airframe.json) are included.
