# Known issues

What McHeli CE (26.2 Port) does not do yet, and where it deliberately differs from upstream
McHeli CE 1.5.1. This file is updated with every milestone (PORTING_PLAN.md, section 6).

## Not ported yet

As of 0.2.0 (M2), the mod loads all content and registers its items, blocks, entity types and
creative tabs, but nothing can be used yet:

| Area | Planned milestone |
|---|---|
| Placing and flying helicopters: riding, controls, flight physics, fuel | M3 |
| Model rendering (OBJ/MQO), HUD, camera and zoom, night vision | M4 |
| Weapons, throwing grenades, flares and chaff, explosions, particles | M5 |
| Planes, ships, tanks, vehicles, UAVs, gunner NPCs, containers, chains, parachutes, GLTD, hand-held weapons, drafting table screen, charger, tools, multiplayer teams | M6 |
| Wingman (AI wingmen, marker block screens and types, missions, `/wingman`) | M6b |
| Remaining `/mcheli` commands (including reloading content with `reconfig`), long-distance tracking and rendering, thermal vision, HBM:NTM stubs, performance work | M7 |

Until then:

- Items do nothing when used, and all entity types are inert placeholders that cannot be summoned.
- The drafting table is drawn with a simple block model; upstream drew a 3D model with a block
  entity renderer (M6). The rangefinder, wrench, GLTD and hand-held launchers show as flat items in
  the hand; upstream drew 3D models (M6).
- Upstream's two special crafting recipes, refilling fuel and recharging the rangefinder, come with
  those items (M6).
- Every placed wingman marker is a waypoint until the marker screen is ported (M6b).

These config entries already exist but have no effect yet:

- SERVER `vehicles.serverAuthoritativeMovement` (needs vehicles, M3)
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

## Upstream content issues kept as they are

The port ships upstream's stock content files unchanged, including what upstream's conversion of
its stock content from TXT to YAML lost or changed (the full list, checked by a test, is in
`src/test/resources/parity/known-differences.txt`):

- Stock vehicles and throwables have no crafting recipes, and vehicles have no paint variants.
- Some weapons lost their damage factors, bomblet, fuel-air explosive, dispenser and targeting pod
  settings; some search lights lost their transparency; some racks lost their fixed angles.
- Stock YAML vehicles sit with the opposite pitch on the ground (`OnGroundPitch` was written with
  its sign flipped).
- Eleven sound events named by stock content have no sound file (for example `railgun` and the
  smoke dispensers' fire sounds), so they play nothing, as upstream.
- Upstream's legacy TXT files (not shipped, used as test fixtures) contain three recipes that cannot
  work: two use a pattern letter without a key, and one needs a 1.12 potion by metadata.
