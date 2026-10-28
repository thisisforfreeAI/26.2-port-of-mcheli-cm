package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.groundcrew.GroundCrew;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * M10c, a port addition: while the vehicle the player rides is being repaired, rearmed, refuelled
 * or restocked, a line at the right of the screen below the component strip says so. A stocked
 * supply vehicle's riders also see its stock.
 */
public final class GroundCrewHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "ground_crew");
    private static final int Y_OFFSET = 178;

    private GroundCrewHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(AircraftHudLayer.ID, ID, GroundCrewHudLayer::render);
    }

    /** The words for a set of service flags, in a fixed order. */
    public static List<String> words(int flags) {
        List<String> words = new ArrayList<>();
        if ((flags & GroundCrew.REPAIR) != 0) {
            words.add("REPAIR");
        }
        if ((flags & GroundCrew.REARM) != 0) {
            words.add("REARM");
        }
        if ((flags & GroundCrew.REFUEL) != 0) {
            words.add("REFUEL");
        }
        if ((flags & GroundCrew.RESTOCK) != 0) {
            words.add("RESTOCK");
        }
        return words;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
                || !(mc.player.getRootVehicle() instanceof AircraftEntity vehicle)) {
            return;
        }
        AircraftInfo info = vehicle.getAcInfo();
        List<String> lines = new ArrayList<>();
        List<String> words = words(vehicle.getServiceFlags());
        if (!words.isEmpty()) {
            lines.add(String.join(" ", words));
        }
        if (info != null && info.ammoSupplyStock > 0) {
            lines.add("STOCK " + vehicle.getAmmoStock() + "/" + info.ammoSupplyStock);
        }
        Font font = mc.font;
        int y = Y_OFFSET;
        for (String line : lines) {
            int x = graphics.guiWidth() - font.width(line) - 4;
            graphics.fill(x - 2, y - 1, x + font.width(line) + 2, y + font.lineHeight + 1, 0x88000000);
            graphics.text(font, line, x, y, 0xFF55FF55);
            y += font.lineHeight + 3;
        }
    }
}
