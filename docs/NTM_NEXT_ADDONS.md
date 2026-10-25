# NTM Next weapon addons

The optional bridge targets [NTM Next 1.0.3 for NeoForge 26.2](https://www.curseforge.com/minecraft/mc-mods/ntm-next/files/9130786).
Use matching McHeli, NTM Next and addon versions on the server and clients. NTM Next client
installations also need its required CrankShaft dependency. Stock McHeli weapons are unchanged;
NTM payloads are selected by addon weapon definitions.

Add an `NTM` block to a weapon YAML in your addon pack's `assets/mcheli/weapons` folder. Its
filename, without `.yml`, is the name used by a vehicle's `Weapons` entry's `Type` field.
Keep the weapon's existing flight, model, ammunition and sound settings. Its ordinary
`Explosion.Power` must be positive so the projectile reaches its explosion path. Restart the
server and clients after changing addon definitions; `/mcheli reconfig` reloads configuration,
not addon content.

For a chemical rocket, shell or bomb, add:

```yaml
NTM:
  PayloadType: CHEMICAL
  EffectOnly: false
  Chemical:
    Type: CHLORINE
    Count: 4
    Speed: 0.2
```

`Chemical.Type` selects a native NTM entity:

| Type | NTM Next entity |
|---|---|
| `CHLORINE` | `EntityChlorineFX` |
| `CLOUD` | `EntityCloudFX` |
| `PINK_CLOUD` | `EntityPinkCloudFX` |
| `ORANGE` | `EntityOrangeFX` |

Types are case-insensitive; spaces and hyphens may replace underscores. Count is a nonnegative
integer, default one; zero disables cloud spawning. Speed is the nonnegative, finite multiplier
for each axis's Gaussian launch velocity, default 0.2; zero starts clouds at rest. The native
entities then control their own motion, lifetime, poison and terrain effects. The McHeli blast
also runs unless `EffectOnly` is true. Setting `Explosion.DestructionPower` to zero prevents the
ordinary McHeli blast from breaking blocks; it does not change the native cloud's behavior.

Chemical cloud entities deal damage themselves and have no visual-only toggle. Consequently
`EffectOnly: true` creates no chemical cloud and no ordinary blast, matching the bridge's mist
behavior. Without HBM, the weapon still has its ordinary blast and logs a fallback once.

The other payloads are `MININUKE` (also `MINI_NUKE`), `NUKE`, `FIRE`, `MIST` and `VNT`.
Their existing `MiniNuke`, `Fire`, `Mist` and `VNT` blocks retain upstream's schema. Nuclear
block explosion power must be at least one. Legacy `NT` has no runtime equivalent in NTM Next;
its settings remain parsed and exportable, but it uses the ordinary blast fallback.

The YAML exporter preserves all NTM containers, even settings not used by the selected payload
or supported by NTM Next. This includes mini-nuke presets expanded into values, `EffectOnly`,
legacy attribute lists, nested VNT processor maps and custom effect parameters. `/mcheli dumpyaml`
uses this exporter for non-YAML content; it continues to skip definitions already loaded from
YAML.

To try every payload in game, `scripts/make-ntm-validation-pack.sh` writes a test pack into
`run/mcheli_addons/` that gives six stock rockets one payload each; its header lists which
vehicle fires which. On Mesa's software Vulkan driver, set CrankShaft's `backend` to
`"flywheel:off"` in `crankshaft-client.toml`, or NTM effects crash the client (KNOWN_ISSUES 7). Details and validation history are in [M7_PLAN.md](M7_PLAN.md).
