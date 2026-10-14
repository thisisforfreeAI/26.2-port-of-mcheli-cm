package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.menu.AircraftMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The fuel slots and trunk of a vehicle (decision D12): the trunk and player inventory on
 * vanilla's chest background, with a fuel panel on the left (fuel can in, empty can out, and
 * upstream's fuel gauge).
 */
public class AircraftScreen extends AbstractContainerScreen<AircraftMenu> {

    private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier FUEL_SLOT = Identifier.fromNamespaceAndPath("mcheli", "textures/gui/fuel_slot.png");
    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int DARK = 0xFF373737;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SLOT_BG = 0xFF8B8B8B;

    public AircraftScreen(AircraftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AircraftMenu.CONTENT_X + 176, menu.getPlayerInventoryY() + 82 + 1);
        this.titleLabelX = AircraftMenu.CONTENT_X + 8;
        this.inventoryLabelX = AircraftMenu.CONTENT_X + 8;
        this.inventoryLabelY = menu.getPlayerInventoryY() - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        int cx = x + AircraftMenu.CONTENT_X;
        int rows = this.menu.getTrunkRows();
        // Trunk rows and the player inventory on the chest background.
        graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, cx, y, 0.0F, 0.0F, 176, 17, 256, 256);
        for (int row = 0; row < rows; row++) {
            int ry = y + 17 + row * 18;
            graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, cx, ry, 0.0F, 17.0F, 176, 18, 256, 256);
        }
        int trunk = this.menu.getTrunkSize();
        for (int i = trunk; i < rows * AircraftMenu.COLUMNS; i++) {
            // Hide the slot frames the trunk does not have.
            int sx = cx + 7 + i % AircraftMenu.COLUMNS * 18;
            int sy = y + 17 + i / AircraftMenu.COLUMNS * 18;
            graphics.fill(sx, sy, sx + 18, sy + 18, PANEL_BG);
        }
        int invTop = y + 17 + rows * 18;
        graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, cx, invTop, 0.0F, 126.0F, 176, 96, 256, 256);

        // The fuel panel left of the chest.
        int px = x;
        int py = y;
        int ph = 17 + 4 * 18 + 16;
        graphics.fill(px, py, px + AircraftMenu.CONTENT_X + 4, py + ph, DARK);
        graphics.fill(px + 1, py + 1, px + AircraftMenu.CONTENT_X + 4, py + ph - 1, PANEL_BG);
        this.slotFrame(graphics, x + 10, y + 17, true);
        this.slotFrame(graphics, x + 10, y + 17 + 3 * 18, false);
        // Upstream's gauge: 50 pixels for a full tank.
        AircraftEntity aircraft = this.menu.getAircraft();
        float fuel = aircraft != null ? aircraft.getFuelPercentage() : 0.0F;
        int gaugeX = x + 31;
        int gaugeTop = y + 19;
        graphics.fill(gaugeX - 1, gaugeTop - 1, gaugeX + 5, gaugeTop + 51, DARK);
        graphics.fill(gaugeX, gaugeTop, gaugeX + 4, gaugeTop + 50, SLOT_BG);
        int ff = (int) (fuel * 50.0F);
        int color = fuel > 0.2F ? 0xFF20C020 : 0xFFE03030;
        graphics.fill(gaugeX, gaugeTop + 50 - ff, gaugeX + 4, gaugeTop + 50, color);
    }

    private void slotFrame(GuiGraphicsExtractor graphics, int sx, int sy, boolean fuelIcon) {
        graphics.fill(sx, sy, sx + 18, sy + 18, DARK);
        graphics.fill(sx + 1, sy + 1, sx + 18, sy + 18, LIGHT);
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_BG);
        if (fuelIcon && this.menu.getSlot(AircraftEntity.SLOT_FUEL_IN).getItem().isEmpty()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, FUEL_SLOT, sx + 1, sy + 1, 0.0F, 0.0F, 16, 16, 16, 16);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        super.extractLabels(graphics, xm, ym);
        AircraftEntity aircraft = this.menu.getAircraft();
        int percent = aircraft != null ? (int) (aircraft.getFuelPercentage() * 100.0F + 0.5F) : 0;
        graphics.text(this.font, String.format("%3d", percent) + "%", 6, 17 + 4 * 18 + 4, 0xFF404040, false);
    }
}
