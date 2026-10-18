# M8 aircraft engine exhaust

Enhanced helicopters can emit short, faint turbine wisps from explicit model-space outlets.
Stock AH-64, MH-60G and MH-60L DAP include two outlets placed at their engine exits using the
stock MQO geometry. Other helicopters need declared outlets; the emitter never guesses engines
from rotor positions. Planes use explicit outlets first, then existing nozzle definitions, then
existing exhaust-flame attachment points when the nozzle is not deflected. VTOL nozzle exhaust
keeps the original direction formula, including pitch, roll and nozzle angle.

Intensity follows throttle from off at 0.1 to full at 0.9, and fades at the distance boundary.
Healthy exhaust uses neutral wisps, not damage smoke. High-throttle jet outlets also receive a
tiny four-tick warm accent; this is not a new afterburner state. Existing addon flame meshes stay
available. Wisps and hot accents inherit aircraft motion instead of leaving stationary glow dots.
Destroyed or fuel-starved engines and submerged outlets do not emit the new effect.

## Content

YAML, under `Render`:

```yaml
EngineOutlets:
- Position: [1.16, 2.51, -5.10]
  Direction: [0.15, 0.08, -1.0]
  Size: 1.1
  Jet: false
```

Position and direction are in model coordinates before aircraft rotation. The existing render
offset is added in world space. Direction defaults to rearward `[0,0,-1]` and is normalized;
Size defaults to one and Jet to false. Finite coordinates/directions and a nonzero direction are
required, with size in `(0,4]`. TXT equivalent:

```text
AddEngineOutlet = 1.16,2.51,-5.10,0.15,0.08,-1.0,1.1,false
```

YAML conversion preserves these fields. The parity baseline explicitly records the three stock
YAML additions; pinned legacy fixtures are unchanged.

## Cost and compatibility

- Consider at most four outlets and process at most two per eligible vehicle tick, round-robin.
  Eligibility is every 4/2/1 ticks on Low/Balanced/High, multiplied by 1–4 under adaptive pressure.
- Global emission caps are 8/16/24 particles per world tick, including hot accents, in addition
  to the existing shared weighted smoke admission/live limits. Weapon flashes retain their reserve.
- Aircraft-origin ranges are 32/64/96 blocks, with fading over the last 40%. Duplicate calls in
  one game tick do not emit twice. A bounded 128-entry ID/timestamp cache resets with the world.
- Wisps last 18 ticks for turbines and 12 for jets; hot accents last four. Radius is at most 1.36
  blocks and charges final size. Particles use existing original wash/glow art, cached local
  lighting and existing translucent/additive pipelines. No extra shader, scene copy or distortion.
- At most two loaded outlet-fluid checks per eligible vehicle tick; no terrain searches, particle
  collisions or nearby-entity wind scans. Early throttle, distance, period and global-cap gates
  avoid work before outlet allocation and world queries.
- Classic uses the previous plane nozzle emitter. Enhanced supported planes replace only that
  nozzle smoke, avoiding duplicate exhaust. Landing dust, damage smoke, splash, ships, flight,
  fuel use, weapons and network protocol are unchanged.

## Validation

Policy tests cover explicit-outlet priority, four-outlet bounds, undeclared helicopter behavior,
downward VTOL direction, throttle shutoff and quality/pressure limits. A TXT-to-YAML test checks
direction, size and jet intent. Existing formula and content-parity tests remain enabled.

The opt-in `EngineExhaustProbe` uses real textured stock models held still, with common effect
sink calls. Client fixtures supply fuel and multipart IDs normally received through entity spawn
packets. It checks off/fuel-starved, Apache high/low throttle, Black Hawk, jet day/night, VTOL,
Classic fallback, water, duplicate calls, expiry/shared limits and eight-aircraft overlap. Empty
and matching models-with-engines-off controls distinguish scene/model cost from exhaust. A frame
window extends if needed to collect at least 48 software-renderer samples. The Classic scene
checks fallback selection; its held fixtures do not run the old plane physics/particle tick.

Run with Java 25, `-I scripts/visual-probe.init.gradle`, and `-Dmcheli.probeEngineExhaust=true`.
`-Dmcheli.probeBackend=OPENGL` selects OpenGL, otherwise Vulkan is used.
`-Dmcheli.probeExhaustPerfOnly=true` runs just the four frame-sampling scenes.
The development driver is excluded from the production jar.

The environment's optional JEI Maven host is blocked, so local builds use an external init
script excluding that integration; repository JEI configuration is unchanged. Software Mesa
does not certify **80 FPS on RTX 3060 8 GB / 7800X3D at 1080p**; hardware acceptance remains open.
