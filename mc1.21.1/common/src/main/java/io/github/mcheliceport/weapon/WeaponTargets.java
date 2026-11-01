package io.github.mcheliceport.weapon;

import io.github.mcheliceport.content.weapon.DamageFactor;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.heli.HeliEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** How weapons see entities: damage factor names, and the projectile interfaces explosions ask about. */
public final class WeaponTargets {

    private WeaponTargets() {
    }

    /** An entity carrying a weapon definition (projectiles), for the explosion's through-wall settings. */
    public interface InfoHolder {
        @Nullable WeaponInfo getWeaponInfo();
    }

    /** A McHeli projectile: explosions do not push it, and its own shooter's blast does not count as a hit. */
    public interface Projectile extends InfoHolder {
        @Nullable Entity getShooter();
    }

    /**
     * The {@link DamageFactor} key of an entity: {@code player}, {@code helicopter} (a helicopter or
     * one of its hit boxes; upstream matched only the helicopter itself), or the entity type ID.
     *
     * <p>Upstream compared exact classes, so its {@code player} factor never matched a real player
     * ({@code EntityPlayerMP}); the port applies it to players.
     */
    public static String targetName(Entity entity) {
        if (entity instanceof Player) {
            return DamageFactor.PLAYER;
        }
        if (entity instanceof AircraftHitBox part) {
            entity = part.getParent();
        }
        if (entity instanceof HeliEntity) {
            return DamageFactor.HELICOPTER;
        }
        String id = DamageScaling.typeId(entity);
        return id != null ? id : "";
    }
}
