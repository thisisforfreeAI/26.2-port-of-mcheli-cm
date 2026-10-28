# Altitude-dependent contrails

Suitable jets leave thin pale trails at their authored engine outlets in high, cold air.
The trails follow the flight path, remain in world space, widen with age and drift with the
shared wind. They use ambient lighting without an emissive core. Hot exhaust, damaged-engine
smoke and short wet-turn wing vapor remain separate effects.

Altitude is scaled to Minecraft's vertical space: the onset is `160 + biomeTemperature * 24`,
with temperature clamped to −0.5…2 and strength increasing over the next 80 blocks. Speed and
throttle also gate strength. Biome temperature is sampled at most once per 20 ticks per plane;
this is a cosmetic approximation, not an atmospheric simulation. Strength rises and falls
smoothly as conditions change.

Grounded, submerged, unpowered, destroyed or deflected-nozzle aircraft stop new trails.
A missing tail also stops emission. Positions come from existing jet engine outlets or
authored nozzle/exhaust anchors; unsupported aircraft gain no guessed outlets. F-35A, F-22A
and B-2A are covered by the native examples. The stock F-15E has no such outlet and deliberately
does not emit this detail.

Enhanced quality and Polished style are required. **Aircraft condensation** in
ESC → MC Heli Effects → Full configuration (`aircraftCondensation`) controls both wing vapor
and contrails. Disabling it stops new particles; existing trails fade naturally.

## Shared limits

- One tapered quad per particle, existing 16-frame wisp alpha and the wing-vapor material.
  Fixed 96-tick life, bounded width and smooth birth/end fade. No new assets, render pass,
  collision queries, entities or extra entity scan.
- Cold detail reaches 256 blocks on Balanced/High and 96 on Low, fading over the final half
  of the range, capped at 96 blocks. Other atmosphere effects retain their 80-block scope;
  general explosion and smoke admission ranges are unchanged.
- Near cadence is 6 / 3 / 2 ticks on Low / Balanced / High. At most two samples per outlet
  connect fast nearby motion; distant emission is at least eight ticks apart. Shared load
  and adaptive pressure reduce cadence further. At most four authored outlets are considered.
- Births use the existing shared atmosphere allowance of 8 / 16 / 24 per tick, plus the
  existing global smoke count and screen-coverage admission. Distant births are capped at two
  per tick and stop before using the final half of the atmosphere allowance.
- At most 128 atmosphere states, with at most 64 retained distant states. Existing nearby
  states that move away are included in that limit. New distant states are only admitted
  while the total count is below 64, leaving space for nearby vehicles.
- Distant-only states retain engine heat but do not start nearby audio or claim ownership
  of damage smoke. Missing-tail and configuration changes stop new contrails promptly.

Pure tests cover height/temperature/speed/throttle, invalid inputs, smoothing, footprint and
state limits, and contrail-only distance admission sharing the existing budget. Native checks
cover three supported jets, observed remote motion, low altitude, engine-off, settings,
Classic, unsupported outlets, a distant observer and shared/mixed loads. The mixed test first
tracks 97 nearby aircraft, then moves 96 away and verifies both retained-state pruning and
continued nearby damage smoke. All scenes must drain after shutdown.

[Native gallery](M8_CONTRAILS_GALLERY.md) · [Recorded results](performance/m8-realism/contrails.json)

The controlled previews use held trajectories rather than combat. Local optional-dependency
exclusions are for validation only; no local JAR is distributed. Software Mesa does not establish
60 FPS at 1080p on the target RTX 3060 8 GB / Ryzen 7 7800X3D.
