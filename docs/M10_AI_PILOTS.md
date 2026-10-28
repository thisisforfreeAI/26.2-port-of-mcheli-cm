# M10d: smarter AI wingmen and NPC pilots

Part of the M10 plan (gameplay). Wingmen and aircraft flying mission orders (the port's NPC
pilots) now watch the terrain, answer missiles and leave a fight when badly hit. The code is in
`wingman/ai` (`PilotAi`, `TerrainAvoidance`, `AiSkill`) and hooks into `WingmanTickHandler`. How they
aim, choose weapons and fly their passes is in [M10_AI_FIRE_CONTROL.md](M10_AI_FIRE_CONTROL.md).

## Difficulty

New server config section `[ai]`:

| `difficulty` | Looks ahead | Clearance | Reacts to a missile | Breaks away | Attack runs |
|---|---|---|---|---|---|
| `EASY` | 2 s | 25 blocks | 1.5 s | no, decoys only | as upstream |
| `NORMAL` (default) | 2 and 4 s | 18 blocks | 0.6 s | 2 s | as upstream |
| `VETERAN` | 2, 4 and 8 s | 12 blocks | 0.2 s | 3 s | 20 to 40 blocks over the ground |

`terrainAvoidance = false` turns the terrain part off and leaves upstream's flight.

## Terrain lookahead

- Each tick the pilot checks the ground (heightmap, leaves left out) under it and every second ahead
  along its horizontal velocity up to its skill's furthest point, each with the columns around it.
  Unloaded columns count as the bottom of the world. Hovering (under 0.05 blocks a tick) only the
  ground under it counts.
- Its move target is raised to the clearance over the highest of that ground. In formation the
  clearance is at most the leader's own height over the ground (at least 3), so wingmen still
  follow a leader flying low.
- Below that height a plane pitches up as far as it may (20 degrees) at full throttle; a helicopter
  throttles up and stops pushing its nose down.
- It applies to wingmen in the air and, on missions, while transiting, on station, striking or
  returning. Taxiing, take-off, landing circuits and VTOL hover keep the heights the mission sets.

## Countermeasures and the break

- The gunner's missile check is now shared (`entity/aircraft/Countermeasures`): a guided weapon
  within 220 blocks homing on the aircraft or a rider.
- The pilot looks every 5 ticks, every tick while a missile is on it. After its reaction time it
  fires every decoy the aircraft has ready (flares, chaff, ECM, APS), then every 1.5 s while the
  missile stays on it.
- From Normal up it then turns to beam the missile (at right angles to it, on the side nearer its
  heading) for the break time, at the height terrain allows, and holds fire meanwhile.

## Damage-aware

- With an engine, the main rotor or the tail rotor disabled (M9 component damage), a wingman stops
  attacking and goes back to formation. It refuses new attack orders until repaired.
- On a mission order, transiting, on station or striking, it flies home (`RTB`, reason `damaged`).

## Tests

- `PilotAiTest` (unit): hovering looks only down; a ridge is seen as far ahead as each skill looks;
  every second between sample points is checked; the safe height; the beam heading; the skills
  order.
- `ai_climbs_before_a_hill` (GameTest): a helicopter wingman at 5 blocks a second, 30 blocks up,
  throttles up and levels its nose for a column 2 seconds ahead; hovering or with the setting off it
  does not; a P-51D at 20 blocks a second pitches up at full throttle.
- `ai_counters_a_missile` (GameTest): no flares before the reaction time, flares and a break after.
- `ai_crippled_wingman_breaks_off` (GameTest): tail rotor out, the attack stops and stays stopped
  until repair; on an order it returns home.
- The upstream formation GameTest runs with terrain avoidance off, as its test floor is lower than
  the clearance.

## Not tested

No client was run, and no wingman flew a real map: the GameTests step single ticks over test
blocks. Whether Veteran's low attack runs look right, and how a break reads to the player, is
unseen. Pilots in seats (gunner NPCs) are unchanged: they already fired decoys.
