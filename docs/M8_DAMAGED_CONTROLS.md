# Damaged aircraft control surfaces

Supported stock body meshes gain small cosmetic trailing panels for ailerons, rudders and
elevators. The panels retain the original texture and UV coordinates. This approximates control
surfaces in models that fuse them into `$body`; it does not add aerodynamic simulation.
Healthy aircraft use their original body geometry unchanged. Unsupported or oversized cuts
are skipped.

Wing/tail component damage drives restrained flutter and sag. Below 75% component health,
control motion grows with damage and airflow; severe hull damage adds a smaller fallback.
At 12% component health or less, suitable panels can separate as small textured fragments.
The aircraft remains governed by its existing flight, weapon and collision rules. Later whole
wing/tail fragments omit panels already shed. Repairs reduce flutter, restore the cosmetic
panels and remove detached control pieces.

The effect uses Enhanced quality, Polished style, Vehicle damage details and Vehicle destruction
sequences. Classic retains its prior presentation. Re-enabling effects or loading an already
damaged aircraft displays its current silhouette without replaying a fresh breakaway.

## Attachment and rendering

- Up to six panels are selected conservatively from existing main-wing and tail sections,
  using face orientation to distinguish horizontal surfaces and vertical fins.
- Leading hinges remain in aircraft body space, including yaw, pitch and roll. A narrow
  central fin keeps both textured sides on one hinge.
- Flutter varies unevenly over time, strengthens with motion and fades between 28 and 40
  blocks. Stationary controls retain damage sag without wind-driven vibration.
- Panel groups keep the original body coordinate space. Newly fitted impact marks follow
  their panel pose; pre-existing marks on replaced mesh groups retain the existing discard
  rule at the initial partition or later mesh replacement.
- All skin pieces partition the existing mesh; there is no extra texture, shader, light,
  particle emitter or gameplay entity.

## Bounds

The existing breakup worker/cache remains limited to one worker, four pending builds and
32 cached results. Each accepted panel has at most 1,200 vertices and 9% of the original
surface area; all panels together remove at most 22%. Total prepared vertices are capped at
72,000 and bounded relative to the source. Clipping preserves surface area and source data.

Breakaway meshes use the existing global LoosePanels allowance: eight live pieces, two births
per tick, 24,000 total piece vertices and two round-robin collision rays per tick. Those limits
are unchanged. Control pieces inherit bounded observed flight motion, live at most 120 ticks,
and clear when repaired or when their effect setting is disabled. No new per-world entity scan
or unbounded geometry cache is added.

[Native comparisons](M8_DAMAGED_CONTROLS_GALLERY.md) cover the F-35A, F-22A and A-10 plus
loaded damage, settings transitions and shared load. The opt-in fixture uses real component
values on held aircraft; it is not a player-driven combat recording. Tests cover source/UV
conservation, panel limits, central fins, whole-wing continuity, hinge attachment and finite
bounded motion. Target-PC 60 FPS at 1080p on RTX 3060 8 GB / 7800X3D remains an acceptance check.

Original craters, gameplay damage, handling, collision and NTM Next integration are unchanged.
