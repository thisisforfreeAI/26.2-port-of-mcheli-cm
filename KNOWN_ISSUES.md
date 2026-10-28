# Known issues

What McHeli CE (26.2 Port) does not do yet, and where it deliberately differs from upstream
McHeli CE 1.5.1. This file is updated with every milestone (PORTING_PLAN.md, section 6).

## Not ported yet

The 0.5.1 baseline (M5) supports helicopters with flight, models, HUD, cameras and weapons.
The current M6 development work also adds planes, ships, tanks and ground vehicles, and every
M6 stage in `docs/M6_PLAN.md` is now implemented; what remains is the broader hands-on gameplay
pass below. Plane effects and destroyed-plane visual motion are now implemented;
plane camera-position switching and the shared gunner camera are connected. Parachutes, plane
ejection and transport-seat bailout are implemented.
Ship placement, water movement, steering, crew weapons and model submission are implemented.
Tank/ground-vehicle placement, driving, wheel contacts, tracks, rollers, independent weapons
and reactive armor state are implemented. Ground crushing/ramming, nearby fuel/ammunition supply,
vehicle repair and medical crew effects are implemented. Cargo containers also place, float,
save items and open a 54-slot inventory. Rack loading/release, vehicle self-boarding, tow chains, ropes and rappelling
and searchlights are implemented. UAV stations and portable controllers deploy stock UAVs and
provide nearby remote piloting, saved pairing lists and station inventory. Pairing devices transfer
idle UAVs between controllers, and handheld tablets control paired UAVs. Gunner NPCs, mobs in
seats, the GLTD, hand-held Stinger and Javelin, the wrench, rangefinder, drafting table, aircraft
charger, teams and the stage 8 combat systems are implemented too.
Automated conventional/VTOL flight sequences and plane weapons in both authority modes pass;
broader gameplay and hardware checks remain pending.

| Area | Planned milestone |
|---|---|
| Longer hands-on play across the stock vehicles (OpenGL) | M6 |

Until then:

- Rack and searchlight controls and hints are implemented.
- UAV control reaches 5,000 blocks by default (3D distance), bounded by `uav.controlRange` and
  any smaller aircraft-specific range. Active control keeps a bounded camera area loaded and
  streams its chunks/entities to the pilot. `uav.remoteViewDistance` defaults to four chunks
  (2..8), limited by server/client view distance. It does not load the intervening corridor.
  Reconnect loads a paired UAV at its saved location in the same dimension. An older UAV must
  have loaded once after this update for its location to be indexed.
  Range loss, leaving the station, death or destruction ends control and clears held inputs and
  throttle. Active control sessions are not restored after loading; reconnect with Connect last.
  Target drones launch autonomously and cannot be remotely piloted. Normal UAVs need fuel.
- One controller owns each UAV pairing. Release an idle UAV with Shift + pairing device before
  transferring it. A tablet accepts small UAVs unless the server enables `uav.tabletControlNormalUav`.
  `uav.maxPairedUavs` defaults to five (0..64). Copied tablets share their copied pairing identity;
  simultaneous control is still excluded by the active UAV link. An unloaded or destroyed
  controller cannot remotely release a loaded UAV; the physical pairing device can release it.
- Tank movement uses the main hull collision box plus wheel terrain probes. A tank's extra hull
  boxes are solid for players, mobs and other vehicles, as upstream's were, but the tank's own
  movement still checks terrain with the main box only, so a long barrel or hull can poke into
  walls. Each extra box is solid as the upright box around it, slightly larger when the tank turns
  diagonally; aircraft keep their extra boxes for hits only. Ground attitude is
  updated at a fixed simulation tick; it is not tied to the client's frame rate.
- The aircraft menu has the fuel slots, the trunk, the weapon list with its reload buttons and the
  command field with an Options button, on a plain container background rather than upstream's
  `gui.png`. Supported planes also expose parachute reserves.
- Fuel comes from fuel cans, fluid containers in the fuel slot (such as the fuel bucket), pipes
  and tanks through the vehicle's fluid capability, or nearby supply vehicles. Electric vehicles take
  energy from the charger and from batteries (any item with NeoForge's item energy capability) in
  the fuel slot. Upstream's GregTech EU and HBM HE energy systems are dropped, so the
  `EnergySystem` and ratio settings are gone; the port registers no battery item, as upstream.

## Deliberate differences from upstream

These follow from how Minecraft 26.2 works or from decisions approved in PORTING_PLAN.md
(sections 8 and 10). They apply as each feature is ported.

1. 1.12.2 worlds and items are not migrated (different mod ID, item flattening, new save format).
2. The UAV station's small preview shows telemetry only. Upstream rendered the whole world a
   second time from the UAV into its own framebuffer. In 26.2 the level renderer always draws into
   the game's main render target and keeps one section-occlusion graph and one extracted level
   state per frame, all built for the main camera, so a second view would mean replacing vanilla's
   frame pipeline and rebuilding visibility every frame. Remote flight uses the supported main
   camera at the UAV instead.
   The handheld tablet controls UAVs deployed from placed stations; it has no deployment inventory.
   Its temporary seat is invisible and is never saved. Pairings update the item immediately, and
   changing/dropping the held tablet ends control. Reopen the tablet after reconnecting or loading.
3. Stippled HUD lines are drawn as dashes, so they may not match upstream pixel for pixel.
4. Recipe and ammo references to 1.12 items that cannot be mapped to a 26.2 item are skipped, with a
   log line.
5. Config entries that matched Java class-name substrings (`DamageVs…`, `IgnoreBulletHit`) match
   entity type IDs instead.
6. GregTech EU charging is removed (Forge Energy only). The One Probe and Trajectory Preview
   integrations are stubs.
7. NTM Next (`hbm`) weapon payloads follow upstream's HBM integration against NTM Next 1.0.3:
   fire, mist, chemical, mini-nuke, nuke, VNT and legacy NT (see `docs/NTM_NEXT_WEAPONS.md`). NTM Next
   removed `ExplosionNT`, mini-nuke attributes and the configurable large explosion effect, so NT
   payloads are rebuilt from VNT parts, mini-nuke attributes are ignored and VNT effects use NTM
   Next's nearest fixed size. With NTM Next, 14 stock planes also carry NTM bombs, which upstream
   never had; without it those bombs are hidden and other payload weapons explode normally. On Mesa's software Vulkan driver, CrankShaft (NTM Next's renderer) crashed the
   client when NTM effects appeared, also without McHeli; OpenGL is the supported backend.
