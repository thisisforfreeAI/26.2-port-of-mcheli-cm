# M6 plan: remaining vehicles and support systems

Status: in progress; shared integration, planes, ships and the ground-vehicle increment are implemented. This expands PORTING_PLAN.md §6.
The starting checkout is 0.5.1. Preserve decisions D1–D23 and compare
behaviour with McHeli CE 1.5.1 at `cbaff7f30e549ff75e485388c24c74a4316c2228`.

## Scope and implementation order

Each stage needs its own build and relevant tests before the next stage relies on it. Keep the
existing helicopter workflow working throughout. M6b (Wingman) remains separate.

| Stage | Implementation | Acceptance evidence |
|---|---|---|
| 1. Shared vehicle integration | Remove helicopter-only assumptions from placement, pilot movement validation, input dispatch, HUD, engine sounds and renderer registration where the behaviour is shared. Keep helicopter-only fold blades and hover handling specific to helicopters. | Existing helicopter JUnit and GameTests still pass; non-helicopter inputs are checked against the controlling player and correct vehicle ID. |
| 2. Planes | Real plane entity, upstream flight formulas, VTOL/nozzles, variable sweep wings, autopilot, controls, part animations, sounds and weapons. | All 46 stock planes place, mount and save/load; representative conventional and VTOL craft take off, turn and land in both authority modes; upstream formula comparisons. |
| 3. Ships | Real ship entity, water depth/buoyancy, propulsion, steering, controls, models and weapon integration. | All 5 stock ships place, mount and save/load; buoyancy, turning, stopping and torpedo firing checked in water. |
| 4. Tanks and vehicles | Real tank and vehicle entities, upstream movement rules, wheel contacts and terrain tilt, crawler tracks/rollers, turret controls and ERA. Respect vehicle content that disables movement or rotation. | All 12 tanks and 18 vehicles place, mount and save/load; movement and slope tests, stationary gun restrictions, turret firing and track animation checks. |
| 5. Vehicle equipment | Racks, cargo containers, chains, parachutes, ropes/rappelling, searchlights, camera position switching, supply vehicles and remaining aircraft menu functions. | Attach/detach, dismount, destruction, reload and invalid-target cases; no dangling riders or duplicated items. |
| 6. Operators and tools | UAV station/pairing/tablet/control, gunner NPCs and mobs in seats, GLTD, hand-held Stinger/Javelin, wrench, rangefinder, mine defuser and item models. | Control authorization, operator disconnect, death and range limits; gunner IFF; hand-held firing/reloading and tool durability tests. |
| 7. Blocks and teams | Drafting table recipe browser/preview, aircraft charger with the current energy capability, multiplayer teams and spotting markers. | Recipe selection and inventory safety, energy transfer limits, server authority and friendly-target/spotting tests. |
| 8. Deferred combat systems | BVR/passive radar, GPS panel/missiles, mortar radar/RWR, ECM, pulse-Doppler notch, iron curtain, data link, anti-radiation missiles, UAV Destruct bombs and targeting-pod spotting. | Upstream behaviour comparisons and functional tests for target acquisition, loss, countermeasures and friendly-team filtering. |

The counts above are the stock inventory from M2, not a limit on addon definitions.

## First implementation increment

- Plane entities now use their content definitions and shared aircraft seats, fuel, damage and
  weapons. Items create/place planes rather than returning an inert placeholder.
- `PlaneFlightModel` ports upstream control and physics formulas. The initial increment
  incorrectly ran two movement steps per tick; the ship increment corrects this to the pinned
  source's single step and adds an entity-level cadence regression.
- Nozzles/VTOL and folding/sweep wings have server-controlled state and animation; saved
  state is restored without reapplying the default VTOL mode over a saved forward-flight mode.
- Rider controls, HUD, mouse flight, engine sounds and model submission support planes alongside
  helicopters. F switches VTOL and Z folds/sweeps wings outside camera view, as upstream.
- The network keeps the helicopter encoding and adds a plane payload; protocol version 3
  requires matching client/server builds. Vehicle identity and rider authorization are checked.
- Flight tests compare 300 ticks per scenario against a transcription of upstream, including
  cruise, VTOL, transition, reverse, sweep speed, water, landing and target drones. GameTests
  exercise all 46 stock definitions, takeoff, persistence, authorization and movement authority.
- All stock plane body models and textures are checked. The 79 part references absent in the
  pinned upstream model files are recorded explicitly in the parity fixture; additional gaps fail.

This does not complete stage 2. Remaining plane work includes equipment integration and broader
gameplay checks. The subsequent ship increment adds conventional and VTOL flight scenarios plus
local OpenGL/Vulkan flight/weapon and two-client smoke checks. UAV-only and target-drone placement
still waits for the UAV station. The ground increment below addresses stage 4; stages 5–8 remain. Do not
treat the shared renderer or asset tests as proof of visual correctness.

Validation of this increment: the saved installation script completed `build`; all 110 JUnit
tests passed with zero failures/errors/skips, and the dedicated GameTest server completed all
58 required tests successfully. Five new GameTests cover stock planes, control/persistence,
VTOL takeoff, authority bounds and the default-VTOL save/load regression. At that point graphical
and two-client tests had not run; the subsequent effects graphics checks are described below.

## Plane effects increment

- `PlaneParticles` ports nozzle smoke and obstruction spread, landing dust, rotor/fuselage damage
  trails and content-defined water wakes. It takes frame values, block queries and an emission
  sink so the same formulas can be tested without a client level. Each plane owns its histories.
