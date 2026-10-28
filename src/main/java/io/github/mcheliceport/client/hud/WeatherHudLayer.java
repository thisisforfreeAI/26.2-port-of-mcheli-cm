package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.AircraftClientEvents;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.weather.FlightWeather;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * M10e, a port addition: a line at the right, under the ground crew line, naming the weather that
 * changes the ridden vehicle's flight or sensors: {@code GUST}, {@code STORM}, {@code ICE n%},
 * {@code NIGHT} and {@code RADAR OFF} while lightning has blanked its radar.
 */
public final class WeatherHudLayer {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "weather");
    private static final int Y_OFFSET = 204;

    private WeatherHudLayer() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(AircraftHudLayer.ID, ID, WeatherHudLayer::render);
    }

    /** The words for the vehicle's weather, in a fixed order. */
    public static List<String> words(FlightWeather.Conditions c, boolean gusting, float ice, int radarBlankTicks) {
        List<String> words = new ArrayList<>();
        if (gusting) {
            words.add(c.thunder() ? "STORM" : "GUST");
        }
        if (ice >= 0.05F) {
            words.add("ICE " + Math.round(ice * 100.0F) + "%");
        }
        if (c.night()) {
            words.add("NIGHT");
        }
        if (radarBlankTicks > 0) {
            words.add("RADAR OFF");
        }
        return words;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) {
            return;
        }
        AircraftEntity ac = AircraftClientEvents.riddenAircraft(mc.player);
        if (ac == null || !FlightWeather.enabled()) {
            return;
        }
        FlightWeather.Conditions c = FlightWeather.at(mc.level, ac.position());
        List<String> words = words(c, FlightWeather.gust(ac) != Vec3.ZERO, ac.getIce(), ac.getRadarBlankTicks());
        if (words.isEmpty()) {
            return;
        }
        Font font = mc.font;
        String line = String.join(" ", words);
        int x = graphics.guiWidth() - font.width(line) - 4;
        graphics.fill(x - 2, Y_OFFSET - 1, x + font.width(line) + 2, Y_OFFSET + font.lineHeight + 1, 0x88000000);
        graphics.text(font, line, x, Y_OFFSET, 0xFF88CCFF);
    }
}
