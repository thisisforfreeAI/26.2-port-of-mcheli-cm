package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * One of a vehicle's content bounding boxes ({@code BoundingBoxes} in the content files) as a
 * part of the vehicle entity, so players can hit and use the whole airframe: upstream's
 * {@code MCH_BoundingBox} together with its {@code MCH_EntityHitBox}.
 *
 * <p>Upstream tested hits against the rotated box. A part entity is an axis-aligned box, so the
 * port uses the box that encloses the rotated one (upstream's own broad-phase box); the damage
 * factor of the box still applies.
 */
public class AircraftHitBox extends PartEntity<AircraftEntity> {

    private @Nullable BoundingBoxInfo info;

    public AircraftHitBox(AircraftEntity parent) {
        super(parent);
        this.noPhysics = true;
    }

    /** Sets the content box this part stands for; null leaves it empty (a vehicle with fewer boxes). */
    public void setInfo(@Nullable BoundingBoxInfo info) {
        this.info = info;
    }

    public @Nullable BoundingBoxInfo getInfo() {
        return this.info;
    }

    public float getDamageFactor() {
        return this.info != null ? this.info.damageFactor : 1.0F;
    }

    /** Upstream {@code MCH_BoundingBox#updatePosition}: rotates the box with the vehicle. */
    public void updatePosition(double posX, double posY, double posZ, float yaw, float pitch, float roll) {
        if (this.info == null) {
            this.setPos(posX, posY, posZ);
            this.setBoundingBox(new AABB(posX, posY, posZ, posX, posY, posZ));
            return;
        }
        Quaternionf orientation = McheliMath.orientationQuat(-yaw, -pitch, -roll);
        Vec3 offset = McheliMath.applyQuat(orientation, this.info.offsetX, this.info.offsetY, this.info.offsetZ);
        double cx = posX + offset.x;
        double cy = posY + offset.y;
        double cz = posZ + offset.z;
        Vec3 ax = McheliMath.applyQuat(orientation, 1.0, 0.0, 0.0);
        Vec3 ay = McheliMath.applyQuat(orientation, 0.0, 1.0, 0.0);
        Vec3 az = McheliMath.applyQuat(orientation, 0.0, 0.0, 1.0);
        double hx = this.info.width / 2.0;
        double hy = this.info.height / 2.0;
        double hz = this.info.widthZ / 2.0;
        double ex = Math.abs(ax.x) * hx + Math.abs(ay.x) * hy + Math.abs(az.x) * hz;
        double ey = Math.abs(ax.y) * hx + Math.abs(ay.y) * hy + Math.abs(az.y) * hz;
        double ez = Math.abs(ax.z) * hx + Math.abs(ay.z) * hy + Math.abs(az.z) * hz;
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setPosRaw(cx, cy - ey, cz);
        this.setBoundingBox(new AABB(cx - ex, cy - ey, cz - ez, cx + ex, cy + ey, cz + ez));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean isPickable() {
        return this.info != null && this.getParent().isPickable();
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return this.getParent().getPickResult();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableToBase(source)) {
            return false;
        }
        return this.getParent().hurtFromHitBox(level, this, source, damage);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return this.getParent().interact(player, hand, location);
    }

    @Override
    public boolean is(Entity other) {
        return this == other || this.getParent() == other;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.info != null ? EntityDimensions.scalable(this.info.width, this.info.height)
                : EntityDimensions.scalable(0.0F, 0.0F);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
