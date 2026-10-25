# M8 plan: effects polish for Vulkan

Current requested realism implementation: complete; native software validation is recorded in
[M8_COMPLETION.md](M8_COMPLETION.md). Target-PC 60 FPS acceptance remains open.

Status: started early, by the user's request, for the vehicles already ported in M6 (helicopters,
planes, ships, tanks and vehicles). This expands the M8 row of [PORTING_PLAN.md](../PORTING_PLAN.md)
§6. PORTING_PLAN.md places M8 after M6, M6b and M7; slices that need M7's sound/particle parity
or render path B wait for it. Preserve decisions D1 to D23.

## 1. Rules

- **Gameplay is untouched.** Effects stay client-only. No change to damage, projectile flight,
  targeting or timings. Classic emitters retain upstream timing and motion; enhanced wash and
  explosion presentation use bounded cosmetic emitters. Client quality budgets may suppress
  cosmetic particles during heavy combat. The weapon-specific ground follow-up adds
  a cosmetic family enum and compact impact direction to the explosion effect payload, bumping the network version; it
  does not change damage or terrain destruction.
- **Both backends.** Minecraft pipeline state (`RenderPipeline` snippets, blend functions,
  depth states) and vanilla shaders for the main effects. The requested exhaust heat follow-up
  is a narrow exception: one small GLSL 330 refraction shader and a shared conditional scene
  copy, disabled on Low or adaptive pressure. No raw OpenGL. Every slice is checked on both
  backends; see [M8_FOLLOWUPS.md](M8_FOLLOWUPS.md).
- **Upstream's look stays one setting away.** `effects.effectQuality` in the client config:
  `CLASSIC` draws exactly as upstream (and as 0.5.1); `ENHANCED` (the default) draws the M8 look.
  Later slices may add levels if a battle needs cheaper effects.
- **Recognisably McHeli.** The stock textures remain available in Classic mode. The pixel-art
  follow-up adds compact shaded smoke, flash and splash sprites; see §6.

## 2. Map of the changes

| Effect | Today (upstream look) | M8 look | Slice |
|---|---|---|---|
| Smoke (explosions, trails, damage, downwash, flares, grenades) | 8-frame puff that pops in and out at full opacity; size jumps once per tick | Fades in over 2 ticks and out over the last 30 % of its life; size grows smoothly between ticks | 1 (done) |
| Explosions / fire | Grey-brown smoke puffs, block dust, flares from large blasts | Earlier growing puffs for small hits; denser connected bomb billows with fast fire pockets and ground dust; [rolling fire, weapon shapes and ground flash](M8_BLAST_MOTION.md) | Follow-up (done) |
| Muzzle flashes | 8-frame starburst, alpha blended | Same starburst drawn additively, plus a short warm glow | 1 (done) |
| Tracers (machine-gun rounds) | Bullet model lit by the world, dark at night | Ambient-lit projectile bodies; sparse, thin warm streaks at most two blocks long, without halos; [visual correction](M8_BLAST_MOTION.md) | Follow-up (done) |
| Missile trails | Flame quad, smoke trail | Smoke as above, plus an additive glow at the flame | 1 (done) |
| Flares | Yellow cube lit by the world, dark at night | Full-bright cube with a flickering two-layer glow; chaff unchanged | 1 (done) |
| Water splashes | Puffs that pop out | Fade as smoke does | 1 (done) |
| Rotor wash / downwash | Grey puffs whatever the ground | Puffs tinted by the ground's map colour (sand, dirt, snow); white spray over water | 1 (done) |
| Burning vehicles | Dark damage smoke | Hot damage puffs below 30% health, cooling into smoke; existing helicopter, plane/ship and tank emitters | 2 (done) |
| Explosion shockwave and debris | Block dust | Bounded sparks, material dust fans and tumbling surface chips/clods; [ground response](M8_BLAST_DEBRIS.md) and [optional ground dust front](M8_FOLLOWUPS.md) | Follow-up (done) |
| Smoke lighting | Always full sky light (upstream sampled 3000 blocks up) | Cached local sky/block light; time of day handled by the existing light map | 2 (done) |
| Engine exhaust | Addon exhaust flames and plane nozzle smoke | Short turbine wisps, restrained jet accents and bounded optional local heat shimmer | 3 + requested follow-up |
| Quality levels and budgets | Vanilla particle setting only | Low/Balanced/High particle budgets and distant thinning | Pixel follow-up (§6) |

