package io.github.mcheliceport.command;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.net.CommandRequestPayload;
import io.github.mcheliceport.net.ModListPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.ScreenshotChunkPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * The server side of {@code /mcheli sendss} and {@code /mcheli modlist} (upstream
 * {@code PacketHandleCommand}, {@code PacketImgDataChunk} and {@code PacketSendModlist}): the requests
 * waiting for a client's answer. A request only takes its answer from the player it asked, a
 * screenshot only in order and up to {@link #MAX_SCREENSHOT_BYTES}, and a request nobody answers
 * expires. Saved screenshots go to {@code <server folder>/screenshots_op/<player>_<date>.png}, as
 * upstream. Server thread only.
 */
@EventBusSubscriber(modid = McheliCePort.MOD_ID)
public final class ClientRequests {

    public static final String FOLDER = "screenshots_op";
    public static final int MAX_SCREENSHOT_BYTES = 16 * 1024 * 1024;
    static final int SCREENSHOT_TIMEOUT_TICKS = 20 * 60;
    static final int MOD_LIST_TIMEOUT_TICKS = 20 * 30;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    private static final Map<Integer, Request> REQUESTS = new HashMap<>();
    private static int nextId = 1;
    private static int ticks;

    /** What an answer did. */
    public enum Result {
        UNKNOWN_REQUEST,
        WRONG_PLAYER,
        REFUSED,
        BROKEN,
        INCOMPLETE,
        SAVED,
        FAILED,
        RELAYED,
        NOT_SHARED
    }

    private static final class Request {
        final CommandRequestPayload.Action action;
        final UUID target;
        final String targetName;
        /** The player who asked, or null for the console or a command block. */
        final @Nullable UUID requester;
        int expiresAt;
        byte @Nullable [] data;
        int received;

        Request(CommandRequestPayload.Action action, ServerPlayer target, @Nullable UUID requester, int expiresAt) {
            this.action = action;
            this.target = target.getUUID();
            this.targetName = target.getGameProfile().name();
            this.requester = requester;
            this.expiresAt = expiresAt;
        }
    }

    private ClientRequests() {
    }

    /** Asks {@code target}'s client for a screenshot or its mod list, and returns the request's ID. */
    public static int request(@Nullable ServerPlayer requester, ServerPlayer target,
            CommandRequestPayload.Action action) {
        int id = nextId++;
        int timeout = action == CommandRequestPayload.Action.SCREENSHOT ? SCREENSHOT_TIMEOUT_TICKS
                : MOD_LIST_TIMEOUT_TICKS;
        REQUESTS.put(id, new Request(action, target, requester == null ? null : requester.getUUID(),
                ticks + timeout));
        ModNetwork.sendToPlayer(target, new CommandRequestPayload(id, action));
        return id;
    }

    static int pending() {
        return REQUESTS.size();
    }

    /** One piece of a screenshot, or a refusal, from {@code from}'s client. */
    public static Result acceptScreenshot(ServerPlayer from, ScreenshotChunkPayload chunk) {
        Request request = REQUESTS.get(chunk.requestId());
        if (request == null || request.action != CommandRequestPayload.Action.SCREENSHOT) {
            return Result.UNKNOWN_REQUEST;
        }
        if (!request.target.equals(from.getUUID())) {
            return Result.WRONG_PLAYER;
        }
        MinecraftServer server = from.level().getServer();
        if (chunk.total() == ScreenshotChunkPayload.REFUSED) {
            REQUESTS.remove(chunk.requestId());
            tell(server, request, Component.literal(request.targetName + " does not allow screen captures (allowScreenCapture is off)"));
            return Result.REFUSED;
        }
        byte[] data = request.data;
        int length = chunk.data().length;
        boolean valid = chunk.total() > 0 && chunk.total() <= MAX_SCREENSHOT_BYTES && length > 0
                && chunk.offset() == request.received && (data == null || data.length == chunk.total())
                && length <= chunk.total() - chunk.offset();
        if (!valid) {
            REQUESTS.remove(chunk.requestId());
            tell(server, request, Component.literal(request.targetName + " sent a broken screenshot"));
            return Result.BROKEN;
        }
        if (data == null) {
            data = new byte[chunk.total()];
            request.data = data;
        }
        System.arraycopy(chunk.data(), 0, data, chunk.offset(), length);
        request.received += length;
        // A screenshot still coming in keeps its request alive.
        request.expiresAt = ticks + SCREENSHOT_TIMEOUT_TICKS;
        if (request.received < data.length) {
            return Result.INCOMPLETE;
        }
        REQUESTS.remove(chunk.requestId());
        if (!isPng(data)) {
            tell(server, request, Component.literal(request.targetName + " sent a broken screenshot"));
            return Result.BROKEN;
        }
        try {
            Path file = save(server.getServerDirectory().resolve(FOLDER), request.targetName, data);
            tell(server, request, Component.literal("Saved " + request.targetName + "'s screenshot as " + FOLDER + "/"
                    + file.getFileName()));
            return Result.SAVED;
        } catch (IOException e) {
            McheliCePort.LOGGER.warn("Could not save {}'s screenshot", request.targetName, e);
            tell(server, request, Component.translatable("screenshot.failure", e.getMessage()));
            return Result.FAILED;
        }
    }

    /** The mod list {@code from}'s client answered with, which goes to whoever asked. */
    public static Result acceptModList(ServerPlayer from, ModListPayload list) {
        Request request = REQUESTS.get(list.requestId());
        if (request == null || request.action != CommandRequestPayload.Action.MOD_LIST) {
            return Result.UNKNOWN_REQUEST;
        }
        if (!request.target.equals(from.getUUID())) {
            return Result.WRONG_PLAYER;
        }
        REQUESTS.remove(list.requestId());
        MinecraftServer server = from.level().getServer();
        if (!list.shared()) {
            tell(server, request, Component.literal(request.targetName
                    + " does not share a mod list (their debugLog is off)"));
            return Result.NOT_SHARED;
        }
        for (String line : list.lines()) {
            tell(server, request, Component.literal(line));
        }
        return Result.RELAYED;
    }

    static boolean isPng(byte[] data) {
        return data.length > PNG_SIGNATURE.length
                && Arrays.equals(data, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length);
    }

    /** Upstream's file name, {@code <player>_<yyyy-MM-dd_HH.mm.ss>.png}, numbered if it is taken. */
    static Path save(Path folder, String playerName, byte[] png) throws IOException {
        Files.createDirectories(folder);
        String base = playerName + "_" + LocalDateTime.now().format(DATE);
        Path file = folder.resolve(base + ".png");
        for (int i = 1; Files.exists(file); i++) {
            file = folder.resolve(base + "_" + i + ".png");
        }
        Files.write(file, png);
        return file;
    }

    private static void tell(MinecraftServer server, Request request, Component message) {
        if (request.requester == null) {
            server.sendSystemMessage(message);
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(request.requester);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks % 20 != 0 || REQUESTS.isEmpty()) {
            return;
        }
        Iterator<Request> it = REQUESTS.values().iterator();
        while (it.hasNext()) {
            Request request = it.next();
            if (ticks - request.expiresAt >= 0) {
                it.remove();
                tell(event.getServer(), request, Component.literal(request.targetName + " did not answer").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        REQUESTS.clear();
    }
}