- Damage smoke preserves the pinned upstream's unused `spawnSmoke` gate and `/3.0` trail
  interpolation. It also preserves `MathHelper.sqrt(double)`'s rounding order; a boundary
  regression checks a movement distance where rounding the input would emit one fewer particle.
  Changes to the visual style or those legacy quirks remain outside this porting increment.
- `PlaneVisualMotion` supplies destroyed-plane roll direction and the client-only pitch/roll
  rules near and away from terrain. Authoritative flight formulas are unchanged.
- Addon exhaust flames load `models/exhaustflames/<model>` and contiguous numbered textures
  `textures/exhaustflames/<prefix>0.png`, `...1.png`, etc., up to 100 frames, as upstream.
  The stock pack has no definitions/assets for this newer feature; addons supply their own.
  Missing models or texture sequences are skipped rather than drawing missing-texture geometry.
- Extracted flame draws contain their model groups, pose and texture; submission uses vanilla's
  translucent emissive pipeline and full brightness. No raw OpenGL calls or custom shaders are
  added. Animation retains upstream's per-extraction cadence; shader resource reload clears its
  caches, and a shortened texture sequence cannot leave a stale frame index.

### Graphics validation setup

The development client runs under a local Xvfb display with Mesa software rendering. Separate
ignored `.run/effects-opengl` and `.run/effects-vulkan` game directories contain copies of a flat
test world and a synthetic addon overriding `harrier_en`. It holds throttle at 1, speed at 0 and
gravity at -0.05 for a stationary scene. The addon supplies a small diagnostic triangle and two
coloured textures for `AddExhaustFlame`; these are test fixtures, not new stock artwork.

The scene summons the plane at `(0, -59, 12)` with `PlaneThrottle:1.0d`, `AcFuel:1200` and
`AcDamage:150`, and views it from `(14, -51, -4)` at yaw 41, pitch 22. Allow the remote throttle
interpolation to settle before inspecting flame size. Use `--graphicsBackend OPENGL` or
`--graphicsBackend VULKAN` in the development run's program arguments and verify the actual
backend line in the client log. For the pinned NeoForge build, Vulkan also needs
`earlyWindowControl = false` in that game directory's `config/fml.toml`: its early OpenGL
window otherwise causes GLFW error 65540 when Vulkan creates a surface. The local software
driver uses an explicit lavapipe ICD and a writable Mesa shader-cache directory.

The effects increment passes `build`, all 121 JUnit tests (zero failures/errors/skips), and all
58 required GameTests. The 11 new JUnit cases cover particle values, trail history and rounding,
water gating, landing dust, destroyed-attitude boundaries, exhaust animation, matrix transforms
and texture-cache reload. Live scene checks confirm damage/nozzle smoke and diagnostic exhaust
geometry on both backends, including resource reload. Switching to VTOL removes the diagnostic
flame. Screenshots: [OpenGL](screens/m6-plane-effects-opengl.png) and
[Vulkan](screens/m6-plane-effects-vulkan.png). The coloured triangle is the synthetic addon probe.

These scene checks cover effects/model submission, not complete plane flight, hardware
performance or two-client synchronization. The virtual display has no audio device; audio is
unverified. Full manual flight/weapon and two-client checks remain outstanding.

## Plane camera increment

- Removed the helicopter-only gate in the shared client camera controller. Planes now receive
  gunner camera positioning, zoom, night-vision post effects, TV-missile camera updates and
  content-defined third-person distance through that controller.
- H follows `MCP_ClientPlaneTickHandler#playerControl` at the pinned commit: enter gunner mode
  when allowed; leave gunner mode for position 1 when extra positions exist; cycle the remaining
  positions and return to normal view. If gunner flight conditions are not met, extra positions
  remain available. A plane without gunner mode or extra positions refuses the switch.
- Camera IDs are local viewer state, not NBT or network fields. Only gunner-mode transitions
  send existing control packets, so protocol version 3 is unchanged. Extra positions apply to
  the pilot; crew `InvCamPos` and ordinary passenger viewpoints stay independent.
- Selection resets on pilot mount/dismount, destruction and content changes. Switching directly
  between vehicles also refreshes the client view marker and third-person distance. Changing a
  content type before seats spawn now skips missing seat references instead of crashing.
- All stock planes have one camera position. Addon definitions can supply more; the HUD shows
  the H camera/gunner hint when they do. Camera `FixedRot`, `Yaw` and `Pitch` remain inert because
  the pinned upstream parses those values but never applies them in the mouse/view path.

Validation: `build` passes, with 121 JUnit tests and zero failures/errors/skips. All 63 required
GameTests pass. Five new GameTests exercise the upstream view sequence, planes without gunner
mode, blocked gunner conditions, invalid indices, crew/stranger isolation, `InvCamPos`,
`AlwaysCameraView`, dismount/remount, save/load, content changes, destruction and network controls.
Live camera checks run in isolated `.run/cameras-opengl` and `.run/cameras-vulkan` directories,
using the effects increment's Xvfb/Mesa setup and backend flags. A separate local addon overrides
`harrier_en` with three TXT `CameraPosition` entries: `(0, 2, 0)`, `(14, 4, -10)` and `(-14, 4, -10)`.
It sets speed to zero, enables gunner mode and keeps the normal camera rotation. Coloured block
pillars provide fixed landmarks. These definitions are test fixtures, not changes to stock content.

