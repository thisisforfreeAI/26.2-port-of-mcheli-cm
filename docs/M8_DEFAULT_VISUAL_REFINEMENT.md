# Default graphics refinement

This pass improves Enhanced/Polished M8 presentation, including the default explosion art.
The existing Modern on/off choice remains. Classic keeps its original presentation. Work
starts from Claude's NTM-removal commits; no NTM renderer, warhead bridge, settings or
workflows are restored or edited. Claude's subsequent weapon rebuild is retained from main.

| Area | Change | Cost |
| --- | --- | --- |
| Smoke | More separated base/upper billows, translucent wisps, varied cold atlas frames and less heated edge smoke | Existing particle and coverage caps; distant blasts request fewer lobes |
| Explosion depth | Cold smoke keeps cold art while receiving a brief amber tint/light boost; rolling fire pockets lose opacity as smoke covers them | Existing cached light samples; no dynamic world lights or new passes |
| Vehicles | Stronger restrained crease/edge contrast, darker authored outlet zones, explicit rubber/vent/glass part cues | Grey vertex colors baked on model load; same GPU-buffer and vertex-renderer palette |
| Terrain scars | Seeded continuous coverage variation, dark core tint and softer varied rim | Four existing vertices per tile; unchanged projection, storage and terrain-read caps |
| Ground contact | Less uniform wash sectors/gusts, local loose-material patches, varied track grain and smoothly approaching contact-shadow footprint | No additional wash samples, particles, track pieces or shadow passes |
| Distance | Fewer far primary lobes and smooth fading of optional smoke/fire planes; contact-shadow size fades to vanilla's radius | No extra geometry; admission charges retain their original bounds |

Vehicle textures and material authorship are retained. Named parts supply conservative
material cues; unnamed/merged body materials keep their authored appearance with improved
crease/edge shading. This is not a new PBR or specular renderer. Scar variation is sampled
at projection time and interpolated by the existing translucent surface pass. Its corner
opacity floor preserves small cannon scars that fit on one quad. Adjacent tiles share the
same UV field, and each mark receives a stable seed.

Original crater generation, terrain destruction, damage, ignition, projectile physics,
packet encoding and sound are untouched. Scorch, dust and track marks remain temporary
client effects on surviving surfaces. Existing live-particle, weighted admission, terrain
query, decal storage, fragment and geometry limits stay unchanged.

## Validation

284 JUnit tests passed before incorporating the subsequent main update, including checks for cold-only smoke frames, bounded lighting and
opacity, monotonic distance thinning, smooth extra-plane fades, GPU-compatible grey surface
colors, continuous scar variation and visible single-quad marks. Native Vulkan/OpenGL
comparison scenes exercise four stock vehicle models and eight weapon profiles with the
default art. Mixed combat and material/wash checks validate runtime bounds and cleanup.
The final four readability tests also pass independently, including the fire-to-smoke
opacity boundary regression. Native Vulkan mixed combat passed 2,400 samples with bounded
work and transient particle drains. Vulkan wash passed 11 material/load phases; OpenGL
tracks passed 1,980 samples across 11 phases, including ramps, removal, disabled effects
and adaptive load. Results and raw compressed CSVs are recorded in
[default-visuals.json](performance/m8-realism/default-visuals.json).

The target remains 60 FPS on RTX 3060 8GB / Ryzen 7 7800X3D at 1080p. The available Mesa
software renderer validates behavior and budgets; it cannot certify target-PC FPS. Local
native builds excluded the unavailable optional JEI API. After incorporating main, the new
NTM dependency download is restricted in this environment, so GitHub CI checks the complete
integrated build. Repository dependencies are unchanged.
