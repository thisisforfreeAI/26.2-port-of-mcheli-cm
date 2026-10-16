# McHeli CE (26.2 Port)

An unofficial community port of [McHeli: Community Edition](https://github.com/Warfactory-Offical/McHeliCE)
(McHeli CE) to **Minecraft 26.2** on **NeoForge**.

> **Unofficial fork.** This port is not affiliated with or endorsed by Warfactory, the McHeli CE
> developers, or the original McHeli authors. The upstream developers have said they will not port
> McHeli CE to other Minecraft versions. Please report problems with this port to
> [its issue tracker](https://github.com/thisisforfreeAI/26.2-port-of-mcheli-cm/issues), not to upstream.

## Status

**Work in progress (milestone M6):** helicopters can be placed, boarded, flown with the keyboard
and mouse, refuelled from fuel cans, landed and picked up again, in single player and multiplayer.
They are drawn with their models, rotors and moving parts, show upstream's HUD, have the gunner
camera with zoom and night vision, and fight with all of upstream's weapon types, guided missiles,
flares, chaff and McHeli's explosions. Grenades can be thrown. Initial plane placement, flight,
VTOL, wing controls and model rendering are now implemented in the M6 development work;
plane smoke, wakes, addon exhaust flames and destroyed-plane motion are also implemented.
Planes now use the gunner camera, zoom and night vision; H also cycles extra camera positions
defined by addons. Hand parachutes, transport-seat bailout and plane ejection are implemented,
including reserve slots in the aircraft menu. The stock ejection/transport model has an upstream
asset defect described in KNOWN_ISSUES.md. Ships can now be placed, boarded and piloted, with buoyancy, steering, crew weapons, models
and wakes. Tanks and ground vehicles now place, mount and save/load, with driving, terrain tilt,
animated wheels/tracks and independent weapon aiming. Broader gameplay validation and the
remaining support systems continue in M6. The stock fuel truck now supplies nearby vehicles, and
medical transports regenerate secondary-seat passengers while a pilot is aboard. Addon-defined
nearby vehicle repair is supported. Cargo containers now place, float and store 54 slots of
items: right-click to open, and attack to break and drop the contents. Aircraft racks now load
eligible cargo with **J** and unload with **U**. Eligible vehicle pilots
can use J to board a carrier and U to leave it. High cargo releases use a transport parachute;
towing now uses the reusable Chain item: right-click the cargo first, then the towing vehicle;
right-click either linked endpoint to disconnect. Helicopters with rope hooks now deploy/retract
ropes with **K** during a steady, level hover. After deployment, crew press **Y** to rappel. Configured searchlights/headlights toggle with **L**.
UAV stations and portable controllers now place and deploy UAV items. Right-click a station,
insert a UAV, then choose **Deploy**, select its row and **Connect**. Use **I** for the aircraft's fuel/menu
and **Y** to leave remote control. The operator stays seated at the station while the main view
follows the UAV. Portable controllers accept small UAVs; shift-right-click toggles their lid.
Control reaches **5,000 blocks** from the operator, including vertical distance. A small area
around the UAV stays loaded and streams to its pilot; leaving control releases it. Reconnect can
load a paired UAV at its saved location. Servers can lower `uav.controlRange` and tune the camera
radius with `uav.remoteViewDistance` (default four chunks). Stations retain a list of paired
UAVs (five by default); select a row to see its status and connect, or **Unpair selected** to release it.
The pairing device selects a deployed UAV with right-click, then pairs it when you right-click a
station. **Shift-right-click an idle UAV with the device** releases its previous pairing.
Right-click a released small UAV with the **UAV Tablet** to pair it, then right-click air to open
the tablet. Keep holding it during control; **Y** exits. Tablets control deployed UAVs and retain
their pairings on the item. The small preview shows telemetry only.
The mod also loads all of McHeli CE's content (vehicles, weapons, throwables and
HUDs, from YAML and legacy TXT files, including addon packs).

- [PORTING_PLAN.md](PORTING_PLAN.md): the roadmap and the decisions behind it
- [CHANGELOG.md](CHANGELOG.md): what each milestone added
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md): what is missing, and where the port differs from upstream

| | |
|---|---|
| Mod ID | `mcheli_ce_port` |
| Minecraft | 26.2 |
| NeoForge | 26.2.0.88 (pinned for development; later 26.2 builds are accepted at runtime) |
| Upstream baseline | McHeli CE 1.5.1 (`master` @ `cbaff7f`) |
| Licence | GPLv3 |

## Building and running