Both backends show the two extra positions and return to the cockpit. Night vision and dismount
cleanup are checked in OpenGL; Vulkan also checks entering gunner mode in the air, zoom changing
from 1× to 2×, night vision and dismount cleanup. The Vulkan fixture uses `ThrottleUpDown = 3` (the parser clamps
higher values to 3); holding W before H raises throttle above the gunner threshold. This checks
camera integration, not a complete takeoff/flight/landing scenario. Build before launching these
clients: recompiling their shared class output while a client is running invalidates the check.
Screenshots: [OpenGL](screens/m6-plane-camera-opengl.png) and
[Vulkan](screens/m6-plane-camera-vulkan.png), both at the first extra camera position.

Full flight/weapon and two-client checks remain outstanding; TV-missile view is connected but
not exercised in these camera scenes. The subsequent parachute increment adds ejection below.

## Parachute and ejection increment

- `ParachuteEntity` replaces the placeholder; `ParachuteMotion` ports the gravity, forward steering,
  horizontal cap, acceleration multiplier and post-collision drag from `MCH_EntityParachute`.
  Type 1 is the hand chute, type 2 the boosted ejection seat (opening below -0.1 vertical speed),
  and type 3 the transport chute. Type 2 uses reduced gravity/drag before tick 20. The server
  simulates motion; clients use vanilla tracking with the upstream thirteen-tick smoothing length.
- Landing releases the rider and removes the chute after six ground ticks. Water removes it;
  a type-2 seat creates an oak rescue boat only when the upstream 5×5×3 water count exceeds 37.
  Water probes use modern build heights and fluid states. Model type and ordinary entity state
  save/load normally; riders use vanilla passenger persistence.
- The hand item deploys at upstream's `(0.5, 3.5, 0.5)` offset and inherits velocity/fall distance.
  The server mounts the user immediately and consumes an item only after successful deployment;
  creative users keep it. Grounded or mounted use is refused without consuming anything.
- Space ejects the pilot or first crew seat of a supported plane. The server checks the sender,
  vehicle ID, content flag, rider seat, destruction state and reserve availability. Launch position
  and impulse rotate with the aircraft, its velocity is inherited, and ejection opens the canopy.
  Pilot ejection also rescues a non-player in seat 1 when another chute remains; a player in that
  seat chooses their own ejection. A repeated request after transfer cannot charge another item.
- One/two-seat ejection-capable planes expose one/two reserve slots. Two independent storage slots
  save under `ParachutesAC`, so `ItemsAC` fuel/trunk indices remain compatible. Menu transfer rules
  accept one parachute per reserve. The opening packet includes reserve-slot count; protocol 4
  rejects older clients rather than letting their menu slot numbering disagree.
- Y now reaches crew seats. A transport passenger beyond seat 1 can use a type-3 chute when content
  permits it, the hatch is open and the ground is clear. Drop positions use the pinned queue's
  ten-position history and its `current + 1` oldest-index quirk. Ordinary dismount remains available.
  Automatic mob dropping, ropes/rappelling and the remaining equipment controls are still pending.
- `ParachuteRenderer` draws the stock hand chute and selects `$seat`/`$parachute` for the other
  forms. Geometry uses the shared submission pipeline and model-sized culling bounds, without
  raw graphics calls. The stock type-2 MQO has malformed exported names and draws neither part
  upstream. Its exact SHA-256 is pinned in a regression test; no shipped asset is rewritten.
  A valid addon model supplies those names. Ejection smoke retains upstream's triple assignment
  to `motionX`, including its random-call order; visual redesign remains outside the port.

Validation: `runData` completed and `build` passes with all 127 JUnit tests and zero
failures/errors/skips. All 76 required GameTests pass. The thirteen new GameTests cover hand
item use/refusals, creative consumption, real descent and safe landing, initial ejection boost
and opening during descent, wrong sender/vehicle ID, empty reserves, repeated packets, first
crew ejection, other-player protection, non-player rescue, inventory transfers and persistence,
older saves, transport-seat bailout, and both sides of the water-volume threshold.

### Parachute graphics checks

The isolated `.run/parachutes-opengl` and `.run/parachutes-vulkan` game directories use the
existing Xvfb/Mesa setup and explicit backend flags. `zz_parachute_probe` is a local test addon:
it removes the exported-name prefixes from a copy of `parachute2.mqo` and moves the seat object
before the canopy group, so the existing geometry has valid groups. This fixture is not shipped
in the mod and does not change the pinned stock asset.

With `/tick freeze`, summon models 1, 2 and 3 at `(-6, -57, 12)`, `(0, -57, 12)` and `(6, -57, 12)`.
Give model 2 upward motion `(0, 2, 0)` to select its closed seat; model 3 selects the open canopy.
View from `(16, -48, -6)` at yaw 41, pitch 18. Both backends draw the stock hand chute and the
addon's closed seat/open canopy. Reloading resources keeps them visible. In Vulkan, temporarily
removing the addon model and reloading makes only the stock hand chute remain; the stock missing
parts produce no rendering error. Restoring the addon and reloading restores the other forms.

Live OpenGL controls also verify the aircraft menu's reserve slot and shift-click round trip.
Space transfers the pilot to a type-2 chute and changes the reserve from one item to empty;
right-clicking the hand item while airborne deploys type 1. Screenshots:
[menu](screens/m6-parachute-menu.png), [OpenGL](screens/m6-parachutes-opengl.png), and
[Vulkan](screens/m6-parachutes-vulkan.png). The large canopy and small black seat in those model
scenes use the test addon; the smaller brown hand chute uses the shipped model.

