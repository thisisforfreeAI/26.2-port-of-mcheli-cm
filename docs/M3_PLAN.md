# M3 plan: placing and flying helicopters (0.3.0)

> **Status:** approved, not started. Decisions D10 to D13 were approved as recommended on
> 2026-10-14. M2 ([PR #2](https://github.com/thisisforfreeAI/26.2-port-of-mcheli-cm/pull/2)) is
> merged; the M3 branch is cut from `main`. NeoForge stays pinned at 26.2.0.88.
> This file expands the M3 row of [PORTING_PLAN.md](../PORTING_PLAN.md) §6. Its decisions are in
> [§9](#9-decisions).

## 1. Goal

A player can place any stock helicopter from its item, climb in, take off, hover, fly, land, get
out and pick it up again, alone and in multiplayer, on a dedicated server and on LAN. Flight
behaves like upstream McHeli CE 1.5.1, which the tests check formula by formula.

The **AH-64** (`ah-64.yml`: 2 seats, a gunner seat, 3 extra bounding boxes, 1,200 fuel, 180 HP)
is the reference helicopter for tests and screenshots. All 34 stock helicopters share one entity
class, as upstream, so all of them become placeable and flyable at once (decision D10).

## 2. Scope

### 2.1 In M3

| Area | What gets ported | Upstream source |
|---|---|---|
| Entities | `AircraftEntity` (the flight-related part of `MCH_EntityAircraft`), `HeliEntity`, `SeatEntity`; the `helicopter` and `seat` placeholder entity types become real | `MCH_EntityAircraft`, `MCH_EntityHeli`, `MCH_EntitySeat` |
| Synced data | Type name, damage taken, status bits, fuel, fuel fluid, texture name, roll, throttle, command, part status, fold-blade state | `entityInit` (13 `DataParameter`s + heli `FOLD_STAT`) |
| Save data | The same fields plus seat layout, through `ValueInput`/`ValueOutput` | `read/writeEntityToNBT` |
| Spawn data | Type name and seat count through `IEntityWithComplexSpawn`, so the client builds the right seats before the first tick | `writeSpawnData`/`readSpawnData` |
| Hit boxes | Content `BoundingBoxes` as NeoForge `PartEntity` children, used for interaction, damage factors and player collision | `MCH_BoundingBox`, `updateExtraBoundingBox` |
| Placement | `AircraftItem#use`: 5-block ray trace, spawn one block above the hit block, yaw snapped to 90°, refuse if the box (shrunk by 0.1) collides, `creativeOnly`, target drones refused ("Please use the UAV station"), `PlaceableOnSpongeOnly` rules, item consumed outside creative | `MCH_ItemAircraft#onItemRightClick`, `spawnAircraft`, `onTileClick` |
| Dispensers | Dispensers place helicopters, as upstream | `MCH_ItemAircraftDispenseBehavior` |
| Item data | Damage (`DataComponents.DAMAGE`), fuel and command (new data components) move between item and entity, gated by `ItemDamage`/`ItemFuel` | `getAcDataFromItem`, `setAcDataToItem` |
| Pick-up and damage | `hurtServer`: punch to pick up (creative, sneak, `DropItemInCreativeMode`, `BreakableOnlyPickaxe`, `PreventingBroken`), armour maths (`armorMaxDamage`, `armorDamageFactor`, `armorMinDamage`, bounding-box factors), lava/fire rules, `invulnerable`, destroyed state, crash damage, burning crew | `attackEntityFrom` and helpers, `explosionByCrash`, `onUpdate_CollisionGroundDamage` |
| Riding | Interact to board seat 0 (pilot), then free seats; canopy opens on the first interact if closed; `canRide`, exclusion seats; seat switching (free-look + GUI / extra key); dismount with upstream's unmount keys and its dismount positions; no fall damage on landing | `processInitialInteract`, `interactFirstSeat`, `switchNext/PrevSeat`, `unmountEntity`, `getUnmountPos` |
| Controls | Key mappings for the flight subset: up/down (W/S), left/right (A/D), hovering (Space), gunner mode (H), unmount (Y, Left Shift to force), free look (Left Ctrl), GUI (I), extra/fold blades (F), landing gear (B). Defaults as upstream; vehicle keys only active while riding (custom `IKeyConflictContext`) | `MCH_ClientHeliTickHandler`, `MCH_AircraftClientTickHandler` |
| Mouse flight | Yaw, pitch and roll from mouse deltas through `CalculatePlayerTurnEvent`; `InvertMouse`, `MouseSensitivity`, `MouseControlStickModeHeli`, `MouseControlFlightSimMode`; the Euler path and the quaternion path (`ExperimentalQuaternionRotation`, **default true upstream**) | `MouseInputHandler`, `setAngles`, `applyQuatAngles` |
| Physics | Throttle (manual, auto throttle-down band 0.48 to 0.52, hovering mode), rotor angle, lift, gravity and water, speed limit and `currentSpeed`, ground friction and on-ground pitch, auto-levelling, server rotation limits, hovering drift, destroyed spin and fall | `onUpdate_Control*`, `onUpdate_Server`, `onUpdate_Client`, `onUpdateAngles` |
| Parts (state only) | Canopy, landing gear, hatch and fold-blade state machines, because riding and control depend on them; drawing them is M4 | `MCH_Parts`, `foldBlades`, `openCanopy` |
| Fuel | Consumption every 20 ticks, `InfinityFuel`, creative pilots fly free, out of fuel means throttle decays; refuelling from fuel cans and fluid containers in the aircraft menu (decision D12) | `updateFuel`, `handleItemFuelRefuel`, `canUseFuel` |
| Aircraft menu | Sneak-interact or the GUI key opens a vanilla container screen with the trunk (`inventorySize`) and the fuel in/out slots, on upstream's GUI texture. Other panels of the aircraft GUI stay M6 | `MCH_AircraftGuiContainer`, `MCH_AircraftInventory` |
| Networking | Payloads for heli controls, attitude, seat control, status request/response and server settings, with CE's compact encodings (§5) | `PacketPlayerControlHeli`, `PacketIndRotation`, `PacketSeatPlayerControl`, `PacketStatusRequest/Response`, `PacketSyncServerSettings` |
| Authority | Decision D2: the pilot's client simulates; the server checks. `vehicles.serverAuthoritativeMovement` switches to upstream's model (§4) | — |
| Camera (minimum) | The pilot's view takes the aircraft's roll (`ViewportEvent.ComputeCameraAngles#setRoll`) and pitch/yaw follow as upstream overrides them; first-person hand hidden while piloting | `MCH_ViewEntityDummy`, `W_Reflection.setCameraRoll` |
| Stand-in renderer | Draws the main box and the content bounding boxes, rotated by yaw/pitch/roll, so flight can be seen and screenshotted before M4 (decision D11) | — |
| Config | The keys in §6 | `MCH_Config`, `MCH_ServerSettings` |

### 2.2 Not in M3

| Area | Milestone |
|---|---|
| OBJ/MQO models, textures, rotor and part animation drawing, HUD, zoom, camera modes, third-person distance, engine and rotor sounds | M4 |
| Weapons and gunner weapon use, flares/chaff/APS/ECM, particles (damage smoke, sand cloud), McHeli's own explosion (M3 uses a vanilla explosion of upstream's power 2 for destruction) | M5 |
| Planes, ships, tanks, vehicles (their items stay inert), UAVs, gunner NPCs and mobs in seats, racks, rappelling, chains, riding an empty minecart (`MountMinecartHeli`), carrying players standing on bounding boxes, teams and IFF, ammo/fuel/repair supply vehicles, the rest of the aircraft GUI | M6 |
| Advancement triggers (`PUT_AIRCRAFT`, `RIDING_VALKYRIES`, refuel), long-distance tracking, `/mcheli` commands other than `dumpyaml` | M7 |

Each of these goes into KNOWN_ISSUES.md under "Not ported yet" with its milestone.

## 3. Design

### 3.1 Entity layout

```
entity/aircraft/
  AircraftEntity        ← Entity + IEntityWithComplexSpawn; owns the collaborators below
  AircraftData          ← EntityDataAccessors and their typed getters/setters
  AircraftAttitude      ← yaw/pitch/roll, Euler and quaternion paths (pure)
  AircraftFuel          ← consumption and refuelling rules (pure + inventory glue)
  AircraftDamage        ← armour maths and pick-up rules (pure + DamageSource glue)
  AircraftSeats         ← seat entities, seat switching, dismount positions
  AircraftParts         ← canopy, gear, hatch state machines
  AircraftHitBox        ← PartEntity for one content bounding box
entity/heli/
  HeliEntity            ← rotor, fold blades, hovering mode, heli control
  HeliFlightModel       ← upstream's heli formulas as side-effect-free functions (pure)
entity/seat/SeatEntity
net/ (payloads)  client/input/ (keys, mouse)  client/render/StandInAircraftRenderer
menu/AircraftMenu  client/gui/AircraftScreen
```

The split follows the method seams of `MCH_EntityAircraft` (PORTING_PLAN.md §5.2, §9). Only
methods M3 needs move; weapon, radar, UAV and supply methods stay unported until their milestone,
so `AircraftEntity` starts at roughly 2,000 of upstream's 7,212 lines.

Every "pure" class takes plain numbers and an injected `RandomSource` (hovering drift uses random
numbers), so the tests can drive it without a world.

### 3.2 Tick order

`AircraftEntity#tick` keeps upstream's `onUpdate` order for the parts M3 ports: controls, server
no-move check, despawn countdown, `super.tick`, parts, fuel, mode cooldown, first-ride hook,
fall-distance reset, rider rotation capture, `onUpdateAircraft` (heli: seats, control, rotor,
auto-level, client/server movement), extra bounding boxes, parts, crash explosion, seat and hit-box
positions, ground collision damage, unmount reserve. Skipped steps (weapons, radar, flares, supply,
repair, UAV) are left as named, empty hooks so later milestones slot in without reordering.

### 3.3 Ground probe

Most heli rules ask "is there ground within N blocks below?" through upstream's
`MCH_Lib.getBlockY`/`getBlockIdY` (auto-levelling, on-ground pitch, strafing roll, destroyed
throttle, fold-blade taxiing). Upstream's probe has two 1.12 assumptions that break on 26.2:

- It treats any `y` outside 0 to 255 as air. A 26.2 overworld runs from -64 to 319, so below
  y 0 a heli never finds ground and above y 255 it never finds blocks.
- It converts positions with `(int) (pos + 0.5)`, which truncates toward zero, so at negative
  coordinates it probes one block off. (Its range check also tests `py + y` even when it probes
  downwards at `py - y`.)

The port's `GroundProbe` keeps upstream's footprint (`size`), length and collidable-only rule but
uses `Mth.floor(pos + 0.5)` and the level's `getMinY()`/`getMaxY()`. This is a deliberate fix of
a 1.12-only limit, not a behaviour change inside 0 to 255 at positive coordinates; it gets a
KNOWN_ISSUES entry and its own tests (`GroundProbeTest`, `FlightGameTest`). The oracle in §8.1
calls the same probe through an interface, so the formula comparison is unaffected.

## 4. Authority (decision D2)

Upstream already lets the pilot's client own **rotation** (`PacketIndRotation`) while the server
owns **position** (`onUpdate_Server`). The port keeps that split in server-authoritative mode and
moves position to the pilot's client in the default mode:

| | Default (`serverAuthoritativeMovement = false`) | Upstream mode (`true`) |
|---|---|---|
| Who runs `HeliFlightModel` for a piloted heli | Pilot's client (`isClientAuthoritative()` true while a player pilots it) | Server |
| What the pilot sends | Vanilla vehicle move packet (position, yaw, pitch) + attitude payload (roll, exact angles) + control switches for state other clients need (throttle, hovering, blades) | Control switches + attitude payload, as upstream |
| Server checks | Vanilla `handleMoveVehicle` limits, plus a per-tick horizontal speed cap of `Speed × AllHeliSpeed × 1.5` and a climb cap from the model's maximum lift; a failed check snaps the heli back and logs once per pilot per minute | Every switch re-validated: sender is the pilot, heli not destroyed, cooldowns |
| Unpiloted helis (falling, hovering mode with nobody in seat 0) | Server | Server |
| Other clients | `InterpolationHandler`, update interval 2 (from M2's entity type) | Same |

The same `HeliFlightModel` code runs on whichever side simulates, so both modes fly identically
for the pilot. A change of mode only needs a world restart.

## 5. Networking

All payloads are `CustomPacketPayload` records with hand-written `StreamCodec`s registered in one
`PayloadRegistrar("1")`, keeping CE's encodings: enum ordinals as bytes, the bit-packed
`PlayerControlSwitches`, entity IDs instead of UUIDs.

| Payload | Direction | Carries |
|---|---|---|
| `HeliControlPayload` | to server | entity ID, the M3 subset of `DataPlayerControlAircraft` (unmount, mode switch, hatch, gear, free look, open GUI, switches byte) and `DataPlayerControlVehicle` (blade fold) |
| `AircraftAttitudePayload` | to server, then to trackers | entity ID, yaw, pitch, roll, roll-reversal flag |
| `SeatControlPayload` | to server | next/previous seat |
| `AircraftStatusRequest` / `AircraftStatusResponse` | both | the status a client asks for on first tick (upstream's `PacketStatusRequest`) |
| `ServerSettingsPayload` | to client on join and on config reload | rotation limit settings |

Weapon fields of upstream's control packet are left out until M5 adds them; the record layout
leaves room for them without renumbering.

## 6. Config keys ported in M3

| Upstream key | Port | Default |
|---|---|---|
| `PlaceableOnSpongeOnly`, `ItemDamage`, `ItemFuel`, `InfinityFuel`, `PreventingBroken`, `DropItemInCreativeMode`, `BreakableOnlyPickaxe`, `AllHeliSpeed`, `AutoThrottleDownHeli`, `ExperimentalQuaternionRotation` | COMMON `vehicles.*` | upstream's (`false`, `true`, `true`, `false`, `false`, `false`, `false`, `1.0`, `true`, `true`) |
| `enableRotationLimit`, `pitchLimitMax`, `pitchLimitMin`, `rollLimit` (`MCH_ServerSettings`) | SERVER `rotationLimits.*`, synced to clients | upstream's (`false`, `10`, `10`, `35`) |
| `InvertMouse`, `MouseSensitivity`, `MouseControlStickModeHeli`, `MouseControlFlightSimMode` | CLIENT `mouse.*` | upstream's (`false`, `10.0`, `false`, `false`) |
| key lines (`KeyUp` … `KeyGearUpDown`) | vanilla Controls screen (`KeyMapping`s in a "McHeli CE (26.2 Port)" category) | upstream's keys |
| — | SERVER `vehicles.serverAuthoritativeMovement` (exists since 0.1.0) | `false`; its "no effect yet" note is removed |

Upstream's COMMON/SERVER placement of each key is checked against `MCH_Config` before it is
added; any key whose side changes gets a KNOWN_ISSUES entry.

## 7. Work order (one commit each, each building and passing tests)

1. **Config**: the keys in §6, server settings sync.
2. **Flight maths**: `HeliFlightModel`, `AircraftAttitude`, `AircraftFuel`, `AircraftDamage` with
   their JUnit tests and the upstream oracle (§8.1).
3. **Entities**: `AircraftEntity`, `HeliEntity`, `SeatEntity`, hit-box parts, synced, save and
   spawn data; placeholders for `helicopter` and `seat` replaced.
4. **Placement and pick-up**: `AircraftItem#use`, dispenser behaviour, item data components,
   `hurtServer`.
5. **Networking**: payloads, codecs, server handlers with validation.
6. **Client**: key mappings, mouse flight, camera roll, control sending, stand-in renderer.
7. **Authority**: client-authoritative path and server checks; server-authoritative mode.
8. **Fuel and aircraft menu**: consumption, refuelling, `AircraftMenu` and screen.
9. **GameTests**: the tests in §8.3, a test structure, and a `./gradlew runGameTestServer` step
   in `.github/workflows/build.yml`.
10. **Docs**: CHANGELOG 0.3.0, KNOWN_ISSUES, README status, PORTING_PLAN §6 (M3 done); version
    0.3.0.

## 8. Exit criteria and tests

M3 is done when every row below is ✅ in the M3 pull request, in the same form as M2's PR: each
criterion names the test that proves it, and in-game checks are listed with what was seen.

| # | Exit criterion | Proved by |
|---|---|---|
| E1 | Flight maths match upstream formula by formula; the ground probe works over 26.2's full height and at negative coordinates | `HeliFlightModelTest`, `AircraftAttitudeTest`, `GroundProbeTest` (§8.1) |
| E2 | Fuel and damage rules match upstream | `AircraftFuelTest`, `AircraftDamageTest` (§8.1) |
| E3 | Every payload round-trips, and the switch byte matches upstream's bit layout | `PayloadCodecTest` (§8.2) |
| E4 | Every one of the 34 stock helicopters can be created from its item, with the content's seat count and hit boxes | `AllHelicoptersSpawnTest` (§8.2) |
| E5 | Placement follows upstream's rules | `PlacementGameTest` (§8.3) |
| E6 | Boarding, seat switching and dismounting follow upstream's rules | `RidingGameTest` (§8.3) |
| E7 | A scripted take-off, hover and landing works on the server, in both authority modes | `FlightGameTest` (§8.3) |
| E8 | Fuel use, refuelling, pick-up and save/load keep the heli's state | `FuelGameTest`, `PickUpGameTest`, `PersistenceGameTest` (§8.3) |
| E9 | Spawn, mount, take off, hover, land in single player | Manual checklist M1 to M8 (§8.4), with screenshots |
| E10 | Remote motion is smooth with two clients | Manual checklist M9, with the smoothness log (§8.4) |
| E11 | 20 idle helicopters cost little on a dedicated server | Manual checklist M10 (§8.4) |
| E12 | `./gradlew build` passes; `runGameTestServer` passes; `runServer` boots and stops cleanly; `runClient` loads a world, save and quit work; no McHeli warnings or errors in any log | Command output in the PR |

### 8.1 Pure JUnit (no game)

- **`HeliFlightModelTest`**: an **upstream oracle** in `src/test` is a line-for-line transcription
  of `MCH_EntityHeli`'s `onUpdate_Control`, `onUpdate_ControlNotHovering`,
  `onUpdate_ControlHovering`, `onUpdate_ControlFoldBladeAndOnGround`, `onUpdate_Server`,
  `onUpdateAngles` and `onUpdate_Client`'s ground friction, with 1.12 types replaced by doubles
  and nothing else changed. The test drives the port's `HeliFlightModel` and the oracle with the
  same scripted inputs and seeded random numbers and requires identical motion, throttle, speed,
  rotor angle and attitude every tick (tolerance 1e-9) for:
  - take-off: 0 to full throttle, climb for 200 ticks;
  - auto throttle-down settling into the 0.48 to 0.52 band;
  - hovering mode on and off, with strafing and forward/back hover moves;
  - forward flight at the speed limit, including `currentSpeed` build-up and decay;
  - on-ground friction and on-ground pitch (`OnGroundPitch` of the AH-64, -3.5);
  - water (`gravityInWater`, floating helis);
  - destroyed: throttle decay, spin and fall;
  - out of fuel: throttle decay at -0.00125 per tick;
  - `AllHeliSpeed` 0.5 and 2.0.
- **`AircraftAttitudeTest`**: mouse input through `setAngles` against the oracle's Euler path and
  quaternion path, including near ±90° pitch, roll wrap at ±180°, content `limitRotation` limits
  and the server rotation limits; `MCH_Lib.eulerFromOrientationQuat` transcription checked
  against hand-computed rotations.
- **`AircraftFuelTest`**: consumption per 20 ticks (`fuelConsumption`, throttle × 1.4 capped at 1,
  fractional carry), `InfinityFuel`, creative pilot, refuel from a fuel can and from a fluid
  container to `MaxFuel` and no further.
- **`GroundProbeTest`**: the probe matches upstream's results for positive coordinates inside
  y 0 to 255, finds ground at negative x/z on the right block, and finds ground below y 0 and
  blocks above y 255 (a stubbed block getter, no world).
- **`AircraftDamageTest`**: armour maths (`armorMaxDamage` cap, factor ≤ 1 before and > 1 after
  `armorMinDamage`), lava multiplier and hit cooldowns, `invulnerable`, the pick-up decision table
  (creative × sneak × `DropItemInCreativeMode`, `BreakableOnlyPickaxe`, `PreventingBroken`,
  occupied heli).

### 8.2 JUnit with the game loaded (as M2's tests)

- **`PayloadCodecTest`**: every payload encodes and decodes to an equal record; the switch byte of
  every single-flag `PlayerControlSwitches` equals upstream's `serialize` output.
- **`AllHelicoptersSpawnTest`**: for each stock helicopter, the item creates a `HeliEntity` with
  the right type name, `MaxHP`, `MaxFuel`, seat count, rotor count and one hit-box part per
  content bounding box; the `helicopter` and `seat` entity types are no longer placeholders, and
  the other placeholder types are unchanged.
- **`AircraftItemDataTest`**: damage, fuel and command components survive item → entity → item,
  and are ignored when `ItemDamage`/`ItemFuel` are off.

### 8.3 GameTests (new in M3)

Registered with `RegisterGameTestsEvent` in the `mcheli_ce_port` namespace, on a flat test
structure with a sponge pad and a water pool. Run by `./gradlew runGameTestServer` locally and in
CI. Players are mock players.

- **`PlacementGameTest`**: using the AH-64 item on a block spawns one heli one block above it with
  yaw snapped to the nearest 90°; nothing spawns when blocks obstruct the box; the item is used up
  in survival and kept in creative; a `creativeOnly` heli is refused in survival;
  `PlaceableOnSpongeOnly` refuses non-sponge blocks; a dispenser places a heli.
- **`RidingGameTest`**: the first interact opens a closed canopy, the next boards seat 0; a second
  player boards seat 1 (AH-64 gunner seat); seat switching moves to the next free seat and skips
  exclusion seats; the unmount action puts the player at upstream's dismount position on solid
  ground; a destroyed heli cannot be boarded; landing does not hurt the pilot.
- **`FlightGameTest`**, run once per authority mode with a server-side scripted pilot: full
  throttle for 100 ticks gains height; hovering mode holds height within ±1 block over 200 ticks;
  throttle down lands it; the same take-off, hover and landing works on a pad at y -50 and at
  negative x/z (§3.3); a landing at normal descent does no damage, and a drop from 30 blocks
  with the heli destroyed explodes on impact.
- **`FuelGameTest`**: a running heli loses fuel at upstream's rate; at 1 fuel it cannot climb; a
  fuel can in the menu's fuel slot refuels it.
- **`PickUpGameTest`**: a survival punch on an empty heli drops its item with damage and fuel; on
  an occupied heli it does not; a creative punch removes it (with or without drop by config and
  sneak).
- **`PersistenceGameTest`**: a heli with fuel, damage, throttle, roll, hovering mode and folded
  blades keeps them through save and chunk reload.

### 8.4 Manual checklist (Xvfb or desktop; screenshots in `docs/screens/m3-*.png`)

| # | Check |
|---|---|
| M1 | Place an AH-64 in survival; the stand-in boxes match its bounding boxes (F3+B agrees) |
| M2 | Board it; the hand is hidden, the view sits at the seat position |
| M3 | Throttle up with W and take off; the climb looks like upstream (side-by-side video against McHeli CE 1.5.1 on 1.12.2, same inputs, is the reference) |
| M4 | Mouse pitch, yaw and roll; the view rolls with the heli; invert and sensitivity settings work |
| M5 | Hovering mode with Space holds position; W/S/A/D move it as upstream |
| M6 | Land, get out with Y; no fall damage |
| M7 | Fly until out of fuel; refuel with a fuel can from the menu (I or sneak-use); fly again |
| M8 | Punch it in survival to get the item back; place it again with its damage and fuel |
| M9 | Dedicated server, two clients: one pilots in circles at full speed for 60 s, the other watches. With `general.debugLog` on, the watcher's client logs the heli's rendered position each frame; no backward step and no frame step larger than twice the average, in both authority modes |
| M10 | Dedicated server: 20 helis, 10 hovering with nobody aboard, 10 parked; `/tick query` shows less than 2 ms per tick added over the empty world |
| M11 | Repeat M1 to M6 with "Prefer Vulkan" on; the stand-in renderer draws the same |
| M12 | Repeat M3 to M6 with a different stock heli that has fold blades or a canopy, and with an addon TXT helicopter |

## 9. Decisions

All four were approved as recommended on 2026-10-14. They are binding for M3, and they continue
PORTING_PLAN.md §10's numbering.

| # | Question | Approved resolution |
|---|---|---|
| D10 | Scope: the AH-64 only, or every helicopter? | **Every stock and addon helicopter**, since they share one entity class; the AH-64 is the test reference. Planes and other types stay M6. |
| D11 | Helicopters are invisible until M4 draws models. Add a throw-away stand-in renderer (bounding boxes) in M3? | **Yes**: needed for M3's manual checks and screenshots; M4 replaces it. |
| D12 | Upstream refuels only through the aircraft menu, and a placed heli starts with 0 fuel, so survival flight needs that menu. Move its trunk and fuel slots forward from M6? | **Yes**, trunk and fuel slots only; the rest of the aircraft GUI stays M6. Without it, survival helis only fly with `InfinityFuel`. |
| D13 | Destruction explosions need McHeli's explosion (M5). Use a vanilla explosion of the same power until then? | **Yes**, listed in KNOWN_ISSUES until M5. |

## 10. Risks

| Risk | Mitigation |
|---|---|
| The oracle transcription carries a typo, so port and oracle agree but both differ from upstream | Two independent transcriptions are compared once; spot values are computed by hand from upstream's source for the take-off case |
| Client-authoritative movement drifts from the server for unpiloted ticks (pilot just left) | Authority switches on the server tick the pilot leaves; the server resumes from the last accepted position and velocity, which the pilot also sends |
| `CalculatePlayerTurnEvent` gives per-frame mouse deltas while upstream integrated per render frame with `deltaSeconds` | The attitude code takes `deltaSeconds` as upstream; `AircraftAttitudeTest` covers 30, 60 and 144 fps |
| PartEntity hit boxes and vanilla collision differ from upstream's custom `move` | Port upstream's `move` collision order (Y, X, Z with step-up) against the main box only, as upstream; parts take no part in terrain collision |
| API names beyond PORTING_PLAN §2.3 (dispenser registration, menu screen registration, GameTest helpers) are not yet verified for 26.2.0.88 | Checked against the decompiled 26.2.0.88 sources on the first day of M3, before commit 3 |
