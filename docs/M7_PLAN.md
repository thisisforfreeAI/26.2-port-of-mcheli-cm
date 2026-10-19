# M7 plan: Polish

PORTING_PLAN.md section 6 lists M7's scope: the remaining `/mcheli` commands, sound and particle
parity, GPU-buffer model rendering, long-distance tracking and rendering, HBM:NTM stubs, thermal
vision and a performance pass. Its exit criterion is no crash in a 30-minute mixed-vehicle soak.

## `/mcheli` command increment

- `McheliCommand` ports upstream's `MCH_Command` with Brigadier: `list`, `status entity|tile [min]`,
  `killentity`, `removeentity`, `attackentity <name> <damage> [source]`, `fill <from> <to> <block>
  [replace|destroy|keep|override]` (up to 3,000,000 blocks), `title <seconds> <position> <message>`,
  `delayhitbox [ticks]`, `showboundingbox true|false`, `reconfig` and the existing `dumpyaml`.
- The server config's `[commands]` section ports upstream's `EnableCommand`, `CommandPermission`
  (players who may use a subcommand without operator permission) and `EnableDebugBoundingBox`,
  which the server settings now carry to clients.
- `TitlePayload` and `TitleOverlay` replace `PacketTitle` and `MCH_GuiTitle`: the message is drawn
  at twice the chat's scale on a dark band, at upstream's five positions, and fades in and out.
- `sendss <player>` and `modlist <players>` send the player's client a `CommandRequestPayload`.
  The client answers a screenshot with `ScreenshotChunkPayload`s, a 30,000-byte piece of the PNG
  each tick, or a refusal while `privacy.allowScreenCapture` is off (decision D7), and the mod
  list with a `ModListPayload`, empty unless its `debugLog` is on. `ClientRequests` on the server
  takes answers only from the player asked, a screenshot only in order, up to 16 MB and as a PNG,
  saves it to `screenshots_op`, tells the sender, and drops requests nobody answers.
- Differences: KNOWN_ISSUES 102. Network protocol 28.

Validation: JUnit covers the permission entries and the status counts; two GameTests cover
permissions, the command switch, `delayhitbox`, `showboundingbox`, `title`, `reconfig` and
`list`, then `status`, `attackentity`, `killentity` and `removeentity` by class name and type ID
(never players) and `fill` in each mode, with containers emptied without drops and the size
limit. All 241 required GameTests pass. In the client, against a dedicated server, `title` showed
a gold message at the centre, and with `showboundingbox false` F3 + B no longer drew the pig's
hitbox until `showboundingbox true`.

![/mcheli title](screens/m7-title.png)

A third GameTest covers `sendss` and `modlist`: players without the mod are skipped, answers from
another player, out of order or not PNG are refused, a refusal and an unshared mod list end the
request, and a screenshot sent in two pieces is saved byte for byte. All 242 required GameTests
pass. In the client, against a dedicated server, both were refused with the settings off; with
them on, `sendss` saved the player's view (226 KB) and `modlist` showed the mods in chat.

![/mcheli modlist](screens/m7-modlist.png)

## Projectile chunk loading

`ProjectileChunkLoader` ports upstream's class of the same name with a NeoForge ticket controller.
A projectile whose weapon sets `CanLoadChunks`, or any projectile while the server config's
`projectiles.chunkLoad` is on (upstream `ProjectileChunkLoad`, off by default), leases its own
chunk and the line ahead of it each tick: 4 chunks, and up to 2 more for fast rounds. A lease
lasts `projectiles.chunkLeaseTicks` (upstream `ProjectileChunkLeaseTicks`, 40) after the last
request, so a burst shares its chunks; a sweep after each level tick releases the lapsed ones.
Upstream was bound by Forge's ticket budget, 200 tickets of 25 chunks; the port caps a dimension
at the same 5,000 chunks. The tickets are dropped when the world loads again.

Validation: JUnit covers the path (own chunk only when still or vertical, 72 blocks ahead at 1
block a tick, at most 96). A GameTest fires a stock rocket with the setting off (no lease), then
on (its chunk and the chunk 4 ahead are leased, the one behind is not), and checks the leases run
out after the rocket is gone. All 243 required GameTests pass. No stock weapon sets
`CanLoadChunks`, so nothing changes with the default settings.

## Turret ballistics sight

`AircraftHudLayer#drawTurretBallistics` ports upstream's `drawTurretBallistics` and
`drawDetachedTurretDot`, which every vehicle GUI called after its HUD. It draws a 4-pixel cross at
the centre of the screen and, at the current weapon's aim point, a 32-segment circle as wide as
the weapon's `Accuracy` cone at that distance (at least 2 pixels), with a cross in it when wider.
`WeaponBase#getTrajectoryTarget` ports `getWeaponTrajectoryTarget`: the shot is stepped from the
weapon's mount for up to 256 ticks or its time fuse, with gravity and drag in air and water, and
the dot goes where it meets a block, or at the top of its arc when it meets nothing while
climbing or aimed upwards. The point is projected with the camera's matrices, as the CCIP ring.
Upstream drew with `ONE_MINUS_DST_COLOR, ONE_MINUS_SRC_COLOR`; the port uses vanilla's
`GUI_INVERT` pipeline, the same blend. Upstream's detached weapon aim is not ported, so the shot
always starts from the weapon's mount on the vehicle.

Validation: a GameTest checks the aim point of an M230 at a wall (on the wall's face), of a 60 mm
mortar (on the wall, lower), and of an M230 aimed at the open sky (the top of its path, 500+
blocks up). All 246 required GameTests pass. In the client, riding an M1A2 towards a stone wall,
the green cross showed at the centre and the gun's dot just below it on the wall.

![Turret ballistics sight](screens/m7-turret-ballistics.png)
