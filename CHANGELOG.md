# Changelog

All notable changes to McHeli CE (26.2 Port), newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Versions stay below 1.0.0 until the port is feature-complete, and each milestone in
[PORTING_PLAN.md](PORTING_PLAN.md) (section 6) gets its own minor version. The port is based on
McHeli CE 1.5.1 (`master` @ `cbaff7f`). Where it deliberately differs from upstream, the difference is
listed in [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## [0.1.0] - unreleased (M1: empty mod boots)

### Added

- Project set up from NeoForge's official MDK-26.2-ModDevGradle template (commit `3acf94e`):
  ModDevGradle 2.0.148, Gradle 9.2.1, Java 25 toolchain, NeoForge pinned to 26.2.0.88.
- Mod metadata: mod ID `mcheli_ce_port`, display name "McHeli CE (26.2 Port)", GPLv3, upstream
  authors and credits, and the unofficial-fork notice. The licence text ships inside the jar.
- Empty registers for items, blocks, block entities, entity types, menus, data components, sounds,
  particles and creative tabs, ready for content from M2 on.
- Config skeleton with one file per side, editable in game from the Mods screen:
  - COMMON `general.debugLog`: replaces upstream's hidden `McHeliOutputDebugLog` switch. For now it
    logs a summary of registered entries at startup.
  - SERVER `vehicles.serverAuthoritativeMovement`: switches back to upstream's server-authoritative
    vehicle movement (decision D2). No effect until vehicles are ported.
  - CLIENT `privacy.allowScreenCapture`: opt-in for `/mcheli sendss` (decision D7). No effect until
    the command is ported.
- Startup log line with the version and the unofficial-port notice.
- `./gradlew runServer` passes terminal input to the development server, so console commands such as
  `stop` work.
