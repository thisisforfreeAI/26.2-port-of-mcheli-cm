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
        addItem(ModItems.SPAWN_GUNNER_VS_MONSTER, "Gunner (vs Monster)");
        addItem(ModItems.SPAWN_GUNNER_VS_PLAYER, "Gunner (vs Player of other team)");
        addItem(ModItems.RANGEFINDER, "Laser Rangefinder");
        addItem(ModItems.WRENCH, "Wrench");
        addItem(ModItems.FUEL, "Fuel");
        addItem(ModItems.GLTD, "GLTD:Target Designator");
        addItem(ModItems.CHAIN, "Chain");
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
        add(prefix + "privacy", "Privacy");
        add(prefix + "allowScreenCapture", "Allow Screen Capture");
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