These checks cover rendering and an integrated-server control path. Two-client synchronization,
full aircraft flight/weapon checks and hardware performance remain unrun; the virtual display
still has no audio device.

## Ship increment and plane cadence correction

- `ShipEntity` replaces the placeholder and uses ship definitions with real placement, shared seats,
  fuel, damage, weapons, inventories and item data. Stock ground-movement/rotation restrictions
  are enforced. `allShipSpeed` scales each ship's normal and swept-wing speed limits.
- The pinned `MCH_EntityShip` duplicates almost all plane control, movement, parts and particle
  code. `WingedAircraftInfo` and `WingedAircraftEntity` share those implementations; planes and
  ships remain sibling entity types. Ship models/parts/textures load from `ships`, both on demand
  and eagerly. Plane exhaust animation remains specific to planes.
- Buoyancy uses the shared five-slice water probe and each definition's float offset, including
  negative world heights. Steering, throttle, reverse (when enabled), hull levelling, nozzle/wing
  parts, rotor animation, damage smoke, wakes, dust, camera controls and sound follow upstream.
  Steering wheels now rotate about their content pivots rather than remaining at rest.
- `ShipControlPayload` carries common controls and nozzle/wing switches. Protocol 5 requires
  matching clients/servers. Identity, rider, pilot and destroyed-state checks use the shared path.
  Pilot motion application also enforces the existing authority and entity-ID checks directly.
- None of the five stock ship definitions mounts torpedoes. The guided Mk46 integration check
  uses an isolated addon-style ship definition; shipped loadouts and assets are preserved.
- Auditing the pinned source found that the earlier plane increment's two-step cadence was
  incorrect: `MCH_EntityAircraft#onUpdate` calls `onUpdateAircraft` once, which calls
  `MCH_EntityPlane#onUpdate_Server` once, and that method moves once. Both vehicle kinds now use
  one movement step per tick. The old numeric oracle repeated both sides twice and missed the
  integration error; an entity-level movement-call/drag/bounds regression now guards it.

The seven ship GameTests cover all five stock items/definitions, pilot and crew mounting,
world persistence, water-surface equilibrium, acceleration, turning, braking, land restrictions,
destroyed buoyancy, both authority modes, wrong sender/ID, invalid velocity, speed configuration,
crew-only gunfire, guided torpedo launch/travel and item fuel/damage round trips. Three additional
plane tests exercise a conventional P-51D and VTOL Harrier through takeoff, mouse turning,
transition and safe landing, plus weapon launch/ammunition in both authority modes. Flights run
outside the GameTest arena's barrier walls and load their collision chunks. They exercise the
real entities synchronously, not a second networked client.

Automated validation: `build` passes with 129 JUnit tests (zero failures, errors or skips),
and the dedicated GameTest server completes all 87 required tests. The model audit covers all
five ship bodies, every referenced part and all textures, with no missing stock ship assets.
A separate transform check verifies the Zodiac outboard's steering pivot and rotation direction.

### Ship graphics and multiplayer checks

Two real development clients connect to a local offline dedicated server: an OpenGL pilot on
Xvfb `:99` and a Vulkan observer on `:100`, both with Mesa software drivers. Isolated game
folders are `.run/ships-server`, `.run/ships-pilot` and `.run/ships-observer`. The pilot uses eager
model loading; the observer uses on-demand loading. The launch overrides are kept locally in
`/workspace/.graphics/ships-multiplayer.init.gradle`. No test addon is needed for these stock scenes.

A source-water lake spans X/Z -128 to 127, Y -59 to -55. All five stock ship models render in the
fleet scene. Resource reload on both clients keeps their models and textures visible. The pilot
opens the Zodiac fuel menu, steers at full throttle for at least 60 seconds in the default client
authority mode while the observer watches, then brakes to zero throttle and zero horizontal
velocity. The server's `vehicles.serverAuthoritativeMovement` setting is reloaded and broadcast
to both clients; the steering/movement check is repeated in that mode. Server position/rotation
queries and observer frame captures confirm movement and turning in both modes. These are local
movement smoke checks, without measured per-frame smoothness statistics or network latency/loss.

The same clients also exercise a Harrier in each authority mode: start in VTOL on land, hold W
for climb, use F to transition to forward flight, and hold the left mouse button for cannon fire.
Server position, part-status and ammo queries confirm climb, forward movement, nozzle transition
and firing; the Vulkan observer sees the moving plane. Actual mouse-turning and complete manual
approach/landing remain unrun; those sequences are covered by the synchronous entity tests above.
Both clients and the dedicated server shut down cleanly.

Screenshots: [OpenGL fleet](screens/m6-ships-opengl.png), [Vulkan fleet](screens/m6-ships-vulkan.png),
and [ship menu](screens/m6-ships-menu.png). Audio remains unverified because the
virtual displays have no audio device; software rendering does not establish hardware performance.

## Ground-vehicle increment

- Real sibling `TankEntity` and `VehicleEntity` types replace the last main-vehicle placeholders.
  All 12 stock tank/car and 18 placed-vehicle definitions use shared placement, seats, fuel,
  inventory, weapons, damage and item/world persistence. Content movement/rotation restrictions
  and the upstream default fixed-position anchor for placed vehicles remain enforced.
