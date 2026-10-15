# Known issues

What McHeli CE (26.2 Port) does not do yet, and where it deliberately differs from upstream
McHeli CE 1.5.1. This file is updated with every milestone (PORTING_PLAN.md, section 6).

## Not ported yet

The 0.5.1 baseline (M5) supports helicopters with flight, models, HUD, cameras and weapons.
The current M6 development work also adds initial plane entities, placement, flight, VTOL,
wing switches and model submission. M6 remains incomplete; see `docs/M6_PLAN.md` for validation
and remaining work. Plane effects and destroyed-plane visual motion are now implemented;
camera-position switching, ejection, and full flight/weapon/multiplayer checks are still pending.

| Area | Planned milestone |
|---|---|
| Planes, ships, tanks, vehicles, UAVs (and the UAV camera), camera position switching, search lights, ropes and rappelling, crawler tracks and track rollers, ERA tiles, gunner NPCs and mobs in seats (and their weapon use), racks, containers, chains, parachutes, GLTD, hand-held weapons (Stinger, Javelin), the rest of the aircraft GUI, drafting table screen, charger, tools, supply vehicles, multiplayer teams and team spotting markers (`MCH_GuiTargetMarker`) | M6 |
| BVR and passive radar, GPS panel and GPS missiles, mortar radar and RWR overlays, ECM jammer, the pulse-Doppler notch, the APS "iron curtain", data link, anti-radiation missiles, `Destruct` bombs (UAVs), the targeting pod's spotting | M6 (with the vehicles that use them) |
| Wingman (AI wingmen, marker block screens and types, missions, `/wingman`) | M6b |
| Remaining `/mcheli` commands (including reloading content with `reconfig`), advancement triggers, long-distance tracking and rendering, thermal vision (the third camera mode), GPU-buffer model rendering, projectile chunk loading (`ProjectileChunkLoad`), HBM:NTM stubs, performance work | M7 |

Until then:

- The key hints for racks and search lights come with those features (M6).
- Ships, tanks and vehicles do nothing when used, and their entity types are inert
  placeholders that cannot be summoned.
- The aircraft menu has the fuel slots, the trunk and the weapon list with its reload buttons, on a
  plain container background; the rest of upstream's aircraft GUI (parachutes, the menu buttons)
  comes with M6.
- Fuel comes from fuel cans only. Refuelling from fluid containers, which upstream also allowed,
  needs a fuel fluid and comes with the supply vehicles (M6).
- The drafting table is drawn with a simple block model; upstream drew a 3D model with a block
  entity renderer (M6). The rangefinder, wrench, GLTD and hand-held launchers show as flat items in
  the hand; upstream drew 3D models (M6).
- Upstream's two special crafting recipes, refilling fuel and recharging the rangefinder, come with
  those items (M6).
- Every placed wingman marker is a waypoint until the marker screen is ported (M6b).
- The targeting pod only plays its refusal sound when fired, until team spotting comes (M6).
- An APS with upstream's "iron curtain" range (100) only plays its sounds (M6).
- Turret ballistics lines (upstream `drawTurretBallistics`) are not drawn yet (M7).

This config entry already exists but has no effect yet:

- CLIENT `privacy.allowScreenCapture` (needs `/mcheli sendss`, M7)

## Deliberate differences from upstream

These follow from how Minecraft 26.2 works or from decisions approved in PORTING_PLAN.md
(sections 8 and 10). They apply as each feature is ported.

1. 1.12.2 worlds and items are not migrated (different mod ID, item flattening, new save format).
2. The UAV live camera feed shows NO SIGNAL plus telemetry: 26.2 has no supported way to render the
   world a second time from another viewpoint.
3. Stippled HUD lines are drawn as dashes, so they may not match upstream pixel for pixel.
4. Recipe and ammo references to 1.12 items that cannot be mapped to a 26.2 item are skipped, with a
   log line.
5. Config entries that matched Java class-name substrings (`DamageVs…`, `IgnoreBulletHit`) match
   entity type IDs instead.
6. GregTech EU charging is removed (Forge Energy only). The One Probe and Trajectory Preview
   integrations are stubs.
7. HBM:NTM warhead payloads do nothing unless an HBM build for 26.2 exists.
8. Vehicles beyond the far plane are drawn as scaled stand-ins instead of in a second far-plane render
   pass.
9. The pilot's client simulates its own vehicle (decision D2). Servers can switch back to upstream's
   server-authoritative model with `vehicles.serverAuthoritativeMovement`.
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
18. The wingman marker block stores its marker type in its block state; upstream kept it in a block
    entity.
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

## Upstream content issues kept as they are

The port ships upstream's stock content files unchanged, including what upstream's conversion of
its stock content from TXT to YAML lost or changed (the full list, checked by a test, is in
`src/test/resources/parity/known-differences.txt`):

- Stock vehicles and throwables have no crafting recipes, and vehicles have no paint variants.
- Some weapons lost their damage factors, bomblet, fuel-air explosive, dispenser and targeting pod
  settings; some search lights lost their transparency; some racks lost their fixed angles.
- Stock YAML vehicles sit with the opposite pitch on the ground (`OnGroundPitch` was written with
  its sign flipped).
- The stock helicopter HUD tests `hovering==1`, but `hovering` is true or false, so the HOVERING
  label never shows, as upstream.
- The AH-1Z names a `canopy` part and the UH-1C a `camera0` part that their model files do not
  have; nothing is drawn for them, as upstream.
- Stock plane definitions contain 79 references to parts without matching geometry in the
  upstream models (including empty rotor hubs/nozzles and mismatched canopy/gear names).
  The exact list is in `src/test/resources/parity/plane-model-gaps.txt`; these missing parts
  are not invented or silently renamed by the renderer.
- Eleven sound events named by stock content have no sound file (for example `railgun` and the
  smoke dispensers' fire sounds), so they play nothing, as upstream.
- Two stock plane files (`b29.yml`, `fuel_truck.yml`) name their `Author` twice; SnakeYAML logs a
  "duplicate keys found" warning while loading them, and the second name wins, as upstream.
- Upstream's legacy TXT files (not shipped, used as test fixtures) contain three recipes that cannot
  work: two use a pattern letter without a key, and one needs a 1.12 potion by metadata.
