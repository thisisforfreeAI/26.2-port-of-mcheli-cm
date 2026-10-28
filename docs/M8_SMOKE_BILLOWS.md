# Smoke billows and 48-frame artwork

Default Polished smoke now uses the approved Minecraft / MC Heli themed sprite style: shaded
pixel clusters, folded dark cores and irregular broken edges. Three original 4×4 sheets supply
**16 frames each, 48 total**: explosion billows, wreck-soot curls and dissipating wisps. Frames
advance with particle age and stop at the final sparse frame instead of looping back to dense
smoke. Existing hot explosion artwork and the Modern explosion toggle remain in place.

## The six smoke refinements

1. **Connected rolling billows.** Wreck emissions share a column seed and time/height curl field.
   Their overlapping footprints, slower rise and mixed rounded/sooty shapes form a connected
   plume. Explosion lobes retain initial radial ejection and gain restrained shared curl once cold.
2. **Distinct movement.** Blast ground smoke remains low and expands rapidly; upper blast smoke
   rises more slowly. Wreck smoke starts tight at the opening, expands as it rises, and couples
   more strongly to the shared wind higher up.
3. **Dense cores and broken edges.** The new art carries sculpted internal shading. Wreck cores
   live 68–88 ticks; wisps live 38. A wisp replaces one of every five emission slots. Rounded
   explosion billows mix with wreck-soot curls, preserving a substantial core as edges peel away.
4. **Internal contrast and local warmth.** Birth variation and the art separate neighboring
   folds. Brief amber tint and up to roughly two block-light levels stay near young, low smoke
   beside existing fire; they expire by 24 ticks or three blocks of rise. Mature smoke uses sampled
   ambient daylight/shade. No world light is added.
5. **Uneven wreck emission.** Shared slow surges vary opacity and source spread; the existing
   cooling density lowers emission frequency. Below 35% density, quiet intervals break the late
   plume into intermittent smoke. Old wrecks keep their existing cooling initialization.
6. **Dissipation by edge breakup.** Later atlas frames open gaps. A normal alpha-blended particle
   shader erodes the thin edges with a stable coarse pattern, while retaining standard fog and
   lighting. It filters nearly transparent matte noise so overlapping sprites do not reveal their
   rectangular cells, and neutralizes cold edge-color fringes. Hot flame/white-hot texels retain
   their existing color and opacity behavior.

Classic style, Minecraft's default explosion-particle option and the old atlas fallback retain
their previous paths. The default smoke change affects new Polished explosions and wreck smoke;
it adds no new menu setting, network payload, damage rule or crater algorithm. NTM Next's
explosion integration, weapons and gore code are untouched.

## Artwork and asset contract

| Family | Atlas | Playback |
| --- | --- | --- |
| Explosion | `m8_billow_explosion.png` | 16 frames; new default smoke-led rocket puffs and cold blast lobes |
| Wreck soot | `m8_billow_wreck.png` | 16 frames; mixed with rounded billows in wreck cores |
| Wisps | `m8_billow_wisps.png` | 16 frames; shorter wreck wisps and selected blast-edge lobes |

Artwork was generated specifically for this project from the user's approved concept direction.
The three source PNGs were copied unchanged from the generated assets; no NTM artwork was used.
The sheets are distinct from native Minecraft screenshots in the gallery. Explicit particle-atlas
entries expose each sheet. Normalized 4×4 UVs select one frame, without per-particle texture uploads,
frame blending quads or per-frame atlas lookup/allocation. Missing new sprites fall back to the
previous art. Resource packs overriding these identifiers should preserve the 4×4 frame layout.

The generated source dimensions are 1254×1254, 1271×1238 and 1278×1230. The observed shared
Minecraft particle atlas is 4096×4096 at mip level zero (about **64 MiB RGBA** total for that atlas,
including other particles). Additional frames trade texture memory for richer animation;
particle births and geometry do not multiply by the frame count.

## Work and lifetime limits

The configured pools remain unchanged:

| Preset | Shared effects pool | Separate FlashParticle pool | Atmosphere births/tick |
| --- | ---: | ---: | ---: |
| Low | 384 | 48 | 8 |
| Balanced | 1536 | 96 | 16 |
| High | 3072 | 192 | 24 |

The larger pool includes smoke, debris and several glow accents; not every bright effect belongs
to the separate flash pool. Admission additionally limits requested work, distance, estimated
coverage and optional geometry; Minecraft's particle setting and adaptive pressure still apply.
The config tooltip now reflects these actual limits instead of its old 768/1536 values.

Wreck births retain the existing 4/8/16-tick cadence, multiplied by adaptive pressure. Surges add
no burst births, and late quiet intervals skip slots. Core lifetime is longer than the old fixed
55 ticks, so steady-state live counts can rise while staying inside the shared pool. Wreck
radius is bounded at 2.2 blocks before the per-age growth envelope; admission pays that largest
radius. The shader takes one atlas sample and adds no scene copy, depth texture, auxiliary
noise texture, world query, extra quad or dynamic light. Existing multi-card blast detail still
pays its original admission cost.

The target remains 60 FPS at 1080p on RTX 3060 8 GB / Ryzen 7 7800X3D. Software Vulkan/OpenGL
checks establish rendering and behavior, not frame rate on that PC.

## Validation

`SmokeBillowLookTest` checks bounded rise/growth/wind coupling, shorter wisps, finite local amber,
quiet intervals, monotonic 16-frame playback and populated/distinct alpha art in all 48 cells.
The full existing unit and server GameTest suites remain required, including the NTM-installed CI
configuration. Non-shipping `SmokeBillowProbe` runs controlled wreck ages through the normal
vehicle emitters, checks effect-pool and atmosphere-birth bounds, and verifies owned effects drain
after repair. Its blast mode covers small/large impacts, Modern, a repeated mixed volley and
Classic; both Vulkan and OpenGL must draw the new shader successfully.


Local results: **376 unit tests and all 268 required GameTests passed**. Native Vulkan checks
passed hot/cooled smoke and repair cleanup on three wrecks. Native OpenGL checks passed small,
large, Modern-night, repeated-volley and Classic scenes, including effect drain. See the
[twelve-image gallery](M8_SMOKE_BILLOWS_GALLERY.md) and
[machine-readable evidence](performance/m8-realism/smoke-billows.json).
