# Vehicle-kill blood

Conventional MC Heli vehicle projectiles, projectile blasts and tank ramming now produce a
brief spray of dark red pixel droplets and a small temporary ground splash when they kill
a supported mob. This is cosmetic: damage, loot, mob bodies and explosion craters are unchanged.
There are no added gibs, corpse replacements, sounds, dynamic lights or glowing blood mist.

## Controls and supported mobs

ESC → **MC Heli Effects → Vehicle-kill blood** toggles the feature immediately. The equivalent
client setting is `effects.vehicleBlood` (default true); Enhanced effects must also be enabled.
Disabling it clears ground splashes, pending samples and client deduplication; airborne drops
remove themselves on their next tick. Other explosion and damage settings are independent.

The `mcheli_ce_port:vehicle_blood_mobs` entity-type tag opts in common flesh-bearing mobs:
zombies, villagers, raiders, piglins and selected animals. Players, skeletons, golems, slimes
and other unlisted types produce no blood. Data packs can extend or replace the tag for add-ons.
An additional namespace guard always excludes `hbm` and `ntm` entities, even if tagged.

## NTM Next boundary

This implements the attribution boundary in [the investigation](M8_VEHICLE_BLOOD_INVESTIGATION.md).
There are **no global damage/death listeners, NTM imports, mixins, gore calls or NTM asset copies**.
NTM's Sedna guns and gore helpers remain owned by their integration.

- A projectile must carry an actual `AircraftEntity` in `shootingAircraft`.
- `WeaponInfo.useHBM`, any non-`NONE` `payloadNTM`, `effectOnly`, and a `requiredMod` of `hbm` or
  `ntm` all exclude the shot. This includes NTM weapons mounted on MC Heli vehicles.
- Portable MC Heli weapons are excluded. A rider firing an NTM gun does not enter these scopes.
- Ramming is reported only from the existing tank collision damage call.
- Laser beams, later burning deaths and environmental deaths do not inherit an earlier vehicle hit.

The original `hurtServer` result is inspected immediately after each scoped hit. The mob must
have been alive beforehand, now have zero health and vanilla's accepted `DYING` pose, and retain
the same damage source. A cancelled `LivingDeathEvent` that leaves health at zero still fails
this check; totem saves and nonlethal/cancelled hits fail too. Custom mobs that do not use the
standard dying pose are conservatively omitted.

## Bounds and terrain behavior

- Server: at most eight optional S2C notifications per level tick, only to players watching the
  victim and within 64 blocks. Clients without the optional channel receive nothing. Recent UUID
  storage is capped at 1,024 entries and expires after 200 ticks; values retain no world or mob.
- Client: at most four accepted kills per tick and a 48-block range. Each requests 10 droplets,
  or 14 for a blast, reduced by adaptive pressure. Every drop pays existing shared atmosphere
  and particle admission; Balanced allows at most 16 atmosphere births per tick across features.
- Drops last 14–23 ticks, use normal world lighting and collide with nearby blocks. They use
  the project's existing debris sprite, tinted red, without a new shader or render pass.
- Each accepted kill queues at most one ground splash, sampled two ticks later so normal
  explosion block updates can arrive. Four downward rays per tick; 32 pending jobs; 64 marks.
- A mark clips to an actual upward collision surface on one block, never water or unloaded
  terrain. It fades over its final four seconds and expires after 300 ticks (15 seconds).
  Changed/unloaded supporting blocks suppress rendering immediately; stored entries are cleaned
  in batches. Disconnects, world changes and the toggle clear state.
- Ground splashes reuse the project's neutral wet-patch alpha mask with a dark red tint, ordinary
  terrain light and one batched translucent submission. Nothing is written into the world/save.

The hardware target remains 60 FPS at 1080p on an RTX 3060 8 GB and Ryzen 7 7800X3D. Software
Vulkan previews establish rendering and behavior, not performance on that hardware.

## Verification

`VehicleBloodRulesTest` checks NTM definitions, deduplication, burst limits, bounded storage and
timeline resets. `PayloadCodecTest` round-trips the new payload and rejects invalid geometry.
Six required `VehicleBloodGameTests` exercise real direct damage, conventional blasts, duplicate
observation, portable/NTM exclusions, skeletons, cancelled death, totem protection, actual tank
ramming and an unrelated later player kill while riding. The normal CI suite also runs with NTM
Next installed; it does not replace NTM's gun/gore tests or claim a visual audit of every Sedna gun.

The non-shipping `VehicleBloodPreviewProbe`, enabled with `-Dmcheli.probeBlood=true` and
`scripts/visual-probe.init.gradle`, holds example vehicles while real server damage calls produce
the normal optional packet and production client effect. Its screenshots are controlled previews,
not combat captures. It checks kill attribution, actual droplets/marks, ray bounds and toggle cleanup.
