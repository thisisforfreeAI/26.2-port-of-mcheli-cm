package io.github.mcheliceport.client.hud;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The helicopter HUD, drawn above vanilla's: upstream's {@code MCH_GuiHeli} and
 * {@code MCH_AircraftCommonGui}. Draws the night-vision noise in night vision, the seat's HUD
 * (the pilot in gunner mode uses the second HUD), and the key hints; vanilla's crosshair, hotbar,
 * health, armour, food, air and boss bars are hidden while riding, as upstream's
 * {@code MCH_CameraManager#onRenderOverlay}.
 *
 * <p>As upstream, nothing is drawn while a screen other than the chat is open, and the HUD and
 * key hints only show in first person unless {@code hud.displayInThirdPerson} is on.
 */
public final class AircraftHudLayer implements GuiLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "aircraft_hud");
    /** Upstream's night-vision noise: {@code blendFunc(GL_ONE, GL_ONE)} over the scene. */
    public static final RenderPipeline GUI_TEXTURED_ADDITIVE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "pipeline/gui_textured_additive"))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .build();
    private static final Identifier NOISE = Identifier.fromNamespaceAndPath("mcheli", "textures/gui/alpha.png");
    private static final Set<Identifier> HIDDEN_WHILE_RIDING = Set.of(VanillaGuiLayers.CROSSHAIR,
            VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.BOSS_OVERLAY);
    private static final int ACTIVE = -1342177281;
    private static final int INACTIVE = -1349546097;

    private static final HudInterpreter INTERPRETER = new HudInterpreter(name -> ContentRegistries.hud().get(name));
    private static final HudFrame FRAME = new HudFrame();
    private final Random rand = new Random();
    private int currentLeftY;
    private int currentRightY;

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ID, new AircraftHudLayer());
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GUI_TEXTURED_ADDITIVE);
    }

    /** Game bus: hides vanilla's crosshair and status bars while riding a vehicle. */
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (HIDDEN_WHILE_RIDING.contains(event.getName())
                && AircraftClientEvents.riddenAircraft(Minecraft.getInstance().player) != null) {
            event.setCanceled(true);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.hud.isHidden()
                || mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(player);
        if (!(ac instanceof HeliEntity heli) || ac.isDestroyed() || ac.getAcInfo() == null) {
            return;
        }
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        boolean thirdPerson = !mc.options.getCameraType().isFirstPerson();
        boolean showHud = !thirdPerson || ClientConfig.get(ClientConfig.HUD_IN_THIRD_PERSON);
        int seatId = ac.getSeatIdByEntity(player);
        if (ac.getCameraMode(player) == AircraftCamera.MODE_NIGHTVISION) {
            this.drawNightVisionNoise(graphics);
        }
        if (showHud) {
            this.drawHud(graphics, ac, player, seatId == 0 && ac.isGunnerMode(player) ? 1 : seatId, partialTicks);
            this.drawKeyBind(graphics, heli, player, seatId);
        }
    }

    /** Upstream {@code drawHud}. */
    private void drawHud(GuiGraphicsExtractor graphics, AircraftEntity ac, LocalPlayer player, int seatId,
            float partialTicks) {
        AircraftInfo info = ac.getAcInfo();
        if (info == null || seatId < 0 || seatId >= info.hudList.size()) {
            return;
        }
        Hud hud = ContentRegistries.hud().get(info.hudList.get(seatId));
        if (hud == null) {
            return;
        }
        FRAME.width = graphics.guiWidth();
        FRAME.height = graphics.guiHeight();
        FRAME.autoScale = ClientConfig.get(ClientConfig.HUD_AUTO_SCALE);
        HudVariables.fill(FRAME, ac, player, partialTicks);
        INTERPRETER.draw(hud, FRAME, new GuiHudCanvas(graphics));
    }

    /** Upstream {@code drawNightVisionNoise}: green noise added over the view, moving every frame. */
    private void drawNightVisionNoise(GuiGraphicsExtractor graphics) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(NOISE);
        double u = this.rand.nextInt(256) / 256.0;
        double v = this.rand.nextInt(256) / 256.0;
        GuiHudCanvas.texturedQuad(new GuiHudCanvas(graphics), texture, GUI_TEXTURED_ADDITIVE,
                0x4C00FF00, 0, 0, graphics.guiWidth(), graphics.guiHeight(), u, v, u + 1.0, v + 1.0, 0.0F);
    }

    /** Upstream {@code MCH_GuiHeli#drawKeyBind} and {@code drawAircraftKeyBinds}, for the parts ported so far. */
    private void drawKeyBind(GuiGraphicsExtractor graphics, HeliEntity heli, LocalPlayer player, int seatId) {
        HeliInfo info = heli.getHeliInfo();
        if (ClientConfig.get(ClientConfig.HIDE_KEY_HINTS) || info == null) {
            return;
        }
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int screenWidth = centerX * 2;
        int leftX = Math.max(10, (int) (screenWidth * 0.05));
        int rightX = Math.min(screenWidth - 100, (int) (screenWidth * 0.80));
        this.currentLeftY = centerY - 80;
        this.currentRightY = centerY - 80;
        boolean freeLookPressed = ModKeyMappings.isFreeLookHeld();
        boolean gunnerMode = heli.isGunnerMode(player);

        // drawAircraftKeyBinds
        if (seatId > 0 && heli.getSeatNum() > 1 || freeLookPressed) {
            // Upstream passed the character 'Ｐ' as the pilot's colour: 0xFF30 without alpha, drawn opaque.
            int seatColor = seatId == 0 ? GuiHudCanvas.textColor('Ｐ') : ACTIVE;
            String prefix = seatId == 0 ? keyName(ModKeyMappings.FREE_LOOK) + " + " : "";
            this.drawRight(graphics, "NextSeat : " + prefix + keyName(ModKeyMappings.GUI), rightX, seatColor);
            this.drawRight(graphics, "PrevSeat : " + prefix + keyName(ModKeyMappings.EXTRA), rightX, seatColor);
        }
        this.drawLeft(graphics, "Gunner %s : %s + %s".formatted(heli.getGunnerStatus() ? "ON" : "OFF",
                keyName(ModKeyMappings.FREE_LOOK), keyName(ModKeyMappings.CAMERA_MODE)), leftX, ACTIVE);
        if (seatId == 0 && info.haveLandingGear()) {
            if (heli.canFoldLandingGear()) {
                this.drawRightKey(graphics, "Gear Up", ModKeyMappings.GEAR_UP_DOWN, rightX, ACTIVE);
            } else if (heli.canUnfoldLandingGear()) {
                this.drawRightKey(graphics, "Gear Down", ModKeyMappings.GEAR_UP_DOWN, rightX, ACTIVE);
            }
        }
        if (heli.canSwitchCameraMode(seatId)) {
            this.drawLeftKey(graphics, "CameraMode", ModKeyMappings.CAMERA_MODE, leftX, ACTIVE);
        }
        if (seatId == 0 && heli.getSeatNum() >= 1) {
            boolean parachuting = info.isEnableParachuting && !heli.blockWithin(3, -10);
            this.drawLeftKey(graphics, parachuting ? "Parachuting" : "Dismount", ModKeyMappings.UNMOUNT, leftX, ACTIVE);
        }
        boolean canFreeLook = seatId == 0 || seatId > 0 && heli.canSwitchGunnerModeOtherSeat(player);
        if (canFreeLook) {
            this.drawLeftKey(graphics, "FreeLook", ModKeyMappings.FREE_LOOK, leftX, ACTIVE);
        }

        // MCH_GuiHeli#drawKeyBind
        if (seatId == 0 && info.isEnableGunnerMode && !freeLookPressed) {
            int color = heli.isHoveringMode() ? INACTIVE : ACTIVE;
            this.drawRightKey(graphics, gunnerMode ? "Normal" : "Gunner", ModKeyMappings.SWITCH_GUNNER, rightX, color);
        }
        if (seatId > 0 && heli.canSwitchGunnerModeOtherSeat(player)) {
            this.drawRightKey(graphics, gunnerMode ? "Normal" : "Camera", ModKeyMappings.SWITCH_GUNNER, rightX, ACTIVE);
        }
        if (seatId == 0 && !freeLookPressed) {
            int color = gunnerMode ? INACTIVE : ACTIVE;
            this.drawRightKey(graphics, gunnerMode ? "Normal" : "Hovering", ModKeyMappings.SWITCH_HOVERING, rightX,
                    color);
        }
        if (seatId == 0 && info.isEnableFoldBlade && heli.blockWithin(1, -2) && heli.getCurrentThrottle() <= 0.01) {
            this.drawRightKey(graphics, "FoldBlade", ModKeyMappings.EXTRA, rightX, ACTIVE);
        }
        if ((gunnerMode || heli.isUAV()) && info.cameraZoom > 1) {
            this.drawLeftKey(graphics, "Zoom", ModKeyMappings.ZOOM, leftX, ACTIVE);
        } else if (seatId == 0 && (heli.canUnfoldHatch() || heli.canFoldHatch())) {
            this.drawLeftKey(graphics, "OpenHatch", ModKeyMappings.ZOOM, leftX, ACTIVE);
        }
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private void drawLeftKey(GuiGraphicsExtractor graphics, String label, KeyMapping key, int x, int color) {
        this.drawLeft(graphics, label + " : " + keyName(key), x, color);
    }

    private void drawRightKey(GuiGraphicsExtractor graphics, String label, KeyMapping key, int x, int color) {
        this.drawRight(graphics, label + " : " + keyName(key), x, color);
    }

    private void drawLeft(GuiGraphicsExtractor graphics, String text, int x, int color) {
        graphics.text(Minecraft.getInstance().font, text, x, this.currentLeftY, GuiHudCanvas.textColor(color));
        this.currentLeftY += 10;
    }

    private void drawRight(GuiGraphicsExtractor graphics, String text, int x, int color) {
        graphics.text(Minecraft.getInstance().font, text, x, this.currentRightY, GuiHudCanvas.textColor(color));
        this.currentRightY += 10;
    }
}
