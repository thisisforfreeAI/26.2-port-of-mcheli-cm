package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.seat.SeatPrompt;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Upstream's {@code GuiInteractSeat}: looking at a vehicle in first person, a prompt below the
 * crosshair names the seat the use key would enter ({@link SeatPrompt}).
 */
public final class InteractSeatHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "interact_seat");
    public static final String ENTER_SEAT = "gui.mcheli.interact_seat.enter";
    private static final int TEXT = 0xFF28A745;
    private static final int BACKGROUND = 0xFF14161C;
    private static final int OFFSET_Y = 50;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 4;

    private InteractSeatHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(AircraftHudLayer.ID, ID, InteractSeatHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.hud.isHidden()
                || mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)
                || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        int seatId = SeatPrompt.targetSeatId(player, mc.crosshairPickEntity);
        if (seatId < 0) {
            return;
        }
        Font font = mc.font;
        Component text = Component.translatable(ENTER_SEAT, mc.options.keyUse.getTranslatedKeyMessage(), seatId);
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + OFFSET_Y;
        int half = font.width(text) / 2;
        graphics.fill(x - half - PADDING_X, y - PADDING_Y, x + half + PADDING_X, y + font.lineHeight + PADDING_Y,
                BACKGROUND);
        graphics.centeredText(font, text, x, y, TEXT);
    }
}
