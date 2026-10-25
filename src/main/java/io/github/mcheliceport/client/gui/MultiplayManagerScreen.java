package io.github.mcheliceport.client.gui;

import io.github.mcheliceport.client.hud.TeamListOverlay;
import io.github.mcheliceport.multiplay.MultiplayManager;
import io.github.mcheliceport.net.MultiplayActionPayload;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's multiplay manager ({@code MCH_GuiScoreboard} with its {@code _Main} and
 * {@code _CreateTeam} screens): the team lists, with buttons that toggle PVP, shuffle the players
 * over the teams, send everyone to their spawn point, remove every vehicle and create a team. The
 * server checks each button's permission, as upstream's did. Under the lists, a port addition: a
 * Join button per team and a Leave button under the players in no team, when the server lets this
 * player choose a team.
 */
public class MultiplayManagerScreen extends Screen {

    private static final Identifier NEW_TEAM = Identifier.fromNamespaceAndPath("mcheli", "textures/gui/mp_new_team.png");
    /** Upstream {@code colorNames}: no color, then the team colors but white. */
    private static final @Nullable TeamColor[] COLORS = {null, TeamColor.BLACK, TeamColor.DARK_BLUE,
            TeamColor.DARK_GREEN, TeamColor.DARK_AQUA, TeamColor.DARK_RED, TeamColor.DARK_PURPLE, TeamColor.GOLD,
            TeamColor.GRAY, TeamColor.DARK_GRAY, TeamColor.BLUE, TeamColor.GREEN, TeamColor.AQUA, TeamColor.RED,
            TeamColor.LIGHT_PURPLE, TeamColor.YELLOW};
    /** Upstream's 16 characters, in the characters {@code /team add} reads as one word. */
    private static final Pattern TEAM_NAME = Pattern.compile("[0-9A-Za-z_\\-.+]{1,16}");

    /** The bottom of the action buttons' column. */
    private static final int ACTIONS_BOTTOM = 180;
    /** Upstream kept the friendly fire choice for the next team. */
    private static boolean friendlyFire = true;

    private boolean createTeam;
    private int teamColor;
    /** What the layout was built for: the teams, the list rows, the team buttons and this player's team. */
    private String lastLayout = "";
    /** The screen to switch to on the next tick: -1 none, 0 main, 1 create team. */
    private int pendingSwitch = -1;
    private String teamName = "";
    private @Nullable Button pvpButton;
    private @Nullable Button okButton;
    private @Nullable Button friendlyFireButton;
    private @Nullable EditBox nameField;

    public MultiplayManagerScreen() {
        super(Component.literal("Multiplay manager"));
    }

    @Override
    protected void init() {
        this.lastLayout = this.layout();
        this.pvpButton = null;
        this.okButton = null;
        this.friendlyFireButton = null;
        this.nameField = null;
        if (this.createTeam) {
            this.initCreateTeam();
        } else {
            this.initMain();
        }
        this.updateButtons();
    }

    private int teamNum() {
        return this.minecraft.level != null ? this.minecraft.level.getScoreboard().getPlayerTeams().size() : 0;
    }

    private String layout() {
        if (this.minecraft.level == null || this.minecraft.player == null) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        this.minecraft.level.getScoreboard().getPlayerTeams().forEach(t -> key.append(t.getName()).append('/'));
        PlayerTeam own = this.ownTeam();
        return key.append(TeamListOverlay.rows(this.minecraft)).append('/').append(MultiplayManager.clientChooseTeam())
                .append('/').append(own == null ? "" : own.getName()).toString();
    }

    private @Nullable PlayerTeam ownTeam() {
        return this.minecraft.level.getScoreboard().getPlayersTeam(this.minecraft.player.getScoreboardName());
    }

