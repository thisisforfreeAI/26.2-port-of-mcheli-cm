# Known issues

What McHeli CE (26.2 Port) does not do yet, and where it deliberately differs from upstream
McHeli CE 1.5.1. This file is updated with every milestone (PORTING_PLAN.md, section 6).

## Not ported yet

As of 0.1.0 (M1), the mod loads, sets up its empty registers and config, and does nothing else.
Everything upstream does is still missing:

| Area | Planned milestone |
|---|---|
| Content loading (YAML and TXT), addon packs, items, creative tabs, recipes, `/mcheli dumpyaml` | M2 |
| Helicopters: spawning, riding, controls, flight physics, fuel | M3 |
| Model rendering (OBJ/MQO), HUD, camera and zoom, night vision | M4 |
| Weapons, flares and chaff, explosions, particles | M5 |
| Planes, ships, tanks, vehicles, UAVs, gunner NPCs, containers, chains, parachutes, GLTD, hand-held weapons, drafting table, charger, tools, multiplayer teams | M6 |
| Wingman (AI wingmen, marker blocks, missions, `/wingman`) | M6b |
| Remaining `/mcheli` commands, long-distance tracking and rendering, thermal vision, HBM:NTM stubs, performance work | M7 |

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
