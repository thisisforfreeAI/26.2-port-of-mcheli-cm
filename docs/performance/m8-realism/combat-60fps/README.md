# Realism combat soak with explicit 60 FPS effects target

The current realism branch was tested at 1920x1080 on Mesa software Vulkan and OpenGL,
with the effects-controller target explicitly set to 60 (16.67 ms), VSync off and frame cap 120.
These sequential whole-frame intervals are not GPU timings, a backend speed comparison or
certification of RTX 3060 / 7800X3D performance. Every scene's p95 exceeds 16.67 ms here.

Each backend has 2,400 tick rows: 600 each for empty, stock-model, fixed-detail combat and
adaptive combat. The combat scenes overlap bombs, missiles, tracers, muzzle flashes, flares,
exhaust, rotor wash and cosmetic ground marks. Source bursts stop at tick 400; the existing
fixture checks transient particles, bullets and queued/active projections at tick 580.
Ground marks may persist. Total-particle counters include ambient vanilla particles; the
summary records the actual drain assertion, rather than asserting every later frame is empty.

`summary.json` contains peak work counters, drain results and observed frame intervals.
Emission/read counters can retain their last active-tick values, and fragment slots are
bounded weak-reference slots, not a distinct count of currently visible fragments. Frame
samples cover ages 80–379 and exclude screenshot readbacks. Both adaptive scenes reached
pressure 3. Existing global caps held; no source/request caps were raised.

Compressed CSVs preserve the raw frame samples and tick telemetry. To reproduce a frame
summary, for example:

```sh
gzip -dc docs/performance/m8-realism/combat-60fps/vulkan-fixed.csv.gz > /tmp/m8-fixed.csv
python3 scripts/summarize-effect-frames.py /tmp/m8-fixed.csv --target-fps 60
```

Run the opt-in combat driver only on its disposable M6Smoke world:
`runClient -Dmcheli.probeCombatSoak=true -Dmcheli.probeTargetFps=60`
with `scripts/visual-probe.init.gradle`; `mcheli.probeBackend=OPENGL` selects OpenGL.
The local validation excluded optional JEI integration because its API was unavailable in
the local Gradle cache. Full GitHub CI builds retain that dependency. Rendering probes are
excluded from release jars. See M8_GROUND_CONTACT and M8_VEHICLE_SHADING for native checks
of the new contact, material, lighting and vertex-color behavior.
