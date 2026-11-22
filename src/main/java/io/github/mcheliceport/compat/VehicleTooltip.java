package io.github.mcheliceport.compat;

import io.github.mcheliceport.damage.ComponentState;
import io.github.mcheliceport.damage.ComponentView;
import io.github.mcheliceport.damage.VehicleComponents;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;

/**
 * The lines an info overlay (Jade) shows for a looked-at vehicle. Everything here is already synced
 * to every client (hull damage, fuel, riders, component health), so no server data is requested.
 */
public final class VehicleTooltip {
    private VehicleTooltip() {
    }

    public static List<Component> lines(AircraftEntity vehicle) {
        List<Component> lines = new ArrayList<>();
        if (vehicle.getAcInfo() == null) {
            return lines;
        }
        if (vehicle.isDestroyed()) {
            lines.add(Component.translatable("mcheli_ce_port.jade.destroyed").withStyle(ChatFormatting.DARK_RED));
        } else {
            int hp = vehicle.getHP(), max = vehicle.getMaxHP();
            lines.add(Component.translatable("mcheli_ce_port.jade.hull", amount(hp, max).withStyle(ratioColor(hp, max))));
        }
        int maxFuel = vehicle.getMaxFuel();
        if (maxFuel > 0) {
            lines.add(Component.translatable(vehicle.isElectric() ? "mcheli_ce_port.jade.charge" : "mcheli_ce_port.jade.fuel",
                    amount(vehicle.getFuel(), maxFuel).withStyle(ratioColor(vehicle.getFuel(), maxFuel))));
        }
        Entity pilot = vehicle.getPilot();
        lines.add(pilot != null
                ? Component.translatable("mcheli_ce_port.jade.pilot", pilot.getDisplayName())
                : Component.translatable("mcheli_ce_port.jade.no_pilot").withStyle(ChatFormatting.GRAY));
        int seats = vehicle.getSeatNum();
        if (seats > 0 && vehicle.getAcInfo().getNumSeatAndRack() > 1) {
            int crew = 0;
            for (int i = 1; i <= seats; i++) {
                if (vehicle.getEntityBySeatId(i) != null) {
                    crew++;
                }
            }
            lines.add(Component.translatable("mcheli_ce_port.jade.crew", crew, seats));
        }
        PlayerTeam team = pilot instanceof LivingEntity rider ? rider.getTeam() : null;
        if (team != null) {
            lines.add(Component.translatable("mcheli_ce_port.jade.team", team.getFormattedDisplayName()));
        }
        if (!vehicle.isDestroyed()) {
            for (ComponentView part : VehicleComponents.of(vehicle)) {
                if (part.state() != ComponentState.OK) {
                    lines.add(Component.translatable("mcheli_ce_port.jade.component."
                                    + part.state().name().toLowerCase(Locale.ROOT),
                            Component.translatable("mcheli_ce_port.jade.part." + part.kind().name().toLowerCase(Locale.ROOT)))
                            .withStyle(part.state() == ComponentState.DISABLED ? ChatFormatting.RED : ChatFormatting.GOLD));
                }
            }
        }
        return lines;
    }

    private static MutableComponent amount(int value, int max) {
        int percent = max > 0 ? Math.round(100.0F * value / max) : 0;
        return Component.literal(value + " / " + max + " (" + percent + "%)");
    }

    static ChatFormatting ratioColor(int value, int max) {
        float r = max > 0 ? (float) value / max : 0.0F;
        return r > 0.5F ? ChatFormatting.GREEN : r > 0.25F ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }
}
