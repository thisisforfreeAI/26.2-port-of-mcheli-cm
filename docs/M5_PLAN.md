# M5 plan: weapons (0.5.0)

> **Status:** in progress. This file expands the M5 row of [PORTING_PLAN.md](../PORTING_PLAN.md) §6.
> Its decisions (D18 to D23, continuing the numbering of the earlier plans) were taken as the
> recommended defaults so the work could go straight on; each can be revisited in review. Where
> the implementation differs from this plan, the difference is listed in [§10](#10-as-built).

## 1. Goal

Helicopters fight as they do in upstream McHeli CE 1.5.1: every seat fires the weapons its
content gives it, with upstream's ammunition, reloads, heat, recoil and turret limits; every weapon
type's projectile flies, homes and explodes as upstream; flares, chaff and the active protection
system defeat missiles; McHeli's own explosion replaces the vanilla one; the HUD shows the weapon
values, hit markers and lock-on; and the hand-thrown grenades of the throwable content work.

The **AH-64** (`ah-64.yml`: M230 chain gun on the gunner's turret, Hydra rockets, Hellfire
missiles, flares) stays the reference. Stock weapons of the other 33 helicopters cover the other
weapon types.

## 2. Scope

### 2.1 In M5

| Area | What gets ported | Upstream source |
|---|---|---|
| Explosion | McHeli's explosion on the server: upstream's ray march for blocks (its block power, water damping and `Explosion_DestroyBlock`), its entity damage formula and radius (`explosionDamageRadius`), through-wall damage, fire on entities and blocks, `DamageVs…` and per-weapon damage factors, the hit result for hit markers. The client draws upstream's effects from a payload: smoke and flash particles, block dust, flares from large explosions, splashes in water, or vanilla's effects with `DefaultExplosionParticle` | `MCH_Explosion`, `MCH_ExplosionV2`, `PacketParticleEffect` |
| Particles | Upstream's smoke (wind from nearby rotors, growth, whitening), explosion flash (8-frame `flash.png`), splash, block dust and muzzle flash as `SingleQuadParticle`s with a `RegisterParticleProvidersEvent`; vanilla particle names used by content map to vanilla particle types | `particles/*`, `MCH_ParticlesUtil` |
| Weapon core | Weapon instances per definition and type, the weapon parameter block, the creator's interval and heat adjustments, weapon sets with magazine and reserve ammunition, reload, manual reload, heat and cooldown, recoil buffers, barrel spin, mode switching, impact prediction | `MCH_WeaponBase`, `MCH_WeaponParam`, `MCH_WeaponCreator`, `MCH_WeaponSet`, `WeaponTypeRegistry` |
| Weapon types | All 18 types: `machinegun1`, `machinegun2`, `buckshot`, `railgun`, `rocket`, `mkrocket`, `tvmissile`, `aamissile`, `atmissile`, `asmissile`, `torpedo`, `bomb` (with mines), `cas`, `dummy`, `smoke`, `dispenser`, `targetingpod`, `laser` | `MCH_Weapon*` |
| Projectiles | One entity type per upstream projectile: bullet, rocket, AA, AT (top attack), AS, TV missile, bomb (bomblets, mines), torpedo, marker rocket, dispensed item, A-10. Upstream's per-tick flight, gravity in air and water, piercing, delay and time fuses, proximity fuse, airburst, spreaders (`spawnBulletInAir`), splash, bounce, block hits (`BulletBreakableBlocks`), entity hits with damage factors and bullet decay, potion effects, interception, and their own explosions | `MCH_EntityBaseBullet` and subclasses, `MCH_EntityA10` |
| Guidance | Lock-on search (angle, range, target kinds, ground clutter, stealth, lock tones), flare and chaff decoys, laser guidance from the targeting pod, homing per tick with upstream's turning factor and seeker angle | `MCH_WeaponGuidanceSystem`, `MCH_WeaponEntitySeeker`, `MCH_LaserGuidanceSystem`, `MCH_EntityGuidanceSystem` |
| Countermeasures | Flares (dispensing patterns per type, the flare entity), chaff, APS (active protection: shoots down missiles nearby), and their cooldowns and sounds | `flare/MCH_Flare`, `MCH_Chaff`, `MCH_APS`, `MCH_EntityFlare`, `MCH_EntityChaff` |
| Aircraft integration | Weapons created from `WeaponSet` content per seat, current weapon per seat, switching and its sync, group sharing, firing for the pilot and crew seats (gunner turret yaw and pitch limits), the use-weapon status bits, weapon rotation stepping, weapon bays, ammunition save and supply, `InfinityAmmo` and creative ammo, external damage factors (`Damage…ByExternal`), McHeli's explosion for crashes and destruction (replacing D13's vanilla one) | `MCH_EntityAircraft` weapon sections |
| Networking | The control payload gains upstream's weapon fields (switch weapon, use weapon with its options, position and user yaw/pitch, flare type, reload, mode switch, airburst reset); new payloads for weapon sync, hit notification, lock notification, ammunition counts, explosion effects and the TV missile entity | `PacketPlayerControlBase`, `DataPlayerControlAircraft`, `PacketSyncWeapon`, `PacketNotifyHit`, `PacketNotifyLock`, `PacketNotifyAmmoNum`, `PacketIndNotifyAmmoNum`, `PacketParticleEffect`, `PacketNotifyTVMissileEntity` |
| Input | Fire (left mouse), switch weapon (G, middle mouse, mouse wheel with `SwitchWeaponWithMouseWheel`), weapon mode (X), reload (R), flare/chaff/APS (V), airburst distance reset (right mouse); vanilla's attack and use are held back while a weapon seat is ridden | `MCH_AircraftClientTickHandler#handleCombatSystems` |
| Rendering | Projectiles drawn with their bullet models (`models/bullets`, `BulletModel`/`BombletModel`) and textures, tracers and smoke trails, the laser beam, ejected cartridges, flares, chaff, the A-10 and throwables; weapon parts of the helicopter model animated (turret yaw and pitch, recoil, barrel spin, missiles on rails hidden as they are fired) | `MCH_RenderBulletBase` and subclasses, `MCH_RenderLaser`, `MCH_RenderCartridge`, `MCH_RenderFlare`, `MCH_RenderChaff`, `MCH_RenderA10`, `MCH_RenderThrowable`, `MCH_RenderAircraft#renderWeapon` |
| HUD | Weapon variables (`wpn_name`, ammo, reserve, heat, reload, lock, mode, sight, impact distance, airburst), the hit marker, lock-on markers, CCIP, and the TV missile camera with its noise | `MCH_HudItem#updateWeapon*`, `MCH_GuiTargetMarker`, `MCH_RenderCCIP`, `MCH_GuiTvMissile` |
| Throwables | Throwing grenades and smoke grenades (charge by holding use), the throwable entity with its fuse, bounce, smoke and explosion, and dispensers throwing them | `throwable/*` |
| Config | The keys in §6 | `MCH_Config` |

