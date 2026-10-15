package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftWeapons;
import io.github.mcheliceport.menu.AircraftMenu;
import io.github.mcheliceport.net.ResupplyPayload;
import io.github.mcheliceport.weapon.WeaponSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The fuel slots and trunk of a vehicle (decision D12): the trunk and player inventory on
 * vanilla's chest background, with a fuel panel on the left (fuel can in, empty can out, and
 * upstream's fuel gauge), plus the plane's parachute reserves below that panel.
 *
 * <p>Beside them, the weapon list of upstream CE's aircraft GUI: each weapon set with a magazine,
 * its magazine and reserve counts, the round items one supply costs, and a reload button that
 * pays them ({@link AircraftWeapons#supplyAmmo}). The rest of that GUI comes with M6.
 */
public class AircraftScreen extends AbstractContainerScreen<AircraftMenu> {

    private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier FUEL_SLOT = Identifier.fromNamespaceAndPath("mcheli", "textures/gui/fuel_slot.png");
    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int DARK = 0xFF373737;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int WEAPON_PANEL_W = 176;
    private static final int WEAPON_ROW_H = 22;

    /** A weapon set with a magazine and its reload button. */
    private record WeaponRow(int weaponId, WeaponSet set, Button button) {
    }

    private final List<WeaponRow> weaponRows = new ArrayList<>();

    public AircraftScreen(AircraftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AircraftMenu.CONTENT_X + 176, menu.getPlayerInventoryY() + 82 + 1);
        this.titleLabelX = AircraftMenu.CONTENT_X + 8;
        this.inventoryLabelX = AircraftMenu.CONTENT_X + 8;
        this.inventoryLabelY = menu.getPlayerInventoryY() - 11;
    }

    private List<Integer> weaponIds() {
        List<Integer> ids = new ArrayList<>();
        AircraftEntity aircraft = this.menu.getAircraft();
        if (aircraft != null) {
            for (int i = 0; i < aircraft.getWeaponNum(); i++) {
                if (aircraft.getWeapon(i).getInfo() != null && aircraft.getWeapon(i).getMagSize() > 0) {
                    ids.add(i);
                }
            }
        }
        return ids;
    }

    private int weaponPanelX() {
        return this.leftPos + this.imageWidth + 2;
    }

    @Override
    protected void init() {
        super.init();
        this.weaponRows.clear();
        List<Integer> ids = this.weaponIds();
        AircraftEntity aircraft = this.menu.getAircraft();
        if (ids.isEmpty() || aircraft == null) {
            return;
        }
        // Centre the menu and the weapon list together.
        this.leftPos = Math.max(0, (this.width - this.imageWidth - 2 - WEAPON_PANEL_W) / 2);
        int px = this.weaponPanelX();
        for (int r = 0; r < ids.size(); r++) {
            int id = ids.get(r);
            WeaponSet ws = aircraft.getWeapon(id);
            Button button = Button.builder(Component.literal("Reload"), b -> this.resupply(id))
                    .bounds(px + WEAPON_PANEL_W - 48, this.topPos + 16 + r * WEAPON_ROW_H, 44, 18)
                    .tooltip(Tooltip.create(this.costOf(ws)))
                    .build();
            this.addRenderableWidget(button);
            this.weaponRows.add(new WeaponRow(id, ws, button));
        }
        this.updateButtons();
    }

    private void resupply(int weaponId) {
        AircraftEntity aircraft = this.menu.getAircraft();
        if (aircraft != null && this.minecraft != null && this.minecraft.player != null
                && aircraft.weapons().canResupply(this.minecraft.player, aircraft.getWeapon(weaponId))) {
            ClientPacketDistributor.sendToServer(new ResupplyPayload(aircraft.getId(), weaponId));
            aircraft.weapons().supplyAmmo(weaponId, this.minecraft.player);
        }
    }

    /** "Reload: 1x Iron Ingot, ..." from the definition's round items. */
    private Component costOf(WeaponSet ws) {
        List<String> parts = new ArrayList<>();
        for (WeaponInfo.RoundItem ri : ws.getInfo().roundItems) {
            Item item = AircraftWeapons.itemOf(ri);
            parts.add(ri.num() + "x " + (item != null ? new ItemStack(item).getHoverName().getString() : ri.itemName()));
        }
        return Component.literal(parts.isEmpty() ? "Reload (free)" : "Reload: " + String.join(", ", parts));
    }

    private void updateButtons() {
        AircraftEntity aircraft = this.menu.getAircraft();
        for (WeaponRow row : this.weaponRows) {
            row.button().active = aircraft != null && this.minecraft != null && this.minecraft.player != null
                    && aircraft.weapons().canResupply(this.minecraft.player, row.set());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.updateButtons();
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
        int ph = this.menu.getParachuteSlots() > 0 ? 120 + this.menu.getParachuteSlots() * 18 : 17 + 4 * 18 + 16;
        graphics.fill(px, py, px + AircraftMenu.CONTENT_X + 4, py + ph, DARK);
        graphics.fill(px + 1, py + 1, px + AircraftMenu.CONTENT_X + 4, py + ph - 1, PANEL_BG);
        this.slotFrame(graphics, x + 10, y + 17, true);
        this.slotFrame(graphics, x + 10, y + 17 + 3 * 18, false);
        for (int i = 0; i < this.menu.getParachuteSlots(); i++) {
            this.slotFrame(graphics, x + 10, y + 117 + i * 18, false);
        }
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

        if (!this.weaponRows.isEmpty()) {
            int wx = this.weaponPanelX();
            int wh = 14 + this.weaponRows.size() * WEAPON_ROW_H + 4;
            graphics.fill(wx, y, wx + WEAPON_PANEL_W, y + wh, DARK);
            graphics.fill(wx + 1, y + 1, wx + WEAPON_PANEL_W - 1, y + wh - 1, PANEL_BG);
        }
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
        if (this.menu.getParachuteSlots() > 0) {
            graphics.text(this.font, "Chutes", 2, 106, 0xFF404040, false);
        }
        if (!this.weaponRows.isEmpty()) {
            int wx = this.weaponPanelX() - this.leftPos;
            graphics.text(this.font, "Weapons", wx + 6, 5, 0xFF404040, false);
            for (int r = 0; r < this.weaponRows.size(); r++) {
                WeaponSet ws = this.weaponRows.get(r).set();
                int ry = 16 + r * WEAPON_ROW_H;
                String name = this.font.plainSubstrByWidth(ws.getInfo().displayName, WEAPON_PANEL_W - 60);
                graphics.text(this.font, name, wx + 6, ry, 0xFF404040, false);
                graphics.text(this.font, ws.getAmmo() + "/" + ws.getMagSize() + " (" + ws.getRestAllAmmoNum() + ")",
                        wx + 6, ry + 10, 0xFF606060, false);
            }
        }
    }
}
