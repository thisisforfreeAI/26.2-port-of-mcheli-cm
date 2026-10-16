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
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import io.github.mcheliceport.entity.aircraft.AircraftWeapons;
import io.github.mcheliceport.client.weapon.WeaponClient;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.aircraft.WingedAircraftEntity;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
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
    private static final Identifier TV_NOISE = Identifier.fromNamespaceAndPath("mcheli", "textures/gui/noise.png");
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
        // MCH_GuiCommon: the hit marker for hits made outside a vehicle.
        drawHitMarker(graphics, WeaponClient.hitCount(), WeaponClient.HIT_COUNT);
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(player);
        if (ac == null || ac.isDestroyed() || ac.getAcInfo() == null) {
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
        }
        // MCH_GuiHeli: in missile camera mode the TV missile's noise replaces the key hints.
        if (showHud) {
            if (!ac.isMissileCameraMode(player)) {
                this.drawKeyBind(graphics, ac, player, seatId);
            } else if (ac.getTVMissile() != null) {
                this.drawTvMissileNoise(graphics);
            }
        }
        drawHitMarker(graphics, ac.getHitStatus(), AircraftWeapons.MAX_HIT_STATUS);
        drawCcip(graphics, ac, player);
    }

    /**
     * Upstream {@code MCH_RenderCCIP}: a ring with ticks and a centre cross at the CCIP weapon's
     * predicted impact point, red when something is within 3 blocks of it, turned with the
     * vehicle's roll. Upstream drew it in the world with depth testing off at a constant screen
     * size; here it is projected onto the HUD, which looks the same.
     */
    private static void drawCcip(GuiGraphicsExtractor graphics, AircraftEntity ac, LocalPlayer player) {
        WeaponSet ws = ac.getCurrentWeapon(player);
        WeaponInfo wi = ws.getInfo();
        if (wi == null || !wi.ccip || !WeaponInfo.isCCIPSupportedType(wi.type)) {
            return;
        }
        Vec3 impact = ac.weapons().getPredictedImpactPoint(player);
        if (impact == null) {
            return;
        }
        CameraRenderState camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState
                .cameraRenderState;
        Vector4f p = new Vector4f((float) (impact.x - camera.pos.x), (float) (impact.y + 0.2 - camera.pos.y),
                (float) (impact.z - camera.pos.z), 1.0F);
        if (p.x * p.x + p.y * p.y + p.z * p.z < 0.25F) {
            return;
        }
        camera.viewRotationMatrix.transform(p);
        camera.projectionMatrix.transform(p);
        if (p.w <= 0.0F) {
            return;
        }
        double x = (p.x / p.w + 1.0) * 0.5 * graphics.guiWidth();
        double y = (1.0 - p.y / p.w) * 0.5 * graphics.guiHeight();
        float radius = 14.0F * Mth.clamp(wi.ccipFactor, 0.1F, 10.0F);
        AABB around = new AABB(impact, impact).inflate(3.0);
        boolean hot = !ac.level().getEntities(ac, around, e -> e != player && e.isAlive()).isEmpty();
        int color = 0xE6000000 | (hot ? 0xFF1A1A : 0x1AFF1A);
        double[] ring = new double[48];
        for (int i = 0; i < 24; i++) {
            double ang = Math.PI * 2.0 * i / 24.0;
            ring[i * 2] = x + Math.cos(ang) * radius;
            ring[i * 2 + 1] = y + Math.sin(ang) * radius;
        }
        double inner = radius * 0.55;
        double cross = radius * 0.30;
        double[] ticks = {
                x + inner, y, x + radius, y, x - inner, y, x - radius, y,
                x, y + inner, x, y + radius, x, y - inner, x, y - radius,
                x - cross, y, x + cross, y, x, y - cross, x, y + cross};
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        canvas.pushRotation(x, y, ac.getRoll());
        canvas.line(ring, color, HudCanvas.LINE_LOOP);
        canvas.line(ticks, color, HudCanvas.LINES);
        canvas.popRotation();
    }

    /**
     * Upstream {@code drawHitMarker}: four diagonal strokes around the centre, fading as the hit
     * count runs down, in the {@code hitMarkColor} config colour.
     */
    private static void drawHitMarker(GuiGraphicsExtractor graphics, int hitStrength, int maxHitStrength) {
        if (hitStrength <= 0) {
            return;
        }
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        double[] lines = {
                cx - 10, cy - 10, cx - 5, cy - 5,
                cx - 10, cy + 10, cx - 5, cy + 5,
                cx + 10, cy - 10, cx + 5, cy - 5,
                cx + 10, cy + 10, cx + 5, cy + 5};
        int config = ClientConfig.hitMarkColor();
        float alphaFactor = (config >>> 24) / 255.0F;
        int alpha = Math.min(hitStrength * (256 / maxHitStrength), 255);
        int color = (int) (alphaFactor * alpha) << 24 | config & 0xFFFFFF;
        new GuiHudCanvas(graphics).line(lines, color, HudCanvas.LINES);
    }

    /** Upstream {@code drawTvMissileNoise}: grey noise added over the missile's view. */
    private void drawTvMissileNoise(GuiGraphicsExtractor graphics) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(TV_NOISE);
        double u = this.rand.nextInt(256) / 256.0;
        double v = this.rand.nextInt(256) / 256.0;
        GuiHudCanvas.texturedQuad(new GuiHudCanvas(graphics), texture, GUI_TEXTURED_ADDITIVE,
                0x66808080, 0, 0, graphics.guiWidth(), graphics.guiHeight(), u, v, u + 1.0, v + 1.0, 0.0F);
    }

    /** Upstream {@code drawHud}. */
    private void drawHud(GuiGraphicsExtractor graphics, AircraftEntity ac, LocalPlayer player, int seatId,
            float partialTicks) {
        AircraftInfo info = ac.getAcInfo();
        if (info == null || seatId < 0 || seatId >= info.hudList.size()) {
            return;
        }
        // Upstream: a TV missile's operator sees the missile HUD instead.
        String hudName = ac.isMissileCameraMode(player) && ac.getTVMissile() != null && info.hudTvMissile != null
                ? info.hudTvMissile : info.hudList.get(seatId);
        Hud hud = ContentRegistries.hud().get(hudName);
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
    private void drawKeyBind(GuiGraphicsExtractor graphics, AircraftEntity aircraft, LocalPlayer player, int seatId) {
        AircraftInfo info = aircraft.getAcInfo();
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
        boolean gunnerMode = aircraft.isGunnerMode(player);

        // drawAircraftKeyBinds
        if (aircraft.getSeatNum() > 0) {
            // Upstream passed the character 'Ｐ' as the pilot's colour: 0xFF30 without alpha, drawn opaque.
            int seatColor = seatId == 0 ? GuiHudCanvas.textColor('Ｐ') : ACTIVE;
            String prefix = keyName(ModKeyMappings.FREE_LOOK) + " + ";
            this.drawRight(graphics, "NextSeat : " + prefix + keyName(ModKeyMappings.GUI), rightX, seatColor);
            this.drawRight(graphics, "PrevSeat : " + prefix + keyName(ModKeyMappings.EXTRA), rightX, seatColor);
        }
        this.drawLeft(graphics, "NPC Gunner %s : %s + %s".formatted(aircraft.getGunnerStatus() ? "ON" : "OFF",
                keyName(ModKeyMappings.FREE_LOOK), keyName(ModKeyMappings.CAMERA_MODE)), leftX, ACTIVE);
        if (seatId >= 0 && seatId <= 1 && aircraft.haveFlare()) {
            this.drawRightKey(graphics, "Flare", ModKeyMappings.FLARE, rightX,
                    aircraft.isFlarePreparation() ? INACTIVE : ACTIVE);
        }
        if (seatId == 0 && info.haveLandingGear()) {
            if (aircraft.canFoldLandingGear()) {
                this.drawRightKey(graphics, "Gear Up", ModKeyMappings.GEAR_UP_DOWN, rightX, ACTIVE);
            } else if (aircraft.canUnfoldLandingGear()) {
                this.drawRightKey(graphics, "Gear Down", ModKeyMappings.GEAR_UP_DOWN, rightX, ACTIVE);
            }
        }
        if (aircraft.getWeaponNum() > 1) {
            this.drawLeftKey(graphics, "Weapon", ModKeyMappings.SWITCH_WEAPON_2, leftX, ACTIVE);
        }
        if (aircraft.getCurrentWeapon(player).getCurrentWeapon().numMode > 0) {
            this.drawLeftKey(graphics, "WeaponMode", ModKeyMappings.SWITCH_WEAPON_MODE, leftX, ACTIVE);
        }
        if (aircraft.canSwitchCameraMode(seatId)) {
            this.drawLeftKey(graphics, "CameraMode", ModKeyMappings.CAMERA_MODE, leftX, ACTIVE);
        }
        if (seatId == 0) {
            this.drawLeftKey(graphics, "Dismount", ModKeyMappings.UNMOUNT, leftX, ACTIVE);
        }
        boolean canFreeLook = !(aircraft instanceof io.github.mcheliceport.entity.ground.GroundVehicleEntity)
                && (seatId == 0 || seatId > 0 && aircraft.canSwitchGunnerModeOtherSeat(player));
        if (canFreeLook) {
            this.drawLeftKey(graphics, "FreeLook", ModKeyMappings.FREE_LOOK, leftX, ACTIVE);
        }

        // MCH_GuiHeli#drawKeyBind
        if (seatId == 0 && aircraft instanceof io.github.mcheliceport.entity.tank.TankEntity) {
            this.drawRightKey(graphics, "Brake", ModKeyMappings.SWITCH_HOVERING, rightX, ACTIVE);
        }
        if (seatId == 0 && aircraft instanceof io.github.mcheliceport.entity.ground.GroundVehicleEntity && aircraft.canSwitchCameraPos()) {
            this.drawRightKey(graphics, "Camera", ModKeyMappings.SWITCH_GUNNER, rightX, ACTIVE);
        }
        if (seatId == 0 && !freeLookPressed && aircraft instanceof WingedAircraftEntity && aircraft.canSwitchCameraPos()) {
            this.drawRightKey(graphics, "Camera / Gunner", ModKeyMappings.SWITCH_GUNNER, rightX, ACTIVE);
        } else if (seatId == 0 && info.isEnableGunnerMode && !freeLookPressed) {
            int color = aircraft.isHoveringMode() ? INACTIVE : ACTIVE;
            this.drawRightKey(graphics, gunnerMode ? "Normal" : "Gunner", ModKeyMappings.SWITCH_GUNNER, rightX, color);
        }
        if (seatId > 0 && aircraft.canSwitchGunnerModeOtherSeat(player)) {
            this.drawRightKey(graphics, gunnerMode ? "Normal" : "Camera", ModKeyMappings.SWITCH_GUNNER, rightX, ACTIVE);
        }
        if (seatId == 0 && !freeLookPressed && aircraft instanceof HeliEntity) {
            int color = gunnerMode ? INACTIVE : ACTIVE;
            this.drawRightKey(graphics, gunnerMode ? "Normal" : "Hovering", ModKeyMappings.SWITCH_HOVERING, rightX,
                    color);
        }
        if (seatId == 0 && info instanceof HeliInfo hi && hi.isEnableFoldBlade && aircraft.blockWithin(1, -2) && aircraft.getCurrentThrottle() <= 0.01) {
            this.drawRightKey(graphics, "FoldBlade", ModKeyMappings.EXTRA, rightX, ACTIVE);
        }
        if (aircraft instanceof WingedAircraftEntity && aircraft.canEjectSeat(player)) {
            this.drawRightKey(graphics, "Eject seat", ModKeyMappings.SWITCH_HOVERING, rightX, ACTIVE);
        }
        if (seatId > 0) {
            this.drawLeftKey(graphics, aircraft.ropes().active() ? (aircraft.ropes().ready() ? "Rappel" : "Rope deploying")
                            : aircraft.canParachute(player) ? "Parachute" : "Dismount",
                    ModKeyMappings.UNMOUNT, leftX, ACTIVE);
        }
        if (seatId == 0 && aircraft instanceof WingedAircraftEntity plane) {
            if (plane.getWingedInfo().isEnableVtol) {
                this.drawRightKey(graphics, "VTOL", ModKeyMappings.EXTRA, rightX, plane.canSwitchVtol() ? ACTIVE : INACTIVE);
            }
            if (plane.partWing != null) {
                this.drawRightKey(graphics, "Fold/Sweep Wing", ModKeyMappings.ZOOM, rightX,
                        plane.canFoldWing() || plane.canUnfoldWing() ? ACTIVE : INACTIVE);
            }
        }
        if ((gunnerMode || aircraft.isUAV()) && info.cameraZoom > 1) {
            this.drawLeftKey(graphics, "Zoom", ModKeyMappings.ZOOM, leftX, ACTIVE);
        } else if (seatId == 0 && (aircraft.canUnfoldHatch() || aircraft.canFoldHatch())) {
            this.drawLeftKey(graphics, "OpenHatch", ModKeyMappings.ZOOM, leftX, ACTIVE);
        }
        if (seatId == 0) {
            if (aircraft.ropes().supported()) {
                this.drawRightKey(graphics, aircraft.ropes().active() ? "Retract ropes" : "Deploy ropes", ModKeyMappings.ROPES,
                        rightX, aircraft.ropes().active() || aircraft.ropes().canStart() ? ACTIVE : INACTIVE);
            }
            if (aircraft.racks().canRide()) { this.drawRightKey(graphics, "Board carrier", ModKeyMappings.PUT_TO_RACK, rightX, ACTIVE); }
            else if (aircraft.racks().canLoad()) { this.drawRightKey(graphics, "Load rack", ModKeyMappings.PUT_TO_RACK, rightX, ACTIVE); }
            if (aircraft.isPassenger() || aircraft.racks().canUnload()) {
                this.drawRightKey(graphics, "Unload / Leave rack", ModKeyMappings.DOWN_FROM_RACK, rightX, ACTIVE);
            }
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
        x = Math.max(0, Math.min(x, graphics.guiWidth() - Minecraft.getInstance().font.width(text) - 10));
        graphics.text(Minecraft.getInstance().font, text, x, this.currentRightY, GuiHudCanvas.textColor(color));
        this.currentRightY += 10;
    }
}