### 2.2 Not in M5

| Area | Milestone |
|---|---|
| Hand-held weapons (`lweapon`: Stinger, Javelin…), GLTD laser designator, gunner mobs (`MCH_EntityGunner`), planes, ships, tanks and vehicles firing (they are not ported yet) | M6 |
| BVR, GPS panel and GPS missiles, mortar radar and RWR overlays, ECM jammer, iron curtain, data link, anti-radiation missiles, ballistic missile arcs | M6 (with the plane and tank systems that use them) |
| HBM:NTM payloads (need that mod), projectile chunk loading (`ProjectileChunkLoad`) | Not planned (HBM) / M7 |

Each goes into KNOWN_ISSUES.md under "Not ported yet". A weapon definition using a missing
system fires as upstream does when that system is absent (for example a GPS missile without a GPS
target flies straight).

## 3. Design

```
weapon/                 server and client weapon logic (no client classes)
  WeaponBase, WeaponParam, WeaponCreator, WeaponSet, WeaponTypes (registry of the 18 types)
  types/                one class per weapon type
  guidance/             WeaponGuidanceSystem, EntitySeeker, LaserGuidanceSystem
  explosion/            McheliExplosion (implements vanilla's Explosion), ExplosionEffects data
  DamageScaling         DamageVs… and …ByExternal lists from the config
entity/weapon/          BaseBulletEntity and the projectile entities, CartridgeEntity (client only)
entity/flare/           FlareEntity, ChaffEntity; aircraft-side Flare, Chaff, Aps in entity/aircraft
entity/throwable/       ThrowableEntity
client/particle/        particle types and providers
client/render/weapon/   projectile, laser, cartridge, flare, chaff and throwable renderers
client/hud/             weapon variables, hit and lock markers, CCIP, TV missile view
```

