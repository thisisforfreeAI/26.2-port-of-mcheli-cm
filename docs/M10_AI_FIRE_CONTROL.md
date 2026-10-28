# M10d+: AI fire control and flight

A follow-up to [M10_AI_PILOTS.md](M10_AI_PILOTS.md). AI pilots (wingmen and aircraft on mission
orders) struggled to hit anything, and planes flown by them drifted about. Both came from upstream's
wingman code. The code is in `wingman/ai/FireControl` (plain math, unit-tested) and
`WingmanTickHandler`.

## Why they missed

- Every shot went along the aircraft's nose (the wingman fires as itself, and the weapon set takes
  its direction from the aircraft), but a gun fired whenever the target was within 20 degrees of
  the heading, whatever the pitch. Most rounds went metres to tens of metres wide.
- The nose was pointed at where the target was, not where it would be: at 100 blocks a gun round
  takes about 13 ticks, so a fighter crossing at 40 blocks a second was missed by 26 blocks.
- Gravity was ignored, so dropping rounds fell short.
- Bombs were released anywhere within 80 blocks horizontally, wherever they would land.
- With no weapon set ("any"), the first weapon was fired at any heading: missiles off the tail,
  air-to-air missiles at zombies.
- Upstream's orbit point turned 2 degrees a tick whatever the plane did, faster than a plane flies a
  wide circle, and a plane steered at its target anyway, so orbit attacks and gunship runs never
  orbited.

## What the pilot does now

**Weapon choice.** Among the weapons its order allows (or the type it was told to use, or any), the
pilot takes the one that suits the target and keeps it for 2 seconds:

| Target | Best | Then |
|---|---|---|
| In the air (over 4 blocks up) | air-to-air missile | gun, rockets; never bombs or ground missiles |
| A vehicle on the ground | anti-tank / air-to-surface / TV missile | rockets, bombs, gun |
| Anything else on the ground | gun | rockets, bombs, missiles |

Marker rockets, smoke, dispensers, targeting pods and dummies are never fired at a target. If no
allowed weapon suits the target, the pilot holds fire rather than waste it. Orders now use every
weapon they list (upstream used only the first non-gun one).

**Guns and rockets.** The fire control flies the round as `BaseBulletEntity` does (speed capped at
3.9 a tick times the step factor of fast rounds, gravity, horizontal drag, the guns' half-tick
muzzle offset) against the target moving with its tracked velocity, and corrects the aim point by
what it missed by until the miss is under 0.1 blocks. The pilot points its nose at that lead point,
and fires only when the shot it can lay (its mount: a fixed gun a couple of degrees off the nose, a
turret within its limits about its default yaw) is within the angle the target fills (half its
size plus part of an explosive round's blast). Shots are now fired along that solution, not along
the nose.

**Missiles** are launched only at a target within 45 degrees of the mount and at least 25 blocks
away; guided, they home from there.

**Bombs.** The fire control drops the bomb in its head with the plane's velocity (gravity, the
bomb's 0.999 a tick slowing) to the target's height, and releases it when the target is within the
blast radius of where it would land, or just passed (late, not never). The bomber runs in over the
target along the line from it, from 100 blocks up (upstream 200, which gave a moving target twice
as long to stray).

**Passes.** A plane with a gun or rockets dives on a ground target up to 35 degrees, pulls off when
closer than its turn needs (25 blocks plus 10 per block a tick of speed) or low in a dive, extends
straight out at its attack height for at least 1.5 seconds and 130 blocks, and turns in for another
pass. Against an aircraft it leads its pursuit and only breaks past when about to collide. A plane
on an Orbit Atk order circles at the order's radius with its guns' side towards the target.
Helicopters keep upstream's approach and orbit, with the new aim.

## Skill

`ai.difficulty` now also sets the aim:

| | Leads by | Turning target | Lays a fixed gun | Fires within |
|---|---|---|---|---|
| `EASY` | 60% of the target's speed | no | 1 degree | 1.8 times the target's size |
| `NORMAL` | 100% | no | 2 degrees | 1.3 times |
| `VETERAN` | 100% | leads its turn | 3 degrees | 1 times |

## Floating

Planes flown by the AI yawed flat (no bank except on mission cruise), kept sliding the way they
had been going after a turn, were pushed sideways towards their formation slot, and with a stopped
(landed or hovering) leader cut their throttle to nothing and sank, turning on the spot. Now:

- Every AI plane in the air banks into its turns (7 degrees per degree a tick, at most 50), in
  formation blending to the leader's roll as it closes on its slot.
- After it moves, 35% of its sideways drift is turned along its nose each tick, as a wing would.
- The slot catch-up push moves a plane only forwards, as far as the slot lies ahead of it.
- A plane in the air whose leader has stopped circles it 60 blocks out and 20 blocks up at 70%
  throttle; on the ground it idles as before.

## Tests

- `FireControlTest` (unit): roles and the weapon table; the speed cap and step factor; a still
  target hit straight on; a crossing target led and hit, where aiming straight at it misses by the
  whole lead; a dropping round aimed high; a dragged rocket at a diving target; a turning target led
  along its turn; a bomb's release point, fall time and throw, and its lead on a moving target; mount
  arcs; Minecraft's angle conventions.
- `PilotAiTest`: better skills lead as fully, lay further and fire tighter.
- GameTests in real ticks (`AiFireControlGameTests`), with the chunks round them forced:
  - `ai_guns_hit_a_drone`: a P-51D wingman 70 blocks behind a BQM-74E flying its autopilot hits it.
  - `ai_strafes_a_ground_target`: a P-51D 40 blocks up hits a husk 110 blocks ahead with its guns
    and never comes within 4 blocks of the ground.
  - `ai_bombs_land_on_target`: an F-4's Mk 82 lands within 6 blocks of a husk.
  - `ai_plane_circles_a_stopped_leader`: a P-51D whose leader sits on the ground keeps over 0.4
    blocks a tick, banks, slips under 8 degrees on average, stays over 10 blocks up and near.
  - `ai_chooses_the_weapon_for_the_target`: an A-10 takes its Sidewinder to a drone, its GAU-8 to a
    husk, its Maverick to a parked aircraft, rockets when told, and nothing when told to use
    Sidewinders on a husk.

Results of the last run (all 292 GameTests passed): the P-51D hit the drone 73 blocks away 15 ticks
in, with its fifth round; it hit the husk on its first strafing pass (12 rounds), lowest 36 blocks over
the ground; the F-4's first bomb landed 4 blocks from the husk 158 ticks in; the circling P-51D slipped
2 degrees on average (3 at most), banked up to 13, held 1 block a tick and stayed 15 blocks or more up.

## Not tested

No client was run; the GameTests fly real aircraft over the GameTest floor, not a real map.
Ground vehicles (tanks) keep upstream's firing checks. Gunner NPCs in seats are unchanged.
