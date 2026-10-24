# Remaining M8 realism implementation

This pass completes the outstanding software work for content-aware vehicle depth, moving
tracks on terrain transitions, and comparisons using actual weapon definitions. Hardware
acceptance remains 60 FPS / 16.67 ms on RTX 3060 8 GB, Ryzen 7 7800X3D, 1080p.

## Content-aware vehicle detail

Static hull colors now receive soft, localized shading around authored engine outlets/nozzles,
lift-rotor hubs and wheel rows. The existing machinery coordinates define each vehicle's zones;
there are no guessed engines on unsupported content. At most four outlet/nozzle definitions,
16 rotor definitions (up to four lift rotors) and 16 wheel rows are inspected at model load.
There are at most 12 zones. Zones taper smoothly to neutral and use the strongest overlap,
preventing multiple anchors from accumulating into a black patch.

Zone darkening is at most 6%; combined with crease shading the final factor is clamped to
0.84. Exposed geometry keeps its existing restrained highlights outside the localized masks.
Only the static body receives these zones; animated groups and separate moving-part models
keep geometry shading without fixed machinery shadows painted onto their motion.

Colors are baked into the existing four-byte-per-vertex cache during model loading. No extra
per-frame zone calculations, textures, shaders, copies or draw calls are introduced. Original
UV artwork remains usable with resource packs. Both Classic switches, thermal, destroyed and
iron-curtain tints and emissive draws retain their bypass. `effects.vehicleSurfaceShade`
controls the result. Very large groups retain the existing ordinary-lighting fallback.

## Treads across support changes

When grounded wheel probes bracket local z=0, each tread side stamps from their interpolated
center instead of jumping between individual wheel rows. The origin stays stable on a planar
ramp when a different support pair takes over. Actual wheel collision results still determine
support. A single grounded wheel uses the original contact and resets when that anchor changes.
Invalid/oversized interpolation spans also use the original nearest-contact fallback.

Distance sampling, two recent samples per contact, turn/reverse behavior, airborne/teleport
reset, material coloring, support-shape clipping and global source/read/fit/quad budgets stay
in place. No vehicle physics or terrain behavior changes. The native fixture now drives a
tracked M1A2 and wheeled Growler over slab/full-block ramps as well as turns and reversals.

## Actual weapon profiles

Ground ignition (`Flaming`) no longer classifies every known conventional weapon as incendiary.
The cosmetic family is chosen after gameplay damage and block effects have already completed.
No ignition, damage, penetration, blast radius or destruction settings change.

| Actual stock weapon | Cosmetic family | Power | Fire ticks |
|---|---|---:|---:|
| M230 | Cannon | 1 | 4 |
| Hydra 70 | HE smoke-led rocket | 3 | 7 |
| AGM-114 | Penetrator | 4 | 8 |
| AGM-158 | Large HE missile | 10 | 18 |
| MK82 | Broad bomb | 4 | 12 |
| GBU-57 | Penetrator | 15 | 13 |
| S-8DM | Thermobaric/fire | 5 | 45 |

FAE, explicit fire containers and NTM fire payloads retain the fire profile. Stock S-8DM and
CBU-72 use it by their authored identifiers, since their old definitions lack an FAE flag.
Unknown custom weapon types retain the prior ignition fallback; unidentified flaming blasts
also retain it. The existing style ordinals and network format are unchanged.

Large shallow HE missile clouds now stretch slightly along the supplied impact heading and
narrow across it, within 0.65 of the existing cloud radius for the original radial envelope.
Steep/unknown directions and broad bomb shapes retain their existing spread. The initial
shape settles into the shared late smoke drift. No extra lobes or larger per-lobe radii are
requested. Small rockets retain their earlier puff character.

## Validation and limits

Tests cover smooth non-stacking detail masks, bounded metadata work, static versus moving
parts, ramp support-pair stability, invalid contact rejection, bounded directional footprints,
and separation of ground ignition from fire-warhead profiles. The native completion fixture
uses actual loaded stock definitions, checks machinery-zone hits and captures geometry-only
versus detail shading plus weapon effects at ages 6, 20 and 50. The track fixture checks
surfaces, removal, air/water rejection, real contacts, turns/reverse, load and ramps.

Adaptive native checks verify geometry against the observed pressure budget; they do not
require every machine to reach pressure 3. A prior harness assertion incorrectly required
maximum pressure even when the controller reported a different valid state. Controller
pressure transitions remain covered separately by unit tests.

These software-renderer scenes do not certify the user's GPU frame rate or replace broader
player-driven stock/addon artistic acceptance. All opt-in drivers alter a disposable preview
world and remain excluded from release jars. Local builds exclude optional JEI only because
its API is unavailable in the cache; full repository CI retains it.

The final 1080p completion fixture passed on Mesa Vulkan and OpenGL: four stock vehicle
comparisons plus seven actual weapon definitions. Static hull masks affected 49 M1A2,
63 Apache, 254 Chinook and 247 F-35 vertices in the checked models. See
[comparison results](performance/m8-realism/completion-comparisons.json).

Both backends also passed all eleven track scenes, with 1,980 tick samples each, including
the new real-contact ramp scene. Tread and tire requests were both observed there. Both
adaptive runs reported active feedback, target 60 and pressure 3; per-frame geometry stayed
within the limit for the observed pressure. See
[track results](performance/m8-realism/completion-tracks.json); compressed raw samples are
`vulkan-completion-tracks.csv.gz` and `opengl-completion-tracks.csv.gz` in that directory.

Release build: 264 unit tests, zero failures/errors/skips. Release packaging was checked for
both probe classes and their inner classes. Implementation and software validation are complete;
60 FPS certification still requires the specified physical PC. Existing saved configs retain
their chosen value; set `effects.targetFps=60` when adopting this performance target.