Weapons live in `AircraftEntity` as `WeaponSet[]` on both sides, as upstream: the client runs the
same sets to predict cooldowns, ammunition and effects (muzzle flash, cartridges, recoil), and the
server fires the projectiles. Projectiles are server entities spawned with
`IEntityWithComplexSpawn` (weapon name, shooter, motion), so clients have their definition from
the first frame.

The explosion is our own `Explosion` implementation rather than `ServerLevel#explode` with a
damage calculator (the PORTING_PLAN §3.1 suggestion): upstream's block ray march, damage formula
and damage radius differ from vanilla's in ways a calculator cannot express (decision D18).

## 4. Networking

| Payload | Direction | Fields | Upstream |
|---|---|---|---|
| `HeliControlPayload` (extended) | C→S | adds `switchWeapon` (byte, -1 none), `useWeapon` (bool) with `option1`, `option2`, `x`, `y`, `z`, `userYaw`, `userPitch`, `flareType` (byte), `airburstReset` | `DataPlayerControlAircraft` |
| `WeaponSyncPayload` | S→C | vehicle id, seat id, weapon id, ammo, reserve | `PacketSyncWeapon` |
| `NotifyHitPayload` | S→C | vehicle id (or none), hit by explosion | `PacketNotifyHit` |
| `NotifyLockPayload` | S→C / C→S | target id | `PacketNotifyLock` |
| `NotifyAmmoPayload` | S→C | vehicle id, per-weapon ammo and reserve | `PacketNotifyAmmoNum`, `PacketIndNotifyAmmoNum` |
| `ExplosionEffectPayload` | S→C | position, size, in water, affected blocks | `PacketParticleEffect` |
| `TvMissilePayload` | S→C | missile id | `PacketNotifyTVMissileEntity` |

## 5. Vulkan

Projectiles, beams and particles use vanilla pipelines only (entity translucent/emissive, the
particle sheets), as the M4 helicopter renderer does, so both backends draw them.

## 6. Config keys

| Key | Spec | Default | Upstream |
|---|---|---|---|
| `weapons.infinityAmmo` | COMMON | false | `InfinityAmmo` |
| `weapons.hurtResistantTime` | COMMON | 0.0 | `HurtResistantTime` |
| `weapons.bulletBreakableBlocks` | COMMON | glass panes, short grass, tall plants, flowers, vines, wheat, sugar cane, lily pads | `BulletBreakableBlocks` |
| `weapons.ignoreBulletHit` | COMMON | empty | `IgnoreBulletHit` |
| `weapons.damageVs*`, `weapons.damage*ByExternal` | COMMON | `1.0`; `3.0, flansmod` and `0.5, flansmod` where upstream writes them | `DamageVs…`, `Damage…ByExternal` |
| `explosions.destroyBlock` | COMMON | true | `Explosion_DestroyBlock` |
| `explosions.flamingBlock` | COMMON | true | `Explosion_FlamingBlock` |
| `effects.defaultExplosionParticle` | CLIENT | false | `DefaultExplosionParticle` |
| `effects.aliveTimeOfCartridge` | CLIENT | 200 | `AliveTimeOfCartridge` |
| `hud.hitMarkColor` | CLIENT | `255, 255, 0, 0` | `HitMarkColor` |
| `controls.switchWeaponWithMouseWheel` | CLIENT | true | `SwitchWeaponWithMouseWheel` |

New key mappings: use weapon (left mouse), switch weapon 1 (middle mouse), switch weapon 2 (G),
weapon mode (X), reload (R), flare (V), airburst reset (right mouse).

## 7. Work order (one commit each, each building and passing tests)

1. **Docs**: this plan.
2. **Config and keys**: §6, damage scaling.
3. **Explosion and particles**, with the effect payload; crash and destruction use it.
4. **Weapon core and types**, guidance.
5. **Projectile entities** and their registration.
6. **Aircraft integration**: weapon sets, control payload fields, sync payloads, ammunition,
   countermeasures.
7. **Client**: input, renderers, weapon part animation, HUD values and markers, CCIP, TV missile
   view.
8. **Throwables**.
9. **GameTests** (§8.3).
10. **Docs**: CHANGELOG 0.5.0, KNOWN_ISSUES, README, PORTING_PLAN §6; version 0.5.0.

## 8. Exit criteria and tests

