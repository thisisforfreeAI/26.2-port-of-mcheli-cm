# Known issues

What McHeli CE (26.2 Port) does not do yet, and where it deliberately differs from upstream
McHeli CE 1.5.1. This file is updated with every milestone (PORTING_PLAN.md, section 6).

## Not ported yet

As of 0.4.0 (M4), helicopters can be placed, flown, refuelled and picked up, and are drawn with
their models, HUD and gunner camera. Everything else that needs a vehicle or a weapon is still to
come:

| Area | Planned milestone |
|---|---|
| Weapons and gunner weapon use, the weapon parts of models (aiming, recoil, barrel spin, missiles on rails), the HUD's weapon values, sight dot, hit and lock-on markers, TV missile camera, BVR, GPS, mortar and RWR overlays, throwing grenades, flares and chaff, APS and ECM, McHeli's own explosions, particles (damage smoke, sand clouds) | M5 |
| Planes, ships, tanks, vehicles, UAVs (and the UAV camera), camera position switching, search lights, ropes and rappelling, crawler tracks and track rollers, ERA tiles, gunner NPCs and mobs in seats, racks, containers, chains, parachutes, GLTD, hand-held weapons, the rest of the aircraft GUI, drafting table screen, charger, tools, supply vehicles, multiplayer teams | M6 |
| Wingman (AI wingmen, marker block screens and types, missions, `/wingman`) | M6b |
| Remaining `/mcheli` commands (including reloading content with `reconfig`), advancement triggers, long-distance tracking and rendering, thermal vision (the third camera mode), GPU-buffer model rendering, HBM:NTM stubs, performance work | M7 |

Until then:

- Weapon parts are drawn at rest, missiles always show, and the HUD shows upstream's values for a
  vehicle without weapons (M5). The key hints for flares, weapons, racks and search lights come
  with those features (M5, M6).
- Planes, ships, tanks, vehicles and throwables do nothing when used, and their entity types are
  inert placeholders that cannot be summoned.
- A destroyed helicopter explodes with a vanilla explosion of upstream's power (decision D13);
  McHeli's explosion comes with M5.
- The aircraft menu has the fuel slots and the trunk only, on a plain container background; the
  rest of upstream's aircraft GUI (weapons, parachutes, the menu buttons) comes with M6.
- Fuel comes from fuel cans only. Refuelling from fluid containers, which upstream also allowed,
  needs a fuel fluid and comes with the supply vehicles (M6).
- External damage factors (`DamageFactor` config lines and weapon damage factors against vehicles)
  apply once weapons are ported (M5).
- The drafting table is drawn with a simple block model; upstream drew a 3D model with a block
  entity renderer (M6). The rangefinder, wrench, GLTD and hand-held launchers show as flat items in
  the hand; upstream drew 3D models (M6).
- Upstream's two special crafting recipes, refilling fuel and recharging the rangefinder, come with
  those items (M6).
- Every placed wingman marker is a waypoint until the marker screen is ported (M6b).

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
- Eleven sound events named by stock content have no sound file (for example `railgun` and the
  smoke dispensers' fire sounds), so they play nothing, as upstream.
- Two stock plane files (`b29.yml`, `fuel_truck.yml`) name their `Author` twice; SnakeYAML logs a
  "duplicate keys found" warning while loading them, and the second name wins, as upstream.
- Upstream's legacy TXT files (not shipped, used as test fixtures) contain three recipes that cannot
  work: two use a pattern letter without a key, and one needs a 1.12 potion by metadata.
