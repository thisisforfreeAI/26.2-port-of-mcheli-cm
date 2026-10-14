package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.TorpedoEntity;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Torpedo: unguided ones run along the shot direction; guided ones fire only when the user looks
 * at a block in water within reach and run to that point. Ported from upstream
 * {@code MCH_WeaponTorpedo}.
 */
public class TorpedoWeapon extends WeaponBase {

    public TorpedoWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.acceleration = 0.5F;
        this.explosionPower = 8;
        this.power = 35;
        this.interval = -100;
        if (w.isClientSide()) {
            this.interval -= 10;
        }
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (this.getInfo() != null) {
            return this.getInfo().isGuidedTorpedo ? this.shotGuided(prm) : this.shotNoGuided(prm);
        }
        return false;
    }

    /** The launch velocity: the direction times {@code Acceleration}, plus the vehicle's velocity. */
    private Vec3 launchMotion(float yaw, float pitch, Entity entity) {
        double mx = -Mth.sin(yaw / 180.0F * (float) Math.PI) * Mth.cos(pitch / 180.0F * (float) Math.PI);
        double mz = Mth.cos(yaw / 180.0F * (float) Math.PI) * Mth.cos(pitch / 180.0F * (float) Math.PI);
        double my = -Mth.sin(pitch / 180.0F * (float) Math.PI);
        Vec3 em = entity.getDeltaMovement();
        float acc = this.getInfo().acceleration;
        return new Vec3(mx * acc + em.x, my * acc + em.y, mz * acc + em.z);
    }

    protected boolean shotNoGuided(WeaponParam prm) {
        if (!this.world.isClientSide() && prm.entity != null) {
            float yaw = prm.rotYaw;
            float pitch = prm.rotPitch;
            Vec3 m = this.launchMotion(yaw, pitch, prm.entity);
            this.acceleration = (float) m.length();
            TorpedoEntity e = new TorpedoEntity(this.world, prm.posX, prm.posY, prm.posZ, m.x, m.y, m.z, yaw, 0.0F,
                    this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            e.setDeltaMovement(m);
            e.accelerationInWater = this.getInfo().accelerationInWater;
            this.world.addFreshEntity(e);
            this.playSound(prm.entity);
        }
        return true;
    }

    protected boolean shotGuided(WeaponParam prm) {
        if (prm.user == null || prm.entity == null) {
            return false;
        }
        float yaw = prm.user.getYRot();
        float pitch = prm.user.getXRot();
        Vec3 v = McheliMath.rotVec3(0.0, 0.0, 1.0, -yaw, -pitch, -prm.rotRoll);
        double reach = this.world.isClientSide() ? 100.0 : 150.0;
        Vec3 t = v.scale(reach / v.length());
        Vec3 src = prm.user.position();
        Vec3 dst = src.add(t);
        BlockHitResult m = WeaponWorld.clip(this.world, src, dst);
        if (m == null || !WeaponWorld.isBlockInWater(this.world, m.getBlockPos())) {
            return false;
        }
        if (!this.world.isClientSide()) {
            Vec3 motion = this.launchMotion(yaw, pitch, prm.entity);
            this.acceleration = (float) motion.length();
            Vec3 em = prm.entity.getDeltaMovement();
            TorpedoEntity e = new TorpedoEntity(this.world, prm.posX, prm.posY, prm.posZ, em.x, em.y, em.z, yaw, 0.0F,
                    this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            e.targetPosX = m.getLocation().x;
            e.targetPosY = m.getLocation().y;
            e.targetPosZ = m.getLocation().z;
            e.setDeltaMovement(motion);
            e.accelerationInWater = this.getInfo().accelerationInWater;
            this.world.addFreshEntity(e);
            this.playSound(prm.entity);
        }
        return true;
    }
}
