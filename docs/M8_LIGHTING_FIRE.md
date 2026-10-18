# M8 smoke lighting, damage fire and explosion sparks

This pass builds on the 80 FPS adaptive budget in PR #41. The target remains RTX 3060 8 GB /
Ryzen 7 7800X3D at 1920×1080 (12.5 ms per frame); hardware acceptance is still open.

## Appearance and cost

Enhanced smoke samples the sky and block light at the puff's actual position. Minecraft's
existing light map handles time of day and dimension lighting; indoor smoke no longer carries
full outdoor sky light. Hot puffs retain self-lit fire colour while cooling, preserving any
brighter sampled torch light. They do not emit dynamic world light.

Each smoke particle reuses one mutable block position and caches ambient lighting. Stationary
puffs refresh every four ticks; moving between blocks refreshes on the next tick, at most once
per tick. Frame extraction performs no new block-position allocation. Unloaded chunks return
zero ambient light and are not loaded by this lookup. Classic smoke retains its old full-sky
lighting. Water drops keep their existing local-light behavior.

Helicopter, winged-aircraft (planes/ships) and tank damage emitters annotate their existing smoke
with a local appearance hint. Below 30% health it becomes progressively hotter, reaching full
heat at 10%. The existing fade/heat curves turn each fresh puff from fire back into its original
damage-smoke colour. Emission counts, random draws, positions, motion and lifetime remain
unchanged. The client ignores the hint in Classic presentation and for splashes or puffs born
in water. This adds no particles to those damage emitters. Placed guns/vehicles without an
existing damage-smoke emitter do not gain a new emitter in this pass.

Enhanced dry explosions of size 2 or greater can add tiny, short-lived sparks. Balanced caps
them at eight, High at twelve. Pressure level 1 halves that allowance; Low and pressure 2–3
suppress them. Each spark must also pass the smoke family's shared admission and live limit,
leaving the weapon-flash reserve independent. Sparks reuse the first cell of the pixel flash
sheet and the existing additive particle layer. They live 7–11 ticks and use no collision queries.
Their independent cosmetic RNG preserves the original explosion emitter's world-RNG sequence.
Existing block debris is retained. Ground rings and heat haze remain deferred until hardware
performance and the current effects have been reviewed.

## Repeating the native scene

Follow the disposable flat-world setup in [M8_PIXEL_EFFECTS.md](M8_PIXEL_EFFECTS.md). This probe
clears entities and changes blocks, time and weather inside that isolated copy.

```sh
./gradlew runClient -Dmcheli.probeLook=true \
  -I scripts/visual-probe.init.gradle --no-configuration-cache
```

Add `-Dmcheli.probeBackend=OPENGL` for the other backend. This scene requires and verifies a
1920×1080 framebuffer. The current camera is at z=4 outdoors and z=11 inside the sealed room.
It displays a cold plume at x=-3 and a critical-damage plume at x=3.
It captures Classic/daylight, Enhanced/daylight, Enhanced/night, a sealed unlit room, a room
with a torch, Classic in the dark room, damage smoke born in water, and explosion/isolated
sparks. It checks actual cached smoke light against the world's light values, requires zero
light in the sealed dark room, and checks that the torch room has block light without sky light.
The extra sources are included only through the opt-in init script and are excluded from normal
builds. These are native renderer checks, not screenshots from an image generator.

## Validation

The Vulkan scene passes all cached-light assertions on Mesa lavapipe at 1920×1080. Recorded
sky/block values are 15/0 outdoors, 0/0 in the dark room, 0/13 in the torch room, 15/0 for Classic
inside the room, and 13/0 at the submerged sample. It captures the two damage plumes, explosion
sparks and isolated sparks, then saves/stops the integrated server cleanly. The Vulkan captures
were taken with the earlier camera at z=-9.5; the current probe moves the camera closer and
inside the room to make the indoor visual comparison easier.

Captures: [Vulkan daylight](screens/m8-lighting-vulkan-day.png),
[Vulkan night](screens/m8-lighting-vulkan-night.png),
[Vulkan explosion](screens/m8-lighting-vulkan-explosion.png).

The OpenGL scene also passes every light assertion at 1920×1080 on Mesa llvmpipe and saves/stops
cleanly. Its closer camera captures the indoor comparison directly:
[dark room](screens/m8-lighting-opengl-room-dark.png),
[torch room](screens/m8-lighting-opengl-room-torch.png),
[Classic room](screens/m8-lighting-opengl-room-classic.png),
[daylight](screens/m8-lighting-opengl-day.png).

The initial PR #44 combined `build runGameTestServer` passed: **180 JUnit tests and 209 required GameTests**,
with zero failures. The normal jar contains the new effects and excludes development probes.
Local builds use the documented external JEI exclusion because the optional API Maven host is
blocked here. Full JEI-enabled
release validation and the target PC's combat frame times remain open.

## Bomb-size follow-up

Enhanced heavy-bomb and incendiary puffs scale smoothly from power 3 to power 12. At power 12,
Balanced increases nominal puff size by 50% (up to 24 instead of 16) and the initial scale from
0.2 to 0.6 times nominal size. That is a 4.5-times wider initial quad and a 50% wider eventual
smoke quad for the same large-bomb puff. Low caps the size multiplier at 1.15 and High at 1.75.
Initial scale caps are 0.4 / 0.6 / 0.7 on Low / Balanced / High; the existing diffusion ceiling
remains twice nominal size. The warm initial glow can reach radius 12 / 16 / 18 respectively.

The emitter adds no puffs or glow layers and retains its timing, lifetime, motion and random
draws. Admission uses the enlarged expected puff/glow radius, so larger effects spend more of
the existing coverage budget. Ordinary cannon/rocket/penetrator impacts, tiny bombs, Classic
and vanilla-explosion selection retain their existing puff scale. The subsequent
[impact/flare follow-up](M8_IMPACT_FLARE.md) adds the requested bomb/missile accents and stronger countermeasure flare glow within bounded allowances.

The size-12 comparison passes on both Vulkan/lavapipe and OpenGL/llvmpipe at 1920×1080,
with screenshots and the repeatable opt-in scene in [M8_GROUND_MARKS.md](M8_GROUND_MARKS.md#bomb-size-follow-up).
The follow-up build passes 183 JUnit tests using the external optional-JEI exclusion.
