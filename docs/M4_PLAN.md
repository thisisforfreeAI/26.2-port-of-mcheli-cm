# M4 plan: helicopter models, HUD and camera (0.4.0)

> **Status:** in progress. This file expands the M4 row of [PORTING_PLAN.md](../PORTING_PLAN.md) §6.
> Its decisions (D14 to D17, continuing §10's numbering) were taken as the recommended defaults so
> the work could go straight on; each can be revisited in review. Where the implementation differs
> from this plan, the difference is listed in [§9](#9-as-built).

## 1. Goal

Helicopters look and sound like upstream McHeli CE 1.5.1: their OBJ/MQO models are drawn with
their textures and moving parts on both the OpenGL and the Vulkan backend, the pilot and the crew
see upstream's HUD, and the gunner camera, zoom, night vision and third-person distance work as
upstream. M3's stand-in box renderer goes away.

The **AH-64** (`ah-64.mqo`, `heli.yml` and `heli_gnr.yml`) stays the reference. As in M3, all 34
stock helicopters share the code, so all of them render.

## 2. Scope

### 2.1 In M4

| Area | What gets ported | Upstream source |
|---|---|---|
| Model parsing | OBJ and MQO parsers, GL-free, producing per-group triangle arrays (position, UV, normal) and bounds. Upstream's rules kept: OBJ groups start only at `g $…`/`o $…`, faces before any group go to `Default`, V is flipped; MQO is scaled by 1/100, its faces are reversed, normals are smoothed per object by `facet` when `shading 1`; MQO objects without `$` merge into the preceding `$` object (`cleanUpMsqMess`); `.obj` is tried before `.mqo`. Quads are split into two triangles | `W_WavefrontObject`, `W_MetasequoiaObject`, `W_Face`, `ModelVBO#prepareGroupData`, `MCH_Models` |
| Model manager | Client resource-reload listener. By default (as upstream's default `ExperimentalAsyncOnDemandModelLoading = true`) a vehicle's models are parsed on a worker thread the first time it is drawn, nothing is drawn until then, and models unused for `onDemandModelLifetimeSeconds` are dropped; with on-demand off, every helicopter model is parsed in parallel during resource reload | `MCH_ModelManager`, `MCH_OnDemandModels`, `MCH_ClientProxy#registerModels*` |
| Part models | Each part uses the body model's `$<part>` group when it exists, otherwise a separate `<vehicle>_<part>` model file | `loadPartModel`, `MCH_RenderAircraft#renderPart` |
| Helicopter renderer | Replaces the stand-in renderer. Render state extracted per frame (slerped attitude, rotor blade angles, part rotations, throttle, texture, destroyed tint, light), then submitted with `submitCustomGeometry` (render path A). Body (`$body` or the whole model), rotors and blades (both `OldRenderMethod`s), and the common parts upstream draws for every vehicle: canopies, landing gear, hatches, light hatches, throttle parts, rotating parts, camera parts, weapon parts at their rest pose, weapon bays, wheels and steering wheels. Colour 0.75 grey (0.15 when destroyed), cull on, blending on, shadow radius 2 | `MCH_RenderHeli`, `MCH_RenderAircraft#doRender`, `renderCommonPart` |
| Render type | Our own `RenderType` on vanilla's `ENTITY_TRANSLUCENT_CULL` pipeline with the content texture (`mcheli:textures/helicopters/<texture>.png`), lightmap and overlay. Only vanilla pipelines, so it runs on Vulkan as well (§5) | `W_Render#setCommonRenderParam` |
| Riders | Crew drawn tilted with the helicopter's pitch and roll and scaled by `EntityWidth`/`EntityHeight`, as upstream's `EnableModEntityRender` | `renderRiddenEntity`, `renderEntitySimple` |
| Debug view | `TestMode` draws the old stand-in boxes over the model | `renderDebugHitBox` |
| Engine sound | Upstream's looping rotor sound: volume `throttle × 2`, pitch `0.2 + throttle × 0.2`, the passing-by pitch shift, and `soundRange` | `MCH_SoundUpdater`, `MCH_EntityHeli#getSound*` |
| HUD expression evaluator | A small evaluator for the HUD expression language (decision D4), with Aviator's semantics where stock HUDs depend on them: long and double numbers (integer division of whole numbers), booleans, `+ - * / %`, `<< >> & \| ^ ~`, comparisons, `&& \|\| !`, `?:`, hex literals, variables, and the `math.*` functions | Aviator 5.4.3 as used by `MCH_HudItem#calc` |
| HUD interpreter | All HUD commands: `If`/`Endif`, `Exit`, `Call`, `Color`, `DrawString`/`DrawCenteredString` with every `HudStringArg`, `DrawTexture` (rotated), `DrawRect`, `DrawLine`, `DrawLineStipple`, `DrawGraduation` (yaw and the three pitch ladders), `CameraRot`, `DrawEntityRadar`/`DrawEnemyRadar`; the 427×240 auto-scale option | `MCH_Hud`, `MCH_HudItem*` |
| HUD variables | Every variable of `updateVarMap`. Weapon variables keep upstream's "no weapon" values until M5; `altitude`, `sea_alt`, `stick_x`/`stick_y`, `radar_rot`, `low_fuel` blink, `hp_rto`, `fuel`, `throttle`, `hovering`, `free_look`, `gunner_mode`, `cam_mode`, `cam_zoom`; the `test_mode` variable dump | `MCH_HudItem#update*` |
| Entity radar | The client's 64-block radar sweep that feeds `DrawEntityRadar`/`DrawEnemyRadar` (`EnableEntityRadar` content) | `MCH_Radar`, `updateRadar` |
| HUD layer | A `GuiLayer` above vanilla's that draws the seat's HUD (seat 0 in gunner mode uses HUD 1), the key hints, and the night-vision noise; vanilla's crosshair, hotbar, health, armour, food, air and boss bars are hidden while riding, as upstream | `MCH_GuiHeli`, `MCH_AircraftCommonGui`, `MCH_CameraManager#onRenderOverlay` |
| Lines and points | A custom `GuiElementRenderState` that turns HUD lines into quads one GUI pixel wide (upstream's `glLineWidth(scaleFactor)`), radar points into 2×2 squares, and stippled lines into dashes from the 16-bit pattern (decision D15) | `drawLine`, `drawLineStipple`, `drawPoints` |
| Gunner camera | In gunner mode the view moves to the content's camera position (`CameraPosition`, or the seat's own), following the aircraft's attitude, with the player's pitch limited to the seat's range; zoom (Z) doubles up to `CameraZoom` and narrows the FOV | `MCH_ViewEntityDummy`, `updateCamera`, `zoomCamera`, `onFOVModifierEvent` |
| Camera modes | The camera-mode key (C) cycles normal and night vision (`EnableNightVision`). The server gives the viewer a short night-vision effect as upstream; the client applies the night-vision post effect (upstream's GLSL in vanilla's post format) and the green noise overlay | `MCH_Camera`, `switchCameraMode`, `updateCameraViewers`, `shaders/program/nightvision.fsh` |
| Third-person distance | `ThirdPersonDist` from content while riding, changed by Page Up/Page Down in steps of 4 (4 to 72) when the server allows it | `setThirdPersonDistance`, `KeyboardInputHandler#handleCameraDistance` |
| Config | The keys in §6 | `MCH_Config` |

### 2.2 Not in M4

| Area | Milestone |
|---|---|
| Weapon HUD values, weapon part animation (aiming, recoil, barrel spin, missiles on rails), turret ballistics dot, hit markers, lock-on markers, TV missile camera and noise, BVR/GPS/mortar/RWR overlays | M5 |
| Search lights, ropes for rappelling, crawler tracks and track rollers, ERA tiles, plane/ship/tank/vehicle renderers, camera position switching (planes and ships), UAV camera | M6 |
| Thermal vision (the third camera mode), render path B (GPU buffers), long-distance rendering, performance pass | M7 |

Each goes into KNOWN_ISSUES.md under "Not ported yet".

## 3. Design

```
client/model/        ← GL-free parsing and models (pure Java, unit-tested without a game)
  McheliModel          groups of triangles: float[] pos, uv, normal; bounds; containsPart
  ObjParser, MqoParser upstream's parsing rules
  ModelLoader          picks .obj then .mqo from a ResourceManager
  AircraftModels       per-vehicle body + part models, eager or on-demand, eviction
client/render/
  AircraftRenderType   the RenderType on ENTITY_TRANSLUCENT_CULL
  AircraftRenderState  everything submit needs, extracted from the entity
  AircraftRenderer     common parts (renderCommonPart) and body
  HeliRenderer         rotors, blades
  RiderPoseHandler     pitch/roll for the crew (render-state modifier + RenderLivingEvent.Pre)
client/hud/
  HudExpression        parser + evaluator (pure Java)
  HudVariables         upstream's var map, filled from the aircraft each frame
  HudInterpreter       runs HudItems against a HudCanvas (pure Java)
  HudCanvas            line/rect/points/texture/text sink; GuiHudCanvas draws through
                       GuiGraphicsExtractor + HudShapeRenderState
  AircraftHudLayer     the GuiLayer, key hints, noise, hiding vanilla layers
client/camera/
  AircraftCamera       gunner camera entity, zoom FOV, third-person distance, post effect
client/sound/AircraftEngineSound
```

The HUD interpreter and expression evaluator take no Minecraft types, so the tests can drive them
with recorded variables and a recording canvas.

### 3.1 Matrices

Part transforms reproduce upstream's `GlStateManager` call sequences on a `PoseStack` one call at a
time (`translate`, `rotate(angle, x, y, z)` as an axis-angle quaternion). The body orientation is
upstream's quaternion slerp between the previous and current attitude.

### 3.2 Camera entity

`Minecraft#setCameraEntity` takes a client-only `Marker` (zero size, so zero eye height) that is
moved to the camera position every frame, as upstream did with `MCH_ViewEntityDummy`. Its angles
come from the player through `ViewportEvent.ComputeCameraAngles`. Leaving gunner mode, the seat or
the world restores the player.

## 4. Networking

| Payload | Direction | Carries |
|---|---|---|
| `HeliControlPayload` (extended) | to server | adds upstream's camera-mode field (`switchCameraMode`, a byte, 0 = none) |
| `ServerSettingsPayload` (extended) | to client | adds `enableCamDistChange` |

The server checks the sender is in the seat whose camera mode changes and that the content allows
night vision.

## 5. Vulkan

Only vanilla render pipelines and post-effect formats are used; the post effect's fragment shader
is written in vanilla's style (`#version 330`, `layout(std140)` uniform blocks, `InSampler`) so it
compiles for both backends. The HUD uses vanilla GUI pipelines. The manual check (§8.4) runs the
client once with "Prefer Vulkan" on.

## 6. Config keys

| Upstream key | Port | Default |
|---|---|---|
| `TestMode` | CLIENT `debug.testMode` | `false` |
| `SmoothShading` | CLIENT `render.smoothShading` | `true` |
| `EnableModEntityRender` | CLIENT `render.tiltRiders` | `true` |
| `ExperimentalAsyncOnDemandModelLoading`, `ExperimentalOnDemandModelLifetimeSeconds` | CLIENT `render.onDemandModels`, `render.onDemandModelLifetimeSeconds` | `true`, `720` |
| `DisplayHUDThirdPerson`, `HideKeybind`, `ExperimentalAutoScaleAircraftGui` | CLIENT `hud.displayInThirdPerson`, `hud.hideKeyHints`, `hud.autoScale` | `false`, `false`, `false` |
| `KeyCameraMode`, `KeyCameraDistanceUp`, `KeyCameraDistanceDown` | key mappings | C, Page Up, Page Down |
| `enableCamDistChange` (`MCH_ServerSettings`) | SERVER `camera.enableCamDistChange`, synced | `true` |

`ExperimentalOnDemandModelLoaderThreads` is not ported: the port always uses virtual threads, as
upstream did on Java 21 and later.

## 7. Work order (one commit each, each building and passing tests)

1. **Docs**: this plan.
2. **Config and keys**: §6.
3. **Expression evaluator** with its tests (§8.1).
4. **Model parsing** with its tests.
5. **Model manager and helicopter renderer**, render type, riders, debug boxes, engine sound;
   stand-in renderer removed.
6. **HUD**: interpreter, variables, radar, canvas, layer, key hints, hidden vanilla layers.
7. **Camera**: gunner camera, zoom, camera modes with the payload change, night vision post
   effect and noise, third-person distance.
8. **GameTests** (§8.3).
9. **Docs**: CHANGELOG 0.4.0, KNOWN_ISSUES, README, PORTING_PLAN §6; version 0.4.0.

## 8. Exit criteria and tests

| # | Exit criterion | Proved by |
|---|---|---|
| E1 | The expression evaluator gives Aviator's result for every expression in every stock HUD | `HudExpressionTest` (§8.1) |
| E2 | The parsers produce upstream's triangles for every stock model | `ModelParserTest` (§8.1) |
| E3 | The HUD of `heli.yml` and `heli_gnr.yml` draws what upstream draws | `HudInterpreterTest` (§8.1) |
| E4 | Part transforms match upstream's GL call sequences | `PartTransformTest` (§8.1) |
| E5 | Camera modes give and remove night vision as upstream | `CameraModeGameTests` (§8.3) |
| E6 | Every stock helicopter's models load; no part is missing | `AllHelicopterModelsTest` (§8.2) |
| E7 | The AH-64 renders, animates and shows its HUD on OpenGL and on Vulkan | Manual checklist (§8.4), screenshots |
| E8 | `./gradlew build` and `runGameTestServer` pass; `runServer` boots and stops; `runClient` loads a world | Command output in the PR |

### 8.1 Pure JUnit

- **`HudExpressionTest`**: Aviator 5.4.3 (a test-only dependency, never shipped) is the oracle.
  Every expression in every stock HUD file (after `toFormula`) is evaluated by both with the same
  variables, for several seeded random variable sets, and must give the same number (booleans as
  0/1, as `MCH_HudItem#calc`); plus hand-written cases for precedence, integer division, shifts,
  hex colours, the ternary and `math.*`.
- **`ModelParserTest`**: an oracle in `src/test` transcribes `W_WavefrontObject`,
  `W_MetasequoiaObject`, `W_Face` and `ModelVBO#prepareGroupData`/`cleanUpMsqMess` line for line
  with the GL calls removed. For every stock `.obj` and `.mqo` file, both must give the same groups
  with the same vertex data (exact floats) and the same bounds; quads are compared after the
  port's split.
- **`HudInterpreterTest`**: an oracle transcribes the `execute` methods of `MCH_HudItem*` with the
  GL calls replaced by a recorder. `heli.yml`, `heli_gnr.yml` and every HUD they call are run by
  both against the same variables and canvas size (auto-scale on and off), and must record the
  same lines, rectangles, points, textures and strings.
- **`PartTransformTest`**: the port's part transforms against upstream's call sequences for a
  rotor blade (both render methods), a canopy, a sliding hatch, landing gear and a throttle part.

### 8.2 JUnit with the game loaded

- **`AllHelicopterModelsTest`**: every stock helicopter's body model and every part model it needs
  resolve (in the body or as a file), and their texture exists.

### 8.3 GameTests

- **`CameraModeGameTests`**: the pilot of a night-vision helicopter switching to night vision gets
  the night-vision effect, keeps it while in that mode, and loses it on switching back; a
  helicopter without `EnableNightVision` refuses the switch; a passenger cannot switch the pilot's
  camera.

### 8.4 Manual checklist (Xvfb with Mesa; screenshots in `docs/screens/m4-*.png`)

| # | Check |
|---|---|
| M1 | Place an AH-64: the model and texture show; the rotors turn with the throttle; the canopy opens on boarding |
| M2 | Fly: the body pitches and rolls smoothly; the crew tilts with it; the engine sound follows the throttle |
| M3 | The pilot's HUD shows upstream's `heli.yml` layout (heading tape, pitch ladder, altitude, speed, fuel and throttle gauges, HP bar) |
| M4 | Gunner mode (H): the view moves to the nose camera with `heli_gnr.yml`; Z zooms; C toggles night vision |
| M5 | Third person: the camera sits at `ThirdPersonDist`; Page Up/Down change it |
| M6 | Destroyed helicopter draws dark |
| M7 | Repeat M1 to M4 with "Prefer Vulkan" on (Mesa lavapipe) |
| M8 | Three other stock helicopters (an OBJ one, one with fold blades, a tandem rotor) render |

## 9. Decisions

| # | Question | Default taken |
|---|---|---|
| D14 | Gunner camera: replace the camera entity, as upstream, or offset the camera in an event? | **Camera entity** (`Minecraft#setCameraEntity`, PORTING_PLAN §5.7): it moves culling, sound listener and FOV with the view, as upstream's dummy did |
| D15 | Stippled HUD lines (PORTING_PLAN §8, item 3) | **Dashes from the 16-bit pattern**, one pattern bit per `factor` GUI pixels along the line, starting at each segment |
| D16 | Upstream's on-demand model loading is on by default; keep that default? | **Yes**: a world with one helicopter parses one model, and the first frame of a new helicopter shows nothing, as upstream |
| D17 | Crew tilt (`EnableModEntityRender`) needs a render-state modifier and a `RenderLivingEvent.Pre` hook; port it in M4? | **Yes**, since a pitched helicopter otherwise shows its crew upright through the fuselage |

## 10. As built

What changed from this plan while implementing it:

- Classes: the renderer is `HeliRenderer` with the part maths in `AircraftPartPoses` (pure, so
  `PartTransformTest` can call it) on a `GlMatrixStack` with `glRotatef`'s semantics;
  `AircraftRenderTypes`, `RiderTilt`, `ModelVertices` and `AircraftDebugBoxes` fill the other
  roles. The camera is split in two: `AircraftCamera` (upstream's `MCH_Camera`, on both sides,
  in `entity/aircraft` with `AircraftRadar`) and `client/camera/AircraftCameraClient` (the view
  entity, zoom, post effect and third-person distance). `HudShapeRenderState` draws the HUD's
  lines, points and rotated textures as GUI quads.
- `GlMatrixStack` builds `glRotatef`'s matrix in doubles: `PartTransformTest` found that JOML's
  float rotations lose precision near half turns.
- The view entity is a `Marker` that keeps the rider's eye height and sits that far below the
  camera, so vanilla's eye-height smoothing does not slide the view when it switches.
- The pilot's first-person eye is placed as upstream's `onCameraSetupEvent` puts it (turned with
  the vehicle's attitude, or with the camera roll while hovering or in free look). It was missed
  at first and reported by the user (the view slid back in the cockpit in forward flight). There
  is no camera-position event in NeoForge 26.2, so an access transformer opens
  `Camera#setPosition` and the eye is placed from the FOV event, the first one after vanilla
  positions the camera.
- Crew gunner mode (upstream's `gunnerModeOtherSeat`) and the camera-mode key for crew seat 1 were
  needed for `heli_gnr` and the key hints, so they came with M4. Destroying a helicopter puts its
  crew's cameras back to normal, as upstream.
- No stock helicopter uses `OldRenderMethod`; `PartTransformTest` switches the AH-64's rotors to
  it. `AllHelicopterModelsTest` found two parts that upstream's own model files lack (AH-1Z
  `canopy`, UH-1C `camera0`); they are listed as upstream content issues.
- Manual checks (§8.4) ran under Xvfb with Mesa, on OpenGL (llvmpipe) and on Vulkan (lavapipe),
  with a temporary scripted driver that was not committed. Both backends gave the same pictures
  and the same values: the view entity takes over in gunner mode, 2x zoom halves the FOV (70 to
  35), night vision sets the post effect and the potion effect and removes both, Page Up moves the
  third-person camera from 16 to 24, and leaving resets it to 4. Two things needed for Vulkan in
  this environment, neither in this mod: NeoForge's early loading window has to be off
  (`earlyWindowControl = false`), and the game resets "Prefer Vulkan" after any startup it did not
  finish, so `--graphicsBackend vulkan` was passed. No sound device under Xvfb, so the engine
  sound in M2 was not heard.
- Not done in M4, as planned: thermal vision (M7), weapon HUD values and weapon part animation
  (M5), search lights, camera position switching and the UAV camera (M6).
