# M8 landing feedback

`effects.landingFeedback` (default on, Enhanced / Polished) adds small touchdown puffs and
restrained compression to supported deployed primary landing gear. The fuselage settles by
a few centimeters while the gear shortens, keeping its feet near their sampled contacts.
Different contact heights produce different gear loads; compression damps back after impact.
The camera, aim, collisions, suspension physics and original weapon craters are unchanged.

This is affine compression of the existing gear/tire assembly, not a new wheel skeleton or
soft-body tire simulation. It preserves group identity and the existing part transforms, so
damage marks follow the gear and existing folding animations remain authoritative.

Footprints are built on the existing model loader. Only low geometry belonging to authored,
non-hatch, non-reverse primary landing-gear components qualifies. Nearby low vertices form
wheel contacts; combined assemblies can have several contacts. Doors and unsupported meshes
retain their original appearance. No stock aircraft-name guesses, new artwork, content-pack
revision or per-frame mesh rebuilding is needed. F-35A, F-15E and A-10 provide native examples;
other models are supported only when their component layout meets these conservative checks.

The existing audio entity scan observes nearby intact aircraft. Admission retains at most
32 aircraft states within 64 blocks, eight supported gear groups per aircraft and four wheel
contacts per group. At most 24 short terrain rays run per client tick, with four-tick sampling
and stale samples dropped. Footprints cache at most 256 group entries with weak model ownership.
There are no new packets or additional entity scans.

Ground contact triggers one short event per gear assembly. Hard pavement gets pale tire smoke;
loose ground uses the existing surface palette. Each contact produces one puff on Low detail
or two normally, reduced under effect pressure. The puffs reuse the existing sixteen-frame
wisps, ambient smoke lighting, shared wind and global smoke allowance. Total births, including
sparks, stay below 12 per client tick. Tiny sparks require a severe impact on hard material;
ordinary landings, sand and grass do not produce them.

First observations of parked aircraft, distant reacquisition, source warps, water, folded gear,
missing airframe sections and wrecks do not invent touchdown events. Unsupported aircraft do
not occupy contact-state slots. Disabling the setting or changing worlds clears owned states.
Wreck settling, fuel fire and broken-section artwork remain in their existing systems.

Validation includes pure landing timing/continuity tests and actual MQO footprint tests,
including F-35 door exclusion and the A-10's combined rear assembly. Native fixtures use
controlled aircraft trajectories over real client terrain; they validate effects and rendering,
not piloted landing physics. The machine-readable record is
[landing-feedback.json](performance/m8-realism/landing-feedback.json).

[Native examples](M8_LANDING_GALLERY.md). Local software Mesa does not establish target-PC
60 FPS / 1080p on the RTX 3060 / Ryzen 7800X3D.
