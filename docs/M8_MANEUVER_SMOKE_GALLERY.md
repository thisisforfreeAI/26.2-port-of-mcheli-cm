# Aircraft damage smoke: native examples

Unedited 1280×720 Vulkan captures of real vehicle models under controlled HP and motion.
The aircraft hold 25% hull HP while turning, then repair. These are development fixtures,
not combat recordings. Existing engine-fire particles remain separate from the new cold smoke.

Dense cores use the approved 16-frame billow art; shorter wisps open gaps. Severe damage keeps
its cores between opacity surges. Old smoke stays in world space and drifts with the shared wind.
The remote F-15E reports zero velocity and exercises observed-motion fallback.
[Implementation and bounds](M8_MANEUVER_SMOKE.md).

## F-35A

Early turn

![F-35A — Early turn](screens/m8-maneuver-smoke/m8-maneuver-f-35a-early.png)

Changing heading

![F-35A — Changing heading](screens/m8-maneuver-smoke/m8-maneuver-f-35a-turn.png)

Older billows and dispersing wisps

![F-35A — Older billows and dispersing wisps](screens/m8-maneuver-smoke/m8-maneuver-f-35a-mature.png)

Repaired: new emission stopped, old smoke drained

![F-35A — Repaired: new emission stopped, old smoke drained](screens/m8-maneuver-smoke/m8-maneuver-f-35a-repaired.png)

## F-15E with zero reported velocity

Early turn

![F-15E with zero reported velocity — Early turn](screens/m8-maneuver-smoke/m8-maneuver-f-15e-remote-early.png)

Changing heading

![F-15E with zero reported velocity — Changing heading](screens/m8-maneuver-smoke/m8-maneuver-f-15e-remote-turn.png)

Older billows and dispersing wisps

![F-15E with zero reported velocity — Older billows and dispersing wisps](screens/m8-maneuver-smoke/m8-maneuver-f-15e-remote-mature.png)

Repaired: new emission stopped, old smoke drained

![F-15E with zero reported velocity — Repaired: new emission stopped, old smoke drained](screens/m8-maneuver-smoke/m8-maneuver-f-15e-remote-repaired.png)

## A-10

Early turn

![A-10 — Early turn](screens/m8-maneuver-smoke/m8-maneuver-a-10-early.png)

Changing heading

![A-10 — Changing heading](screens/m8-maneuver-smoke/m8-maneuver-a-10-turn.png)

Older billows and dispersing wisps

![A-10 — Older billows and dispersing wisps](screens/m8-maneuver-smoke/m8-maneuver-a-10-mature.png)

Repaired: new emission stopped, old smoke drained

![A-10 — Repaired: new emission stopped, old smoke drained](screens/m8-maneuver-smoke/m8-maneuver-a-10-repaired.png)

## Stationary A-10

Older billows and dispersing wisps

![Stationary A-10 — Older billows and dispersing wisps](screens/m8-maneuver-smoke/m8-maneuver-stopped-mature.png)

## Night lighting

Older billows and dispersing wisps

![Night lighting — Older billows and dispersing wisps](screens/m8-maneuver-smoke/m8-maneuver-night-mature.png)

## 32-aircraft shared load

Older billows and dispersing wisps

![32-aircraft shared load — Older billows and dispersing wisps](screens/m8-maneuver-smoke/m8-maneuver-shared-load-mature.png)

Classic/settings and repair-drain checks also run in the opt-in fixture. Software Mesa rendering
checks do not measure the 60 FPS target on the player’s RTX 3060 / 7800X3D at 1080p.
