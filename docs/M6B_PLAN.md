# M6b plan: Wingman

Upstream's `com.norwood.mcheli.wingman` package (about 10,000 lines) adds marker blocks for
airbases, AI wingmen that fly in formation, an autonomous mission state machine, taxi routes, the
base and planner screens and the `/wingman` command. PORTING_PLAN.md section 6 sets the milestone's
exit criterion: formation and one CAP mission fly end to end. Bounded UAV control streaming was
brought forward into M6 (M6_PLAN.md, "UAV 5,000-block control increment").

## Increments

1. Marker screen and registry.
2. Taxi routes and the base screen's Taxi Routes tab (`TaxiRoute`, `TaxiRouteRegistry`, `GuiBaseConfig`).
3. Wingman entries, formation flight and the wingman panel (`WingmanEntry`, `WingmanTickHandler`,
   `GuiWingmanPanel`, `WingmanKeyHandler`).
4. Missions and autonomous flight (`MissionPlan`, `MissionNode`, `AutonomousFlightHandler`,
   `GuiWingmanPlanner`, `GuiNewRoute`, the base screen's Mission tab), with mission chunk loading.
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
- Network protocol 22.

Validation: three GameTests cover placement, IDs, removal through `/setblock`, the update's reach
check and per-type rules, the payload codec, base resolution and the registry's save codec; all
228 required GameTests pass. In the client, a placed marker announced `[Parking] id=p_1`, Tab moved
between the fields, switching to Runway-A dropped the heading row and kept the typed text, and Save
set the block state to `runway_a` with the ID and base (screens `m6b-marker-parking.png`,
`m6b-marker-runway.png`).

![Parking marker](screens/m6b-marker-parking.png)
![Runway end](screens/m6b-marker-runway.png)

## Taxi routes increment

- `TaxiRoute` is a record with upstream's fields, saved names and legs (`fullDeparture`,
  `fullArrival`, `effectiveArrivalEntry`). `TaxiRouteRegistry` is per-dimension saved data keyed
  by route ID in save order, with upstream's lookups (`getForBase`, `findByParking`,
  `findByRunway`, `findById`).
- A base marker opens `BaseConfigScreen` (upstream `GuiBaseConfig`) on its Taxi Routes tab, from
  `OpenBaseScreenPayload`: the base's routes and its markers by type, with waypoints that lack a
  base still offered within 500 blocks, as upstream. The tab keeps upstream's layout, scrolling,
  highlights, departure and arrival previews, picker rules (end B as the end without an end A, a
  helipad as parking and end) and its client-side checks with their Japanese messages.
- `TaxiRouteActionPayload` replaces `PacketBaseAction`'s route actions; the server checks the
  player can edit the base marker and takes the base from it (KNOWN_ISSUES 94). The Mission tab
  button is shown but disabled until increment 4; the nearby-aircraft list upstream sent for it
  is left out of the payload.

Validation: a fourth GameTest covers the base screen's marker lists, the 500-block waypoint
fallback, saving under the marker's base, the reach check, a route missing its parking spot,
deleting only the base's own routes, the legs and both codecs; all 229 required GameTests pass.
In the client a base `alpha` with two parking spots, both runway ends, a helipad and two
waypoints showed them all; a route from `p_a1` through `wp_1` and `wp_2` to `ra_1` saved with
"Route r1 saved.", and reopening the screen listed and reloaded it with its picks highlighted.

![Base screen](screens/m6b-base-routes.png)
![Waypoint editor](screens/m6b-base-waypoints.png)
