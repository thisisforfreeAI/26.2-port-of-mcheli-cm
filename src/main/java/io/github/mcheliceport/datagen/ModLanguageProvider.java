package io.github.mcheliceport.datagen;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.registry.ModBlocks;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.registry.ModTab;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Lang files for the fixed items, blocks and creative tabs, from upstream's
 * {@code W_LanguageRegistry} calls and {@code assets/mcheli/lang/*.lang}. Content item names are
 * generated at runtime from the content files.
 */
public class ModLanguageProvider extends LanguageProvider {

    private final String locale;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, McheliCePort.MOD_ID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        switch (this.locale) {
            case "en_us" -> english();
            case "ja_jp" -> japanese();
            case "zh_cn" -> chinese();
            default -> throw new IllegalArgumentException("No translations for " + this.locale);
        }
    }

    private void english() {
        add("mcheli_ce_port.configuration.aircraftCustomization", "Aircraft markings and wear");
        add("mcheli_ce_port.configuration.weaponMechanisms", "Weapon mechanisms");
        add("mcheli_ce_port.configuration.nightFlightLighting", "Night-flight lighting");
        add("mcheli_ce_port.configuration.landingFeedback", "Landing feedback");
        add("mcheli_ce_port.sound.rocket_flyby", "Rocket rushes past");
        add("mcheli_ce_port.sound.shell_flyby", "Cannon shell whizzes past");
        add("mcheli_ce_port.configuration.flybyAudio", "Flyby audio");
        add("mcheli_ce_port.sound.sonic_boom", "Sonic boom");
        add("mcheli_ce_port.configuration.supersonicEffects", "Supersonic effects");
        add("mcheli_ce_port.cockpit.engine", "ENG");
        add("mcheli_ce_port.cockpit.fuel", "FUEL");
        add("mcheli_ce_port.cockpit.hull", "HULL");
        add("mcheli_ce_port.cockpit.radar", "RDR");
        add("mcheli_ce_port.configuration.cockpitFeedback", "Cockpit feedback");
        add("mcheli.effects.blood", "Vehicle-kill blood: %s");
        add("mcheli.effects.blood.tip", "Blood from conventional MC Heli vehicle kills of supported mobs. NTM guns and payloads keep their own gore. Requires Enhanced effects.");
        add("mcheli.effects.menu", "MC Heli Effects…");
        add("gui.mcheli.interact_seat.enter", "Press %s to enter seat %d");
        add("mcheli.effects.modern", "Modern explosions: %s");
        add("mcheli.effects.modern.tip", "Modern billows and layered depth. Original craters stay unchanged. New explosions use this choice immediately.");
        add("mcheli.effects.modern.requires", "Requires Enhanced effects, Polished artwork and Minecraft explosion particles disabled. Open Full mod settings to adjust them.");
        add("mcheli.effects.inactive", "Modern saved; check full mod settings.");
        add("mcheli.effects.detail", "Detail: %s");
        add("mcheli.effects.detail.low", "Low");
        add("mcheli.effects.detail.balanced", "Balanced");
        add("mcheli.effects.detail.high", "High");
        add("mcheli.effects.adaptive", "Adaptive detail: %s");
        add("mcheli.effects.fps", "Frame-rate goal: %s FPS");
        add("mcheli.effects.full", "Full mod settings…");
        add("mcheli_ce_port.configuration.modernExplosions", "Modern explosions");

        configScreen();
        advancements(new String[] {"Welcome to MC Helicopter MOD", "Put the helicopter", "Refueling", "Refuel aircraft",
                "Supply ammo", "Supply ammo to the aircraft", "Relief supplies", "Drop a container",
                "Ride Of The Valkyries", "?", "Ain't war hell?", "?"});
        for (ModTab tab : ModTab.values()) {
            add(tab.translationKey(), tab.title);
        }
        add("pack.source.mcheli_ce_port.addon", "McHeli addon");
        add("entity.mcheli_ce_port.helicopter", "Helicopter");
        add("entity.mcheli_ce_port.seat", "Seat");
        add("entity.mcheli_ce_port.gltd", "GLTD");
        add("entity.mcheli_ce_port.gunner", "Gunner");
        keys();
        addItem(ModItems.SPAWN_GUNNER_VS_MONSTER, "Gunner (vs Monster)");
        addItem(ModItems.SPAWN_GUNNER_VS_PLAYER, "Gunner (vs Player of other team)");
        addItem(ModItems.RANGEFINDER, "Laser Rangefinder");
        addItem(ModItems.WRENCH, "Wrench");
        add("item.mcheli_ce_port.wrench.tooltip", "Shift+Right click to change vehicle skin!");
        addItem(ModItems.FUEL, "Fuel");
        addItem(ModItems.FUEL_BUCKET, "Fuel Bucket");
        add("fluid_type.mcheli_ce_port.mch_fuel", "Fuel");
        add("jei.mcheli_ce_port.info.charge_fuel", "Refill a used fuel can by crafting it with coal or charcoal. "
                + "Each slot of coal adds %s fuel and each slot of charcoal %s, up to a full can of %s. "
                + "One item is used from each slot.");
        add("jei.mcheli_ce_port.info.charge_power_range_finder",
                "Recharge a used rangefinder by crafting it with one redstone dust.");
        addItem(ModItems.GLTD, "GLTD:Target Designator");
        addItem(ModItems.CHAIN, "Chain");
        add("message.mcheli_ce_port.chain.selected", "Cargo selected. Right-click the towing vehicle.");
        add("message.mcheli_ce_port.chain.connected", "Tow chain connected. Right-click either end to disconnect.");
        add("message.mcheli_ce_port.chain.disconnected", "Tow chain disconnected.");
        add("message.mcheli_ce_port.chain.range", "Chain endpoints must be 2 to 16 blocks apart.");
        addItem(ModItems.PARACHUTE, "Parachute");
        addItem(ModItems.CONTAINER, "Container");
        addItem(ModItems.UAV_STATION, "UAV Station");
        addItem(ModItems.UAV_STATION2, "Portable UAV Controller");
        addItem(ModItems.UAV_PAIRING_DEVICE, "UAV Pairing Device");
        addItem(ModItems.UAV_TABLET, "UAV Tablet");
        addItem(ModItems.MINE_DEFUSER, "Mine Defuser");
        add("item.mcheli_ce_port.mine_defuser.tooltip", "Sneak up to a mine and right-click it to defuse");
        addItem(ModItems.INTERNAL, "Internal Item");
        addItem(ModItems.FIM92_BULLET, "FIM-92 Stinger missile");
        addItem(ModItems.FGM148_BULLET, "FGM-148 Javelin missile");
        addItem(ModItems.FIM92, "FIM-92 Stinger");
        addItem(ModItems.FGM148, "FGM-148 Javelin");
        addBlock(ModBlocks.DRAFTING_TABLE, "Drafting Table");
        addBlock(ModBlocks.LIT_DRAFTING_TABLE, "Drafting Table");
        addBlock(ModBlocks.CHARGER, "Aircraft Charger");
        addBlock(ModBlocks.REPAIR_PAD, "Repair Pad");
        addBlock(ModBlocks.WINGMAN_MARKER, "Marker Block");
    }

    /** The vehicle key mappings (upstream's key names in its config GUI). */
    private void keys() {
        add("key.category.mcheli_ce_port.vehicles", "McHeli CE (26.2 Port)");
        add("key.category.mcheli_ce_port.wingman", "Wingman");
        String prefix = "key." + McheliCePort.MOD_ID + ".";
        add(prefix + "up", "Throttle Up / Forward");
        add(prefix + "down", "Throttle Down / Back");
        add(prefix + "right", "Right");
        add(prefix + "left", "Left");
        add(prefix + "switch_gunner", "Gunner Mode");
        add(prefix + "switch_hovering", "Hover / Eject Seat");
        add(prefix + "zoom", "Zoom / Hatch");
        add(prefix + "unmount", "Get Out");
        add(prefix + "extra", "Extra (Fold Blades)");
        add(prefix + "free_look", "Free Look");
        add(prefix + "gui", "Vehicle Menu");
        add(prefix + "gear_up_down", "Landing Gear");
        add(prefix + "put_to_rack", "Load Rack / Board Carrier");
        add(prefix + "down_from_rack", "Unload / Leave Rack");
        add(prefix + "ropes", "Unload Crew / Ropes");
        add(prefix + "search_light", "Searchlights / Headlights");
        add(prefix + "radar_switch", "Radar Switch");
        add(prefix + "open_gps_panel", "Open GPS Panel");
        add(prefix + "wingman_panel", "Wingman Panel");
        add(prefix + "scoreboard", "[MP] Scoreboard");
        add(prefix + "multiplay_manager", "[MP][OP] Multiplay Manager");
        add(prefix + "camera_mode", "Camera Mode (Night Vision)");
        add(prefix + "camera_distance_up", "Camera Distance Up");
        add(prefix + "camera_distance_down", "Camera Distance Down");
        add(prefix + "use_weapon", "Use Weapon");
        add(prefix + "switch_weapon_1", "Switch Weapon 1");
        add(prefix + "switch_weapon_2", "Switch Weapon 2");
        add(prefix + "switch_weapon_mode", "Switch Weapon Mode");
        add(prefix + "reload_weapon", "Reload Weapon");
        add(prefix + "flare", "Flare");
        add(prefix + "chaff", "Chaff");
        add(prefix + "aps", "Active Protection (APS)");
        add(prefix + "ecm_jammer", "ECM Jammer");
        add(prefix + "airburst_reset", "Reset Airburst Distance");
        add(prefix + "mark_target", "Mark Target (Commander)");
    }

    /** NeoForge's config screen: section titles and one name per option. */
    private void configScreen() {
        String prefix = McheliCePort.MOD_ID + ".configuration.";
        add(prefix + "title", "McHeli CE (26.2 Port) Configuration");
        for (String[] side : new String[][] {{"common", "Common"}, {"client", "Client"}, {"server", "Server"}}) {
            String section = prefix + "section.mcheli.ce.port." + side[0] + ".toml";
            add(section, side[1] + " Settings");
            add(section + ".title", "McHeli CE (26.2 Port): " + side[1] + " Settings");
        }
        add(prefix + "general", "General");
        add(prefix + "debugLog", "Debug Logging");
        add(prefix + "vehicles", "Vehicles");
        add(prefix + "serverAuthoritativeMovement", "Server-Side Movement");
        add(prefix + "placeableOnSpongeOnly", "Placeable on Sponge Only");
        add(prefix + "itemDamage", "Keep Damage on the Item");
        add(prefix + "itemFuel", "Keep Fuel on the Item");
        add(prefix + "infinityFuel", "Infinite Fuel");
        add(prefix + "preventingBroken", "Prevent Pick-Up in Survival");
        add(prefix + "dropItemInCreativeMode", "Drop Item in Creative Mode");
        add(prefix + "breakableOnlyPickaxe", "Pick Up Only with a Pickaxe");
        add(prefix + "allHeliSpeed", "Helicopter Speed");
        add(prefix + "autoThrottleDownHeli", "Helicopter Auto Throttle");
        add(prefix + "dismountAll", "Pilot Unloads Mobs");
        add(prefix + "experimentalQuaternionRotation", "Quaternion Rotation");
        add(prefix + "despawnCount", "Wreck Despawn Time");
        add(prefix + "killPassengersWhenDestroyed", "Kill Passengers When Destroyed");
        add(prefix + "autoRepairHP", "Auto Repair Threshold");
        add(prefix + "collisionDestroyBlock", "Break Soft Blocks");
        add(prefix + "weapons", "Weapons");
        add(prefix + "infinityAmmo", "Infinite Ammunition");
        add(prefix + "hurtResistantTime", "Invulnerability After Hits");
        add(prefix + "hitBoxDelayTick", "Gunner Lead on Riders");
        add(prefix + "rangeOfGunnerVsMonsterHorizontal", "Gunner vs Monster Range (Sideways)");
        add(prefix + "rangeOfGunnerVsMonsterVertical", "Gunner vs Monster Range (Vertical)");
        add(prefix + "rangeOfGunnerVsPlayerHorizontal", "Gunner vs Player Range (Sideways)");
        add(prefix + "rangeOfGunnerVsPlayerVertical", "Gunner vs Player Range (Vertical)");
        add(prefix + "bulletBreakableBlocks", "Blocks Bullets Break");
        add(prefix + "damageVsEntity", "Damage vs Other Entities");
        add(prefix + "damageVsLiving", "Damage vs Mobs");
        add(prefix + "damageVsPlayer", "Damage vs Players");
        add(prefix + "damageVsMCHeliAircraft", "Damage vs Aircraft");
        add(prefix + "damageVsMCHeliTank", "Damage vs Tanks");
        add(prefix + "damageVsMCHeliVehicle", "Damage vs Vehicles");
        add(prefix + "damageVsMCHeliOther", "Damage vs Other McHeli Entities");
        add(prefix + "damageMCHeliAircraftByExternal", "Damage to Aircraft");
        add(prefix + "damageMCHeliTankByExternal", "Damage to Tanks");
        add(prefix + "damageMCHeliVehicleByExternal", "Damage to Vehicles");
        add(prefix + "damageMCHeliOtherByExternal", "Damage to Other McHeli Entities");
        add(prefix + "explosions", "Explosions");
        add(prefix + "destroyBlock", "Explosions Break Blocks");
        add(prefix + "flamingBlock", "Explosions Start Fires");
        add(prefix + "wrecks", "Wrecks");
        add(prefix + "fireBlocks", "Wrecks Start Fires");
        add(prefix + "persistentWrecks", "Persistent Wrecks");
        add(prefix + "wreckCraters", "Wreck Blasts Dig Craters");
        add(prefix + "wreckLifetime", "Wreck Lifetime");
        add(prefix + "rotationLimits", "Rotation Limits");
        add(prefix + "enableRotationLimit", "Enable Rotation Limit");
        add(prefix + "pitchLimitMax", "Pitch Limit (Down)");
        add(prefix + "pitchLimitMin", "Pitch Limit (Up)");
        add(prefix + "rollLimit", "Roll Limit");
        add(prefix + "mouse", "Mouse Flight");
        add(prefix + "invertMouse", "Invert Mouse");
        add(prefix + "mouseSensitivity", "Mouse Sensitivity");
        add(prefix + "mouseControlStickModeHeli", "Stick Mode (Helicopters)");
        add(prefix + "mouseControlFlightSimMode", "Flight Sim Mode");
        add(prefix + "privacy", "Privacy");
        add(prefix + "allowScreenCapture", "Allow Screen Capture");
        add(prefix + "render", "Rendering");
        add(prefix + "smoothShading", "Smooth Shading");
        add(prefix + "tiltRiders", "Tilt the Crew");
        add(prefix + "onDemandModels", "Load Models on Demand");
        add(prefix + "onDemandModelLifetimeSeconds", "On-Demand Model Lifetime");
        add(prefix + "hud", "HUD");
        add(prefix + "displayInThirdPerson", "HUD in Third Person");
        add(prefix + "hideKeyHints", "Hide Key Hints");
        add(prefix + "autoScale", "Auto-Scale the HUD");
        add(prefix + "hitMarkColor", "Hit Marker Colour");
        add(prefix + "switchWeaponWithMouseWheel", "Switch Weapons with the Mouse Wheel");
        add(prefix + "effects", "Weapon Effects");
        add(prefix + "defaultExplosionParticle", "Vanilla Explosion Particles");
        add(prefix + "aliveTimeOfCartridge", "Cartridge Lifetime");
        add(prefix + "effectQuality", "Effect Quality");
        add(prefix + "debug", "Debug");
        add(prefix + "testMode", "Test Mode");
        add(prefix + "camera", "Camera");
        add(prefix + "enableCamDistChange", "Allow Camera Distance Change");
        add(prefix + "spotting", "Spotting");
        add(prefix + "enableEntityMarker", "Allow Entity Markers");
        add(prefix + "rangeFinderSpotDist", "Rangefinder Spot Distance");
        add(prefix + "rangeFinderSpotTime", "Rangefinder Spot Time");
        add(prefix + "rangeFinderConsume", "Rangefinder Uses Charge");
        add(prefix + "displayEntityMarker", "Show Entity Markers");
        add(prefix + "entityMarkerSize", "Entity Marker Size");
        add(prefix + "blockMarkerSize", "Mark Point Size");
        add(prefix + "creativeTabIcons", "Creative Tab Icons");
        for (ModTab tab : ModTab.values()) {
            if (!tab.defaultIcon.isEmpty()) {
                add(prefix + tab.id + "Icon", tab.title.substring("MCHeli CE ".length()) + " Tab Icon");
            }
        }
    }

    /** Upstream's achievement names, {@code achievement.McHeli*} in its lang files, for the advancements. */
    private static final String[] ADVANCEMENTS = {"root", "supply_fuel", "supply_ammo", "relief_supplies",
            "ride_valkyries", "aint_war_hell"};

    private void advancements(String[] titlesAndDescriptions) {
        for (int i = 0; i < ADVANCEMENTS.length; i++) {
            String key = "advancements." + McheliCePort.MOD_ID + "." + ADVANCEMENTS[i];
            add(key + ".title", titlesAndDescriptions[i * 2]);
            add(key + ".description", titlesAndDescriptions[i * 2 + 1]);
        }
    }

    private void japanese() {
        add("gui.mcheli.interact_seat.enter", "%s で座席 %d に搭乗");
        advancements(new String[] {"MC Helicopter MOD へようこそ", "ヘリコプターを設置", "燃料補給", "燃料を補給",
                "弾薬補給", "弾薬を補給", "支援物資", "コンテナを投下", "ワルキューレの騎行", "?", "ホント戦争は地獄だぜ", "?"});
        addItem(ModItems.SPAWN_GUNNER_VS_MONSTER, "対モンスター 射撃手");
        addItem(ModItems.SPAWN_GUNNER_VS_PLAYER, "対他チームプレイヤー 射撃手");
        addItem(ModItems.RANGEFINDER, "レーザー レンジ ファインダー");
        addItem(ModItems.WRENCH, "レンチ");
        addItem(ModItems.FUEL, "燃料");
        addItem(ModItems.FUEL_BUCKET, "燃料入りバケツ");
        add("fluid_type.mcheli_ce_port.mch_fuel", "燃料");
        addItem(ModItems.GLTD, "GLTD:レーザー目標指示装置");
        addItem(ModItems.CHAIN, "鎖");
        addItem(ModItems.PARACHUTE, "パラシュート");
        addItem(ModItems.CONTAINER, "コンテナ");
        addItem(ModItems.UAV_STATION, "UAVステーション");
        addItem(ModItems.UAV_STATION2, "携帯UAV制御端末");
        addBlock(ModBlocks.DRAFTING_TABLE, "製図台");
        addBlock(ModBlocks.LIT_DRAFTING_TABLE, "製図台");
        addBlock(ModBlocks.WINGMAN_MARKER, "マーカーブロック");
    }

    private void chinese() {
        add("gui.mcheli.interact_seat.enter", "按 %s 进入座位 %d");
        advancements(new String[] {"欢迎使用MC直升机模组!", "飞翔吧~少年", "燃料满满", "停机加油~亲", "火力十足",
                "没有弹药啦~补给 补给", "物资支援", "物资箱下投", "女武神的騎行", "?", "战争就是地狱?", "?"});
        addItem(ModItems.RANGEFINDER, "激光测距仪");
        addItem(ModItems.WRENCH, "扳手");
        addItem(ModItems.FUEL, "燃料");
        addItem(ModItems.FUEL_BUCKET, "燃料桶");
        add("fluid_type.mcheli_ce_port.mch_fuel", "燃料");
        addItem(ModItems.GLTD, "GLTD:目标指示器 (呼叫空中打击)");
        addItem(ModItems.CHAIN, "锁链");
        addItem(ModItems.PARACHUTE, "降落伞");
        addItem(ModItems.CONTAINER, "集装箱");
        addItem(ModItems.UAV_STATION, "無人機站");
        addItem(ModItems.UAV_STATION2, "無人機控制器");
        addItem(ModItems.FGM148, "FGM-148 標槍飛彈发射器");
        addItem(ModItems.FGM148_BULLET, "FGM-148 標槍飛彈");
        addItem(ModItems.FIM92, "FIM-92 刺針飛彈发射器");
        addItem(ModItems.FIM92_BULLET, "FIM-92 刺針飛彈");
        addBlock(ModBlocks.DRAFTING_TABLE, "绘图台");
        addBlock(ModBlocks.LIT_DRAFTING_TABLE, "绘图台");
        // Upstream's zh_cn file also named these stock content items, whose content files have no
        // Chinese names.
        add("item.mcheli_ce_port.m18_green", "M18 烟雾弹 (绿色)");
        add("item.mcheli_ce_port.m18_red", "M18 烟雾弹 (红色)");
        add("item.mcheli_ce_port.m18_violet", "M18 烟雾弹 (紫色)");
        add("item.mcheli_ce_port.m18_yellow", "M18 烟雾弹 (黄色)");
        add("item.mcheli_ce_port.fuel_truck", "加油车");
        add("item.mcheli_ce_port.ammo_box", "弹药箱");
    }
}
