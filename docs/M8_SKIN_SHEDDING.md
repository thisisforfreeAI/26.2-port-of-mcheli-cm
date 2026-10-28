# Small skin fragments after aircraft breakup

Fresh wing and tail losses can shed a few tiny painted-metal pieces after the main structural
fragment separates. The pieces retain the aircraft's original texture coordinates and tumble
with its observed flight motion. They use ambient lighting and fade quickly; they do not glow.
Existing craters, damage, collision and flight rules are unchanged.

Up to three chips are clipped from the actual removed skin near its cut boundary. The clipping
keeps original position, UV and normal data. Selection follows the upper cut skin and rejects
missing, oversized or unsupported pieces. Control panels already shed are excluded before chip
selection. Loading old damage, switching from Classic, or re-enabling details does not replay
fresh shedding. Component repairs and effect-setting changes clear affected pieces.

The feature uses Enhanced/Polished, Vehicle damage details and Vehicle destruction sequences.
It appears only within the existing 40-block breakup detail range. Fresh wreck-stage wing/tail
losses use the same mechanism; old saved wrecks do not replay it.

## Bounds

- At most three cached chip shapes per section, each at most 96 vertices and 0.25 square blocks
  of surface. They use the existing single breakup worker, four pending builds and 32-entry cache.
- One shedding attempt per eight ticks per cut, slowed by adaptive pressure, only 8–40 ticks
  after a fresh loss. Each accepted chip lives at most 36 ticks and fades over its final 15 ticks.
- The existing shared LoosePanels limits remain eight live meshes, two births per tick,
  24,000 total mesh vertices and two round-robin collision rays per tick. Whole wings, controls
  and skin pieces compete for those same slots.
- Skin pieces reuse the existing birth light sample and landing/water response, without new
  entity scans, gameplay entities, textures, shaders or lights. Settled chips do not add fluid stains.

Tests verify bounded size, original UV interpolation, source immutability and unsupported-input
fallback. [Native examples](M8_SKIN_SHEDDING_GALLERY.md) use held component states on real vehicle
models, rather than player-driven combat. Local previews use software Mesa and the existing
optional-dependency exclusions; no locally built JAR is distributed. The 60 FPS / 1080p target
on RTX 3060 8 GB / Ryzen 7800X3D remains a target-PC acceptance check.

Claude's NTM Next weapon integration and gore effects remain separate.
