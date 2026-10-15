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

This does not complete stage 2. Remaining plane work includes dedicated exhaust/nozzle/damage
effects, destroyed-plane visual motion, camera-position switching, ejection, equipment integration,
broader conventional takeoff/turn/landing scenarios, and manual OpenGL/Vulkan and two-client
checks. UAV-only and target-drone placement still waits for the UAV station. Stages 3–8 remain
outstanding. Do not treat the shared renderer or asset tests as proof of visual correctness.

Validation of this increment: the saved installation script completed `build`; all 110 JUnit
tests passed with zero failures/errors/skips, and the dedicated GameTest server completed all
58 required tests successfully. Five new GameTests cover stock planes, control/persistence,
VTOL takeoff, authority bounds and the default-VTOL save/load regression. Manual graphical and
two-client tests have not run in this instance.

## Integration points already identified

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
for Gradle commands in this environment. Manual graphical and two-client checks remain unrun.
