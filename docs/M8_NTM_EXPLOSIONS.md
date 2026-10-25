# Native NTM Next explosion mode

ESC → MC Heli Effects → **Explosions: Current M8 / Modern / NTM Next**.
The choice saves immediately and affects new explosions. Existing particles finish in their
original style. Existing `effects.modernExplosions=true` configs retain Modern until the user
changes the selector. The new `effects.explosionMode` defaults to `CURRENT`.

NTM Next must be installed to supply its native particle classes, groups and artwork.
Use matching NTM modpack versions on server and clients, as required by NTM itself.
NTM also requires **Crankshaft 1.6.0+mc26.2 or newer on clients**; install its dependencies
through the mod manager. The optional `-PwithHbm` dev runtime now includes that dependency. This optional adapter targets NTM Next 1.0.3 for NeoForge 26.2, already used by the weapon
addon bridge. Missing or incompatible NTM is reported in the menu and falls back to Current M8.
Enhanced + Polished effects and disabled vanilla explosion particles remain authoritative.
Underwater impacts retain their separate splash effects.

## What matches NTM

This is native particle integration, not a newly painted imitation. The adapter constructs
NTM's public `ParticleExplosionSmall` and `ParticleMukeWave`, and calls its public
`ClientEffects.spawnRocketFlame` helper for large clouds on OpenGL. On Vulkan it constructs
NTM's `ParticleRocketFlame` instead: the helper's accelerated path caused a native Mesa
Vulkan crash during validation, while the portable NTM particle group passed. This keeps
NTM artwork and curves on both backends without exposing that tested crash path. NTM owns
their motion, rolling cloud layers, fire-to-smoke colors, alpha curves, textures, group
rendering and wave growth. Reflection is resolved once, only when HBM is installed. No NTM
source, artwork or binaries are copied or redistributed in McHeli.

The inspected upstream reference is [Warfactory-Official/ntm-next](https://github.com/Warfactory-Official/ntm-next)
commit `68ae79fd0aefc5811159aa39b1448bf69904541a`. Its large clouds contain ten internally
layered quads each. Medium/large baseline compositions use 15/30 clouds, scale 5/6.5,
speed 1/2 and native wave scale 45/65. Small impacts use the native small explosion cloud
rather than making every cannon impact a mushroom. Reported crater footprints can promote
large narrow warheads and enlarge the native cloud scale, capped at 12.

The complete stock NTM preset is **not** invoked: it also plays sound and launches its own
terrain-debris machinery. M8 retains its terrain-colored dust/fragments, scorch marks and
independent bounded sound queue, so silent visuals stay silent and sound never doubles.
The native cloud/fire/wave artwork is exact; composition and scale adapt to McHeli weapons.
No NTM nuclear/chemical/gameplay explosion classes or packet handlers run. Original server
craters, damage, ignition and destruction are untouched. Protocol stays 30.

## Cost and fallback

Each native large cloud pays ten faces; a small cloud or wave pays one. Shared per-mode caps:

| Detail | Live faces | Faces admitted per tick, pressure 0 |
| --- | ---: | ---: |
| Low | 240 | 80 |
| Balanced | 1920 | 400 |
| High | 3840 | 800 |

Distance, Minecraft particles and adaptive pressure thin the requested cloud count. Pressure
also reduces the tick allowance. Low omits waves; pressure 2+ omits waves. No deferred native
emission queue accumulates. Particle weak references and conservative face leases for NTM-owned instanced clouds are
reaped on removal/expiry/world change,
and disconnect clears tracking. Constructed particles are staged before submission. A failure after the native cloud helper
has submitted some clouds disables future native effects without doubling that partial batch. The bridge has no native mods
on its compile classpath; ordinary clients and dedicated servers remain independent of NTM.

The 60 FPS target is unchanged. Native NTM rendering can cost more than Current M8; these
limits bound admitted geometry, not FPS on an RTX 3060 / 7800X3D at 1080p.

## Validation

Unit tests cover old-config migration, exclusive mode resolution, invalid and extreme sizes,
crater-aware native profile selection and pressure/distance/particle-setting reductions.
The opt-in `NtmExplosionProbe` clicks the actual ESC selector, exercises all three choices,
checks gates, Done/ESC, persistence, native classes/waves versus missing-mod fallback, then
requests 2048 overlapping large blasts and checks live-face bounds and final drain.

`.github/workflows/ntm-explosion-client.yml` runs the real installed NTM runtime on both Vulkan
and OpenGL using Mesa/Xvfb, creates a disposable world through GameTests, verifies the actual
backend log, restarts the JVM to verify persistence, and uploads native screenshots/logs.
Local missing-mod checks use the same probe without NTM. These are functional/cleanup checks;
performance acceptance needs the target PC. Native validation results are recorded with the PR.

Local release and all 293 unit tests pass. Vulkan/OpenGL missing-mod UI/fallback and separate
JVM persistence pass, as does the complete existing Modern-menu regression. A newly copied
GameTest world also passes the Vulkan bootstrap and missing-mod path. Native NTM 1.0.3 + Crankshaft 1.6.0+mc26.2 passed on both Vulkan and OpenGL: actual
native classes/waves, 2048 overlapping requests, face bounds, final drain, ESC controls and
a separate JVM persistence check. Native-runtime results are recorded in [the evidence file](performance/m8-realism/ntm-explosions.json).

The final backend policy passed again in [native run 40145049227](https://github.com/thisisforfreeAI/26.2-port-of-mcheli-cm/actions/runs/40145049227):
Vulkan admitted zero accelerated clouds and used NTM's portable group; OpenGL used the
normal helper. Both passed 2048 requests, final drain and separate-JVM persistence. The
fixture now uses eight chunks of view distance so a blast forty blocks away is inside the
fog range; this only affects development captures.
