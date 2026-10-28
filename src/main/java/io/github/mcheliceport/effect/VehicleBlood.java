package io.github.mcheliceport.effect;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.VehicleBloodPayload;
import io.github.mcheliceport.registry.ModTags;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Explicit before/after scopes around MC Heli damage; never a global death attribution listener. */
public final class VehicleBlood {
    private static final Map<ServerLevel, VehicleBloodRules.Gate> LEVELS = new WeakHashMap<>();
    private VehicleBlood() {}

    public record Hit(Mob victim, DamageSource source, Vec3 feet, float width, float height, Vec3 direction, byte cause) {}

    public static @Nullable Hit projectile(Entity target, @Nullable Entity exploder, DamageSource source,
            byte cause, Vec3 direction) {
        if (!(exploder instanceof BaseBulletEntity bullet)
                || !(bullet.shootingAircraft instanceof AircraftEntity aircraft) || aircraft.level() != target.level()
                || !VehicleBloodRules.conventional(bullet.getInfo())) return null;
        return begin(target, source, cause, direction);
    }

    public static @Nullable Hit ram(Entity target, AircraftEntity vehicle, DamageSource source, Vec3 direction) {
        return vehicle.level() == target.level() ? begin(target, source, VehicleBloodRules.RAM, direction) : null;
    }

    private static @Nullable Hit begin(Entity target, DamageSource source, byte cause, Vec3 direction) {
        if (!(target.level() instanceof ServerLevel) || !(target instanceof Mob mob) || !mob.isAlive()
                || mob.getPose() == Pose.DYING || !mob.getType().builtInRegistryHolder().is(ModTags.VEHICLE_BLOOD_MOBS)
                || !VehicleBloodRules.allowedNamespace(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace())) return null;
        if (!Double.isFinite(direction.lengthSqr())) direction = Vec3.ZERO;
        return new Hit(mob, source, mob.position(), Math.clamp(mob.getBbWidth(), .1F, 3),
                Math.clamp(mob.getBbHeight(), .1F, 4), direction.normalize(), cause);
    }

    /** Called immediately after the original hurtServer, before any later discard/fire/knockback. */
    public static @Nullable VehicleBloodPayload finish(@Nullable Hit hit, boolean applied) {
        if (hit == null || !applied) return null;
        Mob mob = hit.victim;
        // A cancelled LivingDeathEvent can leave health at zero: vanilla's accepted death also sets DYING.
        // Totems, nonlethal hits and deaths from a nested, unrelated source fail these checks.
        if (mob.getHealth() > 0 || mob.isAlive() || mob.getPose() != Pose.DYING
                || mob.getLastDamageSource() != hit.source || !(mob.level() instanceof ServerLevel level)) return null;
        var p = new VehicleBloodPayload(mob.getUUID(), hit.feet.x, hit.feet.y, hit.feet.z,
                hit.width, hit.height, (float)hit.direction.x, (float)hit.direction.y, (float)hit.direction.z,
                hit.cause, mob.getUUID().hashCode());
        if (!p.valid() || !LEVELS.computeIfAbsent(level, k -> new VehicleBloodRules.Gate(8))
                .admit(mob.getUUID(), level.getGameTime())) return null;
        for (var player : level.getChunkSource().chunkMap.getPlayersWatching(mob)) {
            if (player.distanceToSqr(hit.feet) <= 64*64) ModNetwork.sendToPlayer(player, p);
        }
        return p;
    }

    public static boolean reported(ServerLevel level, java.util.UUID victim) {
        var gate = LEVELS.get(level);
        return gate != null && gate.contains(victim);
    }
}
