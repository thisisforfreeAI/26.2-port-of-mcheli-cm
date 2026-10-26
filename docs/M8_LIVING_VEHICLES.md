# M8 living vehicle details

All eight additions are implemented in separate stages, with native stock-vehicle examples.
See the [24-image native gallery](M8_LIVING_VEHICLES_GALLERY.md).
Use the existing Enhanced, detail/adaptive budgets and cosmetic controls. Preserve gameplay,
legacy craters and Claude's NTM integration. Target 60 FPS on RTX 3060 8GB / 7800X3D at
1080p; software rendering validates correctness and bounded work, not target-PC FPS.

| Stage | Addition | Status |
| --- | --- | --- |
| 1 | Finite ammunition cook-off flashes, sparks and pops | Complete |
| 2 | Chipped/bent rotor variants and restrained wobble | Complete |
| 3 | Exposed interiors and torn edges behind missing panels | Complete |
| 4 | Weapon heat, discoloration, shimmer and cooling smoke | Complete |
| 5 | Wet paint, parked snow accumulation and shedding | Complete |
| 6 | Stronger wakes, shallow-water spray and debris splashes | Complete |
| 7 | Terrain buildup on lower panels and exhaust soot | Complete |
| 8 | Flexible antennas, vibrating covers and loose cables | Complete |

No new damage, secondary craters, gameplay entities or world-light updates. Prefer authored
anchors, cached geometry and existing render pipelines. Unknown model parts stay intact.

## 1. Ammunition cook-offs

Fresh burning wrecks with conventional ammunition produce up to four small flashes and sharp
pops, with three tiny sparks and a short puff. Empty stores and already old wrecks skip the
sequence. Two pops per world tick, 40-block range and shared atmosphere/particle admission
bound the work. NTM payloads are excluded. Native M1A2/AH-64/F-35A checks verify three
pulses each, matching sound requests and untouched terrain. Audio needs an audible-client check.

## 2. Rotor damage

Badly damaged helicopters use loader-built bent and shortened/chipped rotor meshes, plus
less than half a degree of wobble. One authored blade instance is affected per rotor; a
combined rotor mesh damages one arm. Original geometry, lift and control remain unchanged.
Eight cached groups, four pending jobs and 24,000 vertices per source group bound the work.
Native AH-64/MH-60L DAP/MH-6 previews show two damaged rotor assemblies each.

## 3. Exposed interiors

Removed canopies gain torn rails and exposed hinges while preserving the existing cockpit.
Suitable flat hatches reveal dark recessed backing, sidewalls and mechanical crosspieces.
Geometry builds off-thread; 32 cached panels and four pending jobs bound loading. Shared
per-view detail geometry is capped at 4,096/12,288/24,576 vertices and sheds with pressure.
Native F-35A/F-15E/A-10 checks cover wreck detachment, inserted geometry and repair removal.
Unknown/oversized parts retain their authored geometry, and gameplay canopy access is unchanged.

## 4. Weapon heat

Real client shot events warm conventional guns; repeated events in the same tick do not
multiply heat. Ammo families sharing a barrel share temperature. Named/slender gun groups
receive restrained temper discoloration, heat uses the existing shared refraction pass, and
small cooling puffs follow the posed barrel tip after firing stops. No barrel or bullet glow,
overheating damage or firing changes. Thirty-two warm mounts, four cooling puffs per tick and
shared particle/geometry admission bound cost. `effects.weaponHeat` controls this layer.
Native M1A2/AH-64/T-90 warmup and cooling previews pass.

## 5. Weather

Exposed paint wets and dries gradually without losing its baked panel/vent contrast on either
rendering path. Cold precipitation coats parked upper surfaces; movement shakes loose small
white flakes. Covered vehicles do not accumulate fresh weathering. Sixty-four states, eight
climate samples per tick, four shedding particles and shared geometry admission cap the work.
`effects.vehicleWeather` controls this layer. Native rain/snow/movement checks pass on the
M1A2/AH-64/F-35A. All new atmosphere particles share a world-tick allowance regardless of
subscriber order.

## 6. Water interaction

Moving hulls and wheels leave broken V-shaped foam and small spray. Existing panel collision
checks trigger a splash when debris enters water. Sixty-four short-lived wakes, four births,
twelve source-fluid reads and four support checks per tick bound work; shared particle admission
also applies. No buoyancy, water blocks or gameplay collisions change.
`effects.vehicleWaterDetails` controls it. Native M1A2/Growler/Zodiac previews pass.

## 7. Terrain buildup

Lower panels gradually collect irregular dust/mud on loose soil and sand. Parking preserves it;
rain and water wash it away. Running engines leave restrained soot near authored outlets, with
a rear-deck fallback on tanks. Four ground probes per tick, 64 states and cached overlay meshes
bound cost. `effects.vehicleSurfaceBuildup` controls it. M1A2/AH-64/F-35A native previews pass.

## 8. Small moving details

Audited M1A2/T-90 antenna triangles are split into two flexible segments on the loader,
preserving source UVs and unrelated geometry. Authored plane nozzle covers vibrate slightly
under power; absent parts are skipped. Two short segmented cables hang behind supported missing
panels, sharing the overlay vertex allowance. Sixteen cached groups and four pending jobs bound
loading. `effects.vehicleSmallMotion` controls it. Native M1A2/T-90/F-35B checks pass; the F-35B
has the authored nozzle assembly required for that example. Unknown add-on geometry stays intact.

## Validation and controls

New standalone layers default on in Enhanced quality. Weather, water, terrain buildup, weapon
heat and small motion have separate client config switches listed above. Rotor damage, cook-offs
and interiors use the existing vehicle damage-details control. The shared detail/adaptive budget
continues to cap overlays and atmosphere particles. No target-PC frame-rate claim is made.

The non-shipping fixture is `LivingVehiclePreviewProbe`, enabled only through
`scripts/visual-probe.init.gradle` with `-Dmcheli.livingPreview=1..8`. It renders controlled
cosmetic states through production renderers, asserts behavior and writes unedited screenshots.
Evidence is recorded in `docs/performance/m8-realism/living-*.json`. Native local checks use
compile-only stand-ins for unavailable optional JEI/NTM dependency signatures; they are not
a distributable build or an NTM runtime test. Repository CI builds the normal integration and
runs GameTests both with and without NTM Next.

Final polish replaces regular wake lines with softer irregular foam, clips soot to a bounded
region even on large hull triangles, and excludes all parsed NTM payloads from cook-offs.

Final local validation: 362 unit tests pass; all eight Vulkan stages pass on three vehicles
each. OpenGL also passes wet-paint/snow checks through CPU model submission and moving-detail
checks through GPU model buffers. These are Mesa correctness checks at 1280×720, not RTX 3060
performance measurements. The gallery contains 24 original PNGs with recorded hashes.
