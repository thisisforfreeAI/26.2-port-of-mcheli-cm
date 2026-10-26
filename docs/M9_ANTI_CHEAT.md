# M9c: server checks on weapon fire

Part of the M9 plan (multiplayer combat and component damage). This step stops a modified
client from firing faster, from somewhere else, or at targets it should not be able to lock.

## What the server already owned

Cooldown, heat, ammunition, reloads and turret limits all run on the server (`WeaponSet`,
`AircraftWeapons#aimWeapon`); the client's copies are predictions that the server overwrites. A
fire packet cannot make a weapon fire faster than its interval or beyond its turret's arc, so M9
does not add a second fire-rate counter on top.

## New checks

All in `combat.AntiCheat`, applied to real fire packets by `ModNetwork#screenWeaponControl` and to
BVR locks by `ModNetwork#applyBvrLock`.

| Check | Rule | On failure |
| --- | --- | --- |
| Packet rate | Token bucket per player: burst of 4, then 2 fire packets a tick | Shot dropped (flare, chaff, reload and weapon switch in the same packet still apply) |
| Aim | Yaw and pitch finite, pitch within ±90° | Shot dropped |
| Fire origin | Within 3 blocks plus 2 ticks of the vehicle's own travel of where it was at some tick in the shooter's lag-compensation window plus 4 ticks (M9a history) | Shot fires from the vehicle's server position |
| BVR lock | Target alive, within the radar feed's 2000 blocks, not the shooter, its vehicle or anyone aboard | Lock refused |

## Violations

Each failure adds 1 (packet rate) or 2 (the rest) to the player's score, which decays with a
one-minute time constant. Past `antiCheat.threshold` (server config, default 20, 0 = never) the
server acts at most once a minute per player, per `antiCheat.action`:

- `LOG` (default): one warning line in the server log.
- `NOTIFY_OPS`: also a chat message to online operators.
- `KICK`: also disconnects the player.

`/mcheli anticheat` lists online players with violations; `/mcheli anticheat <player>` shows
their score, counts by check and last 10 failures. It follows the other `/mcheli` subcommands'
permissions (operators, or players listed in `commands.permissions`). Scores reset when the
server stops.

## Not covered

- Pilot movement keeps the existing checks (speed clamp on the pilot's velocity, and the step limit
  that makes vanilla snap a vehicle back). A per-tick acceleration limit was in the plan but needs
  per-type thrust numbers to avoid false positives; it is left for later.
- Light weapons (Stinger, Javelin) use their own packet and are unchanged.

## Tests

- `AntiCheatTest` (unit): decay, the bucket (an honest gun firing every tick never runs dry),
  fire origins for lagging gunners and a pilot ahead of the server, aim validity.
- `anti_cheat_screens_fire_packets` (GameTest): an honest shot passes untouched; a 30-block origin
  is replaced; a NaN aim drops the shot but keeps the flare; the fifth packet in a tick is dropped;
  each failure is recorded and a few stay under the threshold.
- `anti_cheat_screens_bvr_locks` (GameTest): no lock on itself, on the shooter, or past 2000 blocks;
  a nearby vehicle is fine. The existing BVR GameTest still locks its target.
