# M6 plan: remaining vehicles and support systems

Status: in progress, starting with shared integration and planes. This expands PORTING_PLAN.md §6.
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
- `PlaneFlightModel` preserves upstream control and physics formulas, including the pinned
  source's two movement steps per tick. Movement validation accounts for that cadence.
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

This does not complete stage 2. Remaining plane work includes equipment integration,
broader conventional takeoff/turn/landing scenarios, and full OpenGL/Vulkan flight/weapon and two-client
checks. UAV-only and target-drone placement still waits for the UAV station. Stages 3–8 remain
outstanding. Do not treat the shared renderer or asset tests as proof of visual correctness.

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

## Integration points already identified

The integration notes below record the starting 0.5.1 audit; the first increment above has
already generalized the helicopter-only paths for planes.

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
for Gradle commands in this environment. Effects graphics checks now run as described above;
full flight/weapon and two-client checks remain outstanding.
