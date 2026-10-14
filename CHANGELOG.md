# Changelog

All notable changes to McHeli CE (26.2 Port), newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Versions stay below 1.0.0 until the port is feature-complete, and each milestone in
[PORTING_PLAN.md](PORTING_PLAN.md) (section 6) gets its own minor version. The port is based on
McHeli CE 1.5.1 (`master` @ `cbaff7f`). Where it deliberately differs from upstream, the difference is
listed in [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

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
