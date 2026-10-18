package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.net.GpsSetPayload;
import io.github.mcheliceport.weapon.GpsPosition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Upstream's {@code MCH_GuiGPSInput}: X, Y and Z fields for the GPS point, a button that fills in
 * the player's position, and Done (or Enter) to set the point.
 */
public class GpsInputScreen extends Screen {

    private final Player player;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private String message = "";

    public GpsInputScreen(Player player) {
        super(Component.literal("GPS Target Coordinates"));
        this.player = player;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Fill with player position"),
                b -> this.fillFromPlayerPos()).bounds(centerX - 102, centerY + 44, 204, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.applyGps())
                .bounds(centerX - 102, centerY + 68, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(centerX + 2, centerY + 68, 100, 20).build());
        this.xField = this.field(centerX - 100, centerY - 34, "X");
        this.yField = this.field(centerX - 100, centerY - 8, "Y");
        this.zField = this.field(centerX - 100, centerY + 18, "Z");
        GpsPosition.Point gps = GpsPosition.client();
        if (gps != null) {
            this.xField.setValue(String.valueOf(gps.x()));
            this.yField.setValue(String.valueOf(gps.y()));
            this.zField.setValue(String.valueOf(gps.z()));
        }
    }

    private EditBox field(int x, int y, String name) {
        EditBox field = new EditBox(this.font, x, y, 200, 20, Component.literal(name));
        field.setMaxLength(24);
        return this.addRenderableWidget(field);
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.xField);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.applyGps();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        graphics.centeredText(this.font, "GPS Target Coordinates", centerX, centerY - 62, 0xFFFFFFFF);
        graphics.text(this.font, "X", centerX - 112, centerY - 28, 0xFFFFFFFF);
        graphics.text(this.font, "Y", centerX - 112, centerY - 2, 0xFFFFFFFF);
        graphics.text(this.font, "Z", centerX - 112, centerY + 24, 0xFFFFFFFF);
        if (!this.message.isEmpty()) {
            graphics.centeredText(this.font, this.message, centerX, centerY + 94, 0xFFFF5555);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void applyGps() {
        try {
            double x = Double.parseDouble(this.xField.getValue().trim());
            double y = Double.parseDouble(this.yField.getValue().trim());
            double z = Double.parseDouble(this.zField.getValue().trim());
            GpsPosition.setClient(new GpsPosition.Point(x, y, z, true));
            ClientPacketDistributor.sendToServer(new GpsSetPayload(x, y, z, true));
            this.onClose();
        } catch (NumberFormatException e) {
            this.message = "Invalid coordinates";
        }
    }

    private void fillFromPlayerPos() {
        this.xField.setValue(String.valueOf(this.player.getX()));
        this.yField.setValue(String.valueOf(this.player.getY()));
        this.zField.setValue(String.valueOf(this.player.getZ()));
        this.message = "";
    }
}
