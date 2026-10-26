# M8 living vehicle details

Implement the next eight additions separately, with native stock-vehicle examples.
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
| 6 | Stronger wakes, shallow-water spray and debris splashes | Pending |
| 7 | Terrain buildup on lower panels and exhaust soot | Pending |
| 8 | Flexible antennas, vibrating covers and loose cables | Pending |

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
