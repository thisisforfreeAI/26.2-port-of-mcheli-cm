package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.content.weapon.BulletDecay;
import io.github.mcheliceport.content.weapon.PotionEffectInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.net.LaserShotPayload;
import io.github.mcheliceport.net.NotifyHitPayload;
import io.github.mcheliceport.weapon.DamageScaling;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponTargets;
import io.github.mcheliceport.weapon.WeaponWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Laser: an instant beam to the first block or entity along the shot, damaging (with decay over
 * distance), igniting and applying the definition's potion effects, drawn on every client within
 * 1000 blocks. Ported from upstream {@code MCH_WeaponLaser}.
 *
 * <p>Upstream traced blocks with {@code returnLastUncollidableBlock}, which hands back a result
 * even when nothing was hit, so a beam into the sky counted as a hit and stopped at an arbitrary
 * point. Here a miss reaches the full range and shows no impact particles.
 */
public class LaserWeapon extends WeaponBase {

    private static final double BROADCAST_RANGE = 1000.0;

    public LaserWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.power = 8;
        this.acceleration = 4.0F;
        this.explosionPower = 0;
        this.interval = 0;
    }

    @Override
    public boolean shot(WeaponParam prm) {
        float[] rot = this.calculateShotRotation(prm, true, false);
        Vec3 look = WeaponWorld.fromPitchYaw(rot[1], rot[0]);
        if (!this.world.isClientSide()) {
            if (prm.user != null) {
                this.fireLaser(prm, look);
            }
            this.playSound(prm.entity);
        } else {
            WeaponInfo info = this.getInfo();
            WeaponClientHooks.applyRecoil(info.getRecoilPitch(), info.getRecoilYaw(), info.recoilRecoverFactor);
        }
        return true;
    }

    private void fireLaser(WeaponParam prm, Vec3 look) {
        double range = this.weaponInfo.length > 0.0F ? this.weaponInfo.length : 256.0;
        Vec3 start = new Vec3(prm.posX, prm.posY, prm.posZ);
        Vec3 end = start.add(look.scale(range));
        Hit result = laserRaytrace(prm, start, look, end);
        Vec3 hitVec = result != null ? result.pos() : end;
        if (result != null && result.entity() != null) {
            this.applyDamage(prm, result.entity(), start, hitVec);
        }
        int argb = this.weaponInfo.color != null ? this.weaponInfo.color.toARGB() : 0xFFFF0000;
        float width = this.weaponInfo.radius > 0.0F ? this.weaponInfo.radius : 0.1F;
        int life = Math.max(1, this.weaponInfo.timeFuse);
        if (this.world instanceof ServerLevel server) {
            ModNetwork.sendToPlayersNear(server, null, prm.posX, prm.posY, prm.posZ, BROADCAST_RANGE,
                    new LaserShotPayload(start, hitVec, argb, width, life, true, this.weaponInfo.turningFactor,
                            result != null));
        }
    }

    private void applyDamage(WeaponParam prm, Entity hit, Vec3 start, Vec3 hitVec) {
        DamageScaling.applyHurtResistantTime(hit);
        DamageSource ds = this.world.damageSources().thrown(prm.entity, prm.user);
        float damage = DamageScaling.applyDamageVsEntity(hit, this.power);
        damage *= this.getInfo() != null ? this.getInfo().getDamageFactor(WeaponTargets.targetName(hit)) : 1.0F;
        float dist = (float) start.distanceTo(hitVec);
        if (this.weaponInfo.enableBulletDecay && this.weaponInfo.bulletDecay != null) {
            float decayFactor = 1.0F;
            for (BulletDecay decay : this.weaponInfo.bulletDecay) {
                decayFactor = decay.calculateDecayFactor(dist);
            }
            damage *= decayFactor;
        }
        List<LivingEntity> living = new ArrayList<>();
        if (hit instanceof LivingEntity lb) {
            hit.igniteForSeconds(5);
            living.add(lb);
        }
        if (hit instanceof AircraftEntity ac) {
            if (ac.getFirstPassenger() instanceof LivingEntity rb) {
                living.add(rb);
            }
            for (SeatEntity seat : ac.getSeats()) {
                if (seat != null && seat.getFirstPassenger() instanceof LivingEntity sr) {
                    living.add(sr);
                }
            }
        }
        if (this.getInfo() != null && this.getInfo().potionEffect != null) {
            for (LivingEntity lb : living) {
                for (PotionEffectInfo effect : this.getInfo().potionEffect) {
                    if (effect.startDist() < 0 && effect.endDist() < 0
                            || effect.startDist() <= dist && dist < effect.endDist()) {
                        effect.toInstance().ifPresent(lb::addEffect);
                    }
                }
            }
        }
        if (this.world instanceof ServerLevel server) {
            hit.hurtServer(server, ds, damage);
        }
        if (prm.user instanceof ServerPlayer player && prm.entity instanceof AircraftEntity ac) {
            NotifyHitPayload.send(ac, player);
        }
    }

    public static boolean canHitByLaser(@Nullable Entity entity, WeaponParam prm) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof BaseBulletEntity b) {
            if (b.getInfo() != null && !b.getInfo().canBeIntercepted) {
                return false;
            }
            if (WeaponWorld.isEqual(b.shootingAircraft, prm.entity)) {
                return false;
            }
            if (WeaponWorld.isEqual(b.shootingEntity, prm.user)) {
                return false;
            }
        }
        if (entity instanceof SeatEntity) {
            return false;
        }
        if (entity instanceof AircraftHitBox hb && WeaponWorld.isEqual(hb.getParent(), prm.entity)) {
            return false;
        }
        if (WeaponWorld.isEqual(entity, prm.user)) {
            return false;
        }
        if (prm.user != null && WeaponWorld.isEqual(entity, prm.user.getVehicle())) {
            return false;
        }
        if (prm.entity instanceof AircraftEntity ac) {
            if (WeaponWorld.isEqual(entity, prm.entity)) {
                return false;
            }
            return !ac.isMountedEntity(entity);
        }
        return true;
    }

    /** What the beam hit: a block ({@code entity} null) or an entity, at {@code pos}. */
    public record Hit(Vec3 pos, @Nullable Entity entity) {
    }

    /** Blocks first, then entities (bounding boxes grown by 0.3); the nearest hit, or null. */
    public static @Nullable Hit laserRaytrace(WeaponParam prm, Vec3 start, Vec3 look, Vec3 end) {
        Entity user = prm.user;
        double maxDist = start.distanceTo(end);
        BlockHitResult block = WeaponWorld.rayTrace(user.level(), start, end, false, true);
        Hit result = block != null ? new Hit(block.getLocation(), null) : null;
        double blockDist = block != null ? block.getLocation().distanceTo(start) : maxDist;
        Entity pointed = null;
        Vec3 pointedVec = null;
        double closest = blockDist;
        AABB searchBox = user.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(1.0);
        for (Entity entity : user.level().getEntities(user, searchBox)) {
            if (!entity.isPickable() || !canHitByLaser(entity, prm)) {
                continue;
            }
            AABB box = entity.getBoundingBox().inflate(0.3);
            Optional<Vec3> intercept = box.clip(start, end);
            if (box.contains(start)) {
                pointed = entity;
                pointedVec = intercept.orElse(start);
                closest = 0.0;
            } else if (intercept.isPresent()) {
                double d = start.distanceTo(intercept.get());
                if (d < closest || closest == 0.0) {
                    pointed = entity;
                    pointedVec = intercept.get();
                    closest = d;
                }
            }
        }
        if (pointed != null && (closest < blockDist || result == null)) {
            result = new Hit(pointedVec, pointed);
        }
        return result;
    }
}
