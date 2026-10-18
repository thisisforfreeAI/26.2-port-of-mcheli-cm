package io.github.mcheliceport.multiplay;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.ship.ShipEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import io.github.mcheliceport.net.MarkPosPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.SpotEnemyPayload;
import io.github.mcheliceport.util.McheliMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Team spotting, upstream's {@code MCH_Multiplay#spotEntity} and {@code markPoint}: the
 * rangefinder and targeting pods mark what they see for the spotter and the spotter's team.
 *
 * <p>The target filter is upstream's bit set: 1 other mobs, 2 monsters, 4 players, 8 ground
 * vehicles and tanks, 16 helicopters, 32 planes and ships.
 */
public final class Spotting {

    public static final int FILTER_MOBS = 1;
    public static final int FILTER_MONSTERS = 2;
    public static final int FILTER_PLAYERS = 4;
    public static final int FILTER_GROUND = 8;
    public static final int FILTER_HELIS = 16;
    public static final int FILTER_PLANES = 32;
    /** Upstream's mark point height when nothing was hit: clients clear their mark point. */
    public static final int NO_POINT_Y = 1000;

    /** Upstream {@code ENTITY_SPOT_TABLE}: by target row, and whether the spotter is on a team. */
    private static final TargetType[][] SPOT_TABLE = {
            {TargetType.NONE, TargetType.NONE},
            {TargetType.OTHER_MOB, TargetType.OTHER_MOB},
            {TargetType.MONSTER, TargetType.MONSTER},
            {TargetType.NONE, TargetType.NO_TEAM_PLAYER},
            {TargetType.NONE, TargetType.SAME_TEAM_PLAYER},
            {TargetType.NONE, TargetType.OTHER_TEAM_PLAYER},
            {TargetType.NONE, TargetType.NONE},
            {TargetType.NONE, TargetType.NO_TEAM_PLAYER},
            {TargetType.NONE, TargetType.SAME_TEAM_PLAYER},
            {TargetType.NONE, TargetType.OTHER_TEAM_PLAYER}};

    private Spotting() {
    }

    /** Upstream {@code canSpotEntityWithFilter}. */
    public static boolean matchesFilter(int filter, Entity entity) {
        if (entity instanceof PlaneEntity || entity instanceof ShipEntity) {
            return (filter & FILTER_PLANES) != 0;
        } else if (entity instanceof HeliEntity) {
            return (filter & FILTER_HELIS) != 0;
        } else if (entity instanceof VehicleEntity || entity instanceof TankEntity) {
            return (filter & FILTER_GROUND) != 0;
        } else if (entity instanceof Player) {
            return (filter & FILTER_PLAYERS) != 0;
        } else if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return isMonster(entity) ? (filter & FILTER_MONSTERS) != 0 : (filter & FILTER_MOBS) != 0;
    }

    /**
     * Upstream {@code isMonster} looked for "monster" in the class name, which in 1.12 meant the
     * {@code net.minecraft.entity.monster} package. Hostile mobs are {@link Enemy} now; the name
     * test is kept for other mods' mobs.
     */
    public static boolean isMonster(Entity entity) {
        return entity instanceof Enemy || entity.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("monster");
    }

