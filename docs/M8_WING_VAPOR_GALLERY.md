# Wingtip vapor: native examples

Unedited 1280×720 Vulkan captures of real vehicle models on held wet-turn trajectories.
These are controlled previews, not combat recordings. Vapor is deliberately thin: look for
the pale strands curving behind the wing tips. The blue diagonal streaks are vanilla rain.

Vapor follows actual supported tips, stays in world space and disappears after straightening.
The F-15E reports zero velocity and uses observed motion. The lost-wing scene stops the missing
left emitter while retaining the right tip. [Implementation and settings](M8_WING_VAPOR.md).

## F-35A

![F-35A — turn](screens/m8-wing-vapor/m8-vapor-f-35a-turn.png)

![F-35A — close](screens/m8-wing-vapor/m8-vapor-f-35a-close.png)

## F-15E — observed-motion fallback

![F-15E — observed-motion fallback — turn](screens/m8-wing-vapor/m8-vapor-f-15e-remote-turn.png)

![F-15E — observed-motion fallback — close](screens/m8-wing-vapor/m8-vapor-f-15e-remote-close.png)

## A-10

![A-10 — turn](screens/m8-wing-vapor/m8-vapor-a-10-turn.png)

![A-10 — close](screens/m8-wing-vapor/m8-vapor-a-10-close.png)

## After the weather clears

![After the weather clears — later](screens/m8-wing-vapor/m8-vapor-dry-turn-later.png)

## Left wing lost: surviving right tip only

![Left wing lost: surviving right tip only — close](screens/m8-wing-vapor/m8-vapor-lost-wing-close.png)

## 32-aircraft shared load

![32-aircraft shared load — turn](screens/m8-wing-vapor/m8-vapor-shared-load-turn.png)

Native checks also cover straight flight, configuration and Classic transitions, smooth drain
and shared limits. Software Mesa does not establish the 60 FPS target on the player’s PC.
