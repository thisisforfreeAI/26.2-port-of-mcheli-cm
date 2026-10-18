# Changelog

All notable changes to McHeli CE (26.2 Port), newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Versions stay below 1.0.0 until the port is feature-complete, and each milestone in
[PORTING_PLAN.md](PORTING_PLAN.md) (section 6) gets its own minor version. The port is based on
McHeli CE 1.5.1 (`master` @ `cbaff7f`). Where it deliberately differs from upstream, the difference is
listed in [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## [Unreleased] - M6 in progress

### Changed

- M8 performance follow-up: default 80 FPS adaptive cosmetic detail, coverage-weighted smoke/glow
  admission, bounded McHeli dust and secondary spray, early distant-explosion rejection and cached
  flare/glow geometry. Adds a repeatable 1080p frame-time probe; target-PC validation remains open.
- M8 pixel-effects follow-up: shaded smoke, flash and water sprites, a small flare halo, and
  Low/Balanced/High particle budgets. Preserves the existing enhanced heat, tracer, missile and
  ground-tint effects. Classic presentation remains available; see `docs/M8_PIXEL_EFFECTS.md`.

- Restore the post-port Vulkan visual-effects update to the roadmap as M8, after M7: explosions,
  smoke, fire, muzzle flashes, tracers, missile trails, flares, water splashes and rotor wash,
  refined for Vulkan with OpenGL kept working. Automatic vehicle turrets stay off the roadmap.

- The drafting table is drawn with upstream's 3D model by a block entity renderer, facing any of
  its eight directions and tinted grey as upstream, and the item shows the same model in the
  inventory, the hand and on the ground with upstream's offsets.
- The Stinger's and Javelin's lock now holds once complete, until the shot, until the target
  leaves the sight or until the launcher is lowered. Upstream's lock lasted a single tick and
  started again, so the attack key had to be held at the right moment.
- The Stinger, Javelin, laser designator, wrench and rangefinder are drawn with upstream's 3D
  models in the hand (and the designator and rangefinder on the ground), with upstream's
  in-use poses and the wrench's repair swing. The inventory keeps the flat icons.

- Remove the scheduled post-port Vulkan visual-effects update and McHeli team automatic-turret
  extension from the active roadmap. The user plans to port another mod after completing this
  port, before revisiting those extensions. Existing Vulkan compatibility remains required.
- Add optional Just Enough Items (JEI) recipe/usage compatibility to the remaining M6 crafting
  plan, covering fixed/content recipes, special fuel/rangefinder recipes and drafting-table
  recipes where applicable. Fixed/content recipes and the two special recipes are now
  supported (see Added); JEI shows the drafting table as a crafting station for them.

### Added
- Upstream's `mch_fuel` fluid and its fuel bucket. Buckets and other fluid containers in a
  vehicle's fuel slot refuel it, with the emptied container going to the output slot, and pipes
  and tanks from other mods can fill and drain fuelled vehicles through NeoForge's fluid
  capability. Fuels are not mixed, and water or other fluids a vehicle does not burn are refused.
- The aircraft menu has upstream's command field and Options button. A command set there by an
  operator runs on the server when the vehicle is destroyed by anything but a player; Enter runs
  it at once. Options opens the mod's settings. Only game masters can set the command, unlike
  upstream, where any player could.
- The aircraft charger works: cables and generators fill it through NeoForge's energy capability
  (up to 100,000 by default), using it shows what it holds, and every 10 ticks it feeds up to 256
  energy to the electric vehicles within 6 blocks, then to a battery in a full vehicle's fuel slot.
  Electric vehicles that take batteries accept any NeoForge energy item in the fuel slot and draw
  from it, in flight too. The range, rate and capacity are server settings.


- M8 effects polish, first slice, for the vehicles already ported (see docs/M8_PLAN.md): smoke
  fades in and out and grows smoothly; explosion puffs start as fire and cool to smoke over a
  soft additive fireball glow; muzzle flashes glow additively; machine-gun tracers burn at full
  brightness with a soft streak; flares burn at full brightness with a flickering glow; missile
  flames glow; downwash dust takes the ground's colour, with white spray over water. The client
  config's `effects.effectQuality` switches back to upstream's look (`CLASSIC`). Only vanilla
  pipeline state is used, so it draws the same on OpenGL and Vulkan.

- Using a drafting table opens upstream's recipe browser: lists of helicopters, planes,
  vehicles, tanks, items and ships, a search box, a recipe page with its description pictures
  and a vehicle's specifications, and Create, which makes the recipe from the inventory into the
  output slot (dropped if the screen closes with it there). Creative players need no items. A
  vehicle's page shows its model, drawn first as lines and then filled in, which a drag in the
  right half of the window turns (left button), zooms (right) or moves (middle).

- The hand-held FIM-92 Stinger and FGM-148 Javelin work: hold use to raise the sight, which
  zooms and locks on to an aircraft or flying mob (Stinger) or a ground target (Javelin) in view;
  press the attack key and the missile leaves once the lock is complete. A shot empties the
  launcher, and a missile item in the inventory reloads it two seconds after the sight comes
  down. **Z** cycles the zoom, **C** switches night vision after three seconds of aiming, and
  **X** switches the Javelin between direct and top attack (top attack needs a target 40 m
  away). The Stinger's scope and the Javelin's command launch unit, with its indicators, lock box
  and tracking lines, are upstream's. New server setting `stingerLockRange` (320 blocks) and
  client setting `lightWeaponAutoFire`. Network protocol is now 17.
- The GLTD (ground laser target designator) works: use the item on a block to set it up, then use
  the designator to sit behind it. Through its optics (zoom x1 to x10 with Z and X, night vision
  with C) the attack key calls in three A-10 strafing runs on the block in view; the designator
  then cools down for 22.5 seconds. Its HUD shows the zoom, the strike's readiness, positions and
  the range to the target, and other players see its head follow the operator's view with a green
  laser. Leave with sneak or the unmount key. Network protocol is now 16.
- The mine defuser has its upstream tooltip ("Sneak up to a mine and right-click it to defuse").
- The wrench repairs vehicles: hold use while looking at a vehicle within four blocks to restore
  10 HP a second, one durability each, with the vehicle's health shown under the crosshair. As a
  tool it mines metal blocks fast, hits like an iron tool (6 damage) and sometimes knocks iron or
  gunpowder out of what it hits; shift-use still changes a vehicle's skin.
- The laser rangefinder works: hold use for the scope (range to the block in view, charge,
  x1.2 to x10 zoom with Z and X), and the attack key spots for the player's team. V cycles the
  modes: players and vehicles, monsters and mobs, or a mark point on the block in view. Spots use
  one of ten charges; marks are free.
- Team spotting markers: teammates and spotted entities get coloured markers over them, and the
  team's mark point shows its distance and coordinates, with arrows at the screen edge when it is
  out of view. Targeting pods now spot or mark as upstream instead of only playing "ng". New
  server settings `spotting.enableEntityMarker`, `rangeFinderSpotDist` (400), `rangeFinderSpotTime`
  (15 s) and `rangeFinderConsume`, and client settings `hud.displayEntityMarker`,
  `entityMarkerSize` and `blockMarkerSize`. Network protocol is now 15.
- Used fuel cans refill by crafting them with coal or charcoal, and used rangefinders recharge
  with redstone, as upstream's `charge_fuel` and `charge_power_range_finder` recipes did. Each
  coal slot restores 100 fuel and each charcoal slot 75, up to a full can of 600; one item is used
  from each slot. The rangefinder takes exactly one redstone slot. Like upstream, both work in the
  inventory's 2×2 grid as well as a crafting table.
- Optional Just Enough Items (JEI) support. The fixed and content crafting recipes show in JEI's
  recipe and usage lookup as normal crafting recipes. JEI cannot list special recipes, so the fuel
  refill and rangefinder recharge show as example grids made by the real recipes (an empty can
  with one coal, with six coal, with one charcoal and with eight charcoal), and the fuel can and
  rangefinder get an information page. Checked with JEI 30.42.0.237 for 26.2 on a dedicated server and
  client; the mod still runs without JEI.

- UAV control now reaches 5,000 blocks from consoles and tablets, including vertical distance.
  A bounded camera window keeps distant UAVs ticking and delivers terrain/entities without
  moving the operator. Exit, range loss and operator loss release tickets and restore body
  terrain. Paired UAV positions save for bounded reconnect after chunk unload or world restart.
  Servers can lower `uav.controlRange` and adjust `uav.remoteViewDistance` (default four chunks).
  Network protocol is now 14; all players and the server need this build.
- UAV pairing devices and handheld tablets now work. Each station retains an ordered paired-UAV
  roster with selection, status/telemetry, paging and unpairing; deployment pairs UAVs automatically.
  Existing last-UAV saves migrate when their aircraft loads. Pairing devices select a UAV then
  register it at a station; Shift + device releases an idle aircraft for transfer.
- Tablets retain their identity and pairings in typed item components, open a temporary invisible
  controller and use the existing remote flight workflow. They accept small UAVs by default;
  server settings control full-size compatibility and pairing limits. Held-item loss, death and
  disconnect remove active tablet control. Temporary controllers never save; tablets have no
  deployment inventory. Protocol 13 requires matching client/host builds.

- Placed UAV stations and portable controllers now deploy UAV items, reconnect to their saved last
  UAV and provide remote flight through the existing main camera, HUD, weapons and aircraft menu.
  Operators remain at the console. Portable controllers accept small UAVs and have an animated
  lid; target drones launch autonomously with full fuel. Deployment checks loaded terrain and
  obstructions before consuming the item. Station inventory, offsets and last UAV save/load.
- UAV movement runs on the server in both authority modes. Control validates the station operator,
  aircraft identity, tracked terrain and bounded range; link loss clears held controls and throttle.
  Station preview remains NO SIGNAL plus telemetry. Pairing devices and tablets remain pending.
  Protocol 12 requires matching client/host builds; the development version remains 0.5.1.

- L toggles configured searchlights/headlights for the pilot or first crew seat. Movable lamps
  follow first-crew/pilot aim, fixed lamps follow the hull, and addon steering lamps follow wheel
  steering. Light covers animate, switch/retained aim save and synchronize, and destroyed vehicles
  cannot emit light. Additive beams use supported rendering on OpenGL/Vulkan; living entities in
  their cones receive a lightmap highlight. Protocol 11 requires matching client/host builds.
- Restore legacy alpha values for all fifteen stock lamps. A stock content revision refreshes
  existing 0.5.1 extracted definitions while preserving separate addon overrides.

- Helicopters with configured rope hooks deploy/retract ropes with K during a steady, level
  hover. Crew use Y after the deployment delay to descend; eligible non-player crew descend
  automatically at each hook's configured interval. Ropes and upright riders render through
  supported APIs, and the helicopter keeps server-controlled hover during deployment.
- Rappelling preserves the parent, hook and passenger across saves. Ground contact releases
  the rider without fall damage; retraction, lost parents and interrupted paths leave no dangling
  helper rides. Blocked exits and cancelled spawns/mounts retain the original seat. Protocol 10
  requires matching client/host builds for rope and descent entity data.

- The reusable Chain item now selects cargo first and its towing vehicle second. Click either
  linked endpoint to disconnect. Links preserve distinct endpoints and length across saves,
  render the stock chain model, pull and face their trailer towards the tug, and cap extension
  at length plus two blocks. Blocked pulls disconnect instead of crossing solid walls.
- Occupied trailers use server-controlled movement while linked; their drivers stay aboard.
  Linked vehicles cannot ram each other or enter racks. Protocol 9 requires matching client/host
  builds for the new chain entity data and trailer authority state.

- Aircraft racks now load containers and eligible vehicles with J; U releases the last occupied
  rack. Vehicle pilots can board nearby compatible carriers with J and leave with U. Stock rack
  names/kinds, RideRack mappings, entry ranges and seat/rack exclusion groups are enforced.
- Attached loads save in the carrier's passenger tree. Carried vehicles retain their drivers and
  cargo while movement physics, pilot motion/attitude updates and tank crushing are suspended.
  Rack poses follow carrier attitude through supported rendering and world transforms.
- High rack releases use type-3 transport parachutes with carrier velocity. Blocked exits and
  cancelled mounting/dismounting/spawning keep or restore the original attachment. Controls use
  protocol 8 and require matching client/host builds; existing control payload layouts are kept.

- Cargo containers now place from their item and provide a synchronized 54-slot inventory with
  shift-click transfers, slot-preserving saves and current NeoForge item automation capabilities.
  The stock model uses the shared OpenGL/Vulkan submission path. Server motion restores gravity,
  water/lava buoyancy, speed limits, container pushing and configurable snow/lily-pad clearing.
- Player melee breaks containers using upstream damage/decay rules. Survival drops an empty
  container plus its contents; creative drops contents only. Unload/dimension-removal reasons
  preserve inventory without world drops. The formerly empty upstream inventory-opening method
  now opens a standard six-row menu, checked at the current interaction distance.
- Network protocol 7 rejects older clients that cannot decode the new container entity data;
  clients and hosts must use the same build.

- Nearby fuel supply now runs on the server every ten ticks, including the stock infinite fuel
  truck, compatible fuel types, finite sources and the upstream consumption grace period.
- Addon-defined nearby vehicle repair runs every twenty ticks. Medical aircraft (including the
  stock NH90, US-2 and An-2) regenerate secondary-seat passengers while a pilot is aboard.
  These shared effects run in both movement authority modes; stock content remains unchanged.

- Tank/car block crushing and ramming now run on the server in both movement authority modes.
  Weight-specific editable block/tag lists replace the old block Material categories. Protected
  columns, one-in-eight block drops and sampled active extra hull regions follow upstream.
  Ramming includes speed/proximity damage, knockback, car reciprocal damage, pilot credit and
  occupant/recent-dismount exclusions. Heavy-tank damage remains disabled by default.

- M6 ground vehicles: all 12 stock tanks/cars and 18 placed vehicles now place, mount and
  save/load as real entities. Tank throttle, reverse, steering, held Space brake, mirrored wheel
  contacts and terrain tilt are connected. Placed guns respect content movement restrictions.
- Ground models include animated wheels, crawler links/rollers, independent turrets and recursive
  placed-gun parts. Ground camera positions, placed-gun zoom, engine sound and tank particles
  use the shared client paths. Stock models, definitions and loadouts are preserved.
- Reactive armor has per-entity synchronized and saved tile state. Projectile tracing includes
  active extra hull boxes; destroyed tiles stop receiving hits. Ground controls use protocol 6,
  so clients and servers must use matching builds.
- Ground regression coverage checks all stock definitions, driving/braking/reverse, terrain,
  control authorization, both authority modes, weapon use, reactive armor and stock assets.

- M6 ships: all five stock ships now place, mount, float, steer, stop and save/load as real
  vehicles. Shared fuel, damage, inventory, crew weapons, pickup/item data, HUD, cameras and
  engine sounds are connected. Stock loadouts are preserved; a test addon checks torpedo use.
- Ships and planes share the duplicated upstream movement, parts and particle implementations.
  `vehicles.allShipSpeed` scales ship speed; steering wheels animate about their content pivots.
  Ship models support on-demand and eager loading, resource reload and the common render pipeline.
- Ship controls use a new payload and protocol version 5, requiring matching client/server builds.
  Rider/vehicle checks and the shared movement authority checks remain in effect.

- Initial plane integration: real plane entities and item placement; upstream throttle/reverse,
  forward and VTOL movement, buoyancy, swept-wing speed limits and drone flight formulas;
  VTOL nozzle and folding/sweep-wing switches, gunner autopilot, shared seats, weapons, fuel,
  damage and saving/loading. The movement cadence is corrected to one step per tick in the M6 ship increment.
- Shared aircraft renderer and rider controls for helicopters and planes. Plane models draw
  nozzles, wings/pylons and tilt rotors/blades alongside common moving parts. Plane engine sound,
  mouse/stick controls, HUD and VTOL/autopilot variables are connected.
- Plane control payload (network protocol version 3), rider/vehicle checks, and movement bounds
  using each vehicle's speed limit.
- Plane movement comparisons against the pinned upstream code, payload codec coverage, stock
  model/texture checks, and GameTests for all 46 stock plane definitions, control authorization,
  VTOL takeoff, persistence and movement authority.
- Plane nozzle smoke (with greater spread near blocks), landing-gear block dust, damage smoke
  from rotor/fuselage positions, and content-defined water wakes. Particle positions, motion,
  colours and random-call ordering retain upstream's formulas; damage-trail history is per plane.
- Addon exhaust-flame models and numbered textures, with upstream's throttle scale, steering
  deflection, per-extraction animation delay and VTOL suppression. Geometry is extracted into
  render state and submitted through the vanilla emissive pipeline for OpenGL and Vulkan.
  Resource reload clears model/texture caches and shortened texture sequences remain safe.
- Destroyed planes use upstream's roll direction and client-side falling-attitude rules.
- Plane camera views now use the shared camera controller, including gunner zoom, night vision,
  TV-missile view and the content's third-person distance. H cycles gunner mode, additional
  content-defined camera positions and normal view in upstream's order. Camera-position choices
  stay local to the pilot and reset on dismount, destruction and content changes.
- Five plane camera GameTests cover mode cycling, blocked gunner conditions, per-viewer positions,
  save/load, lifecycle resets and authorized network controls.

- Parachute item deployment and real server-controlled parachute entities: hand chutes, delayed
  ejection-seat opening, transport chutes, forward steering, landing cleanup and the ejection
  seat's deep-water rescue boat. Gravity, drag, speed cap and opening thresholds retain upstream
  formulas. Chutes and their model type persist with the world.
- Plane ejection through Space for the pilot and first crew seat, inherited aircraft motion and
  rotated launch impulse, canopy opening, and automatic rescue of a non-player first crew member
  when a second reserve is available. Another player is never forced out with the pilot.
- Aircraft menu reserve slots, shift-click transfers and independent parachute storage. Existing
  fuel/trunk save indices are unchanged. Network protocol version 4 requires matching builds for
  the changed menu layout. Y now reaches crew seats and deploys an eligible transport chute.
- Both parachute models use supported render submission with model-sized culling bounds. The
  stock `parachute2.mqo` cannot supply the part names upstream asks for; that asset defect remains
  unchanged and is documented in KNOWN_ISSUES.md. Correctly grouped addon models render.

### Fixed

- Hide the local rider in the primary first-person marker camera so placed-gun views are clear.
  Honor pilot `HideRiders` through the shared rider render state.

- Vehicle keys now work while Ctrl/Shift/Alt is held. NeoForge previously suppressed the riding
  key context, blocking Left Ctrl freelook and Ctrl+C/I/F across helicopters, planes and ships.
  This also restores independent pilot turret aiming through freelook. Seat shortcuts now show
  their required Ctrl modifier for all riders and remain visible from the pilot seat; Ctrl+C's
  hint identifies NPC gunner status separately from H's camera/gunner view.

- Corrected planes from two movement steps to upstream's one per tick, including movement bounds.
  Added entity cadence, conventional/VTOL takeoff-turn-landing, and plane weapon integration checks.

- Parachute deployment no longer consumes an item when refused on the ground or while mounted.
  Ejection consumes the reserve slot that supplied the chute, correcting upstream's mismatched
  read/write slot indices. Pilot Y hints now say Dismount instead of promising a crew drop.
- Changing a vehicle's content type before its crew seats spawn no longer crashes on a missing seat.

M6 is still in progress. Automatic mob drops, remaining equipment and full
manual flight/weapon/multiplayer checks
are still outstanding, alongside the other M6 vehicle types and systems. Stock model references
without geometry are recorded in `src/test/resources/parity/plane-model-gaps.txt`; they were
checked against the pinned upstream models, with line-ending normalization only.

## [0.5.1] - unreleased (M5 fixes)

### Added

- The aircraft menu lists the vehicle's weapons with their ammunition and a reload button for each,
  as upstream's aircraft GUI did. A reload costs the weapon's round items (for Hydra rockets, 3 iron
  ingots and 3 gunpowder) and fills the reserve. Ammunition could not be loaded at all in 0.5.0.

### Fixed

- The fuel slot no longer takes empty fuel cans, which look like full ones and did not refuel.

## [0.5.0] - unreleased (M5: weapons)

### Added

- **Weapons.** Every seat fires the weapons its content gives it, with upstream's weapon sets:
  magazine and reserve ammunition, reloads (automatic and with R), heat, cooldown, recoil, barrel
  spin, weapon modes (X), group sharing between seats and gunner turret limits. All 18 weapon types
  are ported: `machinegun1`, `machinegun2`, `buckshot`, `railgun`, `rocket`, `mkrocket`,
  `tvmissile`, `aamissile`, `atmissile`, `asmissile`, `torpedo`, `bomb` (with bomblets and mines),
  `cas`, `dummy`, `smoke`, `dispenser`, `targetingpod` and `laser`.
- **Projectiles** as entities with upstream's flight: gravity in air and water, piercing, delay,
  time and proximity fuses, airburst, spreaders, splash, bounce, block hits
  (`weapons.bulletBreakableBlocks`), entity hits with damage factors, potion effects and their own
  explosions. Bullets, rockets, AA, AT, AS and TV missiles, bombs, torpedoes, marker rockets,
  dispensed items and the close air support A-10 are drawn with their bullet models, tints and
  missile flames.
- **Guidance**: lock-on with upstream's seeker angle, range, target kinds, ground clutter and lock
  tones; homing per tick; TV missiles steered through their own camera with its noise overlay; laser
  guidance from the targeting pod.
- **Countermeasures**: flares with upstream's dispensing patterns, chaff and the active protection
  system (V), the missile detector's warning, and their cooldowns and sounds.
- **McHeli's explosion** (decision D18): upstream's block ray march, entity damage and radius,
  through-wall damage, fire, damage factors and hit markers, with its own smoke, flash, splash and
  block dust particles (or vanilla's with `effects.defaultExplosionParticle`). Crashes and destroyed
  helicopters use it in place of M3's vanilla explosion.
- **Client effects**: tracers, laser beams, ejected cartridges (`effects.aliveTimeOfCartridge`),
  muzzle smoke, recoil of the view, and the weapon parts of helicopter models animated (turret yaw
  and pitch, recoil, barrel spin, missiles on rails disappearing as they are fired, weapon bays).
- **HUD weapon values**: weapon name, ammunition, reserve, reload, heat, lock, sight type, mortar
  and airburst distances and the barrel direction, the hit marker (`hud.hitMarkColor`), CCIP for
  bombs, and the flare, weapon and weapon-mode key hints.
- **Input**: fire (left mouse), switch weapon (G, middle mouse, or the mouse wheel with
  `controls.switchWeaponWithMouseWheel`), weapon mode (X), reload (R), flare, chaff and APS (V),
  airburst distance reset (right mouse). Vanilla attack and use are held back on a weapon seat
  (decision D21).
- **Ammunition** is saved with the vehicle and supplied from round items through the aircraft
  menu; `weapons.infinityAmmo` and creative players never run out.
- **Throwables**: grenades and smoke grenades are thrown by holding use, bounce, and explode or
  smoke when their fuse runs out; dispensers throw them.
- Config: `weapons.infinityAmmo`, `weapons.hurtResistantTime`, `weapons.bulletBreakableBlocks`,
  the `weapons.damageVs…` and `…ByExternal` lists (decision D19), `explosions.destroyBlock`,
  `explosions.flamingBlock`, `effects.defaultExplosionParticle`, `effects.aliveTimeOfCartridge`,
  `hud.hitMarkColor`, `controls.switchWeaponWithMouseWheel`.
- Tests: damage scaling, explosion maths and weapon sets checked tick by tick against upstream
  oracles, round trips for the new payloads, every stock weapon and vehicle weapon set resolving,
  and GameTests in which every weapon type fires and hits, flares decoy a heat seeker, ammunition
  is used, reloaded and saved, infinite ammunition stays full, and a destroyed helicopter explodes.

### Fixed

- The close air support A-10 and its bombs now belong to the shooter (upstream never set it,
  decision D20).
- Projectiles below y -10 are no longer dropped; the limit follows 26.2's world floor.

## [0.4.0] - unreleased (M4: models, HUD and camera)

### Added

- **Helicopters are drawn with their models.** OBJ and MQO parsers with upstream's rules
  (`$part` groups, MQO smoothing within the `facet` angle and object merging), and a model
  manager with upstream's on-demand loading on a background thread (`render.onDemandModels`,
  decision D16) or loading during resource reload, which unloads models unused for
  `render.onDemandModelLifetimeSeconds`.
  The renderer draws the body, rotors and blades (both `OldRenderMethod`s), canopies, hatches,
  landing gear, throttle parts, rotating parts, camera parts, weapon bays and weapon parts at rest,
  with the content's texture, upstream's 0.75 grey (dark when destroyed) and optional smooth
  shading (`render.smoothShading`). It uses vanilla's entity render types only, so it works on
  OpenGL and Vulkan. The M3 stand-in box renderer is gone (decision D11).
- **Crew tilt**: riders lean with the helicopter's pitch and roll (`render.tiltRiders`,
  decision D17). `debug.testMode` draws the hit boxes and seats.