    /** Upstream {@code getFirstMountPlayer}: the pilot if a player, else the first player in a seat. */
    public static @Nullable Player firstMountPlayer(AircraftEntity ac) {
        if (ac.getPilot() instanceof Player player) {
            return player;
        }
        for (SeatEntity seat : ac.getSeats()) {
            if (seat != null && seat.getRider() instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** Upstream {@code isMountedSameTeamEntity}: someone aboard is on the player's team. */
    public static boolean isMountedSameTeam(AircraftEntity ac, @Nullable Entity player) {
        if (player == null || player.getTeam() == null) {
            return false;
        }
        if (ac.getPilot() instanceof LivingEntity pilot && player.isAlliedTo(pilot)) {
            return true;
        }
        for (SeatEntity seat : ac.getSeats()) {
            if (seat != null && seat.getRider() instanceof LivingEntity rider && player.isAlliedTo(rider)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Upstream {@code canSpotEntity}: how the spotter sees the target, or {@link TargetType#NONE}
     * if not at all. Players and vehicles only show to a spotter on a team. With {@code checkSee},
     * a block between {@code pos} and the target's eyes hides it.
     */
    public static TargetType canSpotEntity(Entity user, Vec3 pos, Entity target, boolean checkSee) {
        if (!(user instanceof LivingEntity spotter)) {
            return TargetType.NONE;
        }
        int col = spotter.getTeam() == null ? 0 : 1;
        int row = 0;
        if (target instanceof LivingEntity) {
            row = isMonster(target) ? 2 : 1;
        }
        if (spotter.getTeam() != null) {
            if (target instanceof Player player) {
                row = spotter.isAlliedTo(player) ? 4 : 5;
            } else if (target instanceof AircraftEntity ac) {
                Player rider = firstMountPlayer(ac);
                row = rider == null ? 6 : spotter.isAlliedTo(rider) ? 8 : 9;
            }
        } else if (target instanceof Player || target instanceof AircraftEntity) {
            row = 0;
        }
        TargetType ret = SPOT_TABLE[row][col];
        if (checkSee && ret != TargetType.NONE) {
            Vec3 eyes = new Vec3(target.getX(), target.getEyeY(), target.getZ());
            BlockHitResult hit = target.level().clip(new ClipContext(pos, eyes, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, target));
            if (hit.getType() == HitResult.Type.BLOCK) {
                ret = TargetType.NONE;
            }
        }
        return ret;
    }

    /**
     * Upstream {@code spotEntity}: marks, for the spotter and the spotter's team, every entity the
     * filter takes within {@code spotLength} blocks of {@code pos} and within twice {@code angle}
     * degrees of where the spotter looks. Teammates are never marked. Returns whether anything was.
     *
     * @param ac       the vehicle the spotter aims from, whose roll turns the view; may be null
     * @param markTime seconds the marks last
     */
    public static boolean spotEntity(LivingEntity player, @Nullable AircraftEntity ac, Vec3 pos, int targetFilter,
            float spotLength, int markTime, float angle) {
        if (player.level().isClientSide()) {
            return false;
        }
        float acRoll = ac != null ? ac.getRoll() : 0.0F;
        Vec3 look = McheliMath.rotVec3(0.0, 0.0, 1.0, -player.getYRot(), -player.getXRot(), -acRoll);
        List<Entity> list = player.level().getEntities(player, player.getBoundingBox().inflate(spotLength));
        List<Integer> spotted = new ArrayList<>();
        for (Entity entity : list) {
            if (!matchesFilter(targetFilter, entity)) {
                continue;
            }
            TargetType type = canSpotEntity(player, pos, entity, true);
            if (type == TargetType.NONE || type == TargetType.SAME_TEAM_PLAYER) {
                continue;
            }
            double dist = entity.distanceToSqr(pos);
            if (dist > 1.0 && dist < spotLength * spotLength) {
                double cx = entity.getX() - pos.x;
                double cy = entity.getY() - pos.y;
                double cz = entity.getZ() - pos.z;
                double h = posAngle(look.x, look.z, cx, cz);
                double v = Math.abs(Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0 / Math.PI + player.getXRot());
                if (h < angle * 2.0F && v < angle * 2.0F) {
                    spotted.add(entity.getId());
                }
            }
        }
        if (spotted.isEmpty()) {
            return false;
        }
        int[] ids = spotted.stream().mapToInt(Integer::intValue).toArray();
        sendToTeam(player, new SpotEnemyPayload(markTime, ids));
        return true;
    }

    /**
     * Upstream {@code markPoint}: marks the block the player looks at, up to 300 blocks away, for
     * the player's team (two blocks above it, as upstream). Looking at nothing clears the team's
     * mark and returns false.
     */
    public static boolean markPoint(Player player, Vec3 pos) {
        Vec3 end = pos.add(lookVector(player.getYRot(), player.getXRot()).scale(300.0));
        BlockHitResult hit = player.level().clip(new ClipContext(pos, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            sendToTeam(player, new MarkPosPayload(hit.getBlockPos().getX(), hit.getBlockPos().getY() + 2,
                    hit.getBlockPos().getZ()));
            return true;
        }
        sendToTeam(player, new MarkPosPayload(0, NO_POINT_Y, 0));
        return false;
    }

    /**
     * Upstream {@code sendSpotedEntityListToSameTeam}/{@code sendMarkPointToSameTeam}: to the
     * spotter and every player on the spotter's team. (Upstream's mark point went to the spotter
     * once per teammate instead; the team now gets it, as the method's name meant.)
     */
    private static void sendToTeam(LivingEntity spotter, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (spotter.level().getServer() == null) {
            return;
        }
        for (ServerPlayer player : spotter.level().getServer().getPlayerList().getPlayers()) {
            if (player == spotter || spotter.getTeam() != null && spotter.isAlliedTo(player)) {
                ModNetwork.sendToPlayer(player, payload);
            }
        }
    }

    /** Upstream {@code MCH_Lib.getPosAngle}: the angle in degrees between two horizontal vectors. */
    public static float posAngle(double tx, double tz, double cx, double cz) {
        double a = Math.sqrt(tx * tx + tz * tz);
        double b = Math.sqrt(cx * cx + cz * cz);
        return (float) (Math.acos((tx * cx + tz * cz) / (a * b)) * 180.0 / Math.PI);
    }

    /** Upstream {@code MCH_Lib.Rot2Vec3}. */
    public static Vec3 lookVector(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }
}
