# M9a: lag compensation

Part of the M9 plan (multiplayer combat and component damage). This step makes a player's
shots hit vehicles where that player saw them.

## Why

The pilot's client moves its own vehicle and the server tests every projectile against where it
has each target now. A shooter sees other vehicles about one round trip plus the client's
interpolation in the past. At 150 ms ping that is about 250 ms, so a jet at 3.5 blocks a tick
(about 250 km/h) is drawn some 17 blocks behind where the server tests it, and on-screen hits miss.

## How

- `combat.PoseHistory` keeps each vehicle's last 21 server ticks of position (one second).
  `combat.LagCompensation` records it after every vehicle tick on the server.
- When a player fires, the projectile stores a rewind of `round(min(latency + 100 ms, cap) / 50)`
  ticks. Latency is the connection's smoothed round trip. A player with no latency (the integrated
  server's host, test players) and every NPC shooter get no rewind.
- Each tick's hit test moves a candidate vehicle's main box and part boxes by the displacement from
  its current position to its position `rewind` ticks ago. Riders move with their vehicle.
  The proximity fuse does the same for the target's position.
- Gun rounds (`BulletEntity`) keep the rewind for their whole flight. Other projectiles (missiles,
  rockets, bombs, torpedoes) count it down by one each tick of flight, so after the rewind window
  they test where targets are now and a late dodge still works.
- Blocks, mobs and players on foot are never rewound. Explosions and splash damage still happen at
  the server's current positions (an entity hit already explodes at the target's current origin).

## Settings

`projectiles.lagCompensationMaxMs` in the server config, default 250, range 0 to 500. 0 turns lag
compensation off. Higher values favour shooters on slow connections; lower values favour their
targets, who can be hit up to that long after reaching cover.

## Limits

- Boxes are translated, not rotated back. Part boxes are already axis-aligned boxes around rotated
  ones, and over 250 ms translation is most of the movement of a fast vehicle.
- The M8 cosmetic impact marks recover their contact point against the target's current box, so a
  rewound hit on a fast target may leave no mark. Gameplay damage is unaffected.

## Tests

- `PoseHistoryTest` (unit): recording, interpolation, clamping, displacement, rewind arithmetic.
- `lag_compensation_rewinds_vehicle_hits` (GameTest): a shot through where an AH-64 was 5 ticks
  ago misses with no rewind and with a 2-tick rewind, and hits with a 5-tick rewind.
- `lag_compensation_missiles_stop_rewinding` (GameTest): rockets count the rewind down; gun rounds
  keep it.
