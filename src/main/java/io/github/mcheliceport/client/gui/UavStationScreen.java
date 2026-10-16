package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.menu.UavStationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Deployment and saved-UAV telemetry; a live off-screen camera preview is deliberately absent. */
public class UavStationScreen extends AbstractContainerScreen<UavStationMenu> {
    public UavStationScreen(UavStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 260, 244); inventoryLabelY = 125;
    }
    @Override protected void init() {
        super.init();
        button("Deploy", 42, 26, 58, 0); button("Connect last", 106, 26, 88, 1); button("Exit", 200, 26, 46, 2);
        for (int axis = 0; axis < 3; axis++) {
            button("-", 54 + axis * 66, 102, 20, 3 + axis * 2);
            button("+", 78 + axis * 66, 102, 20, 4 + axis * 2);
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
        var ac = station.lastAircraft();
        String name = ac == null ? "No loaded UAV" : ac.getAcInfo() == null ? "UAV" : ac.getAcInfo().displayName;
        if (name.length() > 32) { name = name.substring(0, 32); }
        g.text(font, "NO SIGNAL - preview unavailable", 16, 54, 0xFFAAAAAA, false);
        g.text(font, name, 16, 66, 0xFFCCFFCC, false);
        if (ac != null) {
            g.text(font, "Fuel " + (int) (ac.getFuelPercentage() * 100) + "%   HP " + ac.getHP() + "/" + ac.getMaxHP()
                    + "   " + (int) station.distanceTo(ac) + "m", 16, 76, 0xFFCCFFCC, false);
        }
        g.text(font, "Offset", 10, 92, 0xFF404040, false);
        for (int axis = 0; axis < 3; axis++) { g.text(font, "XYZ".charAt(axis) + ": " + station.offset(axis), 54 + axis * 66, 91, 0xFF404040, false); }
        g.text(font, "I: menu   Y: exit when outside this menu", 16, 214, 0xFF404040, false);
        g.text(font, station.feedback(), 16, 228, 0xFF803020, false);
    }
}
