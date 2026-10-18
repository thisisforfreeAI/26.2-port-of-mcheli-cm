package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.item.SpawnGunnerItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Upstream's {@code MCH_GuiSpawnGunner}: holding a gunner spawn item in first person, a square at
 * the crosshair names what the item would act on: a gunner (olive), a free seat (cyan) or a taken
 * seat (red, crossed out).
 */
public final class SpawnGunnerHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "spawn_gunner");
    private static final int GUNNER = 0xFF808000;
    private static final int FREE = 0xFF00FFFF;
    private static final int TAKEN = 0xFFFF0000;
    private static final int SIZE = 10;

    private SpawnGunnerHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(AircraftHudLayer.ID, ID, SpawnGunnerHudLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.hud.isHidden()
                || mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)
                || !mc.options.getCameraType().isFirstPerson()
                || !(player.getMainHandItem().getItem() instanceof SpawnGunnerItem)) {
            return;
        }
        Entity target = SpawnGunnerItem.findTarget(player);
        if (target == null) {
            return;
        }
        GuiHudCanvas canvas = new GuiHudCanvas(graphics);
        int px = graphics.guiWidth() / 2;
        int py = graphics.guiHeight() / 2;
        if (target instanceof GunnerEntity gunner) {
            String seatName = "";
            if (gunner.getVehicle() instanceof SeatEntity seat) {
                seatName = "(seat " + (seat.getSeatIndex() + 2) + ")";
            } else if (gunner.getVehicle() instanceof AircraftEntity) {
                seatName = "(seat 1)";
            }
            canvas.text(gunner.getTeamName() + " Gunner " + seatName, px, py + 20, GUNNER, true);
            square(canvas, px, py, GUNNER, false);
        } else if (target instanceof SeatEntity seat) {
            boolean taken = seat.getRider() != null;
            canvas.text("seat " + (seat.getSeatIndex() + 2), px, py + 20, taken ? TAKEN : FREE, true);
            square(canvas, px, py, taken ? TAKEN : FREE, taken);
        } else if (target instanceof AircraftEntity ac) {
            boolean taken = ac.getPilot() != null;
            canvas.text("seat 1", px, py + 20, taken ? TAKEN : FREE, true);
            square(canvas, px, py, taken ? TAKEN : FREE, taken);
        }
    }

    private static void square(GuiHudCanvas canvas, int px, int py, int color, boolean crossed) {
        canvas.line(new double[] {px - SIZE, py - SIZE, px + SIZE, py - SIZE, px + SIZE, py + SIZE, px - SIZE,
                py + SIZE}, color, HudCanvas.LINE_LOOP);
        if (crossed) {
            canvas.line(new double[] {px - SIZE, py - SIZE, px + SIZE, py + SIZE, px + SIZE, py - SIZE, px - SIZE,
                    py + SIZE}, color, HudCanvas.LINES);
        }
    }
}
