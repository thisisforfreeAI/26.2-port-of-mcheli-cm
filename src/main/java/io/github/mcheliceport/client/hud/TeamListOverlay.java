package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.gui.MultiplayManagerScreen;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.net.MultiplayActionPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's team lists ({@code MCH_GuiScoreboard_Base.drawList}): a column per team, and one for
 * the players in no team, each with its friendly fire setting and its players' list scores and
 * pings. Held, the scoreboard key shows them over the game ({@code isDrawScoreboard}); the
 * multiplay manager draws them with a column left free for its buttons.
 */
public final class TeamListOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "team_list");
    private static final Identifier[] PING_SPRITES = {
            Identifier.withDefaultNamespace("icon/ping_5"), Identifier.withDefaultNamespace("icon/ping_4"),
            Identifier.withDefaultNamespace("icon/ping_3"), Identifier.withDefaultNamespace("icon/ping_2"),
            Identifier.withDefaultNamespace("icon/ping_1"), Identifier.withDefaultNamespace("icon/ping_unknown")};
    /** Upstream's row background, 553648127. */
    private static final int ROW_BG = 0x20FFFFFF;
    private static final int YELLOW = 0xFFFFFF55;

    private static boolean drawScoreboard;

    private TeamListOverlay() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.TITLE, ID, TeamListOverlay::render);
    }

    /** Upstream {@code handleScoreboardAndMultiplayer}, each client tick with no screen open. */
    public static void handleKeys() {
        Minecraft mc = Minecraft.getInstance();
        boolean manager = consumeAll(ModKeyMappings.MULTIPLAY_MANAGER);
        if (mc.player == null || mc.gui.screen() != null) {
            return;
        }
        drawScoreboard = ModKeyMappings.SCOREBOARD.isDown();
        if (!drawScoreboard && manager) {
            ClientPacketDistributor.sendToServer(new MultiplayActionPayload(MultiplayActionPayload.Action.STATE));
            mc.gui.setScreen(new MultiplayManagerScreen());
        }
    }

    /**
     * Upstream's scoreboard key is L, which is also vanilla's Advancements key; the scoreboard keeps
     * it while it can show, as the searchlight keeps it while riding.
     */
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (ModKeyMappings.MULTIPLAY.isActive() && ModKeyMappings.SCOREBOARD.matches(event.getKeyEvent())
                && mc.options.keyAdvancements.matches(event.getKeyEvent())) {
            while (mc.options.keyAdvancements.consumeClick()) {
                // Shown by the scoreboard instead.
            }
        }
    }

    private static boolean consumeAll(net.minecraft.client.KeyMapping key) {
        boolean clicked = false;
        while (key.consumeClick()) {
            clicked = true;
        }
        return clicked;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!drawScoreboard || mc.player == null || mc.level == null || mc.gui.hud.isHidden()
                || mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
            return;
        }
        drawList(graphics, mc, graphics.guiWidth(), graphics.guiHeight(), false);
    }

    /** Upstream {@code getScoreboradWidth}: the width of one team's column. */
    public static int columnWidth(Minecraft mc, int screenWidth) {
        int teams = mc.level.getScoreboard().getPlayerTeams().size();
        return Math.min(150, (screenWidth - 40) * 3 / 4 / (teams + 1));
    }

    /** Upstream {@code getScoreBoardLeft}: the left edge of column {@code teamIndex} of {@code teamNum}. */
    public static int columnLeft(Minecraft mc, int screenWidth, int teamNum, int teamIndex) {
        return (int) ((double) screenWidth / 2 + (columnWidth(mc, screenWidth) + 10) * (-teamNum / 2.0 + teamIndex));
    }

    /** Upstream {@code drawList}: no team first, then the teams by name. */
    public static void drawList(GuiGraphicsExtractor graphics, Minecraft mc, int screenWidth, int screenHeight,
            boolean manager) {
        List<@Nullable PlayerTeam> teams = new ArrayList<>();
        teams.add(null);
        mc.level.getScoreboard().getPlayerTeams().stream().sorted(Comparator.comparing(PlayerTeam::getName))
                .forEach(teams::add);
        for (int i = 0; i < teams.size(); i++) {
            if (manager) {
                drawPlayers(graphics, mc, screenWidth, screenHeight, teams.get(i), 1 + i, 1 + teams.size());
            } else {
                drawPlayers(graphics, mc, screenWidth, screenHeight, teams.get(i), i, teams.size());
            }
        }
    }

    /**
     * Upstream {@code drawPlayersList}: room for at least ten rows, in steps of five. Upstream also
     * capped the rows at the server's player limit, which clients no longer learn.
     */
    private static void drawPlayers(GuiGraphicsExtractor graphics, Minecraft mc, int screenWidth, int screenHeight,
            @Nullable PlayerTeam team, int teamIndex, int teamNum) {
        Font font = mc.font;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        List<PlayerInfo> players = new ArrayList<>(mc.player.connection.getOnlinePlayers());
        int maxPlayers = Math.max((players.size() / 5 + 1) * 5, 10);
        int width = columnWidth(mc, screenWidth);
        int left = columnLeft(mc, screenWidth, teamNum, teamIndex);
        int top = screenHeight / 2 - (maxPlayers * 9 + 10) / 2;
        graphics.fill(left - 1, top - 1 - 18, left + width, top + 9 * maxPlayers, Integer.MIN_VALUE);
        Component teamName = PlayerTeam.formatNameForTeam(team,
                Component.literal(team == null ? "No team" : team.getName()));
        graphics.text(font, teamName, left + width / 2 - font.width(teamName) / 2, top - 18, -1, true);
        String ff = "FriendlyFire : " + (team == null || team.isAllowFriendlyFire() ? "ON" : "OFF");
        graphics.text(font, ff, left + width / 2 - font.width(ff) / 2, top - 9, -1, true);
        int row = 0;
        for (int i = 0; i < maxPlayers; i++) {
            int y = top + row * 9;
            int rectY = top + i * 9;
            graphics.fill(left, rectY, left + width - 1, rectY + 8, ROW_BG);
            if (i >= players.size()) {
                continue;
            }
            PlayerInfo info = players.get(i);
            String name = info.getProfile().name();
            PlayerTeam playerTeam = scoreboard.getPlayersTeam(name);
            if (playerTeam == null ? team != null : team == null || !playerTeam.getName().equals(team.getName())) {
                continue;
            }
            row++;
            graphics.text(font, name, left, y, -1, true);
            if (objective != null) {
                int scoreLeft = left + font.width(name) + 5;
                int scoreRight = left + width - 12 - 5;
                if (scoreRight - scoreLeft > 5) {
                    ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), objective);
                    String value = String.valueOf(score != null ? score.value() : 0);
                    graphics.text(font, value, scoreRight - font.width(value), y, YELLOW, true);
                }
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, pingSprite(info.getLatency()), left + width - 12, y, 10,
                    8);
        }
    }

    /** Upstream {@code drawResponseTime}'s bands. */
    private static Identifier pingSprite(int latency) {
        int band = latency < 0 ? 5 : latency < 150 ? 0 : latency < 300 ? 1 : latency < 600 ? 2 : latency < 1000 ? 3 : 4;
        return PING_SPRITES[band];
    }
}
