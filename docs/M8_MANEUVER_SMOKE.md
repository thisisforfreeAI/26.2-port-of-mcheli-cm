# Maneuver-readable aircraft damage smoke

Default Polished aircraft damage smoke now uses the approved 16-frame billow artwork. Dense
cores mix with softer, shorter wisps; seeded variation breaks up repeated sizes and shades.
Billows expand gradually, curl briefly, rise slowly and drift with the shared wind. Mature
smoke stays in world space rather than moving with the aircraft, leaving its turns readable.

The emitter uses authored engine/outlet anchors and the existing damaged-tail opening. It
inherits only a small share of bounded flight motion. For remote aircraft with zero reported
velocity, the existing observed-position fallback supplies that motion. Teleport-sized jumps
and non-finite movement are rejected by the existing motion guard.

Dense emission varies in opacity with the existing uneven damage surges. Severe damage keeps
its cores between surges; weaker damage can release lighter wisps through quiet intervals. Close, fast aircraft may use a
two-tick emission interval; ordinary detail uses three ticks, Low or distant detail uses six.
Adaptive pressure slows these intervals further. These requests still compete for the existing
shared admission allowance, so multiple aircraft cannot multiply its global limits.

## Appearance and cost

- Explosion/wreck billow frames supply dense cores; the wisp sheet supplies dissolving edges.
  Missing sprites retain the previous artwork fallback.
- Core growth starts at 40% of its target radius, then reaches 105%; wisps start at 28% and
  spread to 110% while staying more transparent. Admission charges the larger footprint.
- Core life is 33–63 ticks and wisp life 21–51 ticks, depending on severity and small birth
  variation. At equal severity wisps expire earlier. Repairs stop new emission; existing smoke
  drains naturally. Classic and disabled damage details do not emit these new billows.
- Smoke samples existing ambient daylight/shade. It adds no glow, world light, texture, shader,
  extra rendering quad or collision query. Existing engine-fire presentation stays separate.
- The existing 128-state / 80-block atmosphere scope and configured particle limits remain.
  Atmosphere births share the unchanged 8 / 16 / 24 per-tick allowance at Low / Balanced / High.
  No extra entity scan, path-history collection or unbounded cache is added.

Pure checks exercise gradual bounded growth, early wisp dissipation and birth reduction with
distance, pressure and Low settings. [Native examples](M8_MANEUVER_SMOKE_GALLERY.md) use held
HP and motion fixtures on real vehicle models; they are not combat recordings. Old particles
are also tracked across a turn to check that smoke drifts independently of the current aircraft.

Local software Mesa previews and optional-dependency exclusions do not establish the target-PC
60 FPS / 1080p on RTX 3060 8 GB / Ryzen 7800X3D. Original craters, flight handling, gameplay damage
and Claude's NTM Next integration are unchanged.
