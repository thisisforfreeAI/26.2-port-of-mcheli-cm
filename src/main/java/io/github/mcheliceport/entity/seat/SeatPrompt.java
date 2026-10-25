package io.github.mcheliceport.entity.seat;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.item.SpawnGunnerItem;
import io.github.mcheliceport.item.WrenchItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code GuiInteractSeat} seat choice, for the enter-seat prompt. Seat 0 is the pilot's;
 * passenger seats count from 1, as upstream.
 */
public final class SeatPrompt {

    private SeatPrompt() {
    }

    /** The seat the use key would enter on {@code target}, or -1 when the prompt is hidden. */
    public static int targetSeatId(Player player, @Nullable Entity target) {
        if (player.getVehicle() != null || player.isShiftKeyDown() || blocksPrompt(player.getMainHandItem())) {
            return -1;
        }
        if (target instanceof SeatEntity seat) {
            AircraftEntity parent = seat.getParent();
            if (parent == null || parent.isDestroyed() || parent.notOnSameTeam(player)
                    || seat.getRider() != null || !seat.canRideMob()) {
                return -1;
            }
            return seat.getSeatIndex() + 1;
        }
        if (target instanceof AircraftHitBox hitBox) {
            target = hitBox.getParent();
        }
        if (!(target instanceof AircraftEntity aircraft) || aircraft.isDestroyed() || aircraft.getAcInfo() == null
                || aircraft.notOnSameTeam(player) || !aircraft.getAcInfo().canRide) {
            return -1;
        }
        if (aircraft.getPilot() != null || aircraft.isUAV()) {
            return firstFreePassengerSeat(aircraft, player);
        }
        if (!aircraft.canRideSeatOrRack(0, player)
                || aircraft.getAcInfo().haveCanopy() && aircraft.isCanopyClose()
                || aircraft.getModeSwitchCooldown() > 0) {
            return -1;
        }
        return 0;
    }

    private static int firstFreePassengerSeat(AircraftEntity aircraft, Player player) {
        int seatId = 1;
        for (SeatEntity seat : aircraft.getSeats()) {
            if (seat != null && seat.getRider() == null && !aircraft.isMountedEntity(player)
                    && aircraft.canRideSeatOrRack(seatId, player)) {
                return seatId;
            }
            seatId++;
        }
        return -1;
    }

    private static boolean blocksPrompt(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem || stack.getItem() instanceof SpawnGunnerItem;
    }
}