## 3. Slice 1 as built

- `config/EffectQuality` and `effects.effectQuality` (default `ENHANCED`).
- `client/particle/EffectLook`: the fade, heat and glow curves, unit tested in `EffectLookTest`.
- `client/particle/ParticleLayers`: an additive particle layer (vanilla particle shaders,
  `BlendFunction.LIGHTNING`, depth tested without depth writes), registered as a pipeline.
- `client/particle/GlowParticle` and `textures/particle/glow.png` (a generated radial gradient):
  the fireball and muzzle-flash glows.
- `client/render/GlowGeometry`: additive position-colour discs and streaks for flares, tracers and
  missile flames, the same pipeline family as the search light's cones.
- `ParticleParam.groundState`: the downwash's ground block, set by
  `AircraftEntity#onUpdateParticleSandCloud`; only the client's look reads it.

## 4. Validation

Each slice: `./gradlew build` (JUnit and GameTests), `runServer`, and a scripted scene under Xvfb
with Mesa on OpenGL (llvmpipe) and Vulkan (lavapipe), captured with `CLASSIC` and `ENHANCED`, by
day and by night. The scene (a temporary driver, not committed, as in M4 and M5) stands in a flat
world and plays a size-5 McHeli explosion, an M230 burst with a Hydra rocket across the view,
muzzle flashes and four flares. It slows the server to 5 ticks a second (`/tick rate 5`) so the
software renderer draws every tick, and grabs each picture on the next rendered frame.

Frame-time checks under combat load wait for real hardware: software rendering under Xvfb is not
representative.

## 5. As built

See §3. Slice 1 passes `build` (all JUnit tests, including the four `EffectLookTest` cases) and
the GameTests. The scene ran on OpenGL (llvmpipe) and Vulkan (lavapipe), each with `CLASSIC`
(top rows) and `ENHANCED` (bottom rows), by day and by night; columns are the explosion just
after the blast and a little later, the M230 burst, the muzzle flashes and the flares:
[OpenGL day](screens/m8-slice1-opengl-day.png), [OpenGL night](screens/m8-slice1-opengl-night.png),
[Vulkan day](screens/m8-slice1-vulkan-day.png), [Vulkan night](screens/m8-slice1-vulkan-night.png).
Both backends draw the same pictures. `CLASSIC` matches 0.5.1. With `ENHANCED`, the blast is
orange fire that cools to smoke; the tracers, which `CLASSIC` draws dark at night and too small to
see in these frames, show as bright streaks; the flares glow. The additive glows show most at
night; by day they barely lighten the sky behind them, as intended.

Not captured: downwash over sand, snow and water, missile flames (the scene's AIM-9X needs a
lock), and splashes. They use the same layers and pipelines as the captured effects.

## 6. Pixel artwork and scalable budgets

The follow-up in [M8_PIXEL_EFFECTS.md](M8_PIXEL_EFFECTS.md) adds shaded pixel artwork and
Low/Balanced/High particle budgets on top of slice 1. It preserves the enhanced heat, additive
flash/glow pipelines, tracer/missile effects and ground tint. Either `effectQuality = "CLASSIC"`
or `style = "CLASSIC"` selects upstream presentation; budgets still apply. Polished flares use
one small pixel halo instead of two glow discs, and Low disables optional particle glows/halos.
The follow-up document records its earlier isolated captures separately from integration checks.

## 7. 60 FPS performance follow-up

The target is RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p (16.67 ms per frame).
[M8_PERFORMANCE.md](M8_PERFORMANCE.md) documents adaptive cosmetic detail, coverage-weighted
admission, dust/spray budgets and repeatable frame-interval capture. Hardware acceptance remains
open; software-renderer checks cannot certify this PC's FPS.

## 8. Lighting and damage-fire follow-up

[M8_LIGHTING_FIRE.md](M8_LIGHTING_FIRE.md) records cached local smoke lighting, heat hints on
existing damage puffs and tiny explosion sparks with quality/load caps. Classic remains
available; the 60 FPS hardware target and real battle/flight checks remain open.

## 9. Weapon-specific ground appearance

At the user's request, [M8_GROUND_MARKS.md](M8_GROUND_MARKS.md) adds material-aware soot and
disturbed-ground textures fitted to surviving block surfaces. Weapon definitions select the
cosmetic family and explosion power scales its footprint. The cache, projection queue, draw
range and lifetime are bounded; texture rendering uses the existing vanilla entity pipeline.