- `GroundMotion` retains the pinned tank throttle/reverse, gravity, buoyancy, speed cap and two
  ground-grip passes around a single movement step. Wheeled vehicles retain the longer upstream
  coast. Ground attitude/keyboard steering runs once per simulation tick (0.05 s), independent
  of rendering frame rate. Local wheel probes provide mirrored contacts, weighted terrain normals
  and constrained pitch/roll; large hull relocations reset probes instead of sweeping the map.
- Mouse motion aims weapons independently of the chassis. W/S drives, A/D steers, held Space
  brakes tanks/cars, H cycles additional camera positions, and Z zooms placed-gun cameras.
  `GroundControlPayload` adds the held brake with pilot/vehicle checks. Protocol 6 requires
  matching builds. Existing helicopter, plane and ship payload layouts remain intact.
- Supported model submission draws wheel steering/spin, crawler links and rollers, shared tank
  turrets and recursive placed-gun yaw/pitch/barrel/recoil parts. Ground assets load both eagerly
  and on demand. Tank damage smoke, dust and content water splashes use shared effects.
- Reactive armor uses synchronized per-entity state and `AcERAState` persistence, rather than
  mutating shared content definitions. Qualifying hits pop active tiles; popped tiles stop
  rendering and receiving hits. Projectile tracing now checks active extra hull boxes as well
  as the base box. No stock definition includes ERA, so the checks use an isolated fixture.

Automated validation: 136 JUnit tests and 95 required GameTests. Seven new GameTests cover all
30 stock placements, pilot/crew mounting and save/item round trips, M1A2/BNR32 drive/steer/brake/
reverse, uneven terrain, authorization and both movement authority settings, stationary gun and
independent tank firing, plus reactive armor threshold, projectile tracing, per-entity isolation
and actual save restoration. The model audit checks every referenced part and texture; only the
MXT-MV canopy is absent, matching the pinned upstream asset. Four formula/animation unit tests
cover the single movement cadence, tracked/wheeled grip, movement restrictions and track wrap.

### Ground graphics and multiplayer checks

The same isolated local dedicated server and two development clients used for ships now exercise
all 12 stock tanks/cars and all 18 stock placed vehicles in separate fleet scenes. The pilot uses
OpenGL with eager model loading; the observer uses Vulkan with on-demand loading, both on Xvfb/Mesa.
Models remain visible after F3+T resource reload on both backends. Screenshots:
[tanks/OpenGL](screens/m6-tanks-opengl.png), [tanks/Vulkan](screens/m6-tanks-vulkan.png),
[guns/OpenGL](screens/m6-ground-guns-opengl.png), [guns/Vulkan](screens/m6-ground-guns-vulkan.png).

A real M1A2 pilot drives and turns with W/A, holds Space to stop, opens the fuel/ammunition menu
and fires the main cannon. The server's position, rotation, throttle, velocity and ammunition
queries confirm the actions. Mouse motion changes rider aim while a stopped hull keeps its yaw.
After changing `serverAuthoritativeMovement` and receiving the config broadcast on both clients,
the same tank drives/turns, reverses, brakes and fires again. The Vulkan observer sees the tank.
The stock 12.7 cm emplacement remains at its placed position and chassis yaw under W/A, aims with
the mouse and fires using real client input. These are local smoke checks without measured
frame-time statistics or simulated latency/loss. The virtual displays have no audio device.

The placed-gun camera check found the rider body occluding the primary camera. Rider render
state now honors pilot `HideRiders` and suppresses the local rider in a primary first-person
marker camera. The visibility decision is extracted into render state and applied through the shared living
renderer event, including players, without graphics backend calls. The primary gun view is clear
on both graphics backends after the fix. Additional captures: [tank menu](screens/m6-tank-menu.png),
[Vulkan tank observer](screens/m6-tank-observer-vulkan.png), and
[gun camera](screens/m6-ground-gun-aim.png).

The collision follow-up below adds crushing and ramming. Solid movement collision still uses
the main hull and wheel probes; extra hit boxes supply damage/crushing regions. Equipment,
operators, tools, blocks/teams and deferred combat systems in stages 5–8 remain unfinished.

### Tank collision follow-up

`TankCollisions` runs after the server's tank tick, with current part positions, in either
movement authority mode. It restores upstream's weight-specific block crushing, wheel soft-block
removal and collision damage. Cars crush vegetation/panes; heavy tanks also crush wood, glass,
carpets, circuits and the configured nether-brick fence. Torch and glowstone exclusions protect
their columns. Each crushing pass stops at the first protected or broken block in a column;
one in eight broken blocks drops items. Active extra hull regions are sampled one tick in three.

Minecraft 26.2 has no old block `Material`. Editable block IDs and tags in
`vehicles.crushing` replace those categories, using current vanilla/NeoForge tags. The server's
existing `vehicles.collisionDestroyBlock` switch gates all crushing. Coordinates use floor and
the level's real build heights, following the existing `GroundProbe` negative-coordinate fixes;
collision scans skip unloaded chunks. No network layout change is needed for server-side effects.

Ramming retains the speed threshold, speed/proximity damage, cumulative per-query bonus,
ordinary/aircraft knockback and car reciprocal damage. Pilot credit uses a mob-attack damage
source even for a player pilot, so contact cannot invoke the vehicle melee-pickup path. General
entity damage is enabled by default; heavy-tank damage has a separate switch disabled by default.
Occupants, their seat entities, items, XP, mod projectiles and chains are excluded; arrows are
cleared. Upstream's occupant grace counters refresh/decrement every ten ticks, so an eight-count
entry lasts up to eighty ticks. Parent/part query duplicates are collapsed within each region;
popped ERA regions cannot crush or ram. Chain links will add their towing exclusions when the
chain system is implemented. Extra boxes still do not form solid movement collision shapes.

