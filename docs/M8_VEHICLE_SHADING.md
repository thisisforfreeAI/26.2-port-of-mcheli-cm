# M8 vehicle surface shading

The realism follow-up starts with restrained model-space crease shading. Flat panels stay
neutral, recessed geometry darkens by at most 12%, and convex edges gain at most 3%.
Existing textures, normals, sky/block lighting and animated part transforms remain in use.
This is baked geometry shading, not ambient occlusion from surrounding terrain, specular
reflection, or newly painted texture detail. Flat texture-only panel lines need authored art.

`effects.vehicleSurfaceShade` defaults to true, applies in Enhanced mode to intact vehicles,
and is bypassed for thermal vision, iron-curtain tint, destroyed tint, and emissive draws.
Classic retains the original vertex colors.

The vehicle model loader computes one float per vertex using neighbouring triangle edges
at identical positions within each group. Normals determine whether the neighbourhood is
concave or convex. Each group is processed independently so moving parts do not bake shadows
from parts that later move away. This cannot darken a rotor hub solely because another part
sits above it. No rays, new textures, shaders, draw calls, or world reads are added.

The bake runs on the existing model-loading path, before models are published for rendering.
A weak-key cache releases shading when its model group is evicted/reloaded. Groups over
60,000 vertices fall back to ordinary lighting. Cached packed colors cost four bytes per vertex
(12.5% of the existing eight-float vertex data); rendering adds a lookup per group and reads ready vertex colors. Tint rounding/clamping
runs at model load, rather than per vertex per frame. Hardware cost and visual acceptance still require measurement.

Tests cover neutral flat panels, concave/convex bounds, degenerate geometry, oversized addon
fallback, alpha preservation and channel clamping. Native checks use the existing follow-up
fixture's smoke scenes and AH-1Z/CH-47/F-35A draws. Further per-vehicle texture authoring,
contact-shadow work and stock/addon visual acceptance remain open.

## Validation of this slice

The complete existing follow-up fixture passed on both Mesa Vulkan and OpenGL at 1080p:
16 scenes / 1,920 tick samples per backend, including smoke-led rockets, penetrators,
large cruise impacts, ground dust, water, heat/depth occlusion, AH-1Z, CH-47, jet,
Low/disabled/adaptive/cooldown and isolated dust fronts. Its work-limit assertions passed;
[summary](performance/m8-realism/native-summary.json) records maxima, not per-tick rates.
These are functional software-renderer checks, not target-GPU performance results or a
full artistic review of all stock/addon vehicles. The new geometry tests validate shade
bounds; the native fixture confirms the integrated render path loads and draws.

Delayed blast-smoke amber lighting now uses the same local age as its animated sheet, so
lighting does not expire while the lobe is still hidden by its stagger delay.

The focused rendered-vehicle fixture also passed on Vulkan and OpenGL: stock M1A2
Balanced/High/disabled/Classic shadows fit 36/49/25/25 support pieces; shading toggles
and 142 baked-color groups were checked. Night, roof shade and boundary blast captures
completed, with delayed amber block light 7 after the lobe's stagger delay. See the
[focused summary](performance/m8-realism/focused-summary.json). This is a focused integration
check, not certification of every stock/addon texture or the hardware frame-rate goal.

Billow curling now decays between ages 8 and 40, allowing late smoke lobes to converge on
the shared wind instead of maintaining divergent curl headings. A trajectory test covers
opposing curl directions and their convergence; particle requests and lifetimes are unchanged.

Both Classic switches are respected: `effectQuality=CLASSIC` or `effectStyle=CLASSIC`
disables the new vertex shading. The combined atmosphere fixture now checks rendered
vehicle states after changing style, rather than only checking the effects' state caches.
