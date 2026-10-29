# Default blast readability and material refinement

This pass implements the six approved refinements on top of the completed flight/effects-range
batch. Polished Current M8 and Modern explosions share the timing, terrain and distance changes.
Classic and vanilla retain their original particle path. The Modern on/off menu is unchanged.

## Opening and smoke

- The blast flash lasts two ticks, begins at 65% size and decays immediately. Its final radius is
  smaller, leaving the surrounding fire pockets readable. Existing wreck/cook-off flashes retain
  their original curve.
- Hot pockets expand over four ticks. Support smoke waits 2–12 ticks before appearing, with
  slower growth. Incendiaries retain their longer fire duration.
- A few dominant billows survive detail reduction. Other lobes vary substantially in size, height
  and opacity; small edge wisps fade sooner. Small rockets mix the familiar growing puffs with
  rolling billows. The earlier approved explosion/wisp sprite artwork is reused unchanged.
- Rocket smoke and hot-pocket placement follow the known incoming heading. Bombs spread low and
  wide; penetrators concentrate the surface eruption and lift their upper billows faster. These
  are readable cosmetic families rather than exact simulations of particular warheads.

## Ground and crater scale

The legacy affected-block list includes **air**. It remains intact for Classic/vanilla. A separate
cosmetic reservoir samples at most **256 solid blocks** from the existing destruction loop,
without using game RNG, changing block order or making additional reads inside that loop.
After destruction, at most 256 reads omit sampled blocks still solid after protection callbacks.
No terrain-destruction flags, resistances, fire/drop randomness, damage or craters are changed.

The effects payload adds at most 1,538 bytes for these short-offset samples; protocol **33**
requires matching updated client/server builds. Empty samples use the nominal visual size rather
than treating air rays as a crater. No extra explosion ray march is performed.

Client-side, 12 angular sectors estimate each part of the disturbed footprint and its former
height. Dust fronts begin at uneven distances inside those sectors. Each front samples the
remaining loaded surface for its color and height; empty sectors do not invent a circular rim.
Rim work runs even if the central crater is too deep for a center surface; a few existing rim
wisp slots become narrow dirt jets in that case. Surface reads retain
the shared **96/tick Balanced, 192/tick High** cap; no chunks are loaded for effects. The packet
sample is an approximation and is deliberately not a complete crater contour.

Large reported craters can expand smoke up to four times its nominal radius, still capped at
16 blocks Balanced / 22 High, with coverage charged before admission. Low retains the earlier
1.25× multiplier and 10-block cap. The bright core stays at its original nominal family size.

## Material contrast

Surface shading is baked during model loading and shared by vertex and baked-GPU render paths.
Known rubber stays matte, undersides of paint/vents receive restrained shading, and known glass
gets a small upper-surface highlight. No textures are replaced, and there is no extra draw pass.

Mixed canopy/frame groups use alpha evidence: a triangle must have translucent interior samples
in **every selectable livery** before receiving the glass cue. Opaque frames and alpha-cut holes
remain unchanged. Decoding is loader-only, limited to eight liveries and 1,048,576 pixels per
texture. Missing, oversized or unsupported textures keep painted-metal treatment. Unknown body
windows or mixed wheel/gear meshes are not guessed from dark paint. Existing material/detail
settings and thermal bypass remain in effect.

## Distance and limits

Primary cloud requests already decrease smoothly with distance. Removing a second 64/128-block
admission stride prevents the same cloud being thinned twice; vanilla particle settings,
coverage limits, adaptive pressure and the shared live cap still apply. Dominant clouds remain
while optional crossed cards, dirt-column segments and dust wedges transition over several
ticks. Jets/wedges fade into a billboard instead of vanishing at the detail boundary. Their
maximum four-quad transition cost is admitted at birth.

Balanced limits remain 1,536 live smoke particles, 256 coverage units/tick, 36 live fragments,
6 fragment births/tick and 20 rim births/tick. Overlapping explosions share these budgets.
This is depth-enhanced sprite/mesh rendering, without a volumetric ray marcher or new world lights.

## Verification

Native screenshots and measured diagnostic counts are listed in the
[gallery](M8_BLAST_READABILITY_GALLERY.md) and
[validation record](performance/m8-realism/blast-readability.json).
Range photos use actual production projectiles, weapons and original crater settings. Separate
diagnostic photos compare materials, blast families, detail transitions and overlapping salvos.

All **539 unit tests** and **295 required local GameTests** pass. Native validation covers ten
diagnostic scenes on each backend, two additional Vulkan deep-center/airburst cases, one final
canopy-loader check and seven actual-weapon/style/water scenarios in the Vulkan range. All
particles drain; shared ground/admission limits remain enforced. The range restores the original
player mode/position and stays within its 2,048 block operations/tick limit. The dev range probe
confirms server projectile transit as well as client observation, so skipped software-renderer
frames cannot misidentify a real shot as absent. Locally excluded optional dependencies are
validated separately by the repository's normal and real-NTM CI jobs.

The target remains **60 FPS at 1080p on RTX 3060 8 GB / Ryzen 7 7800X3D**. Native checks here use
software Mesa; particle counts and successful renders do not establish target-PC frame times.
Claude's NTM, broken-part art, persistent wrecks and AI work are outside this pass.