8. Vehicles past the render distance are drawn full size and without fog in the normal entity pass,
   not in a second pass with upstream's own 3,000-block projection, so they disappear past vanilla's
   far plane (four times the render distance or the cloud range, whichever is larger).
9. The pilot's client simulates its own vehicle (decision D2). Servers can switch back to upstream's
   server-authoritative model with `vehicles.serverAuthoritativeMovement`. UAV motion always runs
   on the server because its pilot remains physically seated at the station.
10. `/mcheli sendss` screen capture only works for players who opt in with
    `privacy.allowScreenCapture`, and they get a chat notice whenever it happens (decision D7).
11. Content-parser bugs documented in upstream's wiki are fixed (decision D5): the map form of
    `EnableVtol`, `Smoke.Velocity`, `BoundingBoxes.Name`, `WeightedCenterZ` and `RotBarrel`. Addon
    content that relied on the old behaviour may load differently.
12. Item, block and entity IDs are in the `mcheli_ce_port` namespace (`mcheli_ce_port:ah-64`).
    Content names with characters that IDs cannot hold (1.12 allowed spaces, for example) get `_`
    in their item ID.
13. The stock pack in `mcheli_addons/default/` is extracted again when the mod version changes, so
    changes made there are lost; put them in a separate pack. Upstream extracted it once.
14. Addon packs are always-on resource packs; they cannot be switched off in the resource pack
    screen. Only `assets` at the root of a pack count as resources, as upstream.
15. Upstream's custom item content type (the `item` content folder) is not ported: its parsers were
    stubs that never produced items, and no stock or known addon content uses it. So the Recipe
    Items tab stays empty, and Minecraft hides empty tabs.
16. A creative tab whose configured icon item does not exist shows its first item; upstream cycled
    through the tab's items.
17. Content item names come from the content files only. Upstream's static lang files also named
    content items, but its generated names took precedence, except for six Chinese names (the M18
    smoke grenades, the fuel truck and the ammo box), which the port's `zh_cn` file keeps.
18. The wingman marker block stores its marker type in its block state and its ID, parent base and
    parking heading in a block entity; upstream kept all four in its block entity.
19. Sound files that only a resource pack (not the mod or an addon pack) adds do not count when the
    port decides between `<name>.ogg` and `<name>_snd.ogg` for a content sound event. A resource
    pack can still define such events in its own `sounds.json`.
20. Helicopters move with vanilla's entity movement and collisions (`Entity#move`) instead of 1.12's.
    Collision boxes and step-up differ slightly at block edges.