## 10. Bomb/missile accents and stronger flare glow

[M8_IMPACT_FLARE.md](M8_IMPACT_FLARE.md) records the requested follow-up, its quality/load limits,
native scene and measured software-renderer frame intervals.

- [x] **Bomb size:** enlarge heavy-bomb/incendiary ground marks and power-scaled initial
  fire/smoke after playtest feedback. Shared quad, particle and coverage budgets still apply.
- [x] **Bomb accents:** a short animated hot core and six/ten fast outward dust puffs on
  Balanced/High, using sampled material tint. Existing block debris and the fuller smoke
  silhouette remain; Low or overload sheds the secondary accents.
- [x] **Missile explosions:** tighter general/penetrator cores, shorter sparks and glow, and
  fire tint cooling over 18% of puff life. Broad bombs retain longer fire; ground profiles match.
- [x] **Flare glow:** brighter warm-white physical core, larger textured halo and a soft additive
  outer disc with restrained per-entity flicker. Weighted view/distance/load admission bounds
  the halos. Chaff keeps its unlit appearance and existing physics.
- [x] **Native validation:** Vulkan/OpenGL captures, 187 JUnit tests and overlapping-effects
  frame samples pass their functional/budget checks; software timings remain above 16.67 ms.
- [ ] **Hardware acceptance:** representative combat on RTX 3060 8 GB + 7800X3D at 1080p,
  targeting 60 FPS / 16.67 ms. Software-renderer checks cannot certify this target.

These impact/flare layers use existing vanilla shader/pipeline families and pixel artwork,
without extra scene copies or dynamic world lighting. The later optional engine heat layer
has its own conditional scene copy (§16). Gameplay damage, trajectories, destruction and
countermeasure behavior remain controlled by the port's existing systems.

## 11. Rotor-wash follow-up

[M8_ROTOR_WASH.md](M8_ROTOR_WASH.md) replaces enhanced helicopter wash's rising puffs with flatter,
ground-level wisps, outward
motion, mild swirl and smooth height/throttle/speed fading. Use main-rotor positions with
coaxial deduplication, surface-specific dust/snow/spray and faint broken water ripples.
Bound terrain sampling and particle admission; preserve Classic and other vehicles' wake emitters.
Native material, moving-hover, high-altitude, repeated-tick and overlapping-helicopter checks
pass on both Vulkan and OpenGL, including thin snow/powder snow. Hardware FPS remains open.

## 12. Explosion structure and engine exhaust

- [x] **Explosion structure:** give large blasts a short white-hot centre inside a softer,
  irregular orange flash, varied overlapping fire lobes, fast outward dust and heavier smoke
  that develops upward over time. Smaller impacts get a quicker, compact sequence. Improve
  connected cloud shading while retaining pixel silhouettes and bounded overdraw/admission.
  See [M8_EXPLOSION_STRUCTURE.md](M8_EXPLOSION_STRUCTURE.md) for the bounded primary emitter.
- [x] **Engine exhaust plumes:** faint short wisps at helicopter turbine outlets; brighter jet
  plumes only where nozzle/afterburner definitions support them. Intensity follows throttle;
  heavy smoke remains a damage cue. Use content-defined outlets, existing pipelines and
  distance/adaptive caps. Optional heat refraction is added in §16.

These requests follow the rotor-wash work. The 60 FPS target on the specified PC
remains the acceptance goal; each added layer needs native captures and measured cost.

## 13. Engine exhaust implementation

[M8_ENGINE_EXHAUST.md](M8_ENGINE_EXHAUST.md) implements the engine-outlet item in §12: short
throttle-responsive turbine wisps, restrained hot jet accents, explicit helicopter outlets and
existing plane nozzle/flame attachments. Low/Balanced/High global emission, shared smoke admission,
range and adaptive periods bound cost. Classic nozzle smoke and existing damage smoke remain.
Stock Apache and two Black Hawk variants include calibrated outlet positions; other helicopter
definitions can opt in. §16 adds Viper/Chinook outlets and optional heat refraction;
target-PC acceptance remains open.

## 14. Combat-load follow-up and remaining acceptance

- [x] Rolling fire, weapon-specific blast shapes, dirt streams, amber smoke backlighting and
  terrain flashes; reduced bullet glow and sparse tracers. See [M8_BLAST_MOTION.md](M8_BLAST_MOTION.md).