- **Engine and rotor sound** that follows the throttle, with upstream's pitch and volume.
- **HUD**: upstream's HUD language, drawn on a GUI layer above vanilla's: `If`/`Endif`, `Exit`,
  `Call`, `Color`, `DrawString`/`DrawCenteredString` with every format value, `DrawTexture`,
  `DrawRect`, `DrawLine`, `DrawLineStipple` (as dashes, decision D15), `DrawGraduation` (heading
  tape and the three pitch ladders), `CameraRot`, and the entity and enemy radars. Expressions run
  on a port of Aviator's semantics (decision D4). Every HUD variable of upstream is filled (weapon
  values keep upstream's "no weapon" values until M5). The pilot sees seat 0's HUD, and seat 1's
  (`heli_gnr` on most helicopters) in gunner mode; crew seats see their own. Key hints are drawn
  next to it (`hud.hideKeyHints`), the HUD can show in third person (`hud.displayInThirdPerson`) and
  scale to the screen (`hud.autoScale`), and `debug.testMode` lists the HUD variables. Vanilla's
  crosshair, hotbar, health, armour, food, air and boss bars are hidden while riding, as upstream.
- **Gunner camera**: in gunner mode (H) the view moves to the content's camera position and turns
  with the helicopter, through a client-only camera entity (decision D14); the gunner's pitch stays
  within the seat's range. Crew gunner mode works too.
