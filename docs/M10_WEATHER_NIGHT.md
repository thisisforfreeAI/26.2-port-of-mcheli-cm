# M10e: weather and night on flight and sensors

Part of the M10 plan (gameplay). Rain, storms, snow and night change how vehicles fly and how
far sensors reach. Everything is worked out from the level's own weather and time, which every
client has too, so the side that moves a vehicle and the side that checks it agree. M8's
`VehicleWeather` stays the visual layer and is unchanged. Code: `weather/FlightWeather`.

## Conditions

At the vehicle's (or sensor's) block: rain or snow falls there (`Level.precipitationAt`, so under
cover or in a dry biome it does not), a storm is rain with `isThundering`, snow is the biome's
precipitation, night is `isDarkOutside`. Dimensions without weather stay clear.

## Flight

| Condition | Effect |
|---|---|
| Rain | Gusts drift a flying helicopter or plane 0.02 to 0.05 blocks a tick, rising and falling over a few seconds and turning slowly; up to twice that from sea level to 128 blocks above it |
| Storm | Gusts double; the rider's camera shakes by about a degree |
| Snow above the clouds (`icingHeight`, 192) | Ice builds over 30 s and thaws over 60 s elsewhere; full ice costs 10% of top speed |
| Night | No change |

Gusts are added to the move the flight model makes, on whichever side simulates the vehicle, and
use the game time and the vehicle's UUID, so both sides compute the same drift. Vehicles on the
ground or in water, destroyed ones, ground vehicles and ships don't gust. Ice is worked out on the
server once a second and synced.

## Sensors

| Condition | Radar (missiles, radar feed, BVR) | Heat seekers | Eye (other seekers, AI pilots) |
|---|---|---|---|
| Rain | 85% | 80% | 85% |
| Storm | 70% | 60% | 70% |
| Snow | 85% | 85% | 85% |
| Night | 100% | 100% | 60%, unless night or thermal vision on that seat, the vehicle's searchlight, or a lit target (its own searchlight, or block light 8+) |

- Lock ranges (`WeaponGuidanceSystem`) use the factor at the shooter. Active radar missiles scan
  that much less far.
- The radar feed reaches less far for a player in rain. A BVR lock must be within that range.
- Lightning within 64 blocks blanks a vehicle's radar for 2 seconds: no feed, no BVR lock.
- AI wingmen (M10d) look for targets by eye, so rain and night cut their search range too.

## HUD

A line at the right, under the ground crew line: `GUST` or `STORM`, `ICE n%`, `NIGHT`,
`RADAR OFF`.

## Config

Server config `[weather]`: `enabled` (false gives upstream's clear skies), `radarRain`,
`radarThunder`, `irRain`, `irThunder`, `irSnow`, `visualNight`, `gustMin`, `gustMax`,
`icingHeight`, `iceSlow`.

## Tests

- `FlightWeatherTest` (unit): the factor table, gust bounds (none when clear, doubled by height
  and storm, different per vehicle), icing growth, thaw and speed loss, turbulence only in storms,
  the HUD words.
- `weather_rain_and_storm_on_sensors_and_flight` (GameTest): in the test level, rain cuts a heat
  seeker to 80%, a radar seeker to 85% and the radar feed to 85%, and gusts a flying AH-64; a
  storm cuts them to 60% and 70%; the setting off restores them; lightning 22 blocks away blanks
  the radar, which counts down; ice thaws.

## Not tested

No client was run: the gust feel, the camera shake and the HUD line are unseen. Night, snow and
icing were checked only with the unit tests, as the test level has no snowy biome and its time
can't be set safely while other tests run. Gusts with client-side movement were not checked
against a real server and client.