- [x] Spread terrain fitting and cache maintenance over bounded tick allowances, preserving
  completed footprint sizes. See [M8_TERRAIN_BUDGET.md](M8_TERRAIN_BUDGET.md).
- [x] Provide a repeatable combined scene with moving bullets, aircraft exhaust/wash, flare
  volleys, overlapping blasts, raw frame/counter traces and a particle/queue cooldown check.
  See [M8_COMBAT_ACCEPTANCE.md](M8_COMBAT_ACCEPTANCE.md).
- [x] Give rockets a fire/smoke impact mix and large general missile warheads (including the
  AGM-158) a fuller blast profile. See [M8_MISSILE_IMPACTS.md](M8_MISSILE_IMPACTS.md).
- [x] Prioritize the initial hot pocket over the blast's own support smoke/glow in close views;
  verify rocket and AGM-158 core admission at maximum adaptive pressure on both backends.
- [x] Improve tracer readability with narrow bright cores, tapered tails and visible burning
  bases along the firing view. See [M8_TRACERS.md](M8_TRACERS.md).
- [ ] Hardware acceptance at 1080p on RTX 3060 8 GB / Ryzen 7 7800X3D: representative sustained
  firing, overlapping explosions and mixed vehicles, targeting 60 FPS / 16.67 ms.
- [ ] Final visual acceptance in actual flight and battles, including addon/content combinations.

The user has requested the optional heat distortion, ground dust front and further exhaust
placements. Their bounded implementation is documented in [M8_FOLLOWUPS.md](M8_FOLLOWUPS.md).
Additional helicopter exhaust-outlet calibration remains content-specific; current support
is documented in [M8_ENGINE_EXHAUST.md](M8_ENGINE_EXHAUST.md). The native test
scenes validate behavior and work bounds; they cannot replace target-hardware acceptance.

## 15. Tank and vehicle ground tracks

- [x] Add tread imprints for tanks/tracked vehicles and tire marks for wheeled vehicles, using
  their actual ground-contact positions and track/wheel width. Paths should follow turns,
  reversing and separate left/right tread motion instead of stamping beneath the vehicle centre.
- [x] Match the surface: disturbed soil/sand, pressed snow and restrained scuffs on hard ground.
  Respect slopes, slabs and terrain steps; stop at gaps, airborne contacts and submerged surfaces.
- [x] Keep the simple pixel-art style, strengthen turning marks, fade older marks gradually
  and remove them when their surface changes. Broken tread bars and paired tire grooves
  distinguish vehicle types.
- [x] Use client-only cached geometry with bounded contact sampling, segment counts, draw
  distance and lifetime. Coalesce/space samples while moving; shed distant or old marks under
  load. Clear caches on world changes and avoid terrain queries during drawing.
- [x] Native fixtures: straight travel, turns, reversing, slab/step fitting, material/water/air
  rejection, changed surfaces, multiple stock vehicles and combined explosions/rotor wash,
  including Low and maximum adaptive pressure on Vulkan and OpenGL.
- [ ] Final player-driven slope/transition and addon visual acceptance. Profile against the
  existing 60 FPS target on RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p before raising budgets.

Implemented in the requested order: fitting/bounds, materials, then contact paths. See
[M8_VEHICLE_TRACKS.md](M8_VEHICLE_TRACKS.md) for limits and reproduction. Marks are cosmetic;
they do not change terrain blocks, traction, handling, damage or server simulation. Movable
addon vehicles require explicit wheel data; unsupported placed weapons leave no marks.

## 16. Requested optional effects and fuller missile smoke

- [x] More rocket/anti-tank impact smoke within existing primary caps, wider billow spread,
  longer smoke retention and a larger smoke share for general cruise-missile warheads.
- [x] A broken, fast outward ground dust front with material tint, shared admission and strict
  per-tick/read limits; shed on Low and at adaptive pressure one or higher.
- [x] Calibrated AH-1Z Viper and CH-47 Chinook turbine outlets, with a bundled content revision
  refresh so existing default packs receive the definitions.
- [x] Local exhaust heat refraction on both supported backends using a small GLSL 330 pipeline,
  reversed-depth masking and one shared conditional color copy. Low/Classic/adaptive pressure
  disable it. The final world projection is read without altering the camera or simulation.
- [ ] Target-hardware profiling and final visual acceptance in representative player-driven
  combat remain required, especially for the optional scene copy.

See [M8_FOLLOWUPS.md](M8_FOLLOWUPS.md) for appearance, limits, verification and remaining acceptance.

