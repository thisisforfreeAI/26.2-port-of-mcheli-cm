package io.github.mcheliceport.radar;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.flare.ChaffEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.weapon.AAMissileEntity;
import io.github.mcheliceport.net.EntityInfoSyncPayload;
import io.github.mcheliceport.net.ModNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Upstream's {@code EntityInfoManagerAsync}: every ten ticks each player is sent the players,
 * vehicles, flares, chaff and air-to-air missiles within 2000 blocks of them, for the BVR and
 * mortar radar HUDs. Planes, helicopters and chaff count only 30 blocks or more above the ground
 * and moving at half a block a tick or more.
 */
public final class EntityInfoFeed {

    private static final int INTERVAL = 10;
    private static final double RANGE = 2000.0;
    private static int tickCounter;
    private static long snapshotSeq;

    private EntityInfoFeed() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EntityInfoFeed::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % INTERVAL != 0) {
            return;
        }
        snapshotSeq++;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            List<EntityInfo> tracked = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (shouldTrack(level, entity)) {
                    tracked.add(EntityInfo.of(entity));
                }
            }
            for (ServerPlayer player : level.players()) {
                List<EntityInfo> visible = new ArrayList<>();
                for (EntityInfo info : tracked) {
                    double dx = player.getX() - info.x();
                    double dz = player.getZ() - info.z();
                    if (dx * dx + dz * dz <= RANGE * RANGE) {
                        visible.add(info);
                    }
                }
                if (!visible.isEmpty()) {
                    ModNetwork.sendToPlayer(player, new EntityInfoSyncPayload(visible, snapshotSeq));
                }
            }
        }
    }

    /** Upstream {@code shouldTrack}. */
    public static boolean shouldTrack(ServerLevel level, Entity entity) {
        if (entity.isRemoved() || !(entity instanceof Player || entity instanceof AircraftEntity
                || entity instanceof ChaffEntity || entity instanceof FlareEntity
                || entity instanceof AAMissileEntity)) {
            return false;
        }
        if (entity instanceof PlaneEntity || entity instanceof HeliEntity || entity instanceof ChaffEntity) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, entity.getBlockX(), entity.getBlockZ());
            if ((int) (entity.getY() - ground) < 30) {
                return false;
            }
            return entity.getDeltaMovement().lengthSqr() >= 0.25;
        }
        return true;
    }
}