- **Zoom** (Z in gunner mode): doubles up to the content's `CameraZoom`, then back to 1, and
  narrows the field of view.
- **Camera modes** (C): normal and night vision for helicopters with `NightVision`. The server
  gives the viewer upstream's short night-vision effect while in that mode; the client draws
  upstream's night-vision shader as a vanilla post effect, and the green noise overlay.
- **Third-person distance**: the content's `ThirdPersonDist` while riding; Page Up and Page Down
  change it in steps of 4 when the server allows it (SERVER `camera.enableCamDistChange`, synced).
- **Pilot's eye**: in first person the pilot's eye moves with the helicopter's attitude, as
  upstream's camera setup does, so the view stays put in the cockpit when the nose goes down;
  while hovering or in free look it leans with the camera roll only, as upstream.
- Config: CLIENT `render.*`, `hud.*` and `debug.testMode`; SERVER `camera.enableCamDistChange`.
  Keys: camera mode (C), camera distance up and down (Page Up, Page Down).
- Tests:
  - JUnit: `HudExpressionTest` (every expression of every stock HUD against Aviator 5.4.3, a
    test-only oracle), `ModelParserTest` (every stock model against upstream's parsers copied into
    the tests), `HudInterpreterTest` (every stock HUD against a transcription of upstream's HUD
    code), `PartTransformTest` (every moving part of every stock vehicle against upstream's GL call
    sequences), `AllHelicopterModelsTest` (every stock helicopter's models and textures) and
    `AircraftCameraClientTest`.
  - GameTests (29): adds `CameraModeGameTests` (night vision given, kept and removed; refused
    without `NightVision`; camera modes per seat; reset when the vehicle is destroyed).

### Changed from upstream

- A HUD line that fails is logged once and skipped (a failing `If` counts as false); upstream
  stopped drawing the HUD.
- The camera-mode key only works where the content allows night vision, and switching back to
  normal reaches the server, so the night-vision effect ends; upstream never sent the switch back.
- See [KNOWN_ISSUES.md](KNOWN_ISSUES.md), items 30 to 41, for the rest.

## [0.3.0] - unreleased (M3: placing and flying helicopters)

### Added

- **Helicopters can be placed, boarded, flown, landed and picked up.** All 34 stock helicopters
  (and addon helicopters) share one entity, as upstream (decision D10); the AH-64 is the test
  reference. The `helicopter` and `seat` entity types are real; the other entity types stay
  placeholders. A helicopter is named after its item (in the menu title and death messages).
- **Placement** from the helicopter's item, as upstream: a 5-block ray trace, the helicopter one
  block above the clicked block, yaw snapped to a quarter turn, refused when blocks are in the way,
  creative-only content refused in survival, target drones refused ("Please use the UAV station"),
  the `placeableOnSpongeOnly` rules, and the item used up outside creative. Dispensers place
  helicopters two blocks in front of them.
- **Item data**: fuel, damage and the destroy command move between the item and the helicopter
  (new `aircraft_fuel` and `aircraft_command` data components), gated by `itemFuel` and
  `itemDamage`.
- **Riding**: use to board the pilot's seat, then the free crew seats; the first use opens a closed
  canopy. Seat switching (free-look key with the GUI or extra key), upstream's dismount positions,
  the unmount key (Y), and no fall damage for the crew when landing.
- **Flight**: upstream's helicopter physics (`HeliFlightModel`): manual throttle, the auto
  throttle-down band, hovering mode, rotor spin, lift, gravity and water, the speed limit and
  `currentSpeed`, ground friction and on-ground pitch, auto-levelling, server rotation limits,
  hovering drift, destroyed spin and fall. Fold blades (F) and taxiing with folded blades. Canopy,
  landing gear and hatch state.
- **Mouse flight**: upstream's mouse stick, `invertMouse` and `mouseSensitivity`, the stick and
  flight-sim modes, and both rotation paths (Euler and the quaternion path of
  `experimentalQuaternionRotation`). The pilot's view rolls with the helicopter and the hand is
  hidden while piloting.
- **Keys** in the Controls screen under "McHeli CE (26.2 Port)", with upstream's defaults: W/S
  throttle, A/D strafe, Space hovering, H gunner mode, Y unmount, F extra (fold blades), Left Ctrl
  free look, I menu, Z hatch, B landing gear. They only act while riding a McHeli vehicle.
- **Fuel**: consumption every 20 ticks with upstream's rates, `infinityFuel`, creative crews fly
  free, and an empty tank means the throttle runs down. Refuelling from fuel cans.
- **Aircraft menu** (sneak-use or the GUI key, decision D12): the fuel input and output slots and
  the trunk (`InventorySize`).
- **Damage**: upstream's armour maths and bounding-box damage factors, lava and fire rules,
  invulnerable content, punching to pick up (`preventingBroken`, `breakableOnlyPickaxe`,
  `dropItemInCreativeMode`, sneaking in creative), destruction with a vanilla explosion of
  upstream's power 2 (decision D13), crash explosions, ground-collision and submersion damage,
  auto-repair and the destroy command.
- **Authority** (decision D2): by default the pilot's client flies its helicopter and the server
  checks the moves (horizontal speed at most 1.5 × the helicopter's speed, climb at most one block
  per tick, logged at most once a minute). `vehicles.serverAuthoritativeMovement` switches to
  upstream's server-side flight.
- **Networking**: payloads for helicopter controls, attitude, motion, seat control, status
  request and response, and server settings, with CE's encodings (enum ordinals as bytes, the
  14 control switches as a short).