## 17. Realism follow-up (60 FPS target)

The user changed acceptance to **60 FPS / 16.67 ms**, RTX 3060 8 GB, Ryzen 7 7800X3D,
1080p. New configs default to `effects.targetFps=60`; existing configured values remain user-owned:
set that key to 60 on existing installations. Particle/admission caps are unchanged.

- Implemented: shared slow wind for enhanced explosion, engine and damage smoke; initial
  ejection/curl settles into drift, with smaller wisps fading sooner than dense cores.
- Implemented: 2–3 tick flashes, retained delayed cloud development and prompt ground debris.
- Implemented: local-material dust fronts and wash density; hard surfaces trade dirt jets for
  block-textured chips. Existing sand/soil/snow profiles and separate water spray retained.
- Implemented: bounded geometry crease shadows/edge highlights, baked to ready vertex colors
  at model load. This preserves stock textures; texture-only detail still needs authored art.
- Implemented: nearby footprint-aware vanilla shadows, retaining existing track/scorch surface
  fitting. Distant/Low/adaptive fallbacks avoid unnecessary footprint work.
- Checked: daylight/night/roof shade and delayed amber illumination in native fixtures;
  corrected local-age lighting and both Classic switches. Tracer-only bullet glow unchanged.
- Checked: Vulkan/OpenGL functional scenarios and mixed-combat work/drain checks with the
  adaptive target explicitly set to 60. Software rendering does not certify target-PC FPS.
- Remaining acceptance: player-driven stock/addon visual checks and sustained 60 FPS on the
  specified PC. Content-authored machinery shading and ramp/transition software checks are
  completed in §18. Additional hand-repainted skins would be a separate optional art pass.
  Historical captures retain their original targets.

### Realism slice 2: geometry shading and local dust fronts

- Added [vehicle surface shading](M8_VEHICLE_SHADING.md), baked during vehicle model loading:
  neutral flat panels, up to 12% recessed shading, up to 3% edge highlights. Toggle with
  `effects.vehicleSurfaceShade`; Enhanced intact vehicle draws only. Thermal, destroyed,
  iron-curtain and emissive rendering bypass it. Texture-only detail still needs authored art.
- Dust-front sectors now reuse their sampled support block for local material color and speed,
  instead of using the blast center's material across sand/soil/stone transitions. Read caps
  and particle request counts remain unchanged.
- Further content-aware shading is completed in §18. Broader stock/addon gameplay review and
  target-PC 60 FPS measurement remain acceptance items.

### Realism slice 3: ground contact

- Footprint-aware nearby vanilla vehicle shadows, controlled by `effects.vehicleContactShadows`.
  Balanced/High radius caps 2.5/3; Low/load restores the original 2. No new render pass, but
  wider shadows increase bounded vanilla support sampling. See [ground contact](M8_GROUND_CONTACT.md).
- Rotor-wash density now uses each candidate's local surface/height, not the rotor center's
  material. Original per-rotor sample slots retained; review confirmed they do not overlap
  with the existing two-candidate maximum. Read/cache/admission caps remain.
- Existing track/mark surface fitting retained. Moving ramp/transition software checks are completed in §18. Player-driven visual acceptance
  and real-hardware performance remain open; controlled lighting checks are below.

### Refinement after ground-contact slice

- Surface tint is now baked to packed vertex colors during model loading. The render loop
  reads colors directly instead of rounding/clamping three channels for every vertex every frame.
  Cache storage remains four bytes per vertex; nonstandard tint paths continue to bypass it.
- Delayed smoke amber lighting now uses the same local age as its sprite animation.
- A dedicated opt-in realism fixture renders M1A2 shadow/shading toggles and captures night,
  roof shade and sand/grass-boundary explosions. No changes to weapon gameplay or tracer glow.

### Material refinement

Hard surfaces replace dirt-jet slots with block-textured chips, while sand and soil retain
loose jets. Same request count and shared fragment caps. A six-slot Balanced broad response
requests four chips on rock versus two on sand/soil; isolated native scenes check the result
without a primary cloud obscuring ground admission. Boundary capture occurs at age six.

### Cross-effect wind and Classic cleanup

Engine wisps and enhanced damage smoke now use the same slow wind field as explosion smoke,
after their existing launch/vehicle-motion impulse. Lifetimes, emission rates and source caps
remain unchanged. Trails, water spray and Classic smoke retain their existing motion.
Vehicle surface shading now uses the combined quality/style Enhanced gate: either Classic
switch restores original colors. The atmosphere fixture checks rendered vehicle states after
switching style to Classic, in addition to its existing emission/audio/state cleanup assertions.

