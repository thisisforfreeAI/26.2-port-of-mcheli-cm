# M8 destruction sequences

The eight requested additions use bounded client presentation: rare supported tank
ammunition turret separation, track failure, engine cover blowouts, wing/tail breakup,
ground-contact rotor strikes, secondary interior bursts, delayed collapse and smoldering
interiors. Preserve existing gameplay damage, hitboxes, handling, original craters and NTM
ownership. Unknown model geometry stays intact. Use real authored parts or bounded cached
mesh sections, shared particle/overlay admission and finite debris.

After these stages, investigate vehicle-kill blood effects. Attribute only MC Heli conventional
vehicle/weapon kills and avoid NTM gun and NTM payload gore duplication. Do not change NTM guns.

## Implementation limits

`effects.vehicleDestructionSequences` defaults on in Enhanced quality with vehicle damage
details. Off retains the previous M8 wreck presentation. All events are cosmetic.

1. Turret loss requires at least eight conventional explosive rounds, a supported carousel-ammunition model
   (`t-90`, `t-72`, `t-80`, `t-64`, `t84`) and one of eight stable UUID seeds. Abrams-style
   blowout-panel designs keep their turret. The opening gets localized fire; no extra blast
   damage, ammunition consumption or craters are produced.
2. One named crawler side loses a contiguous run of repeated links. The remaining end droops,
   leaving road wheels exposed. Geometry of individual links stays intact.
3. Small upward-facing engine-area surface sections are clipped off on the model loader;
   supported named covers also detach. A short flame jet and dark smoke follow the opening.
4. Bounded aircraft wing and rear-fuselage sections detach, preserving original UVs. Exposed
   spars remain at the cuts. Oversized/unsupported sections retain their geometry.
5. Authored helicopter rotors visually decelerate, with no lift/control changes. Swept tip
   contacts can produce sparks, small fragments and a chipped tip: at most two world collision
   rays per tick and two strike events per wreck. Airborne rotors do not spark without contact.
6. Finite interior bursts add small flashes, fragments and sharp pops for armed fresh wrecks.
   They share particle admission; no world light updates or damaging secondary explosions.
7. Body/gear settling begins after a delay and reaches its final pose gradually. Gameplay
   entity transforms and collision remain unchanged.
8. Small ember pockets breathe inside openings, with occasional thin smoke, then fade out.
   Late-observed old wrecks do not replay fresh particle bursts.

Mesh work runs on a bounded loader (32 cached entries, four pending jobs, 60,000 source vertices).
Detached sections are limited to 9,000 vertices and turrets to 12,000; shared debris is limited
to eight pieces and 24,000 vertices total. The shared overlay/atmosphere budgets also apply.
Repair restores original parts and removes long-lived wreck debris. Target-PC 60 FPS remains
unmeasured; software-rendered previews validate behavior and limits.