Seven additional GameTests exercise car/heavy-tank defaults, protected columns, configuration
overrides and switches, negative-world-height column traversal, damage/credit/knockback in both
movement authority settings, occupant and dismount grace, ignored entity types, heavy-tank opt-in,
car reciprocal damage, multipart deduplication and active/popped ERA collision regions.
The full build passes all 136 JUnit tests and all 102 required GameTests, with no unit-test
failures, errors or skips. Collision fixtures keep the hull still while running real server
ticks; existing drive/terrain/weapon scenarios continue to exercise the stock movement code.

Live verification reuses the isolated dedicated server and OpenGL pilot/Vulkan observer. An
M1A2 starts at `(700.5, -59, 0.5)`, drives through a three-high wooden wall at Z 12 and rams a
marked cow at Z 20.5. Server block queries confirm the wall's center cleared and a protected
glowstone at Z 30 survived; the driver's mob-kill objective increments. W is held eight seconds,
then Space brakes to zero throttle. The default client-authority run reaches Z 74.4. After the
server-authority setting reload reaches both clients, a fresh tank repeats the course with five
seconds of W and five of Space, stops at Z 52.84 and increments the kill count a second time.
No weapon input is used. The Vulkan observer sees the moving tank and contact with the target.

A BNR32 in the parallel lane at X 760.5 clears a three-high glass-pane wall at Z 12, then stops
at Z 24 against the wooden wall at Z 25. Block queries confirm the pane center is gone and the
wood remains. Captures: [tank contact](screens/m6-tank-collision-vulkan.png) and
[car stopped at wood](screens/m6-car-crushing-vulkan.png). These are local functional smoke
checks on software graphics, without latency/loss or hardware-performance measurements.
The dedicated server saves/stops successfully. The Vulkan client reports a native connection
reset on server shutdown and returns to its disconnect screen; both clients then quit normally.

## Supply and medical support increment

- The stock fuel truck now supplies compatible vehicles in its configured 35-block expanded
  bounding box. Fuel supply runs server-side every ten ticks before consumption and transfers
  up to 30 fuel per recipient, capped by recipient capacity. A grounded supplier requires a
  recipient able to resupply; an airborne supplier can refuel airborne recipients.
- Finite suppliers use their current fuel type and pay from their tank; infinite suppliers use
  `FuelSupply.Type` and need no onboard fuel. Empty recipients adopt the source type; nonempty
  incompatible tanks and unaccepted content fuel types are skipped. As upstream, finite sources
  transfer before clamping their remaining fuel, and may supply the last full increment even
  with fewer than 30 units left. A source emptied during a scan may still supply the other
  recipients in that scan. Compatible recipients receive a 40-tick consumption grace even when
  full or unable to land. The grace counter is transient, as upstream.
- `RepairOtherVehicles.Range`/`Value` now reduce nearby vehicle damage every twenty ticks,
  excluding the supplier itself. Upstream does not gate repair on landing or destruction:
  a wreck can supply repair, and reducing wreck damage does not clear its destroyed state.
  All stock repair ranges are zero, so this is an addon feature without stock balance changes.
- `Regeneration` now applies ambient regeneration I for 250 ticks to living secondary-seat
  passengers of an intact medical aircraft while a pilot is present. The pilot is not a
  patient. Effects with at least 500 ticks remaining are left alone; shorter effects use the
  normal effect-merging rules. Stock NH90, US-2 and An-2 definitions already enable this.
- Existing ammo-box resupply remains connected beside fuel supply. The effects run independently
  of movement authority, with no new control payload or client authority over fuel/damage.
- Fuel names remain compatibility tokens until registered fluids/container capabilities are
  ported (KNOWN_ISSUES difference 68). Electric battery/charger support remains pending.

Automated validation: all 136 JUnit tests pass without failures, errors or skips, and all 109
required GameTests pass. Seven new support scenarios check stock fuel and ammo supply in both
movement authority modes, finite transfer/cadence/capacity, fuel compatibility, electric-source
exclusion, range/landing rules, consumption grace, repair/wreck semantics and stock NH90 medical
passengers. Precise addon fixtures run the real shared server tick with movement held still.
The stock-supply scene is elevated above concurrent tests because its 35-block radius exceeds
the GameTest structure spacing; the all-planes spawn check likewise isolates its fuel truck.

Live checks use the isolated dedicated server with the OpenGL pilot (eager models) and Vulkan
passenger/observer (on-demand models), on software Mesa drivers. A stock fuel truck at
`(910.5, -59, 0.5)` refuels the piloted AH-64 nearby from 0 to 1,080, then its 1,200 capacity.
The pilot sees the fuel gauge and the observer sees both models. After changing to server
movement authority, resetting fuel to zero yields 600 after about ten seconds. This verifies
server/client integration locally, without a latency or hardware-performance claim.

In the NH90 at `(960.5, -59, 0.5)`, the Vulkan secondary-seat passenger receives the visible
Regeneration effect; server queries show ambient regeneration with 249 ticks remaining after
an entity tick, while the pilot has no effect. With natural health regeneration disabled, six
points of damage reduce passenger health to 14 and it returns to 20 while the pilot remains
aboard, including after the server-authority setting reload. Clearing the effect after the
pilot exits leaves the passenger without regeneration. Captures:
[fuel truck and AH-64](screens/m6-fuel-truck-vulkan.png) and
[passenger effect panel](screens/m6-medical-passenger-vulkan.png). The server saves/stops
successfully, both clients return to the disconnect screen, and both quit normally with exit 0.


