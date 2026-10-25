# Modern explosions from the ESC menu

Open **ESC → MC Heli Effects… → Modern explosions: ON**. The new client preference starts
Off; Off retains the current M8 art and explosion path. Changes apply to new explosions
immediately and save to `effects.modernExplosions` in the existing client config. Already
visible particles retain their birth art and finish naturally. No restart or resource reload
is required for the switch.

The panel also exposes existing Low/Balanced/High detail, adaptive detail, frame-rate goal
and a link to the full NeoForge mod settings. ESC/Done returns to the originating pause
screen. The pause-menu entry finds free space without moving/overlapping existing controls;
if an exceptionally crowded modded menu has no free slot, the standard Mods config remains
available. The hidden F3+ESC pause variant is left alone.

Global Classic settings and Minecraft explosion-particle mode remain authoritative. A saved
Modern On preference is inactive in those modes; the panel explains this and links to the
full settings. Toggling Modern never silently changes global style or vehicle/flare/tracer
presentation. The preference can still be switched Off while inactive.

## Rendering

A separate generated 4×4 modern fire/smoke sheet provides irregular shaded lobes, dense
creases and softer broken edges. Current art remains untouched. Both styles are preloaded
in the particle atlas, allowing live switching; missing modern art falls back to current
blast art. Packs can override the separate `m8_modern_blast_cloud` sprite while retaining
its 4×4 layout. The source PNG is copied directly from the image-generation output; no
external photographs or game assets were repainted into it.

Modern rocket/missile smoke uses the rolling cloud/depth path rather than the legacy puff
bridge. Nearby crossed planes, layered fire, 3D debris, tapered columns and mesh dust wedges
reuse M8's existing bounded geometry. Ordinary warhead fire cools sooner (at most 12 ticks);
incendiaries retain their longer burning profile. Tiny cannon impacts remain restrained,
and water impacts keep their dedicated splash response. Daylight/shade and brief amber
backlighting continue through the existing cached particle-lighting path.

Missile smoke coverage is fitted to the reported affected footprint rather than inheriting
the narrower fire radius: its target is 75% of reported radial extent, with existing wider
coverage retained and ceilings of 16 blocks Balanced / 22 High. Low keeps its small profile
bound. Smoke offsets/growth place billows around that radius; fire and flash stay compact.
Airbursts without a reported footprint retain profile sizing, with a modest small-blast boost.
This is a volumetric-looking particle/mesh approximation, not full volumetric ray marching.
There are no new shaders, scene copies, dynamic world lights or extra emission queues.

## Original craters and performance

Production changes do not touch crater formation, weapon damage, block selection, ignition,
terrain destruction, projectile physics, audio or packet encoding. They remain client-side
presentation choices. Reported affected blocks approximate the footprint and can include
positions that survive protection/gameplay rules. Circular holes in native comparison
captures are disposable fixtures, not replacement gameplay craters.

The particle/read/geometry budgets from [crater-scale effects](M8_CRATER_SCALE.md) remain in
force. Modern detail sheds geometry under adaptive pressure without resetting the saved
style preference. The effects goal remains 60 FPS on RTX 3060 8 GB / Ryzen 7 7800X3D / 1080p;
actual hardware FPS acceptance remains pending. Preloading the modern sheet has a fixed
atlas memory cost even when Off; the native test atlas was 2048×2048, pack-dependent.

## Validation

284 JUnit tests pass: opt-in/style gates, smoke/warhead timing and crater coverage, transparent
16-frame resources, collision-free pause-entry placement, plus existing gameplay/effect tests.
Native UI checks cover actual clicks, Done/ESC, control cycling, saved preference across a
separate client restart, global Classic behavior, full-config navigation, GUI resizing/scaling,
resource reload and unchanged birth art for particles alive during a toggle.

Both native backends passed eight Modern crater fixtures (seven stock weapon definitions
plus one synthetic power-12 bomb) with asserted side/overhead camera poses, geometry-presence
and bounce/settle checks. Vulkan Modern combat passed four phases / 2400 tick samples,
including fixed/adaptive detail, shared caps and all transient drains; pressure reached three.
[Compact results](performance/m8-realism/modern-explosions.json) and
[raw combat telemetry](performance/m8-realism/modern-combat.csv.gz) are preserved.
The latest OpenGL UI pass additionally exercised detail/adaptive/FPS cycling and Done.
Local builds exclude optional uncached JEI only; repository dependencies remain unchanged.
All preview-world edits and UI drivers are opt-in development sources excluded from release jars.
