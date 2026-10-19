package io.github.mcheliceport.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.command.ClientRequests;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.net.CommandRequestPayload;
import io.github.mcheliceport.net.ModListPayload;
import io.github.mcheliceport.net.ScreenshotChunkPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * The client side of {@code /mcheli sendss} and {@code /mcheli modlist} (upstream
 * {@code MCH_MultiplayClient}). A screenshot is only sent while {@code privacy.allowScreenCapture} is
 * on, with a chat notice each time; it goes as a PNG, one {@link ScreenshotChunkPayload} a tick. The
 * mod list is only shared while {@code debugLog} is on, as upstream.
 */
public final class CommandRequestClient {

    private static final ArrayDeque<ScreenshotChunkPayload> OUTGOING = new ArrayDeque<>();

    private CommandRequestClient() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CommandRequestClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut loggingOut) -> OUTGOING.clear());
    }

    public static void answer(CommandRequestPayload request) {
        switch (request.action()) {
            case SCREENSHOT -> sendScreenshot(request.requestId());
            case MOD_LIST -> ClientPacketDistributor.sendToServer(CommonConfig.DEBUG_LOG.get()
                    ? new ModListPayload(request.requestId(), true, modList())
                    : new ModListPayload(request.requestId(), false, List.of()));
        }
    }

    private static void sendScreenshot(int requestId) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.ALLOW_SCREEN_CAPTURE.get()) {
            ClientPacketDistributor.sendToServer(ScreenshotChunkPayload.refused(requestId));
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(
                    "The server asked for a screenshot; refused because allowScreenCapture is off")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> Util.ioPool().execute(() -> {
            byte[] png;
            try (image) {
                png = encode(image);
            } catch (IOException e) {
                McheliCePort.LOGGER.warn("Could not encode the screenshot the server asked for", e);
                mc.execute(() -> ClientPacketDistributor.sendToServer(ScreenshotChunkPayload.refused(requestId)));
                return;
            }
            mc.execute(() -> {
                queue(requestId, png);
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal(
                        "The server captured your screen (/mcheli sendss)").withStyle(ChatFormatting.YELLOW));
            });
        }));
    }

    /** {@link NativeImage} only writes PNGs to files. */
    private static byte[] encode(NativeImage image) throws IOException {
        Path file = Files.createTempFile("mcheli_sendss", ".png");
        try {
            image.writeToFile(file);
            return Files.readAllBytes(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void queue(int requestId, byte[] png) {
        if (png.length == 0 || png.length > ClientRequests.MAX_SCREENSHOT_BYTES) {
            ClientPacketDistributor.sendToServer(ScreenshotChunkPayload.refused(requestId));
            return;
        }
        for (int offset = 0; offset < png.length; offset += ScreenshotChunkPayload.MAX_CHUNK) {
            int end = Math.min(png.length, offset + ScreenshotChunkPayload.MAX_CHUNK);
            OUTGOING.add(new ScreenshotChunkPayload(requestId, offset, png.length,
                    Arrays.copyOfRange(png, offset, end)));
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ScreenshotChunkPayload chunk = OUTGOING.poll();
        if (chunk != null && Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(chunk);
        }
    }

    /** Upstream {@code readModList}, from NeoForge's mod list: the game version, then each mod and its file. */
    static List<String> modList() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatFormatting.RED + "###### " + Minecraft.getInstance().getUser().getName() + " ######");
        lines.add(ChatFormatting.AQUA + "# Client " + SharedConstants.getCurrentVersion().name());
        lines.add(ChatFormatting.YELLOW + "=== ActiveModList ===");
        for (IModInfo mod : ModList.get().getMods()) {
            String line = "[" + mod.getModId() + "]  " + mod.getDisplayName() + "[" + mod.getVersion() + "]  "
                    + mod.getOwningFile().getFile().getFileName();
            lines.add(line.length() > ModListPayload.MAX_LINE_LENGTH
                    ? line.substring(0, ModListPayload.MAX_LINE_LENGTH) : line);
            if (lines.size() == ModListPayload.MAX_LINES) {
                break;
            }
        }
        return lines;
    }
}
