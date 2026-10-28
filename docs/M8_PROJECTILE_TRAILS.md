# Refreshed missile and rocket trails

Default Enhanced/Polished conventional rockets and missiles now leave the approved 16-frame
smoke billows, mixed with shorter wisps. Nearby samples overlap into a narrow motor stream,
then expand and drift in world space as the projectile moves and turns. Solid motors have
fuller trails; recognized cruise missiles such as the AGM-84/158, JASSM and Tomahawk use
narrower, more transparent exhaust. This is a cosmetic profile, not an engine simulation.

The weapon definition still controls whether smoke is enabled. In particular, the stock
**AGM-158 explicitly disables smoke** and remains smokeless. Authored trajectory start/end
ages now control the enhanced trail and motor flare, with a two-tick startup and six-tick
burnout fade. An unspecified end retains continuous motor presentation. Guidance, thrust,
fuses, payload damage, sounds and original craters are unchanged.

Conventional Polished motor flares are compact rather than the old large billboard. Solid
missiles retain a small optional halo, cruise motors use a much subtler point, and rockets gain
a compact flame without an extra halo. Normal bullets and existing tracer selection do not change.

## Scope and compatibility

The client effects sink replaces only the conventional `explode` smoke path for rockets,
AA/AT/AS/TV missiles and marker rockets. Classic, custom particle names, torpedo bubbles and
other projectile types keep their existing paths. HBM-enabled or NTM-payload definitions are
excluded from both the new trails and motor scaling. No NTM assets, payload bridge, gun/gore
hooks or integration calls are added.

New billows use the existing explosion/wisp artwork, ambient daylight/shade, fog and erosion
layer. There are no new atlases, shaders, lights, collision rays or gameplay entities. Each
billow remains one quad. Cold smoke does not glow.

## Limits

- Up to three samples per projectile update, inside its actual movement segment. Stationary
  samples stay bounded; non-finite or teleport-sized movement over 32 blocks is skipped.
- Optional trails stop beyond 144 blocks and simplify beyond 64 blocks or under pressure.
  Shared load rotates emission slots rather than permanently favoring the first projectiles.
- The existing global particle/coverage admission remains mandatory. Additional motor-trail
  birth ceilings are 12 / 24 / 36 per tick at Low / Balanced / High; pressure slows emission.
- At most 128 tracked projectiles and 256 cached weapon profiles. Tracks prune once per tick;
  world changes and Classic clear the state. No extra entity scan is added.
- Solid-motor core life is capped at 80 ticks for rockets or 96 for missiles; wisps use 65% of
  that life. Authored shorter ages remain within a 24-tick lower bound. Samples expand quickly
  to avoid a string of separated dots; speed-aware width remains bounded (at most 2.464 blocks
  of quad half-size). Source fluid/chunk checks prevent new smoke inside water or unloaded areas.

[Native examples](M8_PROJECTILE_TRAILS_GALLERY.md) show Hydra 70, AIM-9, AGM-84 and smokeless
AGM-158 definitions/models, with A-10, F-35A and F-15E context. These are held trajectories through
`BaseBulletEntity.spawnParticle`, not combat recordings. The authored-cutoff and NTM-bypass
scenes use isolated fixture definitions; no registered content is modified.

Pure tests cover profiles, authored timing, spacing, growth, finite bounds and distance/pressure
reduction. Native checks cover Classic, the NTM filter, motor cutoff, drain and shared load at
Balanced and Low. Software Mesa previews and local optional-dependency exclusions do not
measure the 60 FPS / 1080p target on RTX 3060 8 GB / Ryzen 7800X3D; no local JAR is distributed.
