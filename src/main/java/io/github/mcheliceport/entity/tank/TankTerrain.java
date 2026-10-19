package io.github.mcheliceport.entity.tank;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Local collision probes, as upstream's untracked MCH_EntityWheel/MCH_WheelManager. */
public final class TankTerrain {
    private final TankEntity tank;
    private final TankInfo info;
    private final Wheel[] wheels;
    private final Vec3 center;
    private final double span;
    /**
     * The most the ground tilts a tank, either way, in degrees. Upstream had no limit, so a tank
     * whose front wheels hung over a shell crater tipped until it fell in nose first; 40 degrees
     * still follows a one-block staircase hill (45 degrees between steps, smoothed by the wheels).
     */
    public static final float MAX_TILT = 40.0F;
    public float targetPitch;
    public float targetRoll;

    public TankTerrain(TankEntity tank, TankInfo info) {
        this.tank = tank;
        this.info = info;
        this.center = new Vec3(0, -AircraftInfo.GLOBAL_Y_OFFSET, info.weightedCenterZ);
        this.wheels = new Wheel[info.wheels.size() * 2];
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < this.wheels.length; i++) {
            Vec3 p = info.wheels.get(i / 2).pos();
            Vec3 local = new Vec3(i % 2 == 0 ? p.x : -p.x, p.y, p.z);
            Wheel wheel = new Wheel(tank, local, p.z >= this.center.z);
            wheel.setPos(tank.getTransformedPosition(local).add(0, 1, 0));
            this.wheels[i] = wheel;
            min = Math.min(min, p.z); max = Math.max(max, p.z);
        }
        this.span = max - min;
    }

    public int wheelCount() { return this.wheels.length; }
    /** Read-only contact data for client imprints; excludes the synthetic pitch-support flags below. */
    public Vec3 contactPosition(int index) { return this.wheels[index].position(); }
    public Vec3 contactLocal(int index) { return this.wheels[index].local; }
    public boolean contactGrounded(int index) { return this.wheels[index].contactGrounded; }

    public void move() {
        if (this.wheels.length == 0) { return; }
        for (Wheel w : this.wheels) {
            Vec3 target = this.tank.getTransformedPosition(w.local).add(0, 0.35, 0);
            // Placement/load/teleport may relocate the hull after its type creates these probes.
            // Never sweep a probe across the world from the old position.
            if (target.distanceToSqr(w.position()) > 256) { w.setPos(target.add(0, 1, 0)); }
            Vec3 delta = target.subtract(w.position());
            w.move(MoverType.SELF, new Vec3(delta.x + this.tank.motionX(), delta.y * 0.15, delta.z + this.tank.motionZ()));
            w.move(MoverType.SELF, new Vec3(0, -0.1, 0));
            w.contactGrounded = w.onGround();
        }
        int force = -1;
        for (int i = 0; i < this.wheels.length / 2; i++) {
            force = i;
            Wheel a = this.wheels[2 * i], b = this.wheels[2 * i + 1];
            if (!a.plus && (a.onGround() || b.onGround())) { force = -1; break; }
        }
        if (force >= 0) { this.wheels[2 * force].setOnGround(true); this.wheels[2 * force + 1].setOnGround(true); }
        force = -1;
        for (int i = this.wheels.length / 2 - 1; i >= 0; i--) {
            force = i;
            Wheel a = this.wheels[2 * i], b = this.wheels[2 * i + 1];
            if (a.plus && (a.onGround() || b.onGround())) { force = -1; break; }
        }
        if (force >= 0) { this.wheels[2 * force].setOnGround(true); this.wheels[2 * force + 1].setOnGround(true); }
        Vec3 transformed = this.tank.getTransformedPosition(this.center).subtract(this.tank.position());
        Vec3 origin = this.tank.position().add(transformed.x, this.center.y + 0.35, transformed.z);
        Vec3 normal = Vec3.ZERO;
        for (int i = 0; i < this.wheels.length; i += 2) {
            Wheel a = this.wheels[i], b = this.wheels[i + 1];
            Vec3 va = a.position().subtract(origin), vb = b.position().subtract(origin);
            Vec3 cross = (a.local.z >= 0 ? vb.cross(va) : va.cross(vb)).normalize();
            // A one-axle addon has no pitch baseline. Skip its degenerate normal rather than divide by zero.
            double factor = this.span > 1.0E-8 && (a.onGround() || b.onGround()) ? Math.abs(a.local.z / this.span) : 0;
            normal = normal.add(cross.scale(factor));
        }
        if (normal.lengthSqr() > 1.0E-12) {
            normal = normal.normalize();
            if (normal.y > 0.01 && normal.y < 0.7) {
                this.tank.setDeltaMovement(this.tank.getDeltaMovement().add(normal.x / 50, 0, normal.z / 50));
            }
            normal = normal.yRot((float) Math.toRadians(this.tank.getYaw()));
            float pitch = (float) (90 - Math.toDegrees(Math.atan2(normal.y, normal.z)));
            float roll = -(float) (90 - Math.toDegrees(Math.atan2(normal.y, normal.x)));
            pitch = this.tank.getPitch() + net.minecraft.util.Mth.clamp(pitch - this.tank.getPitch(),
                    -this.info.onGroundPitchFactor, this.info.onGroundPitchFactor);
            roll = this.tank.getRoll() + net.minecraft.util.Mth.clamp(roll - this.tank.getRoll(),
                    -this.info.onGroundRollFactor, this.info.onGroundRollFactor);
            if ((this.tank.onGround() || this.tank.blockWithin(1, -2)) && this.tank.getDeltaMovement().horizontalDistance() < 0.06) {
                pitch = this.tank.getPitch() + Math.max(pitch - this.tank.getPitch(), -3.5F) * 0.45F;
            }
            this.targetPitch = Mth.clamp(pitch, -MAX_TILT, MAX_TILT);
            this.targetRoll = Mth.clamp(roll, -MAX_TILT, MAX_TILT);
        }
        for (Wheel w : this.wheels) {
            Vec3 target = McheliMath.rotVec3(w.local, -this.tank.getYaw(), -this.targetPitch, -this.targetRoll).add(this.tank.position());
            double x = Math.clamp(w.getX(), target.x - 2, target.x + 2);
            double z = Math.clamp(w.getZ(), target.z - 2, target.z + 2);
            w.setPos(x, x != w.getX() || z != w.getZ() ? target.y + w.maxUpStep() / 2 : w.getY(), z);
        }
    }

    private static final class Wheel extends Entity {
        final Vec3 local;
        final boolean plus;
        final TankEntity parent;
        boolean contactGrounded;
        Wheel(TankEntity parent, Vec3 local, boolean plus) {
            super(parent.getType(), parent.level());
            this.parent = parent; this.local = local; this.plus = plus;
            this.refreshDimensions();
        }
        @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(1, 1); }
        @Override public float maxUpStep() { return 1.5F; }
        @Override public boolean canCollideWith(Entity other) { return other != this.parent && !this.parent.isMountedEntity(other) && super.canCollideWith(other); }
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }
        @Override protected void readAdditionalSaveData(ValueInput in) { }
        @Override protected void addAdditionalSaveData(ValueOutput out) { }
        @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                net.minecraft.world.damagesource.DamageSource source, float damage) { return false; }
    }
}
