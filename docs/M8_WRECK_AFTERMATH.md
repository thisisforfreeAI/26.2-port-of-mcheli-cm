# M8 wreck aftermath

The seven requested refinements preserve gameplay collision, vehicle physics, original
craters and the NTM integration. Enhanced quality and vehicle damage details control them.

- Selective breakup removes up to two supported canopies, hatches, covers or small running-gear/
  helicopter weapon parts. Aircraft outer wing geometry sags and loses a few tip triangles;
  existing damaged rotor variants retain chipped tips. Unknown hulls are not cut apart.
- Charcoal body tint retains panel shading; loader-built surviving-paint and bare-metal patches
  break up the former nearly black silhouette. Source geometry and UVs remain unchanged.
- Supported panel openings retain torn frames, machinery and hanging cables. Flat bays gain a
  crushed mechanical block; unsupported covers receive no invented opaque cockpit floor.
- Existing authored engine origins focus fire and embers. Pockets stay anchored longer instead
  of hopping rapidly between outlets. Early fire lasts longer, then gives way to cooler smoke.
- Render matrices add restrained body lean/sag, turret tilt and collapsed landing-gear poses.
  Entity transforms and hitboxes remain unchanged.
- Freshly detached recognizable parts can persist for 45 seconds, with one bounce and small
  ground-fitted stains. Late-observed wrecks do not replay debris. Existing limits remain eight
  pieces, two births and two collision rays per tick; repair restores parts.
- Fresh wrecks burn, retain finite ammunition cook-offs, then cool through smoke to a dead shell.
  Joining near old wrecks does not restart fire.

Twenty-four cached mesh groups, four queued loader jobs, a 60,000-vertex source limit,
48-block detail range and the shared overlay allowance bound work. Native fixtures use controlled
live-to-wreck states, not actual combat. Target-PC 60 FPS remains a hardware acceptance check.

## Native examples and validation

[Six native screenshots](M8_WRECK_GALLERY.md) show early burning and later cooling on the
M1A2, AH-64 and F-35A. The stationary fixture also verifies repair restores the original
parts and clears long-lived debris on each vehicle. Local unit tests pass; local previews
use compile-only optional dependency stand-ins and are not distributable builds. Full
repository CI validates the normal integration and NTM GameTests.

World tracking uses a weak reference and synchronized weak actor keys so disconnecting
does not retain the old level. Thermal view keeps the same broken silhouette and skips
paint/soot overlays. No new shaders, terrain edits or gameplay entities are introduced.
