# M8 ground-contact follow-up

## Footprint-aware nearby shadows

Enhanced vehicle shadows reuse Minecraft's entity-shadow renderer and its support-shape fitting,
height fade, distance fade, entity-shadow setting and invisibility gate. No new decal emitter,
render pass or shader is introduced. `effects.vehicleContactShadows` is an independent switch.
Classic and disabled retain the original two-block shadow radius.

The render state chooses its radius before vanilla extracts shadow pieces. It uses collision
width and up to 16 authored wheel positions (80% of their horizontal reach). The original two-block
radius is the floor, avoiding tiny shadows on helicopters whose collision body is much narrower
than the visual model. Balanced caps radius at 2.5 blocks; High at 3. Low or adaptive pressure
restores 2. Aircraft with narrow bodies and no wheel definitions retain the existing radius.
This is a circular footprint approximation, not a detailed silhouette or a shadow map.

Larger radii increase vanilla's local support sampling: at most 6 by 6 horizontal columns on
Balanced, 7 by 7 on High, compared with 5 by 5 at radius 2. Vanilla retains its close-camera and
height limits. No separate world sampler or per-tick entity scan is added. GPU/CPU performance
on the target PC still needs measurement against 60 FPS / 16.67 ms.

## Rotor wash at surface transitions

The emitter previously used the material at the rotor center to decide whether to emit each
candidate. It now applies density after sampling the candidate's own surface and recomputing
its local height/strength. Sparse pavement wash and denser sand/snow/spray therefore follow the
puff's supporting terrain rather than the terrain directly beneath the helicopter.

The original three cache slots per rotor (center plus up to two candidates) are retained.
An initial diagnosis of slot aliasing was incorrect: candidate count never exceeds two.
Existing source, cache (128 entries), block-read (256/tick), range, period and particle-admission
caps remain.
Candidates now reach the local sample before the material probability test, so sparse surfaces
can require more reads than before; the shared read cap is unchanged. Repeated game times
still cannot duplicate a burst. Existing motion, surface tint, shallow
wash lift and water ripple behavior remain in use.

## Limits and remaining work

Tracks already use actual contact positions, reject air/teleport gaps and fit surviving support
shapes; this slice does not change their sampling. Scorch marks already fit ground surfaces.
Vehicle-specific painted texture art, noncircular contact-shadow art, mixed-surface gameplay
review and target-PC acceptance remain open. These improvements do not complete every realism
request in the plan.

## Validation

The existing 11-scene rotor-wash fixture passed at 1080p on Mesa Vulkan and OpenGL:
Classic sand, sand, grass, snow, water/drain, moving flight, high altitude, empty, Classic load,
Enhanced load and adaptive load. It checks duplicate-tick suppression, terrain/cache caps,
water ripple removal and altitude/load shedding. Both backends peaked at 96 direct block
reads/tick in the eight-helicopter load scenes and reached adaptive pressure 3. The
[summary](performance/m8-realism/ground-contact-summary.json) records peak work counts and
final particle counts; it is not a target-GPU timing certificate. That wash fixture uses unrendered helicopters. A separate opt-in `mcheli.probeRealism`
fixture now renders a held stock M1A2 and checks the complete vanilla render-state path:
Balanced radius 2.5, High 3, disabled/Classic 2, nonempty fitted shadow pieces and model draws,
plus the shading toggle and populated baked-color groups. It also captures night, roof-shaded
and sand/grass boundary blasts and asserts delayed amber illumination. Broader stock/addon,
sloped-ground and moving-vehicle artistic acceptance remains open. The fixture changes the
disposable M6Smoke world and config; do not run it on a valued save.

## Material refinement

Hard-surface impacts now replace dirt-jet slots with block-textured chips. For a six-request
Balanced broad blast, this changes two fragment requests to four, with the same total six
requests. The shared per-tick fragment cap remains four (High: six); admission and the live
fragment cap can still suppress requests under load. Sand/soil retain their narrow dust jets.
This does not add particles beyond the ground response's existing request count.

The focused fixture includes isolated boundary, rock and sand bursts, checks four versus two
admitted fragments in the quiet Balanced scenes, and captures age-six dust/chips before the
front fades. These are cosmetic responses on surviving terrain; no damage or block behavior
changes. Water continues through the separate splash path.

The expanded 10-scene realism fixture passed on both backends at 1080p, checking admitted
fragment counts 2/4/2 on boundary-soil/rock/sand, 10 ring sectors and 12 direct reads in each
quiet isolated scene. See [material summary](performance/m8-realism/material-summary.json).

Shared wind now also affects Enhanced engine wisps and damage smoke after their initial
impulse. Source counts, admission, lifetimes, rotor flow, projectile trails and Classic motion
remain unchanged. The existing combined atmosphere fixture passed on both backends at its
native 1280x720 size, exercising four AH-64s, an M1A2, startup, material wash, damage, night
firing and Classic cleanup. Five rendered vehicle states restored original shading/shadow
settings on the style switch. Audio checks used OpenAL's null output device. The
[atmosphere summary](performance/m8-realism/atmosphere-summary.json) retains frame-sample
accumulators as diagnostics; they are not distinct particle counts or performance rates.
