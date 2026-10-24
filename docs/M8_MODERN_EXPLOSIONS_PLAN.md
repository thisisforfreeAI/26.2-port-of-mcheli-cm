# Toggleable modern explosion style — implementation plan

Status: software implemented; see [implementation and validation](M8_MODERN_EXPLOSIONS.md).
The current M8 crater-scale/3D effects remain the Off baseline. This document retains the
original plan; target-PC performance and player artistic acceptance remain separate checks.

## Goal and fixed constraints

Offer a modern, more volumetric-looking explosion style as a client-local choice reachable
from ESC. Preserve the existing explosion craters: weapon damage, block selection, ignition,
destruction, projectile behavior and crater formation must remain unchanged. The primary
visual goal is smoke/dust/debris proportionate to those original craters.

The renderer will build on layered particles, crossed smoke planes and 3D accents already
implemented in M8. The generated comparison is art direction, not a promise of identical
in-game rendering or full ray-marched volumetric smoke. Target remains 60 FPS on RTX 3060
8 GB / Ryzen 7 7800X3D / 1920×1080, subject to actual hardware measurement.

## 1. Pause-menu entry and saved choice

Add **MC Heli Effects…** to the pause menu through the client `ScreenEvent.Init.Post` hook
for `PauseScreen`, opening an effects panel with the pause screen as its parent.

| Control | Behavior |
|---|---|
| Modern explosions: Off / On | Off = current M8 style; On = new modern presentation |
| Detail: Low / Balanced / High | Reuse the existing particle/geometry budget setting |
| Adaptive detail: Off / On | Reuse the existing adaptive effects controller |
| Frame-rate goal | Reuse the existing setting; default 60 FPS |
| Done / ESC | Save and return to the originating pause screen |

Use a separate `effects.modernExplosions` boolean, initially **false**, in `ClientConfig`.
Do not repurpose the existing `EffectStyle.CLASSIC/POLISHED` setting: it also controls other
M8 effects and vehicle shading. Off must preserve the current M8 appearance, rather than
silently selecting upstream Classic for the whole mod. Preserve all existing config values.

The panel will show the saved preference and whether the modern renderer is active. Existing
global Classic settings remain authoritative: explain that modern explosions require Enhanced
effects and provide access to the existing full config screen. Do not silently switch vehicle,
flare, tracer or rotor-wash presentation when changing this explosion-only preference.

The choice applies to newly spawned explosion effects immediately, without a restart or
resource reload. Already-visible effects finish in their birth style; do not clear all particles
or abruptly replace clouds. Save through the normal client config path and retain the value
across world changes/restarts. No server command, server authority or new network payload.

## 2. Establish one explosion-style selection boundary

Add a small presentation policy near `ExplosionEffects` / `ExplosionCloudEmitter` that chooses
current or modern rendering using the new preference and existing Enhanced/Classic gates.
Keep gameplay power/style/direction/affected-footprint inputs identical for both paths.
Capture each particle's art/style at birth to make hot switching predictable.

Keep existing flash/tracer reserves, terrain-read caps, finite geometry limits, distance LOD
and adaptive pressure. Low and adaptive pressure simplify modern geometry/lighting while
retaining its recognizable smoke/impact silhouette. The saved On preference must not flip Off
when the controller sheds detail; restore optional detail gradually through the existing gate.

## 3. Modern smoke and fire art

Add modern sprite sheets under distinct resource identifiers; retain current textures intact.
Preload both art sets through the particle atlas so menu switching needs no resource rebuild.
Respect resource-pack overrides and choose a defined fallback if an optional modern sprite
is missing. Verify the style at native game scale rather than matching a concept render pixel
for pixel.

Modern characteristics:

- Irregular shaded billows with soft, broken edges and varied silhouettes.
- Large dense cores plus smaller, shorter-lived wisps, shared wind and outward/upward roll.
- Nearby crossed smoke planes and offset layered fire for depth as the camera moves.
- Brief hot impact, restrained amber light inside early smoke, then ordinary ambient shading.
- Tapered dirt columns, spinning/bouncing terrain fragments and low dust wedges integrated
  with the surface and reported crater footprint.

Rework first the smoke silhouette and impact-to-cloud timing; tune lighting and secondary
accents once the silhouette reads well from ground, side and overhead views. Avoid extra
full-screen effects or dynamic world lights as prerequisites for this style.

## 4. Weapon tuning and crater proportions

Tune small rockets/missiles first, then heavy bombs and penetrators, then incendiaries and
water impacts. Small blasts need a compact readable punch; large blasts need mass and slower
smoke development. Incendiaries retain longer fire; penetrators emphasize ground/debris.

Use the existing robust reported-footprint estimate for coverage, with bounded profile/quality
multipliers. Do not enlarge every flash to match the smoke, turn every small rocket into a
large fireball, or alter block destruction to fit a visual. Ground material still determines
pale sand dust, dark soil, stone chips or water response. Keep tracer-only bullet glow.

## 5. Verification and acceptance

- Unit checks: style selection including global Classic, default/save migration behavior,
  finite crater scaling, admission charges and geometry shedding/fallback.
- Native UI checks: open from ESC, toggle repeatedly, use Done/ESC, confirm persistence after
  restarting, resize/change GUI scale, and verify keyboard focus/readable labels. Place the
  entry without covering Options, Resume, multiplayer controls or other mods' buttons.
- Native Vulkan and OpenGL comparison scenes: current vs modern, identical weapon inputs
  and terrain/craters, including small blasts, heavy bombs, penetrators, incendiaries and water.
- Assert camera position/angles before side/overhead captures; label mockups separately from
  actual in-game screenshots. Test from vehicle cockpit and external/spectator cameras.
- Mixed-combat checks with both styles: fixed/adaptive detail, bounded work, quality transitions,
  world/disconnect cleanup and transient effect drain. Exercise resource-pack reload/fallback.
- Measure on the target PC at 1080p, including baseline, isolated blasts and overlapping combat.
  Record frame-time percentiles and adaptive pressure; Mesa checks establish functionality,
  not RTX 3060 FPS acceptance.

## Delivery order

1. Pause-menu entry, effects panel, persisted preference and style-selection boundary; default
   Off, with the current renderer preserved. Add an opt-in UI test driver.
2. Modern smoke/fire resources and renderer, using existing depth primitives and budgets.
3. Per-weapon/crater/material tuning, side-by-side native captures and compatibility/performance
   validation. Keep modern available as an explicit opt-in during player artistic acceptance.

Implementation will primarily touch `ClientConfig`, the client menu/event code, the explosion
presentation policy/emitter/particles, separate modern atlas resources/localizations, and tests.
The existing NeoForge Mods config factory remains available alongside the new ESC entry.
