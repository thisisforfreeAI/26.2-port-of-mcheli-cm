package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Upstream's {@code HudRWR}: a vehicle with an {@code RWR} section shows its radar warning scope
 * whenever someone rides it. Upstream's threat manager, which would fill the scope with the radars
 * and missiles around the vehicle, was never run, so the scope stays empty, as upstream.
 */
public final class RwrHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "rwr");
    private static final Identifier RWR = texture("rwr");
    private static final Identifier RWR_HELI = texture("rwr_heli");
    private static final Identifier RWR_TANK = texture("rwr_tank");
    private static final Identifier RWR_FAC = texture("rwr_fac");
    /** Upstream's layout is given for a screen 520 units high and scaled to the GUI's height. */
    private static final double SCREEN_HEIGHT = 520.0;

    private RwrHudLayer() {
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("mcheli", "textures/" + name + ".png");
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(AircraftHudLayer.ID, ID, RwrHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(mc.player);
        AircraftInfo info = ac != null ? ac.getAcInfo() : null;
        if (info == null || !info.hasRWR) {
            return;
        }
        Identifier texture = RWR;
        int size = 180;
        int centerX = 100;
        int centerY = 280;
        if (ac instanceof PlaneEntity) {
            if (info.isFloat) {
                texture = RWR_FAC;
                size = 160;
                centerX = 220;
                centerY = 370;
            }
        } else if (ac instanceof HeliEntity) {
            texture = RWR_HELI;
        } else if (ac instanceof TankEntity || ac instanceof VehicleEntity) {
            texture = ac instanceof TankEntity ? RWR_TANK : RWR_FAC;
            size = 160;
            centerX = 220;
            centerY = 370;
        }
        double height = graphics.guiHeight();
        double x = height * (centerX / SCREEN_HEIGHT);
        double y = height * (centerY / SCREEN_HEIGHT);
        double half = height * (size / SCREEN_HEIGHT) / 2.0;
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        // Upstream's HUD list held the scope twice for an RWR vehicle, so its translucent face drew twice.
        for (int i = 0; i < 2; i++) {
            canvas.texture(texture, x - half, y - half, half * 2.0, half * 2.0);
        }
    }
}
