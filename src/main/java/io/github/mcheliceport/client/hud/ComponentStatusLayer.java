package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.damage.ComponentState;
import io.github.mcheliceport.damage.ComponentView;
import io.github.mcheliceport.damage.VehicleComponents;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * M9e, a port addition: once any engine, rotor or fuel tank of the vehicle the player rides is
 * hurt, a strip at the right of the screen below the wingman hint shows each one ({@code E1 E2 R T
 * F}) in green, amber or red.
 */
public final class ComponentStatusLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "component_status");
    private static final int Y_OFFSET = 164;
    private static final int GAP = 4;

    private ComponentStatusLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(AircraftHudLayer.ID, ID, ComponentStatusLayer::render);
    }

    /** The strip's label for one component: engines are numbered when there is more than one. */
    public static String label(ComponentView view, int engines) {
        return switch (view.kind()) {
            case ENGINE -> engines > 1 ? "E" + (view.index() + 1) : "E";
            case MAIN_ROTOR -> "R";
            case TAIL_ROTOR -> "T";
            case FUEL_TANK -> "F";
            case WING -> view.index()==0?"WL":"WR";
            case AIRFRAME_TAIL -> "T";
        };
    }

    public static int color(ComponentState state) {
        return switch (state) {
            case OK -> 0xFF55FF55;
            case DAMAGED -> 0xFFFFAA00;
            case DISABLED -> 0xFFFF5555;
        };
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || mc.gui.hud.isHidden()
                || !(mc.player.getRootVehicle() instanceof AircraftEntity vehicle)) {
            return;
        }
        List<ComponentView> views = VehicleComponents.of(vehicle);
        int engines = 0;
        boolean hurt = false;
        for (ComponentView v : views) {
            if (v.kind() == io.github.mcheliceport.damage.ComponentKind.ENGINE) {
                engines++;
            }
            hurt |= v.state() != ComponentState.OK;
        }
        if (!hurt) {
            return;
        }
        Font font = mc.font;
        int total = -GAP;
        for (ComponentView v : views) {
            total += font.width(label(v, engines)) + GAP;
        }
        int x = graphics.guiWidth() - total - 4;
        int y = Y_OFFSET;
        graphics.fill(x - 2, y - 1, x + total + 2, y + font.lineHeight + 1, 0x88000000);
        for (ComponentView v : views) {
            String s = label(v, engines);
            graphics.text(font, s, x, y, color(v.state()));
            x += font.width(s) + GAP;
        }
    }
}
