# M8 explosion structure

Enhanced dry explosions use a fixed, power-scaled group of connected fire/cloud lobes instead
of scattering smoke for every affected terrain block. This makes airbursts visible and gives
large bombs a fuller body, outward expansion and a rising crown. Small bombs and missiles use
a smaller footprint and faster cooling. Incendiaries retain fire longer. The new original
32-pixel frames have irregular silhouettes and broad shading rather than concentric puff rims.

One short additive white/yellow flash has an uneven orange edge. It replaces the previous large
circular glow and is optional under load. Existing bounded outward material dust and tiny sparks
follow the primary cloud. Smoke cools into cached local lighting; it does not remain full-bright
at night. Ground marks still project separately onto surviving terrain.

## Bounds and compatibility

- At most 6/12/18 primary lobes per Low/Balanced/High explosion, and at most four for cannon
  impacts. Pressure divides the requested count by 1–4, with two requested primary lobes at the
  highest pressure. Shared admission can reject any request.
- Every lobe charges its **final** radius against weighted smoke admission, shares the engine's
  live smoke limit and uses the existing translucent particle pipeline. Flash, sparks and dust
  also share this budget; muzzle flashes retain their separate reserve.
- Base radius caps at 6.4 blocks for broad bombs/incendiaries and 3.2 for other weapons. Per-lobe
  variation is at most 1.2 times that radius. Lifetimes cap at 111 ticks including variation.
- No client terrain ray march for enhanced dry clouds; the primary loop is independent of
  affected block count. Dust keeps its single-column, loaded-only search of at most 13 blocks.
  Clouds have no collisions or entity wind searches. No delayed-emission queues.
- Classic, vanilla explosion particles and underwater effects retain their existing emitters.
  Damage, blast radius, destruction, projectiles and network messages do not change.
- The existing `m8_smoke` sheet remains for vehicle damage, trails and other smoke. The new
  `m8_blast_cloud` and `m8_blast_flash` sheets are reproduced by
  `python3 scripts/generate-effect-textures.py` without external art or dependencies.

## Validation

`ExplosionSequenceTest` checks bounded work for extreme power, pressure shedding, compact
missiles, larger bomb volume and fire-to-smoke timing. `EffectTextureTest` packs the actual new
sheets with the other sprites through the pinned vanilla atlas stitcher.

The opt-in `ExplosionStructureProbe` captures Classic size-12, enhanced size-12, size-2,
missile and incendiary blasts at ages 2/10/30/60. Enhanced calls deliberately pass an empty
affected-block list. It also checks cloud expiry/shared limits, then records empty, fixed-detail
and adaptive overlapping-impact frame intervals at 1920×1080.

Run with Java 25, `-I scripts/visual-probe.init.gradle`, and
`-Dmcheli.probeExplosionStructure=true`; choose `-Dmcheli.probeBackend=OPENGL` for OpenGL or
leave the Vulkan default. This development scene is excluded from the normal production jar.

This environment requires an external init script excluding the optional JEI integration and
its dependency because `maven.blamejared.com` is blocked. Repository JEI sources/dependencies
are unchanged. Native software-renderer checks verify rendering and limits; **80 FPS on the
RTX 3060 8 GB / 7800X3D at 1080p still requires hardware acceptance**.

Both native scenes completed with the shared-limit/expiry assertions, isolated blasts and
overlap scenes. Vulkan also captures the incendiary scene at midnight. Real captures:
[flash](screens/m8-structure-vulkan-flash.png), [large fire](screens/m8-structure-opengl-fire.png),
[developing smoke](screens/m8-structure-opengl-smoke.png),
[small blast](screens/m8-structure-vulkan-small.png), [night](screens/m8-structure-vulkan-night.png).

Recorded frame intervals below are from software Mesa at 1080p, with VSync off, a runtime cap
of 120 FPS and four render chunks. Each combat scene triggers three overlapping impacts every
25 ticks. No builds or other native scenes ran during sampling. They are functional/cost
observations on software rasterizers, not a benchmark of the target GPU.

| Renderer | Scene | Frames | Median ms | p95 ms |
|---|---|---:|---:|---:|
| Vulkan / lavapipe | Empty | 91 | 67.563 | 99.348 |
| Vulkan / lavapipe | Fixed | 72 | 86.935 | 101.415 |
| Vulkan / lavapipe | Adaptive | 80 | 75.803 | 93.128 |
| OpenGL / llvmpipe | Empty | 87 | 71.461 | 93.026 |
| OpenGL / llvmpipe | Fixed | 73 | 84.193 | 116.557 |
| OpenGL / llvmpipe | Adaptive | 80 | 75.446 | 97.736 |

At the sample scene's end, fixed detail had 95/104 particles (Vulkan/OpenGL), while adaptive
detail had five at pressure three in both. These are end-scene snapshots, not average live
counts or universal savings. Every scene, including empty, misses 12.5 ms here. CSVs and
summaries are in `docs/performance/m8-structure-lavapipe` and `m8-structure-llvmpipe`.
