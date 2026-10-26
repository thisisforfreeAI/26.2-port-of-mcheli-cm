# M9b: smoother sync for fast vehicles and missiles

Part of the M9 plan (multiplayer combat and component damage). This step makes fast jets and
guided missiles move evenly on other players' screens.

## Why

Vehicles send their position every 2 ticks and vanilla interpolates each update over 3 ticks,
restarting from wherever the vehicle is. A vehicle at constant speed therefore speeds up and slows
down within every update, and when an update is late (jitter, a busy server tick) it stops, then
jumps. Guided missiles updated every 5 ticks, so their turns showed as corners.

## How

- `combat.WatcherMotion` sends `VehicleMotionPayload` (entity id and velocity, 16 bytes) every
  2 ticks for vehicles faster than 1 block a tick, to the players tracking them except the pilot.
  Sends are spread across ticks by entity id.
- A watching client keeps the last velocity for 6 ticks. While it is fresh:
  - interpolation runs over 2 steps instead of 3, one position update apart, so the vehicle moves
    the same distance each tick;
  - when no update is being interpolated, the vehicle carries on along the velocity, at most 4
    ticks in a row; the next update blends it back over 2 ticks.
- The pilot's own client is unchanged. Destroyed vehicles keep their client-side spin.
- Guided missiles (`aa_missile`, `as_missile`, `at_missile`, `tv_missile`) update every 2 ticks.
  Bullets, rockets, bombs and torpedoes stay at 5: they fly straight or ballistic and their
  velocity carries them between updates.
- Clients still draw other vehicles slightly in the past, with no forward prediction, which is
  what M9a lag compensation rewinds for.

The payload is optional: clients without it keep vanilla interpolation, and servers without it
simply never send it.

## Bandwidth

About 16 bytes plus packet overhead per fast vehicle every 2 ticks per watching player, roughly
0.3 KB/s per jet per player. A guided missile's extra position updates add about 0.5 KB/s per
nearby player while it flies. Slow and parked vehicles send nothing extra.

## Tests

- `WatcherMotionTest` (unit): send cadence and speed threshold, staleness (including the initial
  never-received value), the extrapolation limit, payload validation.
- The client-side smoothing needs two real clients and was not exercised by the GameTests, which
  run on a server without clients.
