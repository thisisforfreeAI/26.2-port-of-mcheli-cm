# M10b: crew roles

Part of the M10 plan (gameplay). Each seat has a job, and the job decides what its rider can do
and what HUD they get. Stock vehicles need no edits: every seat keeps who may fire what.

## Roles

| Role | Default seat | Can do | HUD |
| --- | --- | --- | --- |
| Pilot | Seat 0 | Fly, the pilot's weapons, countermeasures | Flight HUD, component strip |
| Gunner | A `Gunner` seat, or any seat a weapon belongs to | Its weapons, camera and zoom | Its seat HUD |
| Loader | Only when set | Fires nothing. Guns reload in 0.6x the time while a loader seat is manned. Its reload key reloads the emptiest gun | Crew panel, every gun's magazine, reserve and reload bar |
| Commander | Only when set | Marks what it looks at (key `M`, Mark Target) for 10 seconds; may fire its seat's weapons | Crew panel |
| Passenger | Every other seat, entity racks | Fires nothing | Its seat HUD |

- A loader or passenger can't fire even from a seat with a weapon. The server drops the shot.
- A seat counts as manned with a living player or gunner mob in it.
- `LoaderRequired: true` makes guns reload in 1.4x the time while the loader seat is empty. It does
  nothing on a vehicle without a loader seat, so stock reloads never change.
- A mark is the nearest living entity or vehicle on the commander's line of sight within 512
  blocks, not behind a block and not in their own vehicle. Pressing the key at nothing clears it.
  It ends after 200 ticks or when the target dies. Every rider sees it as a red diamond with its range.
- The crew panel (left of the screen) appears on vehicles with a loader or commander seat: the crew
  seats' letters (`P G L C`), green when manned, the rider's own underlined, and the current reload
  factor.

## Files

YAML, per seat and under `AircraftFeatures`:

```yaml
Seats:
  - Position: [0, 1, 0]
  - Position: [0, 1, -1]
    Role: LOADER
  - Position: [0, 1, -2]
    Role: COMMANDER
AircraftFeatures:
  LoaderRequired: true
```

TXT, after the seats (seat 1 is the pilot):

```
SeatRole = 2, LOADER
SeatRole = 3, COMMANDER
LoaderRequired = true
```

## Tests

- `CrewRolesTest` (unit): every stock vehicle's pilot seat is the pilot, every weapon's seat may
  fire, every gunner seat is a gunner and no stock vehicle has a loader or commander; TXT roles and
  `LoaderRequired` survive YAML conversion; which roles fire; the mark payload round-trips.
- `crew_loader_reloads` (GameTest, AH-64 with a loader seat): factors 1.0 empty, 1.4 when required,
  0.6 manned; a reload takes the scaled time; the loader's key reloads the half-empty gun.
- `crew_loader_cannot_fire` (GameTest, Mark V): the crew seat fires as a gunner and not as a loader.
- `crew_commander_marks` (GameTest, AH-64 with a commander seat): the husk in view is marked,
  looking away marks nothing, and the mark ends when the husk is gone.

## Not tested

No client was run: the crew panel, the loader's gun list and the mark diamond are compiled but
unseen, and two players sharing a crew over a real network is untested.
