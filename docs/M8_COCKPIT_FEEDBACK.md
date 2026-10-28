# Cockpit feedback

Closed, authored canopies gain a small supplementary pilot instrument strip: engine, fuel,
hull and radar indicators. Engine component health, hull HP, fuel fraction and radar blanking
come from the vehicle's existing synced state. Low fuel (below 15%), heavy engine/hull damage
(below 50%) and radar blanking use warm warning colours. An absent radar stays grey.

Engine throttle drives restrained vibration of the existing cockpit assembly, at most 2 mm
per axis, and up to 0.4 GUI pixels of movement in the supplementary instrument strip.
Damage increases the vibration while the engine is running. The camera and targeting HUD
keep their existing poses. This uses the existing model draws without extra model geometry.

Heavy damage occasionally dims the small instrument backlights for three ticks in a 97-tick
cycle. Warning glyphs retain at least 80% brightness; engine-off lamps are dimmer. Repairs
restore steady lighting directly from current health, without a separate permanent fault state.
This is cosmetic feedback, not an additional electrical failure simulation.

Rain appears as sparse animated screen-space windshield droplets, with a clear central aiming
region. A vanilla rain/exposure check is cached for ten ticks, and wetness builds/dries smoothly.
The shapes use two batched, untextured GUI submissions: no refraction, scene copy, texture or
new shader. There are at most 12 / 20 / 28 droplets on Low / Balanced / High; pressure reduces
the count. This is a conservative windshield overlay, not model-fitted water simulation.

Only the local first-person pilot of an intact vehicle with an authored, closed canopy gets
the detail. Open canopies, other seats, third person, UAV/remote weapon cameras, destroyed
vehicles, menus and hidden HUD are excluded from the overlay. Looking far away from the
forward view hides the strip and droplets. Vehicle/world/view changes clear the local state.

Disable **Cockpit feedback** (`effects.cockpitFeedback`) through
ESC → MC Heli Effects → Full mod settings. Enhanced quality and Polished artwork are required.
The feature adds one local-view state and one cached weather check, without extra entity
scans, particle allocations or audio loops.

The new flight/intact-vehicle batch is tracked in [M8_PLAN.md](M8_PLAN.md). Claude owns the
wreck-art, persistent-crash aftermath and AI prediction/pursuit work separately.

[Native examples](M8_COCKPIT_GALLERY.md) and [recorded checks](performance/m8-realism/cockpit-feedback.json)
cover F-35A/F-15E/A-10 views on Vulkan and eight repair/view/settings/finite-fuel checks on
OpenGL. The 452-test local unit suite passed. Controlled client-held
states are not combat recordings. Local optional-dependency exclusions are validation-only;
the target 60 FPS at 1080p on RTX 3060 8 GB / Ryzen 7 7800X3D still needs hardware testing.