- A **stand-in renderer** that draws the helicopter's boxes (the main box, the content bounding
  boxes rotated with the helicopter, and a nose line) until M4 draws models (decision D11).
- Config: COMMON `vehicles.placeableOnSpongeOnly`, `itemDamage`, `itemFuel`, `infinityFuel`,
  `preventingBroken`, `dropItemInCreativeMode`, `breakableOnlyPickaxe`, `allHeliSpeed`,
  `autoThrottleDownHeli`, `experimentalQuaternionRotation`, `despawnCount`,
  `killPassengersWhenDestroyed`, `autoRepairHP`, `collisionDestroyBlock`; SERVER
  `rotationLimits.*` (synced to clients); CLIENT `mouse.*`. `vehicles.serverAuthoritativeMovement`
  now works.
- Tests:
  - JUnit: `HeliFlightModelTest` runs the port's flight model and a line-for-line transcription of
    upstream's helicopter code side by side for 13 scenarios and requires the same state every
    tick (tolerance 1e-9); `AircraftAttitudeTest`, `GroundProbeTest`, `AircraftFuelTest`,
    `AircraftDamageTest` and `PayloadCodecTest` (including the switch bits against CE's).
  - GameTests (24, run by `./gradlew runGameTestServer` and in CI): placement, dispensers,
    riding, seat switching, unmounting, take-off, hover and landing in both authority modes and at
    negative coordinates, crash explosions, fuel use and refuelling, pick-up, item data, save and
    load, and every stock helicopter created from its item.

### Changed from upstream

- The ground probe (`MCH_Lib.getBlockY`) works over 26.2's whole build height and rounds
  correctly at negative coordinates; upstream treated everything outside y 0 to 255 as air and
  probed one block off at negative coordinates.
- Placement centres the helicopter on the block in double precision; upstream's float maths put it
  on a block edge past about 8 million blocks from the origin.
- Seat switching wraps correctly from the last seat and skips exclusion seats.
- The crew's unmount key lets a crew member out; upstream sent it but only acted on the pilot.

## [0.2.0] - unreleased (M2: content and registries)

### Added

- **Content system**, ported from upstream's `helper/info` and `helper/addon`: the YAML parsers,
  the hand-ported TXT parser, the content model, the YAML emitter and the pack loader. All 372
  stock definitions load (19 HUDs, 234 weapons, 34 helicopters, 46 planes, 5 ships, 12 tanks,
  18 vehicles, 4 throwables), on both sides.
- **Addon packs** in `<game dir>/mcheli_addons/`: folders, `.zip` and `.jar` files with upstream's
  `pack.mcmeta` `addon` block and loader versions 1 and 2. The stock pack is extracted to
  `mcheli_addons/default/`. Packs load stock pack first, then by name, and a definition replaces
  an earlier one with the same name (an override without an author keeps the replaced author).
- Addon packs are also added as always-on resource packs (and as data packs if they have a `data`
  folder), so addon textures, models and sounds load. The port supplies the pack metadata, so old
  `pack_format: 3` packs load without an "incompatible" warning.
- **Items**: upstream's 21 fixed items (gunner spawners, rangefinder, wrench, fuel, GLTD, chain,
  parachute, container, UAV station and portable controller, UAV pairing device and tablet, mine
  defuser, internal item, FIM-92 and FGM-148 with their missiles), with upstream's stack sizes and
  durabilities, and one item per vehicle (durability = `MaxHP`) and throwable (stack size from
  content). IDs are upstream's names in the `mcheli_ce_port` namespace, such as
  `mcheli_ce_port:ah-64`. The items do nothing yet.
