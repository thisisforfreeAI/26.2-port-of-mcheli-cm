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
        configScreen();
        for (ModTab tab : ModTab.values()) {
            add(tab.translationKey(), tab.title);
        }
        add("pack.source.mcheli_ce_port.addon", "McHeli addon");
        add("entity.mcheli_ce_port.helicopter", "Helicopter");
        add("entity.mcheli_ce_port.seat", "Seat");
        keys();
        addItem(ModItems.SPAWN_GUNNER_VS_MONSTER, "Gunner (vs Monster)");
        addItem(ModItems.SPAWN_GUNNER_VS_PLAYER, "Gunner (vs Player of other team)");
        addItem(ModItems.RANGEFINDER, "Laser Rangefinder");
        addItem(ModItems.WRENCH, "Wrench");
        addItem(ModItems.FUEL, "Fuel");
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
        addItem(ModItems.INTERNAL, "Internal Item");
        addItem(ModItems.FIM92_BULLET, "FIM-92 Stinger missile");
        addItem(ModItems.FGM148_BULLET, "FGM-148 Javelin missile");
        addItem(ModItems.FIM92, "FIM-92 Stinger");
        addItem(ModItems.FGM148, "FGM-148 Javelin");
        addBlock(ModBlocks.DRAFTING_TABLE, "Drafting Table");
        addBlock(ModBlocks.LIT_DRAFTING_TABLE, "Drafting Table");
        addBlock(ModBlocks.CHARGER, "Aircraft Charger");
        addBlock(ModBlocks.WINGMAN_MARKER, "Marker Block");
    }

    /** The vehicle key mappings (upstream's key names in its config GUI). */
    private void keys() {
        add("key.category.mcheli_ce_port.vehicles", "McHeli CE (26.2 Port)");
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
        add(prefix + "ropes", "Deploy / Retract Ropes");
        add(prefix + "search_light", "Searchlights / Headlights");
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
        add(prefix + "airburst_reset", "Reset Airburst Distance");
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
        add(prefix + "experimentalQuaternionRotation", "Quaternion Rotation");
        add(prefix + "despawnCount", "Wreck Despawn Time");
        add(prefix + "killPassengersWhenDestroyed", "Kill Passengers When Destroyed");
        add(prefix + "autoRepairHP", "Auto Repair Threshold");
        add(prefix + "collisionDestroyBlock", "Break Soft Blocks");
        add(prefix + "weapons", "Weapons");
        add(prefix + "infinityAmmo", "Infinite Ammunition");
        add(prefix + "hurtResistantTime", "Invulnerability After Hits");
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
        add(prefix + "debug", "Debug");
        add(prefix + "testMode", "Test Mode");
        add(prefix + "camera", "Camera");
        add(prefix + "enableCamDistChange", "Allow Camera Distance Change");
        add(prefix + "creativeTabIcons", "Creative Tab Icons");
        for (ModTab tab : ModTab.values()) {
            if (!tab.defaultIcon.isEmpty()) {
                add(prefix + tab.id + "Icon", tab.title.substring("MCHeli CE ".length()) + " Tab Icon");
            }
        }
    }

    private void japanese() {
        addItem(ModItems.SPAWN_GUNNER_VS_MONSTER, "対モンスター 射撃手");
        addItem(ModItems.SPAWN_GUNNER_VS_PLAYER, "対他チームプレイヤー 射撃手");
        addItem(ModItems.RANGEFINDER, "レーザー レンジ ファインダー");
        addItem(ModItems.WRENCH, "レンチ");
        addItem(ModItems.FUEL, "燃料");
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
        addItem(ModItems.RANGEFINDER, "激光测距仪");
        addItem(ModItems.WRENCH, "扳手");
        addItem(ModItems.FUEL, "燃料");
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
