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

## Wingmen and wingman panel increment

- `WingmanEntry` and `WingmanRegistry` keep upstream's per-wingman state (leader, formation slot,
  attack mode, manual target, weapon type and seat) in memory; the registry is cleared when the
  server stops, as upstream's static maps were.
- `WingmanTickHandler` ports upstream's formation and attack flight: V and column slots from
  `WingmanConfig`'s side, rear and altitude distances, rate-limited yaw and pitch, sibling
  repulsion, the slot boost that catches a wingman up with a fast leader, the hold when the leader
  stops, automatic targeting of hostile mobs, target drones and enemy-team gunners, orbit,
  approach and overfly attack paths, weapon choice by type and the post-fire escape. A wingman
  whose leader has no player pilot holds fire, and one whose leader is gone leaves the registry.
- Helicopter wingmen fly by a hover-relative throttle and nose-down pitch with braking, where
  upstream flew them like planes and they climbed away or flew backwards (KNOWN_ISSUES 97).
  Wingmen fire as themselves, with the target passed to seekers (KNOWN_ISSUES 96).
- The wingman panel (upstream `GuiWingmanPanel`) opens from the numpad 0 key while piloting, with
  a key hint on the HUD. Its Wingmen tab lists up to 8 unassigned aircraft nearby with Follow, and
  the leader's wingmen with Auto, Hold, Stop and a weapon type picker; its Formation tab sets the
  formation and attack altitude limits. `WingmanPanelOpenPayload`, `WingmanPanelDataPayload` and
  `WingmanPanelActionPayload` replace upstream's packets; the server checks every order
  (KNOWN_ISSUES 95).
- Missions, autonomous flight, the base screen's Mission tab, `/wingman` and the config file come
  with increments 4 and 5.
- Network protocol 23.

Validation: two GameTests cover the panel's candidate list, Follow and its limits, the free slot,
leader-only orders, formation clamps and the codecs, and the flight: formation slots, the
helicopter throttle and pitch, the hold, the slot boost, the enemy set, attack throttle, the
auto-hold without a pilot and unregistering after the leader goes; all 231 required GameTests
pass. In the client, an AH-64's panel listed two nearby AH-64s, Follow made them wingmen #0 and #1
following, and they climbed and descended with the leader and settled in their slots; a wingman
moved 44 blocks off its slot flew back and stopped there. Changing the side distance on the
Formation tab and pressing Apply kept the new value when the panel reopened.

![Wingman panel](screens/m6b-wingman-panel.png)
![Formation tab](screens/m6b-wingman-formation.png)
