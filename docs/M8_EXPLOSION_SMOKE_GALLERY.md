# Explosion smoke: named Minecraft examples

**24 unedited native Vulkan screenshots cover all five conventional M8 explosion profiles**, plus an airburst and an underwater variant. They show the 48-frame smoke upgrade on PR #111, with actual loaded weapon settings.

These are **controlled client VFX previews, not screenshots of bombs fired in combat**. They call the production effect entry point with each named weapon’s loaded size and profile. No weapon damage or crater is simulated, and the reported crater footprint is empty. Actual combat visuals can grow to fit a reported crater and vary with terrain, direction, lighting, distance and effect pressure. NTM Next’s effects and vanilla TNT are outside this gallery.

Settings: Enhanced quality, Polished style, Balanced budget, Modern off, adaptive effects off, daylight, 1280×720. The common overview camera is about 25 blocks from the ground impact. Cannon close-ups move to about 8 blocks and are explicitly labeled. Timing is in game ticks (20 per simulated second); the screenshots do not capture every flash stage. PNGs are copied directly from Minecraft without cropping, recoloring or compositing. Sprite artwork was generated separately; these screenshots were not.

| Profile | Examples | Smoke character |
| --- | --- | --- |
| CANNON | M230, Bofors 40 mm | Compact, short impact puffs |
| BLAST | Hydra 70, JASSM, GBU-31, Sidewinder | Smoke-led small hits; larger missile clouds gain volume |
| HEAVY | Mk 82 | Wider ground-level billows |
| PENETRATOR | Hellfire, GBU-57 | Narrower, directional burst |
| INCENDIARY | S-8DM, CBU-72 | Longer fire pockets in broad smoke |

[Full loaded conventional weapon index](M8_EXPLOSION_SMOKE_WEAPON_INDEX.md) maps **153 positive-explosion definitions** to these profiles. A shared profile does not mean identical size or persistence. [Wreck smoke and Modern-at-night examples](M8_SMOKE_BILLOWS_GALLERY.md) are in the earlier gallery.

## Cannon: M230 chain gun

A compact impact puff; the close-up shows the billows that are tiny at the shared overview distance.

Loaded type: `machinegun2` · profile: `CANNON` · preview size: **1**.

| 8 ticks / 0.4 s — overview | 12 ticks / 0.6 s — close-up |
| --- | --- |
| ![M230 Chain gun, early](screens/m8-explosion-smoke-gallery/m230-age8.png) | ![M230 Chain gun, later](screens/m8-explosion-smoke-gallery/m230-age12-close.png) |

## Cannon: Bofors 40 mm

A larger cannon impact using the same restrained, short-lived smoke profile.

Loaded type: `machinegun2` · profile: `CANNON` · preview size: **2**.

| 8 ticks / 0.4 s — overview | 12 ticks / 0.6 s — close-up |
| --- | --- |
| ![Bofors 40mm gun, early](screens/m8-explosion-smoke-gallery/bofors40mm-age8.png) | ![Bofors 40mm gun, later](screens/m8-explosion-smoke-gallery/bofors40mm-age12-close.png) |

## Rocket: Hydra 70

The familiar expanding rocket puff, with a small hot opening and broken smoke edges.

Loaded type: `rocket` · profile: `BLAST` · preview size: **3**.

| 8 ticks / 0.4 s — overview | 35 ticks / 1.75 s — overview |
| --- | --- |
| ![Hydra 70mm Rocket, early](screens/m8-explosion-smoke-gallery/hydra70-age8.png) | ![Hydra 70mm Rocket, later](screens/m8-explosion-smoke-gallery/hydra70-age35.png) |

## Anti-tank missile: AGM-114 Hellfire

The narrower penetrator profile follows the incoming direction.

Loaded type: `atmissile` · profile: `PENETRATOR` · preview size: **4**.

| 8 ticks / 0.4 s — overview | 35 ticks / 1.75 s — overview |
| --- | --- |
| ![AGM-114 Hellfire, early](screens/m8-explosion-smoke-gallery/agm114-age8.png) | ![AGM-114 Hellfire, later](screens/m8-explosion-smoke-gallery/agm114-age35.png) |

## Cruise missile: AGM-158 JASSM

A much larger BLAST cloud with rolling fire and layered smoke; this is the loaded power-10 definition.

Loaded type: `tvmissile` · profile: `BLAST` · preview size: **10**.

| 8 ticks / 0.4 s — overview | 35 ticks / 1.75 s — overview |
| --- | --- |
| ![AGM-158 JASSM, early](screens/m8-explosion-smoke-gallery/agm158-age8.png) | ![AGM-158 JASSM, later](screens/m8-explosion-smoke-gallery/agm158-age35.png) |

