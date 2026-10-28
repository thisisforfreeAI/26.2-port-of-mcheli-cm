# Wet-turn wingtip vapor

Supported aircraft gain short pale vapor strands from their wing tips during suitable turns
in wet weather. The effect rises and fades smoothly with a cosmetic turn-load proxy based on
flight speed and changes in yaw/pitch. Rain intensity supplies a lightweight humidity proxy;
this does not simulate atmospheric moisture or change aircraft forces.

Straight, slow, grounded, submerged or wrecked aircraft do not emit it. Missing wings stop
their own emitter while the surviving wing can continue. Folded/swept wing poses are skipped
when the original tip anchor would be inaccurate. The supported tips come from actual body
vertices and follow yaw, pitch and roll. Remote aircraft can use observed motion when their
reported velocity is zero.

The effect uses Enhanced quality and Polished style. **Aircraft condensation** can be disabled
through ESC → MC Heli Effects → Full configuration (`aircraftCondensation`). Disabling it or
switching to Classic stops new vapor; the short-lived strands drain naturally. Existing
flight handling, component damage, original craters and NTM Next integration are unchanged.

## Artwork, lighting and bounds

Vapor reuses the existing 16-frame wisp alpha silhouette. A small condensation material uses
its alpha with normal ambient particle lighting instead of the soot-colored artwork RGB, so
water vapor stays pale in rain. It retains fog, depth testing and translucent blending. This
is one atlas lookup per fragment, without a new texture, scene copy, depth read or light pass.
Each strand remains one narrow tapered quad, aligned with its birth flight direction. Old
strands stay in world space and drift with shared wind.

- Fixed 24-tick life, smooth birth/end fade, bounded growth and a maximum length footprint
  below 2.31 blocks from its center. No collision queries or gameplay entities.
- Nearby emission within 48 blocks (24 on Low), fading over the final 12 blocks. Pressure,
  Low and shared load slow emission and stagger aircraft slots.
- Births share the existing atmosphere allowance of 8 / 16 / 24 per tick and global particle
  limits. Damage/fire/fuel effects are processed first. No additional entity scan is added.
- Actual body-tip lookup reuses the single breakup worker, four pending builds and 32-entry
  cache. Unsupported or over-12,000-vertex body groups skip the optional detail.

Pure tests cover wet turns versus dry/straight/grounded flight, angle wrapping, invalid input,
loading gaps, smooth fade and load reduction. [Native comparisons](M8_WING_VAPOR_GALLERY.md)
show F-35A, F-15E and A-10 held trajectories plus dry/straight flight, a lost wing, settings and
shared load. These are controlled client previews, not combat recordings.

Local software Mesa validation and optional-dependency exclusions do not establish the target-PC
60 FPS / 1080p on RTX 3060 8 GB / Ryzen 7800X3D. No local JAR is distributed.
