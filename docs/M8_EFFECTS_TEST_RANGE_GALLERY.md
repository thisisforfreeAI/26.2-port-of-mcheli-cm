# Effects test range: real impacts

Unedited captures of real production projectiles detonating on the temporary range pads.
The exterior Vulkan views use the same observation position and 60° FOV at 1280×720, with
Balanced / Polished / Current M8 effects. Smoke frames are taken 24 client ticks after the
projectile disappears; these stills do not necessarily capture the brief peak flash.
The different apparent sizes and crater shapes come from the existing weapon definitions.

## Hydra 70 rocket — sand, daylight

![Real Hydra 70 impact cloud on sand](screens/m8-effects-range/m8-range-hydra-sand-smoke.png)

## AGM-158 JASSM — soil, dusk

![Real AGM-158 blast smoke over the soil pad](screens/m8-effects-range/m8-range-agm158-soil-smoke.png)

## Mk 82 bomb — stone, daylight

![Real Mk 82 free-fall bomb impact on stone](screens/m8-effects-range/m8-range-mk82-stone-smoke.png)

## GBU-57 penetrator — soil, night

![Real GBU-57 impact with its existing crater, fire and smoke](screens/m8-effects-range/m8-range-gbu57-soil-night-smoke.png)

## Water and presentation checks

These OpenGL scenes use actual impacts as well.

![Hydra 70 water impact](screens/m8-effects-range/m8-range-hydra-water-smoke.png)

![Hydra 70 with Classic effects](screens/m8-effects-range/m8-range-classic-hydra-smoke.png)

![Mk 82 with Modern effects](screens/m8-effects-range/m8-range-modern-mk82-smoke.png)

## Player controls

The 960×720 control captures use a 320×240 GUI. Enter/Return, the comparison controls and
reset all fit. The displayed FPS belongs to the software Mesa preview; it does not establish
performance on the requested GPU.

![Test range entry panel](screens/m8-effects-range/m8-range-entry-menu.png)

![Ready range controls](screens/m8-effects-range/m8-range-ready-menu.png)

[Controls, isolation and limits](M8_EFFECTS_TEST_RANGE.md). The native run also verifies return
to the original position/mode and that range lighting leaves the original world's clock alone.
