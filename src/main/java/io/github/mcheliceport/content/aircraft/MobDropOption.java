package io.github.mcheliceport.content.aircraft;

import net.minecraft.world.phys.Vec3;

/**
 * Where and how often a vehicle drops riding mobs. Ported from upstream
 * {@code com.norwood.mcheli.aircraft.MCH_MobDropOption}.
 */
public class MobDropOption {

    public Vec3 pos = Vec3.ZERO;
    public int interval = 1;

    @Override
    public String toString() {
        return "MobDropOption{pos=" + this.pos + ", interval=" + this.interval + '}';
    }
}