21. The ground probe (upstream's `MCH_Lib.getBlockY`) covers 26.2's whole build height and rounds
    correctly at negative coordinates. Upstream treated everything outside y 0 to 255 as air and
    probed one block off at negative x and z, so helicopters there did not level out or find the
    ground (M3_PLAN.md section 3.3).
22. Placement centres a helicopter on the clicked block in double precision. Upstream used float
    maths, which put it on a block edge far from the world origin.
23. Seat switching wraps from the last seat back to seat 1 and skips exclusion seats. The unmount
    key works for the crew too; upstream only acted on it for the pilot (the crew got out with
    sneak, which still works).
24. Crew seats are passengers of the helicopter, so vanilla saves, tracks and positions them with
    it; upstream's seats were separate entities that found their helicopter again by UUID.
25. The content bounding boxes are NeoForge part entities with an axis-aligned box that encloses
    the rotated box. Hits, use and damage factors work on them as upstream, but players cannot stand
    on them, and a hit near a corner of a strongly rotated box can count that box.
26. In the default authority mode (decision D2) the pilot's client sends its helicopter's motion and
    throttle in a new `AircraftMotionPayload`, so the server can check the moves and other players
    see the throttle. Other players get the yaw and pitch through vanilla's entity tracking and the
    roll through synced data; upstream relayed all three in its rotation packet.
27. Hovering mode is synced to clients and saved with the helicopter; upstream kept it on the
    server and the pilot's client only.
28. The aircraft menu has two fuel slots (in and out); upstream's had three fuel slots. The trunk
    and the fuel slots share one screen; upstream opened the trunk separately.
29. The mouse turns the helicopter through `Entity#onPassengerTurned`, which sees every frame's
    raw mouse movement, instead of upstream's tick handler; free look follows vanilla's
    "invert mouse" option as well as `mouse.invertMouse`.
30. A HUD line that fails (an expression error, for example) is logged once and skipped, and an
    `If` whose condition fails counts as false. Upstream threw, which stopped the rest of the HUD
    from drawing.
31. HUD lines are drawn as quads one GUI pixel wide with square ends, and points as 2×2 squares,
    through vanilla's GUI pipelines; upstream drew GL lines `scaleFactor` pixels wide.
    Angled lines may look slightly different.
32. The HUD's `altitude` probe covers 26.2's whole build height and rounds correctly at negative
    coordinates, like the ground probe (item 21). Upstream probed y 0 to 256 only.
33. The HUD's `MC_VER` string is the running Minecraft version; upstream always showed `1.12.2`.
34. The camera-mode key only switches on helicopters whose content has `NightVision`; upstream
    showed the key hint only there but switched on any helicopter. Switching back to normal
    reaches the server, so the night-vision effect ends; upstream never sent it, so the server
    kept giving the effect.
35. A crew member getting out resets that seat's camera mode. Upstream looked the seat up after
    the rider had left, so the mode stayed for the next rider.
36. The third-person distance (`ThirdPersonDist`, Page Up and Page Down) is pulled in by blocks
    behind the camera over its whole length; upstream only pulled in vanilla's first 4 blocks and
    put the rest behind them regardless.
37. In camera view the rider's own name tag is hidden; the rider's model is drawn, as upstream,
    since the view comes from another entity. Switching between first and third person clears
    post effects in 26.2; the port puts night vision back on the next tick.
38. OBJ faces with four vertices are split into two triangles, and a vertex without a normal gets
    its face's normal. Upstream's model path drew quads as triangles (scrambling those faces) and
    gave such vertices a zero normal.
39. Models are drawn with vanilla's translucent entity render type: translucent faces are sorted,
    and pixels with alpha below 0.1 are discarded (upstream: below 0.001, unsorted).
40. Helicopters do not get a glowing outline (the Glowing effect, spectral arrows); vanilla's
    outline pass does not cover custom model geometry.
41. Vanilla's vignette still darkens the screen edges while riding; upstream hid it. In 26.2 it
    shares a layer with the pumpkin, spyglass and portal overlays, which should stay.
42. Weapon input reaches the server in a new `WeaponControlPayload` used by every vehicle kind;
    upstream folded it into each vehicle's control packet. The server takes the rider's predicted
    shot position only within 16 blocks of the vehicle, and accepts flares, chaff and APS from the
    pilot and seat 1 only, as upstream's client did.
43. Lock notifications (`PacketNotifyLock`) are not sent; the lock state each client shows comes
    from its own seeker, as the HUD's `lock` variable does.
44. A weapon seat always uses its seat ID, and upstream's off-by-default quaternion aim
    (`ExperimentalQuaternionRotation`) is not ported.
45. A weapon a set names that does not exist becomes a dummy weapon (with a log line). Round items
    are matched by item only; 1.12 metadata is ignored.
46. Flare, chaff and APS share one default key (V); one refusal sound plays only when none of them
    could be used (upstream played one for each). Chaff is drawn with the flare texture, as
    upstream's chaff renderer did.
47. Ammunition is supplied through the open aircraft menu.
48. Flare patterns are kept per side, so clients wait their intended extra 50 ticks; upstream
    shared one table between the sides in single player. "Using flares" is not saved.
49. Chaff in use is known on both sides and runs out, so radar seekers can lock on again
    afterwards. Upstream set its flag only on clients and never cleared it: there a vehicle that
    once used chaff could never be locked again, while on the server chaff did nothing.
50. The close air support A-10 and its bombs belong to the shooter (decision D20).
51. Projectiles are not saved with the world, except mines; throwables are not saved either.
    Projectiles live down to 10 blocks below the world's floor (upstream: y -10).
52. A projectile whose shooter is gone uses its vehicle for its validity check; upstream crashed.
53. Laser beams are depth tested, so walls hide them; upstream drew them through everything.
54. Ejected cartridges are client-only effects, not entities.
55. The bombing CCIP is drawn on the HUD by projecting the predicted impact point; upstream drew
    it in the world with depth testing off. It is turned by the vehicle's roll.
56. Every weapon part counts towards the missile index of the parts after it; upstream skipped
    hidden `HideGM` parts, so the wrong missile could disappear from the rails.
57. APS activation sounds are played by the server for everyone, so the user no longer hears them
    twice. Other mods' projectiles count for the APS and seekers by their entity type ID.
58. Dispensed items act on blocks through NeoForge's fake player, made once per server.
59. A thrown grenade ignores entity hits, which never changed its flight upstream.
60. The texture `9M113.png` is lowercased (`9m113.png`), since resource paths must be lower case.
61. Server-to-client packets skip connections that do not have the mod's channel (fake players,
    for example); upstream sent them regardless.
62. The reload button in the aircraft menu charges whoever presses it and, for anyone but the
    pilot, fills the magazine at once; upstream always charged the pilot. The pilot's magazines
    still fill when the menu closes, as upstream.
63. The fuel slot refuses empty fuel cans; upstream took them, and they then sat in the slot
    without refuelling.

64. A hand parachute is consumed only after a successful server deployment. Upstream consumed it
    on the ground or while mounted too. Deployment mounts the user immediately, so repeated use
    cannot create several chutes before the next tick.
65. Ejection reserves use separate persistent storage, preserving the existing fuel/trunk slot
    indices. A successful ejection consumes the slot that supplied the chute; upstream checked
    slots `2 + i` but cleared `3 + i`, allowing reuse or consuming a different chute. The server
    checks the content's ejection flag and refuses ejection from a destroyed vehicle.
66. Parachute water rescue searches the current build height and uses water fluid states, with
    floored coordinates. Upstream's probe was restricted to y 0–255 and truncated negative x/z.
67. Tank crushing uses editable block/tag selectors because block `Material` no longer exists.
    These map the old categories to current blocks; addon blocks need matching tags or explicit
    entries. Scans use current build limits and floored negative coordinates. Multipart parents
    receive one ramming hit per region query, rather than one for every modern part returned.

68. Fuel types keep 1.12's Forge fluid names (`FuelType`, `AcFuelType`). `mch_fuel` is the
    port's `mcheli_ce_port:mch_fuel`; another unprefixed name means the source fluid with that
    path in any namespace (`lava` is `minecraft:lava`), and a name with a namespace means exactly
    that fluid. A name no installed mod registers still works between vehicles and supply
    vehicles, but no container or pipe can carry it.

69. Cargo-container interaction opens a standard six-row menu; the pinned upstream wrapper's
    `displayInventory` body is empty. Menu validity uses current entity interaction distance
    instead of the old wrapper's 64-block distance. Current NeoForge transactional item
    capabilities replace the old Forge item handler. Slot-indexed modern item data preserves
    all 54 positions; removed block Material checks use water/lava fluid tags and current build
    heights. Contents drop only for destructive removal, preserving unload and dimension changes.

70. Rack loads use the saved vanilla passenger tree, with full world attitude for carried vehicles
    and a supported render transform for containers. Their own movement/ground effects and pilot
    motion/attitude packets are suspended while carried. This replaces upstream's nested drawing
    and avoids simulating a vehicle through its carrier. Normal player boarding skips rack slots.
    Blocked release points are refused. Failed/cancelled parachute deployment preserves or restores
    the rack attachment. Supported teleport transitions synchronize the driver/crew tree on release
    and transport-chute touchdown instead of upstream's eight-tick position reserve. Tank carrier
    collision grace refreshes while carried and on release. Existing living crew transforms use
    the carried vehicle's composed world attitude.
    A near-ground driver-requested release can briefly correct the client's movement before
    settling at the exit in default movement mode; this smoothing limitation remains open.

