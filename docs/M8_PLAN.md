# M8 plan: effects polish for Vulkan

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
- **Both backends.** Only vanilla pipeline state (`RenderPipeline` snippets, blend functions,
  depth states) and vanilla shaders. No raw OpenGL and no shaders of our own. Every slice is
  checked on OpenGL and on Vulkan.
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
| Explosion shockwave and debris | Block dust | Bounded sparks, material dust fans and tumbling surface chips/clods; [ground response and limits](M8_BLAST_DEBRIS.md); ground ring deferred pending hardware profiling | Follow-up (done) |
| Smoke lighting | Always full sky light (upstream sampled 3000 blocks up) | Cached local sky/block light; time of day handled by the existing light map | 2 (done) |
| Engine exhaust | Addon exhaust flames and plane nozzle smoke | Short bounded turbine wisps and restrained jet accents at supported outlets; distortion deferred | 3 (plumes done) |
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

## 7. 80 FPS performance follow-up

The target is RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p (12.5 ms per frame).
[M8_PERFORMANCE.md](M8_PERFORMANCE.md) documents adaptive cosmetic detail, coverage-weighted
admission, dust/spray budgets and repeatable frame-interval capture. Hardware acceptance remains
open; software-renderer checks cannot certify this PC's FPS.

## 8. Lighting and damage-fire follow-up

[M8_LIGHTING_FIRE.md](M8_LIGHTING_FIRE.md) records cached local smoke lighting, heat hints on
existing damage puffs and tiny explosion sparks with quality/load caps. Classic remains
available; the 80 FPS hardware target and real battle/flight checks remain open.

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
  frame samples pass their functional/budget checks; software timings remain above 12.5 ms.
- [ ] **Hardware acceptance:** representative combat on RTX 3060 8 GB + 7800X3D at 1080p,
  targeting 80 FPS / 12.5 ms. Software-renderer checks cannot certify this target.

The implementation uses existing vanilla shader/pipeline families and pixel artwork. No extra
scene copies or dynamic world lighting. Gameplay damage, trajectories, destruction and
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
  distance/adaptive caps. Heat-distortion rendering remains deferred.

These requests follow the rotor-wash work. The 80 FPS target on the specified PC
remains the acceptance goal; each added layer needs native captures and measured cost.

## 13. Engine exhaust implementation

[M8_ENGINE_EXHAUST.md](M8_ENGINE_EXHAUST.md) implements the engine-outlet item in §12: short
throttle-responsive turbine wisps, restrained hot jet accents, explicit helicopter outlets and
existing plane nozzle/flame attachments. Low/Balanced/High global emission, shared smoke admission,
range and adaptive periods bound cost. Classic nozzle smoke and existing damage smoke remain.
Stock Apache and two Black Hawk variants include calibrated outlet positions; other helicopter
definitions can opt in. Heat distortion remains deferred and target-PC acceptance remains open.
