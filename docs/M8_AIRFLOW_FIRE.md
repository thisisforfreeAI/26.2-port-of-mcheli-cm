# Airflow-shaped aircraft fire

Polished damage effects give critically damaged supported planes and helicopters a small fire at the
existing engine/machinery anchors. The flame axis combines air moving against the aircraft’s
world velocity with upward buoyancy. Moving aircraft leave short tapered fire pockets behind;
a stopped aircraft’s flame points up. Vertical flight contributes to airflow as well.

This is cosmetic presentation of the existing overall damage state, not new per-engine health,
ignition damage, fuel consumption or flight behavior. New fire begins below about 29% overall
HP and stops as repairs raise HP above that threshold. Existing particles fade within 20 ticks.
No explosion or crater changes and no NTM integration, gun/gore hooks or assets are involved.

The existing **Vehicle damage details** setting controls it. Enhanced quality plus Polished
style is required. Classic retains its original wreck fire; tanks retain their current fire.
Aircraft wrecks use the airflow shape at their existing fire cadence. Wreck cooling still
controls intensity and lifetime; late embers and short breakup bursts keep their current look.
New living-plane fire requires identifiable engine outlets or nozzle definitions. Unsupported
planes keep their existing damage smoke; no fire is guessed inside their fuselage. A helicopter
can also use its existing machinery anchor beneath a lift rotor. Living nozzle fire starts a
short distance outside the authored pivot so the engine shell does not hide it.

## Rendering and limits

- One tapered quad per admitted flame pocket, using the existing eight-frame hot atlas.
- Normal translucent vanilla particle rendering with depth testing; no additive halo, custom
  shader, scene copy, dynamic light, trail mesh or extra flame sprites per particle.
- Living aircraft request at most one fire pocket every `3 × (1 + effect pressure)` ticks.
- Moving pockets live 12–20 ticks, inherit only part of aircraft motion and slow after shedding.
  World velocity is sanitized and bounded before use; old pockets never follow a later turn.
- Remote aircraft fall back to observed travel when interpolation leaves simulation velocity
  at zero, using the existing bounded structural-debris motion helper.
- Alive fire is nearby detail: full range through 32 blocks, fading to zero at 48 blocks.
- All births use the existing shared VehicleAtmosphere per-tick allowance and global effects
  admission. Balanced remains 16 atmosphere births per tick, 1,536 shared effect particles and
  96 separate flash particles. Fire does not have a separate unbounded allowance.
- No new entity search, world collision, persistent state map, mesh scan or texture asset.
  Outlet selection reuses the existing bounded engine definitions at emission time.

[Native comparisons](M8_AIRFLOW_FIRE_GALLERY.md) show controlled moving, stopped and repaired
fixtures on the F-35A, AH-64 and F-22A, plus wreck/Classic and shared-load checks. These are
native Minecraft images, not actual combat or flight recordings. Software previews do not
establish 60 FPS on the target RTX 3060 8 GB / 7800X3D at 1080p.

## Validation

Pure look tests check stationary buoyancy, airflow direction including vertical flight, invalid
and extreme velocities, bounded lifetimes, the repair threshold and finite output. The opt-in
`AirflowFireProbe` exercises the production VehicleAtmosphere path and asserts that fire exists
in moving/stopped damaged states, drains after repair, stays absent from Classic and shares the
existing budget during a 32-aircraft load scene. Captures and final run results are recorded in
the gallery’s manifest.
