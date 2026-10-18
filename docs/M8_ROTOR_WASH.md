# M8 rotor-wash presentation

Enhanced helicopter wash now uses short, flat wisps spreading along the ground, with mild
swirl and a smooth response to rotor height, throttle and forward speed. This replaces the
rising explosion-smoke presentation for helicopters. Classic retains its original emitter,
including its random sequence; plane/ship/ground-vehicle sea-only wake emitters retain their
existing presentation. Aircraft flight, rotor animation and gameplay are unchanged.

## Appearance

The emitter uses content-defined lift-rotor positions, rotated with the helicopter. Coaxial
origins within one horizontal block share a footprint; vertical tail rotors are ignored.
At most four distinct lift origins are used. Definitions without suitable axes fall back to
one origin two blocks above the vehicle centre. Fold-capable helicopters suppress wash while
the blades are folded/folding.

Particles spread in uneven patches with outward velocity and a small tangential component.
They slowly bend and lose horizontal speed, with no smoke buoyancy, collision or rotor-entity
searches. Their centres rise only 0.008 blocks per tick over a 24–34-tick lifetime. Four original
32-pixel animation cells give the wisps their flat silhouette, fade in over four ticks, and fade
out over ten. Ambient light uses the existing cached smoke lookup.

| Surface | Presentation |
|---|---|
| Grass/podzol/mycelium | Sparse, low-opacity earthy dust |
| Dirt/mud/farmland | Moderate material-tinted dust |
| Sand/red sand | Fuller tan dust |
| Snow | Fine pale powder |
| Water | Short cool-white spray wisps and faint broken surface ripples |
| Stone/other solid surfaces | Sparse, restrained material-tinted dust |

Throttle fades in above 10%, reaching full power at 80%. Height response fades smoothly to
zero within a bounded 12–28-block search distance, measured from each rotor to the sampled
surface. Faster travel reduces wash intensity and offsets the emitted cloud behind the aircraft.
The maximum wash radius is ten blocks; individual wisps reach at most 1.8-block quad radius.
Water ripples reach at most 2.5-block quad radius, live 22 ticks, and disappear when their water
is drained or unloaded. No water blocks are edited.

## Work limits

| Quality | Maximum candidate wisps per emission tick | Base interval | Emission range |
|---|---:|---:|---:|
| Low | 1 | 3 ticks | 48 blocks |
| Balanced | 2 | 2 ticks | 72 blocks |
| High | 2 | 1 tick | 96 blocks |

Material density and height/throttle strength thin those candidates before admission. Adaptive
pressure multiplies the interval by 2/3/4 at levels 1/2/3. All wisps and ripples spend the existing
weighted smoke admission and live limits; weapon flashes keep their independent allowance.
Water can add at most one ripple per helicopter every eight ticks, only above Low and at
pressure zero. No extra scene copies, dynamic lights or shader families are added.

Loaded-column samples refresh at most every four ticks while their column/height remains the
same; moving to a different column refreshes immediately. Actual spawn columns are sampled as
well as rotor centres, fitting steps and avoiding dust floating at a cliff's old height.
Leaves and non-water fluids reject the sample. Each search examines at most 29 blocks and
never loads chunks. A global cap of 256 block-state lookups per game tick and a 128-entry cache
bound terrain work across all helicopters. Budget exhaustion declines the remaining work.
Another bounded 128-entry emitter clock prevents duplicate bursts when client clock updates
repeat a game tick. World changes clear both caches. Rendering performs no terrain searches.

## Repeatable native check

Use the disposable flat-world setup in [M8_PIXEL_EFFECTS.md](M8_PIXEL_EFFECTS.md), with four
render chunks. This scene clears entities and changes blocks/time inside that isolated copy:

```sh
./gradlew runClient -Dmcheli.probeRotorWash=true \
  -I scripts/visual-probe.init.gradle --no-configuration-cache
```

Add `-Dmcheli.probeBackend=OPENGL` for OpenGL. The scene requires 1920×1080. Unrendered fixture
helicopters invoke the actual common helicopter emitter; their positions/throttles are driven
by the probe instead of flight physics. It captures Classic sand, enhanced sand/grass/snow/water,
forward motion and a high-altitude control. It checks lookup/cache limits each tick, emission
on supported surfaces, shared ripple limits, ripple removal after draining water, and no wash
above ground reach.

The final phases time empty, eight Classic helicopters, eight enhanced helicopters and eight
adaptive helicopters. Screenshots precede timing windows. Raw intervals use the existing
`scripts/summarize-effect-frames.py` helper. A software-renderer overload must activate detail
thinning. Probe sources are opt-in and excluded from normal builds.

The target remains 80 FPS / 12.5 ms on RTX 3060 8 GB + Ryzen 7 7800X3D at 1080p; software
renderer checks cannot certify that hardware target.

## Native results and measurements

The full scenes pass on Vulkan/lavapipe and OpenGL/llvmpipe at 1920×1080. All supported surfaces
emit wash, the high-altitude control emits none, duplicate calls in the same game tick add no
particles or terrain lookups, and ripples share smoke limits and disappear after water removal.
The fixed eight-helicopter scene peaks at 96 sampled block states and eight emitted particles
in a game tick, inside the global sampling/admission ceilings. The final single-quad counts
are 728 Classic versus 223 enhanced on Vulkan and 728 versus 227 on OpenGL. Adaptive detail
reaches pressure 3 on both software backends and ends with fifteen particles.

Raw timing intervals and summaries:
[Vulkan](performance/m8-wash-lavapipe/summary.json),
[OpenGL](performance/m8-wash-llvmpipe/summary.json).

| Software renderer | Empty median / p95 ms | Classic median / p95 ms | Enhanced median / p95 ms | Adaptive median / p95 ms |
|---|---:|---:|---:|---:|
| Vulkan lavapipe | 67.74 / 81.90 | 112.34 / 142.63 | 78.82 / 110.28 | 78.35 / 195.57 |
| OpenGL llvmpipe | 72.00 / 109.13 | 115.48 / 161.96 | 84.00 / 137.21 | 72.49 / 126.52 |

The short fixed-detail samples show lower median frame time than Classic in this fixture,
with fewer particles. Tail variation, including the Vulkan adaptive p95, prevents claiming a
consistent FPS gain from adaptive detail. Empty scenes already miss 12.5 ms on these software
renderers. No build/test process ran during timing; this remains a fixture comparison rather
than hardware combat/flight acceptance.

Thin snow and powder snow have visible tops without normal solid collision support. The
focused `-Dmcheli.probeWashSnowCheck=true` variant checks both sequentially, using their visual
surface heights. Use it alongside `-Dmcheli.probeRotorWash=true` and the same init script.

The focused thin-layer/powder-snow scene passes on both backends. Native captures:
[Classic sand](screens/m8-rotor-vulkan-classic.png),
[enhanced sand](screens/m8-rotor-vulkan-sand.png),
[grass](screens/m8-rotor-vulkan-grass.png),
[water](screens/m8-rotor-vulkan-water.png),
[OpenGL moving wash](screens/m8-rotor-opengl-moving.png), and
[OpenGL thin snow](screens/m8-rotor-opengl-snow-layer.png).

The combined `build runGameTestServer` passes **190 JUnit tests and 209 required GameTests**,
with zero failures. The normal jar includes the wash classes/textures and excludes development
probes; all six effect assets reproduce byte for byte from the generator. As in earlier M8
validation, an external local init script excludes optional JEI sources/dependency because its
API Maven host is blocked here. Repository build configuration and JEI sources are unchanged;
a full JEI-enabled release build and target-PC FPS acceptance remain open.