## Cargo container increment

- Replace the inert container entity/item with the stock two-block-wide, fireproof cargo entity.
  The five-block placement ray accepts blocks/source fluids, snaps yaw by quarter turns and
  preserves the upstream half-block spawn offset. Eye obstruction, occupied placement and failed
  server spawn refuse placement without consuming the item; creative placement keeps the item.
- Right-click opens a vanilla six-row inventory, with all 54 slots shared by viewers and normal
  shift-click transfer. The pinned upstream `displayInventory` stub is empty, so connecting this
  menu is an explicit fix (KNOWN_ISSUES difference 69). Validity follows current entity interaction
  distance. Modern slot codecs save counts, components and empty-slot positions. Both direct and
  sided automation expose NeoForge's current transactional item capability.
- Only player melee damages the container, with external scaling, accumulated damage times 20,
  one point of decay per tick, and the strict greater-than-40 threshold. Creative breaks instantly.
  Survival drops one empty container and its inventory; creative drops the inventory alone.
  Repeated destructive removal cannot drop contents twice. Unload and dimension-change removal
  reasons retain contents and do not scatter items into the source world.
- Server physics preserve gravity/buoyancy from five fluid slices, floating in both water and lava,
  a .35 horizontal speed cap, .99/.95 damping, .9 ground friction and five-degree movement yaw.
  The unused upstream speedMultiplier has no effect and is omitted. Cargo emits no walking
  sounds/events, as upstream. Containers push one another;
  the global collision-destruction setting gates corner-probe snow/lily-pad clearing. Preserve
  upstream's floating-point corner-index division. Probes use current build heights/fluid tags.
- The stock container OBJ, texture, .75 tint and .3 vertical render offset use the supported model
  submission path. Client tracking retains the upstream thirteen-tick interpolation extension.
  Protocol 7 rejects clients from before the new container entity data. No new payload is added.
- Aircraft racks, container rack attachment, tow chains and ropes/rappelling remain later M6
  equipment increments. This increment does not enable transport attachment hints prematurely.

Automated validation: all 136 JUnit tests pass with zero failures, errors or skips, and all
116 required GameTests pass. Seven new cargo scenarios exercise real placement/consumption and
refusals, every saved slot (including item components/custom names), shared six-row menus and
shift-click transfer, transactional capability commit/rollback, range/removal validity, damage
threshold/decay and exactly-once drops, unload/dimension-removal reasons, gravity/speed/yaw,
water/lava buoyancy, fire immunity and configured soft-block clearing/container pushing.
Actual cross-dimension travel is not part of this check; its removal reasons and serialized
inventory are covered independently.

Live validation uses the isolated dedicated server with an OpenGL player (eager models) and
Vulkan player (on-demand models) on software Mesa drivers. Both right-click the same crate at
`(1100.5, -59, 0.5)` and see all 54 slots, including items in slot 53. The OpenGL player
shift-clicks 32 diamonds out of slot 0 while the Vulkan player takes 12 emeralds from slot 53.
Server queries show the crate empty and those exact counts in the two player inventories.
Both return their items by shift-click; the crate holds 32 diamonds and 12 emeralds again.

A survival player places the actual container item near `(1085.5, -59, -0.5)`; it settles on the
ground at Y -59, and the player's inventory no longer contains the placement item. Four normal
melee clicks break it into one container item. Breaking the loaded land crate likewise drops one
container plus diamonds in stacks of 10 and 22, and 12 emeralds (the exact stored total).
A crate in an enclosed three-block-deep water pool remains afloat. A server-set X impulse of
.15 moves it from X 1127.5 to the pool wall at X 1133, with Vulkan tracking the movement. Captures:
[OpenGL model](screens/m6-cargo-opengl.png), [Vulkan inventory](screens/m6-cargo-inventory-vulkan.png)
and [Vulkan floating crate](screens/m6-cargo-floating-vulkan.png).
These are local functional checks; software rendering does not establish hardware performance,
audio or latency/loss behavior. The server saves and stops successfully. The Vulkan client
reports a native Netty connection reset on server close, returns to its disconnect screen, and
both clients quit normally with successful Gradle exit. No mod exception occurs during cargo
placement, inventory interaction or movement.



## Integration points already identified

### Shared Ctrl controls follow-up

The riding key context now conflicts with NeoForge's `IN_GAME` context. `KeyModifier.NONE`
otherwise rejects every riding binding while Ctrl/Shift/Alt is active, including Left Ctrl
itself. This fixes the freelook toggle and Ctrl+C/I/F for all implemented vehicle kinds without
changing packet layouts or stock seat/loadout definitions. Seat hints include Ctrl for crew
as well as pilots, remain visible from the pilot seat, and fit inside the right screen edge.
The Ctrl+C label explicitly says NPC Gunner; it toggles the enabled flag, and NPC implementation
remains pending. H retains the existing camera/gunner-view behavior.

Validation: 130 JUnit tests pass, including a regression for the exact context relationship
used by NeoForge's modifier filter. All 88 required GameTests pass. The new Project 1204 test
checks independent pilot aim, front-gunner aim, access to its three gunner seats, previous-seat
selection and outsider refusal.

