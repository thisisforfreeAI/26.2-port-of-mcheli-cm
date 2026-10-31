# Fire lighting examples

Unedited 1280×720 Vulkan screenshots from controlled client HP/wreck states, using real stock
vehicle models and the production fire emitters. These are lighting fixtures, not server crash
captures. The smoke/flame animation continues normally between scenes.

## AH-64 at night

| Realistic Lighting off | Realistic Lighting on |
| --- | --- |
| ![AH-64 fire without surrounding light](screens/m8-fire-lighting/m8-fire-light-ah64-lighting-off.png) | ![AH-64 fire casting warm ground light](screens/m8-fire-lighting/m8-fire-light-ah64-lighting-on.png) |

Two main sources illuminate the fresh wreck: its hull fire and its fuel pool. Small nearby flame
sprites share those lights.

## Fuel still burning after the hull fire ends

![AH-64 late fuel fire](screens/m8-fire-lighting/m8-fire-light-ah64-late-fuel-fire.png)

The pool keeps its own light after the shorter hull fire ends.

## Damaged engine and repair

| F-35 engine burning | F-35 after repair |
| --- | --- |
| ![F-35 damaged-engine light](screens/m8-fire-lighting/m8-fire-light-f35-engine-fire.png) | ![F-35 repaired with fire light drained](screens/m8-fire-lighting/m8-fire-light-f35-repaired.png) |

## Compatibility renderer and shared load

| GPU model buffers disabled | 24 wrecks sharing the existing budgets |
| --- | --- |
| ![AH-64 compatibility lighting](screens/m8-fire-lighting/m8-fire-light-ah64-compatibility.png) | ![Shared wreck lighting load](screens/m8-fire-lighting/m8-fire-light-shared-load.png) |

The compatibility renderer uses a local lightmap lift; directional amber vehicle lighting remains
on the GPU path. The load check also deliberately fills all 96 source slots before ignition to
verify weaker flame lights can be evicted for the main fires.

## Cooled wreck

![AH-64 after its fire lights drain](screens/m8-fire-lighting/m8-fire-light-ah64-cooled.png)

Smoke can linger after the fuel burns out. It does not keep emitting light.

See [implementation and limits](M8_FIRE_LIGHTING.md). These software-renderer checks do not measure
the 60 FPS target on the user's RTX 3060 / Ryzen 7 7800X3D.
