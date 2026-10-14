package io.github.mcheliceport.content.aircraft;

import net.minecraft.world.phys.AABB;

/**
 * An extra hitbox of a vehicle, as defined in its content file.
 *
 * <p>Ported from the definition part of upstream {@code com.norwood.mcheli.aircraft.MCH_BoundingBox}.
 * Upstream used one class for both the definition and the per-entity rotated box used in collision
 * tests; the port keeps the definition here and builds the runtime boxes from it when the vehicle
 * entities are ported (M3).
 */
public class BoundingBoxInfo {

    /** Upstream {@code MCH_BoundingBox.EnumBoundingBoxType}. */
    public enum Type {
        DEFAULT,
        ENGINE,
        TURRET
    }

    /** Offset from the vehicle origin. */
    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;
    /** Size along X. */
    public final float width;
    /** Size along Z. */
    public final float widthZ;
    /** Size along Y. */
    public final float height;
    /** Multiplier for damage taken through this box. */
    public final float damageFactor;
    public Type boundingBoxType = Type.DEFAULT;
    /** Optional name, for example to tie a box to a part. */
    public String name = "";
    /** True if the box is an explosive reactive armour tile that pops on a qualifying hit. */
    public boolean isERA = false;
    /** Explosion size when the tile pops (0 for none). */
    public float eraExplosion = 0.0F;
    /** Smallest incoming damage that pops the tile. */
    public float eraMinDamage = 0.0F;

    public BoundingBoxInfo(double x, double y, double z, float w, float h, float df) {
        this(x, y, z, w, h, w, df);
    }

    public BoundingBoxInfo(double posX, double posY, double posZ, float widthX, float height, float widthZ,
            float df) {
        this.offsetX = posX;
        this.offsetY = posY;
        this.offsetZ = posZ;
        this.width = widthX;
        this.widthZ = widthZ;
        this.height = height;
        this.damageFactor = df;
    }

    /**
     * The unrotated box around the offset, as upstream's {@code MCH_BoundingBox#boundingBox} right
     * after construction.
     */
    public AABB getBoundingBox() {
        float halfWidth = this.width / 2.0F;
        float halfHeight = this.height / 2.0F;
        float halfDepth = this.widthZ / 2.0F;
        return new AABB(this.offsetX - halfWidth, this.offsetY - halfHeight, this.offsetZ - halfDepth,
                this.offsetX + halfWidth, this.offsetY + halfHeight, this.offsetZ + halfDepth);
    }

    @Override
    public String toString() {
        return "BoundingBoxInfo{offset=(" + this.offsetX + ", " + this.offsetY + ", " + this.offsetZ + "), width="
                + this.width + ", height=" + this.height + ", widthZ=" + this.widthZ + ", damageFactor="
                + this.damageFactor + ", type=" + this.boundingBoxType + ", name='" + this.name + "', isERA="
                + this.isERA + '}';
    }
}