To download a tested development build, sign in to GitHub, open a successful run under
[Actions → Build](https://github.com/thisisforfreeAI/26.2-port-of-mcheli-cm/actions/workflows/build.yml),
and download its `mcheli-ce-port-<commit>` artifact. Extract the ZIP and put the JAR in your
Minecraft instance's `mods` folder. The included `SHA256SUMS` identifies the exact JAR; players
joining each other should use the same artifact, since development builds can share a version
number. Artifacts expire after 30 days. The downloadable artifact is produced only after both
the build and GameTests pass.

The build targets Java 25. Install JDK 25, or run Gradle on any JDK from 17 up and it downloads a
JDK 25 toolchain automatically.

```sh
./gradlew build       # mod jar in build/libs/ (also runs the tests)
./gradlew test        # JUnit tests, with Minecraft and the mod loaded
./gradlew runData     # regenerates models, lang, recipes and loot tables in src/generated/resources
./gradlew runClient   # development client
./gradlew runServer   # development dedicated server (no GUI; type "stop" to shut it down)
```

Development runs use `run/` as the game directory.

For Vulkan with the pinned NeoForge build, set `earlyWindowControl = false` in
`run/config/fml.toml` before launching. Its early OpenGL loading window otherwise conflicts
with Vulkan surface creation. Select Vulkan in video settings and restart the client.

Parachutes: right-click the item while airborne and unmounted. On an ejection-capable plane,
load parachutes into the **Chutes** slots with **I**, then press **Space** from the pilot or first
crew seat. Space still toggles hovering in helicopters. **Y** dismounts; eligible transport
passenger seats use a parachute when the hatch is open and the ground is clear.

Ships: **W/S** controls throttle, **A/D** steers, **I** opens the fuel/ammunition menu, and **Y**
dismounts. Stock ships require water to move. Their crew weapons belong to their assigned seats;
stock loadouts are preserved.

Ground controls: **W/S** accelerates/decelerates and reverses, **A/D** steers, and hold **Space**
to brake a tank or car. The mouse aims independently of the chassis. **I** opens fuel/ammunition,
**Y** dismounts, and **H** cycles additional camera positions when supplied by the content.
Placed guns retain their stock movement restrictions; **Z** cycles their camera zoom.
Network protocol 6 requires this build on both clients and servers.

Cars and tanks crush blocks according to weight: cars break vegetation and glass panes; heavy
tanks also break wood, full glass and other configured blocks. Contact can crush blocks even
while stationary. The server's `vehicles.collisionDestroyBlock` switch and `vehicles.crushing`
block/tag lists control this. Ramming hurts nearby entities except occupants and recently
dismounted riders; heavy tanks are protected unless `vehicles.collisionEntityTankDamage` is enabled.
`vehicles.collisionEntityDamage` controls damage to other targets.

Aircraft/ship aiming: **Left Ctrl toggles freelook** (press again to return to steering with
the mouse). In freelook, the pilot can aim pilot-usable turrets independently of the hull.
Hold **Ctrl+I** for the next crew seat or **Ctrl+F** for the previous one; these shortcuts
also work from the driver's seat. Project 1204's first three crew seats control the front gun,
rocket launcher and rear gun respectively. An occupied gunner seat takes control of its weapons.
**H** switches camera/gunner view where the vehicle supports it. **Ctrl+C** toggles the NPC
gunner status shown on the HUD; NPC gunners are still pending in M6, so it does not enter a
turret seat or enable a player camera.

Searchlights/headlights: **L** toggles the lights on vehicles whose definitions include them.
The pilot and first crew seat can switch them. Movable lights follow the first crew member's look,
or the pilot's when that seat is empty; fixed lights follow the hull. Stock examples include
the Mi-24PS, Bell 207, Project 1204, An-2, Growler and BNR32. Light covers and steering lamps
are also supported for addons. Beams and living-entity highlights do not change world block lighting.
**Protocol 11 requires this updated build on every client and host.**

## Content and addon packs

As in McHeli CE, vehicles, weapons, throwables and HUDs are defined in content files (YAML, or the
legacy TXT format) that the mod reads from `<game dir>/mcheli_addons/` at startup:

- The stock content is extracted to `mcheli_addons/default/` and extracted again whenever the mod
  version or stock content revision changes, so put edits in a separate addon pack.
- Every other folder, `.zip` or `.jar` in `mcheli_addons/` is an addon pack with McHeli CE's layout
  (`pack.mcmeta` with an `addon` block, content under `assets/mcheli/<type>/`). Packs load after the
  stock pack in name order, and a definition replaces an earlier one with the same name.
- Addon packs are also loaded as always-on resource packs, so their textures, models and sounds work.
- `/mcheli dumpyaml [folder]` converts loaded TXT content to YAML in `<game dir>/<folder>`.

The upstream sources the port is based on are not part of this repository. To get a reference copy
in `upstream/` (git-ignored), run:

```sh
bash scripts/fetch-upstream.sh
```

## Credits

McHeli CE and this port exist thanks to:

- **EMB4** and **Murachiki27**: the original McHeli
- **RagexPrince683**: MCHEL-O-Extended (McHeli Overdrive), which McHeli CE continues with
  RagexPrince683's full permission
- **MrNorwood** and **MovBlock**: McHeli CE (Warfactory)
- **TV90** for reforged content, and **H. M. Shrub** and **Jumboreal** for texture work
- McHeli Wingman, originally a standalone addon, which was merged into McHeli CE

The 26.2 port is maintained in
[thisisforfreeAI/26.2-port-of-mcheli-cm](https://github.com/thisisforfreeAI/26.2-port-of-mcheli-cm).

## Licence

This port is licensed under the **GNU General Public License v3.0**, the same licence as McHeli CE;
see [LICENSE](LICENSE). The text is identical to the GPLv3 file in upstream's initial commit
(`7c1b0ee`), because upstream's default branch currently has no LICENSE file (PORTING_PLAN.md,
decision D6). Built jars contain the licence as `LICENSE_mcheli_ce_port`.

The Gradle build files come from NeoForge's
[MDK-26.2-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle) template, which is MIT
licensed; see [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).

Minecraft's official names, which the code uses, are covered by Mojang's own licence; see
[NeoForm's reference copy](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
