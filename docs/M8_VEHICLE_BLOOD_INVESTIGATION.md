# Vehicle-kill blood: compatibility investigation

Requested follow-up after the destruction sequences. This document records the integration
boundary. The subsequent [vehicle-kill blood implementation](M8_VEHICLE_BLOOD.md) now applies
these exclusions without modifying NTM gore.

## Findings

MC Heli can identify its own weapon projectiles through
`entity/weapon/BaseBulletEntity.shootingAircraft`. Direct hits call `hurtServer` in
`onImpactEntity`; conventional projectile explosions pass the projectile into
`weapon/explosion/McheliExplosion`. Tank ramming calls `hurtServer` directly from
`entity/tank/TankCollisions.ram` and uses generic/mob-attack damage sources. A global check
for an explosion tag or a player sitting in a vehicle would therefore misattribute kills.

The inspected NTM Next reference (revision `68ae79fd0aefc5811159aa39b1448bf69904541a`)
calls `ConfettiUtil.decideConfetti` from Sedna `BulletConfig` hit handlers. That helper can
produce giblets, skeleton effects, ashes and sounds depending on the source. NTM VNT/nuclear
explosions and some NTM mobs also invoke gore helpers. These are separate from the MC Heli
rendering pipeline. Do not call, replace, cancel or mix into these helpers.

## Recommended implementation

- Add a server-side cosmetic kill notification at explicit MC Heli damage call sites. Require
  a living mob that was alive before damage and is dead afterward; deduplicate by victim UUID.
  Exclude players, cancelled/nonlethal hits and later unrelated deaths from old vehicle damage.
- For direct hits/explosions, require `shootingAircraft instanceof AircraftEntity` and
  conventional `WeaponInfo`: both `useHBM == false` and `payloadNTM == NONE`. This excludes
  NTM payloads and portable weapons. A player firing an NTM gun while riding a vehicle remains
  an NTM gun kill and never enters this path.
- For ramming, notify only from the tank's own collision damage scope. Do not reinterpret
  every generic or mob-attack death as a vehicle kill.
- Initially opt flesh-bearing mobs in through an entity-type tag. Keep skeletons, golems,
  machinery and NTM (`hbm`) mobs out; some NTM mobs already generate death gore themselves.
  Add-on authors can opt supported entities in without a hard NTM dependency.
- Send one bounded cosmetic packet to nearby tracking players. Render restrained pixel blood
  and optional short-lived ground marks behind a separate client toggle. Reuse shared effect
  admission; use no body replacement, entity removal, damage adjustment or NTM particle assets.

## Acceptance checks

Conventional vehicle gun, missile and ramming kills each emit once; nonlethal hits do not.
NTM gun kills, NTM payload kills and NTM guns fired from a vehicle emit no MC Heli blood.
Run with NTM Next installed and absent, and verify that NTM's own gore still behaves normally.
Check victim filtering, cancelled death/totem cases, multiplayer attribution and burst budgets.