- **Blocks**: drafting table (with a lit variant; sneak-use switches the lamp, as upstream), aircraft
  charger and wingman marker block, with loot tables and pickaxe mining tags.
- **Entity types**: upstream's entity types with their sizes, tracking ranges (in whole chunks) and
  update intervals. They are inert placeholders that cannot be summoned until each entity is
  ported.
- **Seven creative tabs** as upstream (Items, Helicopters, Planes, Ships, Tanks, Vehicles, Recipe
  Items), sorted as upstream. Tab icons are set in the CLIENT config (`creativeTabIcons.*`,
  upstream's `CreativeTabIcon*` keys) with upstream's defaults.
- **Generated resources** (upstream's `mcheli_addons/generated/`, now in memory): an item model for
  every content item using `mcheli:items/<name>`, lang entries from `DisplayName` in every language
  the content names, and `sounds.json` for every sound event the content uses.
- English, Japanese and Chinese names for the fixed items, blocks and tabs, from upstream's lang
  files.
- **Recipes**: the 16 recipes of the fixed items (Forge ore dictionary names became common tags such
  as `c:ingots/iron`), and content recipes (`AddRecipe`, `AddShapelessRecipe`, YAML `Recepie`)
  generated into an always-on data pack. 1.12 item names and metadata are mapped to current items
  (`wool, 14` is `red_wool`).
- **`/mcheli dumpyaml [folder]`** (operators): writes all content that was not loaded from YAML
  to `<game dir>/<folder>` (default `mcheli_yaml_dump`) as YAML.
- Placeholder icons for the UAV pairing device, UAV tablet and mine defuser, which have no texture
  upstream.
- Data generation (`./gradlew runData`) for the fixed items' and blocks' models, block states, lang
  files, recipes, loot tables and tags.
- Tests (JUnit, with Minecraft loaded): every stock definition parsed from YAML and from upstream's
  legacy TXT files and compared field by field (known upstream differences are listed in
  `src/test/resources/parity/known-differences.txt`); TXT to YAML to TXT round trip; the parser
  fixes below; addon override and stock extraction; every legacy stock recipe converted and decoded
  with the game's recipe codec; every item has a model, its textures and a name; every stock sound
  event has a sound file; `/mcheli dumpyaml`.
- SnakeYAML 2.5 is bundled with Jar-in-Jar, as upstream shades it.

### Changed from upstream

- Upstream parser bugs listed in its wiki are fixed (decision D5):
  - `EnableVtol` in its map form applies its settings; upstream only switched VTOL on.
  - Tanks' `WeightedCenterZ` applies; upstream computed it and dropped it.
  - Throwables' nested `Smoke: Velocity:` settings apply; upstream never read them.
  - Bounding boxes keep their `Name`.
  - `RotBarrel` works as an alias of `BarrelRot`, as the wiki documents.
- Every definition is validated exactly once. Upstream validated YAML content twice, which added
  entity racks to vehicles' seat counts and made YAML throwable times 400 times too long.
- YAML damage factors keep their decimals, as in TXT, and name entity types by ID instead of Java
  classes. Potion effects are stored by name (TXT's 1.12 numeric IDs are mapped).
- Sound events defined with an explicit sound list keep it; upstream replaced it with the default
  file for weapon sounds. Explicit `Volume` and `Pitch` are limited to 0 to 1; upstream swapped the
  limits, which forced both to 1. `iron_curtain`, `aps_activate` and `aps_deactivate` play their
  sound files.
- The YAML emitter (used by `/mcheli dumpyaml`) writes everything the TXT format can hold; upstream's
  lost damage factors, paint textures, rack angles and more. Two compatible YAML additions make
  that possible: `RideRack` may list several racks, and `ExtraHuds` holds HUDs past the last seat.
- Only the content definitions of the stock pack are extracted (upstream copied about 90 MB of
  assets), and they are extracted again when the mod version changes.
- Content recipes that name an item with no current equivalent are skipped and logged; upstream left
  that slot empty.
- Sound events named in content whose file exists in neither the mod nor an addon pack are left out
  of `sounds.json`; upstream registered them and played nothing.
- `/mcheli dumpyaml` only accepts a folder name inside the game folder; upstream accepted any path.

## [0.1.0] - unreleased (M1: empty mod boots)

### Added

- Project set up from NeoForge's official MDK-26.2-ModDevGradle template (commit `3acf94e`):
  ModDevGradle 2.0.148, Gradle 9.2.1, Java 25 toolchain, NeoForge pinned to 26.2.0.88.
- Mod metadata: mod ID `mcheli_ce_port`, display name "McHeli CE (26.2 Port)", GPLv3, upstream
  authors and credits, and the unofficial-fork notice. The licence text ships inside the jar.
- Empty registers for items, blocks, block entities, entity types, menus, data components, sounds,
  particles and creative tabs, ready for content from M2 on.
- Config skeleton with one file per side, editable in game from the Mods screen:
  - COMMON `general.debugLog`: replaces upstream's hidden `McHeliOutputDebugLog` switch. For now it
    logs a summary of registered entries at startup.
  - SERVER `vehicles.serverAuthoritativeMovement`: switches back to upstream's server-authoritative
    vehicle movement (decision D2). No effect until vehicles are ported.
  - CLIENT `privacy.allowScreenCapture`: opt-in for `/mcheli sendss` (decision D7). No effect until
    the command is ported.
- Startup log line with the version and the unofficial-port notice.
- `./gradlew runServer` passes terminal input to the development server, so console commands such as
  `stop` work.
