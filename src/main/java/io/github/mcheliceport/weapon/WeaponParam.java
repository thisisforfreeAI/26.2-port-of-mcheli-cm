package io.github.mcheliceport.weapon;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The parameter block passed through a shot: who fires from what, where and in which direction,
 * the weapon's options, and the results the weapon set fills in. Ported from upstream
 * {@code MCH_WeaponParam}.
 */
public class WeaponParam {

    /** The vehicle (or other entity) the weapon is mounted on. */
    public @Nullable Entity entity = null;
    /** The rider firing it. */
    public @Nullable Entity user = null;
    public double posX = 0.0;
    public double posY = 0.0;
    public double posZ = 0.0;
    public float rotYaw = 0.0F;
    public float rotPitch = 0.0F;
    public float rotRoll = 0.0F;
    public int option1 = 0;
    public int option2 = 0;
    public boolean isInfinity = false;
    public boolean isTurret = false;
    public boolean result;
    public boolean reload;
    public float minPitch;
    public float maxPitch;
    public float randYaw;
    public float randPitch;

    public void setPosAndRot(double x, double y, double z, float yaw, float pitch) {
        this.setPosition(x, y, z);
        this.setRotation(yaw, pitch);
    }

    public void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public void setRotation(float y, float p) {
        this.rotYaw = y;
        this.rotPitch = p;
    }
}
