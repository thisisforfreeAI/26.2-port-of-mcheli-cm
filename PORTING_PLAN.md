# McHeli CE → Minecraft 26.2 (NeoForge) — Porting Plan

> **Status:** Plan approved. All [decisions D1–D9](#10-decisions) were approved as recommended on
> 2026-10-14. **M1 to M4 are implemented** (versions 0.1.0 to 0.4.0, see `CHANGELOG.md`); **next
> step is M5** (§6).
> Repo: <https://github.com/thisisforfreeAI/26.2-port-of-mcheli-cm>. In a fresh checkout, run
> `bash scripts/fetch-upstream.sh` to get the upstream reference sources into `upstream/`.
>
> **Unofficial fork.** This is an unofficial, community port of McHeli: Community Edition. It is
> not affiliated with or endorsed by Warfactory, the McHeli CE developers, or the original
> McHeli authors. The upstream developers have said they will not port to other versions.

---

## 1. Ground rules

| Rule | How the port handles it |
|---|---|
| License | **GPLv3**, same as upstream. Note: upstream's default branch `master` has **no LICENSE file** and GitHub reports no license. The GPLv3 text is in upstream's `origin/main` initial commit (`7c1b0ee`, 2025-03-01, 674 lines). We ship that text as `LICENSE`. See Decision D6. |
| Attribution | Keep all notices. Authors listed upstream (`mcmod.info`): **EMB4** and **Murachiki27** (original McHeli), **RagexPrince683** (MCHEL-O-Extended / McHeli Overdrive, which CE continues "with full permission"), **MrNorwood** and **MovBlock** (CE / Warfactory). Credits: TV90 (reforged content), H. M. Shrub and Jumboreal (texture work). The wingman subsystem was a standalone McHeli-Wingman addon merged into CE. |
| Identity | Mod ID `mcheli_ce_port`, display name **"McHeli CE (26.2 Port)"**, Java package `io.github.mcheliceport`. We do not use upstream's mod ID `mcheli`, its name, or its Discord/CurseForge branding. |
| Assets | Ship **only** what is in the upstream repo (§3.3). No third-party content packs. `libs/optifine.jar` (dev-only in upstream) is **not** used or shipped. |
| Faithfulness | Prefer upstream behaviour. Where 26.2 makes a 1:1 port impossible, the difference goes into `KNOWN_ISSUES.md`, not silently dropped. |

---

## 2. Source and target

### 2.1 Upstream snapshot (what we port)

| | |
|---|---|
| Repo | `https://github.com/Warfactory-Offical/McHeliCE`, cloned to `upstream/McHeliCE` (reference only, git-ignored; recreate with `scripts/fetch-upstream.sh`) |
| Commit | `master` @ `cbaff7f` (2026-08-29, "bump version"), 251 commits; mod version **1.5.1** |
| Wiki | cloned to `upstream/McHeliCE.wiki` (14 pages: config, YAML content schemas, Wingman) |
| Build | RetroFuturaGradle + GregTechCEu buildscript, Jabel (modern syntax compiled to Java 8), MixinBooter, an ASM coremod (`core.MCHCore`), access transformer `mcheli_at.cfg` |
| Libraries | Lombok; ElegantNetworking 3.14 (annotation-generated packets); CleanroomMC ModularUI 3.1.6 (GUIs); shaded SnakeYAML 2.5, JOML 1.10.8, Aviator 5.4.3 (HUD expressions) |
| Soft deps | HBM NTM CE 2.5.0.3 (`com.hbm.api.*`), GregTech CEu 2.8.10 (EU energy), The One Probe, Trajectory Preview |
| Size | **582 Java files, 93,886 LOC** in 70 packages; 2 test files (tests commented out); **93 MB** of resources |

### 2.2 Target platform (verified against decompiled sources, not memory)

| | |
|---|---|
| Minecraft | **26.2** (unobfuscated, official Mojang names) |
| NeoForge | **26.2.0.88**, pinned at your request. Latest 26.2 build is .90, but .89/.90 only add translation updates and a Mixin dependency bump, so pinning .88 costs nothing functional. `neoforge.mods.toml` will declare `[26.2.0.88,)`. |
| Build | Official template `NeoForgeMDKs/MDK-26.2-ModDevGradle` (commit `3acf94e`): ModDevGradle **2.0.148**, Gradle **9.2.1**, Java toolchain **25** |
| JDK on this machine | Temurin 25.0.4.1 (works; the MDK set up and decompiled cleanly) |
| Reference sources | `minecraft-patched-26.2.0.88-sources.jar` (7,055 files) and `neoforge-26.2.0.88-sources.jar` (1,158 files), generated with the unmodified MDK |
| Docs read | NeoForge primers 1.21.2 → 26.2 (`neoforged/.github/primers`), 26.2 primer in full for rendering; NeoForge 26.2 docs; Fabric 26.2 porting guide |

### 2.3 Platform facts that shape this plan

Every name below was checked in the 26.2.0.88 sources.

- **Renames.** `ResourceLocation` → `Identifier`. `GuiGraphics` → **`GuiGraphicsExtractor`**. HUD state moved from `Gui` into a new **`Hud`** class (`Minecraft#screen` → `gui.screen()`). `Entity#moveTo` → `snapTo`.
- **Rendering is submit-based.** `Tesselator` and `MultiBufferSource` are **removed**. Entity renderers implement `createRenderState()`, `extractRenderState(T, S, float)` and `submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`. Raw geometry goes through `OrderedSubmitNodeCollector#submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)`, which hands you a `PoseStack.Pose` and a `VertexConsumer`.
- **Custom render features without mixins.** NeoForge adds `RegisterFeatureRenderersEvent` (custom `SubmitNode` → `FeatureRenderer`, drawn with a `RenderPass` from staged GPU buffers), `SubmitCustomGeometryEvent` + `ExtractLevelRenderStateEvent` (world-space geometry), `RegisterRenderPipelinesEvent`, and `RenderLevelStageEvent.After*` stages.
- **Vulkan.** 26.2 ships a Vulkan backend (`com.mojang.blaze3d.vulkan`, "Prefer Vulkan (Experimental)" option). Pipelines use `BindGroupLayout`, `GpuFormat`, `PrimitiveTopology` and reversed depth (`GREATER_THAN_OR_EQUAL`). **No raw GL anywhere**; all GLSL is written in vanilla's style (`#version 330`, `layout(std140)` uniform blocks) so it compiles for both backends.
- **Text.** `Font` draw methods are removed; use `submitText` (world) and `GuiGraphicsExtractor#text` (GUI).
- **GUI.** `GuiGraphicsExtractor` has `fill`, `blit`, `blitSprite`, `text`, `horizontalLine`/`verticalLine`, `outline`, scissor, a 2D pose stack and `submitGuiElementRenderState`. There is **no arbitrary-line primitive**, so HUD lines need a custom `GuiElementRenderState`. Layers: `RegisterGuiLayersEvent` + `GuiLayer#render(GuiGraphicsExtractor, DeltaTracker)`.
- **Entities.** `defineSynchedData(SynchedEntityData.Builder)`, save data via `ValueInput`/`ValueOutput`, `hurtServer(ServerLevel, DamageSource, float)`, `interact(Player, InteractionHand, Vec3)`, `positionRider(Entity, MoveFunction)`, `InterpolationHandler#interpolateTo`, overridable `isClientAuthoritative()`, NeoForge `PartEntity` (multipart) and `IEntityWithComplexSpawn` (extra spawn data).
- **Tracking cap.** Server tracking range is clamped to `min(effectiveRange, playerViewDistance × 16)` in `ChunkMap.TrackedEntity`, which matters for long-distance rendering (§5.7).
- **Vehicle move validation.** `handleMoveVehicle` rejects a client move only if it exceeds the server's expected motion by more than 100 blocks², and corrects mismatches over 0.0625. Client-authoritative piloting is therefore viable (Decision D2).
- **Data-driven everything.** Recipes, loot, tags, item model definitions (`assets/<ns>/items/<id>.json`), atlases (`assets/minecraft/atlases/items.json`, additive) and post effects (`assets/<ns>/post_effect/*.json`) are all data files. Permissions use `PermissionSet` and `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`.
- **Transfer API.** Capabilities are `EnergyHandler` and `ResourceHandler<FluidResource|ItemResource>` for blocks, entities and items. Chunk loading uses `TicketController` + `RegisterTicketControllersEvent`.
- **Tests.** `RegisterGameTestsEvent#registerTest(...)` for GameTests; NeoForge JUnit bootstrap (`net.neoforged.neoforge.junit`) for in-game unit tests; plain JUnit for pure-Java code.

---

## 3. Inventory

### 3.1 Package inventory

Deps columns come from a per-package scan of 1.12.2 API usage (counts are matching lines). Milestones refer to §6.

| Package(s) | Files | LOC | What it does | Key 1.12.2 dependencies | 26.2 replacement | MS |
|---|---:|---:|---|---|---|---|
| *(root)* `MCH_MOD`, proxies | 52 | 7,247 | Mod bootstrap (`@Mod`, sided proxies, pre/init/post), item/block/entity registration, creative tabs, config (`MCH_Config` 961), camera (`MCH_Camera`, `MCH_3rdCamera`, view-entity dummy), explosions (`MCH_Explosion`), recipes (`MCH_ItemRecipe`), model manager + on-demand loader + async model threads, server radar snapshots (`EntityInfoManagerAsync`), key names | `@SidedProxy`, `GameRegistry`, `FMLPreInit`, Forge `Configuration`, `GlStateManager`, LWJGL2 `Keyboard`/`Mouse` | `@Mod` constructor + `IEventBus`, `DeferredRegister`, `ModConfigSpec`, client-only classes behind `Dist.CLIENT` | M1–M2 |
| `aircraft` | 35 | 15,315 | Shared vehicle core. `MCH_EntityAircraft` (**7,212 LOC**): physics, fuel, damage, seats, weapons, parts animation, radar/RWR, missile detector, multipart hitboxes, inventory, sounds, client tick handlers, aircraft GUI/container, viewport/gauge widgets | `DataParameter` (30), NBT (28), `IEntityAdditionalSpawnData`, `IFluidHandler`, `GlStateManager` (257), ModularUI | `Entity` + `SynchedEntityData`, `ValueInput/Output`, `IEntityWithComplexSpawn`, `PartEntity`, fluid capability, `InterpolationHandler`; renderer split into render state + submit | M3–M6 |
| `helicopter`, `plane`, `ship`, `tank`, `vehicle` | 37 | 9,067 | Type-specific entities, info classes, items, client tick handlers, GUIs, renderers. Tank adds wheels and terrain tilt (`MCH_WheelManager`, `MCH_EntityWheel`) | as above, plus `Render<>` subclasses | Subclasses of the ported aircraft core; one `EntityRenderer` per type | M3 (heli), M6 |
| `weapon` (+`registry`) | 65 | 8,305 | ~30 weapon behaviours (`MCH_Weapon*`: guns, rockets, AAM/ASM/ATM, TV missile, bombs, torpedo, CAS, laser, railgun, buckshot, dispenser, targeting pod), `MCH_WeaponSet`, guidance, bullet entities (`MCH_EntityBaseBullet` 1,491), cartridges, CCIP, renderers | Entity/NBT, `Tessellator` (28), GL (115) | `Projectile`-style entities, server hit-scan, `submitCustomGeometry` / custom `RenderType`s | M5 |
| `flare`, `throwable`, `lweapon`, `gltd` | 24 | 3,316 | Flares/chaff/APS, grenades, hand-held Stinger/Javelin, laser target designator | Entities, input, GL | Entities, items with `use` / `releaseUsing`, client tick input | M5–M6 |
| `helper/world`, root `MCH_Explosion` | 2 | 819 | Custom explosion with block breaking, fire, damage factors | `Explosion` internals | `ServerLevel#explode` + custom `ExplosionDamageCalculator`; CE's explosion logic kept where vanilla differs | M5 |
| `particles` | 8 | 1,021 | Smoke, splash, explode, mark-point, block-dust particles | `Particle` + `Tessellator` (25), AT on motion fields | `SingleQuadParticle` subclasses, `RegisterParticleProvidersEvent`, `particles/*.json` | M5/M7 |
| `helper/info` (+parsers, emitters) | 21 | 8,528 | **Content system**: `ContentType`, folder/zip loaders, parser-by-extension registry, typed `ContentRegistry`, `TxtParser` (1,878), `YamlParser` + component/weapon/HUD/throwable parsers (3,462), `YamlEmitter` (2,047, used by `dumpyaml`), `ShaderRegistry` | SnakeYAML, Guava; small `ResourceLocation` use | **Mostly Minecraft-independent, so ported nearly verbatim** and unit-tested. SnakeYAML via `jarJar`. | M2 |
| Info classes (`MCH_*Info`, `MCH_BaseInfo`) | ~12 | ~3,500 | Parsed content model (aircraft 1,864, weapon 597, …) | `Vec3d`, item refs | `Vec3`, `Identifier`; item refs resolved lazily after registries freeze | M2 |
| `helper/addon`, `helper/io` | 7 | 962 | Addon packs from `<gamedir>/mcheli_addons/` (folders or zip/jar), `pack.mcmeta` `addon` metadata, builtin pack extracted to `mcheli_addons/default/`, `generated/` pack, injected as client resource packs | `FMLClientHandler#addModAsResource` | Same discovery code; packs added via `AddPackFindersEvent` (client resources and server data) | M2 |
| `hud` (+`direct_drawable`) | 21 | 2,343 | HUD DSL interpreter (`If/Do`, `Color`, `DrawString/Texture/Rect/Line/Radar/Graduation`, `CameraRot`, `Call`), Aviator expressions, stippled lines; BVR lock, GPS, mortar radar, RWR overlays | GL (17), `GlStateManager` (90), `Tessellator` (45), Aviator, AT on `ActiveRenderInfo` | `GuiLayer` + `GuiGraphicsExtractor`; custom line/poly `GuiElementRenderState`; built-in expression evaluator (Decision D4) | M4 |
| `wrapper/modelloader` | 12 | 2,277 | OBJ (`W_WavefrontObject`) and MQO (`W_MetasequoiaObject`) loaders, group-based part rendering, `ModelVBO` (fixed-function VBOs), Apple VAO path, crawler-track baking | GL11/15 client arrays (16+64 VBO), display lists | CPU-side `BakedMcheliModel` (per-group vertex arrays) built on worker threads; drawn via `submitCustomGeometry` (M4) or a static-GPU-buffer `FeatureRenderer` (M7) | M4, M7 |
| `helper/client` (+model, item renderers) | 25 | 1,153 | Item model bakery hooks, legacy model loader, built-in item renderers (GLTD, wrench, range finder, light weapons, drafting table), camera manager | `IBakedModel`, `TileEntityItemStackRenderer`, `ModelLoaderRegistry` | Item model definitions + `SpecialModelRenderer` (`RegisterSpecialModelRendererEvent`) | M4/M6 |
| `wrapper` | 43 | 1,833 | `W_*` shims over 1.12.2 APIs (entity, item, GUI, sound, network, tick, reflection, NBT) | everything | **Not ported.** Call sites use 26.2 APIs directly. | — |
| `networking` (+data, packet, control) | 66 | 4,043 | ~62 packets: per-vehicle control inputs (bit-packed switches, enum-ordinal bytes), weapon sync, lock/RWR, UAV, status, wingman panel/missions, radar snapshots, image chunks | ElegantNetworking (290), `ByteBuf` | `CustomPacketPayload` records + hand-written `StreamCodec`s that keep CE's compact encodings; `PayloadRegistrar` | M3–M6 |
| `gui`, `factories`, `command/MCH_GuiTitle` | 21 | 3,083 | Aircraft GUI, seat interaction, in-game config editor, GPS input, lists/sliders, UAV station GUI; ModularUI factories | `GuiScreen`/`GuiContainer` (85), ModularUI (48), `IGuiHandler` | Vanilla `Screen` / `AbstractContainerScreen` + `MenuType` (`IMenuTypeExtension`); ModularUI dropped | M4–M6 |
| `event` | 8 | 1,399 | Client tick, GUI tick, keyboard/mouse input (mouse-flight 704 LOC), detached view refresh, camera handler | `TickEvent`, LWJGL2 input | `ClientTickEvent`, `InputEvent`, `CalculatePlayerTurnEvent`, `ViewportEvent.ComputeCameraAngles#setRoll`, `CalculateDetachedCameraDistanceEvent` | M3–M4 |
| `block` | 9 | 1,721 | Drafting table (block, block entity, recipe browser GUI 769 LOC, 3D model preview, container) and aircraft charger (block entity, GregTech EU) | `TileEntity`, TESR, `IGuiHandler` | `Block`, `BlockEntity`, `BlockEntityRenderer#submit`, `MenuType`, picture-in-picture preview, `EnergyHandler` capability | M6 |
| `item`, `tool`, `chain`, `container`, `parachute` | 17 | 2,397 | Content items, wrench, range finder, chain towing, cargo container, parachute | item damage/meta (49), NBT | Data components (`DAMAGE`, custom typed components), entities | M2, M6 |
| `uav` | 13 | 1,859 | UAV station entity, pairing device, tablet, control, live camera feed widget (second world render into an FBO) | `Framebuffer`, render-view swap | Station/pairing ported; camera feed has **no supported 26.2 API**, see KNOWN_ISSUES and §5.4 | M6 |
| `mob` | 4 | 1,023 | Gunner NPC (vs monsters / vs other teams) | `EntityLiving` AI | `PathfinderMob` + goals | M6 |
| `multiplay` | 11 | 1,694 | Teams/scoreboard GUIs, IFF (`canAttackEntity`), target spotting/marking, client screenshot capture (`sendss`) | `glReadPixels`, `ImageIO`, scoreboard | Vanilla scoreboard teams; screenshot via `Screenshot` utility (Decision D7) | M6–M7 |
| `sound` | 4 | 495 | Dynamic sound registration from addon `.ogg` files (no `sounds.json`), sound patching via AT on `SoundHandler` | AT, `SoundHandler` internals | Generated in-memory `sounds.json` for every discovered `.ogg`; `SoundEvent.createVariableRangeEvent`; tickable `SoundInstance`s | M2/M7 |
| `command`, `wingman/command` | 3 | 1,791 | `/mcheli` (dumpyaml, reconfig, status, list, modlist, title, fill, killentity, removeentity, attackentity, showboundingbox, delayhitbox, sendss) and `/wingman` (~20 subcommands); per-subcommand ACL | `CommandBase` | Brigadier via `RegisterCommandsEvent`; `PermissionSet` + config ACL | M2 (dumpyaml), M7 |
| `wingman` (all) | 32 | 10,168 | AI wingmen, formation, autonomous 22-state mission flight (1,994 LOC), marker blocks (airbases, runways, helipads), taxi routes, chunk loading for UAVs/projectiles, UAV chunk streaming to operator, planner GUIs | Forge chunk tickets, AT on `PlayerChunkMap`, `GuiScreen` | `TicketController`, `SavedData`, vanilla screens; chunk streaming needs research (§5.7) | M6b |
| `compat` (hbm, energy, oneprobe, trajectorypreview) | 13 | 1,653 | HBM:NTM warhead payloads (mini-nuke, VNT, mist, fire) via reflection, GT/HBM energy bridge, TOP tooltip, trajectory preview | `@Optional`, reflection | `ModList.get().isLoaded(...)` guarded stubs (HBM required by brief); energy via `EnergyHandler`; TOP/trajectory deferred | M7 |
| `core` (coremod) | 7 | 498 | ASM patch for Aviator under LaunchWrapper, FOV/tracker/render/unload hooks | `IFMLLoadingPlugin`, ASM | **Dropped.** Aviator removed; hooks become events or targeted mixins. | — |
| `mixin` | 5 | 194 | Long-distance tracking (`EntityTrackerEntry` range clamp, force-watch), far-plane entity pass, vehicle render hook, entity unload | Mixin on 1.12.2 internals | NeoForge mixins only where no event exists (§5.7) | M7 |
| `helper/debug`, `helper/criterion`, misc | ~12 | ~800 | Debug renderers (weapon points, test models), advancement triggers, logger | GL | Debug renderers via submit API; `CriterionTrigger` registry | M7 |

### 3.2 Cross-cutting 1.12.2 API usage (lines)

| Category | Lines | Notes |
|---|---:|---|
| `GlStateManager` / raw GL / `Tessellator` / VBO / display lists | 1,030 / 203 / 268 / 89 / 2 | All rewritten (§5.4) |
| `FontRenderer` | 177 | `GuiGraphicsExtractor#text`, `submitText` |
| Networking (ElegantNetworking, `ByteBuf`) | 380 | §5.3 |
| `isRemote` / `@SideOnly` | 603 | `level.isClientSide()`, `Dist`-separated client classes |
| GUI (`GuiScreen` etc.) / ModularUI | 388 / 84 | Vanilla screens |
| Config references (`MCH_Config.*`) | 447 | Typed `ModConfigSpec` values (§5.6) |
| NBT / synced data / item damage-meta | 209 / 111 / 49 | `ValueInput/Output`, `EntityDataAccessor`, data components |
| Input (LWJGL2 `Keyboard`/`Mouse`) | 123 | `KeyMapping` + NeoForge input events |
| Sound / particles | 130 / 69 | §5.8 |
| Reflection / ASM / Mixin | 19 / 11 / 12 | Mostly removable |

### 3.3 Assets (all shipped; nothing from outside the repo)

| Kind | Count | Port action |
|---|---:|---|
| Content definitions (YAML) | 372 (34 heli, 46 plane, 5 ship, 12 tank, 18 vehicle, 234 weapon, 4 throwable, 19 HUD) | Ship as the builtin pack; parse unchanged |
| Legacy TXT equivalents (`legacy/`) | 372 | Not shipped by upstream; used as **parser test fixtures** (TXT vs YAML parity) |
| Models | 141 MQO, 49 OBJ, plus 1.12 item/block JSON models | MQO/OBJ unchanged (our loader); JSON models regenerated by datagen |
| Textures | 559 PNG (+2 GIMP `.xcf` sources) | Unchanged paths under `assets/mcheli/textures/`; item icons added to the items atlas via an additive atlas source |
| Sounds | 72 OGG | Unchanged; `sounds.json` generated |
| Lang | 6 `.lang` files (en_us, ja_jp, zh_cn + legacy-case copies) | Converted to `lang/*.json` (datagen); content display names generated at runtime |
| Shaders | `basic.vert/frag` (model VBO shader), 1.12 post shaders `thermal`, `nightvision` | Rewritten as 26.2 post effects (`post_effect/*.json` + GLSL 330 UBO style); model shader replaced by a `RenderPipeline` |
| Recipes | 17 JSON (Forge 1.12 format with `ore_dict` constants) | Regenerated by `RecipeProvider`; ore dict → item tags (`#minecraft:planks`, `#minecraft:wool`, `#c:glass_blocks`, …) |
| Blockstates | 2 | Regenerated by datagen |
| Docs | `MCHeli_ParameterList.pdf`, `Readme_Aircraft/HUD/Weapon.txt` | Shipped unchanged inside the jar |

---

## 4. Target architecture

```
<workspace root>/                     ← port repo (git); upstream/ is reference only (git-ignored)
  build.gradle, settings.gradle, gradle.properties   ← from MDK-26.2-ModDevGradle, neo_version=26.2.0.88
  src/main/java/io/github/mcheliceport/
    McheliCePort.java                 ← @Mod entry; parses content in the constructor
    registry/                         ← DeferredRegisters: items, blocks, block entities, entities, menus,
                                        data components, sounds, particles, creative tabs
    content/                          ← ported helper/info + helper/addon (pure Java where possible)
    content/legacy/                   ← 1.12 item-name flattening table for recipes/ammo items
    entity/aircraft|heli|plane|ship|tank|vehicle|weapon|misc/
    net/                              ← payload records + StreamCodecs, one registrar
    config/                           ← ModConfigSpec (COMMON, CLIENT, SERVER) + legacy cfg import
    command/                          ← Brigadier /mcheli, /wingman
    resources/                        ← in-memory PackResources: item model defs, lang, sounds.json, recipes
    compat/hbm/                       ← soft-dependency stubs
    client/
      model/                          ← OBJ/MQO parsing → BakedMcheliModel (worker threads)
      render/                         ← EntityRenderers, RenderPipelines/RenderTypes, feature renderer
      hud/                            ← GuiLayer + HUD DSL interpreter + expression evaluator
      gui/                            ← Screens
      input/                          ← KeyMappings, mouse flight, camera
  src/main/resources/assets/mcheli/   ← upstream assets, unchanged paths (addon-compatible namespace)
  src/main/resources/assets/mcheli_ce_port/   ← port-owned resources (lang, item model defs, post effects)
  src/generated/resources/            ← datagen output
  src/test/java/                      ← JUnit: parsers, expression evaluator, physics math
```

---

## 5. Porting challenges

### 5.1 Registration, item metadata and NBT

- **Timing.** Content must exist before registries freeze. The mod constructor runs the ported `ContentRegistries.loadContents(<gamedir>/mcheli_addons)` synchronously (pure file IO, no Minecraft state). The builtin pack is extracted to `mcheli_addons/default/` exactly as upstream does. HUD files stay client-only, as upstream.
- **Registries.** `DeferredRegister.Items#registerItem` (handles `Item.Properties#setId`), `DeferredRegister.Blocks`, `createEntities` (`EntityType.Builder…build(ResourceKey)`), `createDataComponents`, block entity types, `MenuType`, `SoundEvent`, `ParticleType`, `CreativeModeTab`. Content-driven items (one per aircraft, throwable and custom item) are registered in loops over the parsed registries. IDs are lowercased and sanitised, e.g. `mcheli_ce_port:ah-64`.
- **Metadata.** Upstream uses damage as state: aircraft items carry HP (`setMaxDamage(maxHp)`) and fuel cans carry fuel. These map to `DataComponents.MAX_DAMAGE`/`DAMAGE`. Separate legacy variants (`uav_station`/`uav_station2`, gunner vs-monster/vs-player) are already separate items.
- **Item NBT** → typed data components with `Codec` + `StreamCodec` (UAV pairing link, aircraft paint/texture index, light-weapon state). Only genuinely free-form data uses `CUSTOM_DATA`.
- **Legacy references.** Recipes and `AmmoItems` reference 1.12 names with metadata (`Meta`/`Damage`). A flattening table maps them to 26.2 IDs (wool:3 → `light_blue_wool`, …). Unmappable entries are logged once and the recipe skipped (KNOWN_ISSUES).
- **Seven creative tabs** (Items, Helicopters, Planes, Ships, Tanks, Vehicles, Recipe Items) with config-chosen icons, filled via `CreativeModeTab.builder().displayItems(...)`.

### 5.2 Entities, sync and riding

- **Class tree kept.** `AircraftEntity` (ported `MCH_EntityAircraft`) → `HeliEntity`, `PlaneEntity`, `ShipEntity`, `TankEntity`, `VehicleEntity`. The 7,212-line god class is split into collaborators (physics, fuel, damage/armor, seats, weapons, parts animation, radar) **without changing behaviour**, so each piece can be unit-tested.
- **Synced data.** The 13 upstream `DataParameter`s (part state, damage, UAV station, status bits, selected weapon, fuel, roll, throttle, type id, texture, fuel fluid, command, ERA state) become `EntityDataAccessor`s defined in `defineSynchedData(SynchedEntityData.Builder)`. Spawn-time data (content type, seat layout) goes through `IEntityWithComplexSpawn`.
- **Persistence.** `ValueInput`/`ValueOutput`; inventory via `ItemStacksResourceHandler` (item `ResourceHandler` capability); fuel via the fluid `ResourceHandler` capability (upstream implements `IFluidHandler`).
- **Riding.** Upstream: pilot rides the aircraft directly; extra seats are `MCH_EntitySeat` entities. Kept: `getControllingPassenger()` returns the pilot, `positionRider(…, MoveFunction)`, `getPassengerRidingPosition`, `getDismountLocationForPassenger`. Seat entities keep their own interpolation.
- **Multipart hitboxes** (`MCH_EntityHitBox`, `MCH_BoundingBox` with rotatable boxes and damage factors) → NeoForge `PartEntity` children.
- **Smooth client movement.** `InterpolationHandler` (vanilla, configurable step count) for non-authoritative instances, replacing `setPositionAndRotationDirect`. Upstream's tracking intervals (1,200-block range, update interval 2 for vehicles) are kept via `EntityType.Builder#clientTrackingRange`/`updateInterval`, subject to the view-distance cap (§5.7).
- **Authority model (D2, approved).** The pilot's client simulates the vehicle (`isClientAuthoritative()` is true while a player pilots it), and the server applies sanity checks against content speed limits. A SERVER config option restores upstream's server-authoritative model (inputs only, server simulates).

### 5.3 Networking

- Each ElegantNetworking packet becomes a `record … implements CustomPacketPayload` with a `StreamCodec<RegistryFriendlyByteBuf, …>`, registered once in `RegisterPayloadHandlersEvent` (`PayloadRegistrar("1").playToServer/playToClient/playBidirectional`). Handlers run on the main thread.
- **CE's network optimisations are kept byte-for-byte where possible**: enum ordinals as bytes, bit-packed `PlayerControlSwitches`, varints, entity IDs instead of UUIDs, sequence-numbered radar snapshot heartbeats with client-side expiry, delta-only weapon/ammo notifications.
- Sending: `PacketDistributor.sendToPlayer`, `sendToPlayersTrackingEntity(AndSelf)`, `sendToPlayersNear`; client → server via `ClientPacketDistributor.sendToServer`.
- Security: every server handler re-validates (rider actually controls that vehicle, weapon index in range, cooldowns), because NeoForge payloads give no implicit trust.

### 5.4 Rendering (largest job)

1. **Model loading (threaded, kept).** OBJ/MQO parsers are ported from `W_WavefrontObject`/`W_MetasequoiaObject` into a GL-free `BakedMcheliModel`: per-group (`$body`, `$rotor0`, `$weapon1_barrel`, …) arrays of position/UV/normal, bounds, and smooth-shading normals. Parsing runs during resource reload in a `PreparableReloadListener` (`AddClientReloadListenersEvent`); `prepare` fans out across a worker pool like upstream's per-category `CompletableFuture`s. Upstream's on-demand mode (`MCH_OnDemandModels`) is kept: the model is parsed on a background worker the first time it is rendered, with a placeholder until ready. GPU work happens only on the render thread.
2. **Entity renderers.** One `EntityRenderer<E, S>` per type. `extractRenderState` copies everything needed (interpolated rotations, part angles, throttle, rotor phase, damage, texture, lights) into the render state, so `submit` never touches the entity. Part transforms reproduce upstream's `MCH_RenderAircraft` GL matrix calls with `PoseStack` operations.
3. **Geometry submission, path A (M4, correctness first).** `submitCustomGeometry(poseStack, renderType, (pose, buf) -> model.emitGroup(pose, buf, light, overlay, color))` with custom `RenderType`s built from `RenderPipelines` entity pipelines (`RenderTypes#entityCutout/entityTranslucent` or our own pipeline when we need cull or no-cull variants). This works on GL and Vulkan.
4. **Path B (M7, performance parity with CE's VBOs).** A custom `SubmitNode` + `FeatureRenderer` registered via `RegisterFeatureRenderersEvent`. Static model groups are uploaded once into `GpuBuffer`s, and each frame we only push a model matrix and draw with a `RenderPass` and a registered `RenderPipeline`. Path A stays as the fallback (client config switch).
5. **Effects.** Laser beams, search lights, exhaust flames, tracers and chains use translucent/emissive `RenderType`s; world-space effects not tied to an entity use `SubmitCustomGeometryEvent` + `ExtractLevelRenderStateEvent`.
6. **HUD.** A `GuiLayer` registered above vanilla layers runs the HUD DSL each frame. `DrawString` → `text`, `DrawRect` → `fill`, `DrawTexture` → `blit` with 2D pose rotation. `DrawLine`/stipple/radar/graduation go through a custom `GuiElementRenderState` that tessellates segments into thin quads; stipple patterns become dash segments, since there is no `glLineStipple`. Direct drawables (BVR lock, GPS, mortar radar, RWR) project world points to the screen using the frame's view/projection captured at render-state extraction. Upstream's `ActiveRenderInfo` AT is not needed.
7. **Screen-space effects.** Thermal and night vision become `assets/mcheli_ce_port/post_effect/{thermal,nightvision}.json` with GLSL ported to the vanilla post format and toggled with `GameRenderer#setPostEffect`/`clearPostEffect`. Vanilla uses that slot for spectator mob vision, so we save and restore it.
8. **GUI 3D previews** (drafting table, aircraft viewport widget) use a `PictureInPictureRenderer` submitting the model.
9. **UAV live camera feed.** Rendering the world a second time from another viewpoint into a texture has **no supported API** in 26.2 (single `LevelRenderer` view per frame, global render-state extraction). Initial port shows the existing "NO SIGNAL" state plus telemetry; logged in KNOWN_ISSUES and revisited after M7.

### 5.5 Content loader (TXT + YAML)

- `ContentType`, `ContentLoader`, `FolderContentLoader`/`FileContentLoader`, `ContentParsers`, `TxtParser`, `YamlParser` family and `YamlEmitter` are ported with minimal changes (types: `Vec3`, `Identifier`).
- Addon discovery is unchanged: `<gamedir>/mcheli_addons/<pack>/` (folder or `.zip`/`.jar`), `pack.mcmeta` `addon` block (`domain`, `version`, `loader_version`, `authors`), `assets/mcheli/<type>/...`. Each pack is also exposed to Minecraft through `AddPackFindersEvent` as an always-on pack with our own pack metadata, so old `pack_format: 3` packs still load. Data packs are added the same way for server-side files.
- **Generated resources** (upstream's `generated/` folder) become an in-memory `PackResources` providing item model definitions (`items/<id>.json` → `mcheli:items/<id>` texture), lang JSON from `DisplayName` maps, `sounds.json` for discovered `.ogg` files, and content recipes from `Recepie` blocks.
- `/mcheli dumpyaml` is ported to Brigadier and writes `<gamedir>/mcheli_yaml_dump/` as upstream.
- **Tests:** golden tests parse each of the 372 legacy TXT files and the corresponding YAML file and compare the resulting info objects. A round-trip test runs TXT → `YamlEmitter` → `YamlParser`.
- Documented upstream parser bugs (wiki: `EnableVtol` map form, `Smoke.Velocity`, `BoundingBoxes.Name`, `WeightedCenterZ`, `RotBarrel`) are fixed (D5, approved); each fix gets a CHANGELOG entry and a regression test.

### 5.6 Config

- `MCH_Config`'s ~90 keys → `ModConfigSpec`. **COMMON**: gameplay/balance such as speeds, damage multipliers, explosions, collision, gunner ranges. **SERVER** (per-world, synced as upstream does with `PacketSyncServerSettings`): rotation limits, lock ranges, marker settings. **CLIENT**: mouse, HUD, render distance weights, shaders.
- Upstream's free-form repeated lines (`DamageVs… = factor, classSubstring`, `IgnoreBulletHit = class`, `CommandPermission = cmd:players`) become string lists with the same syntax. Matching keys on entity **type IDs** rather than Java class substrings is a behaviour difference noted in KNOWN_ISSUES.
- Wingman `uav.*`/`formation.*` categories map to their own config sections.
- `/mcheli reconfig` reloads SERVER/COMMON values and re-syncs clients.
- Optional one-shot importer: reads an existing `config/mcheli.cfg` and writes the TOML.

### 5.7 Input, camera and long-distance features

- **Key mappings.** Upstream's 24 `[Key config]` keys → `KeyMapping`s in a `KeyMapping.Category(Identifier)` via `RegisterKeyMappingsEvent`, with defaults kept (W/S/A/D, H, Space, mouse-middle, G, X, R, Z, C, Y, V, F, PageUp/PageDown, LCtrl, I, B, J, U, L, M, Numpad0 for the wingman panel). Vehicle keys use a custom `IKeyConflictContext` ("riding a McHeli vehicle") so they do not clash with vanilla movement.
- **Mouse flight** (`MouseInputHandler`, 704 LOC): `CalculatePlayerTurnEvent` consumes mouse deltas while piloting.
- **Camera.** `ViewportEvent.ComputeCameraAngles` (yaw/pitch/**roll**), `ComputeFov` for zoom, `CalculateDetachedCameraDistanceEvent` for third-person distance. View-entity replacement uses `Minecraft#setCameraEntity`.
- **Long-distance rendering (M7).** Upstream lifts the tracking clamp with a mixin and adds a far-plane entity pass. Plan:
  - (a) Radar/RWR beyond tracking range already works through the server snapshot system (`EntityInfoManagerAsync`, virtual threads, sequence-numbered heartbeats), ported as is.
  - (b) A targeted NeoForge mixin on `ChunkMap.TrackedEntity` raises the clamp only for McHeli vehicles and UAV operators, matching upstream's rules.
  - (c) Instead of a second far-plane pass, vehicles beyond the far plane are drawn as **scaled proxies** along the same view ray. This is visually identical under perspective projection and needs no projection hacks, which matters on Vulkan.
- **UAV chunk streaming** (wingman `UavChunkStreamer`, upstream AT on `PlayerChunkMap`): `TicketController` keeps chunks loaded. Sending them to a far-away operator needs research in M6b (camera-entity chunk tracking vs. a narrow mixin).

### 5.8 Sounds, particles, tabs, recipes, loot, lang

| Area | Plan |
|---|---|
| Sounds | `DeferredRegister<SoundEvent>` for static sounds; addon `.ogg` files get generated `sounds.json` entries; looping engine/rotor sounds via `AbstractTickableSoundInstance` (`MCH_SoundUpdater`); server plays via `Holder.direct` for addon sounds. |
| Particles | `ParticleType`s + `particles/*.json` + `SingleQuadParticle` providers; CE's smoke/splash/flak behaviour ported (no AT needed, motion fields are protected). |
| Creative tabs | §5.1. |
| Recipes | Static recipes via `RecipeProvider` datagen. Content recipes (`Recepie`) generated into the in-memory data pack at server start. Drafting-table recipe browser reads the `RecipeManager`. |
| Loot | Block loot tables (drafting table, charger, marker block) via `BlockLootSubProvider` datagen. |
| Lang | en_us, ja_jp, zh_cn converted to JSON by `LanguageProvider` datagen; content names generated at runtime. |
| Advancements | `MCH_CriteriaTriggers` → registered `SimpleCriterionTrigger`s (now in `net.minecraft.advancements.triggers`), advancements by datagen. |

### 5.9 HBM:NTM and other compat

- `compat/hbm`: weapon YAML `NTM` blocks are always **parsed** (no data loss). At runtime `ModList.get().isLoaded("hbm")` gates a reflection/stub bridge. Without HBM, NTM payloads fall back to the weapon's normal explosion and log once. No HBM build exists for 26.2 to test against; this is a stub only.
- GregTech EU bridge: dropped; the charger accepts NeoForge `EnergyHandler` energy. The One Probe and Trajectory Preview hooks are stubbed (Jade could replace TOP later).

---

## 6. Milestones

Every milestone ends with `./gradlew build`, `runClient` (world load, no crash), `runServer` (dedicated, `--nogui`, boots and stops cleanly), and passing tests. CHANGELOG.md and KNOWN_ISSUES.md are updated, and each milestone is its own branch and commit series.

| MS | Goal | Main work | Exit criteria / tests |
|---|---|---|---|
| **M0** | Plan approved | **Done 2026-10-14** (all §10 decisions approved) | — |
| **M1** | Empty 26.2 mod boots | **Done 2026-10-14** (0.1.0). MDK import (neo 26.2.0.88), mod metadata (ID, GPLv3, authors, unofficial notice), LICENSE, empty registries, config skeleton, logging | Build green; client reaches title screen with the mod listed; dedicated server boots |
| **M2** | Content + registries | **Done 2026-10-14** (0.2.0). Ported content system, addon discovery + builtin extraction, all items/blocks/entity types registered (entity types as inert placeholders until each entity is ported), 7 creative tabs, generated item model definitions/lang/sounds, recipes, `/mcheli dumpyaml` | All 372 stock definitions load from YAML **and** TXT (parity tests); every item visible with icon and name in creative; addon folder override test |
| **M3** | One helicopter flies | **Done 2026-10-14** (0.3.0; plan in [docs/M3_PLAN.md](docs/M3_PLAN.md)). `AircraftEntity` core + `HeliEntity` (use a stock heli such as `ah-64`), spawn item, riding/seats, controls + mouse flight, physics, fuel, synced data, control payloads, interpolation | Spawn, mount, take off, hover, land in SP; two-client LAN test shows smooth remote motion; physics unit tests (rotor/throttle/drag math vs upstream formulas) |
| **M4** | Rendering + HUD for that heli | **Done 2026-10-14** (0.4.0; plan in [docs/M4_PLAN.md](docs/M4_PLAN.md)). OBJ/MQO loaders + threaded reload, render path A, rotor/part animation, textures, HUD layer + DSL + expression evaluator, camera/zoom, night vision post effect | Heli renders on **both GL and Vulkan**; HUD matches upstream layout for `heli.yml`; expression evaluator unit tests |
| **M5** | Weapons | **Done 2026-10-14** (0.5.0; plan in [docs/M5_PLAN.md](docs/M5_PLAN.md)). Weapon sets, all weapon types, bullets/missiles/bombs/torpedoes, guidance, flares/chaff, explosions, particles, hit markers, sounds | Each weapon type fires in a GameTest arena or manual checklist; damage-factor unit tests |
| **M6** | Remaining vehicle types | Planes (VTOL, sweep wing, autopilot), ships, tanks (wheels, tilt, tracks), vehicles, UAV + stations, gunner NPC, containers, chains, parachutes, GLTD, light weapons, drafting table, charger, tools, multiplayer/teams | Every stock vehicle spawns, mounts, moves and renders |
| **M6b** | Wingman | Marker blocks, formation AI, missions state machine, chunk loading, planner GUIs, `/wingman` | Formation + one CAP mission flies end-to-end |
| **M7** | Polish | Full config + `/mcheli` commands, sound/particle parity, render path B (GPU buffers), long-distance tracking/rendering, HBM stubs, thermal vision, performance pass (spark profile vs. upstream numbers), docs | No crash in a 30-minute mixed-vehicle soak; frame-time target agreed at M4 |

---

## 7. Testing strategy

- **Pure JUnit** (`src/test`, no game): TXT/YAML parsers (fixtures = upstream stock content + `legacy/`), `YamlEmitter` round trip, HUD expression evaluator, physics math (extracted, side-effect-free functions), StreamCodec round trips for every payload.
- **NeoForge JUnit** (game bootstrapped): registry contents, data component codecs, recipe generation from content.
- **GameTests** (`RegisterGameTestsEvent`): spawn/mount/dismount, projectile impact + explosion damage, fuel consumption, seat switching.
- **Manual checklists** per milestone for rendering and HUD, with screenshots kept under `docs/screens/`.

---

## 8. Expected behavioural differences (seed for KNOWN_ISSUES.md)

1. No migration of 1.12.2 worlds or items (different mod ID, flattening, new save format).
2. UAV live camera feed: no off-screen world render in 26.2 (shows NO SIGNAL / telemetry).
3. Stippled HUD lines are emulated with dashes; pixel-exact match is not guaranteed.
4. Legacy recipe/ammo item references that cannot be flattened are skipped with a log line.
5. Config entries that matched Java class-name substrings (`DamageVs…`, `IgnoreBulletHit`) now match entity type IDs.
6. GregTech EU charging removed (FE only); The One Probe and Trajectory Preview integrations stubbed.
7. HBM:NTM payloads are inert unless an HBM build for 26.2 exists.
8. Long-distance rendering uses scaled proxies instead of a second far-plane pass.
9. D2: the pilot's client simulates its own vehicle (smoother; server validates). Servers can switch back to upstream's server-authoritative model in config.
10. D7: `/mcheli sendss` screen capture only works for players who opt in (client config), and they get a chat notice.

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| `MCH_EntityAircraft` size (7.2K LOC) hides coupling | Port behind characterisation tests of physics math; split into collaborators only along existing method seams |
| Per-frame CPU vertex submission too slow for large MQO models | Path B GPU-buffer feature renderer (M7); measure at M4 |
| Vulkan backend differences (shader compilation, depth) | Test every milestone from M4 on both backends; vanilla-style GLSL only |
| Mixins against 26.2 internals break on NeoForge updates | Keep to one or two small mixins (M7 only), each with an event-based fallback |
| Aviator syntax used by third-party addons beyond stock content | Evaluator supports Aviator's operators plus common `math.*` functions; unknown syntax logs a clear error |
| OneDrive sync locking Gradle/build outputs (workspace is under OneDrive Desktop) | Exclude `build/`, `.gradle/`, `run/` from sync, or move the repo out of OneDrive |
| Licence ambiguity (`master` lacks LICENSE) | D6 |

---

## 10. Decisions

All nine were approved as recommended on 2026-10-14. They are binding for the port; changing one
needs a new entry here.

| # | Decision | Approved resolution |
|---|---|---|
| D1 | Namespaces: registry IDs `mcheli_ce_port:*`, but assets stay under the **`mcheli`** namespace so existing `mcheli_addons/<pack>/assets/mcheli/…` packs work unchanged | **Yes.** Compatibility namespace only; no `mcheli` mod ID or branding. |
| D2 | Flight authority: upstream is server-authoritative (client sends inputs). 26.2 natively supports the controlling player's client simulating the vehicle (like boats), which removes pilot input lag and the jitter CE fought with update interval 2 | **Client-authoritative piloting** with server sanity checks and a server config to force upstream's model |
| D3 | Wingman subsystem (10K LOC, experimental) | Port as **M6b** after all vehicles work |
| D4 | Replace Aviator (needs a coremod; stock HUDs use only arithmetic, comparisons, `&& \|\| !` and `?:`) with a small built-in evaluator | **Yes**, plus `math.*` functions for addon compatibility |
| D5 | Upstream parser bugs documented in the wiki (VTOL map form, smoke velocity, bbox name, `WeightedCenterZ`) | **Fix them** and log each in CHANGELOG (parity tests pin everything else) |
| D6 | Licence: GPLv3 text from upstream `origin/main` (`7c1b0ee`); `master` has none | Proceed under GPLv3 as you instructed; confirm with upstream before any public release |
| D7 | `/mcheli sendss` lets an operator capture a player's screen | Port it **opt-in**: client config defaults to refusing, and the player sees a chat notice when a capture happens |
| D8 | Drop ElegantNetworking, ModularUI, Lombok and the coremod in favour of plain NeoForge and records | **Yes** (no 26.2 builds of the libraries; idiomatic code) |
| D9 | Repo layout: workspace root is the port repo, `upstream/` git-ignored | Done: repo at `thisisforfreeAI/26.2-port-of-mcheli-cm` (branch `main`); `scripts/fetch-upstream.sh` restores `upstream/` at `cbaff7f` |
