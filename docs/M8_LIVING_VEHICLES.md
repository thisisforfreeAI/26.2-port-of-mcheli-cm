# M8 living vehicle details

Implement the next eight additions separately, with native stock-vehicle examples.
Use the existing Enhanced, detail/adaptive budgets and cosmetic controls. Preserve gameplay,
legacy craters and Claude's NTM integration. Target 60 FPS on RTX 3060 8GB / 7800X3D at
1080p; software rendering validates correctness and bounded work, not target-PC FPS.

| Stage | Addition | Status |
| --- | --- | --- |
| 1 | Finite ammunition cook-off flashes, sparks and pops | Complete |
| 2 | Chipped/bent rotor variants and restrained wobble | Pending |
| 3 | Exposed interiors and torn edges behind missing panels | Pending |
| 4 | Weapon heat, discoloration, shimmer and cooling smoke | Pending |
| 5 | Wet paint, parked snow accumulation and shedding | Pending |
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
