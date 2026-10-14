# McHeli CE (26.2 Port)

An unofficial community port of [McHeli: Community Edition](https://github.com/Warfactory-Offical/McHeliCE)
(McHeli CE) to **Minecraft 26.2** on **NeoForge**.

> **Unofficial fork.** This port is not affiliated with or endorsed by Warfactory, the McHeli CE
> developers, or the original McHeli authors. The upstream developers have said they will not port
> McHeli CE to other Minecraft versions. Please report problems with this port to
> [its issue tracker](https://github.com/thisisforfreeAI/26.2-port-of-mcheli-cm/issues), not to upstream.

## Status

**Work in progress (milestone M4):** helicopters can be placed, boarded, flown with the keyboard
and mouse, refuelled from fuel cans, landed and picked up again, in single player and multiplayer.
They are drawn with their models, rotors and moving parts, show upstream's HUD, and have the
gunner camera with zoom and night vision. Weapons do not fire yet: they come with milestone M5, and
the other vehicle types with M6. The mod also loads all of McHeli CE's content (vehicles, weapons,
throwables and HUDs, from YAML and legacy TXT files, including addon packs).

- [PORTING_PLAN.md](PORTING_PLAN.md): the roadmap and the decisions behind it
- [CHANGELOG.md](CHANGELOG.md): what each milestone added
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md): what is missing, and where the port differs from upstream

| | |
|---|---|
| Mod ID | `mcheli_ce_port` |
| Minecraft | 26.2 |
| NeoForge | 26.2.0.88 (pinned for development; later 26.2 builds are accepted at runtime) |
| Upstream baseline | McHeli CE 1.5.1 (`master` @ `cbaff7f`) |
| Licence | GPLv3 |

## Building and running

The build targets Java 25. Install JDK 25, or run Gradle on any JDK from 17 up and it downloads a
JDK 25 toolchain automatically.

```sh
./gradlew build       # mod jar in build/libs/ (also runs the tests)
./gradlew test        # JUnit tests, with Minecraft and the mod loaded
./gradlew runData     # regenerates models, lang, recipes and loot tables in src/generated/resources
./gradlew runClient   # development client
./gradlew runServer   # development dedicated server (no GUI; type "stop" to shut it down)
```

Development runs use `run/` as the game directory.

## Content and addon packs

As in McHeli CE, vehicles, weapons, throwables and HUDs are defined in content files (YAML, or the
legacy TXT format) that the mod reads from `<game dir>/mcheli_addons/` at startup:

- The stock content is extracted to `mcheli_addons/default/` and extracted again whenever the mod
  version changes, so do not edit it there.
- Every other folder, `.zip` or `.jar` in `mcheli_addons/` is an addon pack with McHeli CE's layout
  (`pack.mcmeta` with an `addon` block, content under `assets/mcheli/<type>/`). Packs load after the
  stock pack in name order, and a definition replaces an earlier one with the same name.
- Addon packs are also loaded as always-on resource packs, so their textures, models and sounds work.
- `/mcheli dumpyaml [folder]` converts loaded TXT content to YAML in `<game dir>/<folder>`.

The upstream sources the port is based on are not part of this repository. To get a reference copy
in `upstream/` (git-ignored), run:

```sh
bash scripts/fetch-upstream.sh
```

## Credits

McHeli CE and this port exist thanks to:

- **EMB4** and **Murachiki27**: the original McHeli
- **RagexPrince683**: MCHEL-O-Extended (McHeli Overdrive), which McHeli CE continues with
  RagexPrince683's full permission
- **MrNorwood** and **MovBlock**: McHeli CE (Warfactory)
- **TV90** for reforged content, and **H. M. Shrub** and **Jumboreal** for texture work
- McHeli Wingman, originally a standalone addon, which was merged into McHeli CE

The 26.2 port is maintained in
[thisisforfreeAI/26.2-port-of-mcheli-cm](https://github.com/thisisforfreeAI/26.2-port-of-mcheli-cm).

## Licence

This port is licensed under the **GNU General Public License v3.0**, the same licence as McHeli CE;
see [LICENSE](LICENSE). The text is identical to the GPLv3 file in upstream's initial commit
(`7c1b0ee`), because upstream's default branch currently has no LICENSE file (PORTING_PLAN.md,
decision D6). Built jars contain the licence as `LICENSE_mcheli_ce_port`.

The Gradle build files come from NeoForge's
[MDK-26.2-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle) template, which is MIT
licensed; see [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).

Minecraft's official names, which the code uses, are covered by Mojang's own licence; see
[NeoForm's reference copy](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
