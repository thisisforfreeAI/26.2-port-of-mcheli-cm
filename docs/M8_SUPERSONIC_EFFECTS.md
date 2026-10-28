# Transonic condensation and flypast booms

Explicitly supported jets gain a brief segmented condensation cone while accelerating through
a cosmetic transonic band in rain. The cone follows the airframe for 14 ticks, uses ambient
lighting and disperses quickly. Dry flypasts still produce the observer's boom.

McHeli's flight speeds are compressed for Minecraft. `Render.MachOneSpeed` therefore declares
an authored **game-speed mapping** in blocks per tick before `allPlaneSpeed` is applied.
It controls presentation only. It is not a measurement of real-world Mach or a flight-force
parameter. Zero is the default, leaving unsupported aircraft without the effect.

| Aircraft | Authored mapping |
| --- | ---: |
| F-35A | 0.75 |
| F-35B / F-35C | 0.75 |
| F-15E | 0.44 |
| F-15S/MTD | 0.55 |
| F-14D | 0.51 |
| F-22A | 0.53 |
| F-4A | 0.48 |
| F/A-18F | 0.61 |
| MiG-29 | 0.51 |
| Su-33 | 0.55 |
| Su-47 | 0.73 |
| Su-37 | 0.51 |
| Tornado GR4 / IDS | 0.55 |
| A-10, B-2A and other uncalibrated content | Disabled |

YAML `Render.MachOneSpeed` and TXT `MachOneSpeed` round-trip through the existing exporter.
The stock-content revision is incremented so managed extracted stock definitions refresh on
upgrade. Separate addon packs retain their normal override precedence.

The visual pulse uses a crossing near 0.98 of that mapping, with a lower rearming threshold
and cooldown. First observing an already-fast jet primes tracking rather than inventing an
acceleration event. Observer-pass detection provides one boom when a sufficiently fast jet
passes the listener. The listener aboard the aircraft is excluded from that flypast cue.

Boom arrival is delayed by world-space distance, at 17.15 blocks per tick (343 m/s with one
block per metre). This is a lightweight pass/arrival approximation, not a continuous acoustic
Mach-cone simulation. The mono cue is an original synthetic double pressure pulse and rumble;
its reproducible source is [generate-sonic-boom.py](../scripts/generate-sonic-boom.py).

## Settings and limits

**Supersonic effects** (`effects.supersonicEffects`) is available through
ESC → MC Heli Effects → Full mod settings. Enhanced quality and Polished artwork are required.
Aircraft condensation additionally controls the visual cone. Low detail and adaptive pressure
shed the cone while preserving bounded flypast audio.

- Reuses the existing engine-audio entity scan, with at most 64 observed aircraft states.
- At most 16 pending arrivals, four playing cues and one new cue per five ticks. Distance
  scales volume, while the mono world position preserves direction. Configuration/world
  changes clear pending and playing cues.
- At most two cone births per tick; 14-tick life bounds the new group to 28 live particles.
  Each cone has eight faces and pays eight quads of existing smoke screen-coverage admission.
  It shares the existing global particle limit. Radius is capped at six blocks.
- Reuses the approved 16-frame smoke alpha and condensation material, with no new visual
  texture, shader, scene copy, collision queries or gameplay entity.
- Rain needs actual exposed rain precipitation, with at most four weather checks per tick.
  Missing sections, landing, water, destruction and a source warp stop attached cones.

The flight/intact-vehicle batch is tracked in [M8_PLAN.md](M8_PLAN.md). Claude's wreck artwork,
crash aftermath and AI work remain separate. Native examples and final measured check results
are published with the feature; software Mesa validation does not establish target-PC 60 FPS.
