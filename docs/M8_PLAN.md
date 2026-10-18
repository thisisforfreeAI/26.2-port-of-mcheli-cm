# M8 plan: effects polish for Vulkan

Status: started early, by the user's request, for the vehicles already ported in M6 (helicopters,
planes, ships, tanks and vehicles). This expands the M8 row of [PORTING_PLAN.md](../PORTING_PLAN.md)
§6. PORTING_PLAN.md places M8 after M6, M6b and M7; slices that need M7's sound/particle parity
or render path B wait for it. Preserve decisions D1 to D23.

## 1. Rules

- **Gameplay is untouched.** Effects stay client-only. No change to damage, projectile flight,
  targeting, timings, payloads or what the server sends. Emitters retain upstream timing and
  motion; client quality budgets may suppress cosmetic particles during heavy combat.
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
| Explosions / fire | Grey-brown smoke puffs, block dust, flares from large blasts | Explosion puffs are born as fire (hot orange, glowing at block light 15) and cool to their own smoke colour over the first 35 % of their life; a soft additive fireball glow under the blast | 1 (done) |
| Muzzle flashes | 8-frame starburst, alpha blended | Same starburst drawn additively, plus a short warm glow | 1 (done) |
| Tracers (machine-gun rounds) | Bullet model lit by the world, dark at night | Full-bright model with a soft streak one tick's travel long (at most 8 blocks) in the round's colour | 1 (done) |
| Missile trails | Flame quad, smoke trail | Smoke as above, plus an additive glow at the flame | 1 (done) |
| Flares | Yellow cube lit by the world, dark at night | Full-bright cube with a flickering two-layer glow; chaff unchanged | 1 (done) |
| Water splashes | Puffs that pop out | Fade as smoke does | 1 (done) |
| Rotor wash / downwash | Grey puffs whatever the ground | Puffs tinted by the ground's map colour (sand, dirt, snow); white spray over water | 1 (done) |
| Burning vehicles | Dark damage smoke | Fire at low health (needs a heat hint at the damage-smoke call sites of helicopters, planes and ground vehicles) | 2 |
| Explosion shockwave and debris | Block dust | A brief ring on the ground for large blasts; sparks | 2 |
| Smoke lighting | Always full sky light (upstream sampled 3000 blocks up) | Shade with the sky light and time of day at the puff's position | 2 |
| Heat haze, exhaust | Addon exhaust flames only | Investigate a vanilla-pipeline distortion substitute; may stay out | 3 |
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
