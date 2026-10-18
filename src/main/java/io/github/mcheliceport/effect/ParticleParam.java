package io.github.mcheliceport.effect;

/**
 * The settings of one McHeli smoke or splash particle. Ported from upstream
 * {@code com.norwood.mcheli.particles.MCH_ParticleParam}, without the world (the caller's level
 * is passed alongside).
 */
public class ParticleParam {

    /** {@code splash} for a splash particle, anything else for smoke (upstream's rule). */
    public final String name;
    public double posX;
    public double posY;
    public double posZ;
    public double motionX = 0.0;
    public double motionY = 0.0;
    public double motionZ = 0.0;
    public float size = 1.0F;
    public float a = 1.0F;
    public float r = 1.0F;
    public float g = 1.0F;
    public float b = 1.0F;
    public boolean isEffectWind = false;
    public int age = 0;
    public boolean diffusible = false;
    public boolean toWhite = false;
    public float gravity = 0.0F;
    public float motionYUpAge = 2.0F;
    /** Client appearance hint only: existing damage smoke starts hot, then cools. */
    public float damageHeat;

    public void setDamageHeat(int hp, int maxHp) {
        this.damageHeat = maxHp <= 0 ? 0 : Math.clamp((0.3F - (float) hp / maxHp) / 0.2F, 0, 1);
    }
    /**
     * Not in upstream: the block a vehicle's downwash rises from, so the client's M8 look can
     * colour the dust after it (water gives white spray). Only the look depends on it.
     */
    public net.minecraft.world.level.block.state.@org.jspecify.annotations.Nullable BlockState groundState;

    public ParticleParam(String name, double x, double y, double z) {
        this.name = name;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public ParticleParam(String name, double x, double y, double z, double mx, double my, double mz, float size) {
        this(name, x, y, z);
        this.motionX = mx;
        this.motionY = my;
        this.motionZ = mz;
        this.size = size;
    }

    public void setColor(float a, float r, float g, float b) {
        this.a = a;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public void setMotion(double x, double y, double z) {
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
    }
}
