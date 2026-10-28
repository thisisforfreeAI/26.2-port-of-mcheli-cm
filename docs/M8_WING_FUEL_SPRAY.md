# Fuel spray from newly torn wings

A newly lost wing can briefly shed neutral droplets and faint vapor from its actual cut face.
The origin comes from the same clipped model edge used by live airframe breakup, transformed
with the aircraft’s body pose. A damaged wing can leak while overall hull HP remains healthy.
Droplets leave the moving aircraft behind and fall; vapor uses the approved 16-frame wisps.

The spray lasts at most 90 ticks (4.5 simulated seconds), tapers near the end, and stops new
births immediately when structural repair restores that wing. A wing already missing when
loaded into view does not replay a fresh leak. Existing particles finish within 24 ticks.

A few droplets can briefly ignite if their predicted path passes near a critically burning
engine. That changes the existing particle’s art and brightness, with no child particles,
gameplay ignition, extra damage, sound burst, fuel use or ground fire. This is cosmetic fuel
spray; oil/coolant drips and their stains keep their separate existing behavior.

## Controls and limits

Enhanced quality, Polished style, Vehicle damage details and Vehicle destruction sequences
must be enabled, matching the visible structural breakup. Classic and unsupported cut meshes
produce no new spray. Wrecks retain their existing fire and aftermath effects.

- One request per aircraft every `3 × (1 + effect pressure)` ticks, alternating active wings.
- Vapor replaces every fourth droplet request. Ignition changes the same droplet, adding no
  geometry, particle or emitter. Every new particle uses one quad.
- Births share the existing atmosphere allowance (Balanced 16/tick) and global effects pool
  (Balanced 1,536 effects plus 96 separate flash particles).
- Nearby detail fades from 32 to 48 blocks. Admission rejects unloaded/water-filled origins.
- Cut anchors are computed on the existing bounded mesh worker while clipping the airframe;
  the tick path performs no mesh scan, new entity search, terrain ray or new per-vehicle state map.
- Cold droplets cache ambient lighting; wisps use the normal smoke lighting. Only droplets
  briefly ignited beside an existing engine fire are full bright.
- Small world-space particles are cosmetic and have no liquid simulation or block collision.

No flight handling, collision, weapons, structural health rules, original craters, NTM bridge,
gun/gore hooks or copied NTM assets change. The 60 FPS / 1080p target remains subject to player
acceptance on the RTX 3060 8 GB / 7800X3D.

[Native comparisons and controlled ignition fixture](M8_WING_FUEL_SPRAY_GALLERY.md) distinguish
ordinary wing streams from the separate manual ignition test. The photographed natural streams
remain unlit when their paths miss the engine fire.
