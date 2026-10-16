package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.menu.UavStationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Deployment and saved-UAV telemetry; a live off-screen camera preview is deliberately absent. */
public class UavStationScreen extends AbstractContainerScreen<UavStationMenu> {
    private final java.util.List<Button> rowButtons = new java.util.ArrayList<>();
    private int page;
    public UavStationScreen(UavStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 420, 244); inventoryLabelY = 125;
    }
    @Override protected void init() {
        super.init();
        boolean tablet = menu.station() != null && menu.station().handheld();
        if (!tablet) { button("Deploy", 42, 26, 58, 0); }
        addRenderableWidget(Button.builder(Component.literal("Connect"), b -> {
            if (menu.selected() != null) { action(menu.selected(), 1); }
            else if (minecraft.gameMode != null) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1); }
        }).bounds(leftPos + 106, topPos + 26, 88, 18).build());
        button("Exit", 200, 26, 46, 2);
        for (int axis = 0; axis < 3 && !tablet; axis++) {
            button("-", 54 + axis * 66, 102, 20, 3 + axis * 2);
            button("+", 78 + axis * 66, 102, 20, 4 + axis * 2);
        }
        rowButtons.clear();
        for (int i = 0; i < 5; i++) {
            final int row = i;
            rowButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> {
                int index = page * 5 + row;
                if (index < menu.rows().size()) { action(menu.rows().get(index).id(), 0); }
            }).bounds(leftPos + 266, topPos + 28 + i * 22, 144, 20).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1)).bounds(leftPos + 266, topPos + 144, 32, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(Math.max(0, (menu.rows().size() - 1) / 5), page + 1)).bounds(leftPos + 378, topPos + 144, 32, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Unpair selected"), b -> {
            if (menu.selected() != null) { action(menu.selected(), 2); }
        }).bounds(leftPos + 266, topPos + 172, 144, 18).build());
        refreshRows();
    }
    private void action(java.util.UUID id, int action) {
        if (menu.station() != null) { net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new io.github.mcheliceport.net.UavRosterActionPayload(menu.containerId, menu.station().getId(), id, action)); }
    }
    @Override protected void containerTick() { super.containerTick(); refreshRows(); }
    private void refreshRows() {
        page = Math.min(page, Math.max(0, (menu.rows().size() - 1) / 5));
        for (int i = 0; i < rowButtons.size(); i++) {
            int index = page * 5 + i; var b = rowButtons.get(i); b.active = index < menu.rows().size();
            if (b.active) {
                var row = menu.rows().get(index);
                b.setMessage(Component.literal((row.id().equals(menu.selected()) ? "> " : "") + row.id().toString().substring(0, 4) + " " + font.plainSubstrByWidth(row.name(), 96)));
            } else { b.setMessage(Component.literal("-")); }
        }
    }
    private void button(String text, int x, int y, int width, int id) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> {
            if (minecraft != null && minecraft.gameMode != null) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
        }).bounds(leftPos + x, topPos + y, width, 18).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        for (var slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF555555);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF8B8B8B);
        }
        g.fill(leftPos + 10, topPos + 49, leftPos + 250, topPos + 86, 0xFF202820);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        var station = menu.station(); if (station == null) { return; }
        var row = menu.selectedRow();
        String name = row == null ? "No paired UAV selected" : row.name();
        if (name.length() > 32) { name = name.substring(0, 32); }
        g.text(font, "Preview unavailable - telemetry only", 16, 54, 0xFFAAAAAA, false);
        g.text(font, name, 16, 66, 0xFFCCFFCC, false);
        if (row != null) {
            String telemetry = row.status() + "  Fuel " + row.fuel() + "% HP " + row.hp() + "/" + row.maxHp()
                    + (row.distance() >= 0 ? " " + row.distance() + "m" : "");
            g.text(font, font.plainSubstrByWidth(telemetry, 230), 16, 76, 0xFFCCFFCC, false);
        }
        if (!station.handheld()) {
            g.text(font, "Offset", 10, 92, 0xFF404040, false);
            for (int axis = 0; axis < 3; axis++) { g.text(font, "XYZ".charAt(axis) + ": " + station.offset(axis), 54 + axis * 66, 91, 0xFF404040, false); }
        } else { g.text(font, "Deploy UAVs at a placed station.", 16, 100, 0xFF404040, false); }
        g.text(font, "Paired UAVs: " + menu.rows().size(), 266, 12, 0xFF404040, false);
        g.text(font, (page + 1) + "/" + Math.max(1, (menu.rows().size() + 4) / 5), 326, 149, 0xFF404040, false);
        g.text(font, "I: menu   Y: exit when outside this menu", 16, 214, 0xFF404040, false);
        g.text(font, font.plainSubstrByWidth(station.feedback(), 390), 16, 228, 0xFF803020, false);
    }
}