| # | Exit criterion | Proved by |
|---|---|---|
| E1 | Damage scaling matches upstream for every target kind and config entry | `DamageScalingTest` (§8.1) |
| E2 | Weapon set ammunition, reload, heat and cooldown follow upstream tick for tick | `WeaponSetTest` (§8.1) against an upstream oracle |
| E3 | Explosion damage at distance matches upstream's formula | `ExplosionMathTest` (§8.1) |
| E4 | The new and extended payloads round-trip | `PayloadCodecTest` (§8.1) |
| E5 | Every weapon type fires its projectile, which hits and damages a target | `WeaponGameTests` (§8.3) |
| E6 | Flares decoy heat seekers; ammunition saves and reloads; infinite ammo works | `WeaponGameTests` (§8.3) |
| E7 | The AH-64 fires, the HUD shows the weapon values and the effects draw on OpenGL and Vulkan | Manual checklist (§8.4), screenshots |
| E8 | `./gradlew build` and `runGameTestServer` pass; `runServer` boots and stops; `runClient` loads a world | Command output in the PR |

### 8.1 Pure JUnit

- **`DamageScalingTest`**: upstream's `applyDamageVsEntity`/`applyDamageByExternal` lists with
  the port's ID matching, for each target kind, default and custom entries.
- **`WeaponSetTest`**: an oracle transcribes `MCH_WeaponSet`'s update, use, reload and heat code;
  both run the same scripted fire/idle sequences for several stock weapons and must agree on ammo,
  reserve, cooldown, heat and reload counters every tick.
- **`ExplosionMathTest`**: damage and knockback for upstream's formula at several distances and
  exposures, with and without through-wall factors.
- **`PayloadCodecTest`**: extended with the new payloads.

### 8.2 JUnit with the game loaded

- **`AllWeaponsTest`**: every stock weapon definition creates a weapon of its type, and every
  helicopter's weapon sets resolve.

### 8.3 GameTests

- **`WeaponGameTests`**: per weapon type, a helicopter in an arena fires a stock weapon of that
  type at an armour stand or a zombie, and the target takes damage (or the projectile spawns, for
  `smoke`, `dummy` and `targetingpod`); a heat seeker fired at a target dispensing flares turns to
  the flare; ammunition decreases, reloads, and survives save and load; `infinityAmmo` keeps it
  full; a destroyed helicopter's explosion damages nearby mobs.

### 8.4 Manual checklist (Xvfb with Mesa; screenshots in `docs/screens/m5-*.png`)

| # | Check |
|---|---|
| M1 | AH-64 pilot fires rockets: smoke trails, explosions with McHeli's particles, HUD ammo counts down and reloads |
| M2 | Gunner (seat 1) fires the M230: the turret follows the view within its limits, tracers, cartridges, barrel recoil, hit marker on a mob |
| M3 | Hellfire lock-on: lock tone and marker, the missile homes and hits |
| M4 | Flares (V): flares drop and burn |
| M5 | A grenade thrown by hand bounces and explodes; a smoke grenade smokes |
| M6 | Repeat M1 to M4 on Vulkan (Mesa lavapipe) |

## 9. Decisions

| # | Question | Default taken |
|---|---|---|
| D18 | Explosion: `ServerLevel#explode` with a damage calculator, or our own `Explosion`? | **Our own** implementation of vanilla's `Explosion` interface, transcribing upstream's ray march and damage, so block and entity results match upstream; vanilla hooks (block `onExplosionHit`, NeoForge's detonate event) are still called |
| D19 | `DamageVs…`, `…ByExternal` and `IgnoreBulletHit` matched Java class names by substring | **Entity type IDs** (PORTING_PLAN deliberate difference 5), still by substring, so `flansmod` keeps matching that mod's entities |
| D20 | Upstream's `MCH_WeaponCAS` never set its shooter (`this.shooter = shooter` assigns the field to itself), so the A-10 belongs to no one | **Fixed**: the A-10 and its bombs belong to the shooter |
| D21 | Vanilla attack and use while riding a weapon seat | **Held back**, as upstream's mouse handling did, so firing does not also punch the vehicle |
| D22 | Projectiles are entities with full per-tick logic on the server; the client predicts their flight | **As upstream**: the server is authoritative for hits and explosions; clients move projectiles with the same code between updates |
| D23 | HBM payloads | **Not ported** (that mod does not exist for 26.2); weapons that use them explode normally |

## 10. As built

What changed from this plan while implementing it (filled in as the work goes).