A real OpenGL client on Xvfb/Mesa connected to the local dedicated server. Left Ctrl followed
by mouse motion turned the Project 1204 pilot's look to 40.5 degrees while the hull stayed at
zero, and the visible front turret followed it. Ctrl+I cycled through the front gun, rocket
launcher and rear gun seats; Ctrl+F returned to the rocket seat. Ctrl+C changed the server's
`AcGunnerStatus` from true to false. The same freelook check gave a 40.5-degree rider yaw with
zero vehicle yaw in the AH-64 and Harrier. The server stopped and saved cleanly, and the client
closed. These follow-up checks did not repeat Vulkan, two-client synchronization or full flight.

The integration notes below record the starting 0.5.1 audit; the first increment above has
already generalized the helicopter-only paths for planes and ships.

The prior milestone handoff explicitly includes special crafting recipes in M6. Fixed-item and
content recipes already exist; do not treat all crafting as missing. Port the two deferred special
recipes from `MCH_RecipeFuel` and `MCH_RecipeReloadRangeFinder`:

- Fuel refill: exactly one damaged fuel can and one or more occupied coal/charcoal slots, with no
  other ingredients. Each coal slot restores 100 fuel, each charcoal slot 75, capped at full;
  stack size within a slot does not multiply the refill. Upstream requires a 3×3 crafting grid.
- Rangefinder recharge: exactly one damaged rangefinder and one occupied redstone slot, with no
  other ingredients, producing a fully recharged rangefinder. Upstream permits a 2×2 grid.
- Test full items, multiple tools/cans, unrelated ingredients, mixed coal/charcoal, partial and
  over-capacity refills, slot stack sizes and crafting consumption. Verify recipe serializers,
  data loading and actual crafting output in the target Minecraft version.

The earlier question about recipes and paint variants lost by upstream YAML has no recorded
resolution in the supplied handoff. Keep that content-restoration question separate from the
explicitly scheduled special crafting recipes; do not silently replace stock YAML definitions.

- `AircraftEntity` already owns seats, fuel, damage, weapons, camera and shared state. Add sibling
  vehicle subclasses rather than making planes, tanks or ships inherit helicopter physics.
- `ModEntityTypes` still registers planes, ships, tanks, vehicles and the support entities as
  `PlaceholderEntity`. Replace registrations only with functioning entities and matching renderers.
- `AircraftItem#isPlaceable` and `createAircraft` currently accept only helicopters. Generalize
  dispatch while preserving collision checks, creative-only restrictions and item data transfer.
- `ModNetwork` gates control and pilot motion on `HeliEntity`. New controls must retain sender,
  rider and authority checks; existing payload layouts must not change silently.
- `HeliClientControl`, `MouseFlight`, `AircraftHudLayer`, `AircraftClientEvents` and
  `AircraftEngineSound` contain helicopter gates. Audit each individually: some behaviours are
  genuinely helicopter-specific.
- Reuse model loading, shared part poses, weapon sets and the existing render submission path.
  Port the upstream type-specific render parts as well as the main body.
- Extract movement formulas behind small body/state interfaces, as `HeliFlightModel` does, and
  compare tick sequences against upstream rather than testing only that an entity moves.

## Constraints carried forward

- Keep Minecraft 26.2, NeoForge 26.2.0.88, Java 25 and the compatibility asset namespace.
- Default piloting remains client-authoritative with server validation; also test the server
  authority setting. Vehicle-specific speed, reverse and climb rules need explicit validation.
- Rendering uses supported submission APIs and needs OpenGL and Vulkan checks.
- UAV live camera remains the documented telemetry/NO SIGNAL fallback unless supported off-screen
  world rendering is established. Do not promise a live feed based on station implementation alone.
- Charger energy uses the supported NeoForge capability; no GregTech EU dependency is introduced.
- Wingman formation, missions, planner screens and remote chunk streaming stay in M6b.
- Preserve the documented HBM limitation and all existing privacy decisions.

## Validation and completion

Run `./gradlew build` (including JUnit) and `./gradlew runGameTestServer` using Java 25.
Exercise every stock vehicle, both movement authority modes, crew seats, weapon use, fuel,
damage, item pickup, save/load and multiplayer synchronization. Add invalid/unauthorized control
cases alongside successful control cases. Keep current-run results and actual test counts.

The milestone also requires client world-load and dedicated-server startup/shutdown checks,
OpenGL/Vulkan rendering checks, and a two-client movement check. Report checks unavailable in
the current machine as unrun. Update CHANGELOG.md, README.md and KNOWN_ISSUES.md only for
implemented behaviour; do not remove the M6 missing-feature list merely because a plan exists.

## Development environment

Java 25 and Gradle 9.2.1 are installed, and the pinned upstream source and wiki have been restored
with `scripts/fetch-upstream.sh`. The earlier NeoForge proxy denial has cleared. The downloaded
JDK needs the system Java trust store, which includes the platform proxy CA: set
`systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts` in the Gradle user properties,
alongside the HTTP(S) proxy settings. TLS verification remains enabled. Build, JUnit and dedicated
GameTest execution now work in this instance. Reusable setup/start instructions have been updated
in the environment draft; draft saving does not publish a new snapshot.

Use `JAVA_HOME=/workspace/.toolchains/jdk-25+36` and `GRADLE_USER_HOME=/workspace/.gradle-home`
for Gradle commands in this environment. The increment sections above record the current
automated, graphics and two-client smoke checks; full gameplay and hardware validation remain.
