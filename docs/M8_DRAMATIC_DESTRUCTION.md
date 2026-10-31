# Dramatic vehicle destruction

[Native vehicle examples](M8_DRAMATIC_DESTRUCTION_GALLERY.md).

Fresh vehicle destruction now has a clearer opening, visible fire-lit machinery and heavier contact
feedback. This refines the existing breakup system: aircraft keep their rear fuselages, and no new
large hull sections or destroyed-model art assets are introduced.

## Sequence

1. A newly observed destruction gets a three-tick hot core at its machinery, directed sparks, a
   couple of separating fire pockets, a metal chip and a short pressure puff. Plane ruptures follow
   exhaust direction; tank ruptures vent more upward. The existing cover blowout gets a second
   localized rupture when the opening becomes available.
2. Existing exposed machinery, torn ribs, supported cover inserts and the rare separated-turret
   ring receive a restrained amber emissive reflection. Cook-offs briefly strengthen it; the base
   reflection cools with the original 360-tick hull fire. Cockpits and intact hull skins are excluded.
3. Existing ammunition pops use a two-tick flash, uneven directional sparks, a small fire pocket
   and a pressure puff. Their strengths and sound pitches vary deterministically. Existing ammo
   eligibility, rare turret-ejection rules and event times remain authoritative. NTM/HBM weapons
   and payloads remain excluded from conventional ammunition accounting.
4. Small wreck panels receive a modest release tumble; pieces wider than three blocks retain their
   existing angular motion. This uses their recognizable original model geometry and textures.
5. Cosmetic settling accelerates and eases to rest, with a small damped give-way and a different
   final lean for tanks, helicopters and planes. Maximum extra vertical sag stays at the existing
   0.1 block. Collision, flight, suspension physics, wreck placement and crater protection are unchanged.
6. Grounded wrecks release one short, broken dust surge as they settle. Falling debris uses its
   actual fragment span for dust/sound sizing. Contact fans match the ground: soil-colored dust
   on grass, pale snow, and map-color dust on sand/stone. Sparks require hard ground. Existing water
   splashes remain separate.

## Admission and limits

The existing Enhanced/Polished quality and Vehicle Destruction Sequences switch gate the new rupture,
interior and staged-settling presentation. Classic and the disabled sequence setting retain the earlier
presentation. Contact tint/sizing improvements remain part of Enhanced debris effects.

- At most two new rupture/cook-off events per client-world tick, sharing the existing atmosphere
  particle admission. Busy scenes may omit an opening burst; it is never replayed later.
- Interior reflections: at most 768/1,536/3,072 vertices per frame on Low/Balanced/High, reduced by
  adaptive pressure. A single accepted insert has at most 768 vertices. Cached copies offset vertices
  along their normals to avoid depth flicker; the whole hull is never copied into this layer.
- At most four debris impact effect groups, two contact clangs and two settling surges per tick.
  Particle limits, loose-panel limits, field-geometry limits and collision-ray budgets are unchanged.
- A settling surge uses at most three short downward collision samples, only in loaded chunks and
  after real ground contact. It skips water and unsupported positions. Airborne wrecks emit no surge.
- Old wrecks first seen already destroyed do not replay the rupture or new interior glow. Repair
  stops new events and clears the reflection; existing short-lived particles drain normally.

Client-only presentation: no server damage, ammo consumption, terrain edits, world light blocks,
packets, crater changes or new NTM integration. The brief flashes use the existing scene-light API
alongside the default fuel-pool and engine fire lighting.

## Native validation

```sh
./gradlew runClient -I scripts/visual-probe.init.gradle --no-configuration-cache \
  -Dmcheli.probeDramaticDestruction=true -Dmcheli.probeBackend=VULKAN
```

Repeat with `OPENGL`; `-Dmcheli.probeDramaticEnd=3` runs only the three vehicle example scenes.
The opt-in fixture opens the private `M6Smoke` world in `.run/m8-preview`, holding real stock T-90,
AH-64 and F-35 models through client HP/wreck states. Debris contacts run through production motion
and collision. Other scenes cover an old wreck, the disabled setting, 16 simultaneous wrecks, Classic
quality and an airborne wreck. These are controlled client fixtures, not server crash captures.

The 60 FPS goal on an RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p remains unmeasured on target hardware.
Software Mesa validates appearance and backend compatibility.

Final validation against main's fire-lighting integration: 565 unit tests, all 297 required local
GameTests, and eight native scenes / sixteen checkpoints on each of Vulkan and OpenGL pass.
The 16-wreck load stays within 1,536 interior vertices, sixteen atmosphere births per tick and the
existing light budgets. Repairs drain all fire lights and the new interior reflection. Old/disabled/
Classic exclusions pass; airborne wrecks release no settling surge. Twelve original Vulkan PNGs and
their hashes are recorded in [the validation record](performance/m8-realism/dramatic-destruction.json).
Optional dependencies were excluded only for local dev checks; normal/real-NTM CI covers those builds.