## Dropped bomb: Mk 82

The HEAVY profile spreads wider near the ground before its smoke rises.

Loaded type: `bomb` · profile: `HEAVY` · preview size: **4**.

| 8 ticks / 0.4 s — overview | 35 ticks / 1.75 s — overview |
| --- | --- |
| ![MK82 bomb, early](screens/m8-explosion-smoke-gallery/mk82-age8.png) | ![MK82 bomb, later](screens/m8-explosion-smoke-gallery/mk82-age35.png) |

## Guided bomb: GBU-31

This stock definition uses the BLAST profile because its configured weapon type is asmissile without piercing.

Loaded type: `asmissile` · profile: `BLAST` · preview size: **6**.

| 8 ticks / 0.4 s — overview | 35 ticks / 1.75 s — overview |
| --- | --- |
| ![GBU-31, early](screens/m8-explosion-smoke-gallery/gbu-31-age8.png) | ![GBU-31, later](screens/m8-explosion-smoke-gallery/gbu-31-age35.png) |

## Penetrator: GBU-57

The loaded power-15, piercing definition uses PENETRATOR rather than the broad HEAVY profile.

Loaded type: `asmissile` · profile: `PENETRATOR` · preview size: **15**.

| 8 ticks / 0.4 s — overview | 35 ticks / 1.75 s — overview |
| --- | --- |
| ![GBU-57, early](screens/m8-explosion-smoke-gallery/gbu57-age8.png) | ![GBU-57, later](screens/m8-explosion-smoke-gallery/gbu57-age35.png) |

## Thermobaric rocket: S-8DM

The INCENDIARY profile keeps fire pockets visible longer; the second image is three seconds after the trigger.

Loaded type: `rocket` · profile: `INCENDIARY` · preview size: **5**.

| 8 ticks / 0.4 s — overview | 60 ticks / 3 s — overview |
| --- | --- |
| ![S-8DM 80mmThermobaric, early](screens/m8-explosion-smoke-gallery/s-8dm-age8.png) | ![S-8DM 80mmThermobaric, later](screens/m8-explosion-smoke-gallery/s-8dm-age60.png) |

## Fuel-air bomb: CBU-72 FAE1

The larger INCENDIARY case uses the loaded power-15 setting. This controlled preview is ground-level, rather than reproducing its deployment altitude.

Loaded type: `bomb` · profile: `INCENDIARY` · preview size: **15**.

| 8 ticks / 0.4 s — overview | 60 ticks / 3 s — overview |
| --- | --- |
| ![CBU-72 FAE1, early](screens/m8-explosion-smoke-gallery/cbu-72-age8.png) | ![CBU-72 FAE1, later](screens/m8-explosion-smoke-gallery/cbu-72-age60.png) |

## Airburst: AIM-9 Sidewinder

The BLAST profile triggered nine blocks above the ground. It has no ground crater in this fixture.

Loaded type: `aamissile` · profile: `BLAST` · preview size: **5**.

| 8 ticks / 0.4 s — overview | 35 ticks / 1.75 s — overview |
| --- | --- |
| ![AIM-9 Sidewinder, early](screens/m8-explosion-smoke-gallery/aim9-age8.png) | ![AIM-9 Sidewinder, later](screens/m8-explosion-smoke-gallery/aim9-age35.png) |

## Underwater: Mark 46 torpedo

The underwater path produces spray rather than a new smoke cloud. This uses its underwater power of 8 in a shallow prepared pool.

Loaded type: `torpedo` · profile: `BLAST` · preview size: **8**.

| 8 ticks / 0.4 s — overview | 35 ticks / 1.75 s — overview |
| --- | --- |
| ![Mark 46 torpedo, early](screens/m8-explosion-smoke-gallery/mk46-age8.png) | ![Mark 46 torpedo, later](screens/m8-explosion-smoke-gallery/mk46-age35.png) |

## Reproduce

Use the disposable `M6Smoke` quick-play world with cheats enabled:

```sh
./gradlew -I scripts/visual-probe.init.gradle --no-configuration-cache \
  -Dmcheli.probeExplosionSmokeGallery=true -Dmcheli.probeBackend=VULKAN runClient
```

The opt-in fixture changes terrain in that preview world. It is excluded from normal builds. It writes a loaded weapon index, takes two captures per scene, checks the configured particle bound and verifies that each scene’s mod-owned particles drain. [Capture manifest](performance/m8-realism/explosion-smoke-gallery.json) records hashes and observed counts. Software Mesa Vulkan previews do not establish performance on the target RTX 3060 / 7800X3D PC.
