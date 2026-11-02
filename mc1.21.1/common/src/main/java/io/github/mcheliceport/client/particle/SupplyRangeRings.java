package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import io.github.mcheliceport.shim.client.event.SubscribeEvent;
import io.github.mcheliceport.shim.client.event.ClientTickEvent;

/**
 * M10c, a port addition: while the player rides a vehicle, every fuel or ammo supply vehicle
 * within 64 blocks marks its supply range on the ground with a ring of sparkles once a second.
 */
@io.github.mcheliceport.shim.client.event.ClientSubscriber
public final class SupplyRangeRings {

    private static final double VIEW = 64.0;
    private static final int POINTS = 36;

    private SupplyRangeRings() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % 20 != 0
                || !(mc.player.getRootVehicle() instanceof AircraftEntity)) {
            return;
        }
        for (AircraftEntity supply : mc.level.getEntitiesOfClass(AircraftEntity.class,
                mc.player.getBoundingBox().inflate(VIEW))) {
            AircraftInfo info = supply.getAcInfo();
            if (info == null || supply.isDestroyed()) {
                continue;
            }
            double range = Math.max(info.fuelSupplyRange, info.ammoSupplyRange);
            if (range <= 0.0 || range > VIEW) {
                continue;
            }
            for (int i = 0; i < POINTS; i++) {
                double a = Math.PI * 2.0 * i / POINTS;
                mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, supply.getX() + Math.cos(a) * range,
                        supply.getY() + 0.2, supply.getZ() + Math.sin(a) * range, 0.0, 0.0, 0.0);
            }
        }
    }
}