    /** Upstream {@code MCH_GuiScoreboard_Main.initGui}: the buttons in the column left of the lists. */
    private void initMain() {
        int width = Math.max(TeamListOverlay.columnWidth(this.minecraft, this.width) * 3 / 4, 80);
        int left = TeamListOverlay.columnLeft(this.minecraft, this.width, this.teamNum() + 1, 0) / 4;
        this.pvpButton = this.addRenderableWidget(Button.builder(Component.empty(),
                b -> this.send(MultiplayActionPayload.Action.SET_PVP)).bounds(left, 80, width, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Team shuffle"),
                        b -> this.send(MultiplayActionPayload.Action.SHUFFLE_TEAM))
                .bounds(left, 100, width, 20).tooltip(Tooltip.create(Component.literal("Shuffle all players.")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("New team"), b -> this.switchScreen(true))
                .bounds(left, 120, width, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Jump spawn pos"),
                        b -> this.send(MultiplayActionPayload.Action.JUMP_SPAWNPOINT))
                .bounds(left, 140, width, 20)
                .tooltip(Tooltip.create(Component.literal("Teleport all players -> spawn point."))).build());
        this.addRenderableWidget(Button.builder(Component.literal("Destroy All"),
                        b -> this.send(MultiplayActionPayload.Action.DESTROY_AIRCRAFT))
                .bounds(left, 160, width, 20)
                .tooltip(Tooltip.create(Component.literal("Destroy all aircraft and vehicle."))).build());
        if (MultiplayManager.clientChooseTeam() && this.minecraft.level != null && this.minecraft.player != null) {
            this.initTeamButtons();
        }
    }

    /** The Join and Leave buttons, one under each list. */
    private void initTeamButtons() {
        List<@Nullable PlayerTeam> columns = TeamListOverlay.columns(this.minecraft);
        int width = TeamListOverlay.columnWidth(this.minecraft, this.width);
        // Below the lists, and below the action buttons, which the no-team list reaches under.
        int y = Math.max(TeamListOverlay.listBottom(this.minecraft, this.height) + 4, ACTIONS_BOTTOM + 4);
        PlayerTeam own = this.ownTeam();
        for (int i = 0; i < columns.size(); i++) {
            PlayerTeam team = columns.get(i);
            int left = TeamListOverlay.columnLeft(this.minecraft, this.width, 1 + columns.size(), 1 + i);
            Button button;
            if (team == null) {
                button = Button.builder(Component.literal("Leave team"),
                        b -> ClientPacketDistributor.sendToServer(
                                new MultiplayActionPayload(MultiplayActionPayload.Action.LEAVE_TEAM)))
                        .bounds(left - 1, y, width + 1, 20).build();
                button.active = own != null;
            } else {
                boolean joined = own != null && own.getName().equals(team.getName());
                String name = team.getName();
                button = Button.builder(Component.literal(joined ? "Joined" : "Join"),
                        b -> ClientPacketDistributor.sendToServer(
                                new MultiplayActionPayload(MultiplayActionPayload.Action.JOIN_TEAM, name)))
                        .bounds(left - 1, y, width + 1, 20).build();
                button.active = !joined && name.length() <= MultiplayActionPayload.MAX_TEAM_NAME;
            }
            this.addRenderableWidget(button);
        }
    }

    /** Upstream {@code MCH_GuiScoreboard_CreateTeam.initGui}, around the middle of the screen. */
    private void initCreateTeam() {
        int x = this.width / 2;
        int y = this.height / 2;
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.cycleColor(1))
                .bounds(x + 40, y - 20, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.cycleColor(-1))
                .bounds(x - 80, y - 20, 40, 20).build());
        this.friendlyFireButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
            friendlyFire = !friendlyFire;
            this.updateButtons();
        }).bounds(x - 80, y + 20, 160, 20).build());
        this.okButton = this.addRenderableWidget(Button.builder(Component.literal("OK"), b -> this.createTeam())
                .bounds(x - 80, y + 60, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.switchScreen(false))
                .bounds(x, y + 60, 80, 20).build());
        EditBox field = new EditBox(this.font, x - 80, y - 55, 160, 20, Component.literal("Team name"));
        field.setMaxLength(16);
        field.setTextColor(-1);
        field.setValue(this.teamName);
        field.setResponder(value -> {
            this.teamName = value;
            this.updateButtons();
        });
        this.nameField = this.addRenderableWidget(field);
    }

    /** Upstream focused the name field. */
    @Override
    protected void setInitialFocus() {
        if (this.nameField != null) {
            this.setInitialFocus(this.nameField);
        } else {
            super.setInitialFocus();
        }
    }

    /**
     * Switches on the next tick: a button that rebuilt the screen while its click is handled would
     * take the focus from the name field afterwards.
     */
    private void switchScreen(boolean toCreateTeam) {
        this.pendingSwitch = toCreateTeam ? 1 : 0;
    }

    private void cycleColor(int step) {
        this.teamColor = Math.floorMod(this.teamColor + step, COLORS.length);
    }

    private void send(MultiplayActionPayload.Action action) {
        ClientPacketDistributor.sendToServer(new MultiplayActionPayload(action));
    }

    /** Upstream sent the three team commands as the player's own. */
    private void createTeam() {
        String name = this.teamName;
        if (TEAM_NAME.matcher(name).matches() && this.minecraft.player != null) {
            TeamColor color = COLORS[this.teamColor];
            this.minecraft.player.connection.sendCommand("team add " + name);
            this.minecraft.player.connection.sendCommand(
                    "team modify " + name + " color " + (color == null ? "reset" : color.getSerializedName()));
            this.minecraft.player.connection.sendCommand("team modify " + name + " friendlyFire " + friendlyFire);
        }
        this.switchScreen(false);
    }

    private void updateButtons() {
        if (this.pvpButton != null) {
            this.pvpButton.setMessage(Component.literal("PVP : " + (MultiplayManager.clientPvp() ? "ON" : "OFF")));
        }
        if (this.okButton != null) {
            this.okButton.active = TEAM_NAME.matcher(this.teamName).matches();
        }
        if (this.friendlyFireButton != null) {
            this.friendlyFireButton.setMessage(Component.literal("Friendly Fire : " + (friendlyFire ? "ON" : "OFF")));
        }
    }

    /**
     * Upstream {@code updateScreen}: a new or removed team lays the screen out again, as do more
     * list rows, the server's answer on the team buttons and a change of this player's team.
     */
    @Override
    public void tick() {
        if (this.pendingSwitch >= 0) {
            this.createTeam = this.pendingSwitch == 1;
            this.pendingSwitch = -1;
            // Upstream's acviveScreen: the name field starts empty each time.
            this.teamName = "";
            this.rebuildWidgets();
        } else if (!this.layout().equals(this.lastLayout)) {
            this.rebuildWidgets();
        }
        this.updateButtons();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.createTeam && event.isEscape()) {
            this.switchScreen(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Upstream drew no darkened background, only the lists (and the new team panel). */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.minecraft.level == null || this.minecraft.player == null) {
            return;
        }
        TeamListOverlay.drawList(graphics, this.minecraft, this.width, this.height, true);
        if (!this.createTeam) {
            return;
        }
        graphics.nextStratum();
        int px = (this.width - 222) / 2;
        int py = (this.height - 200) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, NEW_TEAM, px, py, 0.0F, 0.0F, 222, 200, 256, 256);
        int x = this.width / 2;
        int y = this.height / 2;
        graphics.centeredText(this.font, "Create team", x, y - 85, -1);
        graphics.centeredText(this.font, "Team name", x, y - 70, -1);
        TeamColor color = COLORS[this.teamColor];
        int rgb = color == null ? 0xFFFFFF : color.rgb();
        graphics.centeredText(this.font,
                Component.literal("Team Color").withStyle(s -> s.withColor(TextColor.fromRgb(rgb))), x, y - 13, -1);
    }
}
