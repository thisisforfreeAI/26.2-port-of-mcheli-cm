# Fire lighting

[Native examples](M8_FIRE_LIGHTING_GALLERY.md).

With Enhanced effects and `effects.realisticLighting` enabled (the defaults), burning vehicles
cast warm, gently varying light onto nearby vehicle surfaces, living entities and ground top faces.

- A wreck's engine fire and its ground fuel pool have separate, stable light sources. A pool keeps
  lighting its surroundings throughout its existing 760-tick burn, after the shorter hull fire ends.
- Damaged aircraft engines light their actual outlet positions while they burn. Repair stops the
  source, followed by an eight-tick light fade.
- Small fires on detached parts share two-block light cells, instead of creating a light for every
  sprite. Nearby main fire sources take precedence; cells fade over the flame's 24-tick lifetime.
- Fire intensity approaches zero as the source cools. Smoke alone never refreshes a fire light.
- Fire lighting updates before particle admission, so a busy scene cannot turn a burning wreck's
  light off just because its next flame particle was skipped.
- Terrain glow is stronger at night/under cover, covers a wider patch and samples farther beneath
  elevated engines. The four nearest visible cached patches draw first. Moving sources and radius
  changes invalidate their cached patch.
- GPU vehicle buffers retain directional amber point lighting. Disabling GPU buffers uses a local
  lightmap lift on the compatibility renderer.

The existing Realistic Lighting switch controls this work. Classic effect quality still disables
the enhanced lighting system. No world light blocks, light-engine updates, chunk rebuilds, packets,
damage changes or new NTM integration are introduced. Flame counts and burn durations are unchanged.

## Bounds and limitations

The existing 96-source cap and eight point lights per vehicle shader remain. At most four terrain
patches draw; at most one source is resampled each tick, within 96 blocks. Each patch searches a
seven-block horizontal radius and at most fourteen blocks down. Small flame-cell lights can be
evicted for stronger sources. Terrain glow covers simple solid top faces only: it is an additive
visual approximation, not a world-wide dynamic shadow or block-light system. Vertical walls and
water surfaces do not receive a terrain patch.

## Native checks

```sh
./gradlew runClient -I scripts/visual-probe.init.gradle --no-configuration-cache \
  -Dmcheli.probeFireLighting=true -Dmcheli.probeBackend=VULKAN
```

Repeat with `OPENGL`. The opt-in scene uses the private `M6Smoke` dev world in `.run/m8-preview`.
It holds real AH-64/F-35 models in controlled client HP/wreck states, using production fire emitters;
these are geometry/lighting fixtures, not actual server crash captures. It compares lighting off/on,
checks a fuel pool after the hull fire ends, checks repair/cooling drains, exercises GPU buffers off,
and verifies that 24 wrecks retain their main light sources despite shared particle admission.
The load scene first fills all 96 source slots with weak flame cells, then checks main-fire eviction
and keyed-map cleanup. Classic quality is also checked with an explicit fire-light request.

Software Mesa rendering verifies appearance and backend compatibility. The 60 FPS target on an
RTX 3060 8 GB/Ryzen 7 7800X3D at 1080p still needs measurement on that machine.

Validation: 560 unit tests and all 297 required local GameTests passed. All six native scenes and
ten checkpoints passed on both Vulkan and OpenGL, including the 96-source pressure test, all 48
main lights for 24 wrecks, fuel/hull lifetime separation, repair/cooling drains and Classic exclusion.
The gallery contains eight original Vulkan PNGs; their hashes and per-backend results are in
[the validation record](performance/m8-realism/fire-lighting.json). Optional JEI/NTM dependencies
were excluded only for local development checks; the normal and real-NTM CI jobs cover those builds.
