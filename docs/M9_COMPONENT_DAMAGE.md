# M9d: component damage

Part of the M9 plan (multiplayer combat and component damage). Engines, rotors and fuel tanks get
their own health, so later work can make a damaged vehicle limp home and damage visuals can show
which system was hit. This step adds the state; flight effects follow in M9e.

## Components

Each vehicle type has up to 8 component zones, boxes in its unanimated body coordinates (the frame
of `VehicleImpactLook#body` and the M8 impact payload). Stock content names no component
positions, so `damage.DamageZones` derives them:

| Component | Zone | Vehicles |
| --- | --- | --- |
| Engine | An `ENGINE`-typed bounding box; else one per engine outlet, 1.5 outlet sizes forward of it; else one per propeller (a plane rotor turning about a horizontal axis); else the rear third of the hull. At most 4 | All |
| Main rotor | 1-block box around each rotor hub turning about a mostly vertical axis | Helicopters, VTOL rotors |
| Tail rotor | 1-block box around each helicopter rotor turning about a horizontal axis | Helicopters |
| Fuel tank | Middle third of the hull | Vehicles that burn fuel |
| Left/right wing | Authored side boxes, else outer hull estimates | Planes with three spare component slots |
| Rear airframe | Rearmost authored box, else rear hull estimate | Same planes |

The AH-64, for example, gets two engines (from its two outlets), its main rotor (`blade0`), its tail
rotor (`blade1`) and a fuel tank.

## Damage

Hull damage is unchanged; component damage comes on top.

- A direct projectile hit damages the zone its flight enters first, within 3 blocks of the hit.
  Rounds rewound by M9a lag compensation test zones where the vehicle was for that shooter.
- An explosion damages every zone within 6 blocks of it, falling off linearly with distance.
- Fire, lava, crashes and melee damage no component.
- A component holds 35% of the vehicle's max HP. A hit takes `damage × share` from it, with shares
  of 1.0 for engines, 1.2 for rotors and 0.8 for the fuel tank.
- States: OK above 50%, DAMAGED from 50% down to the last point, DISABLED at 0.

## Repair, save, sync

- Every repair (the wrench, auto-repair, repair stations) restores components by the HP it
  restores, as a share of a component's health, the most damaged first. A full repair restores all.
- Component health is saved with the vehicle (`AcComponents`) and, when the item keeps hull damage
  (`itemDamage`), with its item (`aircraft_components`), so picking a vehicle up repairs nothing.
- It syncs to clients as one packed long (`SynchedEntityData`, 8 bits per component): bandwidth only
  when it changes.

## API for damage visuals and addons

Package `io.github.mcheliceport.damage`, safe on both sides, read-only:

```java
List<ComponentView> parts = VehicleComponents.of(vehicle);
ComponentView engine = VehicleComponents.first(vehicle, ComponentKind.ENGINE);
VehicleComponents.addListener((vehicle, before, after) -> { ... });
```

`ComponentView` carries:

- `kind`: ENGINE, MAIN_ROTOR, TAIL_ROTOR, FUEL_TANK, WING or AIRFRAME_TAIL.
- `index`: its number among components of that kind.
- `state` and `health` (0 to 1).
- `center` and `halfSize`, in body coordinates.
- `partName`: the model group for rotors, empty otherwise.
- `lastChangeTick`: when its health last changed on this side.

The list order is fixed per vehicle type. Listeners run on the side where the change arrives
(on clients when the sync arrives), including repairs, so a visual can add smoke to a dead engine,
grow scorch on a hit tank, and fade on repair. M9 does not change the M8 decal code or the impact
payload.

## Flight effects (M9e)

`damage.ComponentEffects` turns component states into limits. Everything reads the synced health,
so the pilot's client, which flies the vehicle, and the server agree.

| Component | Damaged | Disabled |
|---|---|---|
| Plane engine | 60% of its share of thrust | 0; the throttle cap is the mean over engines |
| Helicopter engine | 70% of its share | 0; no engine left means autorotation |
| Ground or ship engine | throttle capped at 70% | 70% while another runs, 25% with none |
| Main rotor | throttle capped at 75% | autorotation |
| Tail rotor | 0.5°/tick yaw at full throttle | 2°/tick |
| Fuel tank | leaks 1% every 100 ticks | 1% every 20 ticks |

- The cap is applied in `AircraftEntity.setCurrentThrottle`, so every flight model sees it
  without changes of its own. It is cached per packed health value.
- Autorotation: after the helicopter physics, an unpowered helicopter's sink is clamped to
  0.4 blocks a tick, so it can be flared onto the ground. It still steers.
- Tail drift scales with throttle (the main rotor's torque) and fades linearly to nothing at 60%
  of the type's top speed, so a helicopter with a dead tail rotor flies home fast and lands with
  little power. The yaw is sent like a pilot's turn.
- Leaks run on the server and never set the vehicle on fire.
- The HUD strip (`client.hud.ComponentStatusLayer`) appears only while some component is hurt:
  `E` (numbered when there are several), `R` main rotor, `T` tail rotor, `F` fuel tank, in green,
  amber or red.

## Not yet

- A `DamageZones:` list in vehicle YAML to place zones by hand for addon vehicles. Until then an
  addon can mark engine boxes with `Type: ENGINE`.

## Tests

- `ComponentDamageTest` (unit): packing, damage and worst-first repair, states, ray entry and
  zone picking, the AH-64's zones, and every stock vehicle having an engine within 8 slots (every
  stock helicopter also has a main rotor).
- `component_damage_hits_repair_save` (GameTest, stock AH-64): a round entering one engine damages
  only that engine and the hull; a blast at the tail damages the tail rotor; health saves, loads,
  heals on a partial repair and resets on a full one.
- `ComponentEffectsTest` (unit): throttle caps per frame, tail drift against throttle and speed,
  leak rates.
- `component_effects_twin_engine_plane` (GameTest): a stock twin-engine plane gets half throttle
  with one engine out, none with both, and full again after a repair.
- `component_effects_heli_autorotates` (GameTest, AH-64): engines out, it has no power and sinks
  at most 0.4 blocks a tick from 60 blocks up; an intact unpowered one falls faster.
- `component_effects_tail_rotor_and_leak` (GameTest, AH-64): a dead tail rotor keeps engine power,
  spins the helicopter at 2°/tick in a hover and not at all at speed; a holed tank leaks 1% a
  second without exploding.


## M8 live structural presentation

`WING` (0/1) and `AIRFRAME_TAIL` append after existing plane components only when all three
fit the eight-slot budget. They share the existing save/item/sync/repair path, with 1.0 damage
share; the original engine/rotor/fuel slot order remains valid. Projectile entry ties prefer the
smaller zone, allowing precise wing/tail zones to win over equally early broad fallback engines.
The M8 renderer removes disabled sections while the plane is alive, retains them through later
wreck breakup and restores them on repair. The HUD labels these WL/WR/T. These new component
kinds affect presentation; they do not alter lift, steering or thrust. See
[M8 live airframes](M8_LIVE_AIRFRAME.md) for geometry, debris and resource limits.
