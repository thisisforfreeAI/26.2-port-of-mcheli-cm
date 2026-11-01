# Tank blow-out panels and turret separation

[Server-owned tank examples](M8_TANK_COOKOFF_GALLERY.md).

Fixes two failures in the earlier generic wreck sequence: the Abrams' pressure panels were never
identified, and loaded carousel tanks needed a favorable one-in-eight seed to lose their turret.
External observers also had no authoritative ammo counts, so they could fail ammo eligibility entirely.

- **M1A2:** clips the two actual bustle roof panels from the original `$weapon0` mesh, behind the
  crew hatches. They swing upward over seven ticks, then eject, leaving an open, darkened compartment
  with upward flame/smoke and fire light. The turret remains attached. Crew hatches, rear rack and
  the turret's remaining original surface stay separate from this cut.
- **T-90 / T84:** a fresh destruction with at least eight conventional explosive rounds now triggers
  the existing turret stage for every seed. The declared T-72/T-80/T-64 addon names retain the same
  design policy. Empty tanks, unsupported designs and NTM/HBM-only stores do not qualify.
- **Complete aimed assembly:** captures the renderer's actual posed turret, barrels and attachments,
  rather than applying the turret pose a second time to raw model groups. Texture coordinates and
  normals are retained. The complete fragment uses the existing debris physics and limits.
- **Budget fallback:** when all eight loose-fragment slots are occupied, pressure panels remain
  visibly raised. A turret visibly lifts and then rests canted/dislodged on the hull if a fragment
  cannot be admitted. No assembly disappears merely because a particle/fragment budget refused it.
- **Observer fix:** destruction sends the existing `NotifyAmmoPayload` once to players tracking a
  tank. The client accepts that snapshot even if its destroyed flag arrived first. No ammo is consumed
  or invented, and the payload format/protocol is unchanged. Normal rider notifications are retained.
- **Repair:** clears the open-panel/separated-turret presentation and its cached snapshot. Old wrecks
  do not replay a flying separation. Classic/disabled destruction effects retain the normal model.

No damage, blast, crater, vehicle collision, handling, ammunition consumption or NTM integration
changes. Mesh snapshots remain at most 12,000 vertices; the existing eight-piece / 24,000-vertex /
two-births-per-tick loose-debris limits remain. Source geometry is clipped once and cached.

## Native checks

```sh
./gradlew runClient -I scripts/visual-probe.init.gradle --no-configuration-cache \
  -Dmcheli.probeTankCookoff=true -Dmcheli.probeBackend=VULKAN
```

Repeat with `OPENGL`. The private `M6Smoke` fixture uses real server-owned tanks, fills their stores
only on the server, and invokes the existing server destruction API while the spectator remains
outside. No client ammo injection or favorable turret seed is used. It covers M1A2, T-90, T84,
an empty T-90, Classic effects, fully occupied debris slots and a controlled presentation reset.
The reset clears server damage and both wreck countdowns in the dev fixture; it does not add gameplay
wreck revival. Capacity tests
hold tiny off-camera fragments airborne so resting fragments cannot simply be evicted to free a slot.

Screenshots are original native captures, not generated concepts or actual weapon-impact captures.
The 60 FPS target on an RTX 3060 8 GB / Ryzen 7 7800X3D at 1080p still needs target-hardware measurement.

Validation: 571 unit tests and all 297 required local GameTests pass. Seven native scenarios on each
of Vulkan and OpenGL pass, including observer ammo delivery, both carousel models, the M1A2 panels,
empty/Classic exclusions, eight occupied fragment slots and presentation reset. Vulkan's five normal
cases and two isolated capacity cases use the same production source; OpenGL runs all seven together.
[Metrics and hashes](performance/m8-realism/tank-cookoff-parts.json) verify eleven unedited Vulkan PNGs.
Local optional dependencies were excluded for development; normal/real-NTM CI covers the full builds.
