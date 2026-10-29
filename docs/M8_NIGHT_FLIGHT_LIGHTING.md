# M8 night-flight lighting

`effects.nightFlightLighting` (default on, Enhanced / Polished) enables authored navigation
lamps and short white strobes, a restrained glow on existing cockpit surfaces, and small
terrain light pools beneath active search/landing lights. F-35A, F-15E and A-10 ship with
explicitly positioned lamps and optional landing lights. Other vehicles keep their existing
lighting; addon authors can supply the same lamp metadata.

Red/green navigation lamps stay steady. Strobes flash twice briefly in an 80-tick cycle.
Small depth-tested points get a faint halo at night, with bounded apparent size at distance.
Every lamp follows its authored model part's extracted pose. They do not make the paint or
ordinary bullets glow. Missing airframe sections, wrecks, thermal/far vehicle passes and
unpowered aircraft do not receive the new navigation/cockpit overlays.

Cockpit illumination is local to a physical first-person pilot in a closed authored canopy.
Small instrument-area sections are clipped from the original model on its existing loader;
their original UVs and paint are retained. A faint emissive blend raises their visibility at
night and follows the existing damage backlight flicker. It adds no camera movement and no
new panel artwork. Remote, gunner and UAV views do not receive this overlay.

The existing landing/searchlight switch controls the three new landing lamps. These require
power and deployed gear. Their clear-air cone is deliberately faint; rain increases visible
scattering. That visibility is separate from terrain/entity illumination. Existing legacy
searchlight colors and cone opacity remain unchanged, including all 15 stock legacy lamps.

Ground projection uses already extracted beams. Four nearby sources share one round-robin
update per client tick: at most 19 short terrain/visibility rays and nine cached top faces
per source. Water, blocked beams and unsuitable collision shapes receive no patch. Sampling
does not load chunks. Sources clear when removed, switched off, folded, unpowered, stale or
out of range. A moving source hides an obsolete patch until resampled. Live lamp damage
strength controls the cached surface tint, so electrical flicker does not leave stale light.

This is bounded surface illumination using the existing glow material, plus existing entity
highlights. It does not change the world's block-light values or implement full scene lighting.
Daylight skips ground projection entirely. At Low / Balanced / High, a frame admits at most
32 / 64 / 96 logical lamps, reduced under effect pressure. All lamps on a vehicle share one
geometry submission. A pilot's instrument overlay is capped at 4,096 vertices; cached clipped
groups use weak model ownership. No additional entity scans or new network payloads are used.

## Content

`Render.FlightLamps` accepts at most 16 entries with `Type` (`NAVIGATION`, `STROBE`, `COCKPIT`),
`Position`, `PartName` (default `body`), 24-bit hexadecimal `Color` and `Radius`.
Coordinates and radii are finite and bounded. TXT supports
`AddFlightLamp = type,x,y,z,RGB,radius[,part]`; YAML emission preserves it.
`Components.SearchLight.M8Only: true` marks an optional light whose rendering follows these
new client/power/gear gates. Legacy lights default to false. TXT's optional eleventh searchlight
argument carries the flag. The managed stock pack refreshes from content revision 6 to 7;
separate addon packs keep their own definitions and precedence.

Validation: 509 local unit tests pass, including round trips, real clipped cockpit meshes,
legacy-light parity, short strobe timing, bounded detail and the regression for small light
pools vanishing between block corners. Three native Vulkan aircraft scenes and 13 OpenGL
cases pass, including three pilot views, switches, daylight, Classic/off, folded gear, water,
blocked beams, no power, parked projection and 32-aircraft load. Load reaches the 64-lamp /
four-source / 19-ray bounds. No target-PC frame-rate claim is made from software Mesa.

[Native examples](M8_NIGHT_FLIGHT_GALLERY.md) · [machine-readable checks](performance/m8-realism/night-flight-lighting.json).
