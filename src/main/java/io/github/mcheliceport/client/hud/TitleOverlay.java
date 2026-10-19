package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.net.TitlePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What {@code /mcheli} does on the client: upstream {@code MCH_GuiTitle}, the message
 * {@code /mcheli title} shows, fading in and out over five ticks in lines as wide as the chat, and
 * upstream {@code ClientCommonTickHandler.clearMarkersAndDebug}, which turns entity hitboxes off while
 * the server forbids them ({@code /mcheli showboundingbox false}).
 */
public final class TitleOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "title");

    private static final List<FormattedCharSequence> LINES = new ArrayList<>();
    private static int restShowTick;
    private static int showTick;
    private static int position;

    private TitleOverlay() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, ID, TitleOverlay::render);
        NeoForge.EVENT_BUS.addListener(TitleOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut loggingOut) -> clear());
    }

    /** Upstream {@code setupTitle}: splits the message at line breaks and at the chat's width. */
    public static void show(TitlePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        LINES.clear();
        Component message = mc.options.chatColors().get() ? payload.message()
                : Component.literal(payload.message().getString());
        int width = Mth.floor(chatWidth(mc) / mc.options.chatScale().get());
        LINES.addAll(mc.font.split(message, Math.max(1, width)));
        while (LINES.size() > 100) {
            LINES.removeLast();
        }
        position = payload.position();
        showTick = 0;
        restShowTick = payload.showTime();
    }

    /** Upstream {@code calculateChatboxWidth}. */
    private static int chatWidth(Minecraft mc) {
        return Mth.floor(mc.options.chatWidth().get() * (320 - 40) + 40);
    }

    private static void clear() {
        LINES.clear();
        restShowTick = 0;
        showTick = 0;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) {
            return;
        }
        if (restShowTick > 0 && !LINES.isEmpty()) {
            showTick++;
            restShowTick--;
        }
        if (!ServerSettings.client().enableDebugBoundingBox()
                && mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER);
        }
    }

    /** Upstream {@code drawChat}: twice the chat's scale, centred, on a band as dark as the chat's. */
    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (restShowTick <= 0 || LINES.isEmpty() || mc.player == null || mc.gui.hud.isHidden()) {
            return;
        }
        float colorAlpha = 1.0F;
        if (restShowTick > 20 && showTick < 5) {
            colorAlpha = 0.2F * showTick;
        }
        if (showTick > 0 && restShowTick < 5) {
            colorAlpha = 0.2F * restShowTick;
        }
        float charAlpha = (float) (mc.options.chatOpacity().get() * 0.9F + 0.1F);
        int alpha = (int) (255.0F * charAlpha * colorAlpha);
        if (alpha <= 3) {
            return;
        }
        float scale = (float) (mc.options.chatScale().get() * 2.0);
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float blockHeight = LINES.size() * 9.0F * scale;
        float posY = switch (position) {
            case 1 -> 0.0F;
            case 2 -> screenHeight - blockHeight;
            case 3 -> screenHeight / 3.0F - blockHeight / 2.0F;
            case 4 -> screenHeight * 2.0F / 3.0F - blockHeight / 2.0F;
            default -> screenHeight / 2.0F - blockHeight / 2.0F;
        };
        Font font = mc.font;
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, posY);
        graphics.pose().scale(scale, scale);
        int bandWidth = Mth.ceil(screenWidth / scale);
        for (int i = 0; i < LINES.size(); i++) {
            FormattedCharSequence line = LINES.get(i);
            int y = i * 9;
            graphics.fill(0, y, bandWidth, y + 9, (alpha / 2) << 24);
            int x = (int) ((screenWidth / 2.0F - font.width(line)) / scale);
            graphics.text(font, line, x, y + 1, 0xFFFFFF | alpha << 24, true);
        }
        graphics.pose().popMatrix();
    }
}
