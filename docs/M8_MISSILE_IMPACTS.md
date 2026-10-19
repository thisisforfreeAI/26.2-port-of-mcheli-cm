# M8 rocket and large missile impact follow-up

The AGM-158's stock power-10 general blast previously followed the same smoke-only path as
small rockets. That path placed identical growing puffs at evenly increasing distances along
the impact heading. Optional flash suppression under adaptive load could leave only grey smoke.

Small impacts retain the earlier growing puff artwork and two-way smoke motion. One or two
rolling-fire pockets replace smoke requests inside the existing primary cap, admitted before
the old puffs can spend shared coverage. Even the two-request pressure case keeps one fire
pocket and one old smoke puff. Cannon effects and sub-power-2 impacts keep their existing path.
The central fire pocket uses its full short heat duration, while secondary pockets cool sooner.
Directional puffs now vary their reach within a shorter fan rather than forming a regular line.

General `BLAST` warheads at power 8 or above use the mixed rolling-cloud path. This covers
the AGM-158 without hard-coding a weapon name or altering its content definition. Its power-10
cloud radius becomes 5.5 blocks (previously capped at 3.2), with eighteen ticks of initial fire
and longer-lived smoke. A few old growing puffs mix with shaded low/upper billows to preserve
the earlier look. Penetrator, bomb and incendiary families retain their existing family selection.
The threshold is a cosmetic power heuristic, not a warhead physics simulation.

No new particle textures, pipelines, scene copies, terrain queries or network data are added.
Primary request caps remain 6/12/18 for Low/Balanced/High before adaptive thinning. Larger
quads still pay the existing weighted coverage admission; live particle, flash, terrain and
fragment limits stay in force. Gameplay damage, destruction, flight and projectile trails are
unchanged. Smoke and dirt are ambient-lit after their short heat/backlight windows.

## Reproduce

Prepare the disposable `M6Smoke` preview world described in
[the preview setup](M8_PIXEL_EFFECTS.md#repeating-the-preview), then run:

```sh
./gradlew runClient -Dmcheli.probeMissiles=true -I scripts/visual-probe.init.gradle --no-configuration-cache
```

Add `-Dmcheli.probeBackend=OPENGL` for the compatibility pass. The four scenes cover a power-4
rocket and the actual loaded stock AGM-158 definition, each at Balanced and Low. Screenshots
at impact ages 4/9/16/35 show initial fire, cooling, mixed smoke and the later cloud. The driver
checks existing fragment/read/terrain-flash ceilings. Unit tests verify the power profile and
that fire replaces smoke requests while retaining old smoke at every adaptive pressure.
The [combined combat scene](M8_COMBAT_ACCEPTANCE.md) checks sustained overlapping effects
and transient drainage separately. Native software rendering cannot certify 80 FPS on the
requested RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p.

Both native backends pass all four focused scenes. Balanced rocket/AGM-158 terrain flashes
peak at 32/60 faces, within the 64-face ceiling; Low emits no optional ground flash or fragments.
The combined fixed/adaptive scenes also pass their caps and cooldown checks on both backends.
Raw frame/tick data and hardware acceptance limits are recorded in the linked combat document.
The final integrated build passes 216 JUnit tests, with optional JEI compatibility excluded
locally through the external init script because its Maven host is blocked. The release jar
excludes the opt-in scene drivers. No repository dependency or JEI source files change.

## Close-view admission follow-up

At adaptive pressure 3, Balanced smoke admission allows 24 weighted units per world tick.
A sufficiently close large quad costs 16. Previously the large-cloud path requested two
support billows before its initial fire pocket: the first billow could leave only eight units,
so the fire was rejected. At lower pressure the optional additive flash could also spend
coverage before the first pocket. A stronger weapon profile alone did not solve this ordering.

The emitter now requests its initial rolling-fire pocket before support smoke and the optional
additive flash. Both still share the same admission and live limits. Secondary smoke, dust or
glow may shed sooner in crowded close views; other emitters, distance/particle settings and
global caps can still reject a blast. This introduces no reservation, extra requests or budget
increase. The fire particle cools into smoke using the existing lifetime and texture sequence.

`-Dmcheli.probeImpactPriority=true` selects close-camera rocket/stock-AGM checks with fixed
Balanced detail, then adaptive Balanced detail. It waits nine seconds before each impact so
the real frame controller can warm up, asserts pressure 3 in the adaptive phases, and asserts
that the actual emitter admits a hot pocket. It calls the production emitter directly and adds
the normal terrain mark separately; the earlier missile scenes cover the full effect entry point.
The pressure assertion requires an overloaded renderer; this is a regression scene, not a
hardware FPS acceptance run. Add `-Dmcheli.probeBackend=OPENGL` to repeat on OpenGL.
Screenshots cover ages 4/9/16/35; existing ground-read, fragment and flash ceilings still apply.

Vulkan/lavapipe and OpenGL/llvmpipe both pass the four close-view scenes at 1920×1080.
At pressure 3, the rocket requests two primary particles and the AGM-158 three; both admit
their hot pocket. The close AGM view spends 16 of 24 available weighted units on that pocket,
leaving too little for its other large quads: it renders one fire-to-smoke particle instead of
losing fire to its support cloud. Fixed Balanced detail admits six AGM particles in this view.
Optional ground flashes/fragments are absent under pressure 3, as before. In this mode the
reused `rocket-low`/`agm158-low` scene labels refer to adaptive Balanced runs, not Low quality.
These are functional software-renderer results, not target-hardware frame-rate measurements.
The follow-up build also passes all 216 JUnit tests with the same local JEI exclusion, and its
release jar excludes the probe drivers.
