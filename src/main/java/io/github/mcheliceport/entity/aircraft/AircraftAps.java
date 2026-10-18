package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.DamageScaling;
import io.github.mcheliceport.weapon.explosion.McheliExplosion;
import java.util.Locale;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * A vehicle's active protection system: for {@code APSUseTime} ticks it shoots down projectiles
 * and grenades within {@code APSRange} (not those of the pilot's team), then waits
 * {@code APSWaitTime}. Ported from upstream {@code MCH_APS}.
 *
 * <p>A range of 100 is upstream's "iron curtain": instead of shooting anything down, the vehicle takes
 * no damage while it runs, glows red, and hits on it throw up red sparks. Upstream played the activation sound on both
 * sides, so the user heard it twice; here the server plays it for everyone. Other mods'
 * projectiles count by their type name ("bullet", "grenade"), as upstream matched class names.
 */
public final class AircraftAps {

    private static final int IRON_CURTAIN_RANGE = 100;

    public int tick;
    public int useTick;
    public int useTime;
    public int waitTime;
    public int range;
    private final Level world;
    private final AircraftEntity aircraft;

    public AircraftAps(Level world, AircraftEntity aircraft) {
        this.world = world;
        this.aircraft = aircraft;
    }

    public boolean onUse() {
        if (this.tick != 0) {
            return false;
        }
        this.tick = this.waitTime;
        this.useTick = this.useTime;
        if (this.range == IRON_CURTAIN_RANGE) {
            this.aircraft.startIronCurtain(this.useTick);
        }
        McheliSounds.play(this.world, this.aircraft.getX(), this.aircraft.getY(), this.aircraft.getZ(),
                this.range == IRON_CURTAIN_RANGE ? "iron_curtain" : "aps_activate",
                this.range == IRON_CURTAIN_RANGE ? 10.0F : 3.0F, 1.0F);
        return true;
    }

    public void onUpdate() {
        if (!this.aircraft.isAlive()) {
            return;
        }
        if (this.tick > 0) {
            this.tick--;
        }
        if (this.useTick > 0) {
            this.useTick--;
            if (this.useTick == 0) {
                McheliSounds.play(this.world, this.aircraft.getX(), this.aircraft.getY(), this.aircraft.getZ(),
                        "aps_deactivate", 3.0F, 1.0F);
                if (this.range == IRON_CURTAIN_RANGE) {
                    this.aircraft.endIronCurtain();
                }
            } else {
                this.onUsing();
            }
        }
    }

    private static boolean typeNameContains(Entity entity, String part) {
        String id = DamageScaling.typeId(entity);
        return id != null && id.toLowerCase(Locale.ROOT).contains(part);
    }

    private void onUsing() {
        if (this.range == IRON_CURTAIN_RANGE) {
            return;
        }
        for (Entity entity : this.world.getEntities(this.aircraft, this.aircraft.getBoundingBox().inflate(this.range))) {
            if (!entity.isAlive()) {
                continue;
            }
            boolean isMissile = entity instanceof BaseBulletEntity;
            boolean isBullet = isMissile || typeNameContains(entity, "bullet");
            boolean isGrenade = typeNameContains(entity, "grenade");
            if (!isBullet && !isGrenade) {
                continue;
            }
            if (entity instanceof BaseBulletEntity bullet && bullet.shootingEntity instanceof LivingEntity shooter
                    && this.aircraft.getPilot() instanceof LivingEntity pilot && shooter.isAlliedTo(pilot)) {
                continue;
            }
            if (this.world.isClientSide()) {
                this.spawnFlameLine(entity);
            } else {
                McheliSounds.play(this.world, this.aircraft.getX(), this.aircraft.getY(), this.aircraft.getZ(),
                        "aps_shoot", 5.0F, 1.0F);
                entity.discard();
                if (isMissile || isGrenade) {
                    McheliExplosion.newExplosion(this.world, null, null, entity.getX(), entity.getY(), entity.getZ(),
                            2.0F, 0.0F, true, false, true, 0);
                }
            }
        }
    }

    private void spawnFlameLine(Entity target) {
        double ax = this.aircraft.getX();
        double ay = this.aircraft.getY();
        double az = this.aircraft.getZ();
        double dx = target.getX() - ax;
        double dy = target.getY() - ay;
        double dz = target.getZ() - az;
        int count = (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 5);
        for (int i = 0; i < count; i++) {
            double r = (double) i / count;
            McheliEffects.spawnVanilla(this.world, "flame", ax + dx * r, ay + dy * r,
                    az + dz * r, 0.0, 0.0, 0.0);
        }
    }

    public boolean isInPreparation() {
        return this.tick != 0;
    }

    public boolean isUsing() {
        return this.useTick > 0;
    }
}