71. Tow chains synchronize distinct endpoint IDs; upstream wrote the trailer ID into both fields.
    Disconnect lookup now finds actual links instead of always returning null. Selection uses a
    dimension and UUID in modern item custom data, and saved chains search loaded entities by UUID
    in their current level for fifty ticks. One active chain per endpoint prevents duplicate links
    and cycles. Anchored placed vehicles, passengers, living entities, items, projectiles, XP and
    helper entities are refused; linked endpoints cannot enter racks.
    Generic entities controlled by another client (such as occupied vanilla boats) cannot be
    trailers; McHeli vehicles support occupied towing through their synchronized authority switch.
    The trailer is server-simulated while linked, including with a driver aboard. Disconnect uses
    supported teleport transitions to return movement control. Tank partner collision grace is
    retained during towing and after disconnect. The inverse-gap pull has a .1 minimum denominator
    to avoid an explosive impulse at the taut boundary. The length-plus-two correction uses solid
    collision movement and disconnects if blocked, instead of teleporting through walls. Stock link
    models use supported rendering with bounds covering both endpoints, replacing direct GL drawing.
    The tether limits maximum separation and does not copy braking or maintain a minimum gap;
    an occupied trailer may roll towards the tug when it stops. The local default-movement check
    logged one tug movement correction after braking, with no damage or lost link.

72. K sends the existing UNMOUNT_CREW action, whose client sender was absent from the pinned
    upstream handler, to upstream's `unmountCrew` (difference 84); with rope hooks away from the
    ground it deploys or retracts the ropes. Ropes require
    configured hooks, hover, pitch/roll below three degrees, recent movement below .3 blocks and
    no ground within four blocks. Their canonical server length grows .25 blocks per tick to
    terrain or fifty blocks, replacing the independent .3 client rate. Terrain uses current world
    heights, including negative Y. Readiness and length synchronize to every client.
    Manual round-robin hook selection and upstream's combined four-block starting drop are
    preserved; automatic non-player crew use a two-block drop and skip the first crew/gunner seat.
    Blocked exits and cancelled deployment retain the original seat. Descent uses current solid
    collision movement, checks the rider's clearance, and releases outside blocks on landing.
    The helicopter is server-simulated with free look and suppressed flight inputs while deployed;
    ending hover retracts ropes. Supported teleport transitions synchronize release/control return.
    Retraction or interruption releases at the current position; ordinary gravity then resumes.
    Unlike upstream's empty descent NBT, deployed ropes and parent/hook/passenger state save and
    recover by UUID with fifty ticks of grace. Failed dismounts keep the helper and rider together
    for retry. Riders stand upright, and ropes hang vertically from transformed hooks with culling
    bounds covering the full rope, through supported line submission instead of direct GL calls.

73. Searchlights use a dedicated bindable L key so C remains available for camera modes.
    The server accepts switches only from actual pilot/first-crew occupants of an intact vehicle
    with lamps. The first crew occupant aims movable lamps, falling back to the pilot. Empty
    vehicles retain synchronized/saved aim and switch state. Light covers open/close over two
    ticks. Fixed and steering lamps preserve upstream's matrix order; additive, two-sided cones
    use supported triangle submission without depth writes, with bounds spanning the cone.
    Living-entity lightmaps take the maximum existing block light and any cone highlight. Unlike
    upstream's last-light-wins loop, one lamp cannot darken another lamp's target. Highlight angles
    use a single 3D cone rather than separate horizontal/vertical approximations. As upstream,
    this is beam geometry and living-entity lighting, not terrain light-engine illumination or
    shadow-casting spotlights. Terrain depth occludes the drawn geometry; the living highlight
    does not ray trace intervening blocks. Stock alpha values are restored from legacy TXT, and
    content revision 1 refreshes the extracted default pack on existing 0.5.1 installs. Keep
    customizations in separate addon packs. Protocol 11 covers retained-aim entity data.

74. Team spotting (rangefinder, targeting pods) sends a mark point to the whole team; upstream's
    `sendMarkPointToSameTeam` sent it to the spotter once per teammate, so teammates never got it.
    A mark point also shows to a spotter on no team; upstream's mark point vanished at once for a
    player without a team. Markers are projected onto the HUD with the frame's camera instead of
    while each entity rendered, so they also show for entities hidden by other mods' renderers.
75. The rangefinder's scope narrows the field of view (as aircraft cameras do) instead of
    upstream's camera-zoom reflection, and hides the hand like the spyglass. Its keys are the
    vehicle zoom, weapon-mode and flare keys (Z, X, V by default) and the attack key, read while
    the player is on foot. The server refuses a spot within 50 ticks of the last (the client
    waits 60, as upstream); upstream trusted the client's cooldown. The last charge leaves an
    empty rangefinder rather than breaking it, as 1.12 items did at their maximum damage.
