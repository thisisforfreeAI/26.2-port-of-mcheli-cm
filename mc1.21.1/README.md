# McHeli CE, Minecraft 1.21.1 (NeoForge and Fabric)

A backport of the 26.2 port to Minecraft 1.21.1, built for both NeoForge 21.1 and Fabric
(Fabric API 0.116, Forge Config API Port 21.1 bundled).

## Layout

- `common/`: the shared mod, written against vanilla 1.21.1 (Mojang names). Loader-specific work
  goes through `platform/` (ServiceLoader services: Platform, Registrar, Network, Configs,
  Transfer, plus `ModEvents`). `shim/` holds 1.21.1 stand-ins for 26.2 classes (ValueInput and
  ValueOutput, InterpolationHandler, PartEntity, TicketController, ...) so that code synced from the
  26.2 port needs few changes. `scripts/backport.py` holds the codemods used for the backport.
- `neoforge/` and `fabric/`: entry points, the service implementations and loader-only mixins.
  Both compile the common sources in and read the shared assets from `../src/main/resources/assets`.

## Building

```
./gradlew :neoforge:build :fabric:build
```

Jars land in `neoforge/build/libs` and `fabric/build/libs`. Dev servers: `:neoforge:runServer`,
`:fabric:runServer`.

## Status and known gaps

- Server side: complete, and a dedicated server boots with the mod on both loaders.
- Client side (rendering, HUD, screens, key bindings): not yet backported. The 26.2 client is
  written for the 26.2 render pipeline and needs porting to 1.21.1's immediate-mode rendering.
- Dropped on 1.21.1: HBM NTM integration, JEI integration, game tests and data generators, the
  Effects Range world clock and timeline (26.2-only data types).
- Fabric only: no battery support (Fabric API has no energy API), vehicle tanks and the charger
  are not exposed to pipes, and the cargo container is not exposed to hoppers.
