package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.WingmanPanelOpenPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/**
 * Upstream's {@code WingmanKeyHandler}: while the player pilots an aircraft with no screen open,
 * the wingman panel key asks the server for the panel, and a "[key] Wingman Panel" hint sits on a
 * dark strip at the right of the screen, 150 pixels down, below upstream's key hints.
 */
public final class WingmanKeyHintLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "wingman_key_hint");
    private static final int Y_OFFSET = 150;

    private WingmanKeyHintLayer() {
    }

    public static void register(io.github.mcheliceport.platform.HudLayers event) {
        event.registerAbove(AircraftHudLayer.ID, ID, WingmanKeyHintLayer::render);
    }

    private static boolean piloting(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && mc.gui.screen() == null && player.getVehicle() instanceof AircraftEntity;
    }

    /** Upstream {@code onClientTick}. */
    public static void handleKey() {
        Minecraft mc = Minecraft.getInstance();
        boolean clicked = ModKeyMappings.WINGMAN_PANEL.consumeClick();
        if (clicked && piloting(mc)) {
            io.github.mcheliceport.platform.Platform.get().sendToServer(WingmanPanelOpenPayload.INSTANCE);
        }
    }

    /** Upstream {@code drawKeyHint}: the key in orange, the label in white. */
    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!piloting(mc) || mc.gui.hud.isHidden()) {
            return;
        }
        Font font = mc.font;
        String keyStr = "[" + ModKeyMappings.WINGMAN_PANEL.getTranslatedKeyMessage().getString() + "]";
        String descStr = " Wingman Panel";
        int keyW = font.width(keyStr);
        int totalW = keyW + font.width(descStr);
        int x = graphics.guiWidth() - totalW - 4;
        int y = Y_OFFSET;
        graphics.fill(x - 2, y - 1, x + totalW + 2, y + font.lineHeight + 1, 0x88000000);
        graphics.text(font, keyStr, x, y, 0xFFFFAA00);
        graphics.text(font, descStr, x + keyW, y, 0xFFFFFFFF);
    }
}