76. The wrench mines blocks tagged `mcheli_ce_port:wrench_metal` (1.12's iron material) at 20.5
    and harvests them, and `mcheli_ce_port:wrench_logic` (circuits, plants, vines, snow layers and
    carpets) at 5.5; 26.2 has no block materials. Upstream's creative dig-delay reset is not
    ported. The held wrench's repair swing is kept by the client rather than in the item, as
    upstream did.
77. The GLTD cools down for 450 ticks (460 on the client) after a strike, the value upstream's
    original source gave it (the A-10 strike's -300, lengthened by 150). McHeli CE's decompiled
    source turned that into 65,386 ticks, about 54 minutes. The designator's camera view is drawn
    from a view entity as vehicle cameras are; upstream swapped the render view entity. Its keys
    are the vehicle keys (attack to call the strike, Z and X to zoom, C for night vision, the
    unmount key or sneak to leave). The server also checks that the operator sending controls
    sits at the designator. Its laser is two crossed strips that widen with distance, so it stays
    about a pixel wide from the designator, instead of a GL line.
78. The hand-held Stinger and Javelin fire from the player's eyes on the server; upstream fired
    from the position the client sent. The server also needs the player to be aiming the launcher
    and the target to be alive and within 1.5 times `stingerLockRange`, where upstream took the
    client's target on trust, and a refused shot leaves the launcher loaded. Their keys are the
    vehicle keys (attack, Z, C and X), read while the player is on foot. The zoom is applied
    through the field of view rather than upstream's camera-zoom reflection. Upstream's complete
    lock lasted one tick before it started again, so the attack key had to be held; here it holds
    until the shot, until the target leaves the sight or until the launcher is lowered.
79. The drafting table lists every shaped or shapeless crafting recipe that makes one of the
    port's items, fixed items first in upstream's order, and the server sends that list when the
    screen opens (26.2 clients do not receive recipes). Upstream checked each ingredient against
    the inventory on its own, so one item could stand for several and Create still gave the
    result after taking fewer items; here every ingredient needs its own item and a short
    inventory makes nothing. Description pages are `drafting_table_desc/<item>/<n>.png`, since
    26.2 resource paths cannot hold upstream's `#`; addon pages named the old way are not shown.
    The list scroll bar is drawn with plain fills. On the 3D vehicle page the port's models hold
    triangles, so the build-up counts triangles rather than upstream's faces (a model of quads
    takes twice as long) and the lines show each quad's diagonal. Crawler tracks are not drawn
    there: upstream's call had no vehicle and threw for every tracked vehicle. The page is there
    when the vehicle has a model file and stays empty while the model loads in the background.
80. McHeli's effects are drawn with the M8 look by default (client config
    `effects.effectQuality = ENHANCED`): growing/fading smoke, weapon-scaled fire/smoke impacts,
    material dust, terrain marks, restrained sparse tracers, flare glow, surface-aware downwash,
    exhaust and vehicle imprints. `CLASSIC` selects upstream presentation; cosmetic budgets
    still apply. Enhanced emitters can change cosmetic counts/timing but do not change gameplay.
    Optional exhaust heat refraction uses one shared scene copy, is disabled on Low/adaptive
    pressure, and respects opaque depth; translucent surfaces may not hide it. See
    Static hull detail is baked from geometry and authored machinery anchors; it is not a new
    texture pack or real-time ambient occlusion. Known conventional weapons keep their HE or
    penetrator look even when they ignite blocks; explicit fire/FAE payloads remain distinct.
    See docs/M8_PLAN.md, docs/M8_FOLLOWUPS.md and docs/M8_COMPLETION.md.
    Target-PC 60 FPS acceptance remains open.
81. Only players who may use game-master commands (permission level 2) can set a vehicle's
    command in the aircraft menu; for others the field is read-only. Upstream let any player set
    it, and it runs with full permissions when the vehicle is destroyed by anything but a player,
    so anyone could run any command. Enter in the field runs the command at once as the player,
    with their own permissions; upstream built that packet but never sent it, so Enter did
    nothing. The menu's "MOD Options" button (Options) closes the menu and opens NeoForge's
    config screen for the mod. Upstream's Close and Inventory buttons are left out: Escape
    closes the menu and the trunk is already part of it.

