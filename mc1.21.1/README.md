# McHeli CE, Minecraft 1.21.1 (NeoForge and Fabric)

A backport of the 26.2 port to Minecraft 1.21.1, built for both NeoForge 21.1 and Fabric
(Fabric API 0.116, Forge Config API Port 21.1 bundled).

## Layout

- `common/`: the shared mod, written against vanilla 1.21.1 (Mojang names). Loader-specific work
  goes through `platform/` (ServiceLoader services: Platform, Registrar, Network, Configs,
  Transfer, plus `ModEvents`). `shim/` holds 1.21.1 stand-ins for 26.2 classes (ValueInput and
  ValueOutput, InterpolationHandler, PartEntity, TicketController, ...) so that code synced from the
  26.2 port needs few changes. `shim/client/` does the same for the client: 26.2's GUI graphics,
  render states, submit collector and particle classes over 1.21.1's immediate drawing, and
  `shim/client/event/` stands in for NeoForge's client events (one `ClientBus`, posted by each
  loader). `scripts/backport.py` and `scripts/backport_client.py` hold the codemods used for the
  backport.
- `common/src/generated/resources`: the 26.2 port's data-generated resources (recipes, loot,
  block states, models, lang) in 1.21.1 form, made by `scripts/backport_resources.py`. Run it
  again after the 26.2 data generators change.
- `neoforge/` and `fabric/`: entry points, the service implementations and loader-only mixins.
  Both compile the common sources in and read the shared assets from `../src/main/resources/assets`.

## Building

```
./gradlew :neoforge:build :fabric:build
```

Jars land in `neoforge/build/libs` and `fabric/build/libs`. Dev servers: `:neoforge:runServer`,
`:fabric:runServer`; dev clients: `:neoforge:runClient`, `:fabric:runClient`.

## Status and known gaps

- Server side: complete, and a dedicated server boots with the mod on both loaders.
- Client side: backported. NeoForge posts the port's client events from its own; Fabric posts
  them from Fabric API events and client mixins (`fabric/.../mixin/client`). Not yet done on
  1.21.1: the 26.2 baked vehicle meshes (vehicles draw the plain way).
- The 3D hand-held tool models are drawn by a common `ItemRendererMixin` in place of 26.2's
  custom item model type; the `item/<tool>_hand` models still give their display transforms.
- The engine heat haze is a core shader drawn after the level (`GameRendererLevelMixin`), under
  `common/src/main/resources/assets/minecraft/shaders/core/mcheli_ce_port_engine_heat.*`.
- Fabric's config screen is Forge Config API Port's copy of NeoForge's, and shows in Mod Menu.
- Post effects (night and thermal vision) are rewritten for 1.21.1's post chain format; vanilla
  1.21.1 only loads post programs from the `minecraft` namespace, so they live in
  `common/src/main/resources/assets/minecraft/shaders/program/mcheli_ce_port_*`.
- Dropped on 1.21.1: HBM NTM integration, JEI integration, game tests and data generators, the
  Effects Range world clock and timeline (26.2-only data types).
- Fabric only: no battery support (Fabric API has no energy API), vehicle tanks and the charger
  are not exposed to pipes, and the cargo container is not exposed to hoppers.