### Work-budget refinement

Skip footprint calculations beyond vanilla's nearby shadow range and on Low/adaptive fallback;
only eligible nearby vehicles inspect their wheel footprint. Build dust-sector tint only after
particle admission. Added a far-shadow native scene: body rendering/shading still works, with
no shadow pieces and the original fallback radius. No density/quality caps raised.

### Explicit 60-target combat acceptance

Both Mesa backends passed the mixed-combat soak with `effects.targetFps=60`: empty/model
baselines plus fixed/adaptive overlapping weapon VFX, 600 tick samples per phase. Bounds
and transient drain checks passed; adaptive pressure reached 3. This is functional software
acceptance, not RTX 3060 performance acceptance. Compressed raw CSVs and frame-interval
summaries are in [combat-60fps](performance/m8-realism/combat-60fps/README.md).

## 18. Remaining realism work closure

[Completion implementation](M8_COMPLETION.md) adds content-authored machinery masks to static
hull vertex colors, stable interpolated tread origins across supporting wheel changes, and
weapon comparisons from actual definitions. Ground ignition no longer makes conventional
Hellfires/bunker-busters use the long-burning profile. Large shallow HE missile clouds gain
a bounded directional footprint; bomb spread and legacy small-rocket smoke remain distinct.
All existing source/geometry/terrain limits remain. Original texture artwork is retained;
vehicle-specific depth is authored from machinery data and baked to colors rather than
requiring replacement textures. Final hardware 60 FPS acceptance is separate from code completion.

## 19. Explosion contrast refinement

[Blast contrast](M8_BLAST_CONTRAST.md) refines the opening flash, staged billow silhouette,
separated rolling fire pockets and bounded distance-delayed audio. Existing weapon families,
terrain-specific dust/debris and the 60 FPS target remain. Protocol 30 carries the independent
sound event so silent explosions do not acquire sound through their visual packet.

The overlapping-audio follow-up prioritizes opening impacts over optional rumble, bounds
actual playback to four voices/tick, and expires stale queue entries after client stalls.
See the blast-contrast document for the sustained-volley validation.

## 20. Larger explosions and crater-aware coverage

[Crater-scale effects](M8_CRATER_SCALE.md) deliberately relax Balanced/High particle allowances
and use reported affected blocks to size the smoke footprint. Rim dust samples local height
within the existing terrain-read budget. Adaptive feedback retains the 60 FPS target; actual
target-PC performance remains an acceptance check.

The same pass adds selected larger bouncing chunks, tapered dirt columns, layered fire,
nearby crossed smoke cards and actual low dust-wedge geometry. Optional extra faces pay
admission costs and shed under load.

User constraint: preserve the existing explosion crater algorithm and weapon destruction
rules. The primary goal is VFX that look proportionate to those original craters. Round
craters in native comparison scenes are disposable test fixtures, never gameplay changes.

## 21. Toggleable modern explosion style

[Modern explosion plan](M8_MODERN_EXPLOSIONS_PLAN.md) specifies an ESC → MC Heli Effects panel
with a persisted, explosion-only Modern On/Off preference. Off keeps current M8 presentation;
On enables new billow/fire art and volumetric-looking depth within the existing adaptive budget
system. Preserve original crater formation and size VFX to its reported footprint. Default Off
until player acceptance. [Implementation](M8_MODERN_EXPLOSIONS.md) adds the saved menu toggle,
modern atlas and per-weapon rendering path. Target-PC 60 FPS/artistic acceptance remains open.

## Default graphics refinement

[Default visual refinement](M8_DEFAULT_VISUAL_REFINEMENT.md) improves smoke silhouettes,
localized fire/smoke contrast, baked vehicle material cues, terrain scars, ground contact
and distance transitions within the existing budgets. Keep the Modern on/off toggle,
original craters and 60 FPS target. NTM work remains owned by Claude and is untouched.

## Localized vehicle damage

[Vehicle impact marks](M8_VEHICLE_IMPACT_MARKS.md): gun-hit paint/metal chips, explosive
cannon soot, cracks on named glazing, part-local attachments and gradual repair fading.
Use bounded optional cosmetic notifications, clipped overlays and the existing damage-details
setting. Keep gameplay damage, craters and NTM unchanged. Mesh deformation is separate work.