82. The fuel bucket (upstream's Forge universal bucket of `mch_fuel`) is in the mod's items tab;
    as upstream the fluid has no block, so the bucket cannot be emptied into the world. A fluid
    container in the fuel slot gives up to the tank's free space each time, so a bucket waits
    until 1000 fits. Upstream drained the container before checking the output slot, so a full
    output slot could empty a tank item without fuelling the vehicle; here nothing moves until
    the output slot can take what is left of the container.

83. Upstream kept the scoreboard key (L) and the multiplay manager (M) off singleplayer (unless
    the debug log was on); here both work in singleplayer too. While L shows the team lists it
    keeps L from vanilla's Advancements key, which upstream's L also opened; in singleplayer it
    does so only once a team exists, so Advancements stay on L until then. The multiplay manager
    toggles PVP through the `pvp` gamerule, which the server has instead of upstream's server
    property, and its buttons need the game-master permission level, as upstream's scoreboard,
    teleport and summon commands did. Its Join and Leave team buttons are a port addition: any
    player may use them while the server setting `teams.playersChooseTeam` is on (the default),
    otherwise only game masters.
    Its lists show at least ten rows in steps of five; upstream also capped them at the server's
    player limit, which clients no longer receive. Team names take the characters `/team add`
    reads as one word, so OK stays greyed out for others; upstream sent the command and let it fail.

84. Mobs in seats follow upstream's `mountMobToSeats` and `unmountCrew`: when a player boards as
    pilot, living things within three blocks (two up or down) that ride nothing take the free
    seats in order, up to the first rack; a player who clicks a passenger seat, or moves to the
    pilot seat from the seat menu, brings none aboard. The pilot's K, which upstream sent from no
    key, unloads them: on the ground they get out; ten blocks or more above ground a vehicle with
    `EnableParachuting` opens its hatch and drops one mob every `MobDropOption` interval by
    parachute from the third seat on, while a mob in the second seat just gets out, as upstream's
    loop did; with rope hooks it lowers the ropes instead. The common config `dismountAll`
    (upstream `DismountAll`, off) unloads the mobs when the pilot gets out.
85. Gunner NPCs follow upstream's `MCH_EntityGunner` and `MCH_ItemSpawnGunner`. A gunner
    placed in creative cannot be hurt, gives its vehicle infinite ammunition and leaves no item
    behind; one placed in survival uses the vehicle's loaded ammunition (a new vehicle has none)
    and drops its spawn item when it is killed or leaves its vehicle. The spawn item's five-block
    search uses the player's body rotation, as upstream, so it can differ slightly from the
    crosshair while the head turns ahead of the body.
86. The ECM jammer has its own key (ECM Jammer, V like the other countermeasures), as upstream's
    `KeyECMJammer`. No stock vehicle carries one; addon vehicles with `ECM: Type: 2` strip radar
    and anti-radiation missiles within 80 blocks of their lock and deny radar locks while it
    runs. Upstream's other types only hid the vehicle from radar warning displays, which are not
    ported yet (M6). `PhotoelectricJammer` and `DIRCM` are read but did nothing upstream either.
87. The APS iron curtain (an APS with range 100) follows upstream: the vehicle takes no damage
    while it runs and its model pulses red. Upstream tinted vanilla clouds and block cracks red
    for the tank haze and for hits on the vehicle; vanilla particles take no tint in 26.2, so
    McHeli's own smoke particle is tinted instead. The APS takes its settings on the vehicle's
    first tick, so, as upstream, it cannot fire on the tick the vehicle appears.
88. Content settings that upstream read but never acted on stay inert: `DataLink`/`OnlyDataLink`,
    the anti-radiation missile tuning (`ARM*`; anti-radiation missiles only fall to a type 2 ECM
    jammer, as upstream), `PassiveRadarLockOutCount`, `SemiActiveRadar`,
    `PDHDNMaxDegreeLockOutCount`, the aircraft-level `PassiveRadar` and `EnableBVR`, and the
    weapon's `HasMortarRadar`. GPS missiles do not steer to the GPS point, as upstream. A
    `PassiveRadar` missile fires without a lock and flies at the BVR target, if any, as upstream;
    upstream's in-flight retargeting from the shooter's lock (`lock`/`onUnlock` with
    `PacketLockTarget`) had no callers and is not ported.
89. Lock boxes (upstream's `renderEntityMarker`) are drawn on the HUD by projecting upstream's
    in-world billboard onto the screen, so they keep upstream's size, place and colours but sit
    under the vehicle HUD instead of in the world. As upstream, only targets with no block in
    the way get one.
90. The radar warning receiver shows its scope on vehicles with an `RWR` section, as upstream, and
    the scope stays empty, as upstream: upstream's threat manager, which would have listed radars
    scanning or locking the vehicle and missiles homing on it, was never run, and its warning
    sounds never played. The radar switch (Radar Switch, `` ` ``) turns a vehicle's `Radar` on
    and off as upstream, which shows or hides the mortar radar scope. Upstream's
    `radar_on`/`radar_off` switch sounds were never in its assets and are left out.
91. The BVR scope and the mortar radar follow upstream, with these differences. Upstream looked
    for planes under their class name from before CE renamed it, so it never showed planes on the
    BVR scope and named them "?" on the mortar radar; planes are shown and named here, like
    helicopters (chosen by the project owner). Upstream's feed sent every dimension's entities to
    every player; each player gets their own dimension's here. Upstream's BVR marker textures
    (`bvr_lock_box.png`, `msl.png`) were not in its assets, so it drew the missing-texture square;
    a vehicle gets a square outline and a missile a diamond here, in upstream's colours. The
    client forgets the feed when it leaves a world instead of letting it expire.
92. The GPS panel opens with N (Open GPS Panel); upstream's K is the port's ropes key (item 72).
    Upstream drew the GPS marker with world transforms inside its 2D overlay pass, which put it at
    a screen position equal to the point's offset in blocks, and with a texture
    (`gps_position.png`) missing from its assets; the marker is projected onto the point here and
    drawn as a square outline. As upstream, the server keeps each player's point but nothing reads
    it (GPS missiles do not steer, item 88), and the point goes inactive off a vehicle.
93. The wingman marker screen saves only for a player who can reach and edit the marker (within
    block reach plus four blocks, not a spectator, outside spawn protection); upstream accepted
    the save from any player for any marker. Changing the type keeps what was typed into the
    fields, where upstream reset them to the saved values. The placement message leaves out
    upstream's pointer to `/wingman marker type` until that command is ported.
94. The base screen saves and deletes taxi routes only for a player who can reach and edit the
    base marker, saves them under that marker's base rather than the base the client names, and
    deletes only that base's routes; upstream took the base from the client and deleted any route
    by ID. Route IDs still share one list per dimension, as upstream, so saving an ID another base
    uses moves that route to this base. Upstream drew every helipad button on one row, so a fifth
    helipad covered the first; the port starts a second row, as its layout already allowed for.
95. The wingman panel only offers and takes aircraft within its 512 blocks that no player flies
    and that are not destroyed, nearest first, and only the pilot of the leader can order its
    wingmen; upstream listed every aircraft in that range, including other players', and acted
    on any aircraft UUID a client sent. A new wingman takes the lowest free formation slot, where
    upstream took the number of wingmen, so after a Stop two wingmen could share a slot. The
    Formation tab still changes the values for the whole server, as upstream, but values that are
    not numbers are ignored and the distances stay within upstream's config ranges; on a screen too
    short for upstream's layout, its Apply button moves between Refresh and Close instead of
    falling off the bottom. Wingmen are forgotten when the server stops, as upstream forgot them
    on restart.
96. Upstream's wingmen fired as their leader and along the leader's heading, so their shots left
    wherever the leader pointed, and seeking missiles fired at nothing; the port fires as the
    wingman, along its own heading, and gives seeking weapons the target, as the port's gunners
    do. Upstream's auto-attack also took any entity its team check passed, which an aircraft's
    check always did (items, animals, parked aircraft); the port takes what upstream's comments
    describe: hostile mobs, target drones and McHeli gunners of a team the leader's pilot is not on.
    An aircraft on a mission has no leader, so to it every McHeli gunner on a team is an enemy.
97. Upstream flew helicopter wingmen as planes: the formation throttle (full throttle while far
    from the slot, the leader's near it) and full throttle to attack made them climb without end,
    0.35 throttle beside a stopped leader was too little for a stock helicopter to hover, and they
    pitched the nose up to climb, which flies a helicopter backwards, away from a climbing leader.
    The port sets a helicopter wingman's throttle to what holds it level against its gravity, plus
    0.03 for each block it is below its slot (or attack height), and pitches the nose down half a
    degree for each block it is from its slot, less 25 degrees for each block a tick it already
    closes (from 15 degrees up to 20 down), to fly there without overshooting, levelling it again
    while it waits beside a stopped leader. A helicopter
    attacking with a fixed gun still aims with its nose, and planes keep upstream's flying.
    On a mission a helicopter holds its target height the same way while it flies to the area,
    orbits and returns, where upstream gave it a plane's full throttle; it hovers over the helipad
    on that throttle, and descends onto it as far below its hover throttle as upstream's planes
    flew below 0.57, plus 0.03 so that it settles on the pad. Returning below a raised helipad (a
    roof or a deck), it climbs where it is before flying in, so it does not end up under the pad,
    where upstream took any height below the pad as landed and cut the engine.
98. Upstream kept chunks loaded around every aircraft only with its `forceChunkload` option, off
    by default. The port always keeps the 5 by 5 chunks around an aircraft on a mission loaded,
    and no others, so a mission flies on away from players; they are released when the order
    ends. Orders are not saved, so a mission ends when the server stops, as upstream.
99. The base screen's Mission tab orders an aircraft only for a player who can reach and edit the
    base marker, takes the base from that marker, and orders only an aircraft within 512 blocks
    of it that no player flies and that is not destroyed; upstream took the base from the client
    and ordered any aircraft by UUID. The tab lists the eight such aircraft nearest the base,
    where upstream showed the first eight it found. The order's numbers are kept within limits
    (target within the world border's range, orbit up to 10,000 blocks, cruise height within the
    world plus 256, up to 100 strike passes and a day's time limit); a number that is not one
    takes upstream's default. A player taking the controls, or the aircraft being destroyed,
    ends its order; upstream flew on under a player. Upstream turned the mission orbit with the
    attack orbit's angle, in degrees, so attacking on station threw the orbit around; the port
    keeps them apart. Upstream never turned a parked aircraft to its spot's heading, since the
    order had ended by then; the port turns it as it parks. The tab's VTOL landing note is drawn
    at the bottom left, where upstream's clipping hid it, and a button scrolled partly off the top
    of either tab is hidden rather than drawn over the tab buttons.
100. The mission planner and `/wingman` follow upstream, with these differences. Planner routes
    are saved with the dimension, where upstream kept one `wingman_routes.json` for every world,
    since their nodes name that dimension's markers. A route is sent only to an aircraft in the
    player's dimension that no player flies, and it replaces the aircraft's order; upstream left
    the order in charge. An abort ends planner missions and leaves orders alone; upstream's
    abort-all also stopped orders, which then restarted. A take-off or landing node finds the
    runway from the base's ID or from either end's ID; upstream needed both ends to share the ID,
    so the runway-end IDs its planner offered skipped the node. Its take-off and landing hints also
    list bases. Nodes refuse numbers that are not finite, a negative radius or time and
    coordinates past the world border, and a route takes up to 64 nodes. The planner narrows its
    two panels and shows fewer rows in a small window, and the route editor keeps every row on
    screen at 427 by 240. `/wingman` is for any player, as upstream, except `spawnuav`, which
    spawns aircraft and needs operator permission (level 2). `follow`, `stop`, `engage`, `auto`,
    `hold` and `weapon` act for the aircraft the player pilots, as the wingman panel does, and
    `follow` takes an aircraft within 512 blocks that nobody flies; `order dispatch` orders an
    aircraft within 512 blocks of the base, which must be one the player may build at; `marker id`
    and `marker type` edit the marker the player looks at within reach, as the marker screen does,
    where upstream looked 8 blocks ahead and let anyone edit it. Numbers out of upstream's config
    ranges are refused. `engage` needs the target's UUID: without one upstream set a manual attack
    on nothing, which left the wingmen out of formation attacking nothing. Brigadier ends a bare
    word at a comma, so `order dispatch` takes several mission types as `cap+recon` or
    `"cap,recon"`, and its last number is the time limit in seconds, which upstream's usage
    called minutes but read as seconds. `order park` keeps the parking spot until the aircraft's
    next order or `order abort`; upstream dropped it on the next tick unless the aircraft was a
    wingman. The formation values are read from the server config's `[wingman]` section when the
    server starts and, as upstream, changes made while it runs are not saved.
101. NPC battles are a port addition, on by default; the server config's `npcBattles.enabled = false`
    keeps upstream's targets. Upstream's NPCs never fought each other: gunners shot only monsters
    or players, and wingmen only hostile mobs, target drones and team gunners. With battles on, an
    NPC fights for a team. A gunner fights for its own team. An unmanned aircraft fights for the
    team of its leader's pilot, or of the player who dispatched it or sent it on a planner route,
    or else for the team of a gunner aboard. A vs-player gunner of a team then also attacks the
    gunners and unmanned aircraft of other teams, and wingmen and aircraft on missions attack them
    on Auto, on station and at a route's attack node; NPCs spare their own team. NPCs without a team
    and vs-monster gunners keep upstream's targets.
102. `/mcheli` follows upstream, with these differences. `reconfig` does not read the config files
    again, since NeoForge already reloads a config file when it is saved; it applies the wingman
    settings and sends every player the server settings again. It never reloaded content, upstream
    or here. `killentity`, `removeentity` and `attackentity` also match part of an entity type ID,
    such as `minecraft:bat`, besides part of the class name; 26.2's class names differ from 1.12's,
    so upstream's examples such as `EntityBat` no longer match. `attackentity outOfWorld` deals the
    void's damage, where upstream gave generic damage. `fill` takes 26.2's block states and data
    tags, such as `minecraft:chest[facing=north]{...}`, in place of a metadata number.
    `showboundingbox false` turns off entity hitboxes (F3 + B) on every client, as upstream did.
    The settings these commands and upstream's `EnableCommand` and `CommandPermission` change live
    in the server config's `[commands]` section and CommonConfig's `hitBoxDelayTick`.
    Upstream's `sendss` and `modlist` never worked: `sendss` sent its request with no action, and
    the `modlist` answer went to the wrong request. Here `sendss` saves the player's screen as
    upstream meant, to `<server folder>/screenshots_op/<player>_<date>.png`, but only for players
    who allow it (item 10), and gives up after a minute without an answer. `modlist` lists
    NeoForge's mods with their versions and files; upstream's class path, access transformer,
    coremod and manifest sections have no 26.2 counterpart. As upstream, a client only shares its
    mod list while its `debugLog` is on, and the sender is told when it does not. Both skip players
    without the mod.
103. The ground tilts a tank at most 40 degrees either way. Upstream had no limit, so a tank whose
    front wheels hung over a shell crater tipped until it fell in nose first, often near vertical;
    40 degrees still follows a one-block staircase hill.
104. Upstream registered six advancement triggers but shipped no advancements that use them. The
    port adds six, named after upstream's old achievements (`achievement.McHeli*` in its lang
    files), in their own tab: placing a vehicle, refuelling, resupplying ammunition, dropping a
    towed container from more than 20 blocks up, boarding a UH-1C (hidden) and hurting a villager
    with a bullet from a vehicle seat (hidden). To drop a container, the extra key unhitches what
    a helicopter or placed vehicle tows, as upstream; the port had not ported that key yet.
105. Thermal vision follows upstream, with these differences. Upstream offered it when the rider's
    client supported shaders and its `DisableShader` was off; every 26.2 client draws post effects,
    so vehicle cameras always have it, and `DisableShader` and `EnableReplaceTextureManager` are not
    ported. Upstream drew every entity but the ridden vehicle white; the port draws living entities
    (not their held items, as upstream) and McHeli vehicles white, and leaves bullets, dropped
    items, the GLTD, cargo containers and other entities in their own textures.
106. Long-distance tracking follows upstream's `ExperimentalRemoveClientTrackingRestrictions`
    (on by default there and here, as the server config's `tracking.longDistance`), with these
    differences. It covers McHeli vehicles only: upstream also sent its bullets, missiles, seats,
    containers and the GLTD past the view distance. Upstream drew far entities in a second pass
    with its own 3,000-block projection; the port keeps vanilla's projection, whose far plane is
    four times the render distance or the cloud range, whichever is larger, and draws the
    vehicles past the render distance with a shader that leaves out fog, as upstream turned fog
    off. Riders are not sent that far (they never were), so far vehicles show empty seats.
107. Burning debris thrown by explosions of size 4 or more lives only on the client that drew the
    explosion, and goes out when it lands, hits a block or after 15 seconds; upstream's client copy
    never went out. Only the `CLASSIC` effect look throws it: the `ENHANCED` look (M8) draws its own
    explosions.
108. Vehicle models are drawn from GPU buffers (client config `render.gpuModelBuffers`, on by
    default), as upstream drew them from display lists. They are drawn with the solid entities, so their
    see-through texels (canopies) are unsorted and do not show translucent things behind them, as
    upstream's were; the per-frame path the option turns back on sorts them. Vehicles with a glowing
    outline and the emissive layers (exhaust flames) always take the per-frame path. M8's surface shading
    is baked into the GPU buffers too. If the GPU path fails once, it turns itself off for the
    session and logs why.
109. AI pilots aim and fly with the port's fire control (`docs/M10_AI_FIRE_CONTROL.md`), not
    upstream's checks. Upstream fired a gun whenever the target was within 20 degrees of the
    heading, with no lead or drop, dropped bombs anywhere within 80 blocks, fired the first weapon
    at any heading with none chosen, and turned its orbit point 2 degrees a tick; the port chooses
    the weapon for the target, leads guns and rockets, releases bombs where they will land, launches
    missiles within 45 degrees, flies passes and orbits planes can fly, and fires along its solution
    rather than the nose. Orders use every weapon they list, not only the first non-gun one. Bomb runs
    are flown from 100 blocks up (upstream 200). Wingmen top up a magazine that was never loaded,
    which upstream's infinite ammunition did not. AI planes bank into every turn, lose their sideways
    drift, are pushed towards their slot only forwards and circle a stopped leader instead of idling
    in the air. Ground vehicles keep upstream's firing.

## Upstream content issues kept as they are

Except for restoring searchlight alpha and stock crafting recipes, the port preserves what upstream's conversion of
its stock content from TXT to YAML lost or changed (the full list, checked by a test, is in
`src/test/resources/parity/known-differences.txt`):

- Stock vehicles have no paint variants. Crafting recipes lost by the YAML conversion have been
  restored: 115 recipes for 113 vehicles, plus four throwable recipes. The infinite fuel truck and
  ammo supply box retain their deliberate lack of survival recipes. The drafting table lists the
  restored recipes. Existing installations refresh their extracted stock pack on restart.
- Some weapons lost their damage factors, bomblet, fuel-air explosive, dispenser and targeting pod
  settings; some racks lost their fixed angles.
- Stock YAML vehicles sit with the opposite pitch on the ground (`OnGroundPitch` was written with
  its sign flipped).
- The stock helicopter HUD tests `hovering==1`, but `hovering` is true or false, so the HOVERING
  label never shows, as upstream.
- The UH-1C names a `camera0` part that its model file does not have; nothing is drawn for it,
  as upstream. Upstream adds each first canopy twice, as `canopy0` and `canopy`, so a model may
  name it either way; the unused name draws nothing, as upstream.
- Stock plane definitions contain 54 references to parts without matching geometry in the
  upstream models (empty rotor hubs and nozzles, and mismatched gear names).
  The exact list is in `src/test/resources/parity/plane-model-gaps.txt`; these missing parts
  are not invented or silently renamed by the renderer.
- The stock planes each define only one camera position. Additional positions require addon
  content. Camera `FixedRot`, `Yaw` and `Pitch` are parsed, but the pinned upstream never applies
  those camera rotation settings; the port preserves that behaviour.
- Eleven sound events named by stock content have no sound file (for example `railgun` and the
  smoke dispensers' fire sounds), so they play nothing, as upstream.
- Two stock plane files (`b29.yml`, `fuel_truck.yml`) name their `Author` twice; SnakeYAML logs a
  "duplicate keys found" warning while loading them, and the second name wins, as upstream.
- Upstream's legacy TXT fixtures contain three broken recipes, repaired in the shipped YAML:
  Robinson R44's boat key is bound to `F`, PZL M-18's water-bucket key to `P`, and US-2 accepts
  any potion instead of requiring a 1.12 potion metadata value. Other restored recipes retain
  their legacy ingredients and patterns.

- Upstream's `models/parachute2.mqo` prefixed every group name with `[Exported] [Imported]`, which
  its parser cut short, and its `$parachute` group was empty, so ejection seats and transport
  chutes drew nothing. The port's copy names the seat `$seat` and puts the canopy and lines in
  `$parachute`, so an ejected pilot falls on the seat and then hangs under the canopy.
