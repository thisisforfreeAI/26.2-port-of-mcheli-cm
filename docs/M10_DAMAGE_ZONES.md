# M10a: addon damage zones

Part of the M10 plan (gameplay). An addon vehicle can place its own component zones, so a custom
engine or fuel tank is hit where the model draws it. Vehicles that list none keep the M9d derived
zones exactly.

## YAML

Under `AircraftFeatures`:

```yaml
AircraftFeatures:
  DamageZones:
    - Kind: ENGINE
      Center: [0, 1.2, -2.5]
      HalfSize: [0.6, 0.5, 0.8]
    - Kind: FUEL_TANK
      Center: [0, 0.8, 0]
      HalfSize: [0.8, 0.4, 1.2]
      Share: 0.9
      Health: 0.5
    - Kind: MAIN_ROTOR
      Part: blade0
```

| Key | Meaning | Default |
| --- | --- | --- |
| `Kind` | `ENGINE`, `MAIN_ROTOR`, `TAIL_ROTOR`, `FUEL_TANK`, or on planes `WING` and `AIRFRAME_TAIL` (M8 live airframe breakup) | required |
| `Center`, `HalfSize` | The box, in body coordinates (the frame of the M8 impact payload). Half sizes in (0, 64] | required unless `Part` |
| `Part` | A rotor's model group (`blade0`, `blade1`, ...). The zone is a 1-block box around that rotor's hub | none |
| `Share` | Part of a hit's hull damage the component takes, 0 to 10 | 1.0 engine, 1.2 rotors, 0.8 fuel tank |
| `Health` | The component's whole health as a share of max HP, 0.01 to 10 | 0.35 |

## TXT

```
AddDamageZone = ENGINE, 0, 1.2, -2.5, 0.6, 0.5, 0.8
AddDamageZone = FUEL_TANK, 0, 0.8, 0, 0.8, 0.4, 1.2, 0.9, 0.5
AddDamageZone = MAIN_ROTOR, blade0
```

That is `kind, x, y, z, hx, hy, hz[, share[, health]]`, or `kind, part[, share[, health]]` for a
rotor. Converting a TXT vehicle to YAML keeps its zones.

## Rules

- A listed zone set replaces the derived one completely, in file order. A vehicle that lists only
  an engine has no rotor or fuel tank components, so list every system that should take damage.
- At most 8 zones (the packed sync holds 8). Later entries are dropped.
- Zones are numbered per kind in file order: the first `ENGINE` is engine 0 on the HUD strip.
- A bad entry (unknown kind, an engine given only a `Part`, an empty box, a share or health out of
  range) is logged and skipped. A `Part` naming no rotor of the vehicle is skipped when the zones are
  built.
- A component with its own `Health` repairs by its own size: a repair of N HP restores N HP of
  component health, the most damaged first, spilling over to the next.
- Flight effects (M9e) are unchanged and read the components by kind, so a listed engine limits
  thrust exactly like a derived one.

## For damage visuals

Nothing changes for listeners: `VehicleComponents.of(vehicle)` returns the placed zones, with
`partName` set for rotors given by part. M10a does not touch the M8 decal code or the impact payload.

## Tests

- `AddonDamageZonesTest` (unit): TXT zones survive YAML conversion; placed zones replace derived
  ones, count per kind and take a rotor's hub (a plane's list replaces its derived wing and tail sections too: the first `WING` is the left wing, the second the right); a missing part, bad kinds, empty boxes and bad shares
  are skipped; the 8-slot cap; damage and repair use each zone's own share and health, and match M9
  for default zones.
- `component_damage_addon_zones` (GameTest, stock AH-64 given a zone list): a round at the derived
  engine spot damages no component, a round at the placed nose engine damages it by its own share
  and health, the rotor by part sits on its hub, and a full repair restores it.
