# M6b plan: Wingman

Upstream's `com.norwood.mcheli.wingman` package (about 10,000 lines) adds marker blocks for
airbases, AI wingmen that fly in formation, an autonomous mission state machine, taxi routes, the
base and planner screens and the `/wingman` command. PORTING_PLAN.md section 6 sets the milestone's
exit criterion: formation and one CAP mission fly end to end. Bounded UAV control streaming was
brought forward into M6 (M6_PLAN.md, "UAV 5,000-block control increment").

## Increments

1. Marker screen and registry (this increment).
2. Taxi routes and the base screen (`TaxiRoute`, `TaxiRouteRegistry`, `GuiBaseConfig`, `GuiNewRoute`).
3. Wingman entries, formation flight and the wingman panel (`WingmanEntry`, `WingmanTickHandler`,
   `GuiWingmanPanel`, `WingmanKeyHandler`).
4. Missions and autonomous flight (`MissionPlan`, `MissionNode`, `AutonomousFlightHandler`,
   `GuiWingmanPlanner`), with mission chunk loading.
5. The `/wingman` command and `WingmanConfig`.

## Marker screen and registry increment

- `WingmanMarkerBlockEntity` (upstream `WingmanMarkerTileEntity`) keeps the marker ID, parent base
  and parking heading; the type stays in the block state (KNOWN_ISSUES difference 18). A new
  marker is a parking spot, as upstream's block entity started, and takes an ID of its type
  (`p_1`, `wp_2`...) with upstream's chat message to a player within 16 blocks.
- `MarkerRegistry` is per-dimension saved data with upstream's lookups (`findById`, `findByType`,
  `findByBase`, `findAllByBase`, `findChildren`, `resolveMarker`). The parking-spot searches, which
  need the wingman AI, come with increment 3. Markers register when they load and leave the
  registry when their block goes, including through `/setblock`, which skips the block's removal
  hooks; a chunk unloading does not remove them.
- Right-clicking a marker opens `MarkerConfigScreen` (upstream `GuiMarkerConfig`) with upstream's
  layout and labels, including the Japanese heading labels. Save sends `MarkerUpdatePayload`,
  which the server applies only for a player who can reach and edit the block (KNOWN_ISSUES 93).
  A base marker opens the same screen until the base screen arrives.
- Network protocol 22.

Validation: three GameTests cover placement, IDs, removal through `/setblock`, the update's reach
check and per-type rules, the payload codec, base resolution and the registry's save codec; all
228 required GameTests pass. In the client, a placed marker announced `[Parking] id=p_1`, Tab moved
between the fields, switching to Runway-A dropped the heading row and kept the typed text, and Save
set the block state to `runway_a` with the ID and base (screens `m6b-marker-parking.png`,
`m6b-marker-runway.png`).

![Parking marker](screens/m6b-marker-parking.png)
![Runway end](screens/m6b-marker-runway.png)
